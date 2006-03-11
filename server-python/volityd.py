#!/usr/bin/python

"""
volityd.py: Start a Volity game parlor.

    GETTING STARTED

To run a Volity parlor, you need -- at minimum -- four things:

* A Jabber account, on any Jabber server. (The volity.net server offers
free accounts to any Volity user. You can use Javolin, or most other
featureful Jabber clients, to register an account.)

* This script, volityd.py.

* The "zymb" and "volity" Python modules. (Available from Volity's CVS
server <https://sourceforge.net/cvs/?group_id=91751>. Or, from
<http://eblong.com/zarf/zymb/> and <http://eblong.com/zarf/volity/>.)

* A Python game module which implements a Volity game. (Some sample
games are available from Volity's CVS, or the Volity page linked above.)

For the purposes of example, we will assume you want to run the "Rock
Paper Scissors" game, which is implemented in a class called games.rps.RPS.

Make sure the "zymb", "volity", and "games" modules are in your $PYTHONPATH.
(If this means nothing to you, just make sure that your current directory
contains the "zymb", "volity", and "games" subdirectories that you have
downloaded and unpacked.) Then type:

  python volityd.py --jid JABBERID --password PASSWORD --game games.rps.RPS

(A JABBERID looks like an email address. I registered user name "zarf" at
volity.net, so my ID there is "zarf@volity.net".)
  
You will see a message:

  WARNING : (volity.parlor) Game parlor running: Zarf's RPS

The script will then idle, waiting for game commands. Everything is now
set up. Anyone with a Volity client can request a new table at JABBERID,
and begin playing on your server.


    MORE CONFIGURATION OPTIONS

Volityd.py offers a great many configuration options (see below) but you
can ignore most of them.

You will probably want to set --contact-jid, or --contact-email, or both.
These offer players a way to contact you in case there is a problem with
your parlor.

If you want your parlor to offer human-vs-computer play, set --bot to the
name of a Python class that implements a bot. For example, the "Rock Paper
Scissors" game includes a bot class named games.rps.RPSBot. (Not all games
include bot implementations.)

The --retry option will attempt to restart the parlor if it shuts down (say,
if it loses contact with the Jabber server).

You may find that your parlor shuts down, or becomes inaccessible, after a
few minutes of inactivity. This is typically because your computer's
network configuration wants to shut down idle network connections. And some
Jabber servers make an effort to never allow your connection to become
completely idle. But if this is a problem for you, use the --keepalive
option.

If you set --admin to one of your Jabber IDs, you will be able to send
administrative commands and queries to your server, via Jabber. (Only the
JID you specify is permitted to send these commands.) Currently there is
no user-friendly tool to send these commands; you will have to use a
Jabber client that lets you send raw Jabber-RPC commands. The RPCs are
described at:

<http://www.volity.org/wiki/index.cgi?Admin_RPC_Requests>


    USING A CONFIGURATION FILE OR ENVIRONMENT VARIABLES

If you don't want to fuss with command-line options, you can put the same
information in a configuration file:

  # options for my Volity parlor
  jid: JABBERID
  password: PASSWORD
  game: games.rps.RPS

Name this "configfile", and then type:

  python volityd.py --config configfile

If environment variables are more your style, you can use them instead:

  setenv VOLITY_JID JABBERID
  setenv VOLITY_PASSWORD PASSWORD
  setenv VOLITY_GAME games.rps.RPS
  python volityd.py

You can mix and match config styles, too. Command-line arguments override
environment variables override config file lines.


    ALL THE CONFIGURATION OPTIONS

Here is the complete list of command-line options. The labels in
[square-brackets] are the equivalent config-file keys. If capitalized,
written with underscores, and prefixed with "VOLITY_", they become the
equivalent environment variables: $VOLITY_CONTACT_JID is another way to
set the --contact-jid option.

  --help, -h
      show this help message and exit
  --config=FILE, -cFILE
      configuration file name
  --game=GAMECLASS, -gGAMECLASS, [game]
      game class to play
  --jid=JID, -jJID, [jid]
      identity to operate game parlor under
  --resource=RESOURCE, -rRESOURCE, [jid-resource]
      resource for JID (if not already present) (default: volity)
  --password=PASSWORD, -pPASSWORD, [password]
      Jabber password of JID
  --bot=BOTCLASS, -bBOTCLASS, [bot]
      bot class to use, if requested
  --contact-jid=JID, [contact-jid]
      identity which is operating this parlor
  --contact-email=EMAIL, [contact-email]
      identity which is operating this parlor
  --visible=BOOL, [visible]
      whether the parlor should be listed in game directories (default: true)
  --muc=HOST, [muchost]
      service for multi-user conferencing (default: conference.volity.net)
  --bookkeeper=JID, [bookkeeper]
      central Volity bookkeeping service (default:
      bookkeeper@volity.net/volity)
  --admin=JID, [admin]
      identity permitted to send admin messages
  --retry, [retry]
      restart parlor if it (and all referees) die
  --keepalive, [keepalive]
      send periodic messages to exercise Jabber connection
  --keepalive-interval=KEEPALIVEINTERVAL, [keepalive-interval]
      send periodic messages with given interval
  --logfile=FILE, [logfile]
      write log info to a file (default: stdout)
  --rotate-logfile=COUNT, [rotate-logfile]
      rotate log files, once per day, keeping given number of old files
  --debug, -D, [debug-level]
      display more info. (If used twice, even more.)

The "debug-level" config file line (or $VOLITY_DEBUG_LEVEL) is a special
case. If you want debug messages, set it to a positive number. A debug-level
of 1 is equivalent to -D on the command line; 2 is equivalent to -DD; and
so on.
"""

import sys
if (sys.hexversion < 0x2030000):
    raise Exception, 'volityd.py requires Python version 2.3 or later.'

### jid or password could be Unicode?
    
import time
import os
import optparse
import logging
import zymb.sched
import volity.config
from volity import parlor

usage = "usage: %prog [ options ]"

popt = optparse.OptionParser(prog=sys.argv[0],
    usage=usage,
    formatter=optparse.IndentedHelpFormatter(short_first=False))

popt.add_option('-c', '--config',
    action='store', type='string', dest='configfile', metavar='FILE',
    help='configuration file name')
popt.add_option('-g', '--game',
    action='store', type='string', dest='game', metavar='GAMECLASS',
    help='game class to play')
popt.add_option('-j', '--jid',
    action='store', type='string', dest='jid',
    help='identity to operate game parlor under')
popt.add_option('-r', '--resource',
    action='store', type='string', dest='jidresource', metavar='RESOURCE',
    help='resource for JID (if not already present) (default: volity)')
popt.add_option('-p', '--password',
    action='store', type='string', dest='password',
    help='Jabber password of JID')
popt.add_option('-b', '--bot',
    action='store', type='string', dest='bot', metavar='BOTCLASS',
    help='bot class to use, if requested')
popt.add_option('--contact-jid',
    action='store', type='string', dest='contactjid', metavar='JID',
    help='identity which is operating this parlor')
popt.add_option('--contact-email',
    action='store', type='string', dest='contactemail', metavar='EMAIL',
    help='identity which is operating this parlor')
popt.add_option('--visible',
    action='store', type='string', dest='visible', metavar='BOOLEAN',
    help='whether the parlor should be listed in game directories (default: true)')
popt.add_option('--muc',
    action='store', type='string', dest='muchost', metavar='HOST',
    help='service for multi-user conferencing (default: conference.volity.net)')
popt.add_option('--bookkeeper',
    action='store', type='string', dest='bookkeeper', metavar='JID',
    help='central Volity bookkeeping service (default: bookkeeper@volity.net/volity)')
popt.add_option('--admin',
    action='store', type='string', dest='admin', metavar='JID',
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
popt.add_option('--logfile',
    action='store', type='string', dest='logfile', metavar='FILE',
    help='write log info to a file (default: stdout)')
popt.add_option('--rotate-logfile',
    action='store', type='int', dest='rotatelogfile', metavar='COUNT',
    help='rotate log files, once per day, keeping given number of old files')
popt.add_option('-D', '--debug',
    action='count', dest='debuglevel',
    help='display more info. (If used twice, even more.)')

# We handle defaults through the ConfigFile mechanism, not OptionParser
    
errors = False
(opts, args) = popt.parse_args()

# Options can come from opts, from a config file, or from environment
# variables.

argmap = {}
argmap['jid'] = opts.jid
argmap['jid-resource'] = opts.jidresource
argmap['contact-jid'] = opts.contactjid
argmap['contact-email'] = opts.contactemail
argmap['visible'] = opts.visible
argmap['game'] = opts.game
argmap['bot'] = opts.bot
argmap['password'] = opts.password
if (opts.debuglevel):
    argmap['debug-level'] = str(opts.debuglevel)
if (opts.retry):
    argmap['retry'] = 'True'
if (opts.rotatelogfile):
    argmap['rotate-logfile'] = str(opts.rotatelogfile)
argmap['muchost'] = opts.muchost
argmap['bookkeeper'] = opts.bookkeeper
if (opts.keepalive):
    argmap['keepalive'] = 'True'
if (opts.keepaliveinterval):
    argmap['keepalive-interval'] = str(opts.keepaliveinterval)
argmap['admin'] = opts.admin
argmap['logfile'] = opts.logfile

config = volity.config.ConfigFile(opts.configfile, argmap)

if (not config.get('game')):
    print sys.argv[0] + ': missing required option: --game GAMECLASS'
    errors = True
        
if (not config.get('jid')):
    print sys.argv[0] + ': missing required option: --jid JID'
    errors = True
else:
    if (config.get('jid').find('@') < 0):
        print sys.argv[0] + ': JID must be a full <name@domain>'
        errors = True

if (not config.get('password')):
    print sys.argv[0] + ': missing required option: --password PASSWORD'
    errors = True
        
if (errors):
    sys.exit(1)

debuglevel = 0
if (config.get('debug-level')):
    debuglevel = int(config.get('debug-level'))

#logging.addLevelName(5, 'TRACE')
#logging.TRACE = 5  # not really kosher
rootlogger = logging.getLogger('')
if (debuglevel >= 3):
    rootlogger.setLevel(logging.DEBUG)
    logging.getLogger('zymb.process').setLevel(logging.INFO)
elif (debuglevel >= 2):
    rootlogger.setLevel(logging.DEBUG)
    logging.getLogger('zymb').setLevel(logging.INFO)
elif (debuglevel == 1):
    rootlogger.setLevel(logging.INFO)
    logging.getLogger('zymb').setLevel(logging.WARNING)

roothandler = None
logfilename = config.get('logfile', '-')

def sethandler():
    global roothandler
    if (roothandler):
        roothandler.flush()
        roothandler.close()
        rootlogger.removeHandler(roothandler)
        removeHandler = None
        
    if (logfilename == '-'):
        roothandler = logging.StreamHandler(sys.stdout)
    else:
        roothandler = logging.FileHandler(logfilename)
    rootform = logging.Formatter('%(levelname)-8s: (%(name)s) %(message)s')
    roothandler.setFormatter(rootform)
    rootlogger.addHandler(roothandler)

lastrotation = time.localtime().tm_yday

def rotatelogs():
    global lastrotation
    assert (logfilename != '-')
    assert (rotatecount)
    
    newrotation = time.localtime().tm_yday
    if (newrotation != lastrotation):
        lastrotation = newrotation
        rootlogger.info('rotating log files...')
        ix = rotatecount-1
        while (ix > 0):
            oldname = logfilename+'.'+str(ix)
            newname = logfilename+'.'+str(ix+1)
            if (os.path.exists(oldname)):
                os.rename(oldname, newname)
            ix -= 1
        os.rename(logfilename, logfilename+'.1')
        sethandler()
        rootlogger.info('...rotated log files')
    
sethandler()

retryflag = False
if (config.get('retry')):
    retryflag = True

rotatecount = None
if (logfilename != '-'):
    rotatecount = config.get('rotate-logfile')
    if (rotatecount):
        rotatecount = int(rotatecount)
    
while 1:
    serv = parlor.Parlor(config)
    serv.start()
    
    if (rotatecount):
        serv.addtimer(rotatelogs, delay=119, periodic=True)

    # The main doing-stuff loop.

    while 1:
        try:
            res = zymb.sched.process(None)
            if (not res):
                break
        except KeyboardInterrupt, ex:
            rootlogger.warning('KeyboardInterrupt, shutting down...')
            serv.stop()
            retryflag = False

    if (not retryflag):
        rootlogger.warning('game parlor (and all referees) have died.')
        break
    rootlogger.warning('restarting game parlor...')
    time.sleep(5)
    