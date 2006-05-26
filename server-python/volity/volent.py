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
VOLITY_CAPS_URI = 'http://volity.org/protocol/caps'

class VolEntity(zymb.sched.Agent):
    """VolEntity: A base class for Volity entities. This does the basic
    work of creating a Jabber client Agent.

    A VolEntity is an Agent itself, so it can use all the scheduling and
    queueing features defined in Zymb's sched module.

    VolEntity(jidstr, password, jidresource=None, host=None) -- constructor.

    The *jidstr* and *password* are used to connect to a Jabber server.
    If *jidresource* is provided, it overrides the resource string in
    *jidstr*. (If neither resource string is available, "volity" is used.)
    If *host* is provided, it overrides the server name in *jidstr*.

    Static methods:

    uniquestamp() -- Generate a number which is unique within this process.
    resolvemetharg() -- convert some Volity objects into Jabber-RPC types.

    Agent states and events:

    state 'start': Initial state. Start up the Jabber connection process.
        When Jabber is fully connected, jump to 'ready'.
    state 'ready': Send out initial presence. In this state, the entity
        is ready to do normal work.
    state 'end': The connection is closed.

    Public methods:

    start() -- begin activity. (inherited)
    stop() -- stop activity. (inherited)

    Significant fields:

    jid -- the JID by which this entity is connected.
    conn -- the Jabber client agent.

    Internal methods:

    startup() -- 'start' state handler.
    authed() -- handler for Jabber client 'authresource' state.
    startpresence() -- 'ready' state handler.
    finalize() -- 'end' state handler.
    rpcnotfound() -- callback for RPC method-not-found case.
    cappresencehook() -- callback for presence stanza generation.
    """
    
    logprefix = 'volity'
    volityrole = 'UNDEFINED'

    def __init__(self, jidstr, password, jidresource=None, host=None):
        zymb.sched.Agent.__init__(self)

        # Figure out the JID to use.        
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

        # Create the Jabber client agent.
        self.conn = jabber.client.JabberAuthResource(self.jid,
            self.password, host=host)

        # Set up handlers for ourself and the Jabber agent.
        self.conn.addhandler('authresource', self.authed)
        self.conn.addhandler('end', self.stop)

        # Set up the presence service.
        pres = jabber.presence.PresenceService();
        pres.addhook(self.cappresencehook)
        self.conn.addservice(pres)

        # Set up the disco services.
        
        disco = jabber.disco.DiscoService()
        self.conn.addservice(disco)

        discoc = jabber.disco.DiscoClience()
        self.conn.addservice(discoc)

        # Set up the RPC services.
        
        rpc = jabber.rpc.RPCService(notfoundhandler=self.rpcnotfound)
        ops = jabber.rpc.SeparatorOpset()
        if (not isinstance(self, actor.Actor)):
            ops = ClientRPCWrapperOpset(ops)
        rpc.setopset(ops)
        self.conn.addservice(rpc)

        rpcc = jabber.rpc.RPCClience()
        self.conn.addservice(rpcc)

        # End of constructor.

    def startup(self):
        """startup() -- 'start' state handler.

        Start the Jabber client agent, and then wait for it to finish
        connecting.
        """
        
        # assumes resource didn't change
        self.log.info('Connecting as <%s>', str(self.jid))
        self.conn.start()

    def authed(self):
        """authed() -- handler for Jabber client 'authresource' state.

        The Jabber client has finished connecting. Jump to 'ready'.
        """
        self.jump('ready')

    def startpresence(self):
        """startpresence() -- 'ready' state handler.

        Send out initial presence.
        """
        serv = self.conn.getservice('presenceservice')
        serv.set()

    def finalize(self):
        """finalize() -- 'end' state handler.

        Shut down the Jabber client agent.
        """
        self.conn.stop()
        self.conn = None

    def rpcnotfound(self, msg, callname, callargs):
        """rpcnotfound() -- callback for RPC method-not-found case.

        When an RPC arrives that we do not recognize, we want to respond
        with an RPC fault. (As opposed to the default behavior, which is
        an <item-not-found> stanza error.)
        """
        raise jabber.rpcdata.RPCFault(603, 'RPC method ' + callname)

    def cappresencehook(self, msg):
        """cappresencehook() -- callback for presence stanza generation.

        This hook is called whenever we generate a presence stanza. It adds
        a JEP-0115 Entity Capabilities tag to the stanza.
        """
        
        typestr = msg.getattr('type', '')
        if (typestr != 'unavailable'):
            msg.setchild('c', namespace=jabber.interface.NS_CAPS, attrs={
                'node': VOLITY_CAPS_URI,
                'ver': volityversion,
                'ext': self.volityrole })

    def resolvemetharg(val, gameresolve=None):
        """resolvemetharg(val) -> val

        Convert some Volity objects into Jabber-RPC types. This is called
        on all RPC arguments before they are sent out.

        If *gameresolve* is not None, it is invoked on the value (and on each
        member of list and tuple values). If it returns non-None, then that
        is the conversion.
        
        Otherwise, a Seat object is converted to a string (the seat ID).
        A Player object is converted to a string (the player's real JID).
        All other types are left alone.
        """
        if (gameresolve):
            newval = gameresolve(val)
            if (newval != None):
                return newval

        selffunc = VolEntity.resolvemetharg
                
        if (isinstance(val, game.Seat) or isinstance(val, actor.Seat)):
            return val.id
        if (isinstance(val, referee.Player)):
            return val.jidstr
        if (type(val) in [list, tuple]):
            return [ selffunc(subval, gameresolve) for subval in val ]
        if (type(val) == dict):
            newval = {}
            for key in val.keys():
                subval = val[key]
                newval[key] = selffunc(subval, gameresolve)
            return newval
        return val
    resolvemetharg = staticmethod(resolvemetharg)
            
    def uniquestamp():
        """uniquestamp() -> int

        Generate a number which is unique within this process. It is
        approximately a Unix timestamp, but incremented when necessary
        to avoid repeats.
        """
        
        val = int(time.time())
        if (val <= VolEntity.laststamp):
            val = VolEntity.laststamp+1
        VolEntity.laststamp = val
        return val
    uniquestamp = staticmethod(uniquestamp)
    laststamp = 0

class ClientRPCWrapperOpset(jabber.rpc.WrapperOpset):
    """ClientRPCWrapperOpset: An Opset which wraps all RPC handlers used
    by Parlors and Referees (but not Actors).

    This Opset ensures that all RPC replies follow the Volity token rules.
    A successful RPC response is returned as a ['volity.ok', value] list.
    Failure tokens are returned in appropriate token-list form.

    (This wrapper is not applied to Actors -- i.e., bots -- because a
    bot is replying to a referee, not to a player. There's no need to
    send translation tokens to a referee.)

    Static method:

    oktoken() -- create a success-token list encapsulating the given value.

    Internal methods:

    __call__() -- invoke an RPC.
    deferredwrapper() -- callback for deferred handlers.
    """
    
    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> <rpc outcome>

        Invoke an RPC. This is invoked by the Zymb RPC-handling service.
        """
        
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
        """oktoken(value) -> list

        Create a success-token list encapsulating the given value.
        """
        
        if (value == None):
            return ['volity.ok']
        return ['volity.ok'] + [value]
    oktoken = staticmethod(oktoken)

class Literal:
    """Literal: Represents a literal string in a token list.

    Literal(value) -- constructor.

    The Literal token will represent the string *value*. If *value* is not
    a string or unicode object, it is converted to one.
    """
    
    def __init__(self, token):
        if (type(token) in [str, unicode]):
            self.token = token
        else:
            self.token = unicode(token)
    
class FailureToken(Exception):
    """FailureToken: Represents a failure token list.

    FailureToken(*tokens) -- constructor.

    The *tokens* list must contain at least one element. 

    Static method:

    encode() -- turn an object into a token.
    """
    
    def __init__(self, tok, *tokargs):
        Exception.__init__(self)
        self.list = [ self.encode(val) for val in ((tok,) + tokargs) ]
    def __repr__(self):
        return '<FailureToken: ' + (', '.join(self.list)) + '>'
    def __str__(self):
        return (', '.join(self.list))

    def encode(val):
        """encode(val) -> str

        Turn an object into a token. The *val* must be a string, a number,
        a Literal object, or a Seat.

        A string is taken to be a token name. If it is in the form
        'namespace.token', it is taken as given. If it has no dot, it
        is assumed to be in the game.* namespace.

        A number is automatically turned into a literal.* namespace token,
        of the form 'literal.NUMBER'.

        A Seat object is turned into a seat.* namespace token.
        """
        
        if (type(val) in [int, long, float]):
            return 'literal.' + str(val)
        if (isinstance(val, Literal)):
            return 'literal.' + val.token
        if (isinstance(val, game.Seat)):
            return 'seat.' + val.id
        if (not type(val) in [str, unicode]):
            raise TypeError('tokens must be numbers, strings, Literals, or Seats')
        pos = val.find('.')
        if (pos < 0):
            return 'game.' + val
        return val
    encode = staticmethod(encode)

# List of units for descinterval().
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
    """descinterval(endtime, starttime=None, limit=None) -> str

    Render an interval as a human-readable string.

    If only *endtime* is provided, this will be of the form 'X hours
    Y minutes Z seconds ago'. (That is, before the present.) If *endtime*
    is None, this returns 'never'.

    If both *starttime* and *endtime* are provided, this will be of the
    form 'X hours Y minutes Z seconds'. (That is, between the two times.)

    If *limit* (an integer) is provided, it limits the number of units
    which are named. For example, if *limit* is 2, then a multi-hour
    time will be rendered as 'X hours Y minutes' (with the 'seconds'
    trimmed off.) A multi-day time will be rendered as 'W days X hours'.
    """
    
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

def doubletoint(nod, nam):
    """doubletoint -- internal function used as an rpcdata value parser hook.

    A peculiarity of Volity is that when a numeric value comes over the
    RPC wire from a client, it will always be a <double>, even if the
    value is a whole number. This is because Volity clients run on ECMAScript,
    which has no type distinction between floats and ints.

    Therefore, we set up an RPC parsing function which converts floats to
    ints. (Games will very rarely use true non-integer values, so this is
    the normal case.) Unfortunately, the rpcdata facility to handle this is
    global -- sorry, I got lazy -- so this conversion applies to every RPC
    received by the process.
    """
    if (nam == 'double'):
        res = jabber.rpcdata.RPCDouble.parsenode(nod)
        ires = int(res)
        if (ires == res):
            return ires
        else:
            return res

# Set up the doubletoint hook (at module load time)
jabber.rpcdata.setvalueparserhook(doubletoint)


# ---- late imports
import referee
import actor
import game

