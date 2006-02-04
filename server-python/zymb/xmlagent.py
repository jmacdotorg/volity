import sys
import logging
import sched
import xmldata
import xml.parsers.expat

class XML(sched.Agent):
    """XML: An Agent which takes data from another Agent and parses it
    into XML stanzas. This only deals with input. It has no code to
    generate or send XML.

    The XML document is assumed to have the form
        <tag><stanza /><stanza />...</tag>
    In other words, children of the top-level tag are considered to be
    the basic units, and an event is triggered each time one completes.
    Character data between stanzas is ignored.

    XML(agent, encoding='UTF-8') -- constructor.

    The *agent* must be of a type that generates 'handle' string events.
    (For example, tcp.TCP.) If the agent stops, or if it generates
    an XML closing tag, the XML agent will itself stop.

    If the *agent* generates an 'error' event, the XML agent will repeat it.

    By default, *encoding* is UTF-8, and so the data that arrives must be
    in that encoding. You can specify other encodings. If *encoding* is
    None, the data should be unicode objects (or convertible to
    unicode). In all cases, the stanzas that are generated will be
    unencoded -- they will contain Unicode elements where appropriate.

    Agent states and events:

    state 'start': Begin parsing XML.
    state 'body' (tag, namespace, attrs): The XML opening tag has been
        received. The handler arguments are the opening tag name,
        the xmlns attribute (if present), and a dict containing all the
        tag attributes (including xmlns).
    event 'stanza' (node): A stanza has been received. The argument
        is a Node object representing the stanza.
    event 'error' (exc, agent): An error was detected while parsing the XML.
        This is immediately followed by a jump to 'end'.
    state 'end': Stop parsing. (No error is generated if the XML structure
        is incomplete.)
        
    Publicly readable fields:

    docname -- the top-level tag name (once received)
    docattrs -- the top-level tag attributes, as a dict (once received)

    Public methods:

    basicstanza(nod) -- a simple event handler for 'stanza'.
    
    Internal methods:

    setup() -- 'start' state handler.
    handle() -- handler for incoming 'handle' events.
    endparse() -- 'end' state handler.

    """
    
    logprefix = 'zymb.xml'

    def __init__(self, stream, encoding='UTF-8'):
        sched.Agent.__init__(self)
        self.stream = stream
        self.encoding = encoding

        self.docattrs = None
        self.docname = None
        self.xmlparse = None

        self.addhandler('start', self.setup)
        self.addhandler('end', self.endparse)
        
        ac = stream.addhandler('handle', self.handle)
        self.addcleanupaction(ac)
        ac = stream.addhandler('end', self.stop)
        self.addcleanupaction(ac)
        ac = stream.addhandler('error', self.perform, 'error')
        self.addcleanupaction(ac)

    def setup(self):
        """setup() -- internal 'start' state handler. Do not call.

        Create the NodeBuilder parser object and initialize it.
        """

        self.xmlparse = ClientNodeGenerator(self, encoding=self.encoding)

    def handle(self, input):
        """handle(input) -- internal handler for 'handle' events generated
        by self.stream. Do not call.

        Push the data into self.xmlparse. This will trigger 'stanza' events
        for each XML stanza which is completed.
        """

        try:
            self.xmlparse.parse(input)
        except xml.parsers.expat.ExpatError, ex:
            self.log.warning('XML error: %s', ex)
            self.perform('error', ex, self)
            self.stop()

    def basicstanza(self, nod):
        """basicstanza(nod) -> None

        A simple event handler for 'stanza'. This simply writes the
        received data to stdout:
        
            sys.stdout.write(str(nod))

        This handler is not installed by default. You can enable it by
        calling:
        
            tcp.addhandler('stanza', tcp.basicstanza)
        """
        
        sys.stdout.write(str(nod))

    def endparse(self):
        """endparse() -- internal 'end' state handler. Do not call.

        Shut down and delete self.xmlparse.
        """
        
        if (self.xmlparse):
            self.xmlparse.close()
            self.xmlparse = None
            self.log.info('destroyed xml parser')


class ClientNodeGenerator(xmldata.NodeGenerator):
    """ClientNodeGenerator: A NodeGenerator which parses a sequence of
    incoming character data, and dispatches stanzas (children of the
    top-level XML node) as they are completed.

    ClientNodeGenerator(owner, encoding='UTF-8') -- constructor.

    The *owner* is an XML Agent on which to trigger events as stanzas
    arrive.
    
    By default, *encoding* is UTF-8, and so the data that arrives must be
    in that encoding. You can specify other encodings. If *encoding* is
    None, the data should be unicode objects (or convertible to
    unicode). In all cases, the stanzas that are generated will be
    unencoded -- they will contain Unicode elements where appropriate.

    Public method:

    parse() -- accept data, and push it into the expat parser.
    """
    
    def __init__(self, owner, encoding='UTF-8'):
        self.owner = owner
        xmldata.NodeGenerator.__init__(self, encoding=encoding)

    def handle_startelement(self, name, attrs):
        """handle_startelement() -- expat parser callback. Do not call.

        This watches for the initial top-level <tag> of the document,
        and jumps to the 'body' state.
        """
        
        xmldata.NodeGenerator.handle_startelement(self, name, attrs)
        
        if (self.depth == 1):
            name = self.curnode.getname()
            namespace = self.curnode.getnamespace()
            self.owner.log.debug('got beginning of doc <%s xmlns=\'%s\'>',
                name, namespace)
            if (self.owner.state == 'start'):
                attrs = self.curnode.getattrs().copy()
                self.owner.docname = name
                self.owner.docattrs = attrs
                self.owner.jump('body', name, namespace, attrs)
            
    def handle_endelement(self, name):
        """handle_endelement() -- expat parser callback. Do not call.

        This watches for completion of a stanza (child of the top-level
        node), and triggers an event. The stanza is removed from the
        XML tree before dispatching, so that the tree doesn't grow forever.

        This also watches for the completion of the top-level document,
        at which time it shuts down.
        """
        
        xmldata.NodeGenerator.handle_endelement(self, name)

        if (self.depth == 1):
            nod = self.curnode.getchild()
            nod.remove(True)
            if (self.owner.log.isEnabledFor(logging.DEBUG)):
                self.owner.log.debug('received:\n%s', nod.serialize(True))
            self.owner.perform('stanza', nod)
        if (self.depth == 0):
            self.owner.log.debug('got end of doc')
            self.owner.stop()

    def handle_data(self, data):
        """handle_data() -- expat parser callback. Do not call.

        This ignores character data in between stanzas. Some Jabber servers
        send whitespace between stanzas to exercise the connection, and we
        don't want that accumulating in the XML tree.
        """
        
        if (self.depth <= 1):
            return
        xmldata.NodeGenerator.handle_data(self, data)
            
    def parse(self, data):
        """parse(data) -> None

        Accept data, and push it into the expat parser. The *data* may
        be a str in some encoding, or a unicode, depending on the original
        *encoding* specified at construction time.
        """
        
        self.parser.Parse(data)

