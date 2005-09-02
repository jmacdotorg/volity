"""This module contains utility functions which turn native Python data into
Jabber-RPC stanzas, and vice versa. It is mostly intended for use by
zymb.jabber.rpc.
"""
    
import re
import time
import base64
import unittest
import interface

# nameregex -- regular expression matching valid XML-RPC names
nameregex = re.compile('\\A[A-Za-z0-9_.:/]*\\Z')

def defaultvalueparser(nod, name):
    """defaultvalueparser(Node, name) -> None

    Default, do-nothing value-parser hook.
    """
    pass

valueparserhook = defaultvalueparser
        
def nameisvalid(st):
    """nameisvalid(st) -> bool

    Determine whether the given string is legal for an XML-RPC methodName.
    (This module uses the same rule for struct member names, although the
    spec doesn't explicitly say to.)
    
    A legal name is any number of letters, digits, underscores, periods,
    colons, and forward slashes.

    (Weirdly, a zero-length name seems to be legal.)
    """
    
    if (nameregex.match(st)):
        return True
    return False

class RPCType:
    """RPCType: Base class for objects which represent specific XML-RPC
    data types.

    You can instantiate an RPCType object, passing in any Python data.
    (You'll get an exception of the data can't be interpreted as the
    RPCType class.) The resulting object can be passed to makevalue(),
    etc.

    Publicly readable field:

    typename -- the XML-RPC type string

    Static method:

    parsenode() -- return the data given the child of a <value> node
    
    Method:
    
    buildnode() -- return a <value> node containing the data.
    """

    typename = None

    def __init__(self):
        raise NotImplementedError('RPCType is an abstract base class')
        
    def buildnode(self):
        valnod = interface.Node('value')
        valnod.setchilddata(self.typename, self.argstring)
        return valnod
        
    def parsenode(nod):
        raise NotImplementedError
    parsenode = staticmethod(parsenode)
    
    def __repr__(self):
        return '<RPCType \'' + self.typename + '\', ' + str(self.origarg) + '>'
    def __str__(self):
        return ('<value><' + self.typename + '>' + self.argstring
            + '</' + self.typename + '></value>')

class RPCInt(RPCType):
    """RPCInt: Represents an XML-RPC 'int' or 'i4'.

    RPCInt(val) -- constructor. *val* is cast to an int; it must fit in
    a signed 32-bit value.
    """
    
    typename = 'int'
    
    def __init__(self, arg):
        arg = int(arg)
        if (arg > 0x7FFFFFFF or arg < -0x80000000L):
            raise ValueError(str(arg) + ' does not fit in 32-bit integer')
        self.origarg = arg
        self.argstring = str(arg)

    def parsenode(nod):
        val = int(nod.getdata())
        if (val > 0x7FFFFFFF or val < -0x80000000L):
            raise ValueError(str(val) + ' does not fit in 32-bit integer')
        return val
    parsenode = staticmethod(parsenode)

class RPCBoolean(RPCType):
    """RPCBoolean: Represents an XML-RPC 'boolean'.

    RPCBoolean(val) -- constructor. *val* can be anything that Python
    can test for truth. (I.e., "if val: ...")
    """
    
    typename = 'boolean'
    
    def __init__(self, arg):
        self.origarg = arg
        if (arg):
            self.argstring = '1'
        else:
            self.argstring = '0'
            
    def parsenode(nod):
        val = nod.getdata()
        if (val == '0'):
            return False
        if (val == '1'):
            return True
        raise ValueError('boolean value was \'' + val + '\', not 0 or 1')
    parsenode = staticmethod(parsenode)

class RPCDateTime(RPCType):
    """RPCDateTime: Represents an XML-RPC 'dateTime.iso8601'.

    RPCDateTime(val=None) -- constructor. *val* can be a number (seconds
    since epoch), or a 9-tuple of the sort used by the time module. You
    can also leave *val* entirely off, in which case the current time
    is used.

    Whether this represents a local time or a UTC is the caller's decision.
    """
    
    typename = 'dateTime.iso8601'

    def __init__(self, arg=None):
        if (not(type(arg) in [tuple, time.struct_time])):
            if (arg == None):
                arg = time.localtime()
            else:
                arg = time.localtime(arg)
        self.origarg = arg
        self.argstring = time.strftime('%Y%m%dT%H:%M:%S', arg)

    def parsenode(nod):
        val = nod.getdata()
        return time.strptime(val, '%Y%m%dT%H:%M:%S')
    parsenode = staticmethod(parsenode)
        
class RPCDouble(RPCType):
    """RPCDouble: Represents an XML-RPC 'double'.

    RPCDouble(val) -- constructor. *val* is cast to a float. The XML
    representations avoid exponential notatation (as the spec requires),
    so underflows turn into zero.
    """
    
    typename = 'double'
    
    def __init__(self, arg):
        arg = float(arg)
        self.origarg = arg
        self.argstring = ('%f' % arg)
        if (self.argstring.find('e') >= 0 or self.argstring.find('E') >= 0):
            raise ValueError(str(arg) + ' does not fit in float without exponent')

    def parsenode(nod):
        val = nod.getdata()
        if (val.find('e') >= 0 or val.find('E') >= 0):
            raise ValueError('double value contained exponent')
        return float(val)
    parsenode = staticmethod(parsenode)

class RPCString(RPCType):
    """RPCString: Represents an XML-RPC 'string'.

    RPCString(val) -- constructor. *val* is cast to a string, unless it's
    a unicode object.
    """
    
    typename = 'string'

    def __init__(self, arg):
        if (type(arg) != unicode):
            arg = str(arg)
        self.origarg = arg
        self.argstring = arg
        
    def parsenode(nod):
        val = nod.getdata()
        return val
    parsenode = staticmethod(parsenode)

class RPCBase64(RPCType):
    """RPCBase64: Represents an XML-RPC 'base64'.

    RPCBase64(val) -- constructor. *val* is cast to a string. (If you want
    to send Unicode data this way, you'll have to encode it yourself, via
    UTF-8 or whatever.)
    """
    
    typename = 'base64'

    def __init__(self, arg):
        arg = str(arg)
        self.origarg = arg
        self.argstring = base64.encodestring(arg).strip()

    def parsenode(nod):
        val = nod.getdata()
        return base64.decodestring(val)
    parsenode = staticmethod(parsenode)

class RPCArray(RPCType):
    """RPCArray: Represents an XML-RPC 'array'.

    RPCArray(val) -- constructor. *val* can be anything iterable.
    The members of *val* must be either RPCType objects or Python
    data acceptable by makevalue().
    """
    
    typename = 'array'
    
    def __init__(self, arg):
        valls = [ makevalue(vv) for vv in arg ]
        self.origarg = arg
        self.argstring = '<data>...</data>'
        self.ls = valls

    def buildnode(self):
        valnod = interface.Node('value')
        arrnod = interface.Node('array')
        valnod.addchild(arrnod)
        datnod = interface.Node('data')
        arrnod.addchild(datnod)
        for val in self.ls:
            datnod.addchild(val)
        return valnod
                    
    def parsenode(nod):
        datnod = nod.getchild('data')
        if (datnod == None):
            raise ValueError('array has no data')
        valls = datnod.getchildren()
        ls = [ parsevalue(val) for val in valls ]
        return ls
    parsenode = staticmethod(parsenode)
    
class RPCStruct(RPCType):
    """RPCStruct: Represents an XML-RPC 'struct'.

    RPCStruct(val=None, **dic) -- constructor.

    You get several options for setting this up:
    
        RPCStruct( fowl=7, fish=5 )
        RPCStruct( {'fowl': 7, 'fish': 5} )
        RPCStruct( [ ('fowl', 7), ('fish', 5) ] )
        RPCStruct(val)    # *val* is anything which can be cast to a dict
        
    The key values must be accepted by nameisvalid(). (The XML-RPC spec 
    doesn't state this requirement, but I'm assuming it.) The value values
    must be either RPCType objects or Python data acceptable by makevalue().
    """
    
    typename = 'struct'
    
    def __init__(self, arg_=None, **dic):
        if (arg_ != None):
            arg_ = dict(arg_)
            dic.update(arg_)
        valls = [ (str(vv), makevalue(dic[vv])) for vv in dic ]
        for (key, nod) in valls:
            if (not nameisvalid(key)):
                raise ValueError('\'' + key + '\' is not a valid struct member name')
        self.origarg = dic
        self.argstring = '<member>...</member>'
        self.ls = valls
        
    def buildnode(self):
        valnod = interface.Node('value')
        structnod = interface.Node('struct')
        valnod.addchild(structnod)
        for (key, nod) in self.ls:
            newnod = interface.Node('member')
            newnod.setchilddata('name', key)
            newnod.addchild(nod)
            structnod.addchild(newnod)
        return valnod

    def parsenode(nod):
        memls = nod.getchildren()
        dic = {}
        for mem in memls:
            checknodename(mem, 'member')
            namenod = mem.getchild('name')
            if (namenod == None):
                raise ValueError('member without name')
            key = namenod.getdata()
            if (not nameisvalid(key)):
                raise ValueError('\'' + key + '\' is not a valid struct member name')
            valnod = mem.getchild('value')
            if (valnod == None):
                raise ValueError('member without value')
            val = parsevalue(valnod)
            if (dic.has_key(key)):
                raise ValueError('\'' + key + '\' exists in struct twice')
            dic[key] = val
        return dic
    parsenode = staticmethod(parsenode)

    
def makevalue(arg):
    """makevalue(arg) -> Node

    Return a <value> node containing *arg*, tagged with the appropriate
    type.

    Python types are converted to XML-RPC types as follows:
        bool         => 'boolean'
        int, long    => 'int'
        float        => 'double'
        str, unicode => 'string'
        JID          => 'string'  (a handy special case)
        list         => 'array'
        dict         => 'struct'

    If you want to explicitly specify a type, pass an RPCInt, RPCBoolean,
    RPCString, RPCDouble, RPCDateTime, RPCBase64, RPCArray, or RPCDict
    object. For example, to make a boolean from an expression, do:

        makevalue(RPCBoolean(boolexpr))

    Raises TypeError or ValueError if *arg* can't be converted to an XML-RPC
    type.
    """

    typ = type(arg)

    if (typ == bool):
        rpcval = RPCBoolean(arg)
    elif (typ in [int, long]):
        rpcval = RPCInt(arg)
    elif (typ == float):
        rpcval = RPCDouble(arg)
    elif (typ in [str, unicode]):
        rpcval = RPCString(arg)
    elif (typ in [list, tuple]):
        rpcval = RPCArray(arg)
    elif (typ == dict):
        rpcval = RPCStruct(arg)
    elif (isinstance(arg, interface.JID)):
        rpcval = RPCString(unicode(arg))
    elif (isinstance(arg, RPCType)):
        rpcval = arg
    else:
        raise TypeError('%s cannot be converted to an XML-RPC type' % arg)

    valnod = rpcval.buildnode()
    return valnod

def makeparams(argls):
    """makeparams(argls) -> Node

    Return a <params> node containing the list of arguments *argls*,
    in XML-RPC format.

    The arguments are converted with makevalue(), so you can pass in either
    RPCType objects or Python data.
    """
    
    parnod = interface.Node('params')
    for arg in argls:
        pnod = interface.Node('param')
        valnod = makevalue(arg)
        pnod.addchild(valnod)
        parnod.addchild(pnod)
    return parnod

def makecall(method, *argls):
    """makecall(method, [ arg1, arg2, ... ]) -> Node

    Return a <methodCall> node containing the <methodName> *method*,
    and the <params> given by the succeeding arguments *argls*.

    The arguments (if any) are converted with makevalue(), so you can pass 
    in either RPCType objects or Python data.
    """
    
    if (not nameisvalid(method)):
        raise ValueError('\'' + method + '\' is not a valid method name')
    callnod = interface.Node('methodCall')
    callnod.setchilddata('methodName', method)
    if (argls):
        parnod = makeparams(argls)
        callnod.addchild(parnod)
    return callnod

def makeresponse(arg=True):
    """makeresponse(arg=True) -> Node

    Return a <methodResponse> node containing the <params> given
    by the argument *arg*.

    The argument is converted with makevalue(), so you can pass 
    in either an RPCType object or Python data.

    An RPC response requires a value. There is no equivalent of null/None,
    so if you pass that in here, it is taken to be (boolean) True.
    """
    
    callnod = interface.Node('methodResponse')
    if (arg == None):
        arg = True
    parnod = makeparams([arg])
    callnod.addchild(parnod)
    return callnod
    
def makefaultresponse(faultcode, faultstring=''):
    """makefaultresponse(faultcode [, faultstring='']) -> Node

    Return a <methodResponse> node containing a <fault> with the given
    *faultcode* and *faultstring*.

    *faultcode* must be an int, and *faultstring* a string (or values
    convertable to those types). Do not pass RPCInt or RPCString objects.
    """
    
    rpccode = RPCInt(faultcode)
    rpcstring = RPCString(faultstring)
    callnod = interface.Node('methodResponse')
    faultnod = interface.Node('fault')
    callnod.addchild(faultnod)
    valnod = makevalue(RPCStruct(faultCode=rpccode, faultString=rpcstring))
    faultnod.addchild(valnod)
    return callnod

def checknodename(nod, wanted):
    nam = nod.getname()
    if (nam != wanted):
        raise ValueError('got \'' + nam + '\', not \'' + wanted + '\'')

def setvalueparserhook(parser):
    """setvalueparserhook(parser) -> None

    Set a global hook which will be used to parse incoming RPC values
    (in both method arguments and replies).

    To unset the global hook, and use the default Jabber-RPC parsing with
    no customization, pass None as the *parser*.

    If not None, the *parser* should be a function of the form:

        parser(node, name) -> value

    The *node* argument will be the immediate child of a <value> tag in
    a Jabber-RPC packet. The *name* will be the node's name (e.g.,
    'int', 'i4', 'boolean', etc -- assuming the packet is well-formed.)

    The *parser* function may return any value, or None to use the default
    Jabber-RPC parsing. It may also raise an exception to indicate a
    badly-formed value.

    Yes, this is global and there's no way to set it per RPC handler. Sorry.
    There's also no way to indicate a parsed value of None.
    """
    
    global valueparserhook
    if (parser == None):
        parser = defaultvalueparser
    valueparserhook = parser
        
def parsevalue(valnod):
    """parsevalue(Node) -> value

    Return the Python value extracted from a <value> Node.

    Python types are converted from XML-RPC types as follows:
        'int', 'i4' => int
        'boolean'   => bool
        'double'    => float
        'string'    => str
        'base64'    => str
        'dateTime.iso8601' => 9-tuple (see time module)
        'array'     => list
        'struct'    => dict
    """
    
    checknodename(valnod, 'value')
    subnod = valnod.getchildren()[0]
    valtyp = subnod.getname()

    val = valueparserhook(subnod, valtyp)
    if (val != None):
        return val
    
    if (valtyp == RPCInt.typename or valtyp == 'i4'):
        return RPCInt.parsenode(subnod)
    if (valtyp == RPCBoolean.typename):
        return RPCBoolean.parsenode(subnod)
    if (valtyp == RPCString.typename):
        return RPCString.parsenode(subnod)
    if (valtyp == RPCDouble.typename):
        return RPCDouble.parsenode(subnod)
    if (valtyp == RPCDateTime.typename):
        return RPCDateTime.parsenode(subnod)
    if (valtyp == RPCBase64.typename):
        return RPCBase64.parsenode(subnod)
    if (valtyp == RPCArray.typename):
        return RPCArray.parsenode(subnod)
    if (valtyp == RPCStruct.typename):
        return RPCStruct.parsenode(subnod)
        
    raise ValueError('XML-RPC value has unknown type \'' + valtyp + '\'')
        
def parseparams(parnod):
    """parseparams(Node) -> list

    Return a list of Python values extracted from a <params> Node.
    """
    
    checknodename(parnod, 'params')
    valls = []
    pnodls = parnod.getchildren()
    for pnod in pnodls:
        checknodename(pnod, 'param')
        valnod = pnod.getchild('value')
        if (valnod == None):
            raise ValueError('param has no value')
        val = parsevalue(valnod)
        valls.append(val)
    return valls
        
def parsecall(callnod):
    """parsecall(Node) -> (str, list)

    Return a method name (string) and a list of arguments, parsed from
    a <methodCall> Node.
    """
    
    checknodename(callnod, 'methodCall')
    nam = callnod.getchilddata('methodName')
    if (nam == None):
        raise ValueError('methodCall has no methodName')
    parnod = callnod.getchild('params')
    if (parnod != None):
        parls = parseparams(parnod)
    else:
        parls = []
    return (nam, parls)

class RPCResponse(Exception):
    """RPCResponse: Exception class representing an RPC response.

    You may use this when responding to incoming RPC requests. If an RPC
    handler raises this, it is equivalent to returning the value. This
    class is not used when parsing incoming RPC responses.

    RPCResponse(value=True) -- constructor.

    Public member variables:

    rpcresponse.value -- value.
    """

    def __init__(self, value=True):
        self.value = value

    def __repr__(self):
        return '<RPCResponse: ' + repr(self.value) + '>'
    def __str__(self):
        return str(self.value)
    def __unicode__(self):
        return unicode(self.value)
    
class RPCFault(Exception):
    """RPCFault: Exception class representing an RPC fault (code and string).

    This is used both when parsing incoming RPC responses (parseresponse()
    can raise an RPCFault) and when responding to incoming RPC requests
    (your RPC handler can raise RPCFault).

    RPCFault(code, string) -- constructor.

    Public member variables:

    rpcfault.code -- integer code.
    rpcfault.string -- string.
    """
    
    def __init__(self, code, st):
        if (type(code) != int):
            raise TypeError(str(code) + ' must be an int')
        if (type(st) != str and type(st) != unicode):
            raise TypeError(str(st) + ' must be a str')
        self.code = code
        self.string = st

    def __repr__(self):
        return '<RPCFault: ' + str(self.code) + ', \'' + self.string + '\'>'
    def __str__(self):
        return str(self.code) + ': ' + str(self.string)
    def __unicode__(self):
        return unicode(self.code) + ': ' + unicode(self.string)
    
def parseresponse(respnod):
    """parseresponse(Node) -> value

    Return the value parsed from a <methodResponse> Node.

    If the response is a fault, this will raise an RPCFault exception.
    You can examine ex.code and ex.string to get the specifics.
    """

    checknodename(respnod, 'methodResponse')
    nodls = respnod.getchildren()
    if (not nodls):
        raise ValueError('methodResponse has no child')
    if (len(nodls) > 1):
        raise ValueError('methodResponse has more than one child')
    nod = nodls[0]

    if (nod.getname() == 'fault'):
        valnod = nod.getchild('value')
        if (valnod == None):
            raise ValueError('fault has no value')
        dic = parsevalue(valnod)
        raise RPCFault(dic['faultCode'], dic['faultString'])

    parls = parseparams(nod)
    if (len(parls) != 1):
        raise ValueError('methodResponse has ' + str(len(parls)) + ' params, instead of 1')
    return parls[0]
    
# ------------------- unit tests -------------------

import codecs

class TestRpcData(unittest.TestCase):
    """Unit tests for the rpcdata module.
    """

    def test_nameisvalid(self):
        validlist = [
            '', 'hello', 'Hello', 'Hello9',
            'this_that', 'this:that', '/frog', 'LOOPS.'
        ]
        invalidlist = [
            ' ', ' root', 'frog ', '\nthis', 'that\n',
            'quark\\', 'comma,name', 'double"quote', 'single\'quote',
            'dol$lar', 'hy-phen', 'plus+', 'angle<', '>bracket'
        ]
        
        for st in validlist:
            self.assert_(nameisvalid(st))
        for st in invalidlist:
            self.assert_(not nameisvalid(st))

    def test_rpcint(self):
        validlist = [ 0, 5, -10, 3L, 0x7FFFFFFF, -0x80000000L ]
        validpairs = [ (0, 0), ('6', 6), ('-100', -100) ]
        
        self.assertRaises(ValueError, RPCInt, 'qwert')
        self.assertRaises(ValueError, RPCInt, 0x80000000L)
        self.assertRaises(ValueError, RPCInt, -0x80000001L)
        
        for val in validlist:
            nod = makevalue(RPCInt(val))
            self.assertEqual(val, parsevalue(nod))

        for (val, res) in validpairs:
            nod = makevalue(RPCInt(val))
            self.assertEqual(res, parsevalue(nod))

    def test_rpcdouble(self):
        validlist = [ 0, 0.5, -6.2350, 1.2e-2, 5.5e-30, 7e32 ]
        validpairs = [ (1, 1.0), ('0.6263', 0.6263) ]
        
        self.assertRaises(ValueError, RPCDouble, 'qwert')
        self.assertRaises(ValueError, RPCDouble, 7e64)
        
        for val in validlist:
            nod = makevalue(RPCDouble(val))
            self.assertAlmostEqual(val, parsevalue(nod))
            
        for (val, res) in validpairs:
            nod = makevalue(RPCDouble(val))
            self.assertAlmostEqual(res, parsevalue(nod))
            
    def test_rpcboolean(self):
        validtrue = [ True, 1, -5, 'true', [1] ]
        validfalse = [ False, 0, '', [], (), None ]
        
        for val in validtrue:
            nod = makevalue(RPCBoolean(val))
            self.assert_(parsevalue(nod))
            
        for val in validfalse:
            nod = makevalue(RPCBoolean(val))
            self.assert_(not parsevalue(nod))
            
    def test_rpcstring(self):
        validlist = [ '', '   ', 'hello', '?', u'hello', u'h\xe8llo',
            ' unstripped ', '\nnew\n\nlines\n', 'null\000 and control\001',
            '& and < and >' ]
        validpairs = [ (12, '12') ]
        
        for val in validlist:
            nod = makevalue(RPCString(val))
            self.assertEqual(val, parsevalue(nod))

        for (val, res) in validpairs:
            nod = makevalue(RPCString(val))
            self.assertEqual(res, parsevalue(nod))

    def test_rpcbase64(self):
        validlist = [ '', '   ', 'hello', '?', 'hello', 'h\xe8llo',
            ' unstripped ', '\nnew\n\nlines\n', 'null\000 and control\001',
            '& and < and >' ]
            
        for val in validlist:
            nod = makevalue(RPCBase64(val))
            self.assertEqual(val, parsevalue(nod))

    def test_rpcarray(self):
        testarray = [ 1, 2, 'three', [ 4, 5 ], [], 6.5, {'1':1, '2':2} ]

        nod = makevalue(testarray)
        self.assertEqual(testarray, parsevalue(nod))
    
    def test_rpcstruct(self):
        testdict = {
            'key' : 'value',
            'one' : 1,
            'cheese/frog' : [ 1,2,'three'],
            'empty.list' : [],
            'dict' : { 'one' : 'two', 'three' : 'four' }
        }
        sampledict = { 'one' : 'two', 'three' : 'four' }

        self.assertRaises(ValueError, RPCStruct, {',' : 'comma'})
        self.assertRaises(ValueError, RPCStruct, {'  ' : 'space'})

        nod = makevalue(testdict)
        self.assertEqual(testdict, parsevalue(nod))
        
        nod = makevalue(RPCStruct(sampledict))
        self.assertEqual(sampledict, parsevalue(nod))
        
        nod = makevalue(RPCStruct(one='two', three='four'))
        self.assertEqual(sampledict, parsevalue(nod))
        
        nod = makevalue(RPCStruct([('one','two'), ('three','four')]))
        self.assertEqual(sampledict, parsevalue(nod))
    
    def test_rpcdatetime(self):
        validpairs = [
            (1116385768, (2005, 5, 17, 23, 9, 28, 1, 137, 1)),
            ((2005, 5, 17, 23, 9, 28, 1, 137, 1), (2005, 5, 17, 23, 9, 28, 1, 137, 1)),
        ]
        
        for (val, res) in validpairs:
            nod = makevalue(RPCDateTime(val))
            # We only compare the first eight elements of the tuple,
            # because the "isdst" (is-daylight-saving-time) flag is
            # indeterminate coming out of strptime.
            self.assertEqual(res[:8], parsevalue(nod)[:8])

    def test_parsevalue(self):
        nod = interface.Node.parse('<xvalue><int>45</int></xvalue>')
        self.assertRaises(ValueError, parsevalue, nod)
        nod = interface.Node.parse('<value></value>')
        self.assertRaises(IndexError, parsevalue, nod)
        nod = interface.Node.parse('<value><squiggle>45</squiggle></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        
        nod = interface.Node.parse('<value><int>45</int></value>')
        self.assertEqual(45, parsevalue(nod))
        nod = interface.Node.parse('<value><int>4294967296</int></value>')
        self.assertRaises(ValueError, parsevalue, nod)

        nod = interface.Node.parse('<value><i4>-45</i4></value>')
        self.assertEqual(-45, parsevalue(nod))

        nod = interface.Node.parse('<value><boolean>1</boolean></value>')
        self.assert_(parsevalue(nod))
        nod = interface.Node.parse('<value><boolean>0</boolean></value>')
        self.assert_(not parsevalue(nod))
        nod = interface.Node.parse('<value><boolean>cheese</boolean></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        nod = interface.Node.parse('<value><boolean>2</boolean></value>')
        self.assertRaises(ValueError, parsevalue, nod)

        nod = interface.Node.parse('<value><double>7.8</double></value>')
        self.assertAlmostEqual(7.8, parsevalue(nod))
        nod = interface.Node.parse('<value><double>1e+5</double></value>')
        self.assertRaises(ValueError, parsevalue, nod)

        nod = interface.Node.parse('<value><string>foo</string></value>')
        self.assertEqual('foo', parsevalue(nod))
        nod = interface.Node.parse('<value><string></string></value>')
        self.assertEqual('', parsevalue(nod))
        nod = interface.Node.parse('<value><string>1&amp;2&lt;3&gt;4</string></value>')
        self.assertEqual('1&2<3>4', parsevalue(nod))

        nod = interface.Node.parse('<value><array></array></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        nod = interface.Node.parse('<value><array><data><octopus /></data></array></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        
        nod = interface.Node.parse('<value><struct><member></member></struct></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        nod = interface.Node.parse('<value><struct><member><name></name></member></struct></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        nod = interface.Node.parse('<value><struct><member><value><string>xx</string></value></member></struct></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        nod = interface.Node.parse('<value><struct><member><name>,,</name><value><string>xx</string></value></member></struct></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        nod = interface.Node.parse('<value><struct><member><name>key</name><value><string>xx</string></value></member><member><name>key</name><value><string>xx</string></value></member></struct></value>')
        self.assertRaises(ValueError, parsevalue, nod)
        
    def test_parsecall(self):
        validls = [
            [ 'hello' ],
            [ 'goodbye', 1, 2, 'three' ],
            [ 'list.er', [ 1,2,[]] ],
            [ '//', 'string', {'one':1, 'two':2} ]
        ]

        for ls in validls:
            nod = makecall(*ls)
            self.assertEqual( (ls[0], ls[1:]), parsecall(nod) )

        self.assertRaises(ValueError, makecall, 'comma,', 1)

    def test_parseresponse(self):
        validlist = [
            5, -5, 1.234, 'string',
            [], {},
            [1, 2, 'three', [4, 5], 6],
            {'one':1, 'two':2},
        ]

        nod = interface.Node.parse('<methodResponse><fault><value><struct><member><name>faultCode</name><value><int>200</int></value></member><member><name>faultString</name><value><string>Whoops</string></value></member></struct></value></fault></methodResponse>')
        self.assertRaises(RPCFault, parseresponse, nod)
        
        nod = interface.Node.parse('<methodResponse><fault><value><struct><member><name>faultCode</name><value><string>200</string></value></member><member><name>faultString</name><value><string>Whoops</string></value></member></struct></value></fault></methodResponse>')
        self.assertRaises(TypeError, parseresponse, nod)

        nod = interface.Node.parse('<methodResponse><fault><value><struct><member><name>faultString</name><value><string>Whoops</string></value></member></struct></value></fault></methodResponse>')
        self.assertRaises(KeyError, parseresponse, nod)
        
        nod = interface.Node.parse('<methodResponse><fault><value><struct><member><name>faultCode</name><value><int>200</int></value></member><member><name>faultName</name><value><int>200</int></value></member><member><name>faultString</name><value><string>Whoops</string></value></member></struct></value></fault></methodResponse>')
        self.assertRaises(RPCFault, parseresponse, nod)
        
        nod = interface.Node.parse('<methodResponse><fault><value><struct><member><name>faultXXXode</name><value><int>200</int></value></member><member><name>faultString</name><value><string>Whoops</string></value></member></struct></value></fault></methodResponse>')
        self.assertRaises(KeyError, parseresponse, nod)
        
        nod = interface.Node.parse('<methodResponse><fault></fault></methodResponse>')
        self.assertRaises(ValueError, parseresponse, nod)

        nod = makeresponse()
        self.assertEqual(True, parseresponse(nod))
        nod = makeresponse(None)
        self.assertEqual(True, parseresponse(nod))
        
        for val in validlist:
            nod = makeresponse(val)
            self.assertEqual(val, parseresponse(nod))

        nod = makefaultresponse(200, 'Whoops')
        fault = None
        try:
            parseresponse(nod)
        except RPCFault, ex:
            fault = (ex.code, ex.string)
        self.assertEqual(fault, (200, 'Whoops'))

        nod = makefaultresponse(300)
        fault = None
        try:
            parseresponse(nod)
        except RPCFault, ex:
            fault = (ex.code, ex.string)
        self.assertEqual(fault, (300, ''))

    def test_roundtrip(self):
        validlist = [
            True, False, 0,
            5, -5, 1.234, 'string',
            [], {},
            [1, 2, 'three', [4, 5], 6],
            {'one':1, 'two':2},
            u'unicode',
            u'uni\xe8code',
            u'uni\u7878code',
            '  space  ', 'new\nline\n', 'funny & char<ac>ters'
        ]
        # fails: 'null\000control\001char'
        # fails: 'uni\xe8code'

        for val in validlist:
            nod = makevalue(val)
            try:
                st = str(nod)
                nod2 = interface.Node.parse(st)
            except UnicodeEncodeError:
                st = unicode(nod)
                (utf8st, dummy) = codecs.getencoder('utf8')(st)
                nod2 = interface.Node.parse(utf8st)
            val2 = parsevalue(nod2)
            self.assertEqual(val, val2)
            typ1 = type(val)
            typ2 = type(val2)
            if (typ1 == str):
                typ1 = unicode
            if (typ2 == str):
                typ2 = unicode
            self.assertEqual(typ1, typ2)

    def test_parserhook(self):
        nod = makevalue(5)
        val = parsevalue(nod)
        self.assertEqual(val, 5)
        nod = makevalue("5")
        val = parsevalue(nod)
        self.assertEqual(val, "5")

        def parserhooktest(nod, nam):
            if (nam in ['int', 'i4']):
                return 1 + RPCInt.parsenode(nod)
        
        setvalueparserhook(parserhooktest)
    
        nod = makevalue(5)
        val = parsevalue(nod)
        self.assertEqual(val, 6)
        nod = makevalue("5")
        val = parsevalue(nod)
        self.assertEqual(val, "5")
        
        setvalueparserhook(None)
    
        nod = makevalue(5)
        val = parsevalue(nod)
        self.assertEqual(val, 5)
        nod = makevalue("5")
        val = parsevalue(nod)
        self.assertEqual(val, "5")
        

if __name__ == '__main__':
    unittest.main()
