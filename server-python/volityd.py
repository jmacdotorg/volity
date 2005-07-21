#!/usr/bin/python

import sys
if (sys.hexversion < 0x2030000):
    raise Exception, 'volityd requires Python version 2.3 or later.'

### jid or password could be Unicode?
### handle HUP signal! 
    
import time
import optparse
import logging
import zymb.sched
from volity import parlor

usage = "usage: %prog [ options ]"

popt = optparse.OptionParser(prog=sys.argv[0],
    usage=usage,
    formatter=optparse.IndentedHelpFormatter(short_first=False))

popt.add_option('-g', '--game',
    action='store', type='string', dest='gameclass',
    help='game class to play')
popt.add_option('-j', '--jid',
    action='store', type='string', dest='jid',
    help='identity to operate game parlor under')
popt.add_option('-r', '--resource',
    action='store', type='string', dest='jidresource', metavar='RESOURCE',
    help='resource for JID (if not already present)')
popt.add_option('-p', '--password',
    action='store', type='string', dest='password',
    help='Jabber password of JID')
popt.add_option('--muc',
    action='store', type='string', dest='muchost', metavar='HOST',
    help='service for multi-user conferencing (default: conference.volity.net)')
popt.add_option('--admin',
    action='store', type='string', dest='adminjid', metavar='JID',
    help='identity permitted to send admin messages')
popt.add_option('--retry',
    action='store_true', dest='retry',
    help='restart parlor if it (and all referees) die')
popt.add_option('--keepalive',
    action='store_true', dest='keepalive',
    help='send periodic messages to exercise Jabber connection')
popt.add_option('--keepalive-interval',
    action='store', type='int', dest='keepaliveinterval',
    help='send periodic messages with given interval')
popt.add_option('-D', '--debug',
    action='count', dest='debuglevel',
    help='display more info. (If used twice, even more.)')

popt.set_defaults(
    retry=False,
    debuglevel=0,
    muchost='conference.volity.net',
    adminjid=None,
    keepalive=False,
    keepaliveinterval=None)
    
errors = False
(opts, args) = popt.parse_args()

if (not opts.gameclass):
    print sys.argv[0] + ': missing required option: --game GAMECLASS'
    errors = True
        
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
    sys.exit(1)

#logging.addLevelName(5, 'TRACE')
#logging.TRACE = 5  # not really kosher
rootlogger = logging.getLogger('')
if (opts.debuglevel >= 3):
    rootlogger.setLevel(logging.DEBUG)
    logging.getLogger('zymb.process').setLevel(logging.INFO)
elif (opts.debuglevel >= 2):
    rootlogger.setLevel(logging.DEBUG)
    logging.getLogger('zymb').setLevel(logging.INFO)
elif (opts.debuglevel == 1):
    rootlogger.setLevel(logging.INFO)
    logging.getLogger('zymb').setLevel(logging.WARNING)
roothandler = logging.StreamHandler(sys.stdout)
rootform = logging.Formatter('%(levelname)-8s: (%(name)s) %(message)s')
roothandler.setFormatter(rootform)
rootlogger.addHandler(roothandler)

while 1:
    serv = parlor.Parlor(opts)
    serv.start()

    # The main doing-stuff loop.

    while 1:
        try:
            res = zymb.sched.process(None)
            if (not res):
                break
        except KeyboardInterrupt, ex:
            rootlogger.warning('KeyboardInterrupt, shutting down...')
            serv.stop()
            opts.retry = False

    if (not opts.retry):
        rootlogger.warning('game parlor (and all referees) have died.')
        break
    rootlogger.warning('restarting game parlor...')
    time.sleep(5)
    