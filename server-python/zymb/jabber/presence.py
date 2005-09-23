import logging

from zymb import sched
import client, service, interface

class PresenceService(service.Service):
    """PresenceService: A high-level Jabber facility for announcing presence.

    (Service label: 'presenceservice'.)

    PresenceService is very simplistic at the moment. All it can do is
    announce that you are available or unavailable. (It takes care of
    announcing you unavailable when you close your Jabber connection.)

    PresenceService() -- constructor.

    Public methods:

    set(avail=True) -- set your availability.
    addhook(hook) -- add a hook to modify outgoing presence stanzas.
    removehook(hook) -- remove a hook to modify outgoing presence stanzas.
    """

    label = 'presenceservice'
    logprefix = 'zymb.jabber.presence'

    def __init__(self):
        service.Service.__init__(self)
        self.available = False
        self.hooklist = []

    def attach(self, agent):
        """attach() -- internal method to attach this Service to a
        JabberStream. Do not call this directly. Instead, call
        jstream.addservice(service).

        This calls the inherited class method, and then sets up the
        'end' handler which marks you unavailable at shutdown time.
        """
        
        service.Service.attach(self, agent)
        self.agent.addhandler('end', self.set, False)

    def set(self, avail=True):
        """set(avail=True) -> None

        Initially, you are unavailable. If you call set(), you are
        available. If you call set(False), you're unavailable. I
        told you it was simplistic.

        You should not call this method until the Jabber connection has
        reached the 'authresource' state.
        """

        avail = bool(avail)
        if (self.available == avail):
            return

        if (avail):
            self.log.debug('announcing available')
            msg = interface.Node('presence')
            try:
                for hook in self.hooklist:
                    res = hook(msg)
                    if (res != None):
                        msg = res
                self.agent.send(msg, addid=False, addfrom=False)
            except interface.StanzaHandled:
                pass
        else:
            self.log.debug('announcing unavailable')
            msg = interface.Node('presence', attrs={'type':'unavailable'})
            try:
                for hook in self.hooklist:
                    res = hook(msg)
                    if (res != None):
                        msg = res
                self.agent.send(msg, addid=False)
            except interface.StanzaHandled:
                pass
        
        self.available = avail

    def addhook(self, hook):
        """addhook(hook) -> None
        
        Add a hook to modify outgoing presence stanzas. The hook is called
        for every presence stanza which is sent out; it has the opportunity
        to modify the stanza or send it itself.
    
        The hook is a function which accepts one argument, a Node. The
        function may either:
    
            - do nothing and return None;
            - modify the Node and return None;
            - create a new Node and return it;
            - raise interface.StanzaHandled to indicate that it has taken
                responsibility for sending presence.
    
        Multiple hooks are executed in the order that they were added.
        """
    
        self.hooklist.append(hook)

    def removehook(self, hook):
        """removehook(hook) -> None

        Remove a hook function which was added with addhook(). If no hook
        matches, this does nothing.
        """
        
        if (hook in self.hooklist):
            self.hooklist.remove(hook)
