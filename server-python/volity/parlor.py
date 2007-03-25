import os, time, types
import logging
import volent
from zymb import sched, jabber
import zymb.jabber.dataform
import zymb.jabber.disco
import zymb.jabber.keepalive
from zymb.jabber import interface
from zymb.jabber import rpc

REFEREE_STARTUP_TIMEOUT = 120
BOOKKEEPER_DEFAULT_RPC_TIMEOUT = 30

class Parlor(volent.VolEntity):
    """Parlor: The implementation of a Volity parlor (i.e., game server).

    This is the central class which runs games on a system. When you
    run the volityd.py script, it creates a Parlor and starts it running.
    
    Parlor is a Jabber client (although it is not a subclass of the
    Zymb Jabber client agent; instead, it creates one as a subsidiary
    object). It sits around and waits for the "new table" command,
    which tells it to start up a Referee. That's nearly all it does,
    actually, although there are some extra debugging and administration
    features.

    Parlor(config) -- constructor.

    The *config* is a ConfigFile object, containing various options needed
    to set up the Parlor. The volityd.py script creates this. For the
    record, the significant fields are:

        jid: Used when connecting to Jabber
        jid-resource: Resource string to use (optional, overrides the
            one in *jid*; if no resource is provided in either *jid* or
            *jid-resource*, then "volity" is used)
        host: Jabber server host to use (optional, overrides the one
            in *jid*)
        password: Used when connecting to Jabber
        game: Name of Python class which implements the game
        bot: Name of Python class which implements the bot (optional;
            if not provided, then the parlor will not create bots)
        admin: JID which is permitted to send admin commands (optional)
            (may be comma-separated list of JIDs)
        muchost: JID of Jabber MUC server (optional, defaults to
            "conference.volity.net")
        bookkeeper: JID of Volity bookkeeper (optional, defaults to
            "bookkeeper@volity.net/volity")
        contact-email: Contact email address of person hosting this
            parlor (optional)
        contact-jid: Contact Jabber address of person hosting this
            parlor (optional)
        keepalive: If present, send periodic messages to exercise the
            Jabber connection (optional)
        keepalive-interval: If present, send periodic messages every
            *keepalive-interval* seconds to exercise the Jabber
            connection (optional)

    Agent states and events:

    state 'start': Initial state. Start up the Jabber connection process.
        When Jabber is fully connected, jump to 'ready'.
    state 'ready': Send out initial presence. In this state, the Parlor
        accepts new_table requests.
    state 'end': The connection is closed.

    Public methods:

    start() -- begin activity. (inherited)
    stop() -- stop activity. (inherited)
    requeststop() -- stop activity in a particular way.
    isadminjid() -- check whether the given JID is an administrator JID.

    Significant fields:

    jid -- the JID by which this Parlor is connected.
    conn -- the Jabber client agent.
    adminjids -- the JID which is permitted to send admin commands.
    gameclass -- the Game subclass which implements the game.
    botclasses -- list of Bot subclasses which implement the bots (may be
        empty).
    botfactories -- list of factory JIDs which the parlor recommends.
    gamename -- the game's human-readable name (taken from gameclass).
    referees -- dict mapping resource strings to Referees.
    actors -- dict mapping resource strings to Actors.
    online -- whether the Parlor is accepting new_table requests.

    Internal methods:

    handlemessage() -- handle a Jabber message stanza.
    handlepresence() -- handle a Jabber presence stanza.
    sendbookkeeper() -- send an RPC to the Volity bookkeeper.
    defaultcallback() -- generic RPC callback, used to catch errors.
    newtable() -- create a new table and Referee.
    refereeready() -- callback invoked when a Referee is successfully
        (or unsuccessfully) created.
    refereedied() -- callback invoked when a Referee shuts down.
    actordied() -- callback invoked when an Actor (bot) shuts down.
    listopengames() -- create a DiscoItems list of currently-active games.
    listbots() -- create a DiscoItems list of available bot URIs.
    stopunlessgraceful() -- stop the given entity if we are in the middle
        of an ungraceful shutdown.
    handlerosterquery() -- accept a request to be added to someone's roster.
    endwork() -- 'end' state handler.

    """
    
    logprefix = 'volity.parlor'
    volityrole = 'parlor'

    def __init__(self, config):
        self.jabbersecurity = config.get('jabber-security')
        volent.VolEntity.__init__(self, config.get('jid'),
            config.get('password'), config.get('jid-resource'),
            config.get('host'), secure=self.jabbersecurity)

        # Set the default shutdown conditions
            
        self.shutdowngraceful = True
        self.shutdownrestart = True
        self.shutdownrestartdelay = True
        self.canrestart = config.has_key('restart-script')
        self.restartfunc = config.get('restart-func-')
        
        # Locate the game class.
        
        ls = config.get('game').split('.')
        if (len(ls) < 2):
            raise ValueError('gameclass must be of the form module.gameclass')
        classname = ls[-1]
        modpath = '.'.join(ls[ : -1 ])
        mod = __import__(modpath, globals(), locals(), [classname])
        gameclass = getattr(mod, classname)
        self.gameclass = gameclass
        
        if (gameclass == game.Game
            or (type(gameclass) != types.ClassType)
            or (not issubclass(gameclass, game.Game))):
            raise ValueError('gameclass must be a subclass of volity.game.Game')
        
        if (not gameclass.gamename):
            raise TypeError('gameclass does not define class.gamename')
        if (not gameclass.ruleseturi):
            raise TypeError('gameclass does not define class.ruleseturi')

        # Locate the bot classes (if there are any).
        self.botclasses = []

        botls = config.getall('bot')
        for botclassname in botls:
            ls = botclassname.split('.')
            if (len(ls) < 2):
                raise ValueError('botclass must be of the form module.botclass')
            classname = ls[-1]
            modpath = '.'.join(ls[ : -1 ])
            mod = __import__(modpath, globals(), locals(), [classname])
            botclass = getattr(mod, classname)
            self.botclasses.append(botclass)
        
            if (botclass == bot.Bot
                or (type(botclass) != types.ClassType)
                or (not issubclass(botclass, bot.Bot))):
                raise ValueError('botclass must be a subclass of volity.bot.Bot')

            if (botclass.gameclass == None
                or (type(botclass.gameclass) != types.ClassType)
                or (not issubclass(self.gameclass, botclass.gameclass))):
                raise ValueError('botclass does not play '+config.get('game'))

            if (not botclass.boturi):
                raise TypeError('botclass does not define class.boturi')

            if (botclass.boturi in
                [bc.boturi for bc in self.botclasses[:-1]]):
                raise TypeError('class.boturi used more than once')

        self.botfactories = []
        for jidstr in config.getall('bot-factory'):
            self.botfactories.append(interface.JID(jidstr))

        bookjidstr = config.get('bookkeeper', 'bookkeeper@volity.net/volity')
        self.bookkeeperjid = interface.JID(bookjidstr)
        if (not self.bookkeeperjid.getresource()):
            self.bookkeeperjid.setresource('volity')
                
        # Set the administrative JID (if present)
        
        self.adminjids = []
        if (config.get('admin')):
            val = config.get('admin')
            ls = [ interface.JID(subval.strip()) for subval in val.split(',') ]
            self.adminjids = ls

        # Various Jabber handlers.
        
        self.addhandler('end', self.endwork)
        self.conn.adddispatcher(self.handlepresence, name='presence')
        self.conn.adddispatcher(self.handlemessage, name='message')

        # Set up internal state.

        val = config.get('entity-name')
        if (not val):
            val = gameclass.gamename        
        self.gamename = val
        self.log.warning('Game parlor running: %s', self.gamename)

        self.referees = {}
        self.muchost = interface.JID(config.get('muchost',
            'conference.volity.net'))
        self.online = True
        self.startuptime = time.time()
        self.activitytime = None
        self.refereesstarted = 0
        self.visibility = config.getbool('visible', True)

        self.actors = {}
        
        # Set up the disco service
        
        disco = self.conn.getservice('discoservice')
        
        info = disco.addinfo()
        info.addidentity('volity', 'parlor', self.gamename)
        info.addfeature(interface.NS_CAPS)
        info2 = disco.addinfo(volent.VOLITY_CAPS_URI+'#'+volent.volityversion)
        info2.addidentity('volity', 'parlor', self.gamename)
        info2.addfeature(interface.NS_CAPS)

        form = jabber.dataform.DataForm()
        val = config.get('entity-desc')
        if (not val):
            val = gameclass.gamedescription
        form.addfield('description', val)
        form.addfield('ruleset', gameclass.ruleseturi)
        form.addfield('volity-role', self.volityrole)
        form.addfield('ruleset-version', gameclass.rulesetversion)
        val = gameclass.websiteurl
        if (not val):
            val = gameclass.ruleseturi
        form.addfield('website', val)
        if (config.get('contact-email')):
            form.addfield('contact-email', config.get('contact-email'))
        if (config.get('contact-jid')):
            form.addfield('contact-jid', config.get('contact-jid'))
        form.addfield('volity-version', volent.volityversion)
        if (self.visibility):
            val = '1'
        else:
            val = '0'
        form.addfield('visible', val)
        info.setextendedinfo(form)
        info2.setextendedinfo(form)

        infocap = jabber.disco.DiscoInfo()
        infocap.addfeature(volent.VOLITY_CAPS_URI+'#'+self.volityrole)
        disco.addinfo(volent.VOLITY_CAPS_URI+'#'+self.volityrole, infocap)

        # assumes resource didn't change
        items = disco.additems()
        items.additem(self.jid, node='ruleset',
            name='Ruleset URI')
        items.additem(self.jid, node='open_games',
            name='Open games at this parlor')
        items.additem(self.jid, node='bots',
            name='Bots available from this parlor')

        items = disco.additems('ruleset')
        items.additem(
            unicode(self.bookkeeperjid),
            node=gameclass.ruleseturi,
            name='Ruleset information (%s)' % gameclass.ruleseturi)

        items = disco.additems('open_games', self.listopengames)
        items = disco.additems('bots', self.listbots)

        # Set up the RPC service
        
        self.rpccli = self.conn.getservice('rpcclience')
        
        rpcserv = self.conn.getservice('rpcservice')
        assert isinstance(rpcserv.getopset(), volent.ClientRPCWrapperOpset)
        ops = rpcserv.getopset().getopset()
        dic = ops.getdict()
        dic['volity'] = ParlorVolityOpset(self)
        dic['admin'] = ParlorAdminOpset(self)

        # Set up a simple roster-handler to accept subscription requests.
        
        self.conn.adddispatcher(self.handlerosterquery, name='iq', type='set')

        # Set up the keepalive service

        keepaliveflag = False
        if (config.get('keepalive')):
            keepaliveflag = True
        keepaliveinterval = None
        if (config.get('keepalive-interval')):
            keepaliveinterval = int(config.get('keepalive-interval'))
        if (keepaliveflag or keepaliveinterval):
            if (keepaliveinterval):
                serv = jabber.keepalive.KeepAliveService(keepaliveinterval)
            else:
                serv = jabber.keepalive.KeepAliveService()
            self.conn.addservice(serv)
            self.addhandler('ready', serv.start)
            self.log.warning('sending keepalive messages to self at interval of %d seconds', serv.getinterval())

        # End of constructor.

    def handlemessage(self, msg):
        """handlemessage(msg) -> <stanza outcome>

        Handle a Jabber message stanza. The Parlor does not react to messages,
        so this just raises StanzaHandled to end processing for the stanza.
        """
        
        if (self.log.isEnabledFor(logging.DEBUG)):
            self.log.info('received message')
            #self.log.debug('received message:\n%s', msg.serialize(True))
        raise interface.StanzaHandled()

    def handlepresence(self, msg):
        """handlepresence(msg) -> <stanza outcome>

        Handle a Jabber presence stanza. The Parlor does not react to presence,
        so this just raises StanzaHandled to end processing for the stanza.
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
        raise interface.StanzaHandled()
            
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
            keywords['timeout'] = BOOKKEEPER_DEFAULT_RPC_TIMEOUT

        methargs = [ self.resolvemetharg(val) for val in methargs ]

        destjid = self.bookkeeperjid
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
            
    def newtable(self, sender, *args):
        """newtable(sender, *args) -> <RPC outcome>

        Create a new table and Referee. The RPC response will be the
        table JID, but that doesn't happen in this function; see the
        refereeready() callback.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'new_table takes no arguments')

        if (self.state != 'ready'):
            raise rpc.RPCFault(609, 'parlor is not yet ready')

        if (not self.online):
            raise volent.FailureToken('volity.offline')
            
        self.log.info('new table requested by %s...', unicode(sender))

        # see unique-nick discussion, bug 1308207
        # assumes resource didn't change
        refresource = 'ref_' + str(os.getpid()) + '_' + str(self.uniquestamp())
        muc = interface.JID(self.muchost)
        muc.setnode(refresource)

        # Create the actual Referee object.
        
        try:
            refclass = self.gameclass.refereeclass
            if (refclass):
                if (not issubclass(refclass, referee.Referee)):
                    raise Exception('refereeclass must be a subclass of Referee')
            else:
                refclass = referee.Referee
            ref = refclass(self, self.jid, self.password, refresource, muc)
        except Exception, ex:
            self.log.error('Unable to create referee',
                exc_info=True)
            raise rpc.RPCFault(608,
                'unable to create referee: ' + str(ex))
            
        assert (not self.referees.has_key(refresource))
        self.referees[refresource] = ref
        
        ref.start()

        ref.addhandler('end', self.refereedied, ref)
        self.addhandler('end', self.stopunlessgraceful, ref)

        # Now we wait until the Referee is finished starting up -- which
        # is defined as "successfully connected to the MUC". This requires
        # a Deferred handler, which is hard to use but easy to explain:
        # it has three possible outcomes, and exactly one of them will happen.
        # Either the Referee will start up, or it will shut down without
        # ever connecting, or we'll give up before either of the above.
        # (We set a timer of REFEREE_STARTUP_TIMEOUT for that last outcome.)
        #
        # Whichever happens, our refereeready() method will be called.

        defer = sched.Deferred(self.refereeready, ref)
        
        ac1 = ref.addhandler('running', defer, 'running')
        defer.addaction(ac1)
        ac2 = self.addtimer(defer, 'timeout', delay=REFEREE_STARTUP_TIMEOUT)
        defer.addaction(ac2)
        ac3 = ref.addhandler('end', defer, 'end')
        defer.addaction(ac3)
        
        raise defer

    def refereeready(self, ref, res):
        """refereeready(ref, res) -> <RPC outcome>

        Callback invoked when a Referee is successfully (or unsuccessfully)
        created. This is the continuation of newtable(). The *ref* is the
        Referee object that was created, and *res* is either 'running',
        'end', or 'timeout'.

        If the Referee is running, we return the table JID. If not, we
        return an RPC fault.
        """
        
        if (res == 'timeout'):
            self.log.warning('referee %s failed to start up in time -- killing', ref.resource)
            ref.stop()
            raise rpc.RPCFault(608, 'unable to start referee')
        if (res == 'end'):
            self.log.warning('referee %s died before responding', ref.resource)
            raise rpc.RPCFault(608, 'referee failed to start up')
        self.activitytime = time.time()
        self.refereesstarted += 1
        return unicode(ref.muc)

    def refereedied(self, ref):
        """refereedied(ref) -> None

        Callback invoked when a Referee shuts down. Remove the Referee
        from our internal table.
        """
        
        resource = ref.resource
        if (self.referees.has_key(resource)):
            assert ref == self.referees[resource]
            self.log.info('referee %s has terminated', resource)
            self.referees.pop(resource)

    def actordied(self, act):
        """actordied(ref) -> None

        Callback invoked when an Actor shuts down. Remove the Actor from
        our internal table. (Note: this can be called after the Parlor
        shuts down!)
        """
        
        resource = act.resource
        if (self.actors.has_key(resource)):
            assert act == self.actors[resource]
            self.log.info('actor %s has terminated', resource)
            self.actors.pop(resource)

    def isadminjid(self, sender):
        """isadminjid(sender) -> bool

        Check whether the given JID is an administrator JID.
        """
        
        for jid in self.adminjids:
            if (jid.barematch(sender)):
                return True
        return False

    def listopengames(self):
        """listopengames() -> DiscoItems

        Create a DiscoItems list of currently-active games. This is used
        when responding to disco#items queries.
        """
        
        items = jabber.disco.DiscoItems()
        for jid in self.referees.keys():
            ref = self.referees[jid]
            if (ref.showtable):
                items.additem(ref.jid, name=self.gamename)
        return items

    def listbots(self):
        """listbots() -> DiscoItems

        Create a DiscoItems list of available bot algorithms. This is used
        when responding to disco#items queries.
        """
        
        items = jabber.disco.DiscoItems()
        for botclass in self.botclasses:
            items.additem(self.jid, node=botclass.boturi, name=botclass.getname())
        for jid in self.botfactories:
            items.additem(jid)
        return items

    def stopunlessgraceful(self, ent):
        """stopunlessgraceful(ent) -> None

        Stop the given entity if we are in the middle of an ungraceful
        shutdown. If it's graceful, allow the entity to live.
        """
        if (not self.shutdowngraceful):
            ent.stop()

    def handlerosterquery(self, msg):
        """handlerosterquery(msg) -> <stanza outcome>

        Accept a request to be added to someone's roster.
        """
        
        qnod = msg.getchild('query')
        if (not qnod or qnod.getnamespace() != interface.NS_ROSTER):
            # Not addressed to us
            return

        item = qnod.getchild('item')
        if (item):
            jidstr = item.getattr('jid')
            if (jidstr):
                self.log.info('accepting presence subscription from %s',
                    jidstr)
                msg = interface.Node('presence', attrs={ 'to':jidstr,
                    'type':'subscribed' })
                self.conn.send(msg, addid=False, addfrom=False)

        raise interface.StanzaHandled

    def requeststop(self, graceful=True, restart=True, delay=False):
        """requeststop(graceful=True, restart=True, delay=False) -> None

        Stop activity in a particular way.

        If *graceful* is false, the parlor and all its referees (and bots)
        will shut down immediately. If true, the parlor will shut down, but
        the referees will keep going until they reach their natural shutdown
        condition.

        If *restart* is false, the process will exit when the parlor and
        all referees shut down. If true, this is still true; however, the
        parlor's shutdown will cause the process to exec a new copy of
        itself, thus restarting itself from scratch. Any remaining referees
        (in the graceful-restart case) will continue to operate in a fork
        of the original process.

        If *delay* is true (and *restart* is also), then the parlor will
        wait a few seconds before starting up. This is used in the case
        of a broken Jabber connection, to prevent the parlor from going
        crazy with restart attempts.

        Note that this restart feature must be enabled by passing the
        --restart-script command-line argument. If that is not available,
        the *restart* argument is ignored.

        Note that a call to the generic agent stop() method is equivalent
        to requeststop(True, True, True). (Unless the agent is already 
        stopping, in which case the originally-requested conditions remain 
        in place.) This means that if the Jabber connection dies, the parlor 
        will attempt a graceful restart.
        """
        
        self.shutdowngraceful = graceful
        self.shutdownrestart = restart
        self.shutdownrestartdelay = delay
        self.stop()

    def endwork(self):
        """endwork() -> None

        The 'end' state handler. This invokes the function (handed in from
        volityd.py) which execs a new parlor if desired.
        """
        
        self.log.warning('Parlor stopped.')
        if (self.shutdownrestart):
            self.restartfunc(self.shutdownrestartdelay)

class ParlorVolityOpset(rpc.MethodOpset):
    """ParlorVolityOpset: The Opset which responds to volity.* namespace
    RPCs.

    ParlorVolityOpset(par) -- constructor.

    The *par* is the Parlor to which this Opset is attached.

    Handler methods:

    rpc_new_table() -- create a new table and Referee.
    """
    
    def __init__(self, par):
        self.parlor = par

    def rpc_new_table(self, sender, *args):
        """rpc_new_table() -> table JID

        Create a new table and Referee.

        Implemented by the parlor's newtable() method.
        """
        
        return self.parlor.newtable(sender, *args)


class ParlorAdminOpset(rpc.MethodOpset):
    """ParlorAdminOpset: The Opset which responds to admin.* namespace
    RPCs.

    ParlorAdminOpset(par) -- constructor.

    The *par* is the Parlor to which this Opset is attached.

    Methods:

    precondition() -- checks authorization before an RPC handler.

    Handler methods:

    rpc_status() -- return assorted status information.
    rpc_list_tables() -- return a list of open table IDs.
    rpc_list_bots() -- return a list of running bots (on all tables).
    rpc_online() -- set the Parlor to accept or reject new table requests.
    rpc_announce() -- yell a message on all open tables.
    rpc_shutdown() -- immediately shut down this Parlor and all tables.
    rpc_graceful_shutdown() -- shut down this Parlor, but leave tables
        operating.
    rpc_restart() -- immediately shut down this Parlor and all tables, then
        restart the Parlor.
    rpc_graceful_restart() -- shut down this Parlor, but leave tables
        operating; then restart the Parlor.
    """
    
    def __init__(self, par):
        self.parlor = par

    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs)

        Checks authorization before an RPC handler. Only the admin JID
        is permitted to send admin.* namespace RPCs. If no admin JID was
        set, then all admin.* RPCs are rejected.
        """

        if (not self.parlor.isadminjid(sender)):        
            raise interface.StanzaNotAuthorized('admin operations are restricted')
        # Log the command that we're about to perform.
        if (not (namehead in ['status'] or namehead.startswith('list'))):
            self.parlor.log.warning('admin command from <%s>: %s %s',
                unicode(sender), namehead, unicode(callargs))

    def rpc_status(self, sender, *args):
        """rpc_status() -> dict

        Return assorted status information. This returns a Jabber-RPC struct
        containing these fields:

            online: Whether the Parlor is online.
            startup_time: When the Parlor was started.
            startup_at: How long ago the Parlor was started.
            last_new_table: How long ago it's been since a table was started.
            tables_running: How many tables are currently open.
            tables_started: How many tables have been started since the
                Parlor began.
        """

        if (len(args) != 0):
            raise rpc.RPCFault(604, 'status: no arguments')
        dic = {}
        dic['online'] = self.parlor.online
        dic['startup_time'] = time.ctime(self.parlor.startuptime)
        dic['startup_at'] = volent.descinterval(
            self.parlor.startuptime,
            limit=2)
        dic['last_new_table'] = volent.descinterval(
            self.parlor.activitytime,
            limit=2)
        dic['tables_running'] = len(self.parlor.referees)
        dic['tables_started'] = self.parlor.refereesstarted
        
        return dic

    def rpc_list_tables(self, sender, *args):
        """rpc_list_tables() -> list

        Return a list of open table IDs.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'list_tables: no arguments')
        ls = [ ref.jid for ref in self.parlor.referees.values() ]
        return ls

    def rpc_list_bots(self, sender, *args):
        """rpc_list_bots() -> list

        Return a list of running bots (on all tables).
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'list_bots: no arguments')
        return self.parlor.actors.keys()

    def rpc_online(self, sender, *args):
        """rpc_online(val) -> str

        Set the Parlor to accept or reject new table requests. The *val*
        must be a boolean. If it is False, then new_table requests will
        be rejected with a 'volity.offline' failure token.

        Returns a message describing the outcome.
        """
        
        if (len(args) != 1):
            raise rpc.RPCFault(604, 'online TRUE/FALSE')
        val = args[0]
        if (type(val) != bool):
            raise rpc.RPCFault(605, 'online TRUE/FALSE')
        if (self.parlor.online and (not val)):
            self.parlor.online = False
            return 'parlor now offline for new table requests'
        if ((not self.parlor.online) and val):
            self.parlor.online = True
            return 'parlor now online for new table requests'
        return 'no change to online status'
    
    def rpc_announce(self, sender, *args):
        """rpc_announce(msg) -> str

        Yell a message on all open tables. The message is broadcast as
        an ordinary group-chat message, so all connected clients will see
        it.

        Returns a message saying how many tables were yelled at.
        """
        
        if (len(args) != 1):
            raise rpc.RPCFault(604, 'announce STRING')
        val = args[0]
        if (not type(val) in [str, unicode]):
            raise rpc.RPCFault(605, 'announce STRING')

        ls = self.parlor.referees.values()
        for ref in ls:
            ref.announce(val)
        return ('sent to %d tables' % len(ls))
            
    def rpc_shutdown(self, sender, *args):
        """rpc_shutdown() -> str

        Immediately shut down this Parlor and all tables. This kills all
        open games, so use it considerately.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'shutdown: no arguments')
        self.parlor.queueaction(self.parlor.requeststop, False, False)
        return 'stopping parlor immediately'

    def rpc_graceful_shutdown(self, sender, *args):
        """rpc_graceful_shutdown() -> str

        Shut down this Parlor, but leave tables operating.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'graceful_shutdown: no arguments')
        self.parlor.queueaction(self.parlor.requeststop, True, False)
        return 'stopping parlor gracefully'

    def rpc_restart(self, sender, *args):
        """rpc_restart() -> str

        Immediately shut down this Parlor and all tables. This kills all
        open games, so use it considerately.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'restart: no arguments')
        if (not self.parlor.canrestart):
            raise rpc.RPCFault(609, 'restart: no restart-script available')
        self.parlor.queueaction(self.parlor.requeststop, False, True)
        return 'restarting parlor immediately'

    def rpc_graceful_restart(self, sender, *args):
        """rpc_graceful_restart() -> str

        Shut down this Parlor, but leave tables operating.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'graceful_restart: no arguments')
        if (not self.parlor.canrestart):
            raise rpc.RPCFault(609, 'restart: no restart-script available')
        self.parlor.queueaction(self.parlor.requeststop, True, True)
        return 'restarting parlor gracefully'


# late imports
import referee
import game
import bot
