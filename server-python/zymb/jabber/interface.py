import unittest
from zymb.xmldata import Node

# NS_ constants -- namespace strings.
#
# These are defined in the Jabber spec and the JEP documents.

# RFC-3920:
NS_JABBER_ORG_STREAMS = 'http://etherx.jabber.org/streams'
NS_CLIENT    = 'jabber:client'
NS_SERVER    = 'jabber:server'
NS_DIALBACK  = 'jabber:server:dialback'
NS_TLS       = 'urn:ietf:params:xml:ns:xmpp-tls'
NS_STREAMS   = 'urn:ietf:params:xml:ns:xmpp-streams'
NS_SASL      = 'urn:ietf:params:xml:ns:xmpp-sasl'
NS_BIND      = 'urn:ietf:params:xml:ns:xmpp-bind'
NS_SESSION   = 'urn:ietf:params:xml:ns:xmpp-session'
NS_STANZAS   = 'urn:ietf:params:xml:ns:xmpp-stanzas'

# RFC-3921:
NS_PRIVACY   = 'jabber:iq:privacy'
NS_ROSTER    = 'jabber:iq:roster'

# JEP-0004: Data Forms
NS_DATA = 'jabber:x:data'

# JEP-0009: Jabber-RPC
NS_RPC = 'jabber:iq:rpc'

# JEP-0013: Flexible Offline Message Retrieval
NS_OFFLINE = 'http://www.jabber.org/jeps/jep-0030.html'

# JEP-0020: Feature Negotiation
NS_FEATURE_NEG = 'http://jabber.org/protocol/feature-neg'

# JEP-0030: Service Discovery
NS_DISCO       = 'http://jabber.org/protocol/disco'
NS_DISCO_INFO  = 'http://jabber.org/protocol/disco#info'
NS_DISCO_ITEMS = 'http://jabber.org/protocol/disco#items'

# JEP-0045: Multi-User Chat
NS_MUC       = 'http://jabber.org/protocol/muc'
NS_MUC_USER  = 'http://jabber.org/protocol/muc#user'
NS_MUC_OWNER = 'http://jabber.org/protocol/muc#owner'
NS_GROUPCHAT = 'gc-1.0'

# JEP-0047: In-Band Bytestreams (IBB)
NS_IBB = 'http://jabber.org/protocol/ibb'

# JEP-0050: Ad-Hoc Commands
NS_COMMANDS = 'http://jabber.org/protocol/commands'

# JEP-0054: vcard-temp
NS_VCARD = 'vcard-temp'

# JEP-0060: Publish-Subscribe
NS_PUBSUB        = 'http://jabber.org/protocol/pubsub'
NS_PUBSUB_EVENT  = 'http://jabber.org/protocol/pubsub#event'
NS_PUBSUB_OWNER  = 'http://jabber.org/protocol/pubsub#owner'
NS_PUBSUB_ERRORS = 'http://jabber.org/protocol/pubsub#errors'

# JEP-0065: SOCKS5 Bytestreams
NS_BYTESTREAMS = 'http://jabber.org/protocol/bytestreams'

# JEP-0077: In-Band Registration
NS_REGISTER = 'jabber:iq:register'

# JEP-0078: Non-SASL Authentication
NS_AUTH = 'jabber:iq:auth'

# JEP-0079: Advanced Message Processing
NS_AMP = 'http://jabber.org/protocol/amp'

# JEP-0091: Delayed Delivery
NS_DELAY = 'jabber:x:delay'

# JEP-0095: Stream Initiation
NS_SI = 'http://jabber.org/protocol/si'

# JEP-0115: Entity Capabilities
NS_CAPS = 'http://jabber.org/protocol/caps'

class JID:
    """JID: A class representing a Jabber ID.
    
    The Jabber ID is defined in RFC-3920 section 3.1. Roughly, it has
    three parts, and looks like "node@domain/resource". The domain part
    is an Internet host name or dotted IP address.

    The node and resource parts are optional. (So "node@domain",
    "domain/resource", and "domain" are valid JIDs. A JID which has
    no resource part is called a "bare JID". The idea is that a bare
    JID represents an identity -- either a person or a service -- and
    different resources represent different Jabber connections which
    that identity may maintain at the same time.)

    JID(jid='', node=None, domain=None, resource=None) -- constructor.

    You have many options in creating a JID. The simplest is to call
        JID(val)
    ...where *val* is a string (or unicode object) in one of the forms
    above. *val* could also be an existing JID (thus creating a copy of
    it).

    Alternatively, you can call
        JID(node='node', domain='domain', resource='resource')
    ...thus supplying the three parts separately. Again, you can leave off
    the optional node and resource parts.

    Finally, you can say something like
        JID(val, resource='resource')
    The *val* must be a valid JID, as above; and then the resource part is
    replaced by your explicit argument. You can replace any or all of the
    parts of *val* in this way.

    Basic operations:

    str(jid) -- convert to a string. This may fail if the JID has unicode
        parts.
    unicode(jid) -- convert to a unicode object.
    (jid1 == jid2) -- compare. Since the domain part of each is a hostname,
        the comparison of that part is case-insensitive. The node and
        resource parts are case-sensitive.
    (jid == str) -- compare. The string (or unicode) part is converted
        to a JID for the comparison.
    dict[jid] -- JIDs may be used as dictionary keys.

    Public methods:

    getnode() -- return the node part of the JID.
    getdomain() -- return the domain part of the JID.
    getresource() -- return the resource part of the JID.
    setnode() -- change the node part of the JID.
    setdomain() -- change the domain part of the JID.
    setresource() -- change the resource part of the JID.
    getbare() -- given a JID, return another JID which has the same
        node and domain, and no resource.
    barematch() -- compare two JIDs, ignoring the resource part of each.
    """
        
    def __init__(self, jid='', node=None, domain=None, resource=None):
        self.node = ''
        self.domain = ''
        self.resource = ''

        if (isinstance(jid, JID)):
            self.node = jid.node
            self.domain = jid.domain
            self.resource = jid.resource
        else:
            pos = jid.find('@')
            if (pos >= 0):
                self.node = jid[ : pos ]
                jid = jid[ pos+1 : ]
                
            pos = jid.find('/')
            if (pos >= 0):
                self.resource = jid[ pos+1 : ]
                jid = jid[ : pos ]
    
            self.domain = jid.lower()

        if (node != None):
            if (not type(node) in [str, unicode]):
                raise TypeError('node must be str or unicode')
            self.node = node
        if (domain != None):
            if (not type(domain) in [str, unicode]):
                raise TypeError('domain must be str or unicode')
            self.domain = domain.lower()
        if (resource != None):
            if (not type(resource) in [str, unicode]):
                raise TypeError('resource must be str or unicode')
            self.resource = resource

        if (not self.domain):
            raise ValueError('JID must contain a domain')
        st = (self.node + self.domain + self.resource)
        if ('@' in st):
            raise ValueError('too many @ characters')
        if ('/' in st):
            raise ValueError('too many / characters')

    def __repr__(self):
        st = self.domain
        if (self.node):
            st = self.node + '@' + st
        if (self.resource):
            st = st + '/' + self.resource
        return '<JID \'' + st + '\'>'

    def __unicode__(self):
        st = unicode(self.domain)
        if (self.node):
            st = self.node + '@' + st
        if (self.resource):
            st = st + '/' + self.resource
        return st
        
    def __str__(self):
        st = str(self.domain)
        if (self.node):
            st = str(self.node) + '@' + st
        if (self.resource):
            st = st + '/' + str(self.resource)
        return st

    def __hash__(self):
        return hash(self.__unicode__())

    def __eq__(self, jid):
        if (not isinstance(jid, JID)):
            try:
                jid = JID(jid)
            except:
                return False
        return ((self.node == jid.node)
            and (self.domain == jid.domain)
            and (self.resource == jid.resource))

    def getnode(self):
        """getnode() -> str/unicode

        Return the node part of the JID, or '' if there is none.
        """
        return self.node

    def getdomain(self):
        """getdomain() -> str/unicode

        Return the domain part of the JID.
        """
        return self.domain

    def getresource(self):
        """getresource() -> str/unicode

        Return the resource part of the JID, or '' if there is none.
        """
        return self.resource

    def setnode(self, node):
        """setnode(node) -> None

        Change the node part of the JID. The value may be '' (or None)
        to clear this part.
        """
        
        if (not node):
            self.node = ''
        else:
            if (not type(node) in [str, unicode]):
                raise TypeError('node must be str or unicode')
            self.node = node

    def setdomain(self, domain):
        """setdomain(domain) -> None

        Change the domain part of the JID.
        """
        
        if (not domain):
            raise ValueError('JID must contain a domain')
        else:
            if (not type(domain) in [str, unicode]):
                raise TypeError('domain must be str or unicode')
            self.domain = domain.lower()

    def setresource(self, resource):
        """setresource(resource) -> None

        Change the resource part of the JID. The value may be '' (or None)
        to clear this part.
        """
        
        if (not resource):
            self.resource = ''
        else:
            if (not type(resource) in [str, unicode]):
                raise TypeError('resource must be str or unicode')
            self.resource = resource
                    
    def getbare(self):
        """getbare() -> JID

        Given a JID, return another JID which has the same node and domain,
        and no resource.
        """
        return JID(node=self.node, domain=self.domain)

    def barematch(self, jid):
        """barematch(jid) -> bool

        Compare two JIDs, ignoring the resource part of each. If *jid* is
        not a JID, it is converted to one for the comparison. (If it is not
        a valid JID, the comparison of course returns False.)
        """
        
        if (not isinstance(jid, JID)):
            try:
                jid = JID(jid)
            except:
                return False
        return ((self.node == jid.node)
            and (self.domain == jid.domain))

        

class StreamLevelError(Exception):
    """StreamLevelError: An Exception representing a stream-level error
    in the Jabber stream.

    This exception is never raised. It's passed in an 'error' event
    generated by the Jabber agent when the error is received. As specified
    in the Jabber spec, a stream-level error causes the agent to immediately
    shut down; it will jump to the 'end' state.

    The contents of the StreamLevelError are a tuple (errtype, errtext).
    *errtype* is a string naming the error condition ('bad-format',
    etc. See RFC-3920 section 4.7.3.) *errtext* is the text included in
    the error stanza. It may be empty.    
    """
    pass
    
class StanzaHandled(Exception):
    """StanzaHandled: An Exception which means that the stanza was handled.
    
    If a stanza handler doesn't raise this (or a StanzaError), then the
    stanza wasn't handled, and the dispatcher goes on to try some more
    dispatchers.
    """
    pass
            
class StanzaError(Exception):
    """StanzaError: A base class for Exceptions representing Jabber
    stanza-level errors.

    Class methods:

    makebyname(errorname, text=None) -> StanzaError

    Given the *errorname* of a stanza-level error (e.g., 'bad-request'),
    this builds an object of that error type (e.g., StanzaBadRequest).

    Publicly readable fields:

    errorname -- the error name ('bad-request')
    errorcode -- the equivalent numeric error code (see JEP-0086)
    errortype -- the recommended action ('cancel', 'modify', 'wait', 'auth')
    description -- a default error text, generically describing the problem
    """
    
    classnamemapping = None

    def makebyname(errorname, text=None):
        if (not StanzaError.classnamemapping):
            # build initial mapping
            dic = {}
            for cla in stanzaerrorlist:
                dic[cla.errorname] = cla
            StanzaError.classnamemapping = dic
    
        cla = StanzaError.classnamemapping.get(errorname)
        if (not cla):
            cla = StanzaUndefinedCondition
    
        return cla(text)
    makebyname = staticmethod(makebyname)
        
    errorname = None
    errorcode = None
    errortype = 'cancel'
    description = 'Error.'
    def __init__(self, text=None):
        self.text = text

    def __str__(self):
        if (self.text):
            return '<StanzaError %s: %s>' % (self.errorname, self.text)
        else:
            return '<StanzaError %s>' % (self.errorname)

class StanzaBadRequest(StanzaError):
    errorname = 'bad-request'
    errorcode = 400
    errortype = 'modify'
    description = 'The request was badly formed.'

class StanzaConflict(StanzaError):
    errorname = 'conflict'
    errorcode = 409
    errortype = 'cancel'
    description = 'The request conflicts with something.'

class StanzaFeatureNotImplemented(StanzaError):
    errorname = 'feature-not-implemented'
    errorcode = 501
    errortype = 'cancel'
    description = 'Request not implemented by this server.'

class StanzaForbidden(StanzaError):
    errorname = 'forbidden'
    errorcode = 403
    errortype = 'auth'
    description = 'That request is forbidden to you.'

class StanzaGone(StanzaError):
    errorname = 'gone'
    errorcode = 302
    errortype = 'modify'
    description = 'The item is gone.'

class StanzaInternalServerError(StanzaError):
    errorname = 'internal-server-error'
    errorcode = 500
    errortype = 'wait'
    description = 'An internal error occurred in the server.'

class StanzaItemNotFound(StanzaError):
    errorname = 'item-not-found'
    errorcode = 404
    errortype = 'cancel'
    description = 'The item cannot be found.'

class StanzaJIDMalformed(StanzaError):
    errorname = 'jid-malformed'
    errorcode = 400
    errortype = 'modify'
    description = 'That JID is malformed.'

class StanzaNotAcceptable(StanzaError):
    errorname = 'not-acceptable'
    errorcode = 406
    errortype = 'modify'
    description = 'That request is unacceptable.'

class StanzaNotAllowed(StanzaError):
    errorname = 'not-allowed'
    errorcode = 405
    errortype = 'cancel'
    description = 'That request is not allowed.'

class StanzaNotAuthorized(StanzaError):
    errorname = 'not-authorized'
    errorcode = 401
    errortype = 'auth'
    description = 'You have no authorization.'

class StanzaPaymentRequired(StanzaError):
    errorname = 'payment-required'
    errorcode = 402
    errortype = 'auth'
    description = 'That request requires payment.'

class StanzaRecipientUnavailable(StanzaError):
    errorname = 'recipient-unavailable'
    errorcode = 404
    errortype = 'wait'
    description = 'The recipient is not available now.'

class StanzaRedirect(StanzaError):
    errorname = 'redirect'
    errorcode = 302
    errortype = 'modify'
    description = 'That request should be redirected.'

class StanzaRegistrationRequired(StanzaError):
    errorname = 'registration-required'
    errorcode = 407
    errortype = 'auth'
    description = 'Registration is required before that request can be fulfilled.'

class StanzaRemoteServerNotFound(StanzaError):
    errorname = 'remote-server-not-found'
    errorcode = 404
    errortype = 'cancel'
    description = 'A service was not found.'

class StanzaRemoteServerTimeout(StanzaError):
    errorname = 'remote-server-timeout'
    errorcode = 504
    errortype = 'wait'
    description = 'A service did not respond.'
    
class StanzaResourceConstraint(StanzaError):
    errorname = 'resource-constraint'
    errorcode = 500
    errortype = 'wait'
    description = 'The request cannot be fulfilled due to lack of resources.'

class StanzaServiceUnavailable(StanzaError):
    errorname = 'service-unavailable'
    errorcode = 503
    errortype = 'cancel'
    description = 'The requested service is not available.'

class StanzaSubscriptionRequired(StanzaError):
    errorname = 'subscription-required'
    errorcode = 407
    errortype = 'auth'
    description = 'Must be subscribed to make that request.'

class StanzaUndefinedCondition(StanzaError):
    errorname = 'undefined-condition'
    errorcode = 500
    errortype = 'cancel'
    description = 'Something happened.'

class StanzaUnexpectedRequest(StanzaError):
    errorname = 'unexpected-request'
    errorcode = 400
    errortype = 'wait'
    description = 'Request arrived at the wrong time.'

# stanzaerrorlist -- a list of all the StanzaError classes.

stanzaerrorlist = [
    StanzaBadRequest, StanzaConflict, StanzaFeatureNotImplemented,
    StanzaForbidden, StanzaGone, StanzaInternalServerError,
    StanzaItemNotFound, StanzaJIDMalformed, StanzaNotAcceptable,
    StanzaNotAllowed, StanzaNotAuthorized, StanzaPaymentRequired,
    StanzaRecipientUnavailable, StanzaRedirect,
    StanzaRegistrationRequired, StanzaRemoteServerNotFound,
    StanzaRemoteServerTimeout, StanzaResourceConstraint,
    StanzaServiceUnavailable, StanzaSubscriptionRequired,
    StanzaUndefinedCondition, StanzaUnexpectedRequest
]

# stanzareversemapping -- a mapping of old-style numeric error codes to
# StanzaError classes. (See JEP-0086.)

stanzareversemapping = {
    302: StanzaRedirect,
    400: StanzaBadRequest,
    401: StanzaNotAuthorized,
    402: StanzaPaymentRequired,
    403: StanzaForbidden,
    404: StanzaItemNotFound,
    405: StanzaNotAllowed,
    406: StanzaNotAcceptable,
    407: StanzaRegistrationRequired,
    408: StanzaRemoteServerTimeout,
    409: StanzaConflict,
    500: StanzaInternalServerError,
    501: StanzaFeatureNotImplemented,
    502: StanzaServiceUnavailable,
    503: StanzaServiceUnavailable,
    504: StanzaRemoteServerTimeout,
    510: StanzaServiceUnavailable
}

def parseerrorstanza(msg):
    """parseerrorstanza(msg) -> StanzaError

    Given a Jabber message of type 'error', parse out the error it contains,
    and return it as a StanzaError. (The exception is returned, not raised.)

    The *msg* should be an interface.Node tree. The function attempts to
    find an <error> child. It understands both old-style numeric error
    codes and modern error tags. It also attempts to extract error text.
    If it can't find anything at all, it returns a StanzaUndefinedCondition.
    """
    
    errtype = ''
    errtext = ''
    errnod = msg.getchild('error')
    if (errnod):
        for nod in errnod.getchildren():
            if (nod.getname() == 'text' and nod.getnamespace() == NS_STANZAS):
                errtext = nod.getdata()
            elif (nod.getnamespace() == NS_STANZAS):
                errtype = nod.getname()
        if (not errtext):
            errtext = errnod.getdata()
        if (not errtype):
            try:
                codestr = errnod.getattr('code')
                code = int(codestr)
                errtype = stanzareversemapping[code].errorname
            except:
                pass
                
    if (not errtype):
        errtype = 'undefined-condition'
            
    ex = StanzaError.makebyname(errtype, errtext)

    return ex

# ------------------- unit tests -------------------

class TestInterface(unittest.TestCase):
    """Unit tests for the interface module.
    """

    def test_jid(self):
        validlist = [
            'hi@site.com',
            'site.com',
            'site.this.com/brick',
            'xx@yy/zz',
        ]

        for st in validlist:
            jid = JID(st)
            self.assertEqual(st, str(jid))
            self.assertEqual(st, unicode(jid))

        st = u'h\xe9llo@site.com/r\u1234source'
        jid = JID(st)
        self.assertEqual(st, unicode(jid))
        self.assertRaises(UnicodeEncodeError, str, jid)

        st = 'xx@yy/zz'
        jid = JID(st, node='xyz')
        self.assertEqual('xyz@yy/zz', str(jid))
        jid = JID(st, node='')
        self.assertEqual('yy/zz', str(jid))
        jid = JID(st, resource='')
        self.assertEqual('xx@yy', str(jid))

        st = 'xx@yy/zz'
        jid = JID(st)
        self.assertEqual('xx@yy/zz', str(jid))

        jid.setresource('zzz')
        self.assertEqual('xx@yy/zzz', str(jid))

        jid.setnode('zyxxy')
        self.assertEqual('zyxxy@yy/zzz', str(jid))

        jid.setdomain('jabber.org')
        self.assertEqual('zyxxy@jabber.org/zzz', str(jid))

        self.assertEqual('zzz', jid.getresource())
        self.assertEqual('zyxxy', jid.getnode())
        self.assertEqual('jabber.org', jid.getdomain())

        st = 'xx@yy/zz'
        jid = JID(st)
        self.assertEqual(jid, st)
        self.assertEqual(jid, JID(st))
        self.assertEqual(jid, JID(jid))
        self.assertNotEqual(jid, '')
        self.assertNotEqual(jid, 'xx@yy.com/zz')
        self.assertNotEqual(jid, JID('xx@yy.com/zz'))

        self.assert_(jid.barematch('xx@yy'))
        self.assert_(jid.barematch(JID('xx@yy')))
        self.assert_(jid.barematch('xx@yy/1'))
        self.assert_(jid.barematch(JID('xx@yy/2')))
        self.assert_(not jid.barematch('xx1@yy'))
        self.assert_(not jid.barematch('xx@yy1'))
        self.assert_(not jid.barematch(JID('xx@yy1')))
        
        dic = { st : 17 }
        val = dic[jid]
        self.assertEqual(val, 17)
        dic = { jid : 18 }
        val = dic[st]
        self.assertEqual(val, 18)

        jid = JID('xx@yy/zz')
        self.assertEqual(jid, 'xx@yy/zz')
        self.assertEqual(jid, 'xx@YY/zz')
        self.assertNotEqual(jid, 'XX@yy/zz')
        self.assertNotEqual(jid, 'xx@yy/ZZ')
        