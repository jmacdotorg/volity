import logging

class Bot:
    """Bot: The implementation of the brains of a bot for a particular game.

    When a new bot is requested, the Referee creates a new Actor, and the
    Actor creates an instance of the Bot subclass. The Bot instance is
    responsible for paying attention to the game state, and making moves
    when necessary.

    This is the class that you subclass in order to implement your game bot.
    It contains a bunch of fields and methods that you override, and another
    bunch of "utility" methods that you can invoke. It should not be necessary
    to invoke the actor or the referee at all.

    Class fields your subclass must override:

    (This must be defined at the top level of the game class, not in the
    contructor.)

    gameclass -- The Game class which your bot is able to play. (Actually,
    Volity will allow your bot to run on *gameclass* or any subclass of
    *gameclass*. So it is legal to specify gameclass=Game, if your bot is
    capable of playing every Volity game. See NullBot in this module.)


    Bot(act) -- constructor.

    This is invoked by the Actor *act*, during the Actor's own constructor.
    You never create Bot objects.

    Methods your subclass can override:

    (You will normally never call these methods. The Actor calls them at the
    appropriate time.)

    getname() -- generate a name for this bot.
    begingame() -- handle the beginning of the game.
    endgame() -- handle the end of the game.
    suspendgame() -- handle game suspension.
    unsuspendgame() -- handle game resumption.
    destroy() -- finalize anything you want to finalize in your class.
    
    Methods your subclass calls:

    send() -- send an RPC to the referee.
    queueaction() -- schedule an action to occur "soon".
    addtimer() -- schedule an action to occur later on.

    Significant fields:

    actor -- the Actor object which this Bot is attached to. You should not
        need to refer to this, but if you do, here it is.
    log -- a logging object. You can use this to put messages in the volityd
        log. (As a rule, you should log ordinary game operations as info();
        detailed game operations as debug(); problems of interest to the
        parlor admin as warning(); and problems that indicate bugs in your
        code as error().)

    Internal methods:
    
    performsend() -- handle the sending of an RPC.
    """
    
    gameclass = None

    def __init__(self, act):
        """__init__(self, act)

        If you provide an __init__() method, it should start like this:

            def __init__(self, act):
                volity.bot.Bot__init__(self, act) # Parent constructor
                # ... game-specific code
        """
        
        if (not isinstance(act, actor.Actor)):
            raise TypeError('bot must be initialized with an Actor')
        self.actor = act
        self.log = self.actor.log

    def getname(self):
        """getname() -> str

        Generate a name for this bot. This is not currently important -- it
        is used only in the bot's disco information. There is no reason
        to change the default.

        Default: the *gamename* from the *gameclass*, plus 'Bot'.
        """
        
        if (self.gameclass and self.gameclass.gamename):
            return self.gameclass.gamename + ' Bot'
        return 'Bot'

    def send(self, methname, *args, **keywords):
        """send(methname, *args, **keywords) -> None

        Send an RPC to the referee.

        The *methname* is the RPC's name. (If it does not contain a period,
        it is assumed to be in the game.* namespace.) The *args*, if present,
        are the RPC arguments. The *keywords* may contain either or both of:

            timeout: How long to wait (in seconds) before considering the
                RPC to have failed.
            callback: A deferral callback to invoke when the outcome of
                the RPC is known.

        By default, the RPC uses a callback which discards the RPC return
        value, logs any errors, and times out after 30 seconds. (Timeouts
        are logged as errors.)

        If you want to make use of the RPC return value, or to catch particular
        error cases, you will have to write a callback function and pass it
        in. A callback looks like this:

            def callback(tup):
                res = sched.Deferred.extract(tup)

        The *tup* argument is opaque; you must pass it to the extract()
        method. The resulting value is the RPC response.

        If the RPC ended abnormally, then the extract() call will raise an
        exception instead of returning a value. You may choose to catch any
        of these exceptions:

            sched.TimeoutException: The RPC timed out.
            rpc.RPCFault: The RPC response was an RPC fault.
            interface.StanzaError: The response was a Jabber stanza-level
                error.
            Exception: Something else went wrong.
        """
        self.queueaction(self.performsend, methname, *args, **keywords)
    
    def performsend(self, methname, *args, **keywords):
        """performsend(methname, args, keywords) -> None

        Handle the sending of an RPC. This is an internal method used by
        send().
        """
        if (not '.' in methname):
            methname = 'game.' + methname
        self.actor.sendref(methname, *args, **keywords)
    
    def queueaction(self, op, *args):
        """queueaction(op, *args) -> action

        Invoke an action. The action will be handed to the scheduler, which
        will run it very soon.

        ("Very soon" means "before the next timed event or network message
        is handled." However, if you queue several actions, they will be
        handled in the order you queued them.)

        This method exists in case you want to undertake some risky action
        from an RPC handler, but you don't want the RPC response to wait on
        (or pass along errors from) your operation. By passing the operation
        to queueaction(), you ensure that it will be executed, but not in
        the current call stack.

        You can pass any callable as *op*; the (optional) *args* will be
        passed to it as arguments. You can also, if you like, create
        an Action object and pass it as *op*. You can still pass *args*
        in this case; they'll be appended to the arguments bound into
        the Action.

        Returns the Action. (The Action class is defined in zymb.sched.)
        """
        return self.actor.queueaction(op, *args)
        
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
        return self.actor.addtimer(op, *args, **dic)

    def begingame(self):
        """begingame() -> None

        Handle the beginning of the game. Set up whatever state you need to.

        Conditions: called only in setup state.

        Default: do nothing.
        """
        pass

    def endgame(self):
        """endgame() -> None

        Handle the end of the game. Clean up whatever state you need to.

        Conditions: can be called in active, disrupted, abandoned, or
        suspended state. (If in suspended, it means that the players voted
        to kill the game. If in any other state, it means that you called
        gameover() or gamecancelled().)

        Default: do nothing.
        """
        pass

    def suspendgame(self):
        """suspendgame() -> None

        Handle game suspension. This may occur because a player requested
        suspension, or because the game was in the abandoned state for too
        long.

        Conditions: can be called in active, disrupted, or abandoned state.

        Default: do nothing.
        """
        pass

    def unsuspendgame(self):
        """unsuspendgame() -> None

        Handle game resumption.

        Conditions: called only in suspended state.

        Default: do nothing.
        """
        pass

    def destroy(self):
        """destroy() -> None

        Finalize anything you want to finalize in your class. This is
        called as the Referee is shutting down. The MUC room has already
        been destroyed, so there's no point in talking to the referee.
        Just clean up the Bot instance.

        Conditions: normally called only in setup state. However, abnormal
        shutdowns can occur at any time.

        Default: do nothing.
        """
        pass

# late imports
import game
import actor

class NullBot(Bot):
    """NullBot: A Bot which can play every game, because it never moves.
    This Bot has no practical value, but it's good for testing.

    If you run a parlor with --bot volity.bot.NullBot, players will be able
    to request bots. A seated NullBot will declare itself ready (like all
    bots), but it never pays attention to game state, and it never makes a
    move. This is almost certainly *not* what players want in an actual game.

    (However, in a game with no turn structure, a NullBot might be useful to
    fill an abandoned seat. It would have to be a game which could be completed
    with one player never doing anything. Most turn-based games require a
    player to send *something*, even if only a game.pass() RPC.)
    """
    gameclass = game.Game

