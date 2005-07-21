import time
import logging
import zymb.sched
from zymb import jabber
import zymb.jabber.interface
import zymb.jabber.client
import zymb.jabber.disco
import zymb.jabber.rpc
import zymb.jabber.rpcdata
import zymb.jabber.presence

volityversion = '1.0'

class VolEntity(zymb.sched.Agent):
    logprefix = 'volity'

    def __init__(self, jidstr, password, jidresource=None):
        zymb.sched.Agent.__init__(self)
        
        self.jid = jabber.interface.JID(jidstr)
        if (jidresource):
            self.jid.setresource(jidresource)
        else:
            if (not self.jid.getresource()):
                self.jid.setresource('volity')
        self.password = password

        self.addhandler('start', self.startup)
        self.addhandler('end', self.finalize)
        self.addhandler('ready', self.startpresence)

        self.conn = jabber.client.JabberAuthResource(self.jid,
            self.password)
            
        self.conn.addhandler('authresource', self.authed)
        self.conn.addhandler('end', self.stop)

        self.conn.addservice(jabber.presence.PresenceService())

        disco = jabber.disco.DiscoService()
        self.conn.addservice(disco)

        discoc = jabber.disco.DiscoClience()
        self.conn.addservice(discoc)

        rpc = jabber.rpc.RPCService(notfoundhandler=self.rpcnotfound)
        ops = ClientRPCWrapperOpset(jabber.rpc.SeparatorOpset())
        rpc.setopset(ops)
        self.conn.addservice(rpc)

        rpcc = jabber.rpc.RPCClience()
        self.conn.addservice(rpcc)

    def startup(self):
        self.log.info('Connecting as <%s>', str(self.jid))
        self.conn.start()

    def authed(self):
        self.jump('ready')

    def startpresence(self):
        serv = self.conn.getservice('presenceservice')
        serv.set()

    def finalize(self):
        self.conn.stop()

    def rpcnotfound(self, msg, callname, callargs):
        raise jabber.rpcdata.RPCFault(603, 'RPC method ' + callname)

class ClientRPCWrapperOpset(jabber.rpc.WrapperOpset):
    """###
    """
    
    def __call__(self, sender, callname, *callargs):
        try:
            res = jabber.rpc.WrapperOpset.__call__(self, sender,
                callname, *callargs)
            return self.oktoken(res)
        except zymb.sched.Deferred, ex:
            ex.addcontext(self.deferredwrapper)
            raise
        except jabber.rpcdata.RPCResponse, ex:
            return self.oktoken(ex.value)
        except FailureToken, ex:
            return ex.list

    def deferredwrapper(self, tup):
        """deferredwrapper() -- callback for deferred handlers.

        This is used by handlecall(), for the case where an RPC handler
        wants to undertake a deferral operation. You should not call it,
        or even try to understand it.
        """
        
        try:
            res = zymb.sched.Deferred.extract(tup)
            return self.oktoken(res)
        except jabber.rpcdata.RPCResponse, ex:
            return self.oktoken(ex.value)
        except FailureToken, ex:
            return ex.list

    def oktoken(value):
        if (value == None):
            return ['volity.ok']
        return ['volity.ok'] + [value]
    oktoken = staticmethod(oktoken)

class FailureToken(Exception):
    """###
    """
    def __init__(self, tok, *tokargs):
        self.list = [ self.encode(val) for val in ((tok,) + tokargs) ]
    def __repr__(self):
        return '<FailureToken: ' + (', '.join(self.list)) + '>'
    def __str__(self):
        return (', '.join(self.list))

    def encode(val):
        if (type(val) in [int, long, float]):
            return 'literal.' + str(val)
        if (isinstance(val, game.Seat)):
            return 'seat.' + val.id
        if (not type(val) in [str, unicode]):
            raise TypeError('tokens must be numbers, strings, or Seats')
        pos = val.find('.')
        if (pos < 0):
            return 'game.' + val
        return val
    encode = staticmethod(encode)

intervalunits = [
    (60*60*24*365, 'year'),
    (60*60*24*30, 'month'),
    (60*60*24*7, 'week'),
    (60*60*24, 'day'),
    (60*60, 'hour'),
    (60, 'minute'),
    (1, 'second'),
]

def descinterval(endtime, starttime=None, limit=None):
    agoflag = False
    if (endtime == None):
        return 'never'
    if (starttime == None):
        starttime = endtime
        endtime = time.time()
        agoflag = True

    interval = long(endtime - starttime)
    if (interval == 0):
        return 'no time'
        
    ls = []
    complexity = 0
    
    if (interval < 0):
        ls.append('negative')
        interval = -interval

    for (size, name) in intervalunits:
        count = interval // size
        if (count == 0):
            continue
            
        ls.append(str(count))
        if (count == 1):
            ls.append(name)
        else:
            ls.append(name+'s')
        complexity += 1

        interval -= count*size
        if (interval <= 0):
            break
        if (limit != None and complexity >= limit):
            break

    if (agoflag):
        ls.append('ago')

    return ' '.join(ls)

# ---- late imports
import game

