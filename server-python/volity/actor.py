import logging

import volent
from zymb import sched, jabber
from zymb.jabber import interface
from zymb.jabber import rpc
import zymb.jabber.dataform
import zymb.jabber.keepalive

REFEREE_DEFAULT_RPC_TIMEOUT = 5 ###

class Actor(volent.VolEntity):
    """Actor: The implementation of a Volity bot.

    An Actor is dedicated to playing a particular kind of game. However,
    there is no game-specific code in Actor. That is all in a subclass of
    the Bot class. (So an Actor contains a Bot subclass, just as a Referee
    contains a Game subclass.)
    
    Actor is a Jabber client (although it is not a subclass of the
    Zymb Jabber client agent; instead, it creates one as a subsidiary
    object). It is created by a Referee when someone requests a new bot
    at the table. The Actor acts as a normal player. It has a direct
    reference to the Referee, but mostly it works by sending out RPCs,
    the same as a real player.

    Actor(referee, jid, password, muc, resource, basenick, botclass) --
        constructor.

    All of the constructor arguments are passed in from the Referee. First
    is the referee itself. The *jid*, *password*, and *resource* are used
    to connect to the Jabber server. (The Actor connects with the same
    parameters as the original Referee, except for the *resource* string,
    which is unique for each Actor.) The *muc* is the JID of the Jabber
    Multi-User Chat room. The *basenick* is a suggestion for the Actor's
    room nickname (although it will take a different one if that is taken).
    And finally, *botclass* is the Python class which implements the game-
    specific code for the bot -- the brain, as it were.

    The Actor has no volition in sitting or standing. It waits for a player
    to seat it. Once it's seated, it is promiscuously ready -- it sends
    a ready() RPC after every table state change that could possibly leave
    it unready. Many of these RPCs will fail (e.g., if the seats aren't all
    taken yet) but it's the easiest way to make sure that the game will
    start as soon as all the humans are ready.
    
    Agent states and events:

    state 'start': Initial state. Start up the Jabber connection process.
        When Jabber is fully connected, jump to 'ready'.
    state 'ready': Jumps to state 'joining', which is where the interesting
        stuff starts.
    state 'joining': Watch for success or failure of the MUC join process.
        If there is a nickname conflict, try again with a different nickname.
        If the join succeeds, jump to 'running'.
    event 'tryjoin': Carry out one attempt to join the MUC.
    state 'running': At this point the Actor is considered to be ready.
        Send a send_state() RPC, requesting the table state.
    state 'end': The connection is closed.

    Significant fields:

    jid -- the JID by which this Referee is connected.
    conn -- the Jabber client agent.
    referee -- the Referee which created this Actor.
    bot -- the Bot object.
    refstate -- the referee state (STATE_SETUP, etc).
    parlor -- the Parlor which created that Referee.
    seat -- the Seat that the bot is sitting in, or None.
    seats -- dict mapping seat IDs to Seat objects.
    seatlist -- list of Seat objects in the game's order.

    Internal methods:

    beginwork() -- 'ready' state handler.
    tryjoin() -- 'tryjoin' event handler.
    endwork() -- 'end' state handler.
    handleseatlist() -- seat list handler.
    handlerequiredseatlist() -- required seat list handler.
    handlereceivestate() -- state recovery handler.
    tryready() -- tell the referee that we are ready to play.
    begingame() -- game-start handler.
    gamehasbegun() -- game-start handler.
    endgame() -- game-end handler.
    suspendgame() -- game-suspend handler.
    unsuspendgame() -- game-resume handler.
    sendref() -- send an RPC to the referee.
    defaultcallback() -- generic RPC callback, used to catch errors.
    handlemessage() -- handle a Jabber message stanza.
    handlepresence() -- handle a Jabber presence stanza.
    handlemucpresence() -- handle a Jabber MUC presence stanza.
    
    """
    
    logprefix = 'volity.actor'
    volityrole = 'bot'

    def __init__(self, referee, jid, password, muc, resource, basenick, botclass):
        self.logprefix = Actor.logprefix + '.' + resource
        self.referee = referee
        self.parlor = referee.parlor
        self.muc = muc
        self.mucnickcount = 0
        self.mucnick = None
        self.basenick = basenick
        self.resource = resource

        volent.VolEntity.__init__(self, jid, password, resource)

        self.refstate = STATE_SETUP
        self.seats = {}
        self.seatlist = []
        self.seat = None

        # Various Jabber handlers.
        
        self.addhandler('ready', self.beginwork)
        self.conn.adddispatcher(self.handlepresence, name='presence')
        self.conn.adddispatcher(self.handlemessage, name='message')
        self.addhandler('joining', self.perform, 'tryjoin')
        self.addhandler('tryjoin', self.tryjoin)

        # Set up the RPC replier.
        
        self.rpccli = self.conn.getservice('rpcclience')
        
        # Set up the RPC service
        
        rpcserv = self.conn.getservice('rpcservice')
        ops = rpcserv.getopset()
        dic = ops.getdict()
        dic['volity'] = BotVolityOpset(self)
        dic['admin'] = BotAdminOpset(self)
        self.gamewrapperopset = WrapGameOpset(self, rpc.Opset())
        dic['game'] = self.gamewrapperopset

        # Set up the bot instance.

        self.bot = botclass(self)
        assert self.bot.actor == self

        # Set up the disco service
        
        disco = self.conn.getservice('discoservice')
        info = disco.addinfo()
        
        info.addidentity('volity', 'bot', self.bot.getname())
        info.addfeature(interface.NS_CAPS)

        form = jabber.dataform.DataForm()
        form.addfield('volity-role', self.volityrole)
        info.setextendedinfo(form)

        # Set up the keepalive service

        sserv = self.referee.conn.getservice('keepaliveservice')
        if (sserv):
            serv = jabber.keepalive.KeepAliveService(sserv.getinterval())
            self.conn.addservice(serv)
            self.addhandler('ready', serv.start)
            self.log.warning('sending keepalive messages to self at interval of %d seconds', serv.getinterval())

    def beginwork(self):
        """beginwork() -> None

        The 'ready' state handler. Jumps to state 'joining', which is where
        the interesting stuff starts.
        """
        
        self.addhandler('end', self.endwork)
        self.jump('joining')

    def tryjoin(self):
        """tryjoin() -> None

        The 'tryjoin' event handler. Begins an attempt to join the MUC.

        This is fired off by the 'joining' event, and repeated if the MUC
        join does not succeed.
        """
        
        self.mucnick = interface.JID(jid=self.muc)

        # Our nickname is based on the basenick the referee gave us. But if
        # a previous attempt failed, we jigger it, to avoid repeating
        # collisions.
        nick = self.basenick
        self.mucnickcount += 1
        if (self.mucnickcount > 1):
            nick = nick + '-' + str(self.mucnickcount)
        self.mucnick.setresource(nick)
        
        self.log.info('joining muc: %s as %s', unicode(self.muc), unicode(self.mucnick))

        msg = interface.Node('presence',
            attrs={ 'to':unicode(self.mucnick) })
        msg.setchild('x', namespace=interface.NS_MUC)
        # We're not using the Zymb PresenceService, so we add the capabilities
        # tag manually.
        self.cappresencehook(msg)

        self.conn.send(msg, addid=False)
        
    def endwork(self):
        """endwork() -> None

        The 'end' state handler. This does its best to blow away all of
        the actor's member fields, so that everything can be garbage-
        collected efficiently.
        """
        
        self.bot.destroy()
        self.bot.actor = None
        self.bot = None
        self.referee = None
        self.parlor = None

        self.rpccli = None
        self.gamewrapperopset = None
        self.seat = None
        self.seats = None
        self.seatlist = None
        self.muc = None

    def handleseatlist(self, ls):
        """handleseatlist(seats) -> None

        Seat list handler. This accepts the list of game seats as sent by
        the referee. It creates Seat objects, and fills in the seats dict
        and the seatlist array.
        """
        
        if (self.seatlist):
            # If we already have a list of seats, this should be an identical
            # list. Let's check that, though.

            if (len(ls) != len(self.seatlist)):
                self.log.error('new set_seats list has different length from original set_seats list')
                return

            for id in ls:
                if (not self.seats.has_key(id)):
                    self.log.error('new set_seats list does not match original set_seats list')
                    return

            return

        # Create our seats list.
        for id in ls:
            seat = Seat(self, id)
            self.seats[id] = seat
            self.seatlist.append(seat)
        
    def handlerequiredseatlist(self, ls):
        """handlerequiredseatlist(seats) -> None

        Required seat list handler. This marks the given seats as required,
        and marks all other seats as optional.
        """
        
        for seat in self.seatlist:
            seat.required = (seat.id in ls)

    def handlereceivestate(self, dic):
        """handlereceivestate(dic) -> None

        State-recovery handler. (Game-specific work is handled by the
        bot's receivestate() method.)
        """

        val = dic.get('state')
        if (val == STATE_SETUP):
            self.refstate = STATE_SETUP
        if (val == STATE_SUSPENDED):
            self.refstate = STATE_SUSPENDED
        if (val in [ STATE_ACTIVE, STATE_DISRUPTED, STATE_ABANDONED ]):
            self.refstate = STATE_ACTIVE
        self.bot.receivestate()

    def tryready(self):
        """tryready() -> None

        Tell the referee that we are ready to play. This is called after
        every received RPC that could leave us unready. (That is, after most
        of the volity.* namespace RPCs.) If we are seated, we send out a
        ready() RPC. It may fail, but if we can possibly be ready, we should
        be ready. Bots exist to play.

        One exception: if we have just stood up, we block ready-sending until
        the stand command is actually confirmed. Otherwise we could get into
        race conditions, where we are in the process of standing up but
        accidentally go ready anyway.
        """
        
        if (self.seat):
            self.queueaction(self.sendref, 'volity.ready')

    def begingame(self):
        """begingame() -> None

        Game-start handler. Sets up all the internal state which we will
        use to track the game. (Game-specific setup is handled by the
        bot's begingame() method.)
        """
        
        self.log.info('game beginning!')
        self.refstate = STATE_ACTIVE
        self.bot.begingame()

    def gamehasbegun(self):
        """gamehasbegun() -> None

        Game-start handler. Called during state recovery if a game is in
        progress. (Game-specific setup is handled by the bot's gamehasbegun()
        method.)
        """
        self.bot.gamehasbegun()

    def endgame(self):
        """endgame() -> None

        Game-end handler. (Game-specific shutdown is handled by the bot's
        endgame() method.)
        """
        self.refstate = STATE_SETUP
        self.bot.endgame()

    def suspendgame(self):
        """suspendgame() -> None

        Game-suspension handler. (Game-specific work is handled by the
        bot's suspendgame() method.)
        """
        self.refstate = STATE_SUSPENDED
        self.bot.suspendgame()

    def unsuspendgame(self):
        """unsuspendgame() -> None

        Game-resumption handler. (Game-specific work is handled by the
        bot's unsuspendgame() method.)
        """
        self.refstate = STATE_ACTIVE
        self.bot.unsuspendgame()

    def sendref(self, methname, *methargs, **keywords):
        """sendref(methname, *methargs, **keywords) -> None

        Send an RPC to the referee.

        The *methname* and *methargs* describe the RPC. The *keywords* may
        contain either or both of:

            timeout: How long to wait (in seconds) before considering the
                RPC to have failed.
            callback: A deferral callback to invoke when the outcome of
                the RPC is known. See the defaultcallback() method for an
                example of the callback model.
        """
        
        op = keywords.pop('callback', self.defaultcallback)
        if (not keywords.has_key('timeout')):
            keywords['timeout'] = REFEREE_DEFAULT_RPC_TIMEOUT
            
        methargs = [ self.referee.resolvemetharg(val, self.bot.makerpcvalue)
            for val in methargs ]
        
        self.rpccli.send(op, self.referee.jid,
            methname, *methargs, **keywords)

    def defaultcallback(self, tup):
        """defaultcallback(tup) -> None

        Generic RPC callback, used to catch errors.

        When an RPC completes (in any sense), the Zymb RPC-sending service
        calls a completion routine. By default, it's this one. The callback
        invokes sched.Deferred.extract(tup) on its *tup* argument to extract
        the RPC outcome. This may return a value, or one of the following
        exceptions might be raised:

            TimeoutException: The RPC timed out.
            RPCFault: The RPC response was an RPC fault.
            StanzaError: The response was a Jabber stanza-level error.
            Exception: Something else went wrong.

        The defaultcallback() method logs all exceptions, ignores all normal
        RPC responses, and that's all it does.
        """
        
        try:
            res = sched.Deferred.extract(tup)
        except sched.TimeoutException, ex:
            self.log.warning('rpc timed out: %s', ex)
        except rpc.RPCFault, ex:
            self.log.warning('rpc returned fault: %s', ex)
        except interface.StanzaError, ex:
            self.log.warning('rpc returned stanza error: %s', ex)
        except Exception, ex:
            self.log.warning('rpc raised exception', exc_info=True)
            
    # ---- network message handlers
        
    def handlemessage(self, msg):
        """handlemessage(msg) -> <stanza outcome>

        Handle a Jabber message stanza. The Actor does not react to messages,
        so this just raises StanzaHandled to end processing for the stanza.
        """
        
        if (self.log.isEnabledFor(logging.DEBUG)):
            self.log.info('received message')
            #self.log.debug('received message:\n%s', msg.serialize(True))
        raise interface.StanzaHandled()

    def handlepresence(self, msg):
        """handlepresence(msg) -> <stanza outcome>

        Handle a Jabber presence stanza. If it signals a failure to join the
        MUC, perform 'tryjoin' again. If it comes from the actor's MUC room,
        it is passed along to handlemucpresence(). Otherwise, it is ignored.
        """
        
        typestr = msg.getattr('type', '')
                
        fromstr = msg.getattr('from')
        if (fromstr):
            jid = interface.JID(fromstr)
        else:
            jid = None

        if (not (jid and self.jid.barematch(jid))):
            if (self.log.isEnabledFor(logging.DEBUG)):
                self.log.debug('received presence:\n%s', msg.serialize(True))
            else:
                self.log.info('received presence \'%s\' from %s', typestr, unicode(jid))

        if (typestr == 'error'
            and msg.getchild('x', namespace=interface.NS_MUC)
            and self.state == 'joining'):
            self.log.info('mucnick %s failed; retrying', unicode(self.mucnick))
            self.perform('tryjoin')
            raise interface.StanzaHandled()

        if (jid
            and jid.getnode() == self.referee.resource
            and jid.getdomain() == self.muc.getdomain()):
            self.handlemucpresence(typestr, jid.getresource(), msg)
            
        raise interface.StanzaHandled()

    def handlemucpresence(self, typestr, resource, msg):
        """handlemucpresence(typestr, resource, msg) -> None

        Handle a Jabber MUC presence stanza. The *typestr* is the presence
        type (with '' representing default "I'm here" presence). The
        *resource* is the resource part of the MUC JID -- that is to say,
        the MUC nick. The *msg* is the full presence stanza, which we
        need for further analysis.

        This watches for one condition: the successful joining of the MUC.
        When it sees that, it jumps to 'running', and sends off the request
        for the table state.
        """
        
        if (typestr == '' and resource == self.referee.mucnick.getresource()
            and self.state == 'joining'):
            self.log.info('detected referee; requesting state')
            self.jump('running')
            self.queueaction(self.sendref, 'volity.send_state')

            
class WrapGameOpset(rpc.WrapperOpset):
    """WrapGameOpset: An Opset which wraps all RPCs in the game.* namespace.

    This Opset passes RPCs on to the Bot's chosen opset. However, if an
    RPC is not found, the RPC fault is blocked; instead, this just returns
    success.

    This Opset provides checking of argument types. The Opset also checks a
    few universal conditions: only the referee can send RPCs, and only after
    the Actor is ready to receive them.
    """
    
    def __init__(self, act, subopset):
        rpc.WrapperOpset.__init__(self, subopset)
        self.actor = act
        self.referee = self.actor.referee

    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs)

        Check the sender before an RPC handler. Only referees can send RPCs
        to a bot.
        """
        
        if (self.actor.state != 'running'):
            raise rpc.RPCFault(609, 'bot not ready for RPCs')

        if (sender != self.referee.jid):
            raise rpc.RPCFault(607, 'sender is not referee')
            
    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> <rpc outcome>

        Invoke an RPC. This is invoked by the Zymb RPC-handling service.
        The CallNotFound exception is trapped and turned into a silent
        success.
        """
        
        try:
            val = rpc.WrapperOpset.__call__(self, sender, callname, *callargs)
            return self.referee.resolvemetharg(val,
                self.actor.bot.makerpcvalue)
        except rpc.CallNotFound:
            # unimplemented methods are assumed to return quietly.
            return None
        except rpc.RPCResponse:
            raise
        except rpc.RPCFault:
            raise
        except interface.StanzaError:
            raise
        except Exception, ex:
            st = str(ex.__class__) + ': ' + str(ex)
            self.actor.log.error(
                'uncaught exception in game opset: %s (from %s)',
                unicode(sender), callname,
                exc_info=True)
            raise rpc.RPCFault(608, st)
            
class BotVolityOpset(rpc.MethodOpset):
    """BotVolityOpset: An Opset which handles all RPCs in the volity.*
    namespace.

    The methods of this Opset invoke appropriate methods in the Actor.
    However, if an RPC is not found, no RPC fault is generated; instead,
    this just returns success. (In other words, not all volity.* RPCs
    are listed below.)

    The Opset also checks a few universal conditions: only the referee can
    send RPCs, and only after the Actor is ready to receive them.

    BotVolityOpset(act) -- constructor.

    The *act* is the Actor to which this Opset is attached.

    Methods:

    precondition() -- checks authorization and argument types before an
    RPC handler.

    Handler methods:

    rpc_player_sat() -- notice that a player has sat (or changed seats).
    rpc_player_stood() -- notice that a player has stood.
    rpc_player_unready() -- notice that a player is unready.
    rpc_kill_game() -- notice that the kill-game flag has changed.
    rpc_show_table() -- notice that the show-table flag has changed.
    rpc_record_games() -- notice that record-games flag has changed.
    rpc_language() -- notice that the table language has changed.
    rpc_start_game() -- notice that the game has started.
    rpc_end_game() -- notice that the game has ended.
    rpc_suspend_game() -- notice that the game has been suspended.
    rpc_resume_game() -- notice that the game has been resumed.
    """
    
    def __init__(self, act):
        self.actor = act
        self.referee = self.actor.referee
                
    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs)

        Check the sender before an RPC handler. Only referees can send RPCs
        to a bot.
        """
        
        if (self.actor.state != 'running'):
            raise rpc.RPCFault(609, 'bot not ready for RPCs')

        if (sender != self.referee.jid):
            raise rpc.RPCFault(607, 'sender is not referee')
            
    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> <rpc outcome>

        Invoke an RPC. This is invoked by the Zymb RPC-handling service.
        The CallNotFound exception is trapped and turned into a silent
        success.
        """
        
        try:
            return rpc.MethodOpset.__call__(self, sender, callname, *callargs)
        except rpc.CallNotFound:
            # unimplemented methods are assumed to return quietly.
            return None
        except rpc.RPCResponse:
            raise
        except rpc.RPCFault:
            raise
        except interface.StanzaError:
            raise
        except Exception, ex:
            st = str(ex.__class__) + ': ' + str(ex)
            self.actor.log.error(
                'uncaught exception in volity opset: %s (from %s)',
                unicode(sender), callname,
                exc_info=True)
            raise rpc.RPCFault(608, st)

    def rpc_receive_state(self, sender, *args):
        if (len(args) >= 1):
            self.actor.handlereceivestate(args[0])

    def rpc_seat_list(self, sender, *args):
        if (len(args) >= 1):
            self.actor.handleseatlist(args[0])
            
    def rpc_required_seat_list(self, sender, *args):
        if (len(args) >= 1):
            self.actor.handlerequiredseatlist(args[0])
            
    def rpc_player_sat(self, sender, *args):
        if (len(args) >= 2):
            jid = args[0]
            seatid = args[1]
            for seat in self.actor.seatlist:
                if (jid in seat.players):
                    seat.players.remove(jid)
            seat = self.actor.seats.get(seatid)
            if (seat):
                seat.players.append(jid)
                if (jid == self.actor.jid):
                    self.actor.seat = seat
        self.actor.tryready()
            
    def rpc_player_stood(self, sender, *args):
        if (len(args) >= 1):
            jid = args[0]
            for seat in self.actor.seatlist:
                if (jid in seat.players):
                    seat.players.remove(jid)
            if (jid == self.actor.jid):
                self.actor.seat = None
        self.actor.tryready()
            
    def rpc_player_unready(self, sender, *args):
        if (len(args) >= 1 and (self.actor.jid == args[0])):
            self.actor.tryready()
            
    def rpc_kill_game(self, sender, *args):
        self.actor.tryready()
            
    def rpc_show_table(self, sender, *args):
        self.actor.tryready()
            
    def rpc_record_games(self, sender, *args):
        self.actor.tryready()
            
    def rpc_language(self, sender, *args):
        self.actor.tryready()

    def rpc_start_game(self, sender, *args):
        self.actor.queueaction(self.actor.begingame)
            
    def rpc_game_has_started(self, sender, *args):
        self.actor.queueaction(self.actor.gamehasbegun)
            
    def rpc_end_game(self, sender, *args):
        self.actor.queueaction(self.actor.endgame)
        self.actor.tryready()
            
    def rpc_suspend_game(self, sender, *args):
        self.actor.queueaction(self.actor.suspendgame)
        self.actor.tryready()
            
    def rpc_resume_game(self, sender, *args):
        self.actor.queueaction(self.actor.unsuspendgame)
            
class BotAdminOpset(rpc.MethodOpset):
    """BotAdminOpset: The Opset which responds to admin.* namespace
    RPCs.

    BotAdminOpset(act) -- constructor.

    The *act* is the Actor to which this Opset is attached.

    Methods:

    precondition() -- checks authorization before an RPC handler.

    Handler methods:

    rpc_status() -- return assorted status information.
    rpc_shutdown() -- immediately shut down this Actor.
    """
    
    def __init__(self, act):
        self.actor = act
        self.referee = self.actor.referee

    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs)

        Checks authorization before an RPC handler. Only the admin JID
        is permitted to send admin.* namespace RPCs. If no admin JID was
        set, then all admin.* RPCs are rejected.
        """
        
        if (not ref.actor.parlor.isadminjid(sender)):
            raise interface.StanzaNotAuthorized('admin operations are restricted')
        self.actor.log.warning('admin command from <%s>: %s %s',
            unicode(sender), namehead, unicode(callargs))

    def rpc_status(self, sender, *args):
        """rpc_status() -> dict

        Return assorted status information. This returns a Jabber-RPC struct
        containing these fields:

            referee: The JID of the bot's referee.
            refstate: The bot's view of the referee state.
            seat: The seat ID the bot is sitting in, or ''.
            gameseatlist: The list of seats which are involved in the current
                game, if there is one.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'status: no arguments')
        dic = {}
        dic['referee'] = unicode(self.referee.jid)
        dic['refstate'] = self.actor.refstate
        dic['seat'] = ''
        if (self.actor.seat):
            dic['seat'] = self.actor.seat.id
        ls = self.actor.bot.getgameseatlist()
        if (ls != None):
            dic['gameseatlist'] = [ seat.id for seat in ls ]

        return dic

    def rpc_players(self, sender, *args):
        """rpc_players() -> dict

        Return the actor's view of the players in each seat.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'players: no arguments')
        dic = {}
        for seat in self.actor.seatlist:
            dic[seat.id] = seat.players
        return dic
        
    def rpc_shutdown(self, sender, *args):
        """rpc_shutdown() -> str

        Immediately shut down this Actor.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'shutdown: no arguments')
        self.actor.queueaction(self.actor.stop)
        return 'stopping actor'
        
class Seat:
    """Seat: Represents the Actor's view of a table seat.

    Seat(act, id) -- constructor.

    The *act* is the Actor which owns this seat; *id* is the seat ID.
    """
    
    def __init__(self, act, id):
        if (not isinstance(act, Actor)):
            raise Exception, 'act argument must be a Actor instance'
        if (not (type(id) in [str, unicode])):
            raise TypeError, 'id argument must be a string'

        self.actor = act
        self.id = id
        self.required = False
        self.players = []
        
    def __repr__(self):
        return '<Seat \'' + self.id + '\'>'

    def __unicode__(self):
        return self.id
        
    def __str__(self):
        return str(self.id)
        
# late imports
import bot
from referee import STATE_SETUP, STATE_ACTIVE, STATE_DISRUPTED
from referee import STATE_ABANDONED, STATE_SUSPENDED
