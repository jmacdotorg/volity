import service
import interface

# Namespace for the <iq> packets used for keepalive queries. This can be
# anything, because keepalive queries are sent only to oneself.
NS_ZYMB_KEEPALIVE = 'zymb:keepalive'

# If the keepalive period is 60 seconds, we wake up every 15 seconds
# to check for activity. 60/4 is 15.
DIVISION = 4

class KeepAliveService(service.Service):
    """KeepAliveService: A high-level Jabber facility for tickling the
    Jabber socket every so often.

    (Service label: 'keepaliveservice'.)

    On some machines, because of the peculiarities of their network
    configurations or firewall, a TCP connection that stands for a long
    time without activity will choke and die. That's a sad thing.

    Most machines don't have this problem. Furthermore, some Jabber
    servers -- notably jabber.org -- push a whitespace character down
    the Jabber socket once per minute. That prevents the problem even if
    your machine has it.

    However, if your machine does, and your Jabber server doesn't,
    then your Jabber client has to do something about it. This service
    does something. Once per 90 seconds, it sends a simple Jabber
    query to itself. The contents of the query are ignored, and there
    is no reply; it's just socket activity.

    The service is clever: if there is actual Jabber traffic coming in,
    it doesn't bother sending keepalive queries. More precisely, it
    guarantees that no more than 90 seconds will pass without *some*
    socket activity.

    KeepAliveService(interval=90, panic=False) -- constructor.

    The default *interval* of 90 seconds was chosen because it works
    (on the machine I use which requires keepalive activity). Also,
    it's longer than 60 seconds. So if you use this service on a
    connection to jabber.org, this service will always see the once-
    per-minute whitespace from jabber.org, and will never need to wake
    up.

    If *panic* is True, the service will actually kill the Jabber agent
    if interval*2 seconds go by without an incoming Jabber message.
    (This would imply that the keepalive query was sent, but never
    delivered.)

    In addition to creating and attaching the service, you must start it
    up. Any time after the Jabber agent reaches the 'authresource' state
    (i.e., when it's connected and ready to do Jabber work) you must
    call the service's start() method. For example, you might use code
    like this:

        serv = KeepAliveService()
        jstream.addservice(serv)
        jstream.addhandler('authresource', serv.start)

    Public methods:

    start() -- begin watching the connection and doing work.
    stop() -- cease watching the connection and doing work.
    getinterval() -- get the interval associated with the service.
    setinterval() -- change the interval associated with the service.

    Internal methods:

    attach() -- attach this Service to a JabberStream.
    activity() -- 'handle' event handler.
    check() -- timer handler.
    handleping() -- keepalive stanza handler.
    """

    label = 'keepaliveservice'
    logprefix = 'zymb.jabber.keepalive'
    
    def __init__(self, interval=90, panic=False):
        if (interval < 10):
            raise ValueError('KeepAliveService interval must be 10 or higher')
        service.Service.__init__(self)
        self.interval = interval
        self.panic = panic

        self.counter = 0
        self.heartbeat = None
        self.action = None

    def attach(self, agent):
        """attach() -- internal method to attach this Service to a
        JabberStream. Do not call this directly. Instead, call
        jstream.addservice(service).

        This calls the inherited class method, and then sets up the
        stanza dispatcher which catches incoming keepalive queries,
        and the handler that watches for socket activity.
        """
        
        service.Service.attach(self, agent)
        self.agent.adddispatcher(self.handleping, name='iq', type='set')
        self.agent.conn.addhandler('handle', self.activity)

    def start(self):
        """start() -> None

        Begin watching the connection and doing work. If the service is
        already started, this does nothing.
        """
        
        if (not self.action):
            self.counter = 0
            self.heartbeat = self.interval / DIVISION
            self.action = self.agent.addtimer(self.check, delay=self.heartbeat)
            self.log.debug('starting up, interval %d, heartbeat %d',
                self.interval, self.heartbeat)

    def stop(self):
        """stop() -> None

        Cease watching the connection and doing work. If the service is
        already stopped, this does nothing.
        """
        
        self.counter = 0
        if (self.action):
            self.action.remove()
            self.action = None
            self.log.debug('shutting down')

    def getinterval(self):
        """getinterval() -> int

        Get the interval associated with the service.
        """
        
        return self.interval

    def setinterval(self, interval=90):
        """setinterval(interval=90) -> None

        Change the interval associated with the service.
        """
        
        if (interval < 10):
            raise ValueError('KeepAliveService interval must be 10 or higher')
        self.interval = interval
        if (self.action):
            self.stop()
            self.start()

    def activity(self, data):
        """activity(data) -- internal 'handle' event handler. Do not call.

        This handler is attached to the socket agent which underlies the
        Jabber stream. When it sees any activity (even a partial Jabber
        message, or whitespace between Jabber stanzas), it resets the
        keepalive timer.
        """
        
        self.counter = 0

    def check(self):
        """check() -- internal timer handler. Do not call.

        This method is called every interval/4 seconds. (It is not put
        into a periodic timer; it invokes a new timer for itself every
        time it runs.) If it is called 4 times in a row with no network
        activity, it fires off an <iq> query, from the Jabber agent to
        itself. If it gets up to 8 times with no activity, and the panic
        option is True, it shuts down the agent.
        """
        
        if (not self.action):
            return
            
        self.action = self.agent.addtimer(self.check, delay=self.heartbeat)
            
        self.counter += 1
        
        if (self.counter >= DIVISION):
            msg = interface.Node('iq',
                attrs={'type':'set', 'to':self.agent.jid})
            nod = msg.setchild('query', namespace=NS_ZYMB_KEEPALIVE)
            self.agent.send(msg)

        if (self.panic and self.counter >= DIVISION*2):
            self.log.error('no activity in %d seconds -- shutting down',
                self.heartbeat*DIVISION*2)
            self.agent.stop()

    def handleping(self, msg):
        """handleping() -- keepalive stanza handler. Do not call.

        This dispatcher accepts the <iq> queries sent by the service.
        It does nothing with them, since the only reason they exist is
        to tickle the network socket.
        """
        
        nod = msg.getchild('query')
        if (not nod or nod.getnamespace() != NS_ZYMB_KEEPALIVE):
            # Not addressed to us
            return

        raise interface.StanzaHandled
        