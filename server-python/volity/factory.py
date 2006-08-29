import os, time, types
import logging
import volent
from zymb import sched, jabber
import zymb.jabber.dataform
import zymb.jabber.disco
import zymb.jabber.keepalive
from zymb.jabber import interface
from zymb.jabber import rpc

BOT_STARTUP_TIMEOUT = 60

class Factory(volent.VolEntity):
    """Factory: The implementation of a Volity bot factory.

    This is the central class which offers bots on a system. When you
    run the volityd.py script with the --bot argument (and not --game),
    it creates a Factory and starts it running.
    
    Factory is a Jabber client (although it is not a subclass of the
    Zymb Jabber client agent; instead, it creates one as a subsidiary
    object). It sits around and waits for the "new bot" command,
    which tells it to start up an Actor. That's nearly all it does,
    actually, although there are some extra debugging and administration
    features.

    Factory(config) -- constructor.

    The *config* is a ConfigFile object, containing various options needed
    to set up the Factory. The volityd.py script creates this. For the
    record, the significant fields are:

        jid: Used when connecting to Jabber
        jid-resource: Resource string to use (optional, overrides the
            one in *jid*; if no resource is provided in either *jid* or
            *jid-resource*, then "volity" is used)
        host: Jabber server host to use (optional, overrides the one
            in *jid*)
        password: Used when connecting to Jabber
        bot: Name of Python class which implements the bot
        admin: JID which is permitted to send admin commands (optional)
            (may be comma-separated list of JIDs)
        bookkeeper: JID of Volity bookkeeper (optional, defaults to
            "bookkeeper@volity.net/volity")
        contact-email: Contact email address of person hosting this
            factory (optional)
        contact-jid: Contact Jabber address of person hosting this
            factory (optional)
        keepalive: If present, send periodic messages to exercise the
            Jabber connection (optional)
        keepalive-interval: If present, send periodic messages every
            *keepalive-interval* seconds to exercise the Jabber
            connection (optional)

    Agent states and events:

    state 'start': Initial state. Start up the Jabber connection process.
        When Jabber is fully connected, jump to 'ready'.
    state 'ready': Send out initial presence. In this state, the Factory
        accepts new_bot requests.
    state 'end': The connection is closed.

    Public methods:

    start() -- begin activity. (inherited)
    stop() -- stop activity. (inherited)
    requeststop() -- stop activity in a particular way.
    isadminjid() -- check whether the given JID is an administrator JID.

    Significant fields:

    jid -- the JID by which this Factory is connected.
    conn -- the Jabber client agent.
    adminjids -- the JID which is permitted to send admin commands.
    botclasses -- list of Bot subclasses which implement the bot.
    actors -- dict mapping resource strings to Actors.
    online -- whether the Factory is accepting new_bot requests.

    Internal methods:

    handlemessage() -- handle a Jabber message stanza.
    handlepresence() -- handle a Jabber presence stanza.
    newbot() -- create a new bot.
    botready() -- callback invoked when an Actor is successfully
        (or unsuccessfully) created.
    actordied() -- callback invoked when an Actor (bot) shuts down.
    listbots() -- create a DiscoItems list of available bot URIs.
    stopunlessgraceful() -- stop the given entity if we are in the middle
        of an ungraceful shutdown.
    handlerosterquery() -- accept a request to be added to someone's roster.
    endwork() -- 'end' state handler.

    """
    
    logprefix = 'volity.factory'
    volityrole = 'factory'

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
        
        # Locate the bot classes.
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

            if (not botclass.ruleseturi):
                raise TypeError('botclass does not define class.ruleseturi')

            if (botclass.ruleseturi != self.botclasses[0].ruleseturi):
                raise ValueError('all botclasses must have the same ruleset URI')
            if (botclass.rulesetversion != self.botclasses[0].rulesetversion):
                raise ValueError('all botclasses must have the same ruleset version')

            if (not botclass.boturi):
                raise TypeError('botclass does not define class.boturi')

            if (botclass.boturi in
                [bc.boturi for bc in self.botclasses[:-1]]):
                raise TypeError('class.boturi used more than once')

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
        
        botclass = self.botclasses[0]

        val = config.get('entity-name')
        if (not val):
            val = botclass.getname()
        self.botname = val
        self.log.warning('Bot factory running: %s', self.botname)

        self.actors = {}
        self.online = True
        self.startuptime = time.time()
        self.activitytime = None
        self.actorsstarted = 0
        self.visibility = config.getbool('visible', True)
        
        self.botcounter = 1
        
        # Set up the disco service
        
        disco = self.conn.getservice('discoservice')
        
        info = disco.addinfo()
        info.addidentity('volity', 'factory', self.botname)
        info.addfeature(interface.NS_CAPS)

        form = jabber.dataform.DataForm()
        val = config.get('entity-desc')
        if (not val):
            val = botclass.botdescription
        form.addfield('description', val)
        form.addfield('ruleset', botclass.ruleseturi)
        form.addfield('volity-role', self.volityrole)
        form.addfield('ruleset-version', botclass.rulesetversion)
        val = botclass.websiteurl
        if (not val):
            val = botclass.ruleseturi
        form.addfield('website', val)
        if (config.get('contact-email')):
            form.addfield('contact-email', config.get('contact-email'))
        if (config.get('contact-jid')):
            form.addfield('contact-jid', config.get('contact-jid'))
        if (self.visibility):
            val = '1'
        else:
            val = '0'
        form.addfield('visible', val)
        info.setextendedinfo(form)

        # assumes resource didn't change
        items = disco.additems()
        items.additem(self.jid, node='ruleset',
            name='Ruleset URI')
        items.additem(self.jid, node='bots',
            name='Bots available from this factory')

        items = disco.additems('ruleset')
        items.additem(
            unicode(self.bookkeeperjid),
            node=botclass.ruleseturi,
            name='Ruleset information (%s)' % botclass.ruleseturi)

        items = disco.additems('bots', self.listbots)

        # Set up the RPC service
        
        rpcserv = self.conn.getservice('rpcservice')
        assert isinstance(rpcserv.getopset(), volent.ClientRPCWrapperOpset)
        ops = rpcserv.getopset().getopset()
        dic = ops.getdict()
        dic['volity'] = FactoryVolityOpset(self)
        dic['admin'] = FactoryAdminOpset(self)

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

        Handle a Jabber message stanza. The Factory does not react to messages,
        so this just raises StanzaHandled to end processing for the stanza.
        """
        
        if (self.log.isEnabledFor(logging.DEBUG)):
            self.log.info('received message')
            #self.log.debug('received message:\n%s', msg.serialize(True))
        raise interface.StanzaHandled()

    def handlepresence(self, msg):
        """handlepresence(msg) -> <stanza outcome>

        Handle a Jabber presence stanza. The Factory does not react to 
        presence, so this just raises StanzaHandled to end processing for the
        stanza.
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
            
    def newbot(self, sender, *args):
        """newbot(sender, uri, tablejid) -> <RPC outcome>

        Create a new bot, and have it join the given table. The RPC
        response will be the bot's full JID, but that doesn't happen in this
        function; see the botready() callback.
        """
        
        if (len(args) != 2):
            raise rpc.RPCFault(604, 'new_bot takes two arguments')
        if (not type(args[0]) in (str, unicode)):
            raise rpc.RPCFault(605, 'new_bot: first argument must be string')
        if (not type(args[1]) in (str, unicode)):
            raise rpc.RPCFault(605, 'new_bot: second argument must be string')
        uri = args[0]
        tablejidstr = args[1]
        tablejid = interface.JID(tablejidstr)

        if (self.state != 'ready'):
            raise rpc.RPCFault(609, 'factory is not yet ready')

        if (not self.online):
            raise volent.FailureToken('volity.offline')

        found = False
        for botclass in self.botclasses:
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
            act = actor.Actor(self, sender, self.jid, self.password,
                tablejid, botresource, botnick, botclass)
        except Exception, ex:
            self.log.error('Unable to create bot',
                exc_info=True)
            raise rpc.RPCFault(608,
                'unable to create bot: ' + str(ex))
        
        assert (not self.actors.has_key(botresource))
        self.actors[botresource] = act
        
        act.start()

        act.addhandler('end', self.actordied, act)
        self.addhandler('end', self.stopunlessgraceful, act)

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
        created. This is the continuation of newbot(). The *act* is
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
        self.activitytime = time.time()
        self.actorsstarted += 1
        return unicode(act.jid)

    def actordied(self, act):
        """actordied(ref) -> None

        Callback invoked when an Actor shuts down. Remove the Actor from
        our internal table.
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

    def listbots(self):
        """listbots() -> DiscoItems

        Create a DiscoItems list of available bot algorithms. This is used
        when responding to disco#items queries.
        """
        
        items = jabber.disco.DiscoItems()
        for botclass in self.botclasses:
            items.additem(self.jid, node=botclass.boturi, name=botclass.getname())
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

        If *graceful* is false, the factory and all its bots will shut
        down immediately. If true, the factory will shut down, but the
        bots will keep going until they reach their natural shutdown
        condition.

        If *restart* is false, the process will exit when the factory and
        all bots shut down. If true, this is still true; however, the
        factory's shutdown will cause the process to exec a new copy of
        itself, thus restarting itself from scratch. Any remaining bots
        (in the graceful-restart case) will continue to operate in a fork
        of the original process.

        If *delay* is true (and *restart* is also), then the factory will
        wait a few seconds before starting up. This is used in the case
        of a broken Jabber connection, to prevent the factory from going
        crazy with restart attempts.

        Note that this restart feature must be enabled by passing the
        --restart-script command-line argument. If that is not available,
        the *restart* argument is ignored.

        Note that a call to the generic agent stop() method is equivalent
        to requeststop(True, True, True). (Unless the agent is already 
        stopping, in which case the originally-requested conditions remain 
        in place.) This means that if the Jabber connection dies, the factory 
        will attempt a graceful restart.
        """
        
        self.shutdowngraceful = graceful
        self.shutdownrestart = restart
        self.shutdownrestartdelay = delay
        self.stop()

    def endwork(self):
        """endwork() -> None

        The 'end' state handler. This invokes the function (handed in from
        volityd.py) which execs a new factory if desired.
        """
        
        self.log.warning('Factory stopped.')
        if (self.shutdownrestart):
            self.restartfunc(self.shutdownrestartdelay)

class FactoryVolityOpset(rpc.MethodOpset):
    """FactoryVolityOpset: The Opset which responds to volity.* namespace
    RPCs.

    FactoryVolityOpset(par) -- constructor.

    The *par* is the Factory to which this Opset is attached.

    Handler methods:

    rpc_new_bot() -- create a new Actor and Bot.
    """
    
    def __init__(self, par):
        self.factory = par

    def rpc_new_bot(self, sender, *args):
        """rpc_new_bot() -> full bot JID

        Create a new Actor and Bot.

        Implemented by the factory's newbot() method.
        """
        
        return self.factory.newbot(sender, *args)


class FactoryAdminOpset(rpc.MethodOpset):
    """FactoryAdminOpset: The Opset which responds to admin.* namespace
    RPCs.

    FactoryAdminOpset(fac) -- constructor.

    The *fac* is the Factory to which this Opset is attached.

    Methods:

    precondition() -- checks authorization before an RPC handler.

    Handler methods:

    rpc_status() -- return assorted status information.
    rpc_list_bots() -- return a list of running bots.
    rpc_online() -- set the Factory to accept or reject new bot requests.
    rpc_announce() -- yell a message on all open bots.
    rpc_shutdown() -- immediately shut down this Factory and all bots.
    rpc_graceful_shutdown() -- shut down this Factory, but leave bots
        operating.
    rpc_restart() -- immediately shut down this Factory and all bots, then
        restart the Factory.
    rpc_graceful_restart() -- shut down this Factory, but leave bots
        operating; then restart the Factory.
    """
    
    def __init__(self, fac):
        self.factory = fac

    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs)

        Checks authorization before an RPC handler. Only the admin JID
        is permitted to send admin.* namespace RPCs. If no admin JID was
        set, then all admin.* RPCs are rejected.
        """

        if (not self.factory.isadminjid(sender)):        
            raise interface.StanzaNotAuthorized('admin operations are restricted')
        # Log the command that we're about to perform.
        if (not (namehead in ['status'] or namehead.startswith('list'))):
            self.factory.log.warning('admin command from <%s>: %s %s',
                unicode(sender), namehead, unicode(callargs))

    def rpc_status(self, sender, *args):
        """rpc_status() -> dict

        Return assorted status information. This returns a Jabber-RPC struct
        containing these fields:

            online: Whether the Factory is online.
            startup_time: When the Factory was started.
            startup_at: How long ago the Factory was started.
            last_new_bot: How long ago it's been since a bot was started.
            bots_running: How many bots are currently open.
            bots_started: How many bots have been started since the
                Factory began.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'status: no arguments')
        dic = {}
        dic['online'] = self.factory.online
        dic['startup_time'] = time.ctime(self.factory.startuptime)
        dic['startup_at'] = volent.descinterval(
            self.factory.startuptime,
            limit=2)
        dic['last_new_bot'] = volent.descinterval(
            self.factory.activitytime,
            limit=2)
        dic['bots_running'] = len(self.factory.actors)
        dic['bots_started'] = self.factory.actorsstarted
        
        return dic

    def rpc_list_bots(self, sender, *args):
        """rpc_list_bots() -> list

        Return a list of open bot IDs.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'list_bots: no arguments')
        ls = [ act.jid for act in self.factory.actors.values() ]
        return ls

    def rpc_online(self, sender, *args):
        """rpc_online(val) -> str

        Set the Factory to accept or reject new bot requests. The *val*
        must be a boolean. If it is False, then new_bot requests will
        be rejected with a 'volity.offline' failure token.

        Returns a message describing the outcome.
        """
        
        if (len(args) != 1):
            raise rpc.RPCFault(604, 'online TRUE/FALSE')
        val = args[0]
        if (type(val) != bool):
            raise rpc.RPCFault(605, 'online TRUE/FALSE')
        if (self.factory.online and (not val)):
            self.factory.online = False
            return 'factory now offline for new bot requests'
        if ((not self.factory.online) and val):
            self.factory.online = True
            return 'factory now online for new bot requests'
        return 'no change to online status'
    
    def rpc_announce(self, sender, *args):
        """rpc_announce(msg) -> str

        Yell a message via all open bots. The message is broadcast as
        an ordinary group-chat message, so all clients at a bot's table
        will see it.

        Returns a message saying how many bots yelled.
        """
        
        if (len(args) != 1):
            raise rpc.RPCFault(604, 'announce STRING')
        val = args[0]
        if (not type(val) in [str, unicode]):
            raise rpc.RPCFault(605, 'announce STRING')

        ls = self.factory.actors.values()
        for act in ls:
            act.announce(val)
        return ('sent to %d bots' % len(ls))
            
    def rpc_shutdown(self, sender, *args):
        """rpc_shutdown() -> str

        Immediately shut down this Factory and all bots.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'shutdown: no arguments')
        self.factory.queueaction(self.factory.requeststop, False, False)
        return 'stopping factory immediately'

    def rpc_graceful_shutdown(self, sender, *args):
        """rpc_graceful_shutdown() -> str

        Shut down this Factory, but leave bots operating.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'graceful_shutdown: no arguments')
        self.factory.queueaction(self.factory.requeststop, True, False)
        return 'stopping factory gracefully'

    def rpc_restart(self, sender, *args):
        """rpc_restart() -> str

        Immediately shut down this Factory and all bots.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'restart: no arguments')
        if (not self.factory.canrestart):
            raise rpc.RPCFault(609, 'restart: no restart-script available')
        self.factory.queueaction(self.factory.requeststop, False, True)
        return 'restarting factory immediately'

    def rpc_graceful_restart(self, sender, *args):
        """rpc_graceful_restart() -> str

        Shut down this Factory, but leave bots operating.
        """
        
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'graceful_restart: no arguments')
        if (not self.factory.canrestart):
            raise rpc.RPCFault(609, 'restart: no restart-script available')
        self.factory.queueaction(self.factory.requeststop, True, True)
        return 'restarting factory gracefully'


# late imports
import actor
import bot
