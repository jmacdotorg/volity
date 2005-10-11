from zymb import jabber
import zymb.jabber.rpc
import zymb.jabber.interface
from volent import FailureToken, Literal

class Game:
    """Game: The implementation of a particular game.

    When a new table is created, the Parlor creates a new Referee, and the
    Referee creates an instance of the Game subclass. The Game instance is
    responsible for knowing the state of the game (both during setup and
    during play), handling each game move, and reporting the outcome to
    the players.

    This is the class that you subclass in order to implement your game.
    It contains a bunch of fields and methods that you override, and another
    bunch of "utility" methods that you can invoke. It should not be necessary
    to invoke the referee at all.

    Class fields your subclass can override:

    (These must be defined at the top level of the game class, not in
    the constructor. The *gamename* and *ruleseturi* are required; the rest
    may be omitted if you want the default value.)

    gamename -- A short label for the game. (Example: "Poker". This is used
        in window titlebars, so don't go overboard.) Your game class must
        set this field.
    ruleseturi -- The URI of the ruleset that this game implements. (As a
        string.) Clients use this to locate UI files. Your game class must
        set this field. (Conventionally, this is a URL for a web page
        describing the Volity implementation of the game. However, that is
        not required; the only requirement is that the URI be a unique string
        associated with the game ruleset.)
    rulesetversion -- A string which gives a version of the ruleset.
        (Default: "1.0")
    gamedescription -- A longer description of the game. (Default: "A Volity
        game.")
    websiteurl -- A URL of a home page for the game, or a web page that
        explains the game for human readers. (Default: same as *ruleseturi*.)
    defaultlanguage -- The initial language setting for new tables.
        (Default: "en")
    defaultrecordgames -- The initial record-games setting for new tables.
        (Default: True)
    defaultshowtable -- The initial show-table setting for new tables.
        (Default: True)
        

    Game(ref) -- constructor.

    This is invoked by the Referee *ref*, during the Referee's own constructor.
    You never create Game objects. However, you must provide an __init__
    method in your subclass, to set up seats and other initial conditions.
    See the documentation of __init__() for details.

    Methods your subclass can override:

    (You will normally never call these methods. The Referee calls them at
    the appropriate time.)

    requestparticularseat() -- validate a player's request for a seat.
    requestanyseat() -- fulfil a player's request for any seat.
    requestanyseatingame() -- fulfil a player's request for any seat (while
        game is suspended).
    checkconfig() -- validate the game configuration before starting.
    checkseating() -- validate the player seating arrangement, before starting.
    begingame() -- handle the beginning of the game.
    endgame() -- handle the end of the game.
    suspendgame() -- handle game suspension.
    unsuspendgame() -- handle game resumption.
    sendconfigstate() -- send the game configuration to a player.
    sendgamestate() -- send the (in-progress) game state to a player.
    destroy() -- finalize anything you want to finalize in your class.

    Methods your subclass calls:

    getseat() -- get the Seat for a given ID.
    getseatlist() -- get the list of Seats.
    setseatsrequired() -- set which Seats are required and which are optional.
    getplayer() -- get the Player for a given JID.
    getplayerlist() -- get the list of Players.
    getplayerseat() -- given a player JID, get the Seat he is sitting in.
    getstate() -- get the Referee state.
    setopset() -- set an Opset to handle incoming game.* RPCs.
    validatecalls() -- set argument-checking on game.* RPCs.
    unready() -- mark all players as unready.
    sendtable() -- send an RPC to everyone present at the table.
    sendobservers() -- send an RPC to everyone at the table who is standing.
    sendgame() -- send an RPC to everyone at the table who is seated.
    sendplayer() -- send an RPC to a single player.
    sendseat() -- send an RPC to everyone who is sitting in a given Seat.
    queueaction() -- schedule an action to occur "soon".
    addtimer() -- schedule an action to occur later on.
    gameover() -- end the game and declare the winners.
    gamecancelled() -- declare the game to have been aborted.

    Significant fields:

    referee -- the Referee object which this Game is attached to. You should
        not need to refer to this, but if you do, here it is.
    log -- a logging object. You can use this to put messages in the volityd
        log. (As a rule, you should log ordinary game operations as info();
        detailed game operations as debug(); problems of interest to the
        parlor admin as warning(); and problems that indicate bugs in your
        code as error().)

    Internal methods:
    
    performsend() -- handle the sending of an RPC.
    """
    
    gamename = None
    gamedescription = 'A Volity game.'
    ruleseturi = None
    rulesetversion = '1.0'
    websiteurl = None

    defaultlanguage = None
    defaultrecordgames = None
    defaultshowtable = None
    
    def __init__(self, ref):
        """__init__(self, ref)

        Your class must provide an __init__() method. At a minimum, it should
        look like this:

            def __init__(self, ref):
                volity.game.Game.__init__(self, ref) # Parent constructor
                self.setopset(self)      # Set up the game.* RPC handlers
                Seat(self, 'seatid')     # Create a seat

        This demonstrates the three things you must do in your constructor:
        call the parent constructor, set a handler for game.* RPCs, and
        create some Seats.

        Depending on the game, it may be convenient to store references to
        your Seat objects. For example:
            self.whiteseat = Seat(self, 'white')
            self.blackseat = Seat(self, 'black')
        This is not required, because you can always retrieve the Seat objects
        by calling getseat() or getseatlist(), but it's often easier.

        By defaults, all Seats are required seats -- the game will not start
        until all your Seats have players. To create an optional seat, use
        the form:
            Seat(self, 'seatid', False)
        You can also call setseatsrequired() to change the requirement flag
        on seats.

        (Note that you may *only* create Seats in your constructor. It's not
        legal later on. Also note that you may want to subclass the generic
        Seat class, and create instances of your Seats instead of the generic
        ones. See the Seat class for details.)

        You also have the opportunity to set up your game state. When
        __init__() is called, the table is brand-new; nobody has sat down
        yet, much less started a game. So you'll want to set up your game
        in its default, pre-game configuration.

        You will probably also want to call validatecalls(), to set up
        automatic arguments-checking for your RPCs.
        """
        
        if (not isinstance(ref, referee.Referee)):
            raise TypeError('game must be initialized with a Referee')
        self.referee = ref
        self.log = self.referee.log

    def getseat(self, id):
        """getseat(id) -> Seat

        Get the Seat for a given ID. This is the same Seat object which you
        created (with *id*) in your constructor. If you did not create a
        Seat with ID *id*, this returns None.
        """
        return self.referee.seats.get(id, None)

    def getseatlist(self):
        """getseatlist() -> list

        Get the list of Seats. These are the same Seat objects which you
        created in your constructor.
        """
        return list(self.referee.seatlist)

    def setseatsrequired(self, set=[], clear=[]):
        """setseatsrequired(set=[], clear=[]) -> None

        Set which Seats are required and which are optional. There are several
        ways to call this:

            setseatsrequired(s)        # mark seat *s* as required
            setseatsrequired(set=s)    # mark seat *s* as required
            setseatsrequired(clear=s)  # mark seat *s* as optional
            setseatsrequired(set=[s1,s2,s3]) # mark *s1*, *s2*, *s3* as
                                    # required
            setseatsrequired(set=[s1,s2], clear=[s3,s4]) # mark *s1*, *s2*
                                    # as required, and *s3*, *s4* as optional

        That is, the *set* and *clear* arguments may each be either a Seat
        object or a list of Seats.

        You may call this at constructor time, or when the game is in the
        setup phase. (See getstate() for access to the game state.) When the
        game is in progress, or suspended, the seating requirements are fixed.
        """
        
        if (isinstance(set, Seat)):
            set = [set]
        if (isinstance(clear, Seat)):
            clear = [clear]
        self.referee.setseatsrequired(set, clear)

    def getplayer(self, jid):
        """getplayer(jid) -> Player

        Get the Player for a given (real) JID. The *jid* may be a JID object
        or a string. If there is no Player with that JID, this returns None.
        """
        
        if (isinstance(jid, jabber.interface.JID)):
            jid = unicode(jid)
        return self.referee.players.get(jid, None)

    def getplayerlist(self):
        """getplayerlist() -> list

        Get the list of Players. This includes all the Players present at
        the table, and also Players who are involved in the game but who
        have disconnected.
        """
        return self.referee.players.values()

    def getplayerseat(self, jid):
        """getplayerseat(jid) -> Seat

        Given a player JID, get the Seat he is sitting in. The *jid* may be
        a JID object or a string. If there is no Player with that JID, or if
        the Player is not seated, this returns None.
        """
        
        pla = self.getplayer(jid)
        if (not pla):
            return None
        return pla.seat

    def getstate(self):
        """getstate() -> str

        Get the Referee state. There are five possible states, which are
        defined as constants in this module:
        
            STATE_SETUP: The game has not yet begun.
            STATE_ACTIVE: The game is in progress.
            STATE_DISRUPTED: The game is in progress, but one seat has been
                abandoned (due to players disconnecting), so the game is
                stuck.
            STATE_ABANDONED: The game is in progress, but all players have
                abandoned it (by disconnecting). The referee will suspend
                the game if this state persists for too long.
            STATE_SUSPENDED: The game has been suspended, either by the
                referee or by player request. Players may sit down, change
                seats, or request bots to fill in seats, so that the game
                can resume.

        (These constants are actually defined as lower-case strings: 'setup',
        'active', etc.)
        """
        return self.referee.refstate

    def setopset(self, gameopset):
        """setopset(gameopset) -> None

        Set an Opset to handle incoming game.* RPCs. You must call this in
        your constructor; if you don't, all game.* RPCs will be rejected.

        The *gameopset* may be an Opset object, or an object that provides
        rpc_* methods. The easiest course is to pass the Game instance itself:
        
            self.setopset(self)

        If you do this, then any methods which begin with "rpc_" will be
        callable as RPCs. For example, a method:

            def rpc_win(self, sender, *args):
                # ...

        will be callable as game.win().
        """
        
        if (not isinstance(gameopset, jabber.rpc.Opset)):
            gameopset = ObjMethodOpset(gameopset)
        self.referee.gamewrapperopset.setopset(gameopset)

    def validatecalls(self, *calllist, **keywords):
        """validatecalls(*calllist, **keywords) -> None

        Set argument-checking on game.* RPCs.

        You typically want to specify the number and type of arguments for
        each game.* RPC in the ruleset. You will also probably want to do
        other kinds of checking for RPCs: some can only be sent during setup,
        some can only be sent by seated players during the game, and so on.

        You could do this by writing your own checking code in each RPC
        handler. However, validatecalls() allows you to specify these checks
        automatically. 

        For example, you could put this line in your constructor:

            self.validatecalls('move', 'pass', afoot=True, argcount=0)

        This specifies that the game.move() and game.pass() RPCs can only
        be sent while the game is in progress, and neither take any arguments.
        If a player invokes a game.* RPC which violates the requirements,
        your RPC handler will not be invoked; he will receive an appropriate
        RPC error.

        You can specify any number of RPC names in a validatecalls() call.
        You can also specify any number of keyword arguments. Each keyword
        defines a restriction; the valid keyswords are defined below.

        It is legal to call validatecalls() more than once for a given RPC.
        The requirements for each RPC stack up. (You might want to define
        afoot=True for several RPCs, and then go back and define argument
        types separately for each of them.)

        If you call validatecalls() with *no* RPC names, the keyword will
        apply to *every* RPC in the game.* namespace. For example:

            self.validatecalls(seated=True)

        This would declare that any game.* RPC should be rejected unless it
        comes from a seated player.
        
        These are the keywords which you can use:

        state=*str*
            Require the referee to be in a particular state. The state may 
            be one of the STATE_* constants defined in this module. Or you
            can use the strings 'setup', 'active', 'disrupted', 'suspended',
            'abandoned'. (Which are what the STATE_* constants are actually
            defined as.) Or, it may contain several of those values, as a
            space-delimited string.
        afoot=*bool*
            If the value is True, this requires the referee to be 'active',
            'disrupted', or 'abandoned'. If False, it requires the referee
            to be in 'setup' or 'suspended'.
        seated=*bool*
            Requires the sending player to be seated (if True) or standing
            (if False).
        argcount=*int*
            Require the given number of arguments.
        args=*typespec*
            Specify the number and type of arguments. This is complicated,
            so we will explain it by example.

            args=None: Require there to be no arguments. (This is equivalent
                to argcount=0.)
            args=int: Exactly one argument, of type integer.
            args=str: Exactly one argument, of type string.
            args=bool: Exactly one argument, of type boolean.
            args=float: Exactly one argument, of type float or integer.
            args=list: Exactly one argument, of type list (array).
            args=dict: Exactly one argument, of type dict (struct).
            args=[int, int]: Exactly two arguments, both integers.
            args=[int, str]: Exactly two arguments; the first an integer, the
                second a string.
            args=(int, str): Exactly one argument, which may be either an int
                or a string.
            args=[str, (int, str)]: Exactly two arguments. The first must be
                a string; the second may be an integer or a string.
            args=(int, None): One optional argument; if present, must be
                an integer.
            args=[str, (int, None)]: Either one or two arguments. The first
                must be a string. The second, if present, must an an integer.
            args=[int, '...']: One or more arguments. The first must be an
                integer. The following arguments are not checked.

            For the purposes of validation, unicode arguments count as strings.
            Integers are accepted as floats. The RPC 'base64' type appears
            as a string. The RPC 'dateTime.iso8601' is not recognized by
            the validator system.

            You may use the Python built-in object Ellipsis instead of the
            literal '...'.

        If you set contradictory conditions, the resulting behavior is poorly
        defined. For example, if you set state='setup' and then set afoot=True,
        the validator may ignore one condition; or it may try to verify both,
        and therefore reject all RPCs. Similarly, setting args=None and
        argcount=1 would cause problems.
        """
        
        if (not calllist):
            self.referee.gamewrapperopset.validation(None, **keywords)
        else:
            for cal in calllist:
                self.referee.gamewrapperopset.validation(cal, **keywords)

    def unready(self):
        """unready() -> None

        Mark all players as unready. You should call this in any setup-phase
        RPC handler that changes the game configuration.

        The idea is that all players must agree on the configuration before
        the game starts. If someone makes a significant change, then any
        previous agreements are void; everyone is marked unready, so that
        they all have to hit the "agree" button again.
        """
        self.referee.unreadyall()

    def sendtable(self, methname, *args, **keywords):
        """sendtable(methname, *args, **keywords) -> None

        Send an RPC to everyone present at the table.

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
        
        ls = self.getplayerlist()
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def sendobservers(self, methname, *args, **keywords):
        """sendobservers(methname, *args, **keywords) -> None

        Send an RPC to everyone at the table who is standing.

        See sendtable() for the description of *methname*, *args*, and
        *keywords*.
        """
        
        ls = [ pla for pla in self.getplayerlist() if not pla.seat ]
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def sendgame(self, methname, *args, **keywords):
        """sendgame(methname, *args, **keywords) -> None

        Send an RPC to everyone at the table who is seated.

        See sendtable() for the description of *methname*, *args*, and
        *keywords*.
        """
        
        ls = [ pla for pla in self.getplayerlist() if pla.seat ]
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def sendplayer(self, player, methname, *args, **keywords):
        """sendplayer(player, methname, *args, **keywords) -> None

        Send an RPC to a single player. The *player* may be a Player object
        or a player's (real) JID.

        See sendtable() for the description of *methname*, *args*, and
        *keywords*.
        """
        
        if (not isinstance(player, referee.Player)):
            player = self.getplayer(player)
        if (not player):
            return
        ls = [ player ]
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def sendseat(self, seat, methname, *args, **keywords):
        """sendseat(seat, methname, *args, **keywords) -> None

        Send an RPC to a everyone who is sitting in a given Seat. The *seat*
        may be a Seat object or the (string) ID of a seat.

        See sendtable() for the description of *methname*, *args*, and
        *keywords*.
        """
        
        if (not isinstance(seat, Seat)):
            seat = self.getseat(seat)
        if (not seat):
            return
        ls = seat.playerlist
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def performsend(self, playerlist, methname, args, keywords):
        """performsend(playerlist, methname, args, keywords) -> None

        Handle the sending of an RPC. This is an internal method used by
        sendtable(), sendgame(), etc.
        """
        
        if (not '.' in methname):
            methname = 'game.' + methname
        for player in playerlist:
            self.referee.sendone(player, methname, *args, **keywords)

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
        return self.referee.queueaction(op, *args)
        
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
        return self.referee.addtimer(op, *args, **dic)

    def gameover(self, *winners):
        """gameover(*winners)

        Declare the game over. Each argument must be a Seat object, or
        a list of Seats (if those seats tied in the final outcome). You
        do not have to list all the seats in the game; the ones you do
        not mention will be considered to have tied for the next-lower
        place. If you pass no arguments at all, then all the seats
        have tied.

        To rephrase that in examples: (Assume north, south, east, west
        are Seats.)

        self.gameover()       # everyone ties
        self.gameover(north)  # North wins, everyone else ties for last
        self.gameover(north, east)  # North wins, East is second, the other
            # two players are tied for last
        self.gameover(north, east, south, west)   # That order
        self.gameover(north, east, south)  # Exactly the same as above
        self.gameover([north, south]) # North and South tie for first, the
            # other two players are tied for last
        self.gameover([north, south], [east, west]) # Exactly the same as above
        self.gameover([north, south], east, west) # North and South tie for
            # first, East is second, West is third
        self.gameover(None)   # everyone ties.

        That last is a special case. You can only use None in that way,
        as the sole argument meaning "all tie".
        """

        winlist = self.referee.parsewinners(winners)
        self.queueaction(self.referee.endgame, winlist)

    def gamecancelled(self, *winners):
        """gamecancelled(*winners) -> None

        Declare the game to have been aborted. You may still pass in
        *winners* (see the gameover() method), and they will be recorded,
        but with a notation which says that the game was incomplete.
        """
        
        winlist = self.referee.parsewinners(winners)
        self.queueaction(self.referee.endgame, winlist, True)
    
    # The methods below are stubs, meant to be overridden by the game class.

    def requestparticularseat(self, player, seat):
        """requestparticularseat(player, seat) -> None

        Validate a player's request for a seat. This method is called when
        the *player* requests permission to sit in *seat*. (A Player object
        and a Seat object, respectively. The Player may currently be
        standing, or sitting in some other Seat.)

        To permit the request, simply return. To forbid it, raise a
        FailureToken which translates to an appropriate error message.

        Conditions: called only in setup or suspended phase.
        
        Default: always permit.
        """
        pass
    
    def requestanyseat(self, player):
        """requestanyseat(player) -> Seat

        Fulfil a player's request for any seat. This method is called when
        the *player* (a Player object) requests permission to sit down, but
        doesn't specify a seat. (The player is currently standing.)

        You can return any Seat (including an occupied seat). To forbid the
        request, raise a FailureToken which translates to an appropriate
        error message. Or you can return None, which generates a
        'volity.no_seat' FailureToken, which translates to "No seats are
        available."

        Conditions: called only in setup phase.

        Default: return the first empty required seat. If none, return the
        first empty seat. If none, return None.
        """
        
        ls = [ seat for seat in self.getseatlist()
            if (seat.isrequired() and seat.isempty()) ]
        if (ls):
            return ls[0]
        ls = [ seat for seat in self.getseatlist()
            if ((not seat.isrequired()) and seat.isempty()) ]
        if (ls):
            return ls[0]
        return None

    def requestanyseatingame(self, player):
        """requestanyseatingame(player) -> Seat

        Fulfil a player's request for any seat (while game is suspended).
        
        Fulfil a player's request for any seat. This method is called when
        the *player* (a Player object) requests permission to sit down, but
        doesn't specify a seat. (The player is currently standing.)

        You can return any Seat (including an occupied seat). To forbid the
        request, raise a FailureToken which translates to an appropriate
        error message. Or you can return None, which generates a
        'volity.no_seat' FailureToken, which translates to "No seats are
        available."

        Conditions: called only in suspended phase.

        Default: return the first empty seat involved in the game. If none,
        return None.
        """
        
        ls = [ seat for seat in self.getseatlist()
            if (seat.isingame() and seat.isempty()) ]
        if (ls):
            return ls[0]
        return None

    def checkconfig(self):
        """checkconfig() -> None

        Validate the game configuration before starting. If your game allows
        the players to fool around with configuration options before playing,
        they may specify options that you can't handle. When a player hits
        "ready", this method is called, to give you a chance to reject the
        configuration.

        (Don't worry about player-seating arrangement in this method;
        handle that in checkseating().)

        To permit the "ready" request, simply return. To forbid it, raise
        a FailureToken which translates to an appropriate error message.
        
        Conditions: called only in setup phase.

        Default: always permit.
        """
        pass

    def checkseating(self):
        """checkseating() -> None

        Validate the player seating arrangement, before starting.

        The standard seating model (a list of seats, each of which is either
        required or optional) handles many cases. However, you may need to
        do more game-specific seat checking. If so, you can customize this
        method. When a player hits "ready", this method is called, to give
        you a chance to reject the configuration.

        To permit the "ready" request, simply return. To forbid it, raise
        a FailureToken which translates to an appropriate error message.
        
        Conditions: called only in setup phase.

        Default: if any required seats are empty, raise a 'volity.empty_seats'
        FailureToken, which translates to "The game cannot begin, because not
        all required seats have players." If all required seats are filled,
        permit.
        """
        
        ls = [ seat for seat in self.getseatlist()
            if seat.isrequired() and seat.isempty() ]
        if (ls):
            raise FailureToken('volity.empty_seats')

    def begingame(self):
        """begingame() -> None

        Handle the beginning of the game. Set up whatever state you need to.
        Send out RPCs to set up the client UIs.

        Conditions: called only in setup state.

        Default: do nothing.
        """
        pass

    def endgame(self, cancelled):
        """endgame() -> None

        Handle the end of the game. Clean up whatever state you need to.
        Send out RPCs to adjust the client UIs.

        You do not call this method. The referee invokes it after you've
        called gameover() or gamecancelled().

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

    def sendconfigstate(self, player):
        """sendconfigstate(player)

        Send the game configuration to a player. This happens whenever a
        new player joins the table -- which can be during game setup, while
        the game is in progress, or in any other state. It may also happen
        if a client gets confused and wants to reload the whole table state.

        The referee invokes this method in order to bring the player's client
        up to date with any game-specific configuration which has been done.
        Your implementation of this method should send out game.* RPCs
        which encode the game configuration.

        (Do not send information about the state of the actual game. If
        a game is in progress, sendgamestate() will be called, and you'll
        have the opportunity to do that there.)

        Conditions: can be called in any game state.

        Default: do nothing.
        """
        pass

    def sendgamestate(self, player, seat):
        """sendgamestate(player, seat) -> None
        
        Send the (in-progress) game state to a player. This happens whenever
        a new player joins the table, and the game is in progress. (Possibly
        suspended.) It may also happen if a client gets confused and wants
        to reload the whole table state.
        
        The referee invokes this method in order to bring the player's client
        up to date with the game. The *player* is sitting in *seat*; if
        standing, *seat* will be None. Your implementation of this method
        should send out game.* RPCs which encode the game state.

        (If your game has hidden information, remember to only send the
        information which is visible to *seat*! If *seat* is None, the player
        is standing, so only send the information which is visible to
        observers.)
        
        Conditions: can be called in active, disrupted, abandoned, or
        suspended state.

        Default: do nothing.
        """
        pass

    def destroy(self):
        """destroy() -> None

        Finalize anything you want to finalize in your class. This is
        called as the Referee is shutting down. The MUC room has already
        been destroyed, so there's no point in talking to the players.
        Just clean up the Game instance.

        Conditions: normally called only in setup state. However, abnormal
        shutdowns can occur at any time.

        Default: do nothing.
        """
        pass

class Seat:
    """Seat: Represents one Seat in your game.

    Your Game constructor must create one or more Seats. (Unless you want
    to have a seatless game, which is strange but technically legal.) You
    may not create Seats *except* in your Game constructor.

    Generally you will want to store game-specific information for each
    seat. To accomplish this, you may create a game-specific subclass of
    Seat, and create instances of that instead of generic Seat instances.

    Seat(game, id, required=True) -- constructor.

    The *game* is your Game instance; *id* is the seat ID you wish to create.
    If *required* is True (as it is by default), this will be a required seat.
    (The referee will not start the game until all required seats are filled.)

    Public methods:

    getplayerlist() -- get all the Players in this seat.
    getplayerhistory() -- get the JIDs of all players who have sat in this
        Seat during the current game.
    isempty() -- is anyone sitting here?
    isrequired() -- is this a required seat?
    isingame() -- is this Seat involved in the current game?
    send() -- send an RPC to the players in this seat.

    Significant fields:

    (Do not try to change the values of these fields. Use the publicly
    available methods, or don't touch them.)

    referee -- the Referee object of the seat's table.
    id -- the seat ID.
    required -- whether this is a required seat.
    ingame -- whether this seat is participating in the current game.
    playerlist -- the Players in this seat.
    playerhistory -- the JIDs of the players who have sat in this seat during
        the current game. (The *playerlist* is a subset of the *playerhistory*.
        The *playerhistory* may contain players who used to sit here, but
        changed seats during a game suspension.)
    """
    
    def __init__(self, game, id, required=True):
        """__init__(self, game, id, required=True)

        If you subclass the Seat class, your __init__() method must start:

            def __init__(self, game, id, required=True):
                volity.game.Seat.__init__(self, game, id, required)
                # ... set up whatever else you want

        (If all of your seats are required, you can leave off the
        *required* arguments.)
        """
        
        if (not isinstance(game, Game)):
            raise Exception, 'game argument must be a Game instance'
        if (not (type(id) in [str, unicode])):
            raise TypeError, 'id argument must be a string'
        ref = game.referee
        if (not ref):
            raise Exception, 'game has no referee value'

        self.referee = ref
        self.id = id
        self.required = bool(required)
        self.ingame = False
        self.playerlist = []
        self.playerhistory = {}

        # This will fail if the referee is already running. You must
        # create all your seats at __init__ time.
        self.referee.addseat(self)

    def getplayerlist(self):
        """getplayerlist() -> list

        Get all the Players in this seat. This includes all the Players
        present at the table, and also Players who are involved in the game
        but who have disconnected.
        """
        return list(self.playerlist)

    def getplayerhistory(self):
        """getplayerhistory() -> list

        Get the (bare) JIDs of all players who have sat in this Seat during
        the current game. (This may include players who used to sit here,
        but changed seats during a game suspension.)
        """
        return self.playerhistory.keys()

    def isempty(self):
        """isempty() -> bool

        Is anyone sitting here? (This is equivalent to checking whether
        playerlist() is empty.)
        """
        return not self.playerlist

    def isrequired(self):
        """isrequired() -> bool

        Is this a required seat?
        """
        return self.required

    def isingame(self):
        """isingame() -> bool

        Is this Seat involved in the current game? A seat is considered to
        be involved if it is required, or if anyone was sitting in it when
        the game started. (If the game is not in progress, this will return
        False.)
        """
        return self.ingame

    def send(self, methname, *args, **keywords):
        """send(methname, *args, **keywords)

        Send an RPC to the players in this seat. See the description of
        the sendseat() method in the Game class.
        """
        self.referee.game.sendseat(self, methname, *args, **keywords)
        
        
class ObjMethodOpset(jabber.rpc.Opset):
    """ObjMethodOpset: An Opset which handles a simple list of call names.

    You use the ObjMethodOpset by passing in an object which has methods
    whose names start with "rpc_". When an RPC comes in, its name is 
    searched for as an object method, and then the method is invoked as
    self.rpc_methodname(sender, *callargs). If there is no matching
    method which starts with "rpc_", a CallNotFound is raised.

    This class is used if you call setopset() with an object which is not
    an Opset. The object is stuck into an ObjMethodOpset, and that's how
    game.* RPCs are handled. (Most often, "the object" is your Game instance
    itself.)
    """
    
    def __init__(self, obj):
        self.object = obj
        
    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> value

        Accept, parse, and handle a call. The *sender* is a JID object.
        The *callname* is the method name (or fraction of a name, if
        the opset is nested inside another opset which parsed the
        namespace). The *callargs* are the RPC arguments, in the form
        of Python objects (ints, strings, etc).

        This method looks for an object method named 'rpc_'+*callname*.
        It invokes this (after calling
        precondition(sender, callname, '', *callargs). The third argument
        is empty because, in an ObjMethodOpset, there is no more of the
        callname to be matched after the method lookup.)
        """
        
        val = getattr(self.object, 'rpc_'+callname, None)
        if (not val):
            raise jabber.rpc.CallNotFound
        self.precondition(sender, callname, '', *callargs)
        return val(sender, *callargs)

# late imports
import referee

# Import the STATE_* constants into this module's namespace.
from referee import STATE_SETUP, STATE_ACTIVE, STATE_DISRUPTED, STATE_ABANDONED, STATE_SUSPENDED

