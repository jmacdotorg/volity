#!/usr/bin/python

"""rpcserver.py -- a sample Jabber-RPC server application.

(Written by Andrew Plotkin. This script is in the public domain.
See <http://www.eblong.com/zarf/zymb/> for more information.)

This is an example which uses jabber.client and the high-level RPC
and disco facilities. It starts a Jabber client, and waits for Jabber-RPC
calls. (Yes, an RPC *server* is implemented as a Jabber *client*. This
program connects to a Jabber server just as any other Jabber client would.
But instead of sending IM messages to it, you send Jabber-RPC calls to it.
It executes the calls and sends back the results. Thus, it's acting as
an RPC server. Get it?)

    rpcserver.py --jid jid --password password [ -D ]

    --jid or -j: The JID which the server operates under.
    --password or -p: The password to authenticate as the JID.
    -D: Log zymb agent activity. -DD: Log even more zymb agent activity.

(You should supply a full JID, with a resource (account@server/resource).
This will distinguish the server's connection from other connections by
the same account.)

The RPC server accepts the following Jabber-RPC calls:

    hello (no arguments): Return the string "hello".
    id (one argument): Return the argument back.
    wait (delay): Wait *delay* seconds before returning. (Must be an int.)
    message (jid, string, string, ...): Send an ordinary Jabber message
        to *jid*. The body of the message is the remaining arguments,
        concatenated with spaces.
    disco (jid [node]): Send a disco info query to *jid* (with optional
        *node*). Return the result as a multilevel (nested) array.
    discit (jid [node]): Send a disco items query to *jid* (with optional
        *node*). Return the result as a multilevel (nested) array.
    exit (no arguments): Shut down the server.

If you pass the wrong number of arguments, you will get an RPC fault.
The "disco" and "discit" calls may return stanza-level errors if anything
goes wrong.

Note that these operations are all fully asynchronous. Even if a "disco" or
"discit" call is taking a long time, the server will continue to handle
RPC calls. (Including more "disco" and "discit" calls.) The same goes for
the "wait" call -- the server can have many calls waiting at the same time,
and still continue handling more requests.

As an added feature-example, the RPC server also handles basic disco
queries. A disco-info query to the server's JID will return its identity;
a disco-items query will return the list of RPCs it accepts.

Yes, you can use the "disco" RPC to make the server make a disco query
on itself. What are you, a smartass?
"""

import sys
import string
import logging
import optparse
import zymb, zymb.sched
import zymb.jabber.client, zymb.jabber.disco, zymb.jabber.rpc
import zymb.jabber.discodata

usage = "usage: %prog [ options ]"

popt = optparse.OptionParser(prog=sys.argv[0],
    usage=usage,
    formatter=optparse.IndentedHelpFormatter(short_first=False))

popt.add_option('-j', '--jid',
    action='store', type='string', dest='jid',
    help='identity to operate game server under')
popt.add_option('-p', '--password',
    action='store', type='string', dest='password',
    help='Jabber password of JID')
popt.add_option('--debug',
    action='count', dest='debuglevel',
    help='display more info. (If used twice, even more.)')

popt.set_defaults(
    debuglevel=0)
    
errors = False
(opts, args) = popt.parse_args()

if (not opts.jid):
    print sys.argv[0] + ': missing required option: --jid JID'
    errors = True
else:
    if (opts.jid.find('@') < 0):
        print sys.argv[0] + ': JID must be a full <name@domain>'
        errors = True

if (not opts.password):
    print sys.argv[0] + ': missing required option: --password PASSWORD'
    errors = True
        
if (errors):
    sys.exit()

rootlogger = logging.getLogger('')
if (opts.debuglevel >= 2):
    rootlogger.setLevel(logging.DEBUG)
    #logging.getLogger('zymb.process').setLevel(logging.INFO)
elif (opts.debuglevel == 1):
    rootlogger.setLevel(logging.INFO)
roothandler = logging.StreamHandler(sys.stdout)
rootform = logging.Formatter('%(levelname)-8s: (%(name)s) %(message)s')
roothandler.setFormatter(rootform)
rootlogger.addHandler(roothandler)


# ---- Enough setup. The example starts here.

class MyMethodOpset(zymb.jabber.rpc.MethodOpset):
    """MyMethodOpset: This class contains the code which handle the RPC
    calls. Methods whose names start with "rpc_" are call handlers.
    (You can add more simply by adding more "rpc_" methods. They
    are recognized automatically.)
    """

    def rpc_hello(self, sender, *args):
        """rpc_hello() -- RPC handler.

        This simply returns the string "hello". (The RPC module takes care
        of converting the return value to a Jabber-RPC stanza and sending it.
        All you have to do is return a value.)
        """
        
        if (len(args) != 0):
            raise zymb.jabber.rpc.RPCFault(999, 'hello takes no arguments')
            
        return 'hello'

    def rpc_id(self, sender, *args):
        """rpc_id() -- RPC handler.

        This returns the argument. Jabber-RPC types are transparently
        converted to Python objects in the argument list, and then
        the return value is converted to a Jabber-RPC stanza.
        """
        
        if (len(args) != 1):
            raise zymb.jabber.rpc.RPCFault(999, 'id takes one argument')
            
        return args[0]

    def rpc_exit(self, sender, *args):
        """rpc_exit -- RPC handler.

        This shuts down the server. (*cl* is a global variable in this
        file -- see below.)
        """
        
        if (len(args) != 0):
            raise zymb.jabber.rpc.RPCFault(999, 'exit takes no arguments')
            
        cl.stop()

    def rpc_message(self, sender, *args):
        """rpc_message -- RPC handler.

        This sends a message to the JID given as args[0]. The following
        args are concatenated together to form the message. The return
        value is the message's unique ID.

        This uses the low-level Jabber sending facility. It builds
        a Jabber stanza out of Node objects, and sends the construct
        directly.
        """
        
        if (len(args) < 2):
            raise zymb.jabber.rpc.RPCFault(999,
                'message takes two or more arguments')
        jidstr = unicode(args[0])
        
        ls = [ unicode(arg) for arg in args[1:] ]
        body = ' '.join(ls)

        # Build a Jabber <message> stanza.
        
        msg = zymb.jabber.interface.Node('message',
            attrs={ 'to':jidstr, 'type':'normal' })
        msg.setchilddata('body', body)

        # Send the message.
        id = cl.send(msg)
        return id

    def rpc_wait(self, sender, *args):
        """rpc_wait() -- RPC handler.

        Given an integer as args[0], this waits that many seconds and then
        returns the string "done waiting".

        It is very bad karma to block the zymb scheduler by calling
        time.sleep. Instead, we use zymb's deferral mechanism. By
        raising a Deferred object, we prevent this RPC handler from returning
        an RPC reply immediately. Instead, the RPC will be replied to
        when the Deferred object is invoked.
        """
        
        if (len(args) != 1):
            raise zymb.jabber.rpc.RPCFault(999, 'wait takes one argument')
        val = args[0]
        if (type(val) != int):
            raise zymb.jabber.rpc.RPCFault(999, 'wait requires an integer')

        # Create a deferral which will (eventually) trigger the RPC reply.
        defer = zymb.sched.Deferred(self.waitfinished)

        # Set up a timer to invoke the deferral in *val* seconds.
        ac = cl.addtimer(defer, delay=val)
        defer.addaction(ac)

        # Raise the deferral, thus suspending this RPC.
        raise defer

    def waitfinished(self):
        """waitfinished() -- deferral handler for rpc_wait.

        When the deferral created in rpc_wait is invoked, it will call
        this function. The semantics are exactly the same as for the original
        RPC handler: any return value is converted to a Jabber-RPC stanza
        and sent back to the original caller. (This function could
        also raise an RPC fault, or even create a second deferral.)
        """
        
        return 'done waiting'
    
    def rpc_disco(self, sender, *args):
        """rpc_disco() -- RPC handler.

        Given a JID as args[0], and (optionally) a node as args[1], this
        performs a disco info query to that JID. When the response comes
        back, it is converted to a nested array, and then sent back as
        the RPC response.

        This function creates a deferral, just like rpc_wait. However,
        it does not schedule it to run at any particular time. Instead,
        it begins a disco query operation. The disco module knows that
        a query can take a long time, and so it creates *its own* deferral
        and tells *us* to wait for a reply! Our job is to chain our own
        deferred operation (the suspended RPC reply) onto the deferred
        disco operation.

        It's less complicated than it sounds, honest.
        """
        
        if (len(args) < 1 or len(args) > 2):
            raise zymb.jabber.rpc.RPCFault(999,
                'disco takes 1 or 2 arguments')
        jidstr = args[0]
        node = None
        if (len(args) > 1):
            node = args[1]

        # Create a deferral which will (eventually) trigger the RPC reply.
        defer = zymb.sched.Deferred(self.gotdiscoinforeply)

        # serv is our DiscoClient module; it is in charge of sending disco
        # queries, and handling the replies on our behalf.
        serv = cl.getservice('discoclience')

        # We send out a disco query. The gotdisco() method (see below) is
        # our callback; it will be invoked when the disco results come in.
        # (We need to sneak the deferral object into the callback, so
        # we actually use a tuple (gotdisco, defer).)
        
        # The timeout parameter ensures that if no results come in within
        # 30 seconds, our callback will be invoked anyhow, with a
        # TimeoutException.
        serv.queryinfo((self.gotdisco, defer), jidstr, node=node, timeout=30)

        # Raise our deferral, thus suspending this RPC.
        raise defer
        
    def rpc_discit(self, sender, *args):
        """rpc_discit() -- RPC handler.

        Given a JID as args[0], and (optionally) a node as args[1], this
        performs a disco items query to that JID. When the response comes
        back, it is converted to a nested array, and then sent back as
        the RPC response.

        This function creates a deferral, just like rpc_wait. However,
        it does not schedule it to run at any particular time. Instead,
        it begins a disco query operation. The disco module knows that
        a query can take a long time, and so it creates *its own* deferral
        and tells *us* to wait for a reply! Our job is to chain our own
        deferred operation (the suspended RPC reply) onto the deferred
        disco operation.

        It's less complicated than it sounds, honest.
        """
        
        if (len(args) < 1 or len(args) > 2):
            raise zymb.jabber.rpc.RPCFault(999,
                'discit takes 1 or 2 arguments')
        jidstr = args[0]
        node = None
        if (len(args) > 1):
            node = args[1]

        # Create a deferral which will (eventually) trigger the RPC reply.
        defer = zymb.sched.Deferred(self.gotdiscoitemsreply)
        
        # serv is our DiscoClient module; it is in charge of sending disco
        # queries, and handling the replies on our behalf.
        serv = cl.getservice('discoclience')
        
        # We send out a disco query. The gotdisco() method (see below) is
        # our callback; it will be invoked when the disco results come in.
        # (We need to sneak the deferral object into the callback, so
        # we actually use a tuple (gotdisco, defer).)
        
        # The timeout parameter ensures that if no results come in within
        # 30 seconds, our callback will be invoked anyhow, with a
        # TimeoutException.
        serv.queryitems((self.gotdisco, defer), jidstr, node=node, timeout=30)

        # Raise our deferral, thus suspending this RPC.
        raise defer
        
    def gotdisco(self, tup, defer):
        """gotdisco() -- disco query callback.

        This is used as the callback for both disco info and disco items
        queries. (The two cases are identical except for the type of the
        *res* object we'll be getting. And we're just passing *res* on
        to the deferral.)

        Callbacks in zymb have a special structure, in order to be
        friendly to exceptions. You are not handed the disco reply data
        directly. Instead, you must call a special function
        zymb.sched.Deferred.extract() to retrieve the data. This function
        may return you the data, *or* raise various kinds of exceptions.
        We are canny, and handle them all.

        Whatever the results (data or exception), the goal is to trigger
        *defer* -- the long-delayed reply to the original "disco" or
        "discit" RPC. In each case, we queue the deferral up to be
        invoked. (It's kind of like addtimer(), but with no delay.)
        In each case, we pass two arguments into the deferral. The first
        is either 'ok' (meaning the query succeeded, and the result is the
        second argument), or 'ex' (meaning the query failed, and the second
        argument is some kind of exception object).
        """
        
        try:
            # Perform the extraction to get our query results!
            res = zymb.sched.Deferred.extract(tup)
            
            # If it succeeded, res is a DiscoInfo or DiscoItems object. Pass
            # it along to the deferral.
            ac = cl.queueaction(defer, 'ok', res)
            defer.addaction(ac)
            
        except zymb.sched.TimeoutException, ex:
            # extract() might have raised a TimeoutException, meaning that
            # 30 seconds passed with no query results. Build a stanza-level
            # error which represents this, and pass it along to the deferral.
            ex = zymb.jabber.interface.StanzaRemoteServerTimeout(
                'disco query timed out')
            ac = cl.queueaction(defer, 'ex', ex)
            defer.addaction(ac)
            
        except zymb.jabber.interface.StanzaError, ex:
            # extract() might have raised a StanzaError, meaning that the
            # query returned a stanza-level error. Pass it along to the
            # deferral.
            ac = cl.queueaction(defer, 'ex', ex)
            defer.addaction(ac)
            
        except Exception, ex:
            # extract() might have raised some other kind of exception.
            # (It's always possible.) Build a stanza-level error which
            # represents this as an "internal server error", and pass that
            # along to the deferral.
            ex = zymb.jabber.interface.StanzaInternalServerError(str(ex))
            ac = cl.queueaction(defer, 'ex', ex)
            defer.addaction(ac)

    def gotdiscoinforeply(self, res, msg):
        """gotdiscoinforeply() -- deferral handler for rpc_disco.

        When the deferral created in rpc_disco is invoked, it will call
        this function. The semantics are exactly the same as for the original
        RPC handler: any return value is converted to a Jabber-RPC stanza
        and sent back to the original caller.

        The *res* argument is either 'ok' (meaning *msg* is a DiscoInfo
        object) or 'ex' (meaning *msg* is an exception that we ran into).
        In the latter case, raise the exception. In the former case, convert
        the DiscoInfo to a nested array and return it.
        """
        
        if (res == 'ex'):
            # gotdisco() only raises exceptions that are stanza-level errors.
            # If we got one of those, we simply raise it -- that will cause
            # the original RPC call to return with that stanza-level error.
            raise msg

        # We are 'ok'. Now *msg* is a DiscoInfo object. This is not a valid
        # return type in Jabber-RPC, so we have to convert it to a type
        # that is valid: an array of arrays. We do it quick and dirty.
        
        ls = []
        subls = msg.getidentities()
        if (subls):
            ls.append(subls)
        subls = msg.getfeatures()
        if (subls):
            ls.append(subls)
        form = msg.getextendedinfo()
        if (form):
            flds = form.getfields()
            subls = [ [el for el in tup if el] for tup in flds ]
            ls.append(subls)
        return ls
        
    def gotdiscoitemsreply(self, res, msg):
        """gotdiscoitemsreply() -- deferral handler for rpc_disco.

        When the deferral created in rpc_disco is invoked, it will call
        this function. The semantics are exactly the same as for the original
        RPC handler: any return value is converted to a Jabber-RPC stanza
        and sent back to the original caller.

        The *res* argument is either 'ok' (meaning *msg* is a DiscoItems
        object) or 'ex' (meaning *msg* is an exception that we ran into).
        In the latter case, raise the exception. In the former case, convert
        the DiscoItems to a nested array and return it.
        """
        
        if (res == 'ex'):
            # gotdisco() only raises exceptions that are stanza-level errors.
            # If we got one of those, we simply raise it -- that will cause
            # the original RPC call to return with that stanza-level error.
            raise msg
            
        # We are 'ok'. Now *msg* is a DiscoItems object. This is not a valid
        # return type in Jabber-RPC, so we have to convert it to a type
        # that is valid: an array of arrays. We do it quick and dirty.
            
        ls = []
        for tup in msg.getitems():
            subls = [ el for el in tup if el ]
            ls.append(subls)
        return ls

    # End of MyMethodOpset class.        

# Create the Jabber connection agent.
cl = zymb.jabber.client.JabberAuthResource(opts.jid, opts.password)

# Add a DiscoService to our Jabber connection. This handles incoming
# disco queries.
disco = zymb.jabber.disco.DiscoService()
cl.addservice(disco)

# Add identity information to the DiscoService. Incoming queries will
# pick up this information.

info = disco.addinfo()
info.addidentity('automation', 'rpc-service', 'Jabber-RPC service')

# Add disco-items information. We do this in the form of a function,
# because each list entry includes the JID, and we don't actually know
# the JID until we're connected. (The JID from the command line may
# not be correct, because the Jabber resource we get may not be the
# one we asked for.)
def itemfunc():
    items = zymb.jabber.discodata.DiscoItems()
    selfjid = cl.getjid()
    items.additem(selfjid, 'Return "hello".', 'hello')
    items.additem(selfjid, 'Returns the argument passed to it.', 'id')
    items.additem(selfjid, 'Waits the requested number of seconds.', 'wait')
    items.additem(selfjid, 'Send a Jabber message.', 'message')
    items.additem(selfjid, 'Send a disco info query.', 'disco')
    items.additem(selfjid, 'Send a disco items query.', 'discit')
    items.additem(selfjid, 'Shut down this server.', 'exit')
    return items
disco.additems(None, itemfunc)

# Add a DiscoClience to our Jabber connection. This handles outgoing
# disco queries (and their incoming results). This will be used by the
# "disco" and "discit" RPC calls -- see above.
discoc = zymb.jabber.disco.DiscoClience()
cl.addservice(discoc)

# Add a RPCService to our Jabber connection. This handles incoming
# RPC calls.
rpc = zymb.jabber.rpc.RPCService()
# A MyMethodOpset instance handles the calls when they arrive.
rpc.setopset(MyMethodOpset())
cl.addservice(rpc)

# We do not need an RPCClience, because we do not plan to make any outgoing
# RPC calls.

# Tell the agent to begin work.
cl.start()

# The main doing-stuff loop.

while 1:
    try:
        res = zymb.sched.process(None)
        if (not res):
            break
    except KeyboardInterrupt, ex:
        rootlogger.warning('KeyboardInterrupt, shutting down...')
        zymb.sched.stopall()
