from zymb import sched
import client, service, interface, discodata

from discodata import DiscoInfo, DiscoItems

class DiscoService(service.Service):
    """DiscoService: A high-level Jabber facility for responding to service
    discovery queries from other entities.

    (Service label: 'discoservice'.)

    The DiscoService is a generic framework for disco queries (both info
    and items). It receives the query stanza and then looks up the results
    in the information you have supplied.

    You can set up info and items replies for any query node
    (including the default "no node specified" query). You do this by
    calling the service's addinfo() and additems() methods. You can
    provide either an DiscoInfo / DiscoItems instance (see the
    discodata module for these classes), or a callable which does one
    of the following:

    * Return a DiscoInfo / DiscoItems instance (as appropriate).
    * Raise an interface.StanzaError. This will cause the query to be replied
        to with the given stanza-level error.
    * Raise some other kind of Exception. This will become a stanza-level
        'internal-server-error' error.
    * Raise a sched.Deferred(op). This is a promise that you will eventually
        invoke the Deferred object. That will result in *op* being called;
        and the *op* should then do one of the things on this list.

    DiscoService() -- constructor.

    Public methods:

    addinfo() -- add an info query response.
    getinfo() -- get an info query response.
    additems() -- add an items query response.
    getitems() -- get an items query response.

    Internal methods:

    attach() -- attach this Service to a JabberStream.
    handleget() -- disco stanza handler.
    handlegetinfo() -- disco stanza handler.
    handlegetitems() -- disco stanza handler.
    deferredinfowrapper() -- callback for deferred handlers.
    deferreditemswrapper() -- callback for deferred handlers.
    """
    
    label = 'discoservice'
    logprefix = 'zymb.jabber.disco'
    
    def __init__(self):
        service.Service.__init__(self)

        self.info = {}
        self.items = {}

    def attach(self, agent):
        """attach() -- internal method to attach this Service to a
        JabberStream. Do not call this directly. Instead, call
        jstream.addservice(service).

        This calls the inherited class method, and then sets up the
        stanza dispatcher which catches incoming disco queries.
        """
        
        service.Service.attach(self, agent)
        self.agent.adddispatcher(self.handleget, name='iq', type='get')

    def addinfo(self, node=None, info=None):
        """addinfo(node=None, info=None) -> DiscoInfo or callable

        Add a response for disco-info queries to the given *node*. If *node*
        is None, the response applies to queries that have no node attribute.

        The *info* should be either a DiscoInfo object, a function which
        returns a DiscoInfo object, or None. (If None, a new DiscoInfo
        is generated and initialized with the
        'http://jabber.org/protocol/disco#info' and
        'http://jabber.org/protocol/disco#items' features.)

        The return value is the *info* you passed in, or the newly-generated
        DiscoInfo if you passed None.
        """
        
        if (not info):
            info = DiscoInfo()
            info.addfeature(interface.NS_DISCO_INFO)
            info.addfeature(interface.NS_DISCO_ITEMS)

        self.info[node] = info
        return info

    def getinfo(self, node=None):
        """getinfo(node=None) -> DiscoInfo or callable

        Return the response set for disco-info queries to the given *node*.
        If *node* is None, the response applies to queries that have no node
        attribute.
        """
        
        return self.info.get(node)

    def additems(self, node=None, items=None):
        """additems(node=None, items=None) -> DiscoItems or callable

        Add a response for disco-items queries to the given *node*. If *node*
        is None, the response applies to queries that have no node attribute.

        The *items* should be either a DiscoItems object, a function which
        returns a DiscoItems object, or None. (If None, a new DiscoItems
        is generated.)

        The return value is the *items* you passed in, or the newly-generated
        DiscoItems if you passed None.
        """
        
        if (not items):
            items = DiscoItems()
            
        self.items[node] = items
        return items

    def getitems(self, node=None):
        """getitems(node=None) -> DiscoItems or callable

        Return the response set for disco-items queries to the given *node*.
        If *node* is None, the response applies to queries that have no node
        attribute.
        """
        
        return self.items.get(node)

    def handleget(self, msg):
        """handleget() -- disco stanza handler. Do not call.

        This checks to see if the query stanza is in a disco query namespace.
        If so, it calls handlegetinfo or handlegetitems.
        """
        
        qnod = msg.getchild('query')
        if (not qnod):
            return

        ns = qnod.getnamespace()
        
        if (ns == interface.NS_DISCO_INFO):
            fromstr = msg.getattr('from')
            id = msg.getattr('id')
            node = qnod.getattr('node')
            self.handlegetinfo(fromstr, id, node)
            return
            
        if (ns == interface.NS_DISCO_ITEMS):
            fromstr = msg.getattr('from')
            id = msg.getattr('id')
            node = qnod.getattr('node')
            self.handlegetitems(fromstr, id, node)
            return

        # Not a disco query after all
        return

    def handlegetinfo(self, jidstr, id, node=None):
        """handlegetinfo() -- disco stanza handler. Do not call.
        """
        
        self.log.debug('Disco#info from <%s>, id %s, node %s',
            jidstr, id, node)

        if (not self.info.has_key(node)):
            if (not node):
                raise interface.StanzaItemNotFound('info does not exist')
            else:
                raise interface.StanzaItemNotFound('info for node "%s" does not exist' % node)

        info = self.info[node]
        if (not isinstance(info, DiscoInfo)):
            try:
                info = info()
            except sched.Deferred, ex:
                ex.addcontext(self.deferredinfowrapper, jidstr, id)
                raise
            if (not isinstance(info, DiscoInfo)):
                raise TypeError('info must be or return a DiscoInfo')

        msg = interface.Node('iq',
            attrs={ 'to':jidstr, 'type':'result', 'id':id })
        qnod = discodata.makediscoinfo(info)
        msg.addchild(qnod)
        
        self.agent.send(msg)
        raise interface.StanzaHandled

    def deferredinfowrapper(self, tup, jidstr, id):
        """deferredinfowrapper(tup, jidstr, id) -- callback for deferred
        handlers.

        This is used by handlegetinfo(), for the case where a disco handler
        wants to undertake a deferral operation. You should not call it,
        or even try to understand it.
        """

        info = sched.Deferred.extract(tup)
        if (not isinstance(info, DiscoInfo)):
            raise TypeError('info must be or return a DiscoInfo')

        msg = interface.Node('iq',
            attrs={ 'to':jidstr, 'type':'result', 'id':id })
        qnod = discodata.makediscoinfo(info)
        msg.addchild(qnod)

        self.agent.send(msg)
        
    def handlegetitems(self, jidstr, id, node=None):
        """handlegetitems() -- disco stanza handler. Do not call.
        """
        
        self.log.debug('Disco#items from <%s>, id %s, node %s',
            jidstr, id, node)

        if (not self.items.has_key(node)):
            if (not node):
                raise interface.StanzaItemNotFound('items do not exist')
            else:
                raise interface.StanzaItemNotFound('items for node "%s" do not exist' % node)

        items = self.items[node]
        if (not isinstance(items, DiscoItems)):
            try:
                items = items()
            except sched.Deferred, ex:
                ex.addcontext(self.deferreditemswrapper, jidstr, id)
                raise
            if (not isinstance(items, DiscoItems)):
                raise TypeError('items must be or return a DiscoItems')
        
        msg = interface.Node('iq',
            attrs={ 'to':jidstr, 'type':'result', 'id':id })
        qnod = discodata.makediscoitems(items)
        msg.addchild(qnod)

        self.agent.send(msg)
        raise interface.StanzaHandled

    def deferreditemswrapper(self, tup, jidstr, id):
        """deferreditemswrapper(tup, jidstr, id) -- callback for deferred
        handlers.

        This is used by handlegetitems(), for the case where a disco handler
        wants to undertake a deferral operation. You should not call it,
        or even try to understand it.
        """

        items = sched.Deferred.extract(tup)
        if (not isinstance(items, DiscoItems)):
            raise TypeError('items must be or return a DiscoItems')

        msg = interface.Node('iq',
            attrs={ 'to':jidstr, 'type':'result', 'id':id })
        qnod = discodata.makediscoitems(items)
        msg.addchild(qnod)

        self.agent.send(msg)
        
    

class DiscoClience(service.Service):
    """DiscoClience: A high-level Jabber facility for making service
    discovery queries to other entities.

    (Service label: 'discoclience'.)

    DiscoClience() -- constructor.

    Public methods:

    queryinfo() -- send a disco info query, asynchronously.
    queryitems() -- send a disco items query, asynchronously.

    Internal methods:

    queryinfoop() -- utility function for queryinfo().
    handlequeryinfo() -- deferred operation handler for queryinfo().
    queryitemsop() -- utility function for queryitems().
    handlequeryitems() -- deferred operation handler for queryitems().
    """
    
    label = 'discoclience'
    logprefix = 'zymb.jabber.disco'
    
    def queryinfo(self, op, jid, node=None, timeout=None):
        """queryinfo(op, jid, node=None, timeout=None) -> None

        Send a disco info query, asynchronously.

        The *jid* is the target of the query; the optional *node* is a
        node to query about. The optional *timeout* is a time limitation
        in seconds. (If the query takes longer than this to return, you
        will get a TimeoutException.)

        Since this is asynchronous, it does not return the query results
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

        If the query results come in cleanly, the sched.Deferred.extract(tup)
        call will raise no exception -- it will simply return the results,
        in the form of a DiscoInfo object. (See the discodata module for
        this class.)

        If something went wrong, an exception will be raised, which you may
        wish to catch:

            sched.TimeoutException: You specified a timeout=val keyword when
                you made the call, and more than *val* seconds have passed
                without a response.
            interface.StanzaError: A stanza-level error was received, instead
                of a response.
        """
        
        try:
            self.queryinfoop(jid, node=node, timeout=timeout)
        except sched.Deferred, ex:
            ex.addcontext(op)
    
    def queryinfoop(self, jid, node=None, timeout=None):
        """queryinfoop() -- utility function for queryinfo(). Do not call.
        """
        
        msg = interface.Node('iq',
            attrs={ 'to':unicode(jid), 'type':'get' })
        qnod = msg.setchild('query', namespace=interface.NS_DISCO_INFO)
        if (node):
            qnod.setattr('node', unicode(node))

        id = self.agent.send(msg)
        
        defer = sched.Deferred(self.handlequeryinfo)

        dsp = self.agent.adddispatcher(defer.queue, self.agent, 'result',
            name='iq', type=('result','error'), id=id, accept=True)
        if (timeout != None):
            ac = self.agent.addtimer(defer, 'timeout', delay=timeout)
        defer.addaction(ac)

        raise defer

    def handlequeryinfo(self, res, msg=None):
        """handlequeryinfo() -- deferred operation handler for queryinfo().
        Do not call.
        """
        
        if (res == 'timeout'):
            raise sched.TimeoutException('disco query timed out')
        assert msg
        if (msg.getattr('type') != 'result'):
            # Error reply
            raise interface.parseerrorstanza(msg)
        
        qnod = msg.getchild('query')
        if (not qnod):
            raise interface.StanzaBadRequest(
                'disco info reply lacks <query>')
        if (qnod.getnamespace() != interface.NS_DISCO_INFO):
            raise interface.StanzaBadRequest(
                'disco info reply in wrong namespace')
            
        info = discodata.parsediscoinfo(qnod)
        return info

    def queryitems(self, op, jid, node=None, timeout=None):
        """queryitems(op, jid, node=None, timeout=None) -> None

        Send a disco items query, asynchronously.

        The *jid* is the target of the query; the optional *node* is a
        node to query about. The optional *timeout* is a time limitation
        in seconds. (If the query takes longer than this to return, you
        will get a TimeoutException.)

        Since this is asynchronous, it does not return the query results
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

        If the query results come in cleanly, the sched.Deferred.extract(tup)
        call will raise no exception -- it will simply return the results,
        in the form of a DiscoItems object. (See the discodata module for
        this class.)

        If something went wrong, an exception will be raised, which you may
        wish to catch:

            sched.TimeoutException: You specified a timeout=val keyword when
                you made the call, and more than *val* seconds have passed
                without a response.
            interface.StanzaError: A stanza-level error was received, instead
                of a response.
        """
        
        try:
            self.queryitemsop(jid, node=node, timeout=timeout)
        except sched.Deferred, ex:
            ex.addcontext(op)
    
    def queryitemsop(self, jid, node=None, timeout=None):
        """queryitemsop() -- utility function for queryitems(). Do not call.
        """
        
        msg = interface.Node('iq',
            attrs={ 'to':unicode(jid), 'type':'get' })
        qnod = msg.setchild('query', namespace=interface.NS_DISCO_ITEMS)
        if (node):
            qnod.setattr('node', unicode(node))

        id = self.agent.send(msg)
        
        defer = sched.Deferred(self.handlequeryitems)

        dsp = self.agent.adddispatcher(defer.queue, self.agent, 'result',
            name='iq', type=('result','error'), id=id, accept=True)
        if (timeout != None):
            ac = self.agent.addtimer(defer, 'timeout', delay=timeout)
        defer.addaction(ac)

        raise defer

    def handlequeryitems(self, res, msg=None):
        """handlequeryitems() -- deferred operation handler for queryitems().
        Do not call.
        """
        
        if (res == 'timeout'):
            raise sched.TimeoutException('disco query timed out')
        assert msg
        if (msg.getattr('type') != 'result'):
            # Error reply
            raise interface.parseerrorstanza(msg)
        
        qnod = msg.getchild('query')
        if (not qnod):
            raise interface.StanzaBadRequest(
                'disco items reply lacks <query>')
        if (qnod.getnamespace() != interface.NS_DISCO_ITEMS):
            raise interface.StanzaBadRequest(
                'disco items reply in wrong namespace')
            
        items = discodata.parsediscoitems(qnod)
        return items

