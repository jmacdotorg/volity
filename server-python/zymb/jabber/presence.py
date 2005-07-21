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
    """

    label = 'presenceservice'
    logprefix = 'zymb.jabber.presence'

    def __init__(self):
        service.Service.__init__(self)
        self.available = False

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
            self.agent.send(msg, addid=False, addfrom=False)
        else:
            self.log.debug('announcing unavailable')
            msg = interface.Node('presence', attrs={'type':'unavailable'})
            self.agent.send(msg, addid=False)
        
        self.available = avail
    