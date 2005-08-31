"""This module provides an Agent which acts as a Jabber client. It can
connect to a Jabber server, authenticate, and send and receive Jabber
messages.

Since this is a fairly massive undertaking, this module is structured
as four classes. Each one is a base class of the next.

JabberStream (subclass of sched.Agent):
    Knows how to categorize and dispatch incoming Jabber messages.
    Also a platform for attaching services.
    This class really exists only for code organization. There is no
    reason to instantiate a JabberStream.
    
JabberConnect (subclass of JabberStream):
    Knows how to open a Jabber connection and parse the incoming XML
    stanzas. (It actually uses subsidiary agents, from the *tcp* and
    *xmlagent* modules, to do this work.)
    This class is able to negotiate stream security (SSL/TLS). It also
    provides the basic methods for sending Jabber messages and handling
    incoming ones.

JabberAuth (subclass of JabberConnect):
    Knows how to open a Jabber connection and then authenticate as
    a specific identity. It can do SASL authentication, or old-style
    (JEP-0078) authentication.
    This class provides a method for binding a JID resource, but does
    not use it. (Although note that old-style authentication automatically
    binds a resource.)

JabberAuthResource (subclass of JabberAuth):
    Knows how to open a Jabber connection, authenticate, and then bind
    one resource.
    This is the class you would use in a typical Jabber client application.
"""

import sys
import types
import logging
import codecs
import sha, base64, random, md5
from zymb import sched, tcp, xmlagent
import interface, service

# Constants representing choices for stream-level security.
SECURE_DEFAULT = ''      # TLS if port 5222, SSL if port 5223
SECURE_NONE    = 'none'  # Do not use stream security
SECURE_SSL     = 'ssl'   # Use SSL (deprecated)
SECURE_TLS     = 'tls'   # Use TLS if the server supports it, otherwise no
                         # security

# Constants representing choices for SASL authentication.
AUTH_NONE           = ''
AUTH_SASL_PLAIN     = 'PLAIN'
AUTH_SASL_DIGESTMD5 = 'DIGEST-MD5'

class Dispatcher:
    """Dispatcher: A class which represents code which handles some Jabber
    stanzas, according to specific criteria.

    This class is used internally in JabberStream. You should not
    create Dispatchers directly.

    Dispatcher(op, args, keywords, agent) -- constructor.

    The *keywords* is a dict containing a mapping of keyword:string.
    The keywords are criteria which must match the incoming stanza. If
    all the criteria match, the dispatcher's *op* is called (with
    argument list *args*). This may reject the stanza by returning, or
    accept it by raising StanzaHandled or a StanzaError.

    Keyword criteria:
    
        name: The top-level tag of the stanza. <name ... />
        type: The 'type' attribute. <name type='type' ... />
        xmlns: The 'xmlns' attribute. <name xmlns='xmlns' ... />
        id: The 'id' attribute. <name id='id' ... />
        resource: The resource part of the 'to' attribute, interpreted as a
            JID. <name to='node@domain/resource' ... /> If the JID lacks
            a resource, that is a non-match.

    If a keyword is present but the stanza lacks the corresponding 
    attribute, that is a non-match.

    You can also supply the keyword "accept=True". If you do this,
    the *op* is presumed to accept the stanza, regardless of
    whether it returns or raises. (This is useful for creating a
    dispatcher with some instance method which does not end with
    "raise StanzaHandled".)

    Public methods:

    remove() -- delete the dispatcher from its JabberStream.
    """
    
    def __init__(self, op, args, keys, agent):
        if (not args):
            self.op = op
        else:
            self.op = sched.Action(op, *args)
        assert isinstance(agent, JabberStream)
        self.agent = agent

        self.autoaccept = keys.pop('accept', False)
        
        self.name = keys.pop('name', None)
        self.typ = keys.pop('type', None)
        self.xmlns = keys.pop('xmlns', None)
        self.resource = keys.pop('resource', None)
        self.id = keys.pop('id', None)
        if (keys):
            raise TypeError('invalid keyword argument for dispatcher: '
                + ' '.join(keys.keys()))

    def __repr__(self):
        st = ''
        if (self.name):
            st += " name='" + self.name + "'"
        if (self.typ):
            if (type(self.typ) == tuple):
                st += " type=" + str(self.typ)
            else:
                st += " type='" + self.typ + "'"
        if (self.xmlns):
            st += " xmlns='" + self.xmlns + "'"
        if (self.resource):
            st += " resource='" + self.resource + "'"
        if (self.id):
            st += " id='" + self.id + "'"
        return '<Dispatcher' + st + '>'

    def check(self, stanza):
        """check(stanza) -> None

        Check to see if this dispatcher matches the stanza. If the keyword
        criteria match, try the dispatcher's operation.

        Returns if the stanza is rejected; raises StanzaHandled or a
        StanzaError if accepted.
        """
        # Could optimize this by replacing this at init time, based on
        # the arguments

        if (self.name):
            if (self.name != stanza.getname()):
                return
        if (self.xmlns):
            if (self.xmlns != stanza.getnamespace()):
                return
        if (self.resource):
            tostr = stanza.getattr('to')
            if (not tostr):
                return
            pos = tostr.find('/')
            if (pos < 0):
                return
            if (self.resource != tostr[ pos+1 : ]):
                return
        if (self.typ):
            if (type(self.typ) == tuple):
                if (not (stanza.getattr('type') in self.typ)):
                    return
            else:
                if (self.typ != stanza.getattr('type')):
                    return
        if (self.id):
            if (self.id != stanza.getattr('id')):
                return

        self.op(stanza)
        if (self.autoaccept):
            raise interface.StanzaHandled

    def remove(self):
        """remove() -> None

        Delete the dispatcher from its JabberStream.
        """
        if (self.agent):
            self.agent.deldispatcher(self)

class JabberStream(sched.Agent):
    """JabberStream: A low-level Jabber agent.
    
    Knows how to categorize and dispatch incoming Jabber messages.
    Also a platform for attaching services.
    
    This class really exists only for code organization. There is no
    reason to instantiate a JabberStream, only one of its subclasses.

    A JabberStream maintains a collection of dispatchers for incoming
    messages. A dispatcher knows how to handle some messages; it ignores
    the rest. A stanza gets handled by exactly one dispatcher.

    (There is currently no ordering of dispatchers. This shouldn't be
    a problem; in my experience, you never want two different dispatchers
    handling overlapping sets of messages. Exception: dispatchers with the
    'id' keyword are checked first. An 'id' dispatcher is also one-shot;
    once it accepts a stanza, it removes itself.)

    A higher-level abstraction is the notion of a service. You can create
    a service object and attach it to a JabberStream; the service will
    provides higher-level communication methods. The service is responsible
    for adding dispatchers to the JabberStream to do its work. The Service
    class is defined in the *service* module.

    JabberStream(jid) -- constructor.

    The JID may be a string or an interface.JID object. If the JID lacks
    a resource, 'JID/zymb' will be assumed.
    
    Agent states and events: None.

    Public methods:

    getjid() -- return the agent's JID.
    addservice(serv) -- attach a service to handle some Jabber protocol.
    getservice(serv) -- get the attached service of a given name or class.
    adddispatcher(op, *args, name=None, type=None, xmlns=None, resource=None,
        id=None, accept=False) -- add a stanza dispatcher with the specified
        criteria.
    deldispatcher(disp) -- remove a stanza dispatcher.

    Internal methods:
    
    dispatch(stanza) -- handle an incoming stanza.
    deferredwrapper(tup, stanza) -- callback used for deferred handlers.
    senderror(msg, exc) -- stub method for sending an error back to Jabber.
    generateid() -- create a unique ID for a Jabber message.
    endjabberstream() -- 'end' state handler.
    """
    
    logprefix = 'zymb.jabber'
    classcounter = 0

    def __init__(self, jid):
        sched.Agent.__init__(self)
        
        if (type(jid) in [str, unicode]):
            jid = interface.JID(jid)
        if (not jid.getresource()):
            jid.setresource('zymb')
        self.jid = jid

        self.waitingids = {} # { id: disp }
        self.dispatchers = []
        self.services = {}

        self.counter = 0
        JabberStream.classcounter += 1
        self.classid = 'jc' + str(JabberStream.classcounter) + 's'

        self.addhandler('end', self.endjabberstream)
        
    def __str__(self):
        ujidstr = unicode(self.jid)
        (jidstr, dummy) = codecs.getencoder('unicode_escape')(ujidstr)
        return '<%s %s>' % (self.__class__, jidstr)

    def getjid(self):
        """    getjid() -> JID

        Return the agent's JID. This will be a full JID, with a resource.

        The resource can change during authentication, because a Jabber
        server is not guaranteed to give you the resource you asked for.
        If you didn't provide a resource in the constructor's JID,
        getjid().getresource() will be 'zymb' before authentication,
        and whatever the Jabber server provides afterwards.
        """
        
        return self.jid

    def addservice(self, serv):
        """addservice(serv) -> None

        Attach a service to handle some Jabber protocol.

        A service is an object which provides some higher-level methods
        for the JabberStream to use, and also provides the stanza dispatchers
        to support those methods. The Service base class is defined in the
        *service* module.

        Use this method to attach a newly-created Service object to the
        JabberStream. The service will call adddispatcher() to set up its
        handlers. You can then call the service's methods.
        """
        
        serv.attach(self)
        
    def getservice(self, val):
        """getservice(serv) -> Service
        
        Get the attached service of a given name or class.

        The *serv* value can either be a Service subclass, or a string
        representing the Service (discoverable as ServiceClass.label).
        """
        
        if (type(val) == types.ClassType
            and issubclass(val, service.Service)):
            val = val.label
        return self.services.get(val)

    def adddispatcher(self, op, *args, **keys):
        """adddispatcher(op, *args, name=None, type=None, xmlns=None, 
            resource=None, id=None, accept=False) -> Dispatcher

        Add a stanza dispatcher with the specified criteria.

        This is a low-level method for controlling what happens to incoming
        Jabber stanzas. When a stanza arrives, it is tested against each
        of the JabberStream's dispatchers. The stanza must match each
        keyword argument that is given (and not None).

        If the stanza matches the dispatcher's criteria, then *op* is called
        (with the stanza as an argument). The *op* callable may do further
        matching checks. If it decides the stanza does not match, it should
        simply return. If it accepts the stanza, it should either take action
        and raise interface.StanzaHandled, or take no action and raise an
        interface.StanzaError.

        The JabberStream will continue checking the stanza against dispatchers
        until one accepts it. If none do, interface.StanzaFeatureNotImplemented
        is raised. If an exception is raised which is not a StanzaError, it
        is converted to a StanzaInternalServerError.
        
        Keyword criteria:
    
            name: The top-level tag of the stanza. <name ... />
            type: The 'type' attribute. <name type='type' ... />
            xmlns: The 'xmlns' attribute. <name xmlns='xmlns' ... />
            id: The 'id' attribute. <name id='id' ... />
            resource: The resource part of the 'to' attribute, interpreted as
                a JID. <name to='node@domain/resource' ... /> If the JID lacks
                a resource, that is a non-match.

        If a keyword is present but the stanza lacks the corresponding
        attribute, that is a non-match.

        A dispatcher added with the "id=*id*" keyword has special meaning.
        It is tested before all non-id dispatchers. Also, it is a one-shot --
        if the dispatcher accepts the stanza, it is immediately removed from
        the stream.
        
        You can also supply the keyword "accept=True". If you do this,
        the *op* is presumed to accept the stanza, regardless of
        whether it returns or raises. (This is useful for creating a
        dispatcher with some instance method which does not end with
        "raise StanzaHandled".)
        """

        disp = Dispatcher(op, args, keys, self)
        id = disp.id
        if (not id):
            self.dispatchers.insert(0, disp)
        else:
            if (self.waitingids.has_key(id)):
                raise Exception('already waiting on id \'%s\'' % id)
            self.waitingids[id] = disp
        return disp

    def deldispatcher(self, val):
        """deldispatcher(disp) -> None

        Remove a stanza dispatcher.

        The *disp* value may be a dispatcher (as returned by adddispatcher),
        or the *op* value from a dispatcher, or the *id* value from an id
        dispatcher.

        If no dispatcher matches *disp*, this does nothing.
        """
        
        if (type(val) in [str, unicode]):
            id = val
            if (self.waitingids.has_key(id)):
                self.waitingids.pop(id)
            return
        if (isinstance(val, Dispatcher)):
            if (val.id and self.waitingids.has_key(val.id)):
                self.waitingids.pop(val.id)
            if (val in self.dispatchers):
                self.dispatchers.remove(val)
            return
        biglist = (self.dispatchers + self.waitingids.values())
        ls = [ disp for disp in biglist if disp.op == val ]
        for disp in ls:
            self.deldispatcher(disp)
        return

    def endjabberstream(self):
        """endjabberstream() -- 'end' state handler. Do not call.

        This just clears out some lists and dicts, on the theory that
        cleanliness is close to provable correctness.
        """
        
        self.dispatchers = []
        self.waitingids.clear()
        self.services.clear()
    
    def dispatch(self, stanza):
        """dispatch(stanza) -- internal method to handle an incoming stanza.

        This method is used as an agent handler by the JabberConnect subclass.
        You should not call it.
        """
        
        try:
            id = stanza.getattr('id')
            if (id):
                if (self.waitingids.has_key(id)):
                    disp = self.waitingids.pop(id)
                    disp.check(stanza)
                    # Didn't get handled, so put it back...
                    self.waitingids[id] = disp
            for disp in self.dispatchers:
                disp.check(stanza)
            raise interface.StanzaFeatureNotImplemented('no matching dispatcher')
        except sched.Deferred, ex:
            ex.addcontext(self.deferredwrapper, stanza)
            raise
        except interface.StanzaHandled, ex:
            pass
        except interface.StanzaError, ex:
            self.senderror(stanza, ex)
        except Exception, ex:
            st = str(ex.__class__) + ': ' + str(ex)
            self.log.error('Uncaught exception in handler',
                exc_info=True)
            self.senderror(stanza, interface.StanzaInternalServerError(st))

    def deferredwrapper(self, tup, stanza):
        """deferredwrapper(tup, stanza) -- callback used for deferred handlers.

        This is used by dispatch(), for the case where a dispatch handler
        wants to undertake a deferral operation. You should not call it,
        or even try to understand it.
        """
        
        try:
            res = sched.Deferred.extract(tup)
        except interface.StanzaHandled, ex:
            pass
        except interface.StanzaError, ex:
            self.senderror(stanza, ex)
        except Exception, ex:
            st = str(ex.__class__) + ': ' + str(ex)
            self.log.error('Uncaught exception in handler',
                exc_info=True)
            self.senderror(stanza, interface.StanzaInternalServerError(st))
        
    def senderror(self, msg, ex):
        """senderror(msg, exc) -- stub method for sending an error back
            to Jabber.

        This is invoked by dispatcher() when a stanza handler raises a
        StanzaError. In JabberStream, it is only a stub. The JabberConnect
        class has a useful implementation.
        """
        
        self.log.error('unable to send error for <%s>: %s', msg.getname(), ex)

    def generateid(self):
        """generateid() -> str

        Create a unique ID for a Jabber message. The IDs are of the form
        'jcXsY', where X is a number unique to the JabberStream, and Y is
        a number unique within the JabberStream. It seems as good a
        scheme as any.
        """
        
        self.counter += 1
        return self.classid + str(self.counter)

        
class JabberConnect(JabberStream):
    """JabberConnect: A medium-level Jabber agent.

    Knows how to open a Jabber connection and parse the incoming XML
    stanzas. (It actually uses subsidiary agents, from the *tcp* and
    *xmlagent* modules, to do this work.)
    
    This class is able to negotiate stream security (SSL/TLS). It also
    provides the basic methods for sending Jabber messages and handling
    incoming ones.

    JabberConnect(jid, port=5222, secure=SECURE_DEFAULT, host=None)
        -- constructor.

    The JID may be a string or an interface.JID object. If the JID lacks
    a resource, 'JID/zymb' will be assumed. The *port* specifies the
    TCP port on the host. If *host* is none, it is inferred from *jid*.

    The *secure* value specifies a level of stream security:

    SECURE_DEFAULT: Use SECURE_TLS if port 5222, SECURE_SSL if port 5223.
    SECURE_NONE: Do not use stream security.
    SECURE_SSL: Use SSL security (deprecated).
    SECURE_TLS: Use TLS security, if the server supports it.

    For most Jabber servers, you can just specify the port, and the
    SECURE_DEFAULT setting will do the right thing.

    Agent states and events:

    state 'start': Initial state. Start the connection and begin parsing XML.
        Send our XML header.
        Wait for the XML agent to receive an incoming XML header; then
        jump to 'gotheader'.
    state 'restart': Restarting the stream after TLS or SASL succeeds.
        Same behavior as state 'start'.
    state 'gotheader': Wait for <features> stanza; then jump to 'streaming'.
    state 'streaming': Begin TLS if appropriate; otherwise, jump to
        'connected'.
    state 'startingtls': Waiting for TLS reply. If failure, stop the agent.
        If successful, move the TCP agent into secure mode. When the
        TCP agent reaches 'secure', the Jabber agent will restart, as
        required by the Jabber spec.
    state 'connected': The stream is connected and security is set up as
        appropriate. You can start doing work.
    event 'error' (exc, agent): An error was detected -- either in the
        connection process, or passed up from the TCP or XML layers. (The
        *agent* indicates where the error originated.)
    state 'end': The connection is closed.
        
    Public methods:

    send(msg, addid=True, addfrom=True) -- send a stanza.

    Internal methods:

    startconnection() -- 'start' state handler.
    startstream() -- handler for the TCP agent's 'connected' event.
    dispatch() -- handler for the XML agent's 'stanza' events.
    checkstreamattrs() -- handler for the XML agent's 'body' event.
    instreaming() -- 'streaming' state handler.
    handle_stanza_tlsfailure() -- TLS dispatcher.
    handle_stanza_tlsproceed() -- TLS dispatcher.
    restartstreamtls() -- handler for the TCP agent's 'secure' event.
    endconnect() -- 'end' state handler.
    handle_stanza_streamerror() -- stream-level error dispatcher.
    handle_stanza_features() -- features stanza dispatcher.
    isunanswerable() -- check whether a stanza should generate an error reply.
    senderror() -- generate an error reply to a message.
    """

    def __init__(self, jid, port=5222, secure=SECURE_DEFAULT, host=None):
        JabberStream.__init__(self, jid)

        self.domain = self.jid.getdomain()
        if (host):
            self.host = host
        else:
            self.host = self.domain
        self.port = int(port)

        if (secure == SECURE_DEFAULT):
            if (port == 5223):
                secure = SECURE_SSL
            else:
                secure = SECURE_TLS
        self.secure = secure
        
        self.parser = None
        self.parserendaction = None
        self.conn = None
        self.xmldoc = None
        self.streamfeatures = None
        self.sendingstreamdoc = False

        self.encodeunicode = codecs.getencoder('utf8')
        self.encodelatin1 = codecs.getencoder('latin-1')

        if (self.secure == SECURE_NONE):
            connectclass = tcp.TCP
        elif (self.secure == SECURE_SSL):
            connectclass = tcp.SSL
        elif (self.secure == SECURE_TLS):
            connectclass = tcp.TCPSecure
        else:
            raise Exception('invalid secure= argument')
        self.conn = connectclass(self.host, self.port)

        self.addhandler('start', self.startconnection)
        self.addhandler('streaming', self.instreaming)
        self.addhandler('end', self.endconnect)

        ac = self.conn.addhandler('connected', self.startstream)
        self.addcleanupaction(ac)
        ac = self.conn.addhandler('secure', self.restartstreamtls)
        self.addcleanupaction(ac)
        
        # It would be slightly cleaner to have an 'end' handler that
        # noted the socket's shutdown, so that we could avoid sending
        # the final </stream:stream>. But it's not a big deal. It just
        # log a warning.
        ac = self.conn.addhandler('end', self.stop)
        self.addcleanupaction(ac)

        self.addhandler('restart', self.startstream)
        
        self.parser = xmlagent.XML(self.conn)
        ac = self.parser.addhandler('end', self.stop)
        self.parserendaction = ac
        ac = self.parser.addhandler('body', self.checkstreamattrs)
        self.addcleanupaction(ac)
        ac = self.parser.addhandler('stanza', self.dispatch)
        self.addcleanupaction(ac)
        ac = self.parser.addhandler('error', self.perform, 'error')
        self.addcleanupaction(ac)

        self.adddispatcher(self.handle_stanza_streamerror, name='error')
        self.adddispatcher(self.handle_stanza_features, name='features')

    def startconnection(self):
        """startconnection() -- internal 'start' state handler. Do not call.

        Start the subsidiary TCP and XML agents.
        """

        self.conn.start()
        self.parser.start()

    def startstream(self):
        """startstream() -- handler for the TCP agent's 'connected' event.
        Do not call.

        Build a Jabber XML stream header and send it. Then wait for the
        server's stream header to arrive.
        """
        
        self.log.info('starting stream to %s:%s', self.host, self.port)
        
        nod = interface.Node('stream:stream')
        nod.setnamespace('jabber:client')
        nod.setattr('version', '1.0')
        nod.setattr('xmlns:stream', interface.NS_JABBER_ORG_STREAMS)
        nod.setattr('to', self.domain)
        self.xmldoc = nod
        
        initstr = "<?xml version='1.0'?>%s>" % str(nod)[:-2]
        st = unicode(initstr)
        (st, dummylen) = self.encodeunicode(st)
        
        self.conn.send(st)
        self.sendingstreamdoc = True
        
        # We've sent our stream header. Now wait in the 'start' (or
        # 'restart') state until the server's stream header comes in.

    def checkstreamattrs(self, tag, ns, attrs):
        """checkstreamattrs() -- handler for the XML agent's 'body' event.
        Do not call.

        Make sure that the incoming Jabber stream header is legal. (If not,
        stop.) If this is a modern Jabber server, jump to 'gotheader' to
        wait for the features stanza; if not, jump to 'streaming'.
        """
        
        if (tag != 'stream' or ns != interface.NS_JABBER_ORG_STREAMS):
            self.log.debug('rejecting doc header <%s xmlns=\'%s\'>',
                tag, ns)
            if (tag != 'stream'):
                errtype = 'invalid-xml'
                errtext = 'xml document tag was ' + tag
            else:
                errtype = 'invalid-namespace'
                errtext = 'xml document namespace was ' + ns
            self.perform('error',
                interface.StreamLevelError(errtype, errtext), self)
            self.stop()
            return

        if (attrs.has_key('version')):
            vers = attrs.get('version')
            versls = vers.split('.')
            if (versls and int(versls[0]) >= 1):
                # Wait for a features stanza
                self.jump('gotheader')
                return
                
        # We're talking to an old server. Don't wait for features; skip
        # straight to the streaming state.
        self.jump('streaming')

    def instreaming(self):
        """instreaming() -- 'streaming' state handler. Do not call.

        The connection is up and we've got the features (if any). Now
        it's time for stream-level security.

        It's possible we've already gone round starting TLS and restarting
        the stream. If so, jump to 'connected'. It's also possible that
        there is no security, or that TLS is not available, or that we're
        using SSL and security was started up before Jabber streaming.
        In those cases, also jump to 'connected'. 

        Otherwise, we start TLS negotiation. Send the request, set up
        the stanza dispatchers to await the reply, and jump to 'startingtls'.
        """
        
        if (self.secure != SECURE_TLS):
            # Either we've been asked to use no security, or SSL was started
            # several stages ago. We are fully connected.
            self.jump('connected')
            return
        if (self.conn.ssl):
            # We've already started TLS. We are fully connected.
            self.jump('connected')
            return
            
        foundtls = False
        for nod in self.streamfeatures:
            if (nod.getname() == 'starttls'
                and nod.getnamespace() == interface.NS_TLS):
                foundtls = True
        if (not foundtls):
            # This server does not support TLS. We are fully connected.
            self.jump('connected')
            return
            
        self.log.info('requesting tls negotiation')
        nod = interface.Node('starttls')
        nod.setnamespace(interface.NS_TLS)
        self.adddispatcher(self.handle_stanza_tlsproceed, name='proceed')
        self.adddispatcher(self.handle_stanza_tlsfailure, name='failure')
        self.send(nod)
        self.jump('startingtls')

    def handle_stanza_tlsfailure(self, msg):
        """handle_stanza_tlsfailure() -- TLS dispatcher. Do not call.

        TLS was denied. Stop the agent.
        """
        
        self.log.error('request to start tls was denied')
        self.perform('error',
            Exception('request to start tls was denied'), self)
        self.stop()
        raise interface.StanzaHandled

    def handle_stanza_tlsproceed(self, msg):
        """handle_stanza_tlsproceed() -- TLS dispatcher. Do not call.

        TLS was accepted. Tell the TCP agent to start socket-level security.
        When it does, it will jump to the 'secure' state, triggering our
        restartstreamtls() handler.
        """
        
        self.log.info('starting tls')
        self.conn.beginssl()
        raise interface.StanzaHandled

    def restartstreamtls(self):
        """restartstreamtls() -- handler for the TCP agent's 'secure' event.
        Do not call.

        The Jabber spec says that once TLS begins, both sides have to chop
        off their Jabber streams and start new ones. We do this by killing
        our XML agent and creating a new one, and then jumping to the
        'restart' state. That will trigger the startstream() handler.
        
        (The second time around, we'll see that TLS is already started,
        so we'll bypass 'startingtls' and proceed to the 'connected' state.)
        """
        
        if (self.state != 'startingtls'):
            self.log.error('connection began ssl from wrong state')
            self.perform('error',
                Exception('connection began ssl from wrong state'), self)
            self.stop()
            return
            
        self.log.info('tls begun successfully -- restarting stream')
        
        # We must now redo our initial setup code. I haven't bothered
        # to abstract it into a separate function, because there's not
        # that much of it.

        self.deldispatcher(self.handle_stanza_tlsproceed)
        self.deldispatcher(self.handle_stanza_tlsfailure)

        # Delete the old 'end' handler from parser, so that we can stop
        # parser without killing ourself        
        self.parserendaction.remove()
        self.parser.stop()

        self.parser = None
        self.parserendaction = None
        self.xmldoc = None
        self.streamfeatures = None
        self.sendingstreamdoc = False

        # Create a new XML parser. (Easier than resetting the old one.)

        self.parser = xmlagent.XML(self.conn)
        ac = self.parser.addhandler('end', self.stop)
        self.parserendaction = ac
        ac = self.parser.addhandler('body', self.checkstreamattrs)
        self.addcleanupaction(ac)
        ac = self.parser.addhandler('stanza', self.dispatch)
        self.addcleanupaction(ac)

        self.parser.start()

        # Now we jump to 'restart'. The handler attached to this is
        # self.startstream, so we'll send off a new stream header and
        # everything will proceed after that.
        
        self.jump('restart')
        
        
    def endconnect(self):
        """endconnect() -- 'end' state handler. Do not call.

        Send the terminator for our Jabber stream, if we're in the middle
        of it. Then stop the XML and TCP agents.
        """
        
        if (self.sendingstreamdoc):
            self.conn.send('</stream:stream>')
            self.sendingstreamdoc = False

        self.xmldoc = None

        self.parser.stop()
        self.conn.stop()
        self.parser = None
        self.conn = None

    def handle_stanza_streamerror(self, msg):
        """handle_stanza_streamerror() -- stream-level error dispatcher.
        Do not call.

        This responds to stream-level errors. As required by the spec,
        a stream-level error stops the agent and shuts down the connection.
        """
        
        errtype = 'undefined-condition'
        errtext = ''
        ls = msg.getchildren()
        for nod in ls:
            if (nod.getname() == 'text' and nod.getnamespace() == interface.NS_STREAMS):
                errtext = nod.getdata()
            elif (nod.getnamespace() == interface.NS_STREAMS):
                errtype = nod.getname()
        if (not errtext):
            errtext = msg.getdata()
                
        self.log.warning('Stream-level error: <%s> %s', errtype, errtext)
        self.perform('error',
            interface.StreamLevelError(errtype, errtext), self)
        self.stop()
        raise interface.StanzaHandled

    def handle_stanza_features(self, msg):
        """handle_stanza_features() -- features stanza dispatcher. Do not
        call.

        When the features stanza arrives, if we're waiting in the 'gotheader'
        state, jump to 'streaming'.
        """
        
        if (not self.streamfeatures):
            self.streamfeatures = msg.getchildren()
        if (self.state == 'gotheader'):
            self.jump('streaming')
        raise interface.StanzaHandled
        
    def send(self, msg, addid=True, addfrom=True):
        """send(msg, addid=True, addfrom=True) -> id

        Send a Jabber message. The *msg* must be a properly-formatted
        interface.Node tree containing the message.

        If *addid* is True, this generates a unique message ID and adds
        it. If *addfrom* is True, this sets the 'from' attr to the agent's
        own JID. These two arguments are ignored if you have already added
        the relevant attribute ('id' or 'from') to the top level of *msg*.

        Return the ID of the message, or None if the message goes out without
        an ID.
        """
        
        id = msg.getattr('id')
        if (addid):
            if (not id):
                id = self.generateid()
                msg.setattr('id', id)

        if (addfrom):
            fromaddr = msg.getattr('from')
            if (not fromaddr):
                msg.setattr('from', unicode(self.jid))

        msg.setparent(self.xmldoc)
        st = unicode(msg)
        (st, dummylen) = self.encodeunicode(st)
        msg.remove()
        
        self.conn.send(st)
        return id

    def isunanswerable(self, msg):
        """isunanswerable() -- internal method to check whether a stanza
        should generate an error reply. Do not call.

        The spec requires that certain kinds of messages never receive errors
        in reply. Notably, you should never reply to an error with another
        error. (This safeguards against echo loops.)

        This tests a message to see if it is unanswerable. It's somewhat
        hardwired: there's a special rule for 'iq' stanzas, which I have
        implemented, but I might have missed special rules for other kinds
        of stanzas. However, I think it covers the important bits.
        """
        
        if (msg.getname() == 'error'):
            return True

        if (msg.getattr('type') == 'error'):
            return True

        if (msg.getname() == 'iq' and msg.getattr('type') == 'result'):
            return True

        return False

    def senderror(self, msg, ex):
        """senderror() -- generate an error reply to a message. Do not call.

        This is called by the JabberStream base class if a stanza dispatcher
        raises a StanzaError. (Or if no dispatcher catches a stanza, thus
        causing StanzaFeatureNotImplemented; or if a dispatcher throws
        an unknown exception, thus causing StanzaInternalServerError.)

        This checks to make sure it's legal to reply with an error to the
        stanza. If so, it assembles a stanza-level Jabber error, and sends it.
        """
        
        id = msg.getattr('id', '')
        jidstr = msg.getattr('from')
        
        desc = ex.description
        if (ex.text):
            desc = ex.text
            
        flag = self.isunanswerable(msg)
        
        if (flag):
            self.log.debug('Rejecting <%s id=\'%s\'> from <%s> (no reply): %s: %s',
                msg.getname(), id, jidstr, ex.errorname, desc)
            return
            
        self.log.debug('Rejecting <%s id=\'%s\'> from <%s>: %s: %s',
            msg.getname(), id, jidstr, ex.errorname, desc)

        errmsg = msg.copy()
        if (jidstr):
            errmsg.setattr('to', jidstr)
        errmsg.setattr('type', 'error')
        
        dic = { 'type': ex.errortype }
        if (ex.errorcode):
            dic['code'] = ex.errorcode
        errnod = errmsg.setchild('error', attrs=dic)
        errnod.setchild(ex.errorname, namespace=interface.NS_STANZAS)

        dic = { 'xml:lang': 'en' }
        errnod.setchild('text', attrs=dic, namespace=interface.NS_STANZAS)
        errnod.setchilddata('text', desc)

        return self.send(errmsg, addid=False)
        

class JabberAuth(JabberConnect):
    """JabberAuth: A high-level Jabber agent.

    Knows how to open a Jabber connection and then authenticate as
    a specific identity. It can do SASL authentication, or old-style
    (JEP-0078) authentication.
    
    This class provides a method for binding a JID resource, but does
    not use it. (Although note that old-style authentication automatically
    binds a resource.)

    JabberAuth(jid, password, port=5222, secure=SECURE_DEFAULT,
        register=False, host=None) -- constructor.

    The JID may be a string or an interface.JID object. If the JID lacks
    a resource, 'JID/zymb' will be assumed. The *password* is used to
    authenticate. If *register* is true, the agent tries to register
    a new account (with the given JID and password) before authenticating.
    (Not all Jabber servers permit autoregistration in this way.)

    The *port* specifies the TCP port on the host. If *host* is None, it
    is inferred from *jid*. The *secure* value specifies a level of stream
    security:

    SECURE_DEFAULT: Use SECURE_TLS if port 5222, SECURE_SSL if port 5223.
    SECURE_NONE: Do not use stream security.
    SECURE_SSL: Use SSL security (deprecated).
    SECURE_TLS: Use TLS security, if the server supports it.

    For most Jabber servers, you can just specify the port, and the
    SECURE_DEFAULT setting will do the right thing.

    Agent states and events:

    (See JabberConnect for states used while connecting and negotiating
    security. In addition to those, JabberAuth uses the following:)

    state 'start': Initial state. Begin connection process.
    state 'restart': Restarting the stream after TLS or SASL succeeds.
        Same behavior as state 'start'.
    state 'connected': The stream is connected and security is set up as
        appropriate. Begin authentication.
    state 'registering': Attempting in-band registration of a new account.
    state 'authnonsasl': Performing non-SASL authentication.
    state 'nonsaslwaitfields': Non-SASL authentication; waiting for field list.
    state 'nonsaslwaitresponse': Non-SASL authentication; waiting for reply.
    state 'authsasl': Performing SASL authentication.
    state 'saslwaitchallenge': SASL authentication; waiting for challenges.
    state 'saslrestarting': SASL authentication; restarting stream.
    state 'authed': Authentication has succeeded. You can start doing work.
    event 'bound' (resource): The stream has successfully bound a Jabber
        resource. If non-SASL authentication occurs, this happens once
        during authentication. Otherwise, it occurs only when bindresource()
        is called successfully.
    event 'error' (exc, agent): An error was detected -- either in the
        connection process, during authentication, or passed up from the
        TCP or XML layers. (The *agent* indicates where the error originated.)
    state 'end': The connection is closed.

    Public methods:

    bindresource() -- bind a JID resource to the authenticated stream.

    Internal methods:

    beginauth() -- 'connected' state handler.
    beginnonsasl() -- 'authnonsasl' state handler.
    handle_stanza_nonsaslfields() -- non-SASL process dispatcher.
    handle_stanza_nonsaslresponse() -- non-SASL process dispatcher.
    beginsasl() -- 'authsasl' state handler.
    handle_stanza_saslchallenge() -- SASL process dispatcher.
    handle_stanza_saslfailure() -- SASL process dispatcher.
    handle_stanza_saslsuccess() -- SASL process dispatcher.
    restartstreamsasl() -- 'saslrestarting' state handler.
    beginregister() -- 'registering' state handler.
    handle_stanza_register() -- registration process dispatcher.
    handle_stanza_registering() -- registration process dispatcher.
    announceinitialresource() -- 'authed' state handler.
    handle_stanza_binding() -- binding process dispatcher.
    
    """

    def __init__(self, jid, password, port=5222, secure=SECURE_DEFAULT, register=False, host=None):
        JabberConnect.__init__(self, jid, port, secure, host)

        self.password = password
        self.authenticated = False
        if (register == False or register == None):
            self.autoregister = False
        else:
            self.autoregister = True
            self.registeremail = str(register)
        self.authresponsecode = None
        self.initialresourcebound = False

        self.addhandler('connected', self.beginauth)
        self.addhandler('registering', self.beginregister)
        self.addhandler('authnonsasl', self.beginnonsasl)
        self.addhandler('authsasl', self.beginsasl)
        self.addhandler('saslrestarting', self.restartstreamsasl)
        self.addhandler('authed', self.announceinitialresource)

    def beginauth(self):
        """beginauth() -- 'connected' state handler. Do not call.

        It's possible we've just gone round authentication and restarting the
        stream. If so, jump to 'authed'. If not, we may want to register
        a new account, in which case jump to 'registering'.

        If not, it's time to authenticate. Look at the server's features
        to determine whether to use SASL or non-SASL (JEP-0078) authentication.
        If SASL, also determine whether to use DIGEST-MD5 or PLAIN. Then
        jump to the appropriate state.
        """
        
        if (self.autoregister):
            # Before we auth, we will try to register a new account.
            # Whether that process succeeds or fails, it will return
            # to 'connected' when it finishes.
            assert (not self.authenticated)
            self.jump('registering')
            return
        
        if (self.authenticated):
            # Whoops, this is our second time through the connected state.
            self.jump('authed')
            return
            
        foundsasl = False
        authforms = []
        for nod in self.streamfeatures:
            if (nod.getname() == 'mechanisms'
                and nod.getnamespace() == interface.NS_SASL):
                foundsasl = True
                for mech in nod.getchildren():
                    if (mech.getname() == 'mechanism'
                        and mech.getdata() == AUTH_SASL_PLAIN):
                        authforms.append(AUTH_SASL_PLAIN)
                    if (mech.getname() == 'mechanism'
                        and mech.getdata() == AUTH_SASL_DIGESTMD5):
                        authforms.append(AUTH_SASL_DIGESTMD5)
                if (AUTH_SASL_DIGESTMD5 in authforms):
                    self.authform = AUTH_SASL_DIGESTMD5
                elif (AUTH_SASL_PLAIN in authforms):
                    self.authform = AUTH_SASL_PLAIN
                else:
                    ex = interface.StanzaNotAuthorized('server does not support DIGEST-MD5 or PLAIN sasl')
                    self.log.error('authentication could not begin: <%s> %s',
                        ex.errorname, ex.text)
                    self.perform('error', ex, self)
                    self.stop()
                    return

        if (not foundsasl):
            self.jump('authnonsasl')
        else:
            self.jump('authsasl')

    def beginnonsasl(self):
        """beginnonsasl() -- 'authnonsasl' state handler. Do not call.

        Begin the non-SASL (JEP-0078) authentication process. Send an
        <iq> stanza; set up a dispatcher to wait for the reply; jump to
        the 'nonsaslwaitfields' state.
        """

        self.log.info('beginning non-sasl authentication of <%s>',
            unicode(self.jid))

        msg = interface.Node('iq', attrs={'type':'get', 'to':self.domain})
        nod = msg.setchild('query', namespace=interface.NS_AUTH)
        nod.setchilddata('username', self.jid.getnode())
        id = self.send(msg, addfrom=False)

        # Subtlety here: we don't specify xmlns because that's attached 
        # to the query child, not the top-level node.
        self.adddispatcher(self.handle_stanza_nonsaslfields,
            name='iq', type=('result','error'), id=id)

        self.jump('nonsaslwaitfields')

    def handle_stanza_nonsaslfields(self, msg):
        """handle_stanza_nonsaslfields() -- non-SASL process dispatcher.
        Do not call.

        Accept a query, which should list the fields we need to authenticate:
        username, resource, and at least one of digest and password. Send an
        <iq> stanza; set up a dispatcher to wait for the reply; jump to
        the 'nonsaslwaitresponse' state.
        """
        
        nod = msg.getchild('query')
        if (not nod or nod.getnamespace() != interface.NS_AUTH):
            # Not addressed to us
            return
            
        if (msg.getattr('type') != 'result'):
            # Error response. Authentication has failed.
            ex = interface.parseerrorstanza(msg)
            self.log.error('authentication could not begin: <%s> %s',
                ex.errorname, ex.text)
            self.perform('error', ex, self)
            self.stop()
            raise interface.StanzaHandled

        # The query list should contain 'username', 'resource', and at least
        # one of 'digest' and 'password'.

        subnod = nod.getchild('resource')
        if (not subnod):
            raise interface.StanzaBadRequest(
                'nonsasl fields list lacks <resource>')
        subnod = nod.getchild('username')
        if (not subnod):
            raise interface.StanzaBadRequest(
                'nonsasl fields list lacks <username>')
        if (subnod.getdata() != self.jid.getnode()):
            raise interface.StanzaBadRequest(
                'nonsasl fields list <username> does not match auth request')

        if (nod.getchild('digest')):
            usedigest = True
        elif (nod.getchild('password')):
            usedigest = False
        else:
            raise interface.StanzaBadRequest(
                'nonsasl fields list lacks <digest> and <password>')

        newmsg = interface.Node('iq', attrs={'type':'set', 'to':self.domain})
        newnod = newmsg.setchild('query', namespace=interface.NS_AUTH)
        newnod.setchilddata('username', self.jid.getnode())
        newnod.setchilddata('resource', self.jid.getresource())
        if (not usedigest):
            newnod.setchilddata('password', self.password)
        else:
            streamid = self.parser.docattrs.get('id', '')
            dat = sha.new(streamid+self.password).hexdigest().lower()
            newnod.setchilddata('digest', dat)
        
        id = self.send(newmsg, addfrom=False)
        self.adddispatcher(self.handle_stanza_nonsaslresponse,
            name='iq', type=('result','error'), id=id)
        
        self.jump('nonsaslwaitresponse')
        raise interface.StanzaHandled

    def handle_stanza_nonsaslresponse(self, msg):
        """handle_stanza_nonsaslresponse() -- non-SASL process dispatcher.
        Do not call.

        Accept a response which says whether authentication succeeded. If
        it failed, stop the agent. If it succeeded, jump to 'authed'.
        """
        
        if (msg.getattr('type') != 'result'):
            # Error response. Authentication has failed. We're not going to
            # go back and ask for another password; just kill this agent.

            ex = interface.parseerrorstanza(msg)
            self.log.error('authentication failed: <%s> %s',
                ex.errorname, ex.text)
            self.perform('error', ex, self)
            self.stop()
            raise interface.StanzaHandled

        # Non-SASL authing takes care of the initial resource binding.
        self.initialresourcebound = True

        # Discard security information, on general principles
        self.password = None
        
        self.jump('authed')
        raise interface.StanzaHandled

    def beginsasl(self):
        """beginsasl() -- 'authsasl' state handler. Do not call.

        Begin the SASL authentication process. (See RFC 2222, 2595,
        2831.) Send an <auth> stanza specifying a mechanism. If using
        the PLAIN mechanism, include the appropriate data. Set up a
        dispatcher to wait for the reply; jump to the 'saslwaitchallenge'
        state.
        """

        self.log.info('beginning sasl (%s) authentication of <%s>',
            self.authform, unicode(self.jid))
        
        self.adddispatcher(self.handle_stanza_saslchallenge,
             name='challenge', xmlns=interface.NS_SASL)
        self.adddispatcher(self.handle_stanza_saslfailure,
             name='failure', xmlns=interface.NS_SASL)
        self.adddispatcher(self.handle_stanza_saslsuccess,
             name='success', xmlns=interface.NS_SASL)

        msg = interface.Node('auth',
            attrs={ 'mechanism' : self.authform, 'xmlns' : interface.NS_SASL })
        if (self.authform == AUTH_SASL_PLAIN):
            identstr = '%s@%s' % (self.jid.getnode(), self.jid.getdomain())
            dat = ('%s\x00%s\x00%s'
                % (identstr, self.jid.getnode(), self.password))
            # UTF8 and then base64
            (st, dummylen) = self.encodeunicode(dat)
            st64 = base64.encodestring(st).replace('\n', '')
            msg.setdata(st64)
        self.send(msg, addid=False, addfrom=False)

        self.jump('saslwaitchallenge')
        
    def handle_stanza_saslchallenge(self, msg):
        """handle_stanza_saslchallenge() -- SASL process dispatcher.
        Do not call.

        Accept a SASL challenge and build the correct response. If this
        is a reply to our previous challenge response, check its validity
        (stopping the agent if it is invalid).
        """
        
        self.log.debug('got sasl challenge')
        dat = msg.getdata()
        chal = {}
        
        chalstr = base64.decodestring(dat)
        ls = chalstr.split(',')
        for st in ls:
            pos = st.find('=')
            if (pos >= 0):
                key = st[ : pos ]
                val = st[ pos+1 : ]
                if (val[0] == val[-1] == '"'):
                    val = val[1: -1]
                chal[key] = val

        if (chal.has_key('rspauth')):
            responsevalue = chal['rspauth']
            if (responsevalue.lower() != self.authresponsecode):
                # Reply with an abort message.
                ex = interface.StanzaNotAuthorized('server sasl response did not match our challenge')
                self.log.error('authentication failed: <%s> %s',
                    ex.errorname, ex.text)
                self.perform('error', ex, self)
                msg = interface.Node('abort',
                    attrs={ 'xmlns' : interface.NS_SASL })
                self.send(msg, addid=False, addfrom=False)
                self.stop()
            else:
                # Reply with an empty (success) response.
                msg = interface.Node('response',
                    attrs={ 'xmlns' : interface.NS_SASL })
                self.send(msg, addid=False, addfrom=False)
            raise interface.StanzaHandled
            
        valid = True
        if (chal.has_key('charset') and chal['charset'] != 'utf-8'):
            valid = False
        if (chal.has_key('algorithm') and chal['algorithm'] != 'md5-sess'):
            valid = False
        if (chal.has_key('qop') and chal['qop'] != 'auth'):
            valid = False
        if (not chal.has_key('nonce')):
            valid = False

        if (not valid):
            ex = interface.StanzaNotAuthorized('sasl challenge contained incorrect fields')
            self.log.error('authentication could not begin: <%s> %s',
                ex.errorname, ex.text)
            self.perform('error', ex, self)
            self.stop()
            raise ex
            

        nonce = chal['nonce']

        cnonce = [ hex(0x10000+random.randrange(0xFFFF))[-4:]
            for ix in range(8) ]
        cnonce = ''.join(cnonce)
        cnonce = cnonce.lower()

        noncecount = ('%08x' % 1).lower()

        jidpass = u'%s:%s:%s' % (self.jid.getnode(), self.jid.getdomain(), self.password)
        # We want to UTF-8-encode this *unless* it all fits in 8859-1, in
        # which case we want to 8859-1-encode it.
        try:
            (jidpassstr, dummylen) = self.encodelatin1(jidpass)
        except UnicodeEncodeError:
            (jidpassstr, dummylen) = self.encodeunicode(jidpass)
        
        ls = [ encoderawdigest(jidpassstr), nonce, cnonce ]
        A1 = ':'.join(ls)

        A2 = 'AUTHENTICATE:xmpp/'

        A2R = ':xmpp/'
        ls = [ nonce, noncecount, cnonce, 'auth', encodehexdigest(A2R) ]
        responserhs = (':'.join(ls))

        responsevalue = encodehexkd(encodehexdigest(A1), responserhs)
        self.authresponsecode = responsevalue

        ls = [ nonce, noncecount, cnonce, 'auth', encodehexdigest(A2) ]
        responserhs = (':'.join(ls))

        responsevalue = encodehexkd(encodehexdigest(A1), responserhs)
            
        resp = []
        resp.append('username="%s"' % qdstrencode(self.jid.getnode()))
        resp.append('realm="%s"' % qdstrencode(self.jid.getdomain()))
        resp.append('nonce="%s"' % nonce)
        resp.append('cnonce="%s"' % cnonce)
        resp.append('nc=%s' % noncecount)
        resp.append('qop=auth')
        resp.append('digest-uri="xmpp/"')
        resp.append('response=%s' % responsevalue)
        resp.append('charset=utf-8')

        respstr = (','.join(resp))
        resp64 = base64.encodestring(respstr).replace('\n', '')
        
        msg = interface.Node('response',
            attrs={ 'xmlns' : interface.NS_SASL })
        msg.setdata(resp64)
        
        self.send(msg, addid=False, addfrom=False)
        
        raise interface.StanzaHandled

    def handle_stanza_saslfailure(self, msg):
        """handle_stanza_saslfailure() -- SASL process dispatcher.
        Do not call.

        Accept a message saying that authentication has failed. Stop the
        agent.
        """
        
        errtype = 'error'
        errtext = 'sasl authentication failed'
        ls = msg.getchildren()
        if (ls):
            errtype = ls[0].getname()
        self.log.error('sasl authentication failed: <%s>', errtype)
        self.perform('error', SASLError(errtype, errtext), self)
        self.stop()
        raise interface.StanzaHandled

    def handle_stanza_saslsuccess(self, msg):
        """handle_stanza_saslsuccess() -- SASL process dispatcher.
        Do not call.

        Accept a message saying that authentication succeeded. Jump to
        state 'saslrestarting'.
        """
        
        if (self.state != 'saslwaitchallenge'):
            self.log.error('got sasl stanza from wrong state')
            self.perform('error',
                Exception('got sasl stanza from wrong state'), self)
            self.stop()
            raise interface.StanzaHandled
            
        self.jump('saslrestarting')
        raise interface.StanzaHandled

    def restartstreamsasl(self):
        """restartstreamsasl() -- 'saslrestarting' state handler. Do not call.

        The Jabber spec says that once SASL succeeds, both sides have to chop
        off their Jabber streams and start new ones. We do this by killing
        our XML agent and creating a new one, and then jumping to the
        'restart' state. That will trigger the startstream() handler.
        
        (The second time around, we'll see that we're already authenticated,
        so we'll bypass authing and proceed to the 'authed' state.)
        """
        
        self.log.info('sasl authenticated successfully -- restarting stream')

        self.authenticated = True
        # Discard security information, on general principles
        self.password = None
        self.authresponsecode = None

        # We must now redo our initial setup code. I haven't bothered
        # to abstract it into a separate function, because there's not
        # that much of it.

        self.deldispatcher(self.handle_stanza_saslchallenge)
        self.deldispatcher(self.handle_stanza_saslfailure)
        self.deldispatcher(self.handle_stanza_saslsuccess)
        
        # Delete the old 'end' handler from parser, so that we can stop
        # parser without killing ourself
        
        self.parserendaction.remove()
        self.parser.stop()

        self.parser = None
        self.parserendaction = None
        self.xmldoc = None
        self.streamfeatures = None
        self.sendingstreamdoc = False

        # Create a new XML parser. (Easier than resetting the old one.)

        self.parser = xmlagent.XML(self.conn)
        ac = self.parser.addhandler('end', self.stop)
        self.parserendaction = ac
        ac = self.parser.addhandler('body', self.checkstreamattrs)
        self.addcleanupaction(ac)
        ac = self.parser.addhandler('stanza', self.dispatch)
        self.addcleanupaction(ac)

        self.parser.start()

        # Now we jump to 'restart'. The handler attached to this is
        # self.startstream, so we'll send off a new stream header and
        # everything will proceed after that.
        
        self.jump('restart')

    def beginregister(self):
        """beginregister() -- 'registering' state handler. Do not call.

        Begin the in-band registration process (JEP-0077). Send
        a <query> stanza; set up a dispatcher to wait for a reply.

        Note that if there is an error in registration, we do not stop
        the agent. We just jump back to the 'connected' state, to try
        to authenticate anyhow.
        """
        
        msg = interface.Node('iq', attrs={'type':'get'})
        msg.setchild('query', namespace=interface.NS_REGISTER)
        id = self.send(msg, addfrom=False)
        self.adddispatcher(self.handle_stanza_register,
            name='iq', type=('result','error'), id=id)

    def handle_stanza_register(self, msg):
        """handle_stanza_register() -- registration process dispatcher.
        Do not call.

        Accept a message saying what fields are needed to register.
        If it indicates an error, or that we're already registered,
        jump to 'connected'. Otherwise, send an appropriate response.
        """
        
        if (msg.getattr('type') != 'result'):
            # Error response. Registration has failed.

            ex = interface.parseerrorstanza(msg)
            self.log.error('registration failed: <%s> %s',
                ex.errorname, ex.text)
            self.perform('error', ex, self)
            self.autoregister = False
            self.jump('connected')
            raise interface.StanzaHandled

        nod = msg.getchild('query')
        if (not nod or nod.getnamespace() != interface.NS_REGISTER):
            # Not addressed to us
            return

        if (nod.getchild('registered')):
            # Already registered at this JID
            self.log.warning('unable to register; this jid already registered')
            self.autoregister = False
            self.jump('connected')
            raise interface.StanzaHandled

        # Send the registration message.

        newmsg = interface.Node('iq', attrs={'type':'set'})
        newnod = newmsg.setchild('query', namespace=interface.NS_REGISTER)
        
        newnod.setchild('username').setdata(self.jid.getnode())
        newnod.setchild('password').setdata(self.password)
        if (nod.getchild('email')):
            newnod.setchild('email').setdata(self.registeremail)
        
        id = self.send(newmsg, addfrom=False)
        self.adddispatcher(self.handle_stanza_registering,
            name='iq', type=('result','error'), id=id)
        
        raise interface.StanzaHandled
        
    def handle_stanza_registering(self, msg):
        """handle_stanza_registering() -- registration process dispatcher.
        Do not call.

        Accept a message saying whether registration succeeded or failed.
        Either way, jump to 'connected'.
        """
        
        if (msg.getattr('type') != 'result'):
            # Error response. Registration has failed.

            ex = interface.parseerrorstanza(msg)
            self.log.error('registration failed: <%s> %s',
                ex.errorname, ex.text)
            self.perform('error', ex, self)
            self.autoregister = False
            self.jump('connected')
            raise interface.StanzaHandled

        self.log.info('registered successfully -- continuing with auth')
        self.autoregister = False
        self.jump('connected')
        raise interface.StanzaHandled

    def announceinitialresource(self):
        """announceinitialresource() -- 'authed' state handler. Do not call.

        If we used non-SASL authentication, we automatically bound a resource.
        In that case, we want to perform a 'bound' event after we reach
        state 'authed'.
        """
        
        if (self.initialresourcebound):
            self.perform('bound', self.jid.getresource())

    def bindresource(self, resource=None):
        """bindresource(resource=None) -> None

        Bind a JID resource to the authenticated stream. If no *resource*
        is provided, use the resource which was provided in the *jid* of
        the stream constructor. (If that JID lacked a resource, ask for
        'zymb'.)

        If this succeeds, the agent performs a 'bound' event containing the
        resource string. Which may not be the one you asked for. If it is
        different, self.jid is updated with the new resource.

        (Warning: you can call bindresource() more than once. However, the
        high-level Jabber features in zymb are not multiple-resource aware.
        They assume that there is just one resource per stream.)
        """
        
        if (not resource):
            resource = self.jid.getresource()
        
        msg = interface.Node('iq', attrs={'type':'set'})
        nod = msg.setchild('bind', namespace=interface.NS_BIND)
        nod.setchild('resource').setdata(resource)
        
        id = self.send(msg, addfrom=False)
        self.adddispatcher(self.handle_stanza_binding,
            name='iq', type=('result','error'), id=id)

    def handle_stanza_binding(self, msg):
        """handle_stanza_binding() -- binding process dispatcher. Do not
        call.

        Accept a message saying whether binding succeeded. If it did,
        perform a 'bound' event.
        """
        
        if (msg.getattr('type') != 'result'):
            # Error response. Binding has failed.

            ex = interface.parseerrorstanza(msg)
            self.log.error('binding failed: <%s> %s',
                ex.errorname, ex.text)
            self.perform('error', ex, self)
            raise interface.StanzaHandled
            
        nod = msg.getchild('bind')
        if (not nod or nod.getnamespace() != interface.NS_BIND):
            # Not addressed to us
            return

        jidnod = nod.getchild('jid')
        jidstr = jidnod.getdata()
        jid = interface.JID(jidstr)
        res = jid.getresource()
        if (res != self.jid.getresource()):
            # This is a different resource than we asked for!
            self.jid.setresource(res)
            self.log.info('new resource; JID is now <%s>', unicode(self.jid))

        self.perform('bound', res)
        raise interface.StanzaHandled

class JabberAuthResource(JabberAuth):
    """JabberAuthResource: A high-level Jabber agent.

    Knows how to open a Jabber connection, authenticate, and then bind
    one resource.
    
    This is the class you would use in a typical Jabber client application --
    one which communicates under a single full JID. When you start the
    agent, it will connect, authenticate, and bind the resource. Then
    your application can begin doing whatever it does.
    
    JabberAuthResource(jid, password, port=5222, secure=SECURE_DEFAULT,
        register=False, host=None) -- constructor.
        
    The JID may be a string or an interface.JID object. If the JID lacks
    a resource, 'JID/zymb' will be assumed. The *password* is used to
    authenticate. If *register* is true, the agent tries to register
    a new account (with the given JID and password) before authenticating.
    (Not all Jabber servers permit autoregistration in this way.)

    The *port* specifies the TCP port on the host. If *host* is None, it
    is inferred from *jid*. The *secure* value specifies a level of stream
    security:

    SECURE_DEFAULT: Use SECURE_TLS if port 5222, SECURE_SSL if port 5223.
    SECURE_NONE: Do not use stream security.
    SECURE_SSL: Use SSL security (deprecated).
    SECURE_TLS: Use TLS security, if the server supports it.

    For most Jabber servers, you can just specify the port, and the
    SECURE_DEFAULT setting will do the right thing.

    Agent states and events:

    (See JabberAuth for states used while connecting and authenticating.
    In addition to those, JabberAuthResource uses the following:)

    state 'start': Initial state. Begin connection process.
    state 'authstartsession': The stream has authenticated and bound
        its resource, but has not yet established a session.
    state 'authresource': The stream has authenticated, bound its
        resource, and (if necessary) established a session. You can start
        doing work.
    event 'error' (exc, agent): An error was detected -- either in the
        connection process, during authentication, or passed up from the
        TCP or XML layers. (The *agent* indicates where the error originated.)
    state 'end': The connection is closed.

    Internal methods:

    bindinitialresource() -- 'authed' state handler.
    detectinitialbinding() -- 'bound' event handler.
    startinitialsession() -- 'authstartsession' event handler.
    """

    def __init__(self, jid, password, port=5222, secure=SECURE_DEFAULT, register=False, host=None):
        JabberAuth.__init__(self, jid, password, port, secure, register, host)
        self.addhandler('authed', self.bindinitialresource)
        self.addhandler('bound', self.detectinitialbinding)
        self.addhandler('authstartsession', self.startinitialsession)

    def bindinitialresource(self):
        """ bindinitialresource() -- 'authed' state handler. Do not call.

        This handler ensures that we bind our first resource as soon as
        authentication is done. If non-SASL authentication was used, then
        the first resource was bound automatically, so this does nothing.
        """
        
        if (not self.initialresourcebound):
            self.bindresource()

    def detectinitialbinding(self, resource):
        """detectinitialbinding() -- 'bound' event handler. Do not call.

        As soon as the first resource is bound, this moves out of the
        'authed' state. If the server supports (and requires) sessions,
        we go to state 'authstartsession'. If not, jump straight to
        'authresource'.
        """
        
        if (self.state == 'authed'):
            foundsession = False
            for nod in self.streamfeatures:
                if (nod.getname() == 'session'
                    and nod.getnamespace() == interface.NS_SESSION):
                    foundsession = True
                    
            if (not foundsession):
                self.jump('authresource')
            else:
                self.jump('authstartsession')

    def startinitialsession(self):
        """startinitialsession() -- 'authstartsession' event handler.
        Do not call.

        This establishes an XMPP session, as per RFC-3921.
        """
        
        hostname = self.jid.getdomain()
        msg = interface.Node('iq', attrs={'type':'set', 'to':hostname})
        msg.setchild('session', namespace=interface.NS_SESSION)
        
        id = self.send(msg, addfrom=False)
        self.adddispatcher(self.handle_stanza_session,
            name='iq', type=('result','error'), id=id)
        
    def handle_stanza_session(self, msg):
        """handle_stanza_session() -- session process dispatcher. Do not
        call.

        Accept a message saying whether session began. If it did,
        jump to 'authresource'.
        """
        
        if (msg.getattr('type') != 'result'):
            # Error response. Session has failed.

            ex = interface.parseerrorstanza(msg)
            self.log.error('session failed: <%s> %s',
                ex.errorname, ex.text)
            self.perform('error', ex, self)
            self.stop()
            raise interface.StanzaHandled
            
        self.jump('authresource')
        raise interface.StanzaHandled

class SASLError(Exception):
    """SASLError: A generic exception used whenever something goes
    wrong during SASL authentication.
    """
    pass

def encoderawdigest(st):
    """encoderawdigest(str) -> str

    A helper function used during SASL authentication. Returns the MD5
    digest of a string, in the form of a 16-byte string. The bytes can
    be any value from \x00 to \xff.
    """
    return md5.new(st).digest()

def encodehexdigest(st):
    """encodehexdigest(str) -> str

    A helper function used during SASL authentication. Returns the MD5
    digest of a string, in the form of a 32-character string. The string
    represents 16 bytes, in lower-case hexadecimal.
    """
    return md5.new(st).hexdigest().lower()

def encodehexkd(key, st):
    """encodehexkd(str1, str2) -> str

    A helper function used during SASL authentication. Equivalent to
    encodehexdigest(st), where st = (str1 + ':' + str2).
    """
    return encodehexdigest(key + ':' + st)

def qdstrencode(st):
    """qdstrencode(str) -> str

    A helper function used during SASL authentication. Quotes strings
    by backslash-escaping backslashes and double-quote characters.
    """
    return st.replace('\\', '\\\\').replace('"', '\\"')
    