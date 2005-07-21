import os, time, types
import logging
import volent, referee, game
from zymb import sched, jabber
import zymb.jabber.dataform
import zymb.jabber.disco
import zymb.jabber.keepalive
from zymb.jabber import interface
from zymb.jabber import rpc

REFEREE_STARTUP_TIMEOUT = 120

class Parlor(volent.VolEntity):
    logprefix = 'volity.parlor'

    def __init__(self, opts):
        volent.VolEntity.__init__(self, opts.jid, opts.password, opts.jidresource)

        ls = opts.gameclass.split('.')
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
            
        self.adminjid = None
        if (opts.adminjid):
            self.adminjid = interface.JID(opts.adminjid)

        self.gamename = gameclass.gamename
        self.log.warning('Game parlor running: %s', self.gamename)

        self.addhandler('end', self.log.warning, 'Parlor stopped.')
        self.conn.adddispatcher(self.handlepresence, name='presence')
        self.conn.adddispatcher(self.handlemessage, name='message')

        self.referees = {}
        self.muchost = interface.JID(opts.muchost)
        self.online = True
        self.startuptime = time.time()
        self.activitytime = None
        self.refereesstarted = 0        
        
        disco = self.conn.getservice('discoservice')
        info = disco.addinfo()
        
        info.addidentity('volity', 'parlor', self.gamename)

        form = jabber.dataform.DataForm()
        form.addfield('description', gameclass.gamedescription)
        form.addfield('ruleset', gameclass.ruleseturi)
        form.addfield('ruleset-version', gameclass.rulesetversion)
        val = gameclass.websiteurl
        if (not val):
            val = gameclass.ruleseturi
        form.addfield('website', val)
        if (gameclass.implementoremail):
            form.addfield('contact-email', gameclass.implementoremail)
        if (gameclass.implementorjid):
            form.addfield('contact-jid', gameclass.implementorjid)
        form.addfield('volity-version', volent.volityversion)
        info.setextendedinfo(form)

        items = disco.additems()
        items.additem(self.jid, node='ruleset',
            name='Ruleset URI')
        items.additem(self.jid, node='open_games',
            name='Open games at this parlor')

        items = disco.additems('ruleset')
        items.additem(
            'bookkeeper@volity.com', ### from opts.bookkeeper
            node=gameclass.ruleseturi,
            name='Ruleset information (%s)' % gameclass.ruleseturi)

        items = disco.additems('open_games', self.listopengames)

        rpcserv = self.conn.getservice('rpcservice')
        ops = rpcserv.getopset().getopset()
        dic = ops.getdict()
        dic['volity'] = ParlorVolityOpset(self)
        dic['admin'] = ParlorAdminOpset(self)

        if (opts.keepalive or opts.keepaliveinterval):
            if (opts.keepaliveinterval):
                serv = jabber.keepalive.KeepAliveService(opts.keepaliveinterval)
            else:
                serv = jabber.keepalive.KeepAliveService()
            self.conn.addservice(serv)
            self.addhandler('ready', serv.start)
            self.log.warning('sending keepalive messages to self at interval of %d seconds', serv.getinterval())

    def handlemessage(self, msg):
        if (self.log.isEnabledFor(logging.DEBUG)):
            self.log.info('received message')
            #self.log.debug('received message:\n%s', msg.serialize(True))
        raise interface.StanzaHandled()

    def handlepresence(self, msg):
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
            
    def newtable(self, sender, *args):
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'new_table takes no arguments')

        if (self.state != 'ready'):
            raise rpc.RPCFault(609, 'parlor is not yet ready')

        if (not self.online):
            raise volent.FailureToken('volity.offline')
            
        self.log.info('new table requested by %s...', unicode(sender))

        ### see unique-nick discussion from mailing list
        refresource = 'ref_' + str(os.getpid()) + '_' + str(int(time.time()))
        muc = interface.JID(self.muchost)
        muc.setnode(refresource)

        try:
            ref = referee.Referee(self, self.jid, self.password, refresource,
                muc, self.gameclass)
        except Exception, ex:
            self.log.error('Unable to create referee',
                exc_info=True)
            raise rpc.RPCFault(608,
                'unable to create referee: ' + str(ex))
            
        assert (not self.referees.has_key(refresource))
        self.referees[refresource] = ref
        
        ref.start()

        ref.addhandler('end', self.refereedied, ref)
        self.addhandler('end', ref.stop)

        defer = sched.Deferred(self.refereeready, ref)
        
        ac1 = ref.addhandler('running', defer, 'running')
        defer.addaction(ac1)
        ac2 = self.addtimer(defer, 'timeout', delay=REFEREE_STARTUP_TIMEOUT)
        defer.addaction(ac2)
        ac3 = ref.addhandler('end', defer, 'end')
        defer.addaction(ac3)
        
        raise defer

    def refereeready(self, ref, res):
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
        resource = ref.resource
        if (self.referees.has_key(resource)):
            assert ref == self.referees[resource]
            self.log.info('referee %s has terminated', resource)
            self.referees.pop(resource)

    def listopengames(self):
        items = jabber.disco.DiscoItems()
        for jid in self.referees.keys():
            ref = self.referees[jid]
            items.additem(ref.jid, name=self.gamename)
        return items

class ParlorVolityOpset(rpc.MethodOpset):
    def __init__(self, par):
        self.parlor = par

    def rpc_new_table(self, sender, *args):
        self.parlor.newtable(sender, *args)


class ParlorAdminOpset(rpc.MethodOpset):
    def __init__(self, par):
        self.parlor = par

    def precondition(self, sender, namehead, nametail, *callargs):
        if ((not self.parlor.adminjid)
            or (not sender.barematch(self.parlor.adminjid))):
            raise interface.StanzaNotAuthorized('admin operations are restricted')
        self.parlor.log.warning('admin command from <%s>: %s %s',
            unicode(sender), namehead, unicode(callargs))

    def rpc_status(self, sender, *args):
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

    ### RPC to change log levels?

    def rpc_list_tables(self, sender, *args):
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'list_tables: no arguments')
        return self.parlor.referees.keys()

    def rpc_online(self, sender, *args):
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
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'shutdown: no arguments')
        self.parlor.queueaction(sched.stopall)
        return 'stopping parlor'

        