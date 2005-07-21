import sys
import logging
import traceback
from zymb import sched
import readline
    
class Readline(sched.Agent):
    """Readline: An Agent which attends to the readline interface.

    This agent WILL NOT WORK without a hacked-up Python readline module.
    The standard Python readline module is missing some APIs which are
    necessary for asynchronous operation. You can get my hacked-up source
    from <http://www.eblong.com/zarf/zymb/>.

    WARNING: Do not instantiate a Readline agent from a Python interactive
    shell which is using readline! The results will make you sad.

    As is common for agents, Readline does not actually do any work
    until you start it and call mainloop(). Also, it does
    not (by default) do anything with received data. Incoming data
    triggers an 'input' event, but there is no preregistered handler for
    this event. You can write your own, or install the basicinput()
    handler. See below.

    mainloop() -- main event loop.

    The procedure for invoking zymb is slightly complicated when you are
    using readline. Instead of calling zymb.sched.process in one of the
    usual ways, you should call the mainloop() function provided in this
    module. (The readline module's event hook is rigged to call
    zymb.sched.process for you.)

    Readline(ignoreempty=False) -- constructor.

    If *ignoreempty* is true, empty inputs (just hitting Enter) will not
    generate 'input' events. If it is false, they will generate 'input'
    events containing empty strings.

    You can only create one Readline agent, because it does not make sense
    for more than one readline to be running in a single process.

    Agent states and events:

    state 'start': Initial state.
    event 'input' (unicode): A line of input has been read. (Newline included.)
    event 'interrupt': The user hit ctrl-C.
    state 'end': The agent has shut down.

    Public methods:

    write(dat) -- print output.
    basicinput(str) -- a simple event handler for 'input'.
    getprompt() -- get the current readline prompt.
    setprompt(str) -- change the readline prompt.
    
    """
    
    singleton = None
    quitnow = False
    received = []
    interruption = None
    
    def __init__(self, ignoreempty=False):
        if (Readline.singleton):
            raise Exception, 'only one Readline agent may exist'
        
        sched.Agent.__init__(self)
        self.prompt = '> '
        self.ignoreempty = ignoreempty

        self.incomplete = ''

        try:
            readline.set_erase_empty_line(1)
            readline.set_event_hook(event_hook)
            readline.set_startup_hook(startup_hook)
        except:
            print """
Hello, Pythoneer! This is the zymb.readlineagent module. I need
some features which your standard Python readline module does not
support. Go to <http://www.eblong.com/zarf/zymb/> for my hacked-up
readline code. I apologize for the inconvenience.
"""
            raise
            
        Readline.singleton = self

    def write(self, dat):
        """write(str) -> None

        Print output. Since readline takes over the terminal, it is unwise
        to print data directly, or write to sys.stdout -- that will confuse
        the cursor position and the editing line.

        Instead, you should call this method. The data you pass will be
        printed tidily, without interrupting or corrupting the user's typing.
        The edit line will be moved down if necessary.

        This method only prints data in full lines. If the string you pass
        does not end with a newline, the fractional line will be invisibly
        buffered until you do write a newline.
        """
        
        Readline.received.append(dat)

    def getprompt(self):
        """getprompt() -> str

        Return the current readline prompt. The default prompt is '> '.
        """
        return self.prompt
        
    def setprompt(self, st):
        """setprompt(str) -> None

        Change the readline prompt. The default prompt is '> '.
        """
        
        self.prompt = st

    def basicinput(self, dat):
        """basicinput(dat) -> None

        A simple event handler for 'input'. This simply writes the
        received data to stdout:
        
            self.write(dat)

        This handler is not installed by default. You can enable it by
        calling:
        
            readlnag.addhandler('input', fileag.basicinput)
        """
        
        self.write(dat)

def event_hook():
    """event_hook() -- internal readline callback. Do not call.
    """
    try:
        res = sched.process(0)
        if (not res):
            Readline.quitnow = True
            readline.set_done(1)
    except Exception, ex:
        (typ, val, tb) = sys.exc_info()
        biglist = traceback.format_exception(typ, val, tb)
        print 'Exception during sched.process():', str(ex)
        print ''.join(biglist)
    if (Readline.received):
        ln = readline.get_line_buffer()
        if (ln != None):
            Readline.interruption = (ln,
                readline.get_point(),
                readline.get_mark())
        readline.set_done(1)
        
def startup_hook():
    """startup_hook() -- internal readline callback. Do not call.
    """
    if (Readline.interruption != None):
        readline.insert_text(Readline.interruption[0])
        readline.set_point(Readline.interruption[1])
        readline.set_mark(Readline.interruption[2])
        Readline.interruption = None

def mainloop():
    """mainloop() -> None

    The procedure for invoking zymb is slightly complicated when you are
    using readline. Instead of calling zymb.sched.process in one of the
    usual ways, you have to set up readline, and then call raw_input in
    a loop. (The readline module's event hook is rigged to call
    zymb.sched.process for you.)

    This function does all of that. Simply call mainloop() as your top-level
    event loop. When the input stream is terminated (either by ctrl-C or
    because the Readline agent stopped), mainloop() will return.
    """
    
    ag = Readline.singleton
    if (not ag or not ag.live):
        raise Exception, 'must create and start a Readline agent before calling mainloop'
        
    while (not Readline.quitnow):
        ln = None
        try:
            ln = raw_input(ag.prompt)
            ln = unicode(ln, sys.stdin.encoding)
        except EOFError:
            print ''
            pass
        except KeyboardInterrupt:
            print ''
            print '<exit>'
            ag.perform('interrupt')
        if (ln == '' and ag.ignoreempty):
            ln = None
        if (ln != None):
            if (Readline.interruption == None):
                ag.perform('input', ln+'\n')
    
        while (Readline.received):
            msg = Readline.received.pop(0)
            ag.incomplete = ag.incomplete + msg
    
        while 1:
            nlpos = ag.incomplete.find('\n')
            if (nlpos < 0):
                break
            line = ag.incomplete[:nlpos]
            ag.incomplete = ag.incomplete[nlpos+1:]
            print line
