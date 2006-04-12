import socket
import sys
import errno
import logging
import sched

DefaultBufferSize = 1024

class TCP(sched.Agent):
    """TCP: An Agent which attends to a single TCP-IP socket connection.

    As is common for agents, TCP does not actually do any network work
    until you start it and begin calling sched.process(). Also, it does
    not (by default) do anything with received data. Incoming data
    triggers a 'handle' event, but there is no preregistered handler for
    this event. You can write your own, or install the basichandle()
    handler. See below.

    TCP(host, port, sock=None) -- constructor.

    If you do not supply a *sock* argument, this will open a socket 
    connection to the given *host* and *port*. If you do supply a *sock*,
    the agent will make use of the existing socket (which must be open).
    In either case, the TCP agent will "own" the socket, and will close it
    when the agent shuts down.

    (If you supply a *sock*, the *host* and *port* values are kept only
    for logging messages. You can fill in filler values if you have nothing
    meaningful to supply.)

    Agent states and events:

    state 'start': Open the socket (if appropriate) and begin watching it.
    state 'connected': The socket is open and ready.
    event 'handle' (str): Data has been received from the socket. (As with
        all low-level Internet messaging, the data is not necessarily
        terminated with a newline, and the sender's messages may be
        divided up or merged together in transit. The bytes will, however,
        arrive in the correct order.)
    event 'error' (exc, agent): A low-level error has been detected.
        Errors detected when the socket is being opened will stop the
        agent; read/write errors will not.
    event 'closed': The socket was closed from the other end. This will
        be immediately followed by a jump to the 'end' state.
    state 'end': Close the socket.

    Publicly readable fields:

    host -- the hostname the socket connects to
    port -- the port number the socket connects to
    sock -- the socket object itself (if open)

    Public methods:

    send(str) -- send data out through the socket.
    basichandle(str) -- a simple event handler for 'handle'.

    Internal methods:

    connect() -- 'start' state handler.
    gotactivity() -- handler for socket activity.
    disconnect() -- 'end' state handler.
    """

    logprefix = 'zymb.tcp'
    connectimmediately = True
    
    def __init__(self, host, port, sock=None):
        sched.Agent.__init__(self)
        self.buffersize = DefaultBufferSize
        self.host = host
        self.port = port
        self.sock = sock
        self.addhandler('start', self.connect)
        self.addhandler('end', self.disconnect)

    def __str__(self):
        return '<%s %s:%d>' % (self.__class__, self.host, self.port)

    def connect(self):
        """connect() -- internal 'start' state handler. Do not call.

        Connect to the socket (if none was provided). If successful, jump
        to state 'connected'. On error, jump to state 'end'.
        """
        
        self.log.info('connecting to %s:%d', self.host, self.port)
        try:
            if (not self.sock):
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.connect( (self.host, self.port) )
            self.sock.setblocking(0)
        except Exception, ex:
            self.log.error('unable to connect to %s:%d: %s',
                self.host, self.port, ex)
            self.perform('error', ex, self)
            self.stop()
            return
        #self.registerpoll(self.gotactivity, interval=1)
        self.registersocket(self.sock, self.gotactivity)
        if (self.connectimmediately):
            self.jump('connected')
        else:
            self.jump('open')

    def gotactivity(self):
        """gotactivity() -- internal handler for socket activity. Do not call.

        Pull as much data from the socket as is currently available.
        Perform a 'handle' event. If the socket has closed, perform 'closed'
        and then jump to state 'end'.
        """
        
        input = ''
        broken = False
        while 1:
            try:
                instr = self.sock.recv(self.buffersize)
                if (instr == ''):
                    self.log.info('connection lost to %s:%d',
                        self.host, self.port)
                    broken = True
                    break
                input = input + instr
            except socket.error, ex:
                (errnum, errstr) = ex
                if (errnum == errno.EAGAIN):
                    # no new data to read
                    break
                self.log.warning('read error on socket %s:%d: %d: %s',
                    self.host, self.port, errnum, errstr)
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
        
            tcp.addhandler('handle', tcp.basichandle)
        """
        
        sys.stdout.write(dat)

    def send(self, dat):
        """send(dat) -> int

        Send data out through the socket. Return the number of bytes written.
        (That will be all of them; this is really a sendall method.)

        The data is sent raw. If you want to send Unicode data, you must
        encode it to a str -- via utf-8 or whatever -- before calling send().
        """
        
        dat = str(dat)
        if (not self.sock):
            if (self.state == 'end'):
                msg = 'socket already closed'
            else:
                msg = 'socket never opened'
            self.log.warning('unable to send (%s)', msg)
            return
        self.log.debug('sending (%d b):  %s', len(dat), dat)

        try:
            res = self.sock.sendall(dat)
        except socket.error, ex:
            (errnum, errstr) = ex
            res = 0
            self.log.warning('write error on socket %s:%d: %d: %s',
                self.host, self.port, errnum, errstr)
            self.perform('error', ex, self)
        return res

    def disconnect(self):
        """disconnect() -- internal 'end' state handler. Do not call.

        Close the socket.
        """
        
        if (self.sock):
            self.sock.close()
            self.sock = None
            self.log.info('closed %s:%d', self.host, self.port)

class TCPSecure(TCP):
    """TCPSecure: A subclass of the TCP Agent which is capable of SSL/TLS
    secure communication.

    A TCPSecure agent behaves just like a plain TCP agent, communicating
    in the clear, until its beginssl() method is called. If that succeeds,
    subsequent communication will be encrypted. (If it fails, the socket
    will be closed.)

    TCPSecure(host, port, sock=None) -- constructor.

    If you do not supply a *sock* argument, this will open a socket 
    connection to the given *host* and *port*. If you do supply a *sock*,
    the agent will make use of the existing socket (which must be open).
    In either case, the TCPSecure agent will "own" the socket, and will close
    it when the agent shuts down.

    (If you supply a *sock*, the *host* and *port* values are kept only
    for logging messages. You can fill in filler values if you have nothing
    meaningful to supply.)

    Agent states and events:

    state 'start': Open the socket (if appropriate) and begin watching it.
    state 'connected': The socket is open and ready.
    state 'secure': Encrypted communication has begun.
    event 'handle' (str): Data has been received from the socket. (As with
        all low-level Internet messaging, the data is not necessarily
        terminated with a newline, and the sender's messages may be
        divided up or merged together in transit. The bytes will, however,
        arrive in the correct order.)
    event 'error' (exc, agent): A low-level error has been detected.
        Errors detected when the socket is being opened will stop the
        agent; read/write errors will not.
    event 'closed': The socket was closed from the other end. This will
        be immediately followed by a jump to the 'end' state.
    state 'end': Close the socket.

    Publicly readable fields:

    host -- the hostname the socket connects to
    port -- the port number the socket connects to
    sock -- the socket object itself (if open)
    ssl -- the socket.ssl object (if in secure mode) or None (if not)

    Public methods:

    send(str) -- send data out through the socket.
    basichandle(str) -- a simple event handler for 'handle'.
    beginssl() -- begin secure communication.

    Internal methods:

    connect() -- 'start' state handler.
    gotactivity() -- handler for socket activity.
    disconnect() -- 'end' state handler.
    """
    
    logprefix = 'zymb.tcpsecure'
    
    def __init__(self, host, port, sock=None):
        TCP.__init__(self, host, port, sock)
        self.ssl = None
    
    def gotactivity(self):
        """gotactivity() -- internal handler for socket activity. Do not call.

        Pull as much data from the socket as is currently available.
        Perform a 'handle' event. If the socket has closed, jump to
        state 'end'.

        This falls back to TCP.gotactivity when in non-secure mode.
        """
        
        if (not self.ssl):
            return TCP.gotactivity(self)
            
        input = ''
        broken = False
        while 1:
            try:
                instr = self.ssl.read()
                input = input + instr
            except socket.sslerror, ex:
                (errnum, errstr) = ex
                if (errnum == socket.SSL_ERROR_WANT_READ
                    or errnum == socket.SSL_ERROR_WANT_WRITE):
                    # no new data to read
                    break
                if (errnum == socket.SSL_ERROR_EOF):
                    broken = True
                    break
                self.log.warning('read error on ssl socket %s:%d: %d: %s',
                    self.host, self.port, errnum, errstr)
                self.perform('error', ex, self)
                break
        if (input):
            self.log.debug('received (ssl, %d b): %s', len(input), input)
            self.perform('handle', input)
        if (broken):
            self.perform('closed')
            self.stop()
            
    def send(self, dat):
        """send(dat) -> int

        Send data out through the socket. Return the number of bytes written.
        (That will be all of them; this is really a sendall method.)

        The data is sent raw. If you want to send Unicode data, you must
        encode it to a str -- via utf-8 or whatever -- before calling send().

        This falls back to TCP.send when in non-secure mode.
        """
        
        if (not self.ssl):
            return TCP.send(self, dat)
        dat = str(dat)
        if (not self.sock):
            if (self.state == 'end'):
                msg = 'socket already closed'
            else:
                msg = 'socket never opened'
            self.log.warning('unable to send (%s)', msg)
            return
        self.log.debug('sending (ssl, %d b):  %s', len(dat), dat)

        try:
            res = 0
            while (dat):
                retry = False
                try:
                    wrote = self.ssl.write(dat)
                except socket.sslerror, ex:
                    (errnum, errstr) = ex
                    if (errnum == socket.SSL_ERROR_WANT_READ
                        or errnum == socket.SSL_ERROR_WANT_WRITE):
                        self.log.warning('retrying write on ssl socket %s:%d',
                            self.host, self.port)
                        retry = True
                    else:
                        raise
                if (retry):
                    continue
                res += wrote
                dat = dat[wrote:]
        except socket.sslerror, ex:
            (errnum, errstr) = ex
            res = 0
            self.log.warning('write error on ssl socket %s:%d: %d: %s',
                self.host, self.port, errnum, errstr)
            self.perform('error', ex, self)
        return res        

    def disconnect(self):
        """disconnect() -- internal 'end' state handler. Do not call.

        Close the socket.
        """
        
        if (self.ssl):
            self.ssl = None
        TCP.disconnect(self)

    def beginssl(self):
        """beginssl() -> None

        Begin secure communication. You should only call this once in the
        lifetime of the socket.

        If SSL/TLS negotiation succeeds, the agent will jump to 'secure'
        state. On error, jump to 'end'.
        """
        
        if (self.ssl):
            self.log.warning('already started ssl on this connection')
            return
        if (not self.sock):
            self.log.warning('cannot start ssl because socket is not open')
        self.log.debug('beginning ssl negotiation')
        try:
            self.sock.setblocking(1)  # Apparently necessary
            self.ssl = socket.ssl(self.sock)
            self.sock.setblocking(0)
        except Exception, ex:
            self.log.error('unable to begin ssl on %s:%d: %s',
                self.host, self.port, ex)
            self.perform('error', ex, self)
            self.stop()
            return
        self.log.info('begun ssl on %s:%d', self.host, self.port)
        if (self.connectimmediately):
            self.jump('secure')
        else:
            self.jump('connected')
    
class SSL(TCPSecure):
    """SSL: A subclass of the TCPSecure Agent which is intended to communicate
    with an SSL server. It begins secure communication as soon as the
    connection is made.

    (Note that SSL agents use the 'connected' state to indicate that the
    socket is secure. This is different from the TCPSecure class, which
    uses 'connected' to indicate an open connection, and then 'secure'
    to indicate a successful beginssl() call. The general principle
    is that, if you create any kind of TCP agent, the 'connected' state
    is your signal to begin sending messages.)

    TCP(host, port, sock=None) -- constructor.

    If you do not supply a *sock* argument, this will open a socket 
    connection to the given *host* and *port*. If you do supply a *sock*,
    the agent will make use of the existing socket (which must be open).
    In either case, the TCP agent will "own" the socket, and will close it
    when the agent shuts down.

    (If you supply a *sock*, the *host* and *port* values are kept only
    for logging messages. You can fill in filler values if you have nothing
    meaningful to supply.)

    Agent states and events:

    state 'start': Open the socket (if appropriate) and begin watching it.
    state 'open': The socket is open but not yet secure. The agent
        immediately begins SSL negotiation and moves to 'connected'.
    state 'connected': The socket is open and ready, in encrypted mode.
    event 'handle' (str): Data has been received from the socket. (As with
        all low-level Internet messaging, the data is not necessarily
        terminated with a newline, and the sender's messages may be
        divided up or merged together in transit. The bytes will, however,
        arrive in the correct order.)
    event 'error' (exc, agent): A low-level error has been detected.
        Errors detected when the socket is being opened will stop the
        agent; read/write errors will not.
    event 'closed': The socket was closed from the other end. This will
        be immediately followed by a jump to the 'end' state.
    state 'end': Close the socket.

    Publicly readable fields:

    host -- the hostname the socket connects to
    port -- the port number the socket connects to
    sock -- the socket object itself (if open)

    Public methods:

    send(str) -- send data out through the socket.
    basichandle(str) -- a simple event handler for 'handle'.

    Internal methods:

    connect() -- 'start' state handler.
    gotactivity() -- handler for socket activity.
    disconnect() -- 'end' state handler.
    """
    
    logprefix = 'zymb.tcpssl'
    connectimmediately = False
    
    def __init__(self, host, port, sock=None):
        TCPSecure.__init__(self, host, port, sock)
        self.addhandler('open', self.beginssl)
        

class TCPListen(sched.Agent):
    """TCPListen: An Agent which listens for incoming TCP-IP connections.

    As is common for agents, TCP does not actually do any network work
    until you start it and begin calling sched.process(). Also, it does
    not (by default) do anything with accepted sockets. When someone
    makes a connection, it triggers an 'accept' event, but there is
    no preregistered handler for this event. You can write your own, or
    install the basicaccept() handler. See below. (If you do not install
    a handler, the socket will linger forever, unused and never closed.)

    TCPListen(port) -- constructor.

    Agent states and events:

    state 'start': Open the socket and begin listening.
    state 'listening': The socket is open and listening.
    event 'accept' (socket, hostname, port): An incoming connection has been
        accepted from this *hostname* (str) and *port* (int).
    state 'end': Close the listening socket.

    Publicly readable fields:

    port -- the port number the socket is listening on
    sock -- the socket object itself (if open)
    
    Public methods:

    basicaccept(socket, host, port) -- a simple event handler for 'accept'.
    
    Internal methods:

    connect() -- 'start' state handler.
    gotactivity() -- handler for socket activity.
    disconnect() -- 'end' state handler.
    
    """

    logprefix = 'zymb.tcplisten'
    
    def __init__(self, port):
        sched.Agent.__init__(self)
        self.port = port
        self.sock = None
        self.addhandler('start', self.connect)
        self.addhandler('end', self.disconnect)

    def __str__(self):
        return '<%s %d>' % (self.__class__, self.port)

    def connect(self):
        """connect() -- internal 'start' state handler. Do not call.

        Bind the socket and start listening. If successful, jump
        to state 'listening'. On error, jump to state 'end'.
        """
        
        self.log.info('listening on port %d', self.port)
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR,
                (self.sock.getsockopt (socket.SOL_SOCKET, socket.SO_REUSEADDR) | 1))
            self.sock.bind( ('localhost', self.port) )
            self.sock.listen(32)
        except Exception, ex:
            self.log.error('unable to listen to port %d: %s',
                self.port, ex)
            self.perform('error', ex, self)
            self.stop()
            return
        self.registersocket(self.sock, self.gotactivity)
        self.jump('listening')

    def gotactivity(self):
        """gotactivity() -- internal handler for socket activity. Do not call.

        Accept the incoming socket connection. Perform an 'accept' event.
        """
        
        try:
            (newsock, (newhost, newport)) = self.sock.accept()
            self.log.info('accepted %s:%d from port %d',
                newhost, newport, self.port)
            self.perform('accept', newsock, newhost, newport)
        except Exception, ex:
            self.log.error('unable to accept on port %d: %s',
                self.port, ex)
            self.perform('error', ex, self)

    def basicaccept(self, sock, host, port):
        """basicaccept(socket, host, port) -> None

        A simple event handler for 'accept'. This creates a TCP agent to
        manage the socket, and starts it:
        
            cl = TCP(host, port, socket)
            cl.start()
            
        This handler is not installed by default. You can enable it by
        calling:
        
            tcpl.addhandler('accept', tcpl.basicaccept)
        """
        
        cl = TCP(host, port, sock)
        cl.start()
                
    def disconnect(self):
        """disconnect() -- internal 'end' state handler. Do not call.

        Close the socket.
        """
        
        if (self.sock):
            self.sock.close()
            self.sock = None
            self.log.info('closed port %d', self.port)

