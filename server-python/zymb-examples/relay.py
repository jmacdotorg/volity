#!/usr/bin/python

"""relay.py -- proxy a TCP connection to some server.

(Written by Andrew Plotkin. This script is in the public domain.
See <http://www.eblong.com/zarf/zymb/> for more information.)

This is an example which uses tcp.TCP and tcp.TCPListen. It listens for
network connections on a given port. When it receives one, it contacts
a specified remote server, and begins relaying data back and forth.
The local connection behaves exactly like a connection to the remote
server.

    relay.py [ --port localport ] [ -D ] remotehost remoteport

The *remotehost* and *remoteport* specify the server to connect to when
a local connection arrives.

    --port or -p: The local port to listen on. (Default 4201.)
    -D: Log zymb agent activity. -DD: Log even more zymb agent activity.
"""

import sys
import string
import logging
import optparse
import zymb, zymb.tcp, zymb.sched

usage = "usage: %prog [ options ] host port"

popt = optparse.OptionParser(prog=sys.argv[0],
    usage=usage,
    formatter=optparse.IndentedHelpFormatter(short_first=False))

popt.add_option('-p', '--port',
    action='store', dest='localport',
    help='localhost port to listen on (default 4201)')
popt.add_option('-D', '--debug',
    action='count', dest='debuglevel',
    help='display more info. (If used twice, even more.)')
    
popt.set_defaults(
    debuglevel=0,
    localport=4201)

(opts, args) = popt.parse_args()


if (len(args) < 2):
    print usage
    sys.exit()

host = args[0]
port = int(args[1])

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


def accepthandler(sock, acchost, accport):
    """accepthandler -- start up a connection relay. This is invoked
    when the main TCPListen agent accepts a connection.
    """
    
    # Create a TCP handler for the connection we just accepted.
    icl = zymb.tcp.TCP(acchost, accport, sock)
    # Create a TCP handler talking to the remote server.
    ocl = zymb.tcp.TCP(host, port)

    # Set each agent to send data to the other's send method.
    icl.addhandler('handle', ocl.send)
    ocl.addhandler('handle', icl.send)
    # Set each agent to shut down when the other one does.
    icl.addhandler('end', ocl.stop)
    ocl.addhandler('end', icl.stop)

    # Start both agents.
    ocl.start()
    icl.start()

# Create the listening agent.
licl = zymb.tcp.TCPListen(int(opts.localport))

# Add a handler to set up relaying when a connection is received.
licl.addhandler('accept', accepthandler)

# Tell the agent to begin work.
licl.start()

# The main doing-stuff loop.

while 1:
    try:
        res = zymb.sched.process(None)
        if (not res):
            break
    except KeyboardInterrupt, ex:
        rootlogger.warning('KeyboardInterrupt, shutting down...')
        zymb.sched.stopall()
