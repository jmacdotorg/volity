#!/usr/bin/python

"""wget.py -- fetch an http or https URL.

(Written by Andrew Plotkin. This script is in the public domain.
See <http://www.eblong.com/zarf/zymb/> for more information.)

This is an example which uses tcp.TCP and tcp.SSL. It creates an agent
which connects to a web server, sends a simple HTTP request, and prints
the data it receives.

    wget.py [ -D ] url

    -D: Log zymb agent activity. -DD: Log even more zymb agent activity.
"""

import sys
import string
import optparse
import logging
import zymb, zymb.tcp, zymb.sched

usage = "usage: %prog [ options ] url"

popt = optparse.OptionParser(prog=sys.argv[0],
    usage=usage,
    formatter=optparse.IndentedHelpFormatter(short_first=False))

popt.add_option('-D', '--debug',
    action='count', dest='debuglevel',
    help='display more info. (If used twice, even more.)')
    
popt.set_defaults(
    debuglevel=0)

(opts, args) = popt.parse_args()

if (len(args) < 1):
    print usage
    sys.exit()

url = args[0]

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

# Figure out if we want SSL or not

if (url.startswith('http://')):
    usessl = False
    uri = url[7:]
elif (url.startswith('https://')):
    usessl = True
    uri = url[8:]
else:
    print 'Invalid protocol'
    sys.exit()

# Split out the hostname and URI
    
pos = uri.find('/')
if (pos < 0):
    host = uri
    uri = '/'
else:
    host = uri[:pos]
    uri = uri[pos:]

# Create the agent (either TCP or SSL)
        
if (usessl):
    port = 443
    cl = zymb.tcp.SSL(host, port)
else:
    port = 80
    cl = zymb.tcp.TCP(host, port)

def sendrequest():
    """sendrequest -- handler run when the socket is connected.
    """
    st = 'GET %s HTTP/1.0\r\nHost: %s\r\n\r\n' % (uri, host)
    print 'Sending:\n', st
    cl.send(st)

# Add a handler to print all received data to stdout.
cl.addhandler('handle', cl.basichandle)

# Add a handler to send the HTTP request as soon as the socket connects.
cl.addhandler('connected', sendrequest)

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
