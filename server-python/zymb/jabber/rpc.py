import unittest
from zymb import sched
import client, service, interface
import rpcdata
from rpcdata import RPCFault, RPCResponse

class RPCService(service.Service):
    """RPCService: A high-level Jabber facility for responding to Jabber-RPC
    calls from other entities.

    (Service label: 'rpcservice'.)

    The RPCService is a generic wrapper for RPC calls. It receives the RPC
    stanza, translates the Jabber-RPC arguments into Python values, and
    then passes everything on to its opset. You supply the opset when
    you create the RPCService; it contains the code which actually handles
    each RPC. (See the Opset class for more information.)

    The code in the opset has several options:

    * Return a Python value. This will become the RPC response value.
    * Return None. This will cause an RPC response with a boolean True value.
    * Raise an RPCResponse(value). This is equivalent to returning the
        value -- it causes an RPC response with that value.
    * Raise an RPCFault(code, string). This will cause an RPC fault reply,
        with the given *code* and *string*.
    * Raise a CallNotFound. This will become a stanza-level 'item-not-found'
        error, indicating that the method does not exist.
    * Raise an interface.StanzaError. This will cause the RPC to be replied
        to with the given stanza-level error.
    * Raise some other kind of Exception. This will become a stanza-level
        'internal-server-error' error.
    * Raise a sched.Deferred(op). This is a promise that you will eventually
        invoke the Deferred object. That will result in *op* being called;
        and the *op* should then do one of the things on this list.

    RPCService(opset=None, notfoundhandler=None) -- constructor.

    If you don't supply an opset, a bare Opset instance will be used. This
    is a minimal handler -- it doesn't accept *any* calls, and always raises
    CallNotFound. You can change the opset later with the setopset() method.

    The *notfoundhandler* is a function which is called when an RPC method
    name is not recognized by the opset. The function is invoked as
        notfoundhandler(msg, callname, callargs)
    where *msg* is a Node tree, *callname* is a string, and *callargs* is
    a tuple of values. The function must return a value or raise
    RPCResponse, RPCFault, or a StanzaError. If you do not supply
    a function, the service will raise StanzaItemNotFound for unrecognized
    RPCs.

    Public methods:

    setopset(opset) -- change the service's opset.
    getopset() -- return the service's current opset.

    Internal methods:

    attach() -- attach this Service to a JabberStream.
    handlecall() -- RPC stanza handler.
    deferredwrapper() -- callback for deferred handlers.
    """
    
    label = 'rpcservice'
    logprefix = 'zymb.jabber.rpc'
    
    def __init__(self, opset=None, notfoundhandler=None):
        service.Service.__init__(self)

        if (not opset):
            opset = Opset()
        self.opset = opset
        self.notfoundhandler = notfoundhandler
        
    def attach(self, agent):
        """attach() -- internal method to attach this Service to a
        JabberStream. Do not call this directly. Instead, call
        jstream.addservice(service).

        This calls the inherited class method, and then sets up the
        stanza dispatcher which catches incoming RPC calls.
        """
        
        service.Service.attach(self, agent)
        self.agent.adddispatcher(self.handlecall, name='iq', type='set')

    def setopset(self, opset):
        """setopset(opset) -> None

        Change the service's opset. An RPCService has exactly one opset
        at a time. (But opsets can contain other opsets. This method
        sets the top-level opset. See the Opset class for details.)
        """
        
        self.opset = opset

    def getopset(self):
        """getopset() -> Opset

        Return the service's current opset.
        """
        
        return self.opset

    def handlecall(self, msg):
        """handlecall() -- RPC stanza handler. Do not call.

        This parses the Jabber stanza, to extract the method name and
        arguments. It translates the arguments into Python objects.
        Then it invokes the service opset, and takes care of the various
        responses and exceptions which that may generate.
        """

        qnod = msg.getchild('query')
        if (not qnod):
            return

        if (qnod.getnamespace() != interface.NS_RPC):
            return
            
        jidstr = msg.getattr('from')
        id = msg.getattr('id')
        nod = qnod.getchild('methodCall')
        if (not nod):
            raise interface.StanzaBadRequest('no methodCall')

        try:
            (callname, callargs) = rpcdata.parsecall(nod)
        except Exception, ex:
            self.log.warning('Ill-formed RPC call from <%s>, id %s',
                jidstr, id, exc_info=True)
            raise interface.StanzaBadRequest(str(ex))
        
        self.log.debug('RPC %s %s from <%s>, id %s',
            callname, callargs, jidstr, id)

        try:
            if (not self.opset):
                raise CallNotFound
            jid = interface.JID(jidstr)
            try:
                res = self.opset(jid, callname, *callargs)
            except CallNotFound:
                res = self.notfound(msg, callname, callargs)
            respnod = rpcdata.makeresponse(res)
        except sched.Deferred, ex:
            ex.addcontext(self.deferredwrapper,
                jidstr, id, msg, callname, callargs)
            raise
        except rpcdata.RPCResponse, ex:
            respnod = rpcdata.makeresponse(ex.value)
        except rpcdata.RPCFault, ex:
            respnod = rpcdata.makefaultresponse(ex.code, ex.string)

        msg = interface.Node('iq',
            attrs={ 'to':jidstr, 'type':'result', 'id':id })
        qnod = msg.setchild('query', namespace=interface.NS_RPC)
        qnod.addchild(respnod)

        self.agent.send(msg)
        raise interface.StanzaHandled

    def deferredwrapper(self, tup, jidstr, id, msg, callname, callargs):
        """deferredwrapper() -- callback for deferred handlers.

        This is used by handlecall(), for the case where an RPC handler
        wants to undertake a deferral operation. You should not call it,
        or even try to understand it.
        """
        
        try:
            try:
                res = sched.Deferred.extract(tup)
            except CallNotFound:
                res = self.notfound(msg, callname, callargs)
            respnod = rpcdata.makeresponse(res)
        except rpcdata.RPCResponse, ex:
            respnod = rpcdata.makeresponse(ex.value)
        except rpcdata.RPCFault, ex:
            respnod = rpcdata.makefaultresponse(ex.code, ex.string)
    
        msg = interface.Node('iq',
            attrs={ 'to':jidstr, 'type':'result', 'id':id })
        qnod = msg.setchild('query', namespace=interface.NS_RPC)
        qnod.addchild(respnod)
    
        self.agent.send(msg)

    def notfound(self, msg, callname, callargs):
        """notfound(msg, callname, callargs) -- internal function called
        when an RPC method name is not recognized. Do not call.

        If a *notfoundhandler* has been set, it is called. Otherwise,
        this generates a stanza-level "item not found" error.
        """

        if (self.notfoundhandler):
            return self.notfoundhandler(msg, callname, callargs)
        raise interface.StanzaItemNotFound('RPC method ' + callname)
    
        
class CallNotFound(Exception):
    """CallNotFound: An Exception which means that there is no such RPC call
    at this server (or namespace).
    """
    pass
            
class Opset:
    """Opset: A class representing a collection of RPCs and their handlers.

    Here's the idea: a call comes in. According to JEP-0009 and the XML-RPC
    spec, it could be named 'frog', or 'server.stop', or 'InfoQuery',
    or 'auth/file/data', or 'A_Z.0:9//' for that matter. Our job is to
    call a Python function based on the name. But different applications
    have different ways of organizing calls, and we want to be able to
    organize our functions the same way.

    The Opset class allows you to do this. When you set up an RPCService,
    you supply one object of class Opset (or a subclass).

    For example, you might use a NamesOpset. This subclass contains a
    dictionary, which you can set up with a mapping of strings to callables.
    Say you set up { 'begin':beginfunc, 'end':endfunc }. The NamesOpset
    will then respond to an RPC with method 'begin' by calling *beginfunc*,
    and to 'end' by calling *endfunc*. Anything else will generate a
    CallNotFound exception.

    (Functions in a NamesOpset are invoked as func(sender, *callargs),
    where *sender* is a JID and *callargs* are the function arguments.
    Some Opset classes invoke func(sender, callname, *callargs)
    instead. See class definition for details.)

    But if you expect RPC names to be structured, you can do better.
    Say you expect the calls 'ClientBegin', 'ClientEnd', 'InfoQuery',
    'InfoReply'. You could set up a PrefixOpset. This would contain
    a dictionary mapping 'Client' and 'Info' to *other opsets*.
    Specifically:

    { 'Client': NamesOpset( {'Begin':clientbeginfunc, 'End':clientendfunc} ),
      'Info': NamesOpset( {'Query':infoqueryfunc, 'Reply':inforeplyfunc} ) }

    Depending on your application structure, this might be valuable. Or not.

    You can also create your own subclasses of Opset to parse calls as you
    like.

    Public method:

    __call__(sender, callname, *callargs) -- handle the given call.

    Stub method (available for overriding in your subclasses):
    
    precondition(sender, namehead, nametail, *callargs) -- check something.
    """
    
    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> value

        Accept, parse, and handle a call. The *sender* is a JID object.
        The *callname* is the method name (or fraction of a name, if
        the opset is nested inside another opset which parsed the
        namespace). The *callargs* are the RPC arguments, in the form
        of Python objects (ints, strings, etc).

        In this Opset base class, __call__ always raises CallNotFound.
        """
        
        raise CallNotFound

    def precondition(self, sender, namehead, nametail, *callargs):
        """precondition(sender, namehead, nametail, *callargs) -> None

        This method is invoked inside the __call__ method, after the
        opset has decided what to do, but before it does it. The
        precondition has the opportunity to raise StanzaErrors, RPCFaults,
        or RPCResponses for a whole class of calls, before a specific 
        function is invoked.

        The *sender* is a JID; the *callargs* are the RPC arguments,
        in the form of Python objects. The *namehead* and *nametail*
        represent the callname. *namehead* is the part that has been
        successfully parsed by this Opset, and *nametail* is the part
        which will be passed on to the function. For some Opset classes
        (NamesOpset and MethodOpset), *nametail* is always empty.

        In all the Opset classes in this module, the precondition method
        does nothing. It is provided so that you can create Opset
        subclasses which do application-specific checks.
        """
        pass

class NamesOpset(Opset):
    """NamesOpset: An Opset which handles a simple list of call names.

    The NamesOpset contains a dictionary, which maps strings to callables.
    When an RPC comes in, the name is found in the dictionary, and then
    the corresponding value is invoked as val(sender, *callargs).

    NamesOpset(dict={}) -- constructor.

    If you do not supply a dictionary, a new empty one is created.

    Public methods:

    getdict() -- retrieve the dictionary.
    """
    
    def __init__(self, dic=None):
        if (dic == None):
            dic = {}
        self.dic = dic

    def getdict(self):
        """getdict() -> dict

        Retrieve the dictionary. You may freely modify this dictionary.
        """
        return self.dic

    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> value

        Accept, parse, and handle a call. The *sender* is a JID object.
        The *callname* is the method name (or fraction of a name, if
        the opset is nested inside another opset which parsed the
        namespace). The *callargs* are the RPC arguments, in the form
        of Python objects (ints, strings, etc).

        This method looks up *callname* in the dictionary, and invokes
        the result (after calling
        precondition(sender, callname, '', *callargs). The third argument
        is empty because, in a NamesOpset, there is no more of the callname
        to be matched after the dictionary lookup.)
        """
        
        if (self.dic.has_key(callname)):
            val = self.dic[callname]
            self.precondition(sender, callname, '', *callargs)
            return val(sender, *callargs)
        raise CallNotFound

class MethodOpset(Opset):
    """MethodOpset: An Opset which handles a simple list of call names.

    You use the MethodOpset by subclassing it and adding methods whose
    names start with "rpc_". When an RPC comes in, its name is searched
    for as an instance method, and then the method is invoked as
    self.rpc_methodname(sender, *callargs). If there is no matching
    method which starts with "rpc_", a CallNotFound is raised.
    """
    
    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> value

        Accept, parse, and handle a call. The *sender* is a JID object.
        The *callname* is the method name (or fraction of a name, if
        the opset is nested inside another opset which parsed the
        namespace). The *callargs* are the RPC arguments, in the form
        of Python objects (ints, strings, etc).

        This method looks for an instance method named 'rpc_'+*callname*.
        It invokes this (after calling
        precondition(sender, callname, '', *callargs). The third argument
        is empty because, in a MethodOpset, there is no more of the
        callname to be matched after the method lookup.)
        """
        
        val = getattr(self, 'rpc_'+callname, None)
        if (not val):
            raise CallNotFound
        self.precondition(sender, callname, '', *callargs)
        return val(sender, *callargs)

class PrefixOpset(Opset):
    """PrefixOpset: An Opset which divides up call names according to
    prefix strings.

    The PrefixOpset contains a dictionary, which maps strings to callables.
    When an RPC comes in, the name is checked against each key in the
    dictionary. If the key is a prefix of the name, the corresponding value
    is invoked as val(sender, suffix, *callargs), where *suffix* is the
    remaining part of *callname*.

    You can (and often will) use Opset instances as values in the dictionary.

    You should be careful that no dict key is a prefix of another dict key.
    That leads to unpredictable results. Do *not* assume you will get the
    longest match. Nor the shortest match for that matter.
    
    PrefixOpset(dict={}) -- constructor.

    If you do not supply a dictionary, a new empty one is created.

    Public methods:

    getdict() -- retrieve the dictionary.
    """
    
    def __init__(self, dic=None):
        if (dic == None):
            dic = {}
        self.dic = dic

    def getdict(self):
        """getdict() -> dict

        Retrieve the dictionary. You may freely modify this dictionary.
        """
        return self.dic

    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> value

        Accept, parse, and handle a call. The *sender* is a JID object.
        The *callname* is the method name (or fraction of a name, if
        the opset is nested inside another opset which parsed the
        namespace). The *callargs* are the RPC arguments, in the form
        of Python objects (ints, strings, etc).

        This method finds a dictionary key which is a prefix of *callname*,
        and invokes the value (after calling
        precondition(sender, prefix, suffix, *callargs). The *prefix*
        and *suffix* together make up the original *callname*.)
        """
        
        for st in self.dic.keys():
            if (callname.startswith(st)):
                val = self.dic[st]
                rootname = callname[ len(st) : ]
                self.precondition(sender, st, rootname, *callargs)
                return val(sender, rootname, *callargs)
        raise CallNotFound

class SeparatorOpset(Opset):
    """SeparatorOpset: An Opset which divides up call names at a given
    string.

    The SeparatorOpset contains a separator string (by default '.'), and a
    dictionary, which maps strings to callables. When an RPC comes in,
    it is split at the first instance of the separator. The prefix is
    found in the dictionary. The corresponding value is invoked as
    val(sender, suffix, *callargs), where *suffix* is the part of *callname*
    after the separator. If the separator is not found, or if the
    prefix is not in the dictionary, CallNotFound is raised. If the separator
    appears more than once, the first instance counts.

    You can (and often will) use Opset instances as values in the dictionary.

    SeparatorOpset(sep='.', dict={}) -- constructor.

    If you do not supply a dictionary, a new empty one is created. The
    separator string can be any number of characters in the XML-RPC alphabet
    (letters, digits, underscore, period, colon, forward slash).

    Public methods:

    getdict() -- retrieve the dictionary.
    """
    
    def __init__(self, sep='.', dic=None):
        if (dic == None):
            dic = {}
        self.dic = dic
        self.sep = sep

    def getdict(self):
        """getdict() -> dict

        Retrieve the dictionary. You may freely modify this dictionary.
        """
        return self.dic

    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> value

        Accept, parse, and handle a call. The *sender* is a JID object.
        The *callname* is the method name (or fraction of a name, if
        the opset is nested inside another opset which parsed the
        namespace). The *callargs* are the RPC arguments, in the form
        of Python objects (ints, strings, etc).

        This method divides *callname* into *prefix*+separator+*suffix*.
        It looks up *prefix* in the dictionary, and invokes the result
        (after calling precondition(sender, prefix, suffix, *callargs).)
        """
        
        pos = callname.find(self.sep)
        if (pos < 0):
            CallNotFound
            
        pref = callname[ : pos ]
        rootname = callname [ pos+len(self.sep) : ]
        if (self.dic.has_key(pref)):
            val = self.dic[pref]
            self.precondition(sender, pref, rootname, *callargs)
            return val(sender, rootname, *callargs)
        raise CallNotFound

class WrapperOpset(Opset):
    """WrapperOpset: An Opset that does no parsing.

    The WrapperOpset contains another opset. It passes all calls on to
    the contained opset. The idea of SeparatorOpset is that you subclass
    it and write a precondition method, which then applies to all
    RPCs that arrive.

    WrapperOpset(subopset) -- constructor.

    Public method:

    setopset(subobset) -- change the contained opset.
    getopset() -- retrieve the contained opset.
    """
    
    def __init__(self, subopset):
        self.subopset = subopset
        
    def setopset(self, subopset):
        """setopset(subopset) -> None

        Change the contained opset.
        """
        self.subopset = subopset

    def getopset(self):
        """getopset() -> Opset

        Retrieve the contained opset.
        """
        return self.subopset
        
    def __call__(self, sender, callname, *callargs):
        """__call__(sender, callname, *callargs) -> value

        Accept, parse, and handle a call. The *sender* is a JID object.
        The *callname* is the method name (or fraction of a name, if
        the opset is nested inside another opset which parsed the
        namespace). The *callargs* are the RPC arguments, in the form
        of Python objects (ints, strings, etc).

        This method invokes the contained opset, after calling
        precondition(sender, '', callname, *callargs). The second argument
        is empty because a WrapperOpset does no callname matching.
        """
        
        self.precondition(sender, '', callname, *callargs)
        return self.subopset(sender, callname, *callargs)

class RPCClience(service.Service):
    """RPCClience: A high-level Jabber facility for making Jabber-RPC calls
    to other entities.

    (Service label: 'rpcclience'.)

    RPCClience() -- constructor.

    Public method:

    send() -- send a Jabber-RPC call, asynchronously.

    Internal methods:

    sendop() -- utility function for send().
    handlesend() -- deferred operation handler for send().
    """
    
    label = 'rpcclience'
    logprefix = 'zymb.jabber.rpc'

    def send(self, op, jid, methname, *methargs, **keywords):
        """send(op, jid, methname, *methargs, timeout=num) -> None

        Send a Jabber-RPC call, asynchronously.

        The *methname* is the RPC method name; *methargs* are the arguments.
        The optional *timeout* is a time limitation in seconds. (If the
        RPC takes longer than this to return, you will get a TimeoutException.)

        Since this is asynchronous, it does not return the RPC results
        directly. Instead, you supply callable *op*. When the results
        come in, *op* will be invoked. The *op* must be in this form:

        def op(tup):
            try:
                res = sched.Deferred.extract(tup)
            except...
                ...

        (You can also pass a tuple (opfunc, oparg, oparg2, ...) as *op*.
        This allows you to bind arguments into the callback. You would
        then structure opfunc as:
            def opfunc(tup, oparg, oparg2, ...):
        The rest of the function would follow as above.)

        If the RPC results came in cleanly, the sched.Deferred.extract(tup)
        call will raise no exception -- it will simply return the results,
        converted to the appropriate Jabber type. You can do with them as
        you will.

        If something went wrong, an exception will be raised, which you may
        wish to catch:

            sched.TimeoutException: You specified a timeout=val keyword when
                you made the call, and more than *val* seconds have passed
                without a response.
            RPCFault: An RPC fault was received, instead of a response.
                You can retrieve the fault values as ex.code and ex.string.
            interface.StanzaError: A stanza-level error was received, instead
                of a response.
                
        """
        
        try:
            self.sendop(jid, methname, *methargs, **keywords)
        except sched.Deferred, ex:
            ex.addcontext(op)
    
    def sendop(self, jid, methname, *methargs, **keywords):
        """sendop() -- utility function for send(). Do not call.
        """
        
        timeout = keywords.pop('timeout', None)
        if (keywords):
            raise ValueError, 'unknown keyword argument to send'
        
        msg = interface.Node('iq',
            attrs={ 'to':unicode(jid), 'type':'set' })
        qnod = msg.setchild('query', namespace=interface.NS_RPC)
        methnod = rpcdata.makecall(methname, *methargs)
        qnod.addchild(methnod)
    
        id = self.agent.send(msg)
        
        defer = sched.Deferred(self.handlesend)

        dsp = self.agent.adddispatcher(defer.queue, self.agent, 'result',
            name='iq', type=('result','error'), id=id, accept=True)
        if (timeout != None):
            ac = self.agent.addtimer(defer, 'timeout', delay=timeout)
        defer.addaction(ac)

        raise defer

    def handlesend(self, res, msg=None):
        """handlesend() -- deferred operation handler for send(). Do not call.
        """
        
        if (res == 'timeout'):
            raise sched.TimeoutException('rpc timed out')
        assert msg
        if (msg.getattr('type') != 'result'):
            # Error reply
            raise interface.parseerrorstanza(msg)
        
        qnod = msg.getchild('query')
        if (not qnod):
            raise interface.StanzaBadRequest(
                'rpc reply lacks <query>')
        if (qnod.getnamespace() != interface.NS_RPC):
            raise interface.StanzaBadRequest(
                'rpc reply in wrong namespace')

        methnod = qnod.getchild('methodResponse')
        if (not methnod):
            raise interface.StanzaBadRequest(
                'rpc reply lacks <methodResponse>')
            
        info = rpcdata.parseresponse(methnod)
        return info

# ------------------- unit tests -------------------

class _TestingOpset(MethodOpset):
    def rpc_hello(self, sender):
        return 'Hello'
    def rpc_goodbye(self, sender):
        return 'Goodbye'
    def rpc_twoarg(self, sender, arg):
        return 'twoarg'+arg

class _TestingPreconOpset(_TestingOpset):
    def precondition(self, sender, namehead, nametail, *callargs):
        if (namehead == 'hello'):
            raise NotImplementedError

class TestRpc(unittest.TestCase):
    """Unit tests for the rpc module.
    """

    def reflect(self, arg):
        return sched.Action(self.reflector, arg)
        
    def reflector(self, arg, sender):
        return arg

    def bireflect(self, arg):
        return sched.Action(self.bireflector, arg)
        
    def bireflector(self, arg, sender, arg2):
        return arg+arg2

    def test_namesopset(self):
        jid = 'test@test.test'
        dic = {
            'hello' : self.reflect('Hello')
        }
        opset = NamesOpset(dic)
        opset.getdict()['goodbye'] = self.reflect('Goodbye')

        self.assertEqual(opset(jid, 'hello'), 'Hello')
        self.assertEqual(opset(jid, 'goodbye'), 'Goodbye')
        self.assertRaises(CallNotFound, opset, jid, 'qwert')
        self.assertRaises(CallNotFound, opset, jid, 'hel')
        self.assertRaises(CallNotFound, opset, jid, 'hello.frog')
        
    def test_separatoropset(self):
        jid = 'test@test.test'
        dic = {
            'hello' : self.bireflect('Hello')
        }
        opset = SeparatorOpset(dic=dic)
        opset.getdict()['goodbye'] = self.bireflect('Goodbye')

        self.assertEqual(opset(jid, 'hello.frog'), 'Hellofrog')
        self.assertEqual(opset(jid, 'goodbye.cheese'), 'Goodbyecheese')
        self.assertEqual(opset(jid, 'hello.'), 'Hello')
        self.assertEqual(opset(jid, 'hello.cheese.pile'), 'Hellocheese.pile')
        self.assertRaises(CallNotFound, opset, jid, 'hello')
        self.assertRaises(CallNotFound, opset, jid, 'qwert')
        self.assertRaises(CallNotFound, opset, jid, 'qwert.yuiop')
        
    def test_separatoropsetbig(self):
        jid = 'test@test.test'
        opset = SeparatorOpset('--')
        opset.getdict()['goodbye'] = self.bireflect('Goodbye')

        self.assertEqual(opset(jid, 'goodbye--frog'), 'Goodbyefrog')
        self.assertRaises(CallNotFound, opset, jid, 'hello')
        self.assertRaises(CallNotFound, opset, jid, 'hello-1')
        self.assertRaises(CallNotFound, opset, jid, 'hello--21')
        self.assertRaises(CallNotFound, opset, jid, 'goodbye-21')

    def test_prefixopset(self):
        jid = 'test@test.test'
        dic = {
            'hello' : self.bireflect('Hello')
        }
        opset = PrefixOpset(dic)
        opset.getdict()['goodbye'] = self.bireflect('Goodbye')

        self.assertEqual(opset(jid, 'hellofrog'), 'Hellofrog')
        self.assertEqual(opset(jid, 'goodbyecheese'), 'Goodbyecheese')
        self.assertEqual(opset(jid, 'hello'), 'Hello')
        self.assertEqual(opset(jid, 'hellocheese.pile'), 'Hellocheese.pile')
        self.assertRaises(CallNotFound, opset, jid, 'hel')
        self.assertRaises(CallNotFound, opset, jid, 'qwert')
        
    def test_methodopset(self):
        jid = 'test@test.test'
        opset = _TestingOpset()
        
        self.assertEqual(opset(jid, 'hello'), 'Hello')
        self.assertEqual(opset(jid, 'goodbye'), 'Goodbye')
        self.assertRaises(CallNotFound, opset, jid, 'qwert')
        self.assertRaises(CallNotFound, opset, jid, 'hel')
        self.assertRaises(CallNotFound, opset, jid, 'hello.frog')

        self.assertEqual(opset(jid, 'twoarg', 'more'), 'twoargmore')
        self.assertRaises(TypeError, opset, jid, 'twoarg')

    def test_wrapperopset(self):
        jid = 'test@test.test'
        opset = WrapperOpset(_TestingOpset())
        
        self.assertEqual(opset(jid, 'hello'), 'Hello')
        self.assertEqual(opset(jid, 'goodbye'), 'Goodbye')
        self.assertRaises(CallNotFound, opset, jid, 'qwert')
        self.assertRaises(CallNotFound, opset, jid, 'hel')
        self.assertRaises(CallNotFound, opset, jid, 'hello.frog')

        self.assertEqual(opset(jid, 'twoarg', 'more'), 'twoargmore')
        self.assertRaises(TypeError, opset, jid, 'twoarg')

    def test_precondition(self):
        jid = 'test@test.test'
        opset = _TestingPreconOpset()

        self.assertRaises(CallNotFound, opset, jid, 'qwert')
        self.assertRaises(NotImplementedError, opset, jid, 'hello')
        self.assertEqual(opset(jid, 'goodbye'), 'Goodbye')
        