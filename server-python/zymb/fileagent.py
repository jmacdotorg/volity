import sys
import os
import errno
import fcntl
import logging
import sched

class File(sched.Agent):
    """File: An Agent which reads from a file or file object.
    
    As is common for agents, File does not actually do any work
    until you start it and begin calling sched.process(). Also, it does
    not (by default) do anything with received data. Incoming data
    triggers a 'handle' event, but there is no preregistered handler for
    this event. You can write your own, or install the basichandle()
    handler. See below.

    File(file) -- constructor.

    The *file* can be a string (filename) or a file object which is open
    for reading (such as sys.stdin).

    If you pass a string, the agent will open the file. In this mode,
    the agent will almost certainly read the whole file in a single gulp,
    and then close it immediately. I don't know if this is useful, but you
    can do it.

    If you pass a file object, the agent sets it to non-blocking mode.
    Again, if the file refers to an actual disk file, you'll probably
    get the whole contents at once. If it's stdin or some other stream,
    you'll receive the data as it arrives. In this mode, since the agent
    did not open the file, it will not presume to close it.

    Agent states and events:

    state 'start': Open the file (if appropriate), set it non-blocking.
    state 'connected': The file is open and ready.
    event 'handle' (str): Data has been received from the file.
    event 'error' (exc, agent): A low-level error has been detected.
        Errors detected when the file is being opened will stop the
        agent; read/write errors will not.
    event 'closed': Reached the end of the file, or (for a stdin-like
        file) the file was closed from the other side. This will be
        immediately followed by a jump to the 'end' state.
    state 'end': Close the file (if this agent was responsible for opening
        it).

    Public methods:

    basichandle(str) -- a simple event handler for 'handle'.
    
    Internal methods:

    connect() -- 'start' state handler.
    gotactivity() -- handler for socket activity.
    disconnect() -- 'end' state handler.
    """
    
    logprefix = 'zymb.file'
    
    def __init__(self, fl):
        sched.Agent.__init__(self)
        if (type(fl) in [str, unicode]):
            self.file = None
            self.filename = fl
            self.agentopened = True
        else:
            self.file = fl
            self.filename = fl.name
            self.agentopened = False
        self.addhandler('start', self.connect)
        self.addhandler('end', self.disconnect)

    def __str__(self):
        return '<%s %s>' % (self.__class__, self.filename)
        
    def connect(self):
        """connect() -- internal 'start' state handler. Do not call.
        Open the file (if a filename was provided) and set it nonblocking.
        If successful, jump to state 'connected'. On error, jump to
        state 'end'.
        """
        
        self.log.info('connecting to %s', self.filename)
        try:
            if (self.agentopened):
                assert (self.file == None)
                self.file = open(self.filename)
            flags = fcntl.fcntl(self.file, fcntl.F_GETFL)
            fcntl.fcntl(self.file, fcntl.F_SETFL, flags | os.O_NDELAY)
        except Exception, ex:
            self.log.error('unable open %s: %s',
                self.filename, ex)
            self.perform('error', ex, self)
            self.stop()
            return
        self.registersocket(self.file.fileno(), self.gotactivity)
        self.jump('connected')

    def gotactivity(self):
        """gotactivity() -- internal handler for file activity. Do not call.

        Pull as much data from the file as is currently available.
        Perform a 'handle' event. If the file has closed, perform 'closed'
        and then jump to state 'end'.
        """
        
        input = ''
        broken = False
        while 1:
            try:
                st = self.file.read()
                if (not st):
                    self.log.info('end of file on %s',
                        self.filename)
                    broken = True
                    break
                input += st
            except IOError, ex:
                if (ex.errno == errno.EAGAIN):
                    # no new data to read
                    break
                self.log.warning('read error on file %s: %s',
                    self.filename, ex)
                self.perform('error', ex, self)
                break
        if (input):
            self.log.debug('received (%d b): %s', len(input), input)
            self.perform('handle', input)
        if (broken):
            self.perform('closed')
            self.stop()
    
    def basichandle(self, dat):
        """basichandle(dat) -> None

        A simple event handler for 'handle'. This simply writes the
        received data to stdout:
        
            sys.stdout.write(dat)

        This handler is not installed by default. You can enable it by
        calling:
        
            fileag.addhandler('handle', fileag.basichandle)
        """
        
        sys.stdout.write(dat)

    def disconnect(self):
        """disconnect() -- internal 'end' state handler. Do not call.

        Close the file, if this agent was responsible for opening it.
        """
        
        if (self.file):
            if (self.agentopened):
                self.file.close()
                self.log.info('closed %s', self.filename)
            else:
                self.log.info('released %s', self.filename)
            self.file = None
            
        