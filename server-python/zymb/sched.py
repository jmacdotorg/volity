import sys
import time
import select
import logging

# Constants used internally in Action.register()

LOC_ACTION  = intern('action')
LOC_TIMER   = intern('timer')
LOC_SOCKETS = intern('sockets')
LOC_POLLERS = intern('pollers')
LOC_HANDLER = intern('handler')

class SchedException(Exception):
    """SchedException: A generic exception used whenever something goes
    wrong in the scheduler.
    """
    pass

class TimeoutException(Exception):
    """TimeoutException: An exception which is used whenever something takes
    longer than you wanted it to.
    """
    pass

class Action:
    """Action: Something to do. A function reference, more or less,
    except this class can include arguments bound into the function call.
    
    (If Python had closures, this wouldn't be necessary. Okay: Python
    *does* have closures. Fine. Feel free to use them. Or use an Action
    with arguments. It's fine either way.)

    What you do with an action is pass it to the scheduler, which will
    then execute it. You can pass it in several ways:

    * agent.queueaction -- do it immediately (very soon)
    * agent.addhandler -- do it when the agent signals some event
    * agent.addtimer -- do it at a specified time
    * agent.registerpoll -- do it at regular intervals to check for activity
    * agent.registersocket -- do it when there is activity on a file descriptor

    An action can only be handed to the scheduler once. If you want something
    to happen in several ways, make a new action for each use. Fortunately, 
    they are cheap to manufacture. (In fact, the methods listed above will
    all manufacture an Action for you. You can simply pass *func* and
    *args* to them.)

    An exception inside an Action will be caught; it will not kill the Zymb
    scheduler. Depending on the context, the exception may be caught and
    handled in a context-dependent way. If not, the scheduler will catch it
    and log the stack trace.

    Action(func, [ arg1, arg2, ... ] ) -- constructor.

    *func* should be a callable, and then you can supply any number of
    *args*. Alternatively, *func* can be a tuple whose first element
    is callable: (func, arg1, arg2...) If you really want to get fancy,
    you can supply a tuple plus more args after it. They'll be concatenated.

    You cannot put keyword arguments into an Action.

    Methods:

    __call__(*exargs) -- make it happen.
    remove() -- remove the action from the scheduler.

    Internal methods:

    register(loc, agent) -- mark the action as being in the scheduler.
    """
    
    ident = None
    interval = None
    periodic = False
    secondary = None
    
    def __init__(self, op, *args):
        if (type(op) == tuple):
            self.op = op[0]
            self.args = op[1:] + args
        else:
            self.op = op
            self.args = args
        self.agent = None
        self.location = None
        self.locevent = None

    def __call__(self, *exargs):
        """__call__(*exargs) -> result
        
        Make it happen. The *exargs* are appended to the bound-in arguments.
        The operation's return value is passed back out.
        
        action.__call__(...) can more concisely be written as action(...).
        """
        
        ls = self.args + exargs
        return self.op(*ls)

    def register(self, loc, agent, event=None):
        """register(loc, agent, event=None) -- internal method to mark the
        action as being in the scheduler. Do not call.
        """
        
        if (self.agent):
            raise SchedException, 'action is already in the scheduler'
        self.agent = agent
        self.location = loc
        self.locevent = event
        if (self.secondary == None):
            if (loc == LOC_SOCKETS or loc == LOC_POLLERS):
                self.secondary = True
            else:
                self.secondary = False

    def remove(self):
        """remove() -> None

        Remove the action from the scheduler entirely. (Whether it was
        added with queueaction, addhandler, registerpoller, registersocket,
        or addtimer.) It will not be invoked again.

        It is safe to call this more than once, or to call it on an action
        which has never been scheduled.
        """
        
        if (True):
            # Always do this
            ls = [ tup for tup in actionqueue if tup[0] == self ]
            for tup in ls:
                actionqueue.remove(tup)
            ls = [ tup for tup in secondaryqueue if tup[0] == self ]
            for tup in ls:
                secondaryqueue.remove(tup)
        if (self.location == LOC_TIMER):
            ls = [ tup for tup in timerqueue if tup[1] == self ]
            for tup in ls:
                timerqueue.remove(tup)
        if (self.location == LOC_POLLERS):
            ls = [ tup for tup in pollers if tup[1] == self ]
            for tup in ls:
                pollers.remove(tup)
        if (self.location == LOC_SOCKETS):
            ls = [ fno for fno in sockets.keys() if sockets[fno] == self ]
            for fno in ls:
                sockets.pop(fno)
        if (self.location == LOC_HANDLER):
            ls = self.agent.handlers.get(self.locevent)
            if (ls):
                while (self in ls):
                    ls.remove(self)

class PeriodicAction(Action):
    """PeriodicAction: Something to do periodically.

    See the Action class for the basic description. A PeriodicAction differs
    only in that you must pass an *interval* when you create it, and then
    you can use the PeriodicAction only with addtimer(). (As opposed to
    queueaction(), addhandler(), etc.)

    PeriodicAction(interval, func, [ arg1, arg2, ... ] ) -- constructor.
    
    The *interval* must be positive. Once the timer is started, the action
    will be invoked every *interval* seconds. Call remove() to shut it down.
    
    Methods:

    __call__(*exargs) -- make it happen.
    remove() -- remove the action from the scheduler.

    Internal methods:

    register(loc, agent) -- mark the action as being in the scheduler.
    """
    
    periodic = True
    
    def __init__(self, interval, op, *args):
        Action.__init__(self, op, *args)
        self.interval = float(interval)
        if (self.interval <= 0):
            raise ValueError('periodic timer must have a positive interval')

    def register(self, loc, agent, event=None):
        """register(loc, agent, event=None) -- internal method to mark the
        action as being in the scheduler. Do not call.

        The PeriodicAction only works with addtimer. This method is customized
        to check that.
        """
        
        if (loc != LOC_TIMER):
            raise ValueError('a PeriodicAction can only be used with addtimer')
        Action.register(self, loc, agent, event=event)

class ActionPollTimer(Action):
    """ActionPollTimer: An Action used internally to keep track of poller
    timing. You should not use this class.
    """
    
    periodic = True
    
    def __init__(self, pollop, interval):
        Action.__init__(self, pollop)
        self.interval = interval

    def __call__(self, *exargs):
        raise SchedException, 'ActionPollTimer should not be invoked'
        
class Deferred:
    """Deferred: An object which represents an operation to be finished later.

    Sometimes, in an asynchronous world, you find yourself in a function
    call that you don't want to return from right away. You want to go
    do some other operations -- maybe gather some information from another
    source -- and then return.

    Some people would use threading to solve this problem. Start a new
    thread, block the original thread, and wait for the results to come in.
    But here at Zymb, we don't believe in threading. It's a headache.
    It leads to lock hierarchies, deadlocks, and profanity.

    Instead, you are allowed (in certain contexts) to create a Deferred
    object, and raise it. The Deferred represents the completion of the
    operation. It contains a function (which you supply), which will be
    called "later on".

    Now, that sounds simple enough. But exceptions complicate the problem.
    Perhaps the semantics of your function call permit it to return a
    value *or* raise a Fault of some kind. (The Fault is caught and
    handled specially by the code that called you.) If you defer your
    response, you must still be permitted to raise the Fault later on.
    
    Therefore, a Deferred must contain a callback function *and* the
    layers of exception handlers that must be wrapped around it.

    (If this makes your head hurt, imagine how I feel.)

    A Deferred object guarantees that it will only be invoked once.
    This allows you to register it in several places, with different
    arguments. (Perhaps you want it to be called by a message dispatcher
    and also by a timer -- whichever triggers first.)

    To make this more efficient, whenever you schedule a Deferred to be
    invoked (via agent.addtimer, agent.queueaction, or whatever) you should
    call Deferred.addaction() on the resulting Action. The first time
    the Deferred is invoked, it will cancel all its other Actions.
    (This is, however, not mandatory. If the Deferred gets invoked a second
    time, it will simply do nothing.)

    Deferred(op, *args) -- constructor.

    Static method:

    extract() -- retrieve the results of a deferred operation.

    Public methods:

    addaction() -- register an action that can invoke the object.
    queue() -- queue the object to be invoked immediately.

    Internal methods:
    
    addcontext() -- wrap a layer of exception handling around the object.
    """
    def __init__(self, op, *args):
        ac = Action(op, *args)
        self.live = True
        self.action = ac
        self.actionlist = []
        self.contextlist = None

    def extract(tup):
        """extract(tup) -> value

        This static method must be called by the callback to a deferred
        operation. If the operation returned a value, extract() will
        return it to you. If the operation raised an exception, extract()
        will raise it for you to catch.
        """
        
        (conchain, ac, exargs) = tup
        if (not conchain):
            return ac(*exargs)
        else:
            (subchain, wrap, wrapargs) = conchain
            return wrap((subchain, ac, exargs), *wrapargs)
    extract = staticmethod(extract)

    def addaction(self, ac):
        """addaction(Action) -> None

        Register an action that can invoke the Deferred object. When
        the object is invoked, all registered actions are cancelled.
        """
        
        self.actionlist.append(ac)

    def queue(self, agent, *args):
        """queue(agent, *args) -> None

        Queue the Deferred object to be invoked immediately, on *agent*. 
        (The choice of agent doesn't really matter, since the object
        will be invoked the same way regardless. But you do have to pick
        one.)

        This takes care of calling addaction() for you.
        """
        
        if (not self.live):
            return
        ac = agent.queueaction(self, *args)
        self.actionlist.append(ac)
        
    def addcontext(self, wrap, *wrapargs):
        """addcontext() -- wrap a layer of exception handling around the
        object.

        You do not need to call this either to defer an operation, or
        to start an operation which will be deferred. It is used internally
        by services which implement deferrable operations.
        """
        
        if (type(wrap) == tuple):
            wrapargs = wrap[1:] + wrapargs
            wrap = wrap[0]
        self.contextlist = (self.contextlist, wrap, wrapargs)

    def __call__(self, *exargs):
        """__call__(*exargs) -> value

        Invoke the Deferred, passing in *exargs*. The fact that Deferred
        objects are callable is what allows you to schedule them and
        set them up as dispatchers.

        This method first checks to see if this is the first invocation.
        (If not, it just exits.) It then cancels all the registered actions,
        to prevent future invocations. It then calls in through the nested
        onion of exception handlers, down to the callback.
        """
        
        if (not self.live):
            return
            
        self.live = False
        while (self.actionlist):
            ac = self.actionlist.pop(0)
            ac.remove()
            
        conchain = self.contextlist
        ac = self.action

        try:
            if (not conchain):
                res = ac(*exargs)
            else:
                (subchain, wrap, wrapargs) = conchain
                res = wrap((subchain, ac, exargs), *wrapargs)
            return res
        except Deferred, ex:
            ex.contextlist = self.contextlist
            
class Agent:
    """Agent: Base class representing a single connection or source of data.
    The scheduler keeps track of all Agents and watches all their data
    sources in parallel.

    Agents have several features which work nicely with the scheduler.

    * Agents are state machines.

    The act of making and authenticating a network connection can
    require several steps. But we never want to block and call a function
    that steps through several operations. Therefore, an agent has a
    notion of its "current operating state".

    The state is just a string. Every agent has 'start' and 'end' states;
    most have more.

    Various events (like receiving network data) can cause the agent
    to jump to a new state. When this happens, state handler functions
    are called. (There can be one handler, or several, or none, for a
    particular state transition. They run in last-added-first-run order.)

    When you create an agent, it is not in any state; it is entirely
    inactive. (It will not do any network activity at construction time.)
    When you start the agent, it moves to the 'start' state. In general,
    every agent has a predefined 'start' handler that begins network
    activity (opening sockets, etc).

    When this starting activity succeeds, the agent moves itself to a
    new state, such as 'connected'. (See the documentation for particular
    Agent classes.) To make use of an agent, you will typically add a
    handler which begins doing your work.

    When you want to stop an agent, or if it encounters a fatal error,
    it moves to the 'end' state. Every agent has a predefined 'end' handler
    that shuts down its resources. You can add more 'end' handlers to
    notify your application that the agent is dead.

    * Agents can handle events as well as state transitions.

    When an agent finds it has work to do, it will typically do some
    processing (e.g., pulling data out of a network socket) and then try
    to pass on the results to someone (e.g., your application). It does
    this by performing an event. Just as with state transitions, you
    can register handlers to respond to events. (In fact, you register
    them in the same way.)

    Any agent can perform an 'error' event, which always has two arguments:
    an exception, and the agent which caused the error. (The second argument
    is useful because error events are sometimes relayed around between
    agents.) Other events are defined for particular Agent classes.

    A freshly-created agent will not have any handlers for its work
    events. It is your job to add handlers to do the work you desire.
    However, most agents have a *sample* handler method for each
    event.

    * Agents can talk to each other.

    By registering event handlers with each other, agents can pass information
    back and forth. An agent can act as a filter or a higher-level protocol
    on top of another agent.

    * Agents can efficiently wait for activity.

    The scheduler is in charge of checking whether agents have work to do.
    This is so that the scheduler can be efficient about it and not spin
    the CPU. Each agent has to nicely ask the scheduler to do this checking
    for it, and there are three ways to do it:

    - Regular polling. The scheduler will call the agent's polling function
    at a regular interval you specify (but at least once per sched.process()
    call.)

    - Socket activity. If the agent is watching a network socket, the
    scheduler can add that socket to its select() call, and notify the
    agent only when there is socket activity.

    - Triggered by other agents. If the agent eats the output of another
    agent, it just has to add a handler for that agent's events.

    * Agents can set timed events.

    An agent can ask the scheduler to call it back at some point in the
    future. This is handy for states or operations where you want to
    give up after a while.

    * Agents deal with their own laundry first.

    You may wonder, given all these events and state transitions, whether
    agents have to do a lot of locking and consistency checks. After all,
    if one network message triggers a state transition, might another
    network message arrive -- and trigger a different transition -- before
    the first transition's handlers have a chance to run?

    The answer is, actually, no. Timers and network pollers are considered
    low priority. Work queued up by perform(), jump(), and queueaction()
    will be handled before the next timer event or network message. So
    you can program a chain of handlers and consequences without worrying
    that external events will sneak in and disrupt it.

    Of course, if you set a timed event, many things might happen before
    it fires. So you do have to watch out for that case.
    

    Agent() -- constructor.

    Publicly readable fields:

    live -- indicates whether the agent is listed in the scheduler.
    logprefix -- string to prepend to logging messages (names the class)
    
    Public methods:

    start() -- begin activity.
    stop() -- stop activity.
    jump(state, *args) -- jump to a different operating state.
    perform(eventname, *args) -- trigger an event.
    addhandler(eventname, op, *args) -- add a state/event handler.
    addtimer(op, *args, delay=num, when=num) -- add a timed event.
    addcleanupaction(action) -- mark an action to be removed when agent stops.
    basicerror(exc, agent) -- a simple event handler for 'error'.

    Methods intended for the agent to call on itself:

    queueaction(op, *args) -- invoke an action.
    registerpoll(op, *args, interval=num) -- add a repeating activity check.
    registersocket(socket, op, *args) -- add a socket-based activity check.

    Internal methods:

    shutdown() -- 'end' state handler.
    """

    agentcounter = 0

    logprefix = 'zymb.agent'    
    hidden = False
    
    def __init__(self):
        self.agentindex = Agent.agentcounter
        Agent.agentcounter = Agent.agentcounter+1
        
        self.state = ''
        self.live = False
        self.handlers = {}
        self.cleanupactions = []
        self.log = logging.getLogger(self.logprefix)

        self.addhandler('end', self.shutdown)

    def __str__(self):
        return '<%s #%d>' % (self.__class__, self.agentindex)
        
    def start(self):
        """start() -> None

        Begin activity. The agent adds itself to the scheduler and moves
        to the 'start' state. It does not actually begin doing work in
        this call; that happens the next time the scheduler is invoked
        (sched.process).

        You should only call this once per agent.
        """
        
        global agentcount
        if (self.live or self in agents):
            raise SchedException('tried to start agent which was already started')
        agents.append(self)
        self.live = True
        if (not self.hidden):
            agentcount = agentcount+1
        self.jump('start')

    def stop(self):
        """stop() -> None

        Stop activity. The agent moves to the 'end' state. Its 'end'
        state handlers will take care of shutting it down and removing
        it from the scheduler.

        It is safe to call this on an agent which is already in 'end',
        or which is completely shut down. (Nothing further will happen.)

        (Remember, state handlers are not called as part of the stop()
        method. They are run by the scheduler "soon" after stop() is
        called. If you want to be sure the agent is completely stopped,
        you can check the agent.live field. However, it is better to
        register your own 'end' state handler, and consider the agent
        stopped when that it called.)
        """
        
        if (not self.live):
            return
        self.jump('end')

    def shutdown(self):
        """shutdown() -- internal 'end' state handler. Do not call.

        This is the final 'end' handler. It removes the agent from the
        scheduler, and cancels all its remaining actions, timed events,
        etc.
        """
        global agentcount
        if (not self.live):
            return
        self.live = False
        if (not self.hidden):
            agentcount = agentcount-1
        agents.remove(self)
        
        ls = [ tup for tup in actionqueue if tup[0].agent == self ]
        for tup in ls:
            actionqueue.remove(tup)
        ls = [ tup for tup in secondaryqueue if tup[0].agent == self ]
        for tup in ls:
            secondaryqueue.remove(tup)
        ls = [ tup for tup in timerqueue if tup[1].agent == self]
        for tup in ls:
            timerqueue.remove(tup)
        ls = [ tup for tup in pollers if tup[1].agent == self]
        for tup in ls:
            pollers.remove(tup)
        ls = [ fno for fno in sockets.keys() if sockets[fno].agent == self]
        for fno in ls:
            sockets.pop(fno)
        
        while (self.cleanupactions):
            ac = self.cleanupactions.pop()
            ac.remove()
        self.handlers.clear()

    def queueaction(self, op, *args):
        """queueaction(op, *args) -> action

        Invoke an action. The action will be handed to the scheduler, which
        will run it very soon.

        ("Very soon" means "before the next timed event or network message
        is handled." However, if you queue several actions, they will be
        handled in the order you queued them.)

        You can pass any callable as *op*; the (optional) *args* will be
        passed to it as arguments. You can also, if you like, create
        an Action object and pass it as *op*. You can still pass *args*
        in this case; they'll be appended to the arguments bound into
        the Action.

        Returns the Action.
        """
        
        if (isinstance(op, Action)):
            ac = op
            tup = (ac, args)
        else:
            ac = Action(op, *args)
            tup = (ac, ())
            
        ac.register(LOC_ACTION, self)

        if (secondaryaction):
            secondaryqueue.append(tup)
        else:
            actionqueue.append(tup)
        return ac

    def jump(self, st, *exargs):
        """jump(state, *args) -> None

        Jump to a different operating state. (If the agent is already
        in the given state, nothing happens.)

        This queues up all the state transition handlers registered
        for the new state. If you provide *args*, they are passed to the
        handlers when they are called.
        """
        
        assert self.live
        if (self.state == st):
            return
        if (self.state == 'end'):
            self.log.warning('tried to jump from state \'end\' to \'%s\'',
                st)
            return
        self.log.info('%s in state "%s"', self, st)
        self.state = st
        
        actions = self.handlers.get(st)
        if (actions):
            if (secondaryaction):
                for ac in actions:
                    secondaryqueue.append( (ac, exargs) )
            else:
                for ac in actions:
                    actionqueue.append( (ac, exargs) )

    def perform(self, name, *exargs):
        """perform(eventname, *args) -> None

        Trigger an event.

        This queues up all the event handlers registered for the given
        event. If you provide *args*, they are passed to the handlers when
        they are called.
        """
        
        actions = self.handlers.get(name)
        if (actions):
            if (secondaryaction):
                for ac in actions:
                    secondaryqueue.append( (ac, exargs) )
            else:
                for ac in actions:
                    actionqueue.append( (ac, exargs) )

    def addhandler(self, state, op, *args):
        """addhandler(eventname, op, *args) -> action

        Add a state or event handler. The *eventname* is a string naming
        the state or event you want to handle. The *op* and *args* are
        the operation to perform when that state or event occurs.
        (See queueaction() for the use of *op* and *args*.)

        Handlers are invoked in last-added-first-run order. If an agent
        has a built-in handler, it is added first, so it runs last. Handlers
        that you add on later run earlier.

        Returns the Action. (You can cancel the handler by calling
        ac.remove().)
        """
        
        if (isinstance(op, Action)):
            ac = op
        else:
            ac = Action(op, *args)
            
        ac.register(LOC_HANDLER, self, state)
        
        actions = self.handlers.get(state)
        if (actions == None):
            self.handlers[state] = [ ac ]
        else:
            actions.insert(0, ac)

        return ac

    def registerpoll(self, op, *args, **dic):
        """registerpoll(op, *args, interval=num) -> action

        Add a repeating activity check. *interval* is a (mandatory) keyword
        argument; the scheduler will invoke your operation at least every
        *interval* seconds. (And at least once per sched.process() call.)
        
        See queueaction() for the use of *op* and *args*. Your operation
        should not do any work except for absorbing input and queueing
        actions. Typically, it will collect the input and then call
        self.perform(), triggering an event, so that the agent's event
        handlers can do something with the input.

        Returns the Action. (You can cancel it by calling ac.remove().)
        """
        
        if (isinstance(op, Action)):
            ac = op
        else:
            ac = Action(op, *args)

        if (dic.has_key('interval')):
            interval = float(dic['interval'])
        else:
            raise ValueError('registerpoll requires interval= argument')

        if (interval <= 0):
            raise ValueError('registerpoll must have a positive interval')
            
        ac.register(LOC_POLLERS, self)
        pollers.append( (interval, ac) )
        return ac

    def registersocket(self, fileno, op, *args):
        """registersocket(socket, op, *args) -> action

        Add a socket-based activity check. The *socket* may be a socket
        object, or a number representing a fileno, or any object which
        provides a fileno() method. 

        See queueaction() for the use of *op* and *args*. Your operation
        should not do any work except for absorbing input and queueing
        actions. Typically, it will collect the input and then call
        self.perform(), triggering an event, so that the agent's event
        handlers can do something with the input.

        Returns the Action. (You can cancel it by calling ac.remove().)
        """
        
        assert self.live
        if (type(fileno) != int):
            fileno = fileno.fileno()
            
        if (isinstance(op, Action)):
            ac = op
        else:
            ac = Action(op, *args)
            
        if (sockets.has_key(fileno)):
            raise SchedException('socket %d is already registered' % (fileno))
            
        ac.register(LOC_SOCKETS, self)
        sockets[fileno] = ac
        return ac

    def addtimer(self, op, *args, **dic):
        """addtimer(op, *args, delay=num, when=num, periodic=bool) -> action

        Add a timed event. You must supply exactly one of the keyword
        arguments *delay* and *when*. A *when* is an absolute time,
        expressed as a number of seconds since the epoch (e.g., the kind
        of value returned by time.time()). A *delay* is a relative time,
        expressed as a number of seconds in the future.

        It is legal to give a time in the past (or a negative *delay*).
        In that case, this is similar to queueaction() -- the operation
        will occur very soon. (Although it's still a "low priority"
        action.)

        If you supply *periodic*=True, the event will occur repeatedly,
        every *delay* seconds. (The interval is inferred if you give
        *when*.) In this case, you may NOT give a negative *delay* or
        a time in the past -- it is meaningless for a periodic event to
        have a period less than or equal to zero.

        You can also create a periodic timer by calling addtimer with
        a PeriodicAction, which is a subclass of Action. This is slightly
        more flexible; the interval between calls is specified when
        you create the PeriodicAction, but the delay before the first
        call may be specified with *delay* or *when*.

        See queueaction() for the use of *op* and *args*.

        Returns the Action. (You can cancel the timer by calling
        ac.remove().)
        """
        
        assert self.live
        if (isinstance(op, Action)):
            ac = op
        else:
            ac = Action(op, *args)
            
        if (dic.has_key('when')):
            val = dic.pop('when')
            when = float(val)
            interval = when - time.time()
        elif (dic.has_key('delay')):
            val = dic.pop('delay')
            interval = float(val)
            when = time.time() + interval
        elif (ac.interval):
            interval = float(ac.interval)
            when = time.time() + interval
        else:
            raise ValueError('addtimer requires when= or delay= argument')

        if (dic.has_key('periodic')):
            val = dic.pop('periodic')
            if (val):
                if (interval <= 0):
                    raise ValueError('periodic timer must have a positive interval')
                ac.periodic = True
                ac.interval = interval
            
        if (dic):
            raise TypeError('invalid keyword argument for addtimer: '
                + ' '.join(dic.keys()))
            
        ac.register(LOC_TIMER, self)
        
        for ix in range(len(timerqueue)):
            if (when < timerqueue[ix][0]):
                timerqueue.insert(ix, (when, ac) )
                return ac
        timerqueue.append( (when, ac) )
        return ac

    def addcleanupaction(self, ac):
        """addcleanupaction(action) -> None

        Add *action* to the list of actions to be removed when the agent
        stops (i.e., jumps to the 'end' state).

        This is intended for actions that an agent adds as handlers to
        other agents. For example:
        
            ac = otheragent.addhandler('event', self.foreigneventhandler)
            # Ensure that ac is removed when *self* shuts down.
            self.addcleanupaction(ac)

        You do not need to list pollers or socket-checkers as cleanup
        actions. Nor do you need to list handlers that an agent adds
        to itself. (All these are automatically removed when the agent
        shuts down.) You also don't have to worry about the case of
        *otheragent* shutting down; when that happens, all handlers added
        to it are removed.
        """
        
        self.cleanupactions.append(ac)

    def basicerror(self, exc, agent):
        """basicerror(exc, agent) -> None

        A simple event handler for 'error'. This simply writes the
        received data to stdout:
        
            sys.stdout.write('Error: ' + str(agent) + ': ' + str(exc) + '\n')

        This handler is not installed by default. You can enable it by
        calling:
        
            ag.addhandler('error', ag.basicerror)
        """
        
        sys.stdout.write('Error: ' + str(agent) + ': ' + str(exc) + '\n')

class SchedTimeoutAgent(Agent):
    """SchedTimeoutAgent: An Agent used internally to keep track of timeouts
    in the process() function. You should not use this class.

    There is just one instance of this class, which is stored in the global
    variable blockeragent.
    """
    
    hidden = True
    def __init__(self):
        Agent.__init__(self)
        self.proclog = logging.getLogger('zymb.process')
    def trigger(self):
        self.proclog.debug('timed out')

# ----
# Global variables used by process():

# agents -- list of Agent objects which are active within the scheduler.
# (That is, Agents whose agent.live field is True.)

agents = []

# agentcount -- number of live Agents, discounting hidden ones.
# (A hidden Agent is one which does not count for the purpose of
# determining whether process() returns True.) Currently, the only
# hidden agent is the SchedTimeoutAgent, so agentcount will always
# be len(agents)-1.

agentcount = 0

# secondaryaction -- whether actions queued *by* the current action
# should go into the primary queue or the secondary queue. By default,
# if the current action was registered as a poller, this will be
# true. Timers, handlers, and ordinary queued actions will have this
# be false.

secondaryaction = False

# actionqueue -- list of (action, args) tuples.
# This represents actions which are scheduled to happen right away.
# The actions are Action objects. The call to be made will be action(*args).

actionqueue = []

# secondaryqueue -- list of (action, args) tuples.
# This represents actions which are scheduled to happen *almost* right
# away... right after the actionqueue completes.
# The actions are Action objects. The call to be made will be action(*args).

secondaryqueue = []

# timerqueue -- list of (time, action) tuples.
# This represents actions which are scheduled to happen in the future.
# The actions are Action objects. The time value is an absolute time
# (a float, seconds since the epoch). The timerqueue list is sorted by time.
#
# An action can be on timerqueue and actionqueue at the same time (this
# happens for periodic actions).

timerqueue = []

# pollers -- list of (interval, action) tuples.
# This represents repeating activity checks, registered by various agents.
# The actions are Action objects. The interval values are the interval
# (in seconds) at which to repeat the poll actions. 

pollers = []

# sockets -- dict of { fileno : action } pairs.
# This represents socket-based activity checks, registered by various agents.
# The fileno values are integer filenos. The actions are Action objects.

sockets = {}

def process(timeout=None):
    """process(timeout=None) -> bool

    This is the core of the system. It handles all the work of all started
    Agents: checking for input, transiting from one state to another,
    carrying out timed actions.

    The return value indicates whether any Agents are still alive and
    continuing work. ("Work" defined as above: waiting for input, waiting
    for a timer, changing state.) If process() returns False, there's no
    point in calling it again, unless you create a new Agent.

    If the *timeout* argument is zero, this operates like a poll. It will
    process input which is ready immediately, handle events whose time
    has come, and take care of all other current activity. Then it will
    return immediately.

    If *timeout* is None, this will do all of the above. If there is in
    fact some activity, it will return immediately. However, if there is
    no ready activity, it will block until some *is* ready. The first timer
    that fires, or the first input which arrives, will be processed, and then
    the call will return.

    If *timeout* is a positive number, this will handle activity or block
    (as above). However, the maximum amount of time it will block is
    *timeout* seconds. (This may be an int or float.) After that time,
    the call will return whether there has been activity or not.

    Do not call process(0) in a tight loop. That's the non-blocking form,
    so you'll be spinning the CPU.

    It is reasonable (recommended, in fact) to call process() in a tight
    loop with no timeout argument, or a positive timeout. However, you
    *must* check the return value and break if it is False. The call
    will refuse to block if there are no Agents running.

    So, given all that, there are several ways you can use process().

    * In a non-interactive program:

        # Call process in blocking mode, until there is no more work
        # to do. (I.e., until all Agents have terminated.)

        while True:
            res = sched.process()
            if (not res):
                break

    * As a poller in an existing interactive program with an event loop:

        while True:
            UI.nextevent()
            sched.process(0)

    * As a poller in an existing interactive program which provides
    a "wait" or "cycle" handler:

        def myevent():
            sched.process(0)
        UI.set_wait_handler(myevent)

    """

    global secondaryaction

    activity = False
    firsttime = True
    polltimersset = False
    blockeraction = None
    scheddead = False
    log = logging.getLogger('zymb.process')

    while 1:
        if (firsttime):
            log.debug('beginning')
        else:
            log.debug('continuing')
        
        now = time.time()
        nowpolls = []
        repeaters = []
        while (timerqueue):
            (tim, ac) = timerqueue[0]
            if (tim > now):
                break
            timerqueue.pop(0)
            if (isinstance(ac, ActionPollTimer)):
                nowpolls.append(ac.op)
            else:
                actionqueue.append( (ac, ()) )
            if (ac.periodic):
                repeaters.append(ac)

        for ac in repeaters:
            interval = ac.interval
            ag = ac.agent
            ac.agent = None  # pretend we can re-register the action
            ag.addtimer(ac, when=now+interval)

        if (firsttime):
            ls = [ ac for (interval, ac) in pollers ]
        else:
            ls = nowpolls
        for ac in ls:
            assert ac.agent.live
            secondaryaction = ac.secondary
            #log.debug('polling %s for %s', ac, ac.agent)
            try:
                ac()
            except Exception, ex:
                log.error('Uncaught exception in poll action',
                    exc_info=True)

        secondaryaction = False
            
        if (sockets):
            ls = sockets.keys()
            (inls, outls, exls) = select.select(ls, [], [], 0)
            for fileno in inls:
                ac = sockets[fileno]
                secondaryaction = ac.secondary
                try:
                    ac()
                except Exception, ex:
                    log.error('Uncaught exception in poll action',
                        exc_info=True)

        secondaryaction = False
        
        # All the items on actionqueue are timers and polls. They all
        # count as secondary actions. So move them there.

        secondaryqueue.extend(actionqueue)
        del actionqueue[:]

        while (1):
            if (actionqueue):
                (ac, exargs) = actionqueue.pop(0)
            elif (secondaryqueue):
                (ac, exargs) = secondaryqueue.pop(0)
            else:
                break
                
            assert ac.agent.live
            activity = True
            secondaryaction = ac.secondary
            #log.debug('activity %s for %s', ac, ac.agent)
            try:
                ac(*exargs)
            except Deferred:
                pass
            except Exception, ex:
                log.error('Uncaught exception in action',
                    exc_info=True)
    
        secondaryaction = False
        
        if (activity):
            break
        if (timeout == 0):
            break

        if (not polltimersset):
            polltimersset = True
            now = time.time()
        
            if (timeout != None):
                assert (blockeraction == None)
                blockeraction = blockeragent.addtimer(blockeragent.trigger,
                    when=now+timeout)

            for (interval, ac) in pollers:
                timac = ActionPollTimer(ac, interval)
                ac.agent.addtimer(timac, when=now+interval)

        if (not (timerqueue or sockets)):
            log.info('scheduler has nothing left to do')
            scheddead = True
            break

        if (timerqueue):
            firstwhen = timerqueue[0][0]
            now = time.time()
            delay = firstwhen - now + 0.01
            if (delay > 0):
                if (sockets):
                    ls = sockets.keys()
                    select.select(ls, [], [], delay)
                else:
                    time.sleep(delay)
        else:
            ls = sockets.keys()
            select.select(ls, [], [])

        firsttime = False

    # Finished with loop.

    if (polltimersset):
        if (blockeraction):
            blockeraction.remove()
        ls = [ tup for tup in timerqueue
            if isinstance(tup[1], ActionPollTimer) ]
        for tup in ls:
            timerqueue.remove(tup)

    if (scheddead):
        return False
    return (agentcount > 0)

def stopall():
    """stopall() -> None

    Stop all agents (which are currently running).

    This requests that the agents stop. It doesn't carry out the work of
    stopping them (closing sockets, etc). After you call this, you should
    continue to call process() until it returns False.
    """
    
    for ag in agents:
        ag.stop()

blockeragent = SchedTimeoutAgent()
blockeragent.start()
