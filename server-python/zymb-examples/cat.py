#!/usr/bin/python

"""cat.py -- cat a file or stdin.

(Written by Andrew Plotkin. This script is in the public domain.
See <http://www.eblong.com/zarf/zymb/> for more information.)

This is an example which uses fileagent.File. It creates a fileagent.File
agent, which reads from a file -- or from stdin -- and prints the data
it receives. This effectively emulates "cat".

Maybe this isn't exciting for you, but it demonstrates how to do it.

    cat.py [ -D ] [ filename ]

If *filename* is given, that is the file which is opened. If none is given,
stdin is read.

    -D: Log zymb agent activity. -DD: Log even more zymb agent activity.
"""

import sys
import logging
import optparse
import zymb, zymb.fileagent, zymb.sched

usage = "usage: %prog [ options ] [ filename ]"

popt = optparse.OptionParser(prog=sys.argv[0],
    usage=usage,
    formatter=optparse.IndentedHelpFormatter(short_first=False))

popt.add_option('-D', '--debug',
    action='count', dest='debuglevel',
    help='display more info. (If used twice, even more.)')
    
popt.set_defaults(
    debuglevel=0)

(opts, args) = popt.parse_args()

if (not args):
    fl = sys.stdin
else:
    fl = args[0]

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


# Create the agent.
cl = zymb.fileagent.File(fl)

# Add a handler to print all received data to stdout.
cl.addhandler('handle', cl.basichandle)

# If operating on stdin, add another handler to print a shutdown message.
if (fl == sys.stdin):
    cl.addhandler('closed', sys.stderr.write, '<end of file>\n')

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
