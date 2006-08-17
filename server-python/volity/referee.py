import os
import time
import logging
from zymb import sched, jabber
from zymb.jabber import interface
from zymb.jabber import rpc
import zymb.jabber.dataform
import zymb.jabber.keepalive

#### playeraddexternalbot should parse the return value for volity.ok
#### or volity.TOKEN, really

# Constants for the five referee states.
STATE_SETUP     = intern('setup')
STATE_ACTIVE    = intern('active')
STATE_DISRUPTED = intern('disrupted')
STATE_ABANDONED = intern('abandoned')
STATE_SUSPENDED = intern('suspended')
STATE_AUTHORIZING = intern('authorizing')

# Constants for the presencechange() game method.
ACTION_JOINED   = intern('joined')
ACTION_LEFT     = intern('left')
ACTION_NICKNAME = intern('nickname')

# slightly late import
import volent

CLIENT_DEFAULT_RPC_TIMEOUT = 30
CLIENT_INVITATION_RPC_TIMEOUT = 30
BOT_STARTUP_TIMEOUT = 60
DEAD_CONFIG_TIMEOUT = 90
ABANDONED_TIMEOUT = 3*60

class Referee(volent.VolEntity):
    """Referee: The implementation of a Volity referee.

    A Referee is dedicated to running a particular kind of game. However,
    there is no game-specific code in Referee (nor is it subclassed for
    a particular game). Instead, the game-specific code is in a subclass
    of the Game class. The game implementor creates this class; the Parlor
    loads its module; and the Referee instantiates it (at constructor time).
    The Game subclass handles all game.* RPCs, and provides assorted other
    methods which the Referee calls to decide what to do.

    Referee is a Jabber client (although it is not a subclass of the
    Zymb Jabber client agent; instead, it creates one as a subsidiary
    object). It is created by a Parlor when someone requests a new game
    table. The Referee is responsible for managing one game table, at which
    a bunch of players play one game. Or rather, a sequence of games. (When
    a game finishes, the players have the option of setting up a new one at
    the same table.)

    Referee(parlor, jid, password, resource, muc) -- constructor.

    All of the constructor arguments are passed in from the Parlor. First
    is the parlor itself. The *jid*, *password*, and *resource* are used
    to connect to the Jabber server. (The Referee connects with the same
    parameters as the original Parlor, except for the *resource* string,
    which is unique for each Referee.) The *muc* is the JID of the Jabber
    Multi-User Chat room (which the Referee is responsible for creating).
    
    Agent states and events:

    state 'start': Initial state. Start up the Jabber connection process.
        When Jabber is fully connected, jump to 'ready'.
    state 'ready': Send out initial presence. Do a disco query of the MUC
        host, to see if it's up. Request the creation of the MUC room,
        and then jump to 'configuring'.
    state 'configuring': Complete the MUC configuration process. Jump to
        'running'.
    state 'running': At this point the Referee is considered to be complete.
        It begins watching for game activity.
    state 'end': The connection is closed.
    
    Public methods:

    start() -- begin activity. (inherited)
    stop() -- stop activity. (inherited)
    isadminjid() -- check whether the given JID is an administrator JID.

    Significant fields:

    jid -- the JID by which this Referee is connected.
    conn -- the Jabber client agent.
    parlor -- the Parlor which created this Referee.
    game -- the Game object.
    refstate -- the referee state (STATE_SETUP, etc).
    players -- dict mapping players' real JIDs to Player objects.
    playernicks -- dict mapping players' MUC nicknames to Player objects.
    seats -- dict mapping seat IDs to Seat objects.
    seatlist -- list of Seat objects in the game's order.
    gameseatlist -- during a game, the list of Seats involved in the game.
        (A subset of seatlist.)

    Internal methods:

    addseat() -- add a seat to the game's permanent list.
    setseatsrequired() -- mark certain seats as required or optional.
    sendone() -- send an RPC to a single player.
    sendall() -- send an RPC to all players at the table.
    sendbookkeeper() -- send an RPC to the Volity bookkeeper.
    defaultcallback() -- generic RPC callback, used to catch errors.
    handlemessage() -- handle a Jabber message stanza.
    handlepresence() -- handle a Jabber presence stanza.
    handlemucpresence() -- handle a Jabber MUC presence stanza.
    beginwork() -- 'ready' state handler.
    gotquerymuc() -- disco query callback.
    configuremuc() -- begin the configuration of a MUC room.
    handle_stanza_configuremuc() -- MUC configuration callback.
    buildconfigmuc() -- construct a form to configure a MUC room.
    handle_stanza_configuremucresult() -- second MUC configuration callback.
    checktimersetting() -- check whether the Referee needs a state timer.
    settimer() -- set a state timer.
    deadconfigtimer() -- abandoned-in-configuration timer callback.
    abandonedtimer() -- abandoned-during-game timer callback.
    playerarrived() -- handle a player joining the table.
    playerleft() -- handle a player leaving the table.
    playersit() -- handle a player sit request.
    playerstand() -- handle a player stand request.
    playerready() -- handle a player ready request.
    playerunready() -- handle a player unready request.
    playeraddbot() -- handle a player request for a new local bot.
    botready() -- callback invoked when a bot is successfully
        (or unsuccessfully) created.
    playeraddexternalbot() -- handle a player request for a new factory bot.
    gotnewbotreply() -- callback invoked when a new_bot RPC is replied to.
    externalbotready() -- callback which replies to the original add_bot
        request.
    playerremovebot() -- handle a player request to remove a bot.
    playersuspend() -- handle a player game-suspend request.
    playerinvite() -- handle a player request to invite another player to the
        table.
    gotinvitereply() -- callback invoked when an invitation is replied to.
    playerinvitecont() -- callback which replies to the original invite
        request.
    configsetlanguage() -- handle a player request to change the table
        language.
    configrecordgames() -- handle a player request to change the game-record
        flag.
    configshowtable() -- handle a player request to change the show-table
        flag.
    configkillgame() -- handle a player request to change the kill-game flag.
    unreadyall() -- mark all players as unready.
    sendfullstate() -- send the complete table state to a player.
    removenonliveplayers() -- remove all players who are not connected.
    reportsit() -- report a player sitting.
    reportstand() -- report a player standing.
    reportreadiness() -- report a player changing readiness.
    reportunreadylist() -- report several players becoming unready.
    announce() -- yell a message to everyone at the table.
    updatediscoinfo() -- update and return the disco-info form.
    parsewinners() -- turn a list of game seats (in winning order) into
        a standardized winners list (a list of lists of lists of players).
    authorizinggame() -- game-authorization handler.
    begingame() -- game-start handler.
    endgame() -- game-end handler.
    suspendgame() -- game-suspend handler.
    unsuspendgame() -- game-resume handler.
    endwork() -- 'end' state handler.

    """
    
    logprefix = 'volity.referee'
    volityrole = 'referee'

    maxmucusers = 60

    def __init__(self, parlor, jid, password, resource, muc):
        self.logprefix = Referee.logprefix + '.' + resource
        self.parlor = parlor
        self.resource = resource
        self.muc = muc
        self.mucnick = interface.JID(jid=muc)
        self.mucnick.setresource('referee')
        self.jabbersecurity = parlor.jabbersecurity
        
        volent.VolEntity.__init__(self, jid, password, resource,
            secure=self.jabbersecurity)

        # Lots of internal state to set up.

        self.refstate = STATE_SETUP
        # prevrefstate is only used during STATE_AUTHORIZING
        self.prevrefstate = None
        
        self.startuptime = time.time()
        self.activitytime = None
        self.gamescompleted = 0
        
        self.players = {}
        self.playernicks = {}

        self.seats = {}
        self.seatlist = []
        self.gameseatlist = None
        self.seatsetupphase = True

        self.timerreason = None
        self.timeraction = None

        self.discomuctries = 0
        self.mucrunning = False
        self.mucshutdownreason = 'The referee has shut down.'

        self.botcounter = 1
        
        # Various Jabber handlers.
        
        self.addhandler('ready', self.beginwork)
        self.conn.adddispatcher(self.handlepresence, name='presence')
        self.conn.adddispatcher(self.handlemessage, name='message')

        # Publicly-visible table state.

        self.language = 'en'
        self.recordgames = True
        self.showtable = self.parlor.visibility
        self.killgame = False

        # Set up the RPC replier.
        
        self.rpccli = self.conn.getservice('rpcclience')
        
        rpcserv = self.conn.getservice('rpcservice')
        assert isinstance(rpcserv.getopset(), volent.ClientRPCWrapperOpset)
        ops = rpcserv.getopset().getopset()
        dic = ops.getdict()
        dic['volity'] = RefVolityOpset(self)
        dic['admin'] = RefAdminOpset(self)
        self.gamewrapperopset = WrapGameOpset(self, rpc.Opset())
        dic['game'] = self.gamewrapperopset

        # Set up the game instance.

        self.game = self.parlor.gameclass(self)
        assert self.game.referee == self
        self.seatsetupphase = False
        if (not self.seatlist):
            raise Exception('the game must create at least one seat')

        if (self.game.defaultlanguage != None):
            self.language = bool(self.game.defaultlanguage)
        if (self.game.defaultrecordgames != None):
            self.recordgames = bool(self.game.defaultrecordgames)
        if (self.game.defaultshowtable != None):
            if (not bool(self.game.defaultshowtable)):
                self.showtable = False
        ### let parlor config override these? More?

        # Set up the disco replier.

        disco = self.conn.getservice('discoservice')
        
        # assumes resource didn't change
        form = jabber.dataform.DataForm()
        form.addfield('parlor', unicode(self.parlor.jid))
        form.addfield('volity-role', self.volityrole)
        form.addfield('table', unicode(self.muc))
        # The following are adjusted in updatediscoinfo().
        form.addfield('state', '')
        form.addfield('players', '')
        form.addfield('max-players', '')
        form.addfield('language', '')
        form.addfield('recorded', '')
        form.addfield('visible', '')

        ident = ('volity', 'referee', self.parlor.gamename)
        info = jabber.disco.DiscoInfo([ident], None, form)
        info.addfeature(interface.NS_DISCO_INFO)
        info.addfeature(interface.NS_DISCO_ITEMS)
        info.addfeature(interface.NS_CAPS)
        self.discoinfo = info
        
        disco.addinfo(None, self.updatediscoinfo)
        disco.additems()

        # Set up the keepalive service

        sserv = self.parlor.conn.getservice('keepaliveservice')
        if (sserv):
            serv = jabber.keepalive.KeepAliveService(sserv.getinterval())
            self.conn.addservice(serv)
            self.addhandler('ready', serv.start)
            self.log.warning('sending keepalive messages to self at interval of %d seconds', serv.getinterval())
            
        
    # ---- called by Game base methods

    def addseat(self, seat):
        """addseat(seat) -> None

        Add a seat to the game's permanent list. This can only be done
        during the Game constructor.
        """
        
        if (not self.seatsetupphase):
            raise Exception('you cannot add new seats after the referee has started up')
            
        if (self.seats.has_key(seat.id)):
            raise ValueError('seat %s already exists' % seat.id)
        self.seats[seat.id] = seat
        self.seatlist.append(seat)

    def setseatsrequired(self, setseats=[], clearseats=[]):
        """setseatsrequired(setseats=[], clearseats=[]) -> None

        Mark certain seats as required or optional. The seats listed in
        *setseats* become required; the ones listed in *clearseats* become
        optional. If this results in any seats actually changing state,
        required_seat_list RPCs are sent.
        """
        
        if (self.refstate != STATE_SETUP):
            raise Exception('cannot change seat requirements after game setup')
            
        setls = [ seat for seat in setseats if not seat.required ]
        clrls = [ seat for seat in clearseats if seat.required ]
        if ((not setls) and (not clrls)):
            return

        for seat in setls:
            seat.required = True
        for seat in clrls:
            seat.required = False

        if (not self.seatsetupphase):
            ls = [ seat.id for seat in self.seatlist if seat.required ]
            self.game.sendtable('volity.required_seat_list', ls)

    def sendone(self, player, methname, *methargs, **keywords):
        """sendone(player, methname, *methargs, **keywords) -> None

        Send an RPC to a single player.

        The *player* is the Player object to send to. The *methname* and
        *methargs* describe the RPC. The *keywords* may contain either or
        both of:

            timeout: How long to wait (in seconds) before considering the
                RPC to have failed.
            callback: A deferral callback to invoke when the outcome of
                the RPC is known. See the defaultcallback() method for an
                example of the callback model.
        """
        
        op = keywords.pop('callback', self.defaultcallback)
        if (not keywords.has_key('timeout')):
            keywords['timeout'] = CLIENT_DEFAULT_RPC_TIMEOUT
            
        if (not (player.live and player.aware)):
            return

        methargs = [ self.resolvemetharg(val, self.game.makerpcvalue)
            for val in methargs ]
        
        self.rpccli.send(op, player.jidstr,
            methname, *methargs, **keywords)

    def sendall(self, methname, *methargs, **keywords):
        """sendall(methname, *methargs, **keywords) -> None

        Send an RPC to all players at the table.

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
            keywords['timeout'] = CLIENT_DEFAULT_RPC_TIMEOUT
            
        methargs = [ self.resolvemetharg(val, self.game.makerpcvalue)
            for val in methargs ]
        
        for player in self.players.values():
            if (player.live and player.aware):
                self.rpccli.send(op, player.jidstr,
                    methname, *methargs, **keywords)

    def sendbookkeeper(self, methname, *methargs, **keywords):
        """sendbookkeeper(methname, *methargs, **keywords) -> None

        Send an RPC to the Volity bookkeeper.

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
            keywords['timeout'] = CLIENT_DEFAULT_RPC_TIMEOUT

        # Game-specific types don't need to be transformed. Besides, it
        # would be a security hole, to the extent that Python has such
        # things.
        methargs = [ self.resolvemetharg(val) for val in methargs ]

        destjid = self.parlor.bookkeeperjid
        self.rpccli.send(op, destjid, methname, *methargs, **keywords)

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

        Handle a Jabber message stanza. The Referee does not react to messages,
        so this just raises StanzaHandled to end processing for the stanza.
        """
        
        if (self.log.isEnabledFor(logging.DEBUG)):
            self.log.info('received message')
            #self.log.debug('received message:\n%s', msg.serialize(True))

        jid = None
        bodystr = ''
            
        fromstr = msg.getattr('from')
        if (fromstr):
            jid = interface.JID(fromstr)
            
        # Most of the game APIs deal with real JIDs, so we translate a MUC
        # JID to a real JID if necessary.
        if (jid and self.muc.barematch(jid)):
            nick = jid.getresource()
            if (self.playernicks.has_key(nick)):
                pla = self.playernicks[nick]
                jid = pla.jid

        body = msg.getchild('body')
        if (body):
            bodystr = body.getdata()

        if (jid):
            self.queueaction(self.game.acceptmessage, jid, bodystr, msg)
        raise interface.StanzaHandled()

    def handlepresence(self, msg):
        """handlepresence(msg) -> <stanza outcome>

        Handle a Jabber presence stanza. If it comes from the referee's MUC
        room, it is passed along to handlemucpresence(). Otherwise, it is
        ignored.
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

        if (jid
            and jid.getnode() == self.resource
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

        MUC presence is interesting in two cases. First, when the referee
        is first creating the MUC, it watches for a status code 201, which
        indicates that it's time to configure the new MUC room.

        Second, as players join and leave the MUC room, the referee must
        track them. It calls playerarrived() and playerleft() when it
        detects these events.
        """
        
        if (resource == 'referee' and self.state == 'configuring'):
            xnod = msg.getchild('x', namespace=interface.NS_MUC_USER)
            if (xnod):
                statnod = xnod.getchild('status')
                if (statnod and statnod.getattr('code') == '201'):
                    self.queueaction(self.configuremuc)

        if (resource != 'referee'):
            xnod = msg.getchild('x', namespace=interface.NS_MUC_USER)
            if (xnod):
                inod = xnod.getchild('item')
                jidstr = inod.getattr('jid')
                if (jidstr):
                    jid = interface.JID(jidstr)
                    if (jid == self.jid):
                        self.log.error('our own JID %s showed up with resource "%s"', jidstr, resource)
                    if (typestr == ''):
                        isbot = False
                        cnod = msg.getchild('c', namespace=interface.NS_CAPS)
                        if (cnod
                            and cnod.getattr('node') == volent.VOLITY_CAPS_URI
                            and cnod.getattr('ext') == 'bot'):
                            isbot = True
                        if (jid.barematch(self.jid)
                            and self.parlor.actors.has_key(jid.getresource())):
                            isbot = True
                        self.playerarrived(jid, resource, isbot)
                        self.activitytime = time.time()
                    if (typestr == 'unavailable'):
                        self.playerleft(jid, resource)
                        self.activitytime = time.time()

    def beginwork(self):
        """beginwork() -> None

        The 'ready' state handler. Send out initial presence. Do a disco
        query of the MUC host, to see if it's up.
        """
        
        self.addhandler('end', self.endwork)    

        serv = self.conn.getservice('discoclience')
        serv.queryinfo(self.gotquerymuc, self.muc.getdomain(), timeout=30)

    def gotquerymuc(self, tup):
        """gotquerymuc(tup) -> None

        Disco query callback. If the disco query failed, try again. (But
        after three failures, give up and shut down the referee.) If the
        query succeeds, send a command to create the new MUC room, and
        jump to state 'configuring'.
        """
        
        try:
            res = sched.Deferred.extract(tup)
        except Exception, ex:
            self.log.error('unable to query MUC host %s: %s', self.muc.getdomain(), ex)
            self.discomuctries += 1
            if (self.discomuctries < 3):
                # Try again
                serv = self.conn.getservice('discoclience')
                serv.queryinfo(self.gotquerymuc, self.muc.getdomain(), timeout=30)
            else:
                # Give up
                self.stop()
            return

        if (not (interface.NS_MUC in res.features)):
            self.log.error('MUC host %s does not support the MUC feature', self.muc.getdomain())
            self.stop()
            return

        self.log.info('joining new muc: %s', unicode(self.muc))

        msg = interface.Node('presence',
            attrs={ 'to':unicode(self.mucnick) })
        msg.setchild('x', namespace=interface.NS_MUC)
        # We're not using the Zymb PresenceService, so we add the capabilities
        # tag manually.
        self.cappresencehook(msg)

        self.conn.send(msg, addid=False)
        self.jump('configuring')
        
    def configuremuc(self):
        """configuremuc() -> None

        Begin the configuration of a MUC room. Send out the query stanza
        which begins the process.
        """

        msg = interface.Node('iq',
            attrs={'type':'get', 'to':unicode(self.muc)})
        msg.setchild('query', namespace=interface.NS_MUC_OWNER)
        id = self.conn.send(msg)
        
        self.conn.adddispatcher(self.handle_stanza_configuremuc,
            name='iq', type=('result','error'), id=id)
        
    def handle_stanza_configuremuc(self, msg):
        """handle_stanza_configuremuc(msg) -> <stanza outcome>

        MUC configuration callback. If the config query failed, shut down
        the referee. If it succeeded, extract the MUC configuration form,
        build a response form, and send it back.
        """
        
        nod = msg.getchild('query')
        if (not nod or nod.getnamespace() != interface.NS_MUC_OWNER):
            # Not addressed to us
            return
            
        if (msg.getattr('type') != 'result'):
            ex = interface.parseerrorstanza(msg)
            self.log.error('could not configure MUC %s: %s',
                unicode(self.muc), ex)
            self.stop()
            raise interface.StanzaHandled

        try:
            xnod = nod.getchild('x')
            newxnod = self.buildconfigmuc(xnod)
            newmsg = interface.Node('iq',
                attrs={'type':'set', 'to':unicode(self.muc)})
            newqnod = newmsg.setchild('query',
                namespace=interface.NS_MUC_OWNER)
            if (newxnod):
                newqnod.addchild(newxnod)
                
            id = self.conn.send(newmsg)

            self.conn.adddispatcher(self.handle_stanza_configuremucresult,
                name='iq', type=('result','error'), id=id)
        except:
            self.stop()
            raise
            
        raise interface.StanzaHandled

    def buildconfigmuc(self, origform):
        """buildconfigmuc(origform) -> Node

        Construct a form to configure a MUC room. The *origform* is an XML
        Node object containing a JEP-0004 data form. This method returns
        a new form which comes from filling the original form out.

        We are really only interested in three fields: the room name,
        the maximum number of users, and whether the room will be anonymous
        (permit people to see each others' real JIDs). Unfortunately,
        different MUC hosts use different names for these fields, so we
        have to cover a lot of possibilities.

        Fields which we don't care about keep their default values.
        """

        dic = {
            'muc#owner_roomname':      self.parlor.gamename,
            'muc#roomconfig_roomname': self.parlor.gamename,
            'title':                   self.parlor.gamename,
            'muc#owner_maxusers':      str(self.maxmucusers),
            'muc#roomconfig_maxusers': str(self.maxmucusers),
            'muc#owner_whois':         'anyone',
            'muc#roomconfig_whois':    'anyone',
            'anonymous':               '0',
            'muc#roomconfig_presencebroadcast': ['moderator', 'participant', 'visitor'],
        }
    
        if (origform == None):
            return None

        if (origform.getnamespace() != interface.NS_DATA):
            raise interface.BadRequest('muc config form had wrong namespace for <x>')
        formtype = origform.getattr('type')
        # Old MUC servers don't provide a 'type' attribute
        if (formtype != None and formtype != 'form'):
            raise interface.BadRequest('muc config form had wrong type for <x>')

        newform = interface.Node('x', attrs={'type':'submit'})
        newform.setnamespace(interface.NS_DATA)

        for nod in origform.getchildren():
            if (nod.getname() != 'field'):
                continue
            varattr = nod.getattr('var')
            typattr = nod.getattr('type')
            if (not varattr or typattr=='fixed'):
                continue

            value = ''
            valnod = nod.getchild('value')
            if (valnod):
                value = valnod.getdata()

            if (dic.has_key(varattr)):
                value = dic[varattr]
                
            fnod = interface.Node('field', attrs={'var':varattr})
            if (type(value) == list):
                for subval in value:
                    valnod = interface.Node('value')
                    valnod.setdata(subval)
                    fnod.addchild(valnod)
            else:
                valnod = fnod.setchild('value')
                if (value):
                    valnod.setdata(value)

            newform.addchild(fnod)

        return newform
        
    def handle_stanza_configuremucresult(self, msg):
        """handle_stanza_configuremucresult(msg) -> <stanza outcome>

        Second MUC configuration callback. If our config form failed, shut
        down the referee. If it succeeded, jump to state 'running'.
        """

        if (msg.getattr('type') != 'result'):
            ex = interface.parseerrorstanza(msg)
            self.log.error('failed to configure MUC %s: %s',
                unicode(self.muc), ex)
            self.mucshutdownreason = 'Failed to configure MUC.'
            self.stop()
            raise interface.StanzaHandled

        self.mucrunning = True
        self.jump('running')
        self.queueaction(self.checktimersetting)
        raise interface.StanzaHandled

    def checktimersetting(self):
        """checktimersetting() -> None

        Check whether the Referee needs a state timer. This is called after
        any referee state change that might start or stop a timer. There
        are two timers that might be running:

        'deadconfig': Starts whenever there are no (human) players in
        the MUC, in either 'setup' or 'suspended' state. After 90 seconds,
        shuts down the MUC.

        'abandoned': Starts whenever there are no seated (human) players
        in the MUC, in the 'active', 'disrupted', or 'abandoned' state.
        After 3 minutes, suspends the game.

        This method determines which of the above should be running, and
        calls settimer() with that string. If no timer should be running,
        it calls settimer(None).

        In the 'active', 'disrupted', or 'abandoned' states, this method also
        decides whether the referee should jump to a different one of those
        three states. The 'abandoned' timer is always associated with the
        'abandoned' state; if the timer is not running, the referee might
        be 'active' or 'disrupted'.
        """

        oldstate = self.refstate
        
        if (self.refstate in [STATE_SETUP, STATE_SUSPENDED]):
            ls = [ pla for pla in self.players.values()
                if not pla.isbot ]
            if (not ls):
                self.settimer('deadconfig')
            else:
                self.settimer(None)
            return
        elif (self.refstate == STATE_AUTHORIZING):
            self.settimer(None)
            return
        else:   # STATE_ACTIVE, STATE_DISRUPTED, STATE_ABANDONED
            ls = [ player for player in self.players.values()
                if player.live and player.seat and (not player.isbot) ]
            if (not ls):
                self.refstate = STATE_ABANDONED
                if (oldstate != STATE_ABANDONED):
                    self.reportactivity()
                self.settimer('abandoned')
                return
            for seat in self.gameseatlist:
                if (seat.eliminated):
                    continue
                ls = [ player for player in seat.playerlist
                    if player.live ]
                if (not ls):
                    self.refstate = STATE_DISRUPTED
                    if (oldstate != STATE_DISRUPTED):
                        self.reportactivity()
                    self.settimer(None)
                    return
            self.refstate = STATE_ACTIVE
            if (oldstate != STATE_ACTIVE):
                self.reportactivity()
            self.settimer(None)
            return

    def settimer(self, reason):
        """settimer(reason) -> None

        Set a state timer. The *reason* is 'deadconfig', 'abandoned', or
        None (for no timer). If the given timer is already set (or not set),
        this does nothing.
        """

        if (reason == None):
            if (self.timeraction):
                self.timeraction.remove()
            self.timerreason = None
            self.timeraction = None
            return
        if (reason == 'deadconfig'):
            if (self.timerreason == 'deadconfig'):
                return
            if (self.timeraction):
                self.timeraction.remove()
            self.timerreason = 'deadconfig'
            self.timeraction = self.addtimer(self.deadconfigtimer,
                delay=DEAD_CONFIG_TIMEOUT)
            return
        if (reason == 'abandoned'):
            if (self.timerreason == 'abandoned'):
                return
            if (self.timeraction):
                self.timeraction.remove()
            self.timerreason = 'abandoned'
            self.timeraction = self.addtimer(self.abandonedtimer,
                delay=ABANDONED_TIMEOUT)
            return
        assert 0, ('unknown reason %s in settimer' % reason)

    def deadconfigtimer(self):
        """deadconfigtimer() -> None

        Abandoned-in-configuration timer callback. This shuts down the
        referee.
        """
        self.log.warning('the table has been depopulated in configuration for %d seconds -- closing', DEAD_CONFIG_TIMEOUT)
        self.mucshutdownreason = 'The table has been abandoned.'
        self.stop()

    def abandonedtimer(self):
        """abandonedtimer() -> None

        Abandoned-during-game timer callback. This suspends the game.
        """
        self.log.warning('the game has been abandoned in play for %d seconds -- suspending', ABANDONED_TIMEOUT)
        self.queueaction(self.suspendgame)

    def isadminjid(self, sender):
        """isadminjid(sender) -> bool

        Check whether the given JID is an administrator JID. This forwards
        the request to the parlor.
        """
        return self.parlor.isadminjid(sender)
        
    # ---- player request handlers

    def playerarrived(self, jid, nick, isbot=False):
        """playerarrived(jid, nick, isbot=False) -> None

        Handle a player joining the table. His real JID is *jid*, his
        room nickname is *nick*, and *isbot* flags whether we think he's
        a bot.

        This may be a player changing his MUC nickname; we detect that
        by checking the real JID. Otherwise, it's someone genuinely joining.

        If we're in the middle of a game, it might be a disconnected player
        returning to his seat. If so, mark his Player object as live.
        Otherwise, create a new Player object for the new player.
        """
        
        jidstr = unicode(jid)
        jidchanged = False
        player = self.players.get(jidstr, None)
        
        if (not player):
            # Conceivably this is a disconnected player returning to us
            # with a new resource.
            for jidstr2 in self.players.keys():
                player2 = self.players[jidstr2]
                if ((not player2.live) and jid.barematch(jidstr2)):
                    player = player2
                    jidchanged = True
        
        self.queueaction(self.checktimersetting)
        
        if (self.playernicks.has_key(nick)
            and player != self.playernicks[nick]):
            # Someone else already has this nickname. Jabber ought to prevent
            # this from happening.
            self.log.error('got presence from %s claiming nick %s, which belongs to %s. Ignoring',
                jidstr, nick, unicode(self.playernicks[nick].jid))
            return

        if (player):

            if (not self.playernicks.has_key(nick)):
                # I guess this player changed his room nick.
                self.playernicks.pop(player.nick, None)
                player.nick = nick
                self.playernicks[nick] = player

            if (jidchanged):
                # We have to change the player's JID, and his key in
                # self.players.
                self.log.info('player %s has changed to resource %s',
                    player.jidstr, jidstr)
                self.players.pop(player.jidstr)
                player.jid = jid
                player.jidstr = unicode(jid)
                self.players[jidstr] = player

            if (not player.live):
                self.log.info('player %s has rejoined the table',
                    unicode(player))
                player.live = True
                player.aware = False
                self.queueaction(self.game.presencechange, player.jid,
                    ACTION_JOINED)
                if (player.seat):
                    self.queueaction(self.reportsit, player, player.seat)
            else:
                self.queueaction(self.game.presencechange, player.jid,
                    ACTION_NICKNAME)
                
            return

        # New player.
        player = Player(self, jid, nick, isbot)
        self.players[jidstr] = player
        self.playernicks[nick] = player
        
        self.queueaction(self.game.presencechange, player.jid, ACTION_JOINED)

    def playerleft(self, jid, nick):
        """playerleft(jid, nick) -> None

        Handle a player leaving the table. His real jid is *jid*, his
        room nickname is *nick.

        If it's a seated player during a game, we keep the Player object.
        (But we mark it non-live.) Otherwise, throw away the Player.
        """
        
        jidstr = unicode(jid)

        if (not self.players.has_key(jidstr)):
            return

        # This will do appropriate checking to see if the room is dead,
        # or if we need to throw the game.
        self.queueaction(self.checktimersetting)
        
        player = self.players[jidstr]

        if ((not (self.refstate in [STATE_SETUP, STATE_SUSPENDED]))
            and player.seat):
            # We have to keep the player record around (and in the seat.)
            self.log.info('seated player %s has left the table',
                unicode(player))
            self.playernicks.pop(player.nick, None)
            player.nick = None
            player.live = False
            player.aware = False
            # However, we send out player_stood() RPCs. When the player
            # returns, we will send out player_sat() RPCs.
            seat = player.seat
            self.queueaction(self.reportstand, player, seat)
            self.queueaction(self.game.presencechange, player.jid, ACTION_LEFT)
            return
            
        if (player.seat):
            # Presume player stood up before leaving.
            seat = player.seat
            assert (player in seat.playerlist)
            seat.playerlist.remove(player)
            player.seat = None

            # Message-sending will run after the player object is ditched
            self.unreadyall(False)
            self.queueaction(self.reportstand, player, seat)

        self.players.pop(jidstr, None)
        self.playernicks.pop(player.nick, None)
        player.nick = None
        player.live = False
        player.aware = False
        self.queueaction(self.game.presencechange, player.jid, ACTION_LEFT)

    def playersit(self, sender, jidstr, seatid=None):
        """playersit(sender, jidstr, seatid=None) -> str

        Handle a player sit request. The *sender* is the JID which made the
        request; *jidstr* is the player the request is about. This returns
        the ID of the seat which the player actually winds up in.

        If *seatid* is None, the referee gets to choose a seat. We call
        the game's requestanyseat() method (or requestanyseatingame() if
        the game is in progress). By default, these methods pick the first
        empty seat, preferring required seats over optional ones. However,
        the game may have a better scheme.

        If *seatid* is not None, the request is for a particular seat. We
        let the game vet that, by calling its requestparticularseat()
        method. By default, the request is permitted.
        """
        
        if (not self.players.has_key(jidstr)):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(jidstr))

        player = self.players[jidstr]
        if (not player.aware):
            raise game.FailureToken('volity.jid_not_ready',
                game.Literal(jidstr))
        
        if (not seatid):
        
            if (player.seat):
                # Already seated
                return player.seat.id

            if (self.refstate == STATE_SETUP):
                seat = self.game.requestanyseat(player)
            else:
                seat = self.game.requestanyseatingame(player)
            if (not seat):
                raise game.FailureToken('volity.no_seat')
            if (not isinstance(seat, game.Seat) or (seat.referee != self)
                or not (seat in self.seatlist)):
                raise ValueError('requestanyseat returned an invalid seat')
            
        else:

            if (not self.seats.has_key(seatid)):
                raise rpc.RPCFault(606,
                    ('seat \'%s\' does not exist' % seatid))
            
            seat = self.seats[seatid]
            if (player.seat != seat):
                self.game.requestparticularseat(player, seat)

        # Now we have a seat

        if (self.refstate == STATE_SUSPENDED
            and not seat.ingame):
            raise game.FailureToken('volity.seat_not_available')
        
        if (player.seat == seat):
            # Already seated
            return player.seat.id

        if (player.seat):
            assert (player in player.seat.playerlist)
            player.seat.playerlist.remove(player)
            player.seat = None

        seat.playerlist.append(player)
        player.seat = seat

        self.unreadyall(False)
        self.queueaction(self.reportsit, player, seat)

        return seat.id

    def playerstand(self, sender, jidstr):
        """playerstand(sender, jidstr) -> None

        Handle a player stand request. The *sender* is the JID which made the
        request; *jidstr* is the player the request is about.
        """
        
        if (not self.players.has_key(jidstr)):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(jidstr))
                
        player = self.players[jidstr]

        if (not player.seat):
            # Already standing
            return
        
        seat = player.seat
        assert (player in seat.playerlist)
        seat.playerlist.remove(player)
        player.seat = None
        
        self.unreadyall(False)
        self.queueaction(self.reportstand, player, seat)

    def playerready(self, sender):
        """playerready(sender) -> None

        Handle a player ready request. The *sender* is the player's JID.

        This can trigger the beginning (or resumption) of a game, so we do
        a lot of checking. If we're in 'setup' state, we ask the game whether
        its state is legal, by calling checkconfig() and checkseating().
        If we're in 'suspended', then we just have to check that all the
        game's seats are occupied... unless the players have voted to
        kill off the game, in which case we don't need to check anything.

        We also check that at least one human is seated.

        Finally, if all seated players are ready, the game actually begins
        (or resumes, or is killed).
        """

        assert (self.refstate in [STATE_SETUP, STATE_SUSPENDED])
        newgame = (self.refstate == STATE_SETUP)
        player = self.players[unicode(sender)]

        if (player.ready):
            return

        if (not player.seat):
            raise game.FailureToken('volity.not_seated')

        ls = [ pla for pla in self.players.values()
            if (pla.seat and (not pla.isbot)) ]
        if (not ls):
            raise rpc.RPCFault(609, 'no humans are seated')

        if (newgame):
            # To leave setup mode, we need the game to check the config.
            self.game.checkconfig()
            self.game.checkseating()
        else:
            if (not self.killgame):
                # To leave suspension, we have to fill all the in-game seats.
                ls = [ seat for seat in self.gameseatlist if seat.isempty() ]
                if (ls):
                    raise game.FailureToken('volity.empty_seats')
            else:
                # But if the decision is to kill the game, we don't need
                # to check anything.
                pass

        player.ready = True
        self.queueaction(self.reportreadiness, player)

        ls = [ pla for pla in self.players.values() if pla.seat ]
        if (not ls):
            return
        subls = [ pla for pla in ls if (not pla.ready) ]
        if (subls):
            # Someone is unready
            return

        self.queueaction(self.authorizinggame, newgame)
            
    def playerunready(self, sender):
        """playerunready(sender) -> None

        Handle a player unready request. The *sender* is the player's JID.
        """
        
        player = self.players[unicode(sender)]

        if (not player.ready):
            return

        if (not player.seat):
            raise game.FailureToken('volity.not_seated')

        player.ready = False
        self.queueaction(self.reportreadiness, player)

    def playeraddbot(self, sender, uri=None):
        """playeraddbot(sender, uri=None) -> None

        Handle a player request for a new (internal) bot. The *sender* is
        the player's JID. The *uri* is a bot algorithm URI, if a specific
        one is desired.

        This creates an Actor, which is a Volity entity that connects to
        the Jabber server and plays as a player would. (The Bot is the game-
        specific "brain" inside the Actor, much as the Game object is the
        game-specific part of the Referee.)
        """
        
        if (not self.parlor.botclasses):
            raise game.FailureToken('volity.no_bots_provided')

        if (not uri):
            botclass = self.parlor.botclasses[0]
        else:
            found = False
            for botclass in self.parlor.botclasses:
                if (botclass.boturi == uri):
                    found = True
                    break
            if (not found):
                raise volent.FailureToken('volity.bot_not_available')

        self.log.info('new bot requested by %s (%s)...', unicode(sender),
            botclass.boturi)
        
        botresource = 'bot_' + str(os.getpid()) + '_' + str(self.uniquestamp())
        
        # The player-visible name of the bot shouldn't conflict with existing
        # MUC nicknames. We will do collision detection when we join the MUC,
        # but some MUC servers get crashy when you do that (in cases which
        # we haven't diagnosed yet). So we make a rough attempt to start
        # with a never-before-used name.
        botnick = 'Bot ' + str(self.botcounter)
        self.botcounter += 1

        try:
            act = actor.Actor(self, self.jid,
                self.parlor.jid, self.parlor.password,
                self.muc, botresource, botnick, botclass)
        except Exception, ex:
            self.log.error('Unable to create bot',
                exc_info=True)
            raise rpc.RPCFault(608,
                'unable to create bot: ' + str(ex))
        
        assert (not self.parlor.actors.has_key(botresource))
        self.parlor.actors[botresource] = act
        
        act.start()

        act.addhandler('end', self.parlor.actordied, act)
        self.addhandler('end', act.stop)
        self.parlor.addhandler('end', self.parlor.stopunlessgraceful, act)

        # Now we wait until the Actor is finished starting up -- which
        # is defined as "successfully connected to the MUC". This requires
        # a Deferred handler, which is hard to use but easy to explain:
        # it has three possible outcomes, and exactly one of them will happen.
        # Either the Actor will start up, or it will shut down without
        # ever connecting, or we'll give up before either of the above.
        # (We set a timer of BOT_STARTUP_TIMEOUT for that last outcome.)
        #
        # Whichever happens, our boteready() method will be called.

        defer = sched.Deferred(self.botready, act)
        
        ac1 = act.addhandler('running', defer, 'running')
        defer.addaction(ac1)
        ac2 = self.addtimer(defer, 'timeout', delay=BOT_STARTUP_TIMEOUT)
        defer.addaction(ac2)
        ac3 = act.addhandler('end', defer, 'end')
        defer.addaction(ac3)
        
        raise defer

    def botready(self, act, res):
        """botready(act, res) -> <RPC outcome>

        Callback invoked when an Actor is successfully (or unsuccessfully)
        created. This is the continuation of playeraddbot(). The *act* is
        the Actor object that was created, and *res* is either 'running',
        'end', or 'timeout'.
        """
        
        if (res == 'timeout'):
            self.log.warning('bot %s failed to start up in time -- killing', act.resource)
            act.stop()
            raise rpc.RPCFault(608, 'unable to start bot')
        if (res == 'end'):
            self.log.warning('bot %s died before responding', act.resource)
            raise rpc.RPCFault(608, 'bot failed to start up')
        return act.jid
    
    def playeraddexternalbot(self, sender, uri, jid):
        """playeraddexternalbot(sender, uri, jid) -> None

        Handle a player request for a new bot from a bot factory. The
        *sender* is the player's JID. The *uri* specifies which bot algorithm
        is being requested; the *jid* is the (full) JID of the bot factory.
        """

        # Set up a Deferred handler which will be invoked when the new_bot
        # request completes.
        defer = sched.Deferred(self.externalbotready)

        self.rpccli.send((self.gotnewbotreply, defer, jid),
            jid, 'volity.new_bot', uri, self.muc,
            timeout=BOT_STARTUP_TIMEOUT)
        raise defer

    def gotnewbotreply(self, tup, defer, jidstr):
        """gotnewbotreply(tup, defer, jidstr) -> None

        Callback invoked when a new_bot RPC is replied to. The *tup* contains
        the RPC result; the *defer* and *jidstr* are passed down from
        playeraddexternalbot().

        This just extracts the RPC result from *tup*, catching all the
        possible things which could go wrong. Then it invokes the
        externalbotready() callback.
        """
        
        try:
            res = sched.Deferred.extract(tup)
            # If no exception, then the call succeeded.
            ac = self.queueaction(defer, jidstr, 'ok', res)
            defer.addaction(ac)
        except sched.TimeoutException, ex:
            ac = self.queueaction(defer, jidstr, 'timeout')
            defer.addaction(ac)
        except jabber.interface.StanzaError, ex:
            ac = self.queueaction(defer, jidstr, 'ex', None, ex)
            defer.addaction(ac)
        except Exception, ex:
            ex = jabber.interface.StanzaInternalServerError(str(ex))
            ac = self.queueaction(defer, jidstr, 'ex', None, ex)
            defer.addaction(ac)

    def externalbotready(self, jidstr, res, botjid=None, ex=None):
        """externalbotready(jidstr, res, botjid=None, ex=None) -> <RPC outcome>

        Callback which replies to the original add_bot request. The *jidstr*
        is the JID we sent the invitation to; *res* describes whether the
        invitation succeeded; *botjid* is the JID of the bot, if all went
        well; and *ex* is the exception that caused the problem, if there
        was a problem.

        If the invitation succeeded, we just return cleanly to the original
        invite RPC. If there was any problem, we return a 'relay_failed'
        failure token, with *jidstr* in the \1 spot.
        """
        
        if (res == 'ok'):
            return botjid
        raise game.FailureToken('volity.relay_failed', game.Literal(jidstr))
            
    def playerremovebot(self, sender, jidstr):
        """playerremovebot(sender, jid) -> None

        Handle a player request to remove a bot. The *sender* is the player's
        JID. The *jid* is the bot's JID.

        This only works on unseated bots.
        """
        
        if (not self.players.has_key(jidstr)):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(jidstr))
                
        player = self.players[jidstr]
        if (not player.isbot):
            raise game.FailureToken('volity.not_bot', game.Literal(jidstr))

        if (player.seat):
            raise game.FailureToken('volity.bot_seated')
                
        act = self.parlor.actors.get(player.jid.getresource())
        if (act):
            # Internal bot; stop it directly.
            act.stop()
            return

        # External bot; throw it a leave_table() RPC.
        self.rpccli.send(self.defaultcallback, jidstr,
            'volity.leave_table', timeout=CLIENT_DEFAULT_RPC_TIMEOUT)
                
    def playersuspend(self, sender=None):
        """playersuspend(sender=None) -> None

        Handle a player game-suspend request. The *sender* is the player's
        JID.

        It's also possible that the game was suspended by the referee, due
        to the 'abandoned' timer. In that case, *sender* will be None.
        """
        self.queueaction(self.suspendgame, sender)

    def playerinvite(self, sender, jidstr, msg=None):
        """playerinvite(sender, jidstr, msg=None):

        Handle a player request to invite another player to the table. The
        *sender* is the player's JID; the *jidstr* is the recipient of the
        invitation; and *msg* is an additional message from the inviter to
        the invitee.
        """

        # Can't invite someone who's already present!        
        player = self.players.get(jidstr, None)
        if (player and player.live):
            raise game.FailureToken('volity.jid_present',
                game.Literal(jidstr))

        argmap = {
            'player' : unicode(sender),
            'table' : unicode(self.muc),
            'referee' : unicode(self.jid),
            'parlor' : unicode(self.parlor.jid),
            'ruleset' : self.parlor.gameclass.ruleseturi,
            'name' : self.parlor.gamename,
        }
        if (msg):
            argmap['message'] = msg

        jid = interface.JID(jidstr)
        if (not jid.getresource()):
            # Bare JID; we must send a <message> invitation.

            textmsg = ('[' + unicode(sender.getbare())
                + ' has invited you to join a game of '
                + argmap['name'] + ' at table ' + argmap['table'] + '.]')
            if (msg):
                textmsg = msg + ' ' + textmsg
            
            nod = interface.Node('message', attrs={'to':jidstr} )
            nod.setchilddata('body', textmsg)
            form = jabber.dataform.DataForm()
            form.addfield('FORM_TYPE',
                'http://volity.org/protocol/form/invite',
                None, 'hidden')
            for key in argmap.keys():
                form.addfield(key, argmap[key])
            formwrap = interface.Node('volity',
                namespace='http://volity.org/protocol/form')
            formwrap.addchild(form.makenode())
            nod.addchild(formwrap)
            self.conn.send(nod)
            # There is no feedback to a message; just return success.
            return

        # JID with resource; we must send an RPC invitation.
        
        # Set up a Deferred handler which will be invoked when the invitation
        # completes.
        defer = sched.Deferred(self.playerinvitecont)

        self.rpccli.send((self.gotinvitereply, defer, jidstr),
            jidstr, 'volity.receive_invitation', argmap,
            timeout=CLIENT_INVITATION_RPC_TIMEOUT)
        raise defer

    def gotinvitereply(self, tup, defer, jidstr):
        """gotinvitereply(tup, defer, jidstr) -> None

        Callback invoked when an invitation is replied to. The *tup* contains
        the RPC result; the *defer* and *jidstr* are passed down from
        playerinvite().

        This just extracts the RPC result from *tup*, catching all the
        possible things which could go wrong. Then it invokes the
        playerinvitecont() callback.

        (Yes, there's probably some way to combine gotinvitereply() and
        playerinvitecont() into one function. Never bothered to work out
        how.)
        """
        
        try:
            res = sched.Deferred.extract(tup)
            # If no exception, then the call succeeded.
            ac = self.queueaction(defer, jidstr, 'ok')
            defer.addaction(ac)
        except sched.TimeoutException, ex:
            ac = self.queueaction(defer, jidstr, 'timeout')
            defer.addaction(ac)
        except jabber.interface.StanzaError, ex:
            ac = self.queueaction(defer, jidstr, 'ex', ex)
            defer.addaction(ac)
        except Exception, ex:
            ex = jabber.interface.StanzaInternalServerError(str(ex))
            ac = self.queueaction(defer, jidstr, 'ex', ex)
            defer.addaction(ac)

    def playerinvitecont(self, jidstr, res, ex=None):
        """playerinvitecont(jidstr, res, ex=None) -> <RPC outcome>

        Callback which replies to the original invite request. The *jidstr*
        is the JID we sent the invitation to; *res* describes whether the
        invitation succeeded; and *ex* is the exception that caused the
        problem, if there was a problem.

        If the invitation succeeded, we just return cleanly to the original
        invite RPC. If there was any problem, we return a 'relay_failed'
        failure token, with *jidstr* in the \1 spot.
        """
        
        if (res == 'ok'):
            return
        raise game.FailureToken('volity.relay_failed', game.Literal(jidstr))
            
    def configsetlanguage(self, sender, lang):
        """configsetlanguage(sender, lang) -> None

        Handle a player request to change the table language. The *sender*
        is the player's JID; *lang* is the language requested.
        """
        
        if (len(lang) != 2):
            raise rpc.RPCFault(606, 'language must be a two-character string')

        if (self.language == lang):
            return

        self.language = lang
        
        self.unreadyall(False)
        self.queueaction(self.sendall, 'volity.language',
            sender, self.language)

    def configrecordgames(self, sender, flag):
        """configrecordgames(sender, flag) -> None

        Handle a player request to change the game-record flag. The *sender*
        is the player's JID; *flag* indicates whether to record games at
        the bookkeeper.
        """
        
        if (self.recordgames == flag):
            return

        self.recordgames = flag
        
        self.unreadyall(False)
        self.queueaction(self.sendall, 'volity.record_games',
            sender, self.recordgames)

    def configshowtable(self, sender, flag):
        """configshowtable(sender, flag) -> None

        Handle a player request to change the show-table flag. The *sender*
        is the player's JID; *flag* indicates whether the parlor should
        display this game.
        """
        
        if (self.showtable == flag):
            return

        self.showtable = flag
        
        self.unreadyall(False)
        self.queueaction(self.sendall, 'volity.show_table',
            sender, self.showtable)

    def configkillgame(self, sender, flag):
        """configkillgame(sender, flag) -> None

        Handle a player request to change the kill-game flag. The *sender*
        is the player's JID; *flag* indicates whether the referee should
        kill the game when it unsuspends.
        """
        
        if (self.killgame == flag):
            return

        self.killgame = flag
        
        self.unreadyall(False)
        self.queueaction(self.sendall, 'volity.kill_game',
            sender, self.killgame)

    # ---- Player notification utilities

    def unreadyall(self, notify=True):
        """unreadyall(notify=True) -> None

        Mark all players as unready. If *notify* is True, this sends
        out unready() RPCs for all players who changed state. If False,
        there is no notification.

        (The False form of this method is used when handling RPCs that
        are documented as automatically unreadying all players. (Sit, stand,
        and so on.) The client is required to know about this automatic
        unreadying, so there's no need for us to send out notices about
        it.)
        """
        
        ls = [ pla for pla in self.players.values() if pla.ready ]
        if (not ls):
            return
            
        self.log.info('marking %d player(s) unready, notify %s',
            len(ls), notify)
        for player in ls:
            player.ready = False
        if (notify):
            self.queueaction(self.reportunreadylist, ls)

    def sendfullstate(self, player):
        """sendfullstate(player) -> None

        Send the complete table state to a player. The argument is a
        Player object. This also serves as notice that the Player is
        ready to receive RPCs, so we mark it aware.

        This includes seating information, table configuration, and the
        state of the game (if in progress). And it's all wrapped in a
        receive_state() / state_sent() pair, so the client knows when the
        burst is over.
        """
        
        if (not player.live):
            return
        player.aware = True

        argmap = { 'state':self.refstate }
        self.game.sendplayer(player, 'volity.receive_state', argmap)

        # First, the seating information
        ls = [ seat.id for seat in self.seatlist ]
        subls = [ seat.id for seat in self.seatlist if seat.required ]
        self.game.sendplayer(player, 'volity.seat_list', ls)
        if (subls):
            self.game.sendplayer(player, 'volity.required_seat_list', subls)

        for pla in self.players.values():
            if (pla.seat):
                self.game.sendplayer(player, 'volity.player_sat', pla.jidstr, pla.seat.id)

        for pla in self.players.values():
            if (pla.ready):
                self.game.sendplayer(player, 'volity.player_ready', pla.jidstr)

        # Then, the game config information
        self.game.sendconfigstate(player)

        # Then, the game state (if in progress)
        if (not (self.refstate == STATE_SETUP
            or (self.refstate == STATE_AUTHORIZING
                and self.prevrefstate == STATE_SETUP))):
            seat = player.seat
            ### or the last known seat, if in suspended state
            ### or no seat?
            self.game.sendplayer(player, 'volity.game_has_started')
            self.game.sendgamestate(player, seat)
            
        self.game.sendplayer(player, 'volity.state_sent')

    def removenonliveplayers(self):
        """removenonliveplayers() -> None

        Remove all players from the player list who are not connected to
        the table. We must do this whenever we enter the setup or suspended
        states. (During other states, we need to keep track of absent
        players.)
        """
        
        for jidstr in self.players.keys():
            player = self.players[jidstr]
            if (not player.live):
                seat = player.seat
                if (seat):
                    assert (player in seat.playerlist)
                    seat.playerlist.remove(player)
                    player.seat = None
                self.players.pop(jidstr, None)

    def reportactivity(self):
        """reportactivity() -> None

        Report a new "in progress" state (one of ACTIVE, DISRUPTED, or
        ABANDONED).
        """
        assert (self.refstate in
            [STATE_ACTIVE, STATE_DISRUPTED, STATE_ABANDONED])
        self.log.info('game is now %s', self.refstate)
        self.sendall('volity.game_activity', self.refstate)
        
    def reportsit(self, player, seat):
        """reportsit(player, seat) -> None

        Report a player sitting. The *player* has sat in Seat *seat*.
        """
        self.log.info('player %s sits in seat %s', unicode(player), seat.id)
        self.sendall('volity.player_sat', player.jidstr, seat.id)
        self.game.seatchange(player, seat)
        
    def reportstand(self, player, seat):
        """reportstand(player, seat) -> None

        Report a player standing. The *player* has stood from Seat *seat*.
        """
        self.log.info('player %s stands from seat %s', unicode(player), seat.id)
        self.sendall('volity.player_stood', player.jidstr)
        self.game.seatchange(player, None)

    def reportreadiness(self, player):
        """reportreadiness(player) -> None

        Report a player changing readiness. The *player*'s current state
        determines whether this sends a player_ready() or player_unready()
        RPC.
        """
        
        if (player.ready):
            st = 'ready'
        else:
            st = 'unready'
        self.log.info('player %s reports %s', unicode(player), st)
        self.sendall('volity.player_'+st, player.jidstr)

    def reportunreadylist(self, ls):
        """reportunreadylist(ls) -> None

        Report several players becoming unready. The *ls* is a list of
        Players.
        """
        for player in ls:
            self.sendall('volity.player_unready', player.jidstr)

    def announce(self, data):
        """announce(data) -> None

        Yell a message to everyone at the table. The *data* string is sent
        as a group-chat message in the MUC room. (This method is used by
        adminstrative commands, not by players or the referee.)
        """
        
        msg = interface.Node('message',
            attrs={ 'to':unicode(self.muc), 'type':'groupchat' })
        msg.setchilddata('body', 'administrator announcement: ' + data)
        self.queueaction(self.conn.send, msg)

    def updatediscoinfo(self):
        """updatediscoinfo() -> DiscoInfo

        Update and return the disco-info form. We keep a DiscoInfo object
        lying around, but before we can send it, we have to update some
        fields to represent the current referee state. This method does
        that.
        """
        
        form = self.discoinfo.getextendedinfo()
        
        form.addfield('state', self.refstate)

        ls = [ seat for seat in self.seatlist if seat.playerlist ]
        form.addfield('players', str(len(ls)))
        
        form.addfield('language', self.language)
        
        form.addfield('max-players', len(self.seatlist))

        if (not self.showtable):
            st = '0'
        else:
            st = '1'
        form.addfield('visible', st)

        if (not self.recordgames):
            st = '0'
        else:
            st = '1'
        form.addfield('recorded', st)

        if (not self.killgame):
            st = '0'
        else:
            st = '1'
        form.addfield('kill-game', st)

        return self.discoinfo

    def parsewinners(self, ls):
        """parsewinners(ls) -> list

        Turn a list of game seats (in winning order) into a standardized
        winners list (a list of lists of seats). This is called by the
        game-ending methods of Game. Since the *ls* comes from
        game-specific code, we do lots of consistency-checking on it.

        Each element of *ls* must be either a Seat object, or a list of
        Seat objects. The latter indicates a tie at that position. Not
        all Seats in the game have to be included; if any are missing,
        they are tacked onto the end as a "tie for last place".

        As a special case, if *ls* has the sole element None, then everybody
        is considered to have tied.
        """
        
        if (ls == (None,)):
            ls = ()
            
        # Make a copy of gameseatlist
        remaining = list(self.gameseatlist)

        res = []

        for val in ls:
            if (isinstance(val, game.Seat)):
                if (not(val in self.gameseatlist)):
                    raise ValueError('winners may only contains Seats which are part of the current game')
                if (not(val in remaining)):
                    raise ValueError('winners may not contain any Seat twice')
                remaining.remove(val)
                res.append([val])
            elif (type(val) in [tuple, list]):
                subls = list(val)
                for val in subls:
                    if (not isinstance(val, game.Seat)):
                        raise ValueError('winners must be a list of Seats or Seat lists')
                    if (not(val in self.gameseatlist)):
                        raise ValueError('winners may only contains Seats which are part of the current game')
                    if (not(val in remaining)):
                        raise ValueError('winners may not contain any Seat twice')
                    remaining.remove(val)
                res.append(subls)
            else:
                raise ValueError('winners must be a list of Seats or Seat lists')

        if (remaining):
            res.append(remaining)
            
        return res

    # ---- Game state transitions.

    def authorizinggame(self, newgame):
        """authorizinggame(newgame) -> None

        Game-authorization handler. This is triggered when all the players
        become ready. If *newgame* is true, the previous state was SETUP,
        and this is a new game; otherwise, the previous state was SUSPENDED.
        """

        if (newgame):
            prevstate = STATE_SETUP
        else:
            prevstate = STATE_SUSPENDED
            
        if (self.refstate != prevstate):
            self.log.error('entered authorizinggame when state was %s, not %s',
                self.refstate, prevstate)
            return

        self.log.info('game authorizing (from %s)', prevstate)
        self.prevrefstate = prevstate
        self.refstate = STATE_AUTHORIZING

        if (newgame):
            # We must compute the game seat list and so forth. (Although
            # if the authorization fails, we'll be throwing that away.)
            
            self.gameseatlist = []
            for seat in self.seatlist:
                if (seat.playerlist):
                    seat.ingame = True
                    seat.eliminated = False
                    self.gameseatlist.append(seat)
                else:
                    seat.ingame = False
                    seat.eliminated = False

            # From this point on, the seat listings are immutable. Also,
            # we don't discard a seated Player object even if it
            # disconnects.
        
            # Prepare the history lists.
            for seat in self.seatlist:
                seat.playerhistory.clear()

        # Generate the list of full JIDs which are involved in the game.
        # This only includes players who are currently connected.
        ls = []
        for seat in self.gameseatlist:
            for pla in seat.playerlist:
                if (pla.live):
                    ls.append(pla.jid)

        self.queueaction(self.checktimersetting)
        
        self.sendall('volity.game_validation', self.refstate)
        self.sendbookkeeper('volity.prepare_game',
            newgame, ls, callback=self.gotpreparegame)

    def gotpreparegame(self, tup):
        reason = 'error'
        reasonlist = []
        
        try:
            try:
                res = sched.Deferred.extract(tup)
            except rpc.RPCFault, ex:
                self.log.info('prepare_game rpc returned fault, ignoring: %s',
                    ex)
                res = True
            
            if (res == True):
                # Authorized!
                if (self.prevrefstate == STATE_SETUP):
                    self.queueaction(self.begingame)
                    return
                if (self.prevrefstate == STATE_SUSPENDED):
                    self.queueaction(self.unsuspendgame)
                    return
                self.log.error('prepare_game returned in wrong prevstate %s',
                    self.prevrefstate)
                return
                
            if (type(res) != list):
                raise rpc.RPCFault(605, 'prepare_game did not return array')
            if (len(res) < 1):
                raise rpc.RPCFault(606, 'prepare_game returned empty array')
            if (not (type(res[0]) in [str, unicode])):
                raise rpc.RPCFault(605, 'prepare_game array element 1 not string')
            reason = res[0]
            if (len(res) >= 2):
                if (type(res[1]) != list):
                    raise rpc.RPCFault(605, 'prepare_game array element 2 not list')
                reasonlist = res[1]

            self.log.info('game authorization failed: %s, %s',
                reason, reasonlist)
                
        except sched.TimeoutException, ex:
            self.log.warning('prepare_game rpc timed out: %s', ex)
        except rpc.RPCFault, ex:
            self.log.warning('prepare_game rpc returned fault: %s', ex)
        except interface.StanzaError, ex:
            self.log.warning('prepare_game rpc returned stanza error: %s', ex)
        except Exception, ex:
            self.log.warning('prepare_game rpc raised exception',
                exc_info=True)

        # We now know that authorization failed. (Or we got a bad return
        # value, which counts as the same thing.) We must unready some or
        # all players, go back to setup/suspended state, and then tell
        # the table what happened.

        self.removenonliveplayers()
        
        ls = []
        if (reasonlist):
            for pla in self.players.values():
                if (pla.ready and pla.jid in reasonlist):
                    ls.append(pla)
        
        if (not ls):
            ls = [ pla for pla in self.players.values() if pla.ready ]

        if (ls):
            self.log.info('marking %d player(s) unready due to auth failure',
                len(ls))
            for pla in ls:
                pla.ready = False
            self.queueaction(self.reportunreadylist, ls)
        
        self.refstate = self.prevrefstate
        self.prevrefstate = None
        self.log.info('game is now in %s', self.refstate)
        
        if (self.refstate == STATE_SETUP):
            self.gameseatlist = None
            for seat in self.seatlist:
                seat.ingame = False
                seat.eliminated = False
            
        self.sendall('volity.game_validation', self.refstate)

        if (reason in ['players_not_responding', 'players_not_authorized']):
            lstext = ', '.join(reasonlist)
            tok = game.FailureToken('volity.'+reason, game.Literal(lstext))
        elif (reason in ['game_record_conflict', 'game_record_missing']):
            tok = game.FailureToken('volity.'+reason)
        elif (reason == 'error'):
            tok = game.FailureToken('volity.prepare_game_failure',
                game.Literal('internal referee error'))
        else:
            tok = game.FailureToken('volity.prepare_game_failure',
                'volity.'+reason)
        self.sendall('volity.message', tok.getlist())

        # We changed back to setup/suspended, so we need to kick the timers
        self.queueaction(self.checktimersetting)
        
    def begingame(self):
        """begingame() -> None

        Game-start handler. Sets up all the internal state which we will
        use to track the game. (Game-specific setup is handled by the
        game's begingame() method.)

        This creates the gameseatlist, which is the list of seats that are
        involved in this particular game. It also adds the current seated
        players to each seat's history -- the list which will be used when
        creating the game record.
        """
        
        if (self.refstate != STATE_AUTHORIZING
            or self.prevrefstate != STATE_SETUP):
            self.log.error(
                'entered begingame when state was %s, previous state was %s',
                self.refstate, self.prevrefstate)
            return
        
        self.log.info('game beginning!')
        self.refstate = STATE_ACTIVE
        self.prevrefstate = None
        self.gamestarttime = time.time()

        # add current seat.playerlists to seat accumulated totals
        for seat in self.gameseatlist:
            for pla in seat.playerlist:
                barejid = pla.jid.getbare()
                seat.playerhistory[barejid] = True
        
        self.game.begingame()
        self.unreadyall(False)
        self.sendall('volity.start_game')

        # This will check against the immutable seat listings.
        self.queueaction(self.checktimersetting)

    def endgame(self, winners, cancelled=False):
        """endgame() -> None

        Game-end handler. (Game-specific shutdown is handled by the game's
        endgame() method.)

        This creates and fires off the game record, if that's desired. It
        also removes any Player objects corresponding to players who have
        left the MUC. (Remember, we keep Player objects for unavailable
        seated players, but only during of the game.)
        """
        
        if (not cancelled):
            if (self.refstate in
                [STATE_SETUP, STATE_SUSPENDED, STATE_AUTHORIZING]):
                self.log.error('entered endgame when state was %s',
                    self.refstate)
                return
        else:
            if (self.refstate == STATE_SETUP):
                self.log.error('cancelled game when state was %s',
                    self.refstate)
                return

        self.unreadyall(False)       # Might be needed in the killgame case
        self.sendall('volity.end_game')
        self.game.endgame(cancelled)

        seatmap = {}
        for seat in self.gameseatlist:
            seatmap[seat.id] = seat.getplayerhistory()

        self.log.info('winners: %s', winners)
        self.log.info('seat inhabitants: %s', seatmap)
        dic = {
            'winners' : winners,
            'seats' : seatmap,
            'end_time' : time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
            'start_time' : time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(self.gamestarttime)),
            'game_uri' : self.parlor.gameclass.ruleseturi,
            'parlor' : unicode(self.parlor.jid.getbare())
        }
        if (cancelled):
            dic['finished'] = False

        if (self.recordgames):
            ### check callback result, async
            self.queueaction(self.sendbookkeeper, 'volity.record_game', dic)

        self.removenonliveplayers()
            
        self.log.info('game ending!')
        self.refstate = STATE_SETUP
        self.prevrefstate = None
        self.killgame = False
        self.gamescompleted += 1

        self.gameseatlist = None
        for seat in self.seatlist:
            seat.ingame = False
            seat.eliminated = False
            
        # Check just in case the room is empty of all but bots.
        self.queueaction(self.checktimersetting)

    def suspendgame(self, jidstr=None):
        """suspendgame() -> None

        Game-suspension handler. (Game-specific work is handled by the
        game's suspendgame() method.)

        This removes any Player objects corresponding to players who have
        left the MUC. (Remember, we keep Player objects for unavailable
        seated players, but only during of the game.)
        """
        
        if (self.refstate in [STATE_SETUP, STATE_SUSPENDED]):
            self.log.error('tried to suspend when state was %s', self.refstate)
            return

        if (not jidstr):
            # assumes resource didn't change
            jidstr = self.jid

        self.unreadyall(False)
        self.sendall('volity.suspend_game', jidstr)
        self.game.suspendgame()

        self.removenonliveplayers()
            
        self.log.info('game suspended')
        self.refstate = STATE_SUSPENDED

        # Throw in this check, just in case.
        self.queueaction(self.checktimersetting)
        
    def unsuspendgame(self):
        """unsuspendgame() -> None

        Game-resumption handler. (Game-specific work is handled by the
        game's unsuspendgame() method.)

        This does not modify gameseatlist, but it does add the current
        seated players to each seat's history.
        """

        if (self.refstate != STATE_AUTHORIZING
            or self.prevrefstate != STATE_SUSPENDED):
            self.log.error(
                'tried unsuspendgame when state was %s, previous state was %s',
                self.refstate, self.prevrefstate)
            return

        for seat in self.gameseatlist:
            if (not seat.playerlist):
                self.log.error('tried to unsuspend when seat %s was empty',
                    seat.id)
                return

        self.log.info('game unsuspended')
        self.refstate = STATE_ACTIVE
        self.prevrefstate = None

        # add current seat.playerlists to seat accumulated totals
        for seat in self.gameseatlist:
            for pla in seat.playerlist:
                barejid = pla.jid.getbare()
                seat.playerhistory[barejid] = True

        self.game.unsuspendgame()
        self.unreadyall(False)
        self.sendall('volity.resume_game')
        
        # This will check against the immutable seat listings.
        self.queueaction(self.checktimersetting)

    def endwork(self):
        """endwork() -> None

        The 'end' state handler. This destroys the MUC room. Then, it does
        its best to blow away all of the referee's member fields, so that
        everything can be garbage-collected efficiently.
        """

        self.settimer(None)
                
        if (self.mucrunning):
            try:
                msg = interface.Node('iq',
                    attrs={'type':'set', 'to':unicode(self.muc)})
                newqnod = msg.setchild('query',
                    namespace=interface.NS_MUC_OWNER)
                newqnod.setchild('destroy').setchilddata('reason',
                    self.mucshutdownreason)
                self.conn.send(msg)
            except:
                # ignore send errors
                pass 
            
        # Tear down everything
        
        self.game.destroy()
        self.game.referee = None
        self.game = None
        self.players.clear()
        self.playernicks.clear()
        self.seats.clear()
        self.seatlist = []
        self.gameseatlist = None
        self.rpccli = None
        self.discoinfo = None
        self.gamewrapperopset = None
        self.parlor = None
        

class Player:
    """Player: Represents a player at a table.

    A player is identified by a full JID (including resource string).
    During setup (and suspension), the referee only tracks players who are
    actually present at the table; if a player becomes unavailable, his
    Player object is removed. However, while the game is in progress,
    the referee tracks everyone who is supposed to be seated at the table.
    (Unavailable players remain in the Players list, so that they can be
    recognized should they return.)

    Player(ref, jid, nick, isbot=False) -- constructor.

    The *ref* is the Referee which created this Player. The *jid* is the
    player's (real) JID; *nick* is his MUC nickname (the resource part only).
    The *isbot* flag indicates whether the player is known to be a robot.
    (This may be because of entity capabilities, or because the bot was
    created directly by the referee.)

    Public methods:

    send() -- send an RPC to this player.

    Publicly-readable fields:

    jid -- the player's real JID, as a JID object.
    jidstr -- the player's real JID, as a string.
    nick -- the MUC nickname (resource part only).
    isbot -- is this player a robot?
    live -- is this player available at the table?
    aware -- is the player's client capable of receiving RPCs?
    seat -- the Seat the player is sitting in, or None.
    ready -- is the player "ready"? (Can only be True if seated.)
    """
    
    def __init__(self, ref, jid, nick, isbot=False):
        self.referee = ref
        self.jid = jid
        self.jidstr = unicode(jid)
        self.nick = nick
        self.isbot = isbot
        
        self.live = True
        self.aware = False
        self.seat = None
        self.ready = False

    def __str__(self):
        return '<Player %s \'%s\'>' % (self.jidstr, self.nick)

    def __unicode__(self):
        return u'<Player %s \'%s\'>' % (self.jidstr, self.nick)

    def send(self, methname, *args, **keywords):
        """send(methname, *args, **keywords)

        Send an RPC to this player. See the description of the sendplayer()
        method in the game.Game class.
        """
        self.referee.game.sendplayer(self, methname, *args, **keywords)

class Validator:
    """Validator: Represents a set of argument-type restrictions on a
    Jabber RPC call.

    Various parts of the Volity system accept RPCs. Most of them want to
    check the number and type of the RPC arguments, and return appropriate
    RPC faults if the arguments are wrong. However, type-checking code is
    prolix and tedious. This class allows an RPC opset to check the arguments
    easily.

    A Validator also allows you to check other Volity state conditions. For
    example, you can set a Validator to reject its RPC while the game is
    in progress.

    By default, a Validator is completely permissive. You set the conditions
    that it checks by specifying a mapping of keywords to values. You may
    set these either when the Validator is constructed, or afterwards (using
    the set() method).

    These are the keywords which Validators understand:

        state=*str*
            Require the referee to be in a particular state. The state
            may be one of 'setup', 'active', 'disrupted', 'suspended',
            'abandoned', 'authorizing'. Or, it may contain several of
            those values, as a space-delimited string.
        afoot=*bool*
            If the value is True, this requires the referee to be 'active',
            'disrupted', or 'abandoned'. If False, it requires the referee
            to be in 'setup' or 'suspended'.
        seated=*bool*
            Requires the sending player to be seated (if True) or standing
            (if False).
        admin=*bool*
            If True, requires the sending player to be a parlor administrator.
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
            this class.

            You may use the Python built-in object Ellipsis instead of the
            literal '...'.

    If you set contradictory conditions, the resulting behavior is poorly
    defined. For example, if you set state='setup' and then set afoot=True,
    the Validator may ignore one condition; or it may try to verify both,
    and therefore reject all RPCs. Similarly, setting args=None and
    argcount=1 would cause problems.

    Validator(**keywords) -- constructor.

    Create a Validator with the given conditions. You might say, for example:

        val = Validator(argcount=2, afoot=False)

    Public methods:

    set() -- set more conditions.
    check() -- check the validity of an RPC call.

    Static method:

    typespecstring() -- turn one or more types into a human-readable string.
    """
    
    def typespecstring(typ):
        """typespecstring(typ) -> str

        Turn a type, or a tuple of types, into a human-readable string.
        The *typ* must be a Python type object (str, int, or so forth),
        or else a tuple of type objects. The result is a string of the
        form 'string or int or ...'
        """
        
        if (type(typ) != tuple):
            typ = ( typ, )
        ls = []
        for val in typ:
            if (val == str):
                res = 'string'
            elif (val == int):
                res = 'int'
            elif (val == bool):
                res = 'bool'
            elif (val == float):
                res = 'float'
            elif (val == list):
                res = 'array'
            elif (val == dict):
                res = 'struct'
            else:
                res = str(val)
            ls.append(res)
        return ' or '.join(ls)
    typespecstring = staticmethod(typespecstring)

    def __init__(self, **keywords):
        self.phasesetup = True
        self.phaseactive = True
        self.phasedisrupted = True
        self.phaseabandoned = True
        self.phasesuspended = True
        self.phaseauthorizing = True
        self.seated = True
        self.observer = True
        self.administrator = False
        self.argcount = None
        self.arglist = None
        self.hasarglist = False
        if (keywords):
            self.set(keywords)

    def set(self, arg_=None, **keywords):
        """set(arg_=None, **keywords) -> None

        Set more conditions. You have several options for setting keyword-
        value pairs:

            validator.set( argcount=2 )
            validator.set( {'argcount': 2} )
            validator.set( [ ('argcount' , 2) ] )
            validator.set(val) # *val* is anything which can be cast to a dict

        See the class documentation for the list of keywords.

        If you call this multiple times, or call it on a Validator which
        was constructed with conditions, then the conditions accumulate.
        (But see the class documentation for warnings about contradictory
        conditions.)
        """

        if (arg_ != None):
            arg_ = dict(arg_)
            keywords.update(arg_)
        
        if (keywords.has_key('state')):
            self.phasesetup = False
            self.phaseactive = False
            self.phasedisrupted = False
            self.phaseabandoned = False
            self.phasesuspended = False
            self.phaseauthorizing = False
            val = keywords.pop('state')
            ls = val.split()
            if (STATE_SETUP in ls):
                self.phasesetup = True
            if (STATE_ACTIVE in ls):
                self.phaseactive = True
            if (STATE_DISRUPTED in ls):
                self.phasedisrupted = True
            if (STATE_ABANDONED in ls):
                self.phaseabandoned = True
            if (STATE_SUSPENDED in ls):
                self.phasesuspended = True
            if (STATE_AUTHORIZING in ls):
                self.phaseauthorizing = True
            
        if (keywords.has_key('afoot')):
            val = keywords.pop('afoot')
            if (val):
                self.phasesetup = False
                self.phaseactive = True
                self.phasedisrupted = True
                self.phaseabandoned = True
                self.phasesuspended = False
                self.phaseauthorizing = False
            else:
                self.phasesetup = True
                self.phaseactive = False
                self.phasedisrupted = False
                self.phaseabandoned = False
                self.phasesuspended = True
                self.phaseauthorizing = False
        if (keywords.has_key('seated')):
            val = keywords.pop('seated')
            if (val):
                self.observer = False
                self.seated = True
            else:
                self.observer = True
                self.seated = False
        if (keywords.has_key('admin')):
            val = keywords.pop('admin')
            if (val):
                self.administrator = True
        if (keywords.has_key('argcount')):
            val = keywords.pop('argcount')
            self.argcount = int(val)
        if (keywords.has_key('args')):
            val = keywords.pop('args')
            if (type(val) != list):
                val = [ val ]
            self.arglist = val
            self.hasarglist = True
        if (keywords):
            raise ValueError('unrecognized keywords in validator: '
                + ', '.join(keywords.keys()))
            
    def check(self, ref, sender, callname, callargs):
        """check(ref, sender, callname, callargs) -> None

        Checks the validity of an RPC invocation. The *ref* is the referee
        which received the RPC; the *sender* is the JID which sent the
        RPC; *callname* and *callargs* describe the RPC invocation.

        If a problem is detected, this method generates either a Volity
        failure token or an RPC fault. If the invocation is valid, this
        simply returns.

        (Note that no conditions check the *callname*, since this is the
        Validator for that callname. It is used only to generate readable
        RPC faults.)
        """
        
        if (ref.refstate == STATE_SETUP and not self.phasesetup):
            raise game.FailureToken('volity.game_not_in_progress')
        if (ref.refstate == STATE_ACTIVE and not self.phaseactive):
            raise game.FailureToken('volity.game_in_progress')
        if (ref.refstate == STATE_DISRUPTED and not self.phasedisrupted):
            raise game.FailureToken('volity.game_in_progress')
        if (ref.refstate == STATE_ABANDONED and not self.phaseabandoned):
            raise game.FailureToken('volity.game_in_progress')
        if (ref.refstate == STATE_SUSPENDED and not self.phasesuspended):
            raise game.FailureToken('volity.game_not_in_progress')
        if (ref.refstate == STATE_AUTHORIZING and not self.phaseauthorizing):
            raise game.FailureToken('volity.authorizing_in_progress')

        if (not self.seated):
            pla = ref.game.getplayer(sender)
            if (pla.seat):
                raise game.FailureToken('volity.are_seated')
        if (not self.observer):
            pla = ref.game.getplayer(sender)
            if (not pla.seat):
                raise game.FailureToken('volity.not_seated')

        if (self.administrator):
            if (not ref.parlor.isadminjid(sender)):
                raise interface.StanzaNotAuthorized('admin operations are restricted')
            ref.log.warning('admin RPC from <%s>: %s %s',
                unicode(sender), callname, unicode(callargs))

        if (self.argcount != None):
            if (len(callargs) != self.argcount):
                st = '%s takes %d argument' % (callname, self.argcount)
                if (self.argcount != 1):
                    st = st+'s'
                raise rpc.RPCFault(604, st)

        if (self.hasarglist):
            ls = self.arglist
            gotellipsis = False
            ix = 0
            while (ix < len(ls)):
                typ = ls[ix]
                if (typ == '...' or typ == Ellipsis):
                    gotellipsis = True
                    break
                if (typ == None or typ == (None,)):
                    break
                if (ix >= len(callargs)):
                    if (type(typ) == tuple and None in typ):
                        pass # ok
                    else:
                        raise rpc.RPCFault(604,
                            '%s: missing argument %d' % (callname, ix+1))
                else:
                    argtyp = type(callargs[ix])
                    if (argtyp == unicode):
                        argtyp = str
                    if (argtyp == typ or (type(typ) == tuple and argtyp in typ)):
                        pass # ok
                    elif (argtyp == int and
                        (float == typ or (type(typ) == tuple and float in typ))):
                        pass # ok
                    else:
                        raise rpc.RPCFault(605,
                            '%s: argument %d must be %s, not %s' % (callname,
                                ix+1,
                                self.typespecstring(typ),
                                self.typespecstring(argtyp)))
                ix = ix+1
                
            # ix is now the length of ls, or perhaps the index of the
            # ellipsis or None entry (which indicates the effective end of ls)
            if (len(callargs) > ix):
                if (gotellipsis):
                    return
                raise rpc.RPCFault(604, '%s: too many arguments' % callname)
        
class WrapGameOpset(rpc.WrapperOpset):
    """WrapGameOpset: An Opset which wraps all RPCs in the game.* namespace.

    This Opset provides checking of argument types. It also checks a few
    other universal conditions: you cannot send RPCs before the referee is
    ready, and you cannot send RPCs unless you are present at the table.

    WrapGameOpset(ref, subopset)

    Public methods:

    setopset() -- change the contained opset. (inherited)
    validation() -- define argument-type checking for an RPC.
    precondition() -- checks authorization and argument types before an
    RPC handler.
    __call__() -- invoke an RPC.
    """
    
    def __init__(self, ref, subopset):
        rpc.WrapperOpset.__init__(self, subopset)
        self.referee = ref
        self.validators = {}

    def validation(self, callname, **keywords):
        """validation(callname, **keywords) -> None

        Define argument-type checking for an RPC. The *callname* is the
        name part of the RPC (that is, excluding the namespace). For the
        possible *keywords*, see the Validator class.

        If you call this multiple times for the same RPC, the keywords are
        accumulated.
        """
        
        val = self.validators.get(callname)
        if (not val):
            val = Validator()
            self.validators[callname] = val
        val.set(keywords)

    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs)

        Checks authorization and argument types before an RPC handler.
        Only players at the table are permitted to send RPCs. We also use
        Validators to check the number and type of the RPC arguments.
        """
        
        if (self.referee.state != 'running'):
            raise game.FailureToken('volity.referee_not_ready')

        if (not self.referee.players.has_key(unicode(sender))):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(sender))

        val = self.validators.get(None)
        if (val):
            val.check(self.referee, sender, nametail, callargs)
        val = self.validators.get(nametail)
        if (val):
            val.check(self.referee, sender, nametail, callargs)
                
        self.referee.activitytime = time.time()

    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> <rpc outcome>

        Invoke an RPC. This is invoked by the Zymb RPC-handling service.
        """
        
        try:
            val = rpc.WrapperOpset.__call__(self, sender,
                callname, *callargs)
            return self.referee.resolvemetharg(val,
                self.referee.game.makerpcvalue)
        except rpc.CallNotFound:
            raise rpc.RPCFault(603,
                'method does not exist in game opset: ' + callname)
        except volent.FailureToken:
            raise
        except rpc.RPCResponse:
            raise
        except rpc.RPCFault:
            raise
        except interface.StanzaError:
            raise
        except Exception, ex:
            st = str(ex.__class__) + ': ' + str(ex)
            self.referee.log.error(
                'uncaught exception in game opset: %s (from %s)',
                unicode(sender), callname,
                exc_info=True)
            raise rpc.RPCFault(608, st)
        
class RefVolityOpset(rpc.MethodOpset):
    """RefVolityOpset: The Opset which responds to volity.* namespace
    RPCs.

    RefVolityOpset(ref) -- constructor.

    The *ref* is the Referee to which this Opset is attached.

    Methods:

    precondition() -- checks authorization and argument types before an
    RPC handler.

    Handler methods:

    rpc_sit() -- handle a player sit request.
    rpc_stand() -- handle a player stand request.
    rpc_ready() -- handle a player ready request.
    rpc_unready() -- handle a player unready request.
    rpc_add_bot() -- handle a player request for a new bot.
    rpc_remove_bot() -- handle a player request to remove a bot.
    rpc_suspend_game() -- handle a player game-suspend request.
    rpc_set_language() -- handle a player request to change the table language.
    rpc_record_games() -- handle a player request to change the game-record
        flag.
    rpc_show_table() -- handle a player request to change the show-table flag.
    rpc_kill_game() -- handle a player request to change the kill-game flag.
    rpc_send_state() -- handle a player request for the complete table state.
    rpc_invite_player() -- handle a player request to invite another player to
        the table.
    """
    
    def __init__(self, ref):
        self.referee = ref
        self.validators = {}

        self.validators['__default'] = Validator(state=STATE_SETUP)
        self.validators['sit'] = Validator(afoot=False,
            args=[str, (str, None)])
        self.validators['stand'] = Validator(afoot=False,
            args=str)
        self.validators['ready'] = Validator(afoot=False,
            argcount=0)
        self.validators['unready'] = Validator(afoot=False,
            argcount=0)
        self.validators['add_bot'] = Validator(args=[(str, None), (str, None)])
        self.validators['remove_bot'] = Validator(args=str)
        self.validators['suspend_game'] = Validator(afoot=True,
            argcount=0, seated=True)
        self.validators['set_language'] = Validator(state=STATE_SETUP,
            args=str)
        self.validators['record_games'] = Validator(state=STATE_SETUP,
            args=bool)
        self.validators['show_table'] = Validator(state=STATE_SETUP,
            args=bool)
        self.validators['kill_game'] = Validator(state=STATE_SUSPENDED,
            args=bool)
        self.validators['send_state'] = Validator(argcount=0)
        self.validators['invite_player'] = Validator(args=[str, (str, None)])

    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs)

        Checks authorization and argument types before an RPC handler.
        Only players at the table are permitted to send RPCs. We also use
        Validators to check the number and type of the RPC arguments.
        """
        
        if (self.referee.state != 'running'):
            raise game.FailureToken('volity.referee_not_ready')

        if (not self.referee.players.has_key(unicode(sender))):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(sender))

        val = self.validators.get(namehead)
        if (not val):
            val = self.validators.get('__default')
        if (val):
            val.check(self.referee, sender, namehead, callargs)
            
        self.referee.activitytime = time.time()

    def rpc_sit(self, sender, *args):
        return self.referee.playersit(sender, *args)

    def rpc_stand(self, sender, *args):
        return self.referee.playerstand(sender, args[0])

    def rpc_ready(self, sender, *args):
        return self.referee.playerready(sender)

    def rpc_unready(self, sender, *args):
        return self.referee.playerunready(sender)

    def rpc_add_bot(self, sender, *args):
        if (len(args) != 0 and len(args) != 2):
            raise rpc.RPCFault(604, 'add_bot takes zero or two arguments')
        uri = None
        jid = None
        if (len(args) >= 1):
            uri = args[0]
        if (len(args) >= 2):
            jid = args[1]
            if (jid):
                jid = interface.JID(jid)
        if ((not jid) or (self.referee.jid == jid)
            or (self.referee.parlor.jid == jid)):
            jid = None
            
        if (not jid):
            return self.referee.playeraddbot(sender, uri)
        else:
            return self.referee.playeraddexternalbot(sender, uri, jid)
    
    def rpc_remove_bot(self, sender, *args):
        return self.referee.playerremovebot(sender, args[0])
    
    def rpc_suspend_game(self, sender, *args):
        return self.referee.playersuspend(sender)
    
    def rpc_set_language(self, sender, *args):
        return self.referee.configsetlanguage(sender, args[0])

    def rpc_record_games(self, sender, *args):
        return self.referee.configrecordgames(sender, args[0])

    def rpc_show_table(self, sender, *args):
        return self.referee.configshowtable(sender, args[0])

    def rpc_kill_game(self, sender, *args):
        return self.referee.configkillgame(sender, args[0])

    def rpc_send_state(self, sender, *args):
        player = self.referee.game.getplayer(sender)
        if (not player):
            raise Exception('sender could not be found')
        self.referee.sendfullstate(player)
        return None

    def rpc_invite_player(self, sender, *args):
        return self.referee.playerinvite(sender, *args)

        
class RefAdminOpset(rpc.MethodOpset):
    """RefAdminOpset: The Opset which responds to admin.* namespace
    RPCs.

    RefAdminOpset(ref) -- constructor.

    The *ref* is the Referee to which this Opset is attached.

    Methods:

    precondition() -- checks authorization before an RPC handler.

    Handler methods:

    rpc_status() -- return assorted status information.
    rpc_players() -- return a list of player JIDs at the table.
    rpc_bots() -- return a list of bot JIDs at the table.
    rpc_seats() -- return the list of seats; also the sublists of required
        seats, occupied seats, and (if the game is in progress) the seats
        involved in the current game.
    rpc_seat -- return details about a seat and who is sitting there.
    rpc_announce() -- yell a message to the table.
    rpc_shutdown() -- immediately shut down this Referee and its table.
    """
    
    def __init__(self, ref):
        self.referee = ref
        self.parlor = self.referee.parlor

    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs)

        Checks authorization before an RPC handler. Only the admin JID
        is permitted to send admin.* namespace RPCs. If no admin JID was
        set, then all admin.* RPCs are rejected.
        """
        
        if (not self.parlor.isadminjid(sender)):
            raise interface.StanzaNotAuthorized('admin operations are restricted')
        # Log the command that we're about to perform.
        if (namehead in ['announce','shutdown']):
            self.parlor.log.warning('admin command from <%s>: %s %s',
                unicode(sender), namehead, unicode(callargs))

    def rpc_status(self, sender, *args):
        """rpc_status() -> dict

        Return assorted status information. This returns a Jabber-RPC struct
        containing these fields:

            agentstate: The Zymb agent state ("running" is normal operation).
            state: The Volity referee state ("setup", "active", "disrupted",
                "abandoned", "suspended").
            players: The number of players (including bots).
            bots: The number of bots.
            startup_time: When this table was started.
            startup_at: How long it's been since the table was started.
            last_activity_at: How long it's been since there was any activity
                at the table.
            games_completed: How many games have been completed at the table.
        """

        if (len(args) != 0):
            raise rpc.RPCFault(604, 'status: no arguments')
        dic = {}

        dic['agentstate'] = self.referee.state
        dic['state'] = self.referee.refstate
        dic['players'] = len(self.referee.players)
        ls = [ pla for pla in self.referee.players.values() if pla.isbot ]
        dic['bots'] = len(ls)
        dic['startup_time'] = time.ctime(self.referee.startuptime)
        dic['startup_at'] = volent.descinterval(
            self.referee.startuptime,
            limit=2)
        dic['last_activity_at'] = volent.descinterval(
            self.referee.activitytime,
            limit=2)
        dic['games_completed'] = self.referee.gamescompleted

        return dic

    def rpc_players(self, sender, *args):
        """rpc_players() -> list

        Return a list of the (real) JIDs of all players at this table
        (including bots).
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'players: no arguments')
        return self.referee.players.keys()

    def rpc_bots(self, sender, *args):
        """rpc_bots() -> list

        Return a list of the (real) JIDs of all bots at this table.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'bots: no arguments')
        return [ id for (id, pla) in self.referee.players.items() if pla.isbot ]

    def rpc_seats(self, sender, *args):
        """rpc_seats() -> dict

        This returns a Jabber-RPC struct containing these fields:

            allseats: The IDs of all seats in the game configuration.
            reqseats: The IDs of all required seats.
            occupiedseats: The IDs of all occupied seats.
            gameseats: The IDs of all seats involved in the current game.
                (Only present if a game is in progress.)
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'seats: no arguments')
        dic = {}
        ls = [ seat.id for seat in self.referee.seatlist ]
        dic['allseats'] = ' '.join(ls)
        ls = [ seat.id for seat in self.referee.seatlist if seat.required ]
        dic['reqseats'] = ' '.join(ls)
        ls = [ seat.id for seat in self.referee.seatlist if seat.playerlist ]
        dic['occupiedseats'] = ' '.join(ls)
        if (self.referee.gameseatlist):
            ls = [ seat.id for seat in self.referee.gameseatlist ]
            dic['gameseats'] = ' '.join(ls)
        return dic
            
    def rpc_seat(self, sender, *args):
        """rpc_seat(seatid) -> dict

        Return information about the seat *seatid*. This returns a Jabber-RPC
        struct containing these fields:

            id: The seat ID.
            required: Whether this is a required seat.
            ingame: Whether this seat is involved in the current game.
            players: A list of the players currently in this seat.
            history: A list of the players who have sat in this seat at any
                time in the current game.
        """

        if (len(args) != 1):
            raise rpc.RPCFault(604, 'seat STRING')
        seat = self.referee.game.getseat(args[0])
        if (not seat):
            raise rpc.RPCFault(606, 'not a seat ID')
        dic = {}
        dic['id'] = seat.id
        dic['required'] = seat.required
        dic['ingame'] = seat.ingame
        dic['eliminated'] = seat.eliminated
        dic['players'] = [ pla.jidstr for pla in seat.playerlist ]
        dic['history'] = seat.getplayerhistory()
        return dic
            
    def rpc_announce(self, sender, *args):
        """rpc_announce(msg) -> str

        Yell a message on the table. The message is broadcast as an ordinary
        group-chat message, so all connected clients will see it.

        Returns a message saying that the message was sent.
        """
        
        if (len(args) != 1):
            raise rpc.RPCFault(604, 'announce STRING')
        val = args[0]
        if (not type(val) in [str, unicode]):
            raise rpc.RPCFault(605, 'announce STRING')
        self.referee.announce(val)
        return 'sent'

    def rpc_shutdown(self, sender, *args):
        """rpc_shutdown() -> str

        Immediately shut down this Referee and its table. This kills the game
        if it is in progress, so use it considerately.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'shutdown: no arguments')
        self.referee.queueaction(self.referee.stop)
        return 'stopping referee'

# late imports
import game
import actor
