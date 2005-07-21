import logging
import client

class Service:
    """Service: A class that attaches to a JabberStream, providing a
    high-level Jabber feature.

    A Service is an abstraction which lets you do Jabber work without
    messing with XML and namespaces. You can create a service object
    and attach it to a JabberStream. The service provides higher-level
    communication methods; it automatically takes care of building Node
    trees, adding dispatchers, etc.

    To make use of a Service in a JabberStream, create an instance of the
    service class, and then call jstream.addservice(serviceinstance).

    Each class of Service has a label (a string). Only one instance of
    a Service class can be attached to a JabberStream. You can retrieve
    it by calling jstream.getservice(label).

    Most Services fall into one of two categories:

    * A "service", which passively waits for Jabber requests from other
    Jabber entities, and responds to them. You configure the service
    with appropriate reply behavior when you create it.
    
    * A "clience", which you invoke (via a method) to make a Jabber request
    to another entity. It awaits the reply, decodes it, and passes it
    back to you. (Since zymb is asynchronous, a clience method does not
    return the request's reply. You must provide a callback, which the
    clience will invoke when the reply arrives.)

    For example, the *disco* module contains DiscoService (which responds
    to disco queries from others), and DiscoClience (which allows you to
    do disco queries on others). A given Jabber application might have
    either, both, or neither.

    Internal methods:

    attach() -- attach this Service to a JabberStream.
    """
    
    label = None
    logprefix = 'zymb.jabber.service'
    
    def __init__(self):
        if (not self.label):
            raise NotImplementedError, 'Service is a virtual base class; you cannot instantiate it'
        self.agent = None

    def attach(self, agent):
        """attach() -- internal method to attach this Service to a
        JabberStream. Do not call this directly. Instead, call
        jstream.addservice(service).
        """
        
        if (not isinstance(agent, client.JabberStream)):
            raise Exception, 'Service must be attached to a JabberStream'
        if (self.agent):
            raise Exception, 'Service is already attached'
        if (agent.services.has_key(self.label)):
            raise Exception, ('agent already has a \'%s\' service attached'
                % self.label)
        
        self.agent = agent
        agent.services[self.label] = self
        self.log = logging.getLogger(self.logprefix)
