#!/usr/bin/python

"""
volityd.py: Start a Volity game parlor (or bot factory).

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


    RUNNING A BOT FACTORY

The volityd.py script can also be used to operate a bot factory. A factory
is very similar to a parlor; but instead of creating and operating entire
games, it creates and operates game bots. These bots are requested by
players in other parlors.

To run a bot factory, you use the arguments described above; except that
instead of --game, you use --bot, followed by the name of a Python class
that implements a bot. For example, the "Rock Paper Scissors" game includes
a bot class named games.rps.RPSBot. (Not all games include bot
implementations.)


    MORE CONFIGURATION OPTIONS

Volityd.py offers a great many configuration options (see below) but you
can ignore most of them.

You will probably want to set --contact-jid, or --contact-email, or both.
These offer players a way to contact you in case there is a problem with
your parlor.

If you want your parlor to offer human-vs-computer play, set both the
--game and the --bot arguments. The --bot should be the name of a Python
class that implements a bot. These bots will only be used in your parlor.

The --restart-script option allows the parlor to restart itself if its
Jabber connection dies. It also allows you to use the restart admin RPCs.
You should pass in the location of the script to use to restart the
service; normally, this will be volityd.py itself.

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
      resource for JID (if not given in --jid) (default: volity)
  --host=HOST, [host]
      Jabber server to contact (if not given in --jid) (default: as in --jid)
  --password=PASSWORD, -pPASSWORD, [password]
      Jabber password of JID
  --bot=BOTCLASS, -bBOTCLASS, [bot]
      bot class to use, if requested (may be repeated)
  --bot-factory=JID, [bot-factory]
      external bot factory to recommend (may be repeated)
  --entity-name=ENTITYNAME [entity-name]
      name for parlor to publish
  --entity-description=ENTITYDESC [entity-desc]
      description for parlor to publish
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
      identity permitted to send admin messages (or comma-separated list)
  --restart-script=PATH, [restart-script]
      location of volityd.py, or whatever script you want to restart dead
      parlors (default: do not restart)
  --keepalive, [keepalive]
      send periodic messages to exercise Jabber connection
  --keepalive-interval=KEEPALIVEINTERVAL, [keepalive-interval]
      send periodic messages with given interval
  --logfile=FILE, [logfile]
      write log info to a file (default: stdout)
  --rotate-logfile=COUNT, [rotate-logfile]
      rotate log files, once per day, keeping given number of old files
  --delay=INT
      pause the given number of seconds before starting up (internal use
      only)
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
from volity import factory

# Save sys.args for future forking
originalargs = list(sys.argv)

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
    help='resource for JID (if not given in --jid) (default: volity)')
popt.add_option('--host',
    action='store', type='string', dest='host', metavar='HOST',
    help='Jabber server to contact (if not given in --jid) (default: as in --jid)')
popt.add_option('-p', '--password',
    action='store', type='string', dest='password',
    help='Jabber password of JID')
popt.add_option('-b', '--bot',
    action='append', type='string', dest='bot', metavar='BOTCLASS',
    help='bot class to use, if requested (may be repeated)')
popt.add_option('--bot-factory',
    action='append', type='string', dest='botfactoryjid', metavar='JID',
    help='external bot factory to recommend (may be repeated)')
popt.add_option('--entity-name',
    action='store', type='string', dest='entityname',
    help='name for parlor to publish')
popt.add_option('--entity-description',
    action='store', type='string', dest='entitydesc',
    help='description for parlor to publish')
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
    help='identity permitted to send admin messages (or comma-separated list)')
popt.add_option('--restart-script',
    action='store', type='string', dest='restartscript', metavar='PATH',
    help='location of volityd.py, or whatever script you want to restart dead parlors (default: do not restart)')
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
popt.add_option('--delay',
    action='store', type='int', dest='delay', metavar='INT',
    help='pause the given number of seconds before starting up (internal use only)')
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
argmap['host'] = opts.host
argmap['entity-name'] = opts.entityname
argmap['entity-desc'] = opts.entitydesc
argmap['contact-jid'] = opts.contactjid
argmap['contact-email'] = opts.contactemail
argmap['visible'] = opts.visible
argmap['game'] = opts.game
argmap['bot'] = opts.bot  # list
argmap['bot-factory'] = opts.botfactoryjid  # list
argmap['password'] = opts.password
if (opts.debuglevel):
    argmap['debug-level'] = str(opts.debuglevel)
if (opts.rotatelogfile):
    argmap['rotate-logfile'] = str(opts.rotatelogfile)
argmap['muchost'] = opts.muchost
argmap['bookkeeper'] = opts.bookkeeper
if (opts.keepalive):
    argmap['keepalive'] = 'True'
if (opts.keepaliveinterval):
    argmap['keepalive-interval'] = str(opts.keepaliveinterval)
argmap['admin'] = opts.admin
argmap['restart-script'] = opts.restartscript
argmap['logfile'] = opts.logfile
def argmap_restart_func(delay):
    execself(delay)
argmap['restart-func-'] = argmap_restart_func

config = volity.config.ConfigFile(opts.configfile, argmap)

if (not (config.get('game') or config.get('bot'))):
    print sys.argv[0] + ': missing required option: --game GAMECLASS (or --bot BOTCLASS)'
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

if (opts.delay):
    time.sleep(opts.delay)

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

isprimary = True
        
roothandler = None
logfilename = config.get('logfile', '-')

def sethandler():
    global roothandler
    if (roothandler):
        roothandler.flush()
        roothandler.close()
        rootlogger.removeHandler(roothandler)
        roothandler = None

    if (not isprimary):
        return
        
    if (logfilename == '-'):
        roothandler = logging.StreamHandler(sys.stdout)
    else:
        roothandler = logging.FileHandler(logfilename)
    rootform = logging.Formatter('%(levelname)-8s: (%(name)s) %(message)s')
    roothandler.setFormatter(rootform)
    rootlogger.addHandler(roothandler)

lastrotation = time.localtime().tm_yday

def rotatelogs():
    global lastrotation, roothandler
    assert (logfilename != '-')
    assert (rotatecount)

    if (not isprimary):
        # We don't want a child process rotating the logs! We also want
        # to stop logging before the next rotation. We leave a five-minute
        # safety margin.
        newrotation = time.localtime(time.time()+300).tm_yday
        if (newrotation != lastrotation):
            lastrotation = newrotation
            rootlogger.warning('giving up on logging so as not to confuse rotation')
            sethandler()
        return
    
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

rotatecount = None
if (logfilename != '-'):
    rotatecount = config.get('rotate-logfile')
    if (rotatecount):
        rotatecount = int(rotatecount)

def execself(delay):
    global isprimary
    
    restartscript = config.get('restart-script')
    if (not restartscript):
        rootlogger.error('no restart-script available!')
        return

    if (not isprimary):
        rootlogger.error('child process cannot fork again!')
        return
        
    rootlogger.warning('will re-exec self...')

    newargs = list(originalargs)
    try:
        pos = newargs.index('--delay')
    except:
        pos = -1
    if (delay):
        if (pos < 0):
            newargs.extend(['--delay', '5'])
        else:
            newargs[pos+1] = '5'
    else:
        if (pos < 0):
            newargs.extend(['--delay', '0'])
        else:
            newargs[pos+1] = '0'

    pid = os.fork()
    if (pid == 0):
        # child
        isprimary = False
        rootlogger.warning('...child forked, continuing')
    else:
        # parent
        try:
            os.execv(restartscript, newargs)
        except Exception, ex:
            rootlogger.error('Unable to exec %s: %s', restartscript, ex)
        rootlogger.error('parent should not still exist!')

if (config.get('game')):
    volrole = 'game parlor'
    subroles = 'referees'
    serv = parlor.Parlor(config)
else:
    volrole = 'bot factory'
    subroles = 'actors'
    serv = factory.Factory(config)
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
        if (isprimary):
            # stop immediately, no restart
            serv.requeststop(False, False)
        else:
            rootlogger.warning('...emergency stop!')
            zymb.sched.stopall()

rootlogger.warning('%s (and all %s) have died.', volrole, subroles)

