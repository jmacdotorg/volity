"""zymb.testing -- unit tests for a bunch of zymb modules.

To run these tests, start an interactive Python shell and type:

    import zymb.testing
    zymb.testing.run()

Or, from a Unix shell:

    python -c 'import zymb.testing; zymb.testing.run()'
"""

import sys
import re
import unittest
import logging
import sched
import tcp, xmlagent, xmldata
import jabber.client
import jabber.interface
import jabber.rpcdata
import jabber.rpc
import jabber.discodata
import xml.parsers.expat

class AgentHandler(logging.Handler):
    def __init__(self, logbuf):
        logging.Handler.__init__(self)
        self.agent = logbuf
    def emit(self, record):
        msg = self.format(record)
        self.agent.handle(msg)

class LoggingBuffer:
    alsoprint = False
    
    def __init__(self):
        
        rootlogger = logging.getLogger('')
        for han in rootlogger.handlers:
            rootlogger.removeHandler(han)
        rootlogger.setLevel(logging.DEBUG)
        roothandler = AgentHandler(self)
        rootform = logging.Formatter('%(levelname)-8s: (%(name)s) %(message)s')
        roothandler.setFormatter(rootform)
        rootlogger.addHandler(roothandler)

        self.log = []
        rootlogger.info('-' * 55)

        self.errors = []

    def clear(self):
        self.log = []
        
    def handle(self, msg):
        if (self.alsoprint):
            print msg
        self.log.append(msg)

    def handleerror(self, ex, ag):
        self.errors.append(ex)

    def getagentstates(self, ag):
        ls = []
        agname = str(ag)
        regex = re.compile('in state "([^"]*)"')
        for msg in self.log:
            if (agname in msg):
                match = regex.search(msg)
                if (match):
                    ls.append(match.group(1))
        return ls

    def getdologs(self):
        ls = []
        for msg in self.log:
            pos = msg.find('DOLOG ')
            if (pos >= 0):
                ls.append(msg[pos+6:])
        return ls

    def geterrors(self):
        return self.errors

def schedloop():
    while 1:
        res = sched.process(None)
        if (not res):
            break

def dolog(agent, msg, *args):
    agent.log.info('DOLOG '+msg, *args)

def assertnoaccumulate(agent, stanza):
    if (agent.xmlparse.result.getcontents()):
        raise Exception('nodes are accumulating')

def waitforquit(listenagent, cliagent, msg):
    if (msg == 'stop'):
        cliagent.stop()
        return
    if (msg == 'quit'):
        cliagent.stop()
        listenagent.stop()
        return
    if (msg == 'stopall'):
        sched.stopall()
        return
    
def acceptwaitquit(listenag, sock, host, port):
    cl = tcp.TCP(host, port, sock)
    cl.addhandler('handle', waitforquit, listenag, cl)
    cl.start()

def acceptreflect(sock, host, port):
    cl = tcp.TCP(host, port, sock)
    cl.addhandler('handle', cl.send)
    cl.start()

class Pocket:
    def __init__(self):
        self.agent = None
    def set(self, agent):
        self.agent = agent
        agent.addhandler('closed', dolog, agent, 'closedpocket')
    def stop(self):
        self.agent.stop()
    
def acceptpocket(pocket, sock, host, port):
    cl = tcp.TCP(host, port, sock)
    pocket.set(cl)
    cl.start()

def acceptbriefly(listenag, sock, host, port):
    cl = tcp.TCP(host, port, sock)
    cl.start()
    cl.addtimer(cl.stop, delay=0.1)
    cl.addhandler('end', listenag.stop)

class TestSelfJumperAgent(sched.Agent):
    def __init__(self):
        sched.Agent.__init__(self)
        self.addhandler('start', self.starthandler)
        self.addhandler('s1', self.handler1)
        self.addhandler('s2', self.handler2)
    def starthandler(self):
        self.jump('s1')
    def handler1(self):
        self.jump('s2')
    def handler2(self):
        self.jump('s3')

class TestAssertAgent(sched.Agent):
    def __init__(self):
        sched.Agent.__init__(self)
        self.addhandler('start', self.starthandler)
    def starthandler(self):
        assert 0, 'assertion'

class TestTimerAgent(sched.Agent):
    def __init__(self):
        sched.Agent.__init__(self)
        self.counter = 0
        self.addhandler('start', self.starthandler)
    def starthandler(self):
        self.addtimer(self.increment, delay=0.05)
    def increment(self):
        self.counter += 1
        self.jump('s'+str(self.counter))
        if (self.counter < 4):
            self.addtimer(self.increment, delay=0.05)

class TestPeriodicTimerAgent(sched.Agent):
    def __init__(self, testtype):
        sched.Agent.__init__(self)
        self.counter = 0
        self.timeraction = None
        self.testtype = testtype
        self.addhandler('start', self.starthandler)
    def starthandler(self):
        if (self.testtype):
            ac = sched.PeriodicAction(0.05, self.increment)
            self.timeraction = self.addtimer(ac)
        else:
            self.timeraction = self.addtimer(self.increment,
                delay=0.05, periodic=True)
    def increment(self):
        self.counter += 1
        self.jump('s'+str(self.counter))
        if (self.counter >= 4):
            self.timeraction.remove()

class TestStringListAgent(sched.Agent):
    def __init__(self, ls, thenend=False):
        sched.Agent.__init__(self)
        self.list = ls[:]
        self.thenend = thenend
        self.addhandler('start', self.starthandler)
    def starthandler(self):
        for st in self.list:
            self.perform('handle', st)
        if (self.thenend):
            self.stop()

# ----

class TestSched(unittest.TestCase):

    def test_startstop(self):
        logbuf = LoggingBuffer()
        schedloop()

    def test_startagent(self):
        logbuf = LoggingBuffer()
        ag = sched.Agent()
        ag.addhandler('error', logbuf.handleerror)
        ag.start()
        
        schedloop()
        
        ls = logbuf.getagentstates(ag)
        self.assertEqual(ls, ['start'])
        self.assert_(not logbuf.geterrors())

    def test_actionargs(self):
        logbuf = LoggingBuffer()
        ag = sched.Agent()
        ag.addhandler('error', logbuf.handleerror)
        ag.addhandler('log', dolog, ag)
        ag.addhandler('logfix', dolog, ag, 'logfixer')
        ag.addhandler('logplus', dolog, ag, 'logplus-%s')
        ag.addhandler('middle', dolog, ag, 'inmiddle')
        ag.addhandler('late', dolog, ag, 'inlate %s %s', 'one')
        
        ag.start()
        ag.perform('log', 'hello')
        ag.perform('log', 'percent %s %d', 'cheese', 5)
        ag.perform('logfix')
        ag.perform('logplus', 'more')
        ag.jump('middle')
        ag.jump('late', 'two')
        
        schedloop()
        
        ls = logbuf.getagentstates(ag)
        self.assertEqual(ls, ['start', 'middle', 'late'])
        ls = logbuf.getdologs()
        self.assertEqual(ls, [
            'hello', 'percent cheese 5',
            'logfixer', 'logplus-more',
            'inmiddle', 'inlate one two'])
        self.assert_(not logbuf.geterrors())

    def test_handlerremoval(self):
        logbuf = LoggingBuffer()
        ag = sched.Agent()
        ag.addhandler('error', logbuf.handleerror)
        
        ac1 = ag.addhandler('log', dolog, ag, 'one %s')
        ac2 = ag.addhandler('log', dolog, ag, 'two %s')
        
        ag.start()
        
        ag.perform('log', 'A')
        ag.perform('log', 'B')
        schedloop()
        ac1.remove()
        ag.perform('log', 'C')
        schedloop()
        ac2.remove()
        ag.perform('log', 'D')
        schedloop()
        
        ac3 = ag.addhandler('log', dolog, ag, 'three %s')
        ag.perform('log', 'E')
        ac3.remove()
        schedloop()
        
        ls = logbuf.getdologs()
        self.assertEqual(ls, ['two A', 'one A', 'two B', 'one B', 'two C'])
        self.assert_(not logbuf.geterrors())
        
    def test_selfjumperagent(self):
        logbuf = LoggingBuffer()
        ag = TestSelfJumperAgent()
        ag.addhandler('error', logbuf.handleerror)
        ag.start()
        
        schedloop()
        
        ls = logbuf.getagentstates(ag)
        self.assertEqual(ls, ['start', 's1', 's2', 's3'])
        self.assert_(not logbuf.geterrors())
        
    def test_timeragent(self):
        logbuf = LoggingBuffer()
        ag = TestTimerAgent()
        ag.addhandler('error', logbuf.handleerror)
        ag.start()
        
        schedloop()
        
        ls = logbuf.getagentstates(ag)
        self.assertEqual(ls, ['start', 's1', 's2', 's3', 's4'])
        self.assert_(not logbuf.geterrors())

    def test_assertagent(self):
        logbuf = LoggingBuffer()
        ag = TestAssertAgent()
        ag.addhandler('error', logbuf.handleerror)
        ag.start()
        
        schedloop()
        
        self.assert_(not logbuf.geterrors())

    def test_periodictimeragent(self):
        logbuf = LoggingBuffer()
        ag = TestPeriodicTimerAgent(False)
        ag.addhandler('error', logbuf.handleerror)
        ag2 = TestPeriodicTimerAgent(True)
        ag2.addhandler('error', logbuf.handleerror)
        ag.start()
        ag2.start()
        
        schedloop()
        
        ls = logbuf.getagentstates(ag)
        self.assertEqual(ls, ['start', 's1', 's2', 's3', 's4'])
        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 's1', 's2', 's3', 's4'])
        self.assert_(not logbuf.geterrors())

    def test_precedence(self):
        logbuf = LoggingBuffer()
        ag = sched.Agent()
        ag.addhandler('error', logbuf.handleerror)
        
        def logactioncount(ag, msg, count):
            ag.queueaction(dolog, ag, msg+'-'+str(count))
            if (count > 1):
                ag.queueaction(logactioncount, ag, msg, count-1)

        def gotactivity(ag):
            ag.queueaction(logactioncount, ag, 'gamma', 2)
            ag.queueaction(logactioncount, ag, 'delta', 2)
            pollaction.remove()

        pollaction = ag.registerpoll(gotactivity, ag, interval=0.05)
        ag.start()

        ag.addtimer(logactioncount, ag, 'alpha', 2, delay=0.0)
        ag.addtimer(logactioncount, ag, 'beta', 2, delay=0.0)

        schedloop()

        ls = logbuf.getdologs()
        self.assertEqual(ls,
            ['gamma-2', 'gamma-1', 'delta-2', 'delta-1',
            'alpha-2', 'alpha-1', 'beta-2', 'beta-1'])
        self.assert_(not logbuf.geterrors())

    def test_deferred(self):
        logbuf = LoggingBuffer()
        ag = sched.Agent()
        ag.addhandler('error', logbuf.handleerror)

        def defer2string(st):
            defer = sched.Deferred(deferstring, st+'e')
            ac = ag.queueaction(defer)
            defer.addaction(ac)
            raise defer

        def deferstring(st):
            defer = sched.Deferred(raisestring, st+'s')
            ac = ag.queueaction(defer)
            defer.addaction(ac)
            raise defer

        def raisestring(st):
            raise Exception, st

        def defwrapper(tup, ag):
            try:
                res = sched.Deferred.extract(tup)
            except Exception, ex:
                dolog(ag, 'caught-'+str(ex))
            
        def exceptwrapper(ag, func, *args):
            try:
                func(*args)
            except sched.Deferred, ex:
                ex.addcontext(defwrapper, ag)
            except Exception, ex:
                dolog(ag, 'caught-'+str(ex))
        
        ag.addhandler('start', exceptwrapper, ag, raisestring, 'bell')
        ag.addhandler('start', exceptwrapper, ag, deferstring, 'book')
        ag.addhandler('start', exceptwrapper, ag, defer2string, 'candl')
        
        ag.start()
        schedloop()
        
        self.assert_(not logbuf.geterrors())
        ls = logbuf.getdologs()
        self.assertEqual(ls, ['caught-candles', 'caught-books', 'caught-bell'])

        
class TestTCP(unittest.TestCase):

    def test_tcplisten(self):
        logbuf = LoggingBuffer()
        ag = tcp.TCPListen(4201)
        ag2 = tcp.TCP('localhost', 4201)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag.addhandler('accept', acceptwaitquit, ag)
        ag2.addhandler('connected', ag2.send, 'quit')
        
        ag.start()
        ag2.start()

        schedloop()
        
        ls = logbuf.getagentstates(ag)
        self.assertEqual(ls, ['start', 'listening', 'end'])
        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'connected', 'end'])
        self.assert_(not logbuf.geterrors())
        
    def test_tcplisten2(self):
        logbuf = LoggingBuffer()
        ag = tcp.TCPListen(4201)
        ag2 = tcp.TCP('localhost', 4201)
        ag3 = tcp.TCP('localhost', 4201)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag3.addhandler('error', logbuf.handleerror)
        ag.addhandler('accept', acceptwaitquit, ag)
        ag.addhandler('accept', dolog, ag, 'accepting %s %s %s')
        
        ag.start()
        ag2.start()
        ag3.start()
        ag.addtimer(sched.stopall, delay=0.1)

        schedloop()
        
        ls = logbuf.getagentstates(ag)
        self.assertEqual(ls, ['start', 'listening', 'end'])
        ls = logbuf.getdologs()
        ls = [ st[:9] for st in ls ]
        self.assertEqual(ls, ['accepting', 'accepting'])
        self.assert_(not logbuf.geterrors())
        
    def test_tcplistenreflect(self):
        logbuf = LoggingBuffer()
        ag = tcp.TCPListen(4201)
        ag2 = tcp.TCP('localhost', 4201)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag.addhandler('accept', acceptreflect)
        ag2.addhandler('connected', ag2.send, 'quit')
        ag2.addhandler('connected', ag2.send, 'begin')
        ag2.addhandler('handle', waitforquit, ag, ag2)
        ag2.addhandler('handle', dolog, ag2)
        
        ag.start()
        ag2.start()

        schedloop()
        
        ls = logbuf.getdologs()
        self.assertEqual(ls, ['begin', 'quit'])
        self.assert_(not logbuf.geterrors())

    def test_tcplistencloseserv(self):
        logbuf = LoggingBuffer()
        ag = tcp.TCPListen(4201)
        ag2 = tcp.TCP('localhost', 4201)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag.addhandler('accept', acceptbriefly, ag)
        ag.addhandler('closed', dolog, ag, 'closedlisten')
        ag2.addhandler('closed', dolog, ag2, 'closedcli')
        
        ag.start()
        ag2.start()

        schedloop()
        
        ls = logbuf.getdologs()
        self.assertEqual(ls, ['closedcli'])
        self.assert_(not logbuf.geterrors())
        
    def test_tcplistencloseserv2(self):
        logbuf = LoggingBuffer()
        ag = tcp.TCPListen(4201)
        ag2 = tcp.TCP('localhost', 4201)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        pocket = Pocket()
        ag.addhandler('accept', acceptpocket, pocket)
        ag.addhandler('closed', dolog, ag, 'closedlisten')
        ag2.addhandler('closed', dolog, ag2, 'closedcli')
        
        ag.start()
        ag2.start()
        ag.addtimer(pocket.stop, delay=0.1)
        ag.addtimer(ag.stop, delay=0.1)

        schedloop()
        
        ls = logbuf.getdologs()
        self.assertEqual(ls, ['closedcli'])
        self.assert_(not logbuf.geterrors())
        
    def test_tcplistenclosecli(self):
        logbuf = LoggingBuffer()
        ag = tcp.TCPListen(4201)
        ag2 = tcp.TCP('localhost', 4201)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        pocket = Pocket()
        ag.addhandler('accept', acceptpocket, pocket)
        ag.addhandler('closed', dolog, ag, 'closedlisten')
        ag2.addhandler('closed', dolog, ag2, 'closedcli')
        
        ag.start()
        ag2.start()
        ag2.addtimer(ag2.stop, delay=0.1)
        ag.addtimer(ag.stop, delay=0.1)

        schedloop()
        
        ls = logbuf.getdologs()
        self.assertEqual(ls, ['closedpocket'])
        self.assert_(not logbuf.geterrors())
        
class TestXML(unittest.TestCase):

    def test_basicxml(self):
        xmldat = [
            "<toplevel xmlns='name:space' key1='val' xml:lang='en'>",
            "<stanza num='1' />",
            "<stanza num='2'>cheese</stanza>",
            "</toplevel>"
        ]
        
        logbuf = LoggingBuffer()

        ag = TestStringListAgent(xmldat)
        ag2 = xmlagent.XML(ag)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('body', dolog, ag2, 'body %s %s %s')
        ag2.addhandler('stanza', dolog, ag2, 'stanza %s')

        ag2.addhandler('stanza', assertnoaccumulate, ag2)

        ag2.start()
        ag.start()

        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'body', 'end'])
        
        self.assertEqual(ag2.docattrs['key1'], 'val')
        self.assertEqual(ag2.docattrs['xml:lang'], 'en')
        ls = logbuf.getdologs()
        st = ls[0]
        self.assert_(re.match("body toplevel name:space {[^}]*}", st))
        self.assertEqual(ls[1:], [
            'stanza <stanza xmlns="name:space" num="1" />',
            'stanza <stanza xmlns="name:space" num="2">cheese</stanza>'
        ])
        self.assert_(not logbuf.geterrors())

    def test_unicodexml(self):
        xmldat = [
            "<toplevel xmlns='name:space' key1='val' xml:lang='en'>",
            "<stanza num='1'>\xe2\xa8\xb8</stanza>",
            # utf8-encoded: <CIRCLED DIVISION SIGN>
            "<stanza num='2'>che\xc3\xa8se</stanza>",
            # utf8-encoded: che<E WITH GRAVE>se
            "</toplevel>"
        ]
        
        logbuf = LoggingBuffer()
        stanzas = []

        ag = TestStringListAgent(xmldat)
        ag2 = xmlagent.XML(ag)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('stanza', stanzas.append)

        ag2.start()
        ag.start()

        schedloop()

        self.assertEqual(len(stanzas), 2)
        self.assertEqual(stanzas[0].getdata(), u'\u2a38')
        self.assertEqual(stanzas[1].getdata(), u'che\xe8se')
        self.assert_(not logbuf.geterrors())
        
    def test_basicxmlended(self):
        xmldat = [
            "<toplevel xmlns='name:space' key1='val' xml:lang='en'>",
            "<stanza num='1' />",
            "<stanza num='2'>cheese</stanza>"
        ]
        
        logbuf = LoggingBuffer()

        ag = TestStringListAgent(xmldat, True)
        ag2 = xmlagent.XML(ag)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('body', dolog, ag2, 'body %s %s %s')
        ag2.addhandler('stanza', dolog, ag2, 'stanza %s')

        ag2.start()
        ag.start()

        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'body', 'end'])
        
        self.assertEqual(ag2.docattrs['key1'], 'val')
        self.assertEqual(ag2.docattrs['xml:lang'], 'en')
        ls = logbuf.getdologs()
        st = ls[0]
        self.assert_(re.match("body toplevel name:space {[^}]*}", st))
        self.assertEqual(ls[1:], [
            'stanza <stanza xmlns="name:space" num="1" />',
            'stanza <stanza xmlns="name:space" num="2">cheese</stanza>'
        ])
        self.assert_(not logbuf.geterrors())
        
    def test_basicxmlnoclose(self):
        xmldat = [
            "<toplevel xmlns='name:space' key1='val' xml:lang='en'>",
            "<stanza num='1' />",
            "<stanza num='2'>cheese</stanza>"
        ]
        
        logbuf = LoggingBuffer()

        ag = TestStringListAgent(xmldat)
        ag2 = xmlagent.XML(ag)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('body', dolog, ag2, 'body %s %s %s')
        ag2.addhandler('stanza', dolog, ag2, 'stanza %s')

        ag2.start()
        ag.start()

        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'body'])
        
        self.assertEqual(ag2.docattrs['key1'], 'val')
        self.assertEqual(ag2.docattrs['xml:lang'], 'en')
        ls = logbuf.getdologs()
        st = ls[0]
        self.assert_(re.match("body toplevel name:space {[^}]*}", st))
        self.assertEqual(ls[1:], [
            'stanza <stanza xmlns="name:space" num="1" />',
            'stanza <stanza xmlns="name:space" num="2">cheese</stanza>'
        ])
        self.assert_(not logbuf.geterrors())
        
    def test_malformedxml(self):
        xmldat = [
            "<toplevel xmlns='name:space' key1='val' xml:lang='en'>",
            "<stanza num='1' />",
            "</screwytag>",
            "<stanza num='2'>cheese</stanza>",
            "</toplevel>"
        ]
        
        logbuf = LoggingBuffer()

        ag = TestStringListAgent(xmldat)
        ag2 = xmlagent.XML(ag)
        ag.addhandler('error', logbuf.handleerror)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('body', dolog, ag2, 'body %s %s %s')
        ag2.addhandler('stanza', dolog, ag2, 'stanza %s')

        ag2.start()
        ag.start()

        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'body', 'end'])
        
        self.assertEqual(ag2.docattrs['key1'], 'val')
        self.assertEqual(ag2.docattrs['xml:lang'], 'en')
        ls = logbuf.getdologs()
        st = ls[0]
        self.assert_(re.match("body toplevel name:space {[^}]*}", st))
        self.assertEqual(ls[1:], [
            'stanza <stanza xmlns="name:space" num="1" />'
        ])
        ls = logbuf.geterrors()
        self.assertEqual(len(ls), 1)
        self.assert_(isinstance(ls[0], xml.parsers.expat.ExpatError))

class TestParrotAgent(tcp.TCP):
    def __init__(self, host, port, socket, dic):
        tcp.TCP.__init__(self, host, port, socket)
        self.dic = dic
        self.addhandler('handle', self.grabstring)
    def grabstring(self, st):
        if (self.dic.has_key(st)):
            res = self.dic[st]
            if (type(res) != tuple):
                res = ( res, )
            for val in res:
                if (val == None):
                    self.stop()
                    return
                self.send(val)
        
class TestListenParrotAgent(tcp.TCPListen):
    def __init__(self, port, pocket, dic):
        tcp.TCPListen.__init__(self, port)
        self.pocket = pocket
        self.dic = dic
        self.addhandler('accept', self.acceptone)
    def acceptone(self, socket, host, port):
        cl = TestParrotAgent(host, port, socket, self.dic)
        cl.start()
        self.pocket.set(cl)
        self.stop()
    
class TestJabber(unittest.TestCase):

    def test_connectandstop(self):
        dic = {
            '<?xml version=\'1.0\'?><stream:stream xmlns="jabber:client" to="localhost" version="1.0" xmlns:stream="http://etherx.jabber.org/streams" >' :
            ( "<stream:stream xmlns='jabber:client' xml:lang='en' xmlns:stream='http://etherx.jabber.org/streams' from='jabber.org' id='ID' version='1.0'>",
            "<features />"),
            'QUIT': None
        }
        
        logbuf = LoggingBuffer()

        pocket = Pocket()
        ag = TestListenParrotAgent(4201, pocket, dic)
        ag2 = jabber.client.JabberConnect('erkyrath@localhost/test', 4201)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('connected', ag2.conn.send, 'QUIT')

        ag.start()
        ag2.start()
        
        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'gotheader', 'streaming', 'connected', 'end'])
        self.assert_(not logbuf.geterrors())

    def test_malformedheader(self):
        dic = {
            '<?xml version=\'1.0\'?><stream:stream xmlns="jabber:client" to="localhost" version="1.0" xmlns:stream="http://etherx.jabber.org/streams" >' :
            ( "</stream:stream xmlns='jabber:client' xml:lang='en' xmlns:stream='http://etherx.jabber.org/streams' from='jabber.org' id='ID' version='1.0'>",
            "<features />"),
            'QUIT': None
        }
        
        logbuf = LoggingBuffer()

        pocket = Pocket()
        ag = TestListenParrotAgent(4201, pocket, dic)
        ag2 = jabber.client.JabberConnect('erkyrath@localhost/test', 4201)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('connected', ag2.conn.send, 'QUIT')

        ag.start()
        ag2.start()
        
        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'end'])
        ls = logbuf.geterrors()
        self.assertEqual(len(ls), 1)
        self.assert_(isinstance(ls[0], xml.parsers.expat.ExpatError))

    def test_wrongtagheader(self):
        dic = {
            '<?xml version=\'1.0\'?><stream:stream xmlns="jabber:client" to="localhost" version="1.0" xmlns:stream="http://etherx.jabber.org/streams" >' :
            ( "<stream:streamq xmlns='jabber:client' xml:lang='en' xmlns:stream='http://etherx.jabber.org/streams' from='jabber.org' id='ID' version='1.0'>",
            "<features />"),
            'QUIT': None
        }
        
        logbuf = LoggingBuffer()

        pocket = Pocket()
        ag = TestListenParrotAgent(4201, pocket, dic)
        ag2 = jabber.client.JabberConnect('erkyrath@localhost/test', 4201)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('connected', ag2.conn.send, 'QUIT')

        ag.start()
        ag2.start()
        
        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'end'])
        ls = logbuf.geterrors()
        self.assertEqual(len(ls), 1)
        self.assert_(isinstance(ls[0], jabber.interface.StreamLevelError))
        (nam, text) = ls[0]
        self.assert_(nam, 'invalid-xml')

    def test_wrongtagnamespace(self):
        dic = {
            '<?xml version=\'1.0\'?><stream:stream xmlns="jabber:client" to="localhost" version="1.0" xmlns:stream="http://etherx.jabber.org/streams" >' :
            ( "<stream:stream xmlns='jabber:client' xml:lang='en' xmlns:stream='qhttp://etherx.jabber.org/streams' from='jabber.org' id='ID' version='1.0'>",
            "<features />"),
            'QUIT': None
        }
        
        logbuf = LoggingBuffer()

        pocket = Pocket()
        ag = TestListenParrotAgent(4201, pocket, dic)
        ag2 = jabber.client.JabberConnect('erkyrath@localhost/test', 4201)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('connected', ag2.conn.send, 'QUIT')

        ag.start()
        ag2.start()
        
        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'end'])
        ls = logbuf.geterrors()
        self.assertEqual(len(ls), 1)
        self.assert_(isinstance(ls[0], jabber.interface.StreamLevelError))
        (nam, text) = ls[0]
        self.assert_(nam, 'invalid-namespace')

    def test_malformedstanza(self):
        dic = {
            '<?xml version=\'1.0\'?><stream:stream xmlns="jabber:client" to="localhost" version="1.0" xmlns:stream="http://etherx.jabber.org/streams" >' :
            ( "<stream:stream xmlns='jabber:client' xml:lang='en' xmlns:stream='http://etherx.jabber.org/streams' from='jabber.org' id='ID' version='1.0'>",
            "</features>"),
            'QUIT': None
        }
        
        logbuf = LoggingBuffer()

        pocket = Pocket()
        ag = TestListenParrotAgent(4201, pocket, dic)
        ag2 = jabber.client.JabberConnect('erkyrath@localhost/test', 4201)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('connected', ag2.conn.send, 'QUIT')

        ag.start()
        ag2.start()
        
        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'gotheader', 'end'])
        ls = logbuf.geterrors()
        self.assertEqual(len(ls), 1)
        self.assert_(isinstance(ls[0], xml.parsers.expat.ExpatError))

    def test_streamerror(self):
        dic = {
            '<?xml version=\'1.0\'?><stream:stream xmlns="jabber:client" to="localhost" version="1.0" xmlns:stream="http://etherx.jabber.org/streams" >' :
            ( "<stream:stream xmlns='jabber:client' xml:lang='en' xmlns:stream='http://etherx.jabber.org/streams' from='jabber.org' id='ID' version='1.0'>",
            "<error><xml-not-well-formed xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></error>"),
            'QUIT': None
        }
        
        logbuf = LoggingBuffer()

        pocket = Pocket()
        ag = TestListenParrotAgent(4201, pocket, dic)
        ag2 = jabber.client.JabberConnect('erkyrath@localhost/test', 4201)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('connected', ag2.conn.send, 'QUIT')

        ag.start()
        ag2.start()
        
        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'gotheader', 'end'])
        ls = logbuf.geterrors()
        self.assertEqual(len(ls), 1)
        self.assert_(isinstance(ls[0], jabber.interface.StreamLevelError))
        (nam, text) = ls[0]
        self.assert_(nam, 'xml-not-well-formed')

    def test_secondaryqueue(self):
        dic = {
            '<?xml version=\'1.0\'?><stream:stream xmlns="jabber:client" to="localhost" version="1.0" xmlns:stream="http://etherx.jabber.org/streams" >' :
            ( "<stream:stream xmlns='jabber:client' xml:lang='en' xmlns:stream='http://etherx.jabber.org/streams' from='jabber.org' id='ID' version='1.0'>",
            "<features />"),
            'M1': ('<message>alpha1</message>',
                '<message>beta1</message>',
                '<message>alpha2</message><message>beta2</message>'
                '<message>omega</message>')
        }

        def handlestanza(ag, stanza):
            val = stanza.getdata()
            dolog(ag, val)
            if (val.startswith('alpha')):
                ag.queueaction(dolog, ag, 'startgame')
            if (val.startswith('beta')):
                dolog(ag, 'turn')
            if (val == 'omega'):
                ag.queueaction(ag.stop)
        
        logbuf = LoggingBuffer()

        pocket = Pocket()
        ag = TestListenParrotAgent(4201, pocket, dic)
        ag2 = jabber.client.JabberConnect('erkyrath@localhost/test', 4201)
        ag2.addhandler('error', logbuf.handleerror)
        ag2.addhandler('connected', ag2.conn.send, 'M1')
        ag2.adddispatcher(handlestanza, ag2,
            name='message', accept=True)

        ag.start()
        ag2.start()
        
        schedloop()

        ls = logbuf.getagentstates(ag2)
        self.assertEqual(ls, ['start', 'gotheader', 'streaming', 'connected', 'end'])
        self.assert_(not logbuf.geterrors())
        ls = logbuf.getdologs()
        self.assertEqual(ls,
            ['alpha1', 'startgame', 'beta1', 'turn',
            'alpha2', 'startgame', 'beta2', 'turn', 
            'omega', 'closedpocket'])

# ----
        
testlist = [
    ('sched', TestSched),
    ('tcp', TestTCP),
    ('xml', TestXML),
    ('xmldata', xmldata.TestXMLData),
    ('jabber', TestJabber),
    ('interface', jabber.interface.TestInterface),
    ('rpcdata', jabber.rpcdata.TestRpcData),
    ('rpc', jabber.rpc.TestRpc),
    ('discodata', jabber.discodata.TestDiscoData),
]

def run(arglist=[]):
    if (type(arglist) == str):
        arglist = arglist.split()
    if ('-D' in arglist):
        arglist.remove('-D')
        LoggingBuffer.alsoprint = True
        
    if (not arglist):
        tests = testlist
    else:
        tests = [(key, case) for (key, case) in testlist if key in arglist]
        
    ls = [ case for (key, case) in tests ]
    print 'Running:', (' '.join([ key for (key, case) in tests ]))
    suitels = [ unittest.makeSuite(case) for case in ls ]
    suite = unittest.TestSuite(suitels)
    unittest.TextTestRunner().run(suite)

if __name__ == '__main__':
    run(sys.argv[1:])
