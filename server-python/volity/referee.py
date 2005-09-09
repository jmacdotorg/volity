import time
import logging
import volent
import game
from zymb import sched, jabber
from zymb.jabber import interface
from zymb.jabber import rpc
import zymb.jabber.dataform
import zymb.jabber.keepalive

STATE_SETUP     = intern('setup')
STATE_ACTIVE    = intern('active')
STATE_DISRUPTED = intern('disrupted')
STATE_ABANDONED = intern('abandoned')
STATE_SUSPENDED = intern('suspended')

CLIENT_DEFAULT_RPC_TIMEOUT = 5 ###
DEAD_CONFIG_TIMEOUT = 90
ABANDONED_TIMEOUT = 3*60

class Referee(volent.VolEntity):
    logprefix = 'volity.referee'

    maxmucusers = 60

    def __init__(self, parlor, jid, password, resource, muc, gameclass):
        self.logprefix = Referee.logprefix + '.' + resource
        self.parlor = parlor
        self.resource = resource
        self.muc = muc
        self.mucnick = interface.JID(jid=muc)
        self.mucnick.setresource('referee')
        
        volent.VolEntity.__init__(self, jid, password, resource)

        self.refstate = STATE_SETUP
        self.startuptime = time.time()
        self.activitytime = None
        self.gamescompleted = 0
        
        self.players = {}
        self.playernicks = {}

        self.seats = {}
        self.seatlist = []
        self.gameseatlist = None
        self.seatsetupphase = True

        self.timerreason = None
        self.timeraction = None

        self.discomuctries = 0
        self.mucrunning = False
        self.mucshutdownreason = 'The referee has shut down.'
        
        self.addhandler('ready', self.beginwork)
        self.conn.adddispatcher(self.handlepresence, name='presence')
        self.conn.adddispatcher(self.handlemessage, name='message')

        self.language = 'en'
        self.recordgames = True
        self.showtable = True
        self.killgame = False

        # Set up the RPC replier.
        
        self.rpccli = self.conn.getservice('rpcclience')
        
        rpcserv = self.conn.getservice('rpcservice')
        ops = rpcserv.getopset().getopset()
        dic = ops.getdict()
        dic['volity'] = RefVolityOpset(self)
        dic['admin'] = RefAdminOpset(self)
        self.gamewrapperopset = WrapGameOpset(self, rpc.Opset())
        dic['game'] = self.gamewrapperopset

        # Set up the game instance.

        self.game = gameclass(self)
        assert self.game.referee == self
        self.seatsetupphase = False
        if (not self.seatlist):
            raise Exception('the game must create at least one seat')

        if (self.game.defaultlanguage != None):
            self.language = self.game.defaultlanguage
        if (self.game.defaultrecordgames != None):
            self.recordgames = self.game.defaultrecordgames
        if (self.game.defaultshowtable != None):
            self.showtable = self.game.defaultshowtable

        # Set up the disco replier.

        disco = self.conn.getservice('discoservice')
        
        # assumes resource didn't change
        form = jabber.dataform.DataForm()
        form.addfield('server', unicode(self.parlor.jid))
        form.addfield('table', unicode(self.muc))
        # The following are adjusted in updatediscoinfo().
        form.addfield('state', '')
        form.addfield('players', '')
        form.addfield('max-players', '')
        form.addfield('language', '')
        form.addfield('recorded', '')
        form.addfield('visible', '')

        ident = ('volity', 'referee', self.parlor.gamename)
        info = jabber.disco.DiscoInfo([ident], None, form)
        info.addfeature(interface.NS_DISCO_INFO)
        info.addfeature(interface.NS_DISCO_ITEMS)
        self.discoinfo = info
        
        disco.addinfo(None, self.updatediscoinfo)
        disco.additems()

        sserv = self.parlor.conn.getservice('keepaliveservice')
        if (sserv):
            serv = jabber.keepalive.KeepAliveService(sserv.getinterval())
            self.conn.addservice(serv)
            self.addhandler('ready', serv.start)
            self.log.warning('sending keepalive messages to self at interval of %d seconds', serv.getinterval())
            
        
    # ---- called by game base methods

    def addseat(self, seat):
        if (not self.seatsetupphase):
            raise Exception('you cannot add new seats after the referee has started up')
            
        if (self.seats.has_key(seat.id)):
            raise ValueError('seat %s already exists' % seat.id)
        self.seats[seat.id] = seat
        self.seatlist.append(seat)

    def setseatsrequired(self, setseats=[], clearseats=[]):
        if (self.refstate != STATE_SETUP):
            raise Exception('cannot change seat requirements after game setup')
            
        setls = [ seat for seat in setseats if not seat.required ]
        clrls = [ seat for seat in clearseats if seat.required ]
        if ((not setls) and (not clrls)):
            return

        for seat in setls:
            seat.required = True
        for seat in clrls:
            seat.required = False

        if (not self.seatsetupphase):
            ls = [ seat.id for seat in self.seatlist if seat.required ]
            self.game.sendtable('volity.required_seat_list', ls)

    def resolvemetharg(val):
        if (isinstance(val, game.Seat)):
            return val.id
        if (isinstance(val, Player)):
            return val.jidstr
        return val
    resolvemetharg = staticmethod(resolvemetharg)
            
    def sendone(self, player, methname, *methargs, **keywords):
        op = keywords.pop('callback', self.defaultcallback)
        if (not keywords.has_key('timeout')):
            keywords['timeout'] = CLIENT_DEFAULT_RPC_TIMEOUT
            
        if (not (player.live and player.aware)):
            return

        methargs = [ self.resolvemetharg(val) for val in methargs ]
        
        self.rpccli.send(op, player.jidstr,
            methname, *methargs, **keywords)

    def sendall(self, methname, *methargs, **keywords):
        op = keywords.pop('callback', self.defaultcallback)
        if (not keywords.has_key('timeout')):
            keywords['timeout'] = CLIENT_DEFAULT_RPC_TIMEOUT
            
        methargs = [ self.resolvemetharg(val) for val in methargs ]
        
        for player in self.players.values():
            if (player.live and player.aware):
                self.rpccli.send(op, player.jidstr,
                    methname, *methargs, **keywords)

    def defaultcallback(self, tup):
        try:
            res = sched.Deferred.extract(tup)
        except sched.TimeoutException, ex:
            self.log.warning('rpc timed out: %s', ex)
        except rpc.RPCFault, ex:
            self.log.warning('rpc returned fault: %s', ex)
        except interface.StanzaError, ex:
            self.log.warning('rpc returned stanza error: %s', ex)
        except Exception, ex:
            self.log.warning('rpc raised exception', exc_info=True)
            
    # ---- network message handlers
        
    def handlemessage(self, msg):
        if (self.log.isEnabledFor(logging.DEBUG)):
            self.log.info('received message')
            #self.log.debug('received message:\n%s', msg.serialize(True))
        raise interface.StanzaHandled()

    def handlepresence(self, msg):
        typestr = msg.getattr('type', '')
                
        fromstr = msg.getattr('from')
        if (fromstr):
            jid = interface.JID(fromstr)
        else:
            jid = None
            
        if (not (jid and self.jid.barematch(jid))):
            if (self.log.isEnabledFor(logging.DEBUG)):
                self.log.debug('received presence:\n%s', msg.serialize(True))
            else:
                self.log.info('received presence \'%s\' from %s', typestr, unicode(jid))

        if (jid
            and jid.getnode() == self.resource
            and jid.getdomain() == self.muc.getdomain()):
            self.handlemucpresence(typestr, jid.getresource(), msg)
            
        raise interface.StanzaHandled()

    def handlemucpresence(self, typestr, resource, msg):
        if (resource == 'referee' and self.state == 'configuring'):
            xnod = msg.getchild('x', namespace=interface.NS_MUC_USER)
            if (xnod):
                statnod = xnod.getchild('status')
                if (statnod and statnod.getattr('code') == '201'):
                    self.queueaction(self.configuremuc)

        if (resource != 'referee'):
            xnod = msg.getchild('x', namespace=interface.NS_MUC_USER)
            if (xnod):
                inod = xnod.getchild('item')
                jidstr = inod.getattr('jid')
                affil = inod.getattr('affiliation')
                if (jidstr and affil != 'owner'):
                    jid = interface.JID(jidstr)
                    if (typestr == ''):
                        self.playerarrived(jid, resource)
                        self.activitytime = time.time()
                    if (typestr == 'unavailable'):
                        self.playerleft(jid, resource)
                        self.activitytime = time.time()

    def beginwork(self):
        self.addhandler('end', self.endwork)    

        serv = self.conn.getservice('discoclience')
        serv.queryinfo(self.gotquerymuc, self.muc.getdomain(), timeout=30)

    def gotquerymuc(self, tup):
        try:
            res = sched.Deferred.extract(tup)
        except Exception, ex:
            self.log.error('unable to query MUC host %s: %s', self.muc.getdomain(), ex)
            self.discomuctries += 1
            if (self.discomuctries < 3):
                # Try again
                serv = self.conn.getservice('discoclience')
                serv.queryinfo(self.gotquerymuc, self.muc.getdomain(), timeout=30)
            else:
                # Give up
                self.stop()
            return

        if (not (interface.NS_MUC in res.features)):
            self.log.error('MUC host %s does not support the MUC feature', self.muc.getdomain())
            self.stop()
            return

        self.log.info('joining new muc: %s', self.muc)

        msg = interface.Node('presence',
            attrs={ 'to':unicode(self.mucnick) })
        msg.setchild('x', namespace=interface.NS_MUC)

        self.conn.send(msg, addid=False)
        self.jump('configuring')
        
    def configuremuc(self):
        msg = interface.Node('iq',
            attrs={'type':'get', 'to':unicode(self.muc)})
        msg.setchild('query', namespace=interface.NS_MUC_OWNER)
        id = self.conn.send(msg)
        
        self.conn.adddispatcher(self.handle_stanza_configuremuc,
            name='iq', type=('result','error'), id=id)
        
    def handle_stanza_configuremuc(self, msg):
        nod = msg.getchild('query')
        if (not nod or nod.getnamespace() != interface.NS_MUC_OWNER):
            # Not addressed to us
            return
            
        if (msg.getattr('type') != 'result'):
            ex = interface.parseerrorstanza(msg)
            self.log.error('could not configure MUC %s: %s', self.muc, ex)
            self.stop()
            raise interface.StanzaHandled

        try:
            xnod = nod.getchild('x')
            newxnod = self.buildconfigmuc(xnod)
            newmsg = interface.Node('iq',
                attrs={'type':'set', 'to':unicode(self.muc)})
            newqnod = newmsg.setchild('query',
                namespace=interface.NS_MUC_OWNER)
            if (newxnod):
                newqnod.addchild(newxnod)
                
            id = self.conn.send(newmsg)

            self.conn.adddispatcher(self.handle_stanza_configuremucresult,
                name='iq', type=('result','error'), id=id)
        except:
            self.stop()
            raise
            
        raise interface.StanzaHandled

    def buildconfigmuc(self, origform):

        dic = {
            'muc#owner_roomname':      self.parlor.gamename,
            'muc#roomconfig_roomname': self.parlor.gamename,
            'muc#owner_maxusers':      str(self.maxmucusers),
            'muc#roomconfig_maxusers': str(self.maxmucusers),
            'muc#owner_whois':         'anyone',
            'muc#roomconfig_whois':    'anyone',
        }
    
        if (origform == None):
            return None

        if (origform.getnamespace() != interface.NS_DATA):
            raise interface.BadRequest('muc config form had wrong namespace for <x>')
        if (origform.getattr('type') != 'form'):
            raise interface.BadRequest('muc config form had wrong type for <x>')

        newform = interface.Node('x', attrs={'type':'submit'})
        newform.setnamespace(interface.NS_DATA)

        for nod in origform.getchildren():
            if (nod.getname() != 'field'):
                continue
            varattr = nod.getattr('var')
            typattr = nod.getattr('type')
            if (not varattr or typattr=='fixed'):
                continue

            value = ''
            valnod = nod.getchild('value')
            if (valnod):
                value = valnod.getdata()

            if (dic.has_key(varattr)):
                value = dic[varattr]
                
            fnod = interface.Node('field', attrs={'var':varattr})
            valnod = fnod.setchild('value')
            if (value):
                valnod.setdata(value)

            newform.addchild(fnod)

        return newform
        
    def handle_stanza_configuremucresult(self, msg):
        if (msg.getattr('type') != 'result'):
            ex = interface.parseerrorstanza(msg)
            self.log.error('failed to configure MUC %s: %s', self.muc, ex)
            self.mucshutdownreason = 'Failed to configure MUC.'
            self.stop()
            raise interface.StanzaHandled

        self.mucrunning = True
        self.jump('running')
        self.queueaction(self.checktimersetting)
        raise interface.StanzaHandled

    def checktimersetting(self):
        if (self.refstate in [STATE_SETUP, STATE_SUSPENDED]):
            if (not self.players): ### account for bots
                self.settimer('deadconfig')
            else:
                self.settimer(None)
            return
        else:   # STATE_ACTIVE, STATE_DISRUPTED, STATE_ABANDONED
            ls = [ player for player in self.players.values()
                if player.live and player.seat ]
            if (not ls):
                self.refstate = STATE_ABANDONED
                self.settimer('abandoned')
                return
            for seat in self.gameseatlist:
                if (False): ### seat is eliminated
                    continue
                ls = [ player for player in seat.playerlist
                    if player.live ]
                if (not ls):
                    self.refstate = STATE_DISRUPTED
                    self.settimer(None)
                    return
            self.refstate = STATE_ACTIVE
            self.settimer(None)
            return

    def settimer(self, reason):
        if (reason == None):
            if (self.timeraction):
                self.timeraction.remove()
            self.timerreason = None
            self.timeraction = None
            return
        if (reason == 'deadconfig'):
            if (self.timerreason == 'deadconfig'):
                return
            if (self.timeraction):
                self.timeraction.remove()
            self.timerreason = 'deadconfig'
            self.timeraction = self.addtimer(self.deadconfigtimer,
                delay=DEAD_CONFIG_TIMEOUT)
            return
        if (reason == 'abandoned'):
            if (self.timerreason == 'abandoned'):
                return
            if (self.timeraction):
                self.timeraction.remove()
            self.timerreason = 'abandoned'
            self.timeraction = self.addtimer(self.abandonedtimer,
                delay=ABANDONED_TIMEOUT)
            return
        assert 0, ('unknown reason %s in settimer' % reason)

    def deadconfigtimer(self):
        self.log.warning('the table has been depopulated in configuration for %d seconds -- closing', DEAD_CONFIG_TIMEOUT)
        self.mucshutdownreason = 'The table has been abandoned.'
        self.stop()

    def abandonedtimer(self):
        self.log.warning('the game has been abandoned in play for %d seconds -- suspending', ABANDONED_TIMEOUT)
        self.queueaction(self.suspendgame)

    def playerarrived(self, jid, nick):
        jidstr = unicode(jid)
        player = self.players.get(jidstr, None)
        
        self.queueaction(self.checktimersetting)
        
        if (self.playernicks.has_key(nick)
            and player != self.playernicks[nick]):
            # Someone else already has this nickname. Jabber ought to prevent
            # this from happening.
            self.log.error('got presence from %s claiming nick %s, which belongs to %s. Ignoring',
                jidstr, nick, unicode(self.playernicks[nick].jid))
            return

        if (player):

            if (not self.playernicks.has_key(nick)):
                # I guess this player changed his room nick.
                self.playernicks.pop(player.nick, None)
                player.nick = nick
                self.playernicks[nick] = player

            if (not player.live):
                self.log.info('player %s has rejoined the table',
                    unicode(player))
                player.live = True
                player.aware = False
                
            return

        # New player.
        player = Player(self, jid, nick)
        self.players[jidstr] = player
        self.playernicks[nick] = player

    def playerleft(self, jid, nick):
        jidstr = unicode(jid)

        if (not self.players.has_key(jidstr)):
            return

        # This will do appropriate checking to see if the room is dead,
        # or if we need to throw the game.
        self.queueaction(self.checktimersetting)
        
        player = self.players[jidstr]

        if (not (self.refstate in [STATE_SETUP, STATE_SUSPENDED])
            and player.seat):
            # We have to keep the player record around (and in the seat.)
            self.log.info('seated player %s has left the table',
                unicode(player))
            self.playernicks.pop(player.nick, None)
            player.nick = None
            player.live = False
            player.aware = False
            return
        
        if (player.seat):
            # Presume player stood up before leaving.
            seat = player.seat
            assert (player in seat.playerlist)
            seat.playerlist.remove(player)
            player.seat = None

            # Message-sending will run after the player object is ditched
            self.unreadyall(False)
            self.queueaction(self.reportstand, player, seat)

        self.players.pop(jidstr, None)
        self.playernicks.pop(player.nick, None)
        player.nick = None
        player.live = False
        player.aware = False

    def playersit(self, sender, jidstr, seatid=None):
        if (not self.players.has_key(jidstr)):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(jidstr))

        player = self.players[jidstr]
        if (not player.aware):
            raise game.FailureToken('volity.jid_not_ready',
                game.Literal(jidstr))
        
        if (not seatid):
        
            if (player.seat):
                # Already seated
                return player.seat.id

            if (self.refstate == STATE_SETUP):
                seat = self.game.requestanyseat(player)
            else:
                seat = self.game.requestanyseatingame(player)
            if (not seat):
                raise game.FailureToken('volity.no_seat')
            if (not isinstance(seat, game.Seat) or (seat.referee != self)
                or not (seat in self.seatlist)):
                raise ValueError('requestanyseat returned an invalid seat')
            
        else:

            if (not self.seats.has_key(seatid)):
                raise rpc.RPCFault(606,
                    ('seat \'%s\' does not exist' % seatid))
            
            seat = self.seats[seatid]
            if (player.seat != seat):
                self.game.requestparticularseat(player, seat)

        # Now we have a seat

        if (self.refstate == STATE_SUSPENDED
            and not seat.ingame):
            raise game.FailureToken('volity.seat_not_available')
        
        if (player.seat == seat):
            # Already seated
            return player.seat.id

        if (player.seat):
            assert (player in player.seat.playerlist)
            player.seat.playerlist.remove(player)
            player.seat = None

        seat.playerlist.append(player)
        player.seat = seat

        self.unreadyall(False)
        self.queueaction(self.reportsit, player, seat)

        return seat.id

    def playerstand(self, sender, jidstr):
        if (not self.players.has_key(jidstr)):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(jidstr))
                
        player = self.players[jidstr]

        if (not player.seat):
            # Already standing
            return
        
        seat = player.seat
        assert (player in seat.playerlist)
        seat.playerlist.remove(player)
        player.seat = None
        
        self.unreadyall(False)
        self.queueaction(self.reportstand, player, seat)

    def playerready(self, sender):
        player = self.players[unicode(sender)]

        if (player.ready):
            return

        if (not player.seat):
            raise game.FailureToken('volity.not_seated')

        if (self.refstate == STATE_SETUP):
            # To leave setup mode, we need the game to check the config.
            self.game.checkconfig()
            self.game.checkseating()
        else:
            if (not self.killgame):
                # To leave suspension, we have to fill all the in-game seats.
                ls = [ seat for seat in self.gameseatlist if seat.isempty() ]
                if (ls):
                    raise game.FailureToken('volity.empty_seats')
            else:
                # But if the decision is to kill the game, we don't need
                # to check anything.
                pass

        player.ready = True
        self.queueaction(self.reportreadiness, player)

        ls = [ pla for pla in self.players.values() if pla.seat ]
        subls = [ pla for pla in ls if (not pla.ready) ]
        if (ls and not subls):
            if (self.refstate == STATE_SETUP):
                self.queueaction(self.begingame)
            else:
                if (not self.killgame):
                    self.queueaction(self.unsuspendgame)
                else:
                    self.queueaction(self.endgame, None, True)
            
    def playerunready(self, sender):
        player = self.players[unicode(sender)]

        if (not player.ready):
            return

        if (not player.seat):
            raise game.FailureToken('volity.not_seated')

        player.ready = False
        self.queueaction(self.reportreadiness, player)

    def playersuspend(self, sender=None):
        self.queueaction(self.suspendgame, sender)

    def configsetlanguage(self, sender, lang):
        if (len(lang) != 2):
            raise rpc.RPCFault(606, 'language must be a two-character string')

        if (self.language == lang):
            return

        self.language = lang
        
        self.unreadyall(False)
        self.queueaction(self.sendall, 'volity.language',
            sender, self.language)

    def configrecordgames(self, sender, flag):
        if (self.recordgames == flag):
            return

        self.recordgames = flag
        
        self.unreadyall(False)
        self.queueaction(self.sendall, 'volity.record_games',
            sender, self.recordgames)

    def configshowtable(self, sender, flag):
        if (self.showtable == flag):
            return

        self.showtable = flag
        
        self.unreadyall(False)
        self.queueaction(self.sendall, 'volity.show_table',
            sender, self.showtable)

    def configkillgame(self, sender, flag):
        if (self.killgame == flag):
            return

        self.killgame = flag
        
        self.unreadyall(False)
        self.queueaction(self.sendall, 'volity.kill_game',
            sender, self.killgame)

    def unreadyall(self, notify=True):
        ls = [ pla for pla in self.players.values() if pla.ready ]
        if (not ls):
            return
            
        self.log.info('marking %d player(s) unready, notify %s',
            len(ls), notify)
        for player in ls:
            player.ready = False
        if (notify):
            self.queueaction(self.reportunreadylist, ls)

    def sendfullstate(self, player):
        if (not player.live):
            return
        player.aware = True

        self.game.sendplayer(player, 'volity.receive_state')

        # First, the seating information        
        ls = [ seat.id for seat in self.seatlist ]
        subls = [ seat.id for seat in self.seatlist if seat.required ]
        self.game.sendplayer(player, 'volity.seat_list', ls)
        if (subls):
            self.game.sendplayer(player, 'volity.required_seat_list', subls)

        for pla in self.players.values():
            if (pla.seat):
                self.game.sendplayer(player, 'volity.player_sat', pla.jidstr, pla.seat.id)

        for pla in self.players.values():
            if (pla.ready):
                self.game.sendplayer(player, 'volity.player_ready', pla.jidstr)

        # Then, the game config information
        self.game.sendconfigstate(player)

        # Then, the game state (if in progress)
        if (self.refstate != STATE_SETUP):
            seat = self.game.getplayerseat(player)
            ### or the last known seat, if in suspended state
            ### or no seat?
            self.game.sendgamestate(player, seat)
            
        self.game.sendplayer(player, 'volity.state_sent')

    def reportsit(self, player, seat):
        self.log.info('player %s sits in seat %s', unicode(player), seat.id)
        self.sendall('volity.player_sat', player.jidstr, seat.id)
        
    def reportstand(self, player, seat):
        self.log.info('player %s stands from seat %s', unicode(player), seat.id)
        self.sendall('volity.player_stood', player.jidstr)

    def reportreadiness(self, player):
        if (player.ready):
            st = 'ready'
        else:
            st = 'unready'
        self.log.info('player %s reports %s', unicode(player), st)
        self.sendall('volity.player_'+st, player.jidstr)

    def reportunreadylist(self, ls):
        for player in ls:
            self.sendall('volity.player_unready', player.jidstr)

    def updatediscoinfo(self):
        form = self.discoinfo.getextendedinfo()
        
        form.addfield('state', self.refstate)

        ls = [ seat for seat in self.seatlist if seat.playerlist ]
        form.addfield('players', str(len(ls)))
        
        form.addfield('language', self.language)
        
        form.addfield('max-players', len(self.seatlist))

        if (not self.showtable):
            st = '0'
        else:
            st = '1'
        form.addfield('visible', st)

        if (not self.recordgames):
            st = '0'
        else:
            st = '1'
        form.addfield('recorded', st)

        if (not self.killgame):
            st = '0'
        else:
            st = '1'
        form.addfield('kill-game', st)

        return self.discoinfo

    def begingame(self):
        if (self.refstate != STATE_SETUP):
            self.log.error('entered begingame when state was %s',
                self.refstate)
            return
        
        self.log.info('game beginning!')
        self.refstate = STATE_ACTIVE

        self.gameseatlist = []
        for seat in self.seatlist:
            if (seat.playerlist):
                seat.ingame = True
                self.gameseatlist.append(seat)
            else:
                seat.ingame = False

        ### add current seat.playerlists to seat accumulated totals
        
        # From this point on, the seat listings are immutable. Also,
        # we don't discard a seated Player object even if it
        # disconnects.
        
        self.game.begingame()
        self.unreadyall(False)
        self.sendall('volity.start_game')

        # This will check against the immutable seat listings.
        self.queueaction(self.checktimersetting)

    def endgame(self, winners, cancelled=False):
        if (not cancelled):
            if (self.refstate in [STATE_SETUP, STATE_SUSPENDED]):
                self.log.error('entered endgame when state was %s',
                    self.refstate)
                return
        else:
            if (self.refstate == STATE_SETUP):
                self.log.error('cancelled game when state was %s',
                    self.refstate)
                return

        self.unreadyall(False)       # Might be needed in the killgame case
        self.sendall('volity.end_game')
        self.game.endgame(cancelled)

        # Get rid of all non-live players
        for jidstr in self.players.keys():
            player = self.players[jidstr]
            if (not player.live):
                seat = player.seat
                if (seat):
                    assert (player in seat.playerlist)
                    seat.playerlist.remove(player)
                    player.seat = None
                self.players.pop(jidstr, None)
            
        self.log.info('game ending!')
        self.refstate = STATE_SETUP
        self.killgame = False
        self.gamescompleted += 1

        self.gameseatlist = None
        for seat in self.seatlist:
            seat.ingame = False
            
        # Check just in case the room is empty of all but bots.
        self.queueaction(self.checktimersetting)

    def suspendgame(self, jidstr=None):
        if (self.refstate in [STATE_SETUP, STATE_SUSPENDED]):
            self.log.error('tried to suspend when state was %s', self.refstate)
            return

        if (not jidstr):
            # assumes resource didn't change
            jidstr = self.jid

        self.unreadyall(False)
        self.sendall('volity.suspend_game', jidstr)
        self.game.suspendgame()

        # Get rid of all non-live players
        for jidstr in self.players.keys():
            player = self.players[jidstr]
            if (not player.live):
                seat = player.seat
                if (seat):
                    assert (player in seat.playerlist)
                    seat.playerlist.remove(player)
                    player.seat = None
                self.players.pop(jidstr, None)
            
        self.log.info('game suspended')
        self.refstate = STATE_SUSPENDED

        # Throw in this check, just in case.
        self.queueaction(self.checktimersetting)
        
    def unsuspendgame(self):
        if (self.refstate != STATE_SUSPENDED):
            self.log.error('tried to unsuspendgame when state was %s',
                self.refstate)
            return

        for seat in self.gameseatlist:
            if (not seat.playerlist):
                self.log.error('tried to unsuspend when seat %s was empty',
                    seat.id)
                return

        self.log.info('game unsuspended')
        self.refstate = STATE_ACTIVE

        ### add current seat.playerlists to seat accumulated totals

        self.game.unsuspendgame()
        self.unreadyall(False)
        self.sendall('volity.resume_game')
        
        # This will check against the immutable seat listings.
        self.queueaction(self.checktimersetting)
        
    def announce(self, data):
        msg = interface.Node('message',
            attrs={ 'to':unicode(self.muc), 'type':'groupchat' })
        msg.setchilddata('body', 'administrator announcement: ' + data)
        self.queueaction(self.conn.send, msg)

    def endwork(self):
        if (self.mucrunning):
            msg = interface.Node('iq',
                attrs={'type':'set', 'to':unicode(self.muc)})
            newqnod = msg.setchild('query',
                namespace=interface.NS_MUC_OWNER)
            newqnod.setchild('destroy').setchilddata('reason',
                self.mucshutdownreason)
            self.conn.send(msg)
            
        # Tear down everything
        
        self.game.destroy()
        self.game.referee = None
        self.game = None
        self.players.clear()
        self.playernicks.clear()
        self.seats.clear()
        self.seatlist = []
        self.rpccli = None
        self.discoinfo = None
        self.gamewrapperopset = None
        self.parlor = None
        

class Player:
    def __init__(self, ref, jid, nick):
        self.referee = ref
        self.jid = jid
        self.jidstr = unicode(jid)
        self.nick = nick
        
        self.live = True
        self.aware = False
        self.seat = None
        self.ready = False

    def __str__(self):
        return '<Player %s \'%s\'>' % (self.jidstr, self.nick)

    def __unicode__(self):
        return u'<Player %s \'%s\'>' % (self.jidstr, self.nick)

    def send(self, methname, *args, **keywords):
        self.referee.game.sendplayer(self, methname, *args, **keywords)

class Validator:
    def typespecstring(typ):
        if (type(typ) != tuple):
            typ = ( typ, )
        ls = []
        for val in typ:
            if (val == str):
                res = 'string'
            elif (val == int):
                res = 'int'
            elif (val == bool):
                res = 'bool'
            elif (val == float):
                res = 'float'
            elif (val == list):
                res = 'array'
            elif (val == dict):
                res = 'struct'
            else:
                res = str(val)
            ls.append(res)
        return ' or '.join(ls)
    typespecstring = staticmethod(typespecstring)

    def __init__(self, **keywords):
        self.phasesetup = True
        self.phaseactive = True
        self.phasedisrupted = True
        self.phaseabandoned = True
        self.phasesuspended = True
        self.seated = True
        self.observer = True
        self.argcount = None
        self.arglist = None
        self.hasarglist = False
        if (keywords):
            self.set(keywords)

    def set(self, keywords):
        if (keywords.has_key('state')):
            self.phasesetup = False
            self.phaseactive = False
            self.phasedisrupted = False
            self.phaseabandoned = False
            self.phasesuspended = False
            val = keywords.pop('state')
            ls = val.split()
            if (STATE_SETUP in ls):
                self.phasesetup = True
            if (STATE_ACTIVE in ls):
                self.phaseactive = True
            if (STATE_DISRUPTED in ls):
                self.phasedisrupted = True
            if (STATE_ABANDONED in ls):
                self.phaseabandoned = True
            if (STATE_SUSPENDED in ls):
                self.phasesuspended = True
            
        if (keywords.has_key('afoot')):
            val = keywords.pop('afoot')
            if (val):
                self.phasesetup = False
                self.phaseactive = True
                self.phasedisrupted = True
                self.phaseabandoned = True
                self.phasesuspended = False
            else:
                self.phasesetup = True
                self.phaseactive = False
                self.phasedisrupted = False
                self.phaseabandoned = False
                self.phasesuspended = True
        if (keywords.has_key('seated')):
            val = keywords.pop('seated')
            if (val):
                self.observer = False
                self.seated = True
            else:
                self.observer = True
                self.seated = False
        if (keywords.has_key('argcount')):
            val = keywords.pop('argcount')
            self.argcount = int(val)
        if (keywords.has_key('args')):
            val = keywords.pop('args')
            if (type(val) != list):
                val = [ val ]
            self.arglist = val
            self.hasarglist = True
        if (keywords):
            raise ValueError('unrecognized keywords in validator: '
                + ', '.join(keywords.keys()))
            
    def check(self, ref, sender, callname, callargs):
        if (ref.refstate == STATE_SETUP and not self.phasesetup):
            raise game.FailureToken('volity.game_not_in_progress')
        if (ref.refstate == STATE_ACTIVE and not self.phaseactive):
            raise game.FailureToken('volity.game_in_progress')
        if (ref.refstate == STATE_DISRUPTED and not self.phasedisrupted):
            raise game.FailureToken('volity.game_in_progress')
        if (ref.refstate == STATE_ABANDONED and not self.phaseabandoned):
            raise game.FailureToken('volity.game_in_progress')
        if (ref.refstate == STATE_SUSPENDED and not self.phasesuspended):
            raise game.FailureToken('volity.game_not_in_progress')

        if (not self.seated):
            pla = ref.game.getplayer(sender)
            if (pla.seat):
                raise game.FailureToken('volity.are_seated')
        if (not self.observer):
            pla = ref.game.getplayer(sender)
            if (not pla.seat):
                raise game.FailureToken('volity.not_seated')

        if (self.argcount != None):
            if (len(callargs) != self.argcount):
                st = '%s takes %d argument' % (callname, self.argcount)
                if (self.argcount != 1):
                    st = st+'s'
                raise rpc.RPCFault(604, st)

        if (self.hasarglist):
            ls = self.arglist
            gotellipsis = False
            ix = 0
            while (ix < len(ls)):
                typ = ls[ix]
                if (typ == '...' or typ == Ellipsis):
                    gotellipsis = True
                    break
                if (typ == None or typ == (None,)):
                    break
                if (ix >= len(callargs)):
                    if (type(typ) == tuple and None in typ):
                        pass # ok
                    else:
                        raise rpc.RPCFault(604,
                            '%s: missing argument %d' % (callname, ix+1))
                else:
                    argtyp = type(callargs[ix])
                    if (argtyp == unicode):
                        argtyp = str
                    if (argtyp == typ or (type(typ) == tuple and argtyp in typ)):
                        pass # ok
                    elif (argtyp == int and
                        (float == typ or (type(typ) == tuple and float in typ))):
                        pass # ok
                    else:
                        raise rpc.RPCFault(605,
                            '%s: argument %d must be %s, not %s' % (callname,
                                ix+1,
                                self.typespecstring(typ),
                                self.typespecstring(argtyp)))
                ix = ix+1
                
            # ix is now the length of ls, or perhaps the index of the
            # ellipsis or None entry (which indicates the effective end of ls)
            if (len(callargs) > ix):
                if (gotellipsis):
                    return
                raise rpc.RPCFault(604, '%s: too many arguments' % callname)
        
class WrapGameOpset(rpc.WrapperOpset):
    def __init__(self, ref, subopset):
        rpc.WrapperOpset.__init__(self, subopset)
        self.referee = ref
        self.validators = {}

    def validation(self, callname, **keywords):
        val = self.validators.get(callname)
        if (not val):
            val = Validator()
            self.validators[callname] = val
        val.set(keywords)

    def precondition(self, sender, namehead, nametail, *callargs):
        if (self.referee.state != 'running'):
            raise game.FailureToken('volity.referee_not_ready')

        if (not self.referee.players.has_key(unicode(sender))):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(sender))

        val = self.validators.get(None)
        if (val):
            val.check(self.referee, sender, nametail, callargs)
        val = self.validators.get(nametail)
        if (val):
            val.check(self.referee, sender, nametail, callargs)
                
        self.referee.activitytime = time.time()

    def __call__(self, sender, callname, *callargs):
        try:
            return rpc.WrapperOpset.__call__(self, sender,
                callname, *callargs)
        except rpc.CallNotFound:
            raise rpc.RPCFault(603,
                'method does not exist in game opset: ' + callname)
        except volent.FailureToken:
            raise
        except rpc.RPCResponse:
            raise
        except rpc.RPCFault:
            raise
        except interface.StanzaError:
            raise
        except Exception, ex:
            st = str(ex.__class__) + ': ' + str(ex)
            self.referee.log.error(
                'uncaught exception in game opset: %s (from %s)',
                unicode(sender), callname,
                exc_info=True)
            raise rpc.RPCFault(608, st)
        
class RefVolityOpset(rpc.MethodOpset):
    def __init__(self, ref):
        self.referee = ref
        self.validators = {}

        self.validators['__default'] = Validator(state=STATE_SETUP)
        self.validators['sit'] = Validator(afoot=False,
            args=[str, (str, None)])
        self.validators['stand'] = Validator(afoot=False,
            args=str)
        self.validators['ready'] = Validator(afoot=False,
            argcount=0)
        self.validators['unready'] = Validator(afoot=False,
            argcount=0)
        self.validators['add_bot'] = Validator(afoot=False,
            argcount=0)
        self.validators['suspend_game'] = Validator(afoot=True,
            argcount=0, seated=True)
        self.validators['set_language'] = Validator(state=STATE_SETUP,
            args=str)
        self.validators['record_games'] = Validator(state=STATE_SETUP,
            args=bool)
        self.validators['show_table'] = Validator(state=STATE_SETUP,
            args=bool)
        self.validators['kill_game'] = Validator(state=STATE_SUSPENDED,
            args=bool)
        self.validators['send_state'] = Validator(argcount=0)

    def precondition(self, sender, namehead, nametail, *callargs):
        if (self.referee.state != 'running'):
            raise game.FailureToken('volity.referee_not_ready')

        if (not self.referee.players.has_key(unicode(sender))):
            raise game.FailureToken('volity.jid_not_present',
                game.Literal(sender))

        val = self.validators.get(namehead)
        if (not val):
            val = self.validators.get('__default')
        if (val):
            val.check(self.referee, sender, namehead, callargs)
            
        self.referee.activitytime = time.time()

    def rpc_sit(self, sender, *args):
        return self.referee.playersit(sender, *args)

    def rpc_stand(self, sender, *args):
        return self.referee.playerstand(sender, args[0])

    def rpc_ready(self, sender, *args):
        return self.referee.playerready(sender)

    def rpc_unready(self, sender, *args):
        return self.referee.playerunready(sender)

    def rpc_add_bot(self, sender, *args):
        raise interface.StanzaFeatureNotImplemented() ###
    
    def rpc_suspend_game(self, sender, *args):
        return self.referee.playersuspend(sender)
    
    def rpc_set_language(self, sender, *args):
        return self.referee.configsetlanguage(sender, args[0])

    def rpc_record_games(self, sender, *args):
        return self.referee.configrecordgames(sender, args[0])

    def rpc_show_table(self, sender, *args):
        return self.referee.configshowtable(sender, args[0])

    def rpc_kill_game(self, sender, *args):
        return self.referee.configkillgame(sender, args[0])

    def rpc_send_state(self, sender, *args):
        player = self.referee.game.getplayer(sender)
        if (not player):
            raise Exception('sender could not be found')
        self.referee.sendfullstate(player)
        return None

        
class RefAdminOpset(rpc.MethodOpset):
    def __init__(self, ref):
        self.referee = ref
        self.parlor = self.referee.parlor

    def precondition(self, sender, namehead, nametail, *callargs):
        if ((not self.parlor.adminjid)
            or (not sender.barematch(self.parlor.adminjid))):
            raise interface.StanzaNotAuthorized('admin operations are restricted')
        self.parlor.log.warning('admin command from <%s>: %s %s',
            unicode(sender), namehead, unicode(callargs))

    def rpc_status(self, sender, *args):
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'status: no arguments')
        dic = {}

        dic['agentstate'] = self.referee.state
        dic['state'] = self.referee.refstate
        dic['players'] = len(self.referee.players)
        dic['startup_time'] = time.ctime(self.referee.startuptime)
        dic['startup_at'] = volent.descinterval(
            self.referee.startuptime,
            limit=2)
        dic['last_activity_at'] = volent.descinterval(
            self.referee.activitytime,
            limit=2)
        dic['games_completed'] = self.referee.gamescompleted

        return dic

    def rpc_players(self, sender, *args):
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'players: no arguments')
        return self.referee.players.keys()

    def rpc_seats(self, sender, *args):
        if (len(args) >= 2):
            raise rpc.RPCFault(604, 'seats: one optional argument')
        if (not args):
            dic = {}
            ls = [ seat.id for seat in self.referee.seatlist ]
            dic['allseats'] = ' '.join(ls)
            ls = [ seat.id for seat in self.referee.seatlist if seat.required ]
            dic['reqseats'] = ' '.join(ls)
            ls = [ seat.id for seat in self.referee.seatlist if seat.playerlist ]
            dic['occupiedseats'] = ' '.join(ls)
            if (self.referee.gameseatlist):
                ls = [ seat.id for seat in self.referee.gameseatlist ]
                dic['gameseats'] = ' '.join(ls)
            return dic
        seat = self.referee.game.getseat(args[0])
        if (not seat):
            raise rpc.RPCFault(606, 'not a seat ID')
        dic = {}
        dic['id'] = seat.id
        dic['required'] = seat.required
        dic['ingame'] = seat.ingame
        dic['players'] = [ pla.jidstr for pla in seat.playerlist ]
        return dic
            
    def rpc_announce(self, sender, *args):
        if (len(args) != 1):
            raise rpc.RPCFault(604, 'announce STRING')
        val = args[0]
        if (not type(val) in [str, unicode]):
            raise rpc.RPCFault(605, 'announce STRING')
        self.referee.announce(val)
        return 'sent'

    def rpc_shutdown(self, sender, *args):
        if (len(args) != 0):
            raise rpc.RPCFault(604, 'shutdown: no arguments')
        self.referee.queueaction(self.referee.stop)
        return 'stopping referee'

