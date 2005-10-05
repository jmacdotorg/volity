import logging

import volent
from zymb import sched, jabber
from zymb.jabber import interface
from zymb.jabber import rpc
import zymb.jabber.dataform
import zymb.jabber.keepalive

REFEREE_DEFAULT_RPC_TIMEOUT = 5 ###

class Actor(volent.VolEntity):
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

        self.seat = None
        
        self.addhandler('ready', self.beginwork)
        self.conn.adddispatcher(self.handlepresence, name='presence')
        self.conn.adddispatcher(self.handlemessage, name='message')
        self.addhandler('joining', self.perform, 'tryjoin')
        self.addhandler('tryjoin', self.tryjoin)

        # Set up the bot instance.

        self.bot = botclass(self)
        assert self.bot.actor == self

        # Set up the RPC replier.
        
        self.rpccli = self.conn.getservice('rpcclience')
        
        # Set up the disco service
        
        disco = self.conn.getservice('discoservice')
        info = disco.addinfo()
        
        info.addidentity('volity', 'bot', self.bot.getname())
        info.addfeature(interface.NS_CAPS)

        form = jabber.dataform.DataForm()
        form.addfield('volity-role', self.volityrole)
        info.setextendedinfo(form)

        # Set up the RPC service
        
        rpcserv = self.conn.getservice('rpcservice')
        ops = rpcserv.getopset()
        dic = ops.getdict()
        dic['volity'] = BotVolityOpset(self)
        dic['admin'] = BotAdminOpset(self)
        self.gamewrapperopset = WrapGameOpset(self, rpc.Opset())
        dic['game'] = self.gamewrapperopset

        # Set up the keepalive service

        sserv = self.parlor.conn.getservice('keepaliveservice')
        if (sserv):
            serv = jabber.keepalive.KeepAliveService(sserv.getinterval())
            self.conn.addservice(serv)
            self.addhandler('ready', serv.start)
            self.log.warning('sending keepalive messages to self at interval of %d seconds', serv.getinterval())

    def beginwork(self):
        self.addhandler('end', self.endwork)
        self.jump('joining')

    def tryjoin(self):
        self.mucnick = interface.JID(jid=self.muc)

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
        self.bot.destroy()
        self.bot.actor = None
        self.bot = None
        self.referee = None
        self.parlor = None

        self.rpccli = None
        self.gamewrapperopset = None

    def tryready(self):
        if (self.seat):
            self.queueaction(self.sendref, 'volity.ready')

    def begingame(self):
        self.log.info('game beginning!')
        self.bot.begingame()

    def endgame(self):
        self.bot.endgame()

    def suspendgame(self):
        self.bot.suspendgame()

    def resumegame(self):
        self.bot.resumegame()

    def sendref(self, methname, *methargs, **keywords):
        op = keywords.pop('callback', self.defaultcallback)
        if (not keywords.has_key('timeout')):
            keywords['timeout'] = REFEREE_DEFAULT_RPC_TIMEOUT
            
        self.rpccli.send(op, self.referee.jid,
            methname, *methargs, **keywords)

    def defaultcallback(self, tup):
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
        if (typestr == '' and resource == self.referee.mucnick.getresource()
            and self.state == 'joining'):
            self.log.info('detected referee; requesting state')
            self.jump('running')
            self.queueaction(self.sendref, 'volity.send_state')

            
class WrapGameOpset(rpc.WrapperOpset):
    def __init__(self, act, subopset):
        rpc.WrapperOpset.__init__(self, subopset)
        self.actor = act
        self.referee = self.actor.referee

    def precondition(self, sender, namehead, nametail, *callargs):
        if (self.actor.state != 'running'):
            raise rpc.RPCFault(609, 'bot not ready for RPCs')

        if (sender != self.referee.jid):
            raise rpc.RPCFault(607, 'sender is not referee')
            
    def __call__(self, sender, callname, *callargs):
        try:
            return rpc.WrapperOpset.__call__(self, sender, callname, *callargs)
        except rpc.CallNotFound:
            # unimplemented methods are assumed to return quietly.
            return
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
    def __init__(self, act):
        self.actor = act
        self.referee = self.actor.referee
                
    def precondition(self, sender, namehead, nametail, *callargs):
        if (self.actor.state != 'running'):
            raise rpc.RPCFault(609, 'bot not ready for RPCs')

        if (sender != self.referee.jid):
            raise rpc.RPCFault(607, 'sender is not referee')
            
    def __call__(self, sender, callname, *callargs):
        try:
            return rpc.MethodOpset.__call__(self, sender, callname, *callargs)
        except rpc.CallNotFound:
            # unimplemented methods are assumed to return quietly.
            return
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

    def rpc_player_sat(self, sender, *args):
        if (len(args) >= 1 and (self.actor.jid == args[0])):
            self.actor.seat = args[1]
        self.actor.tryready()
            
    def rpc_player_stood(self, sender, *args):
        if (len(args) >= 1 and (self.actor.jid == args[0])):
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
            
    def rpc_end_game(self, sender, *args):
        self.actor.queueaction(self.actor.endgame)
        self.actor.tryready()
            
    def rpc_suspend_game(self, sender, *args):
        self.actor.queueaction(self.actor.suspendgame)
        self.actor.tryready()
            
    def rpc_resume_game(self, sender, *args):
        self.actor.queueaction(self.actor.resumegame)
            
class BotAdminOpset(rpc.MethodOpset):
    def __init__(self, act):
        self.actor = act
        self.referee = self.actor.referee

    def precondition(self, sender, namehead, nametail, *callargs):
        if ((not self.actor.parlor.adminjid)
            or (not sender.barematch(self.actor.parlor.adminjid))):
            raise interface.StanzaNotAuthorized('admin operations are restricted')
        self.actor.log.warning('admin command from <%s>: %s %s',
            unicode(sender), namehead, unicode(callargs))

    def rpc_status(self, sender, *args):
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'status: no arguments')
        dic = {}
        dic['referee'] = unicode(self.referee.jid)

        return dic
        
# late imports
import bot
