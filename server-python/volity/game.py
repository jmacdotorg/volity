from zymb import jabber
import zymb.jabber.rpc
import zymb.jabber.interface
from volent import FailureToken, Literal

class Game:
    gamename = None
    gamedescription = 'A Volity game.'
    ruleseturi = None
    rulesetversion = '1.0'
    websiteurl = None

    defaultlanguage = None
    defaultrecordgames = None
    defaultshowtable = None
    
    def __init__(self, ref):
        if (not isinstance(ref, referee.Referee)):
            raise TypeError('game must be initialized with a Referee')
        self.referee = ref
        self.log = self.referee.log

    def getseat(self, id):
        return self.referee.seats.get(id, None)

    def getseatlist(self):
        return self.referee.seatlist

    def setseatsrequired(self, set=[], clear=[]):
        if (isinstance(set, Seat)):
            set = [set]
        if (isinstance(clear, Seat)):
            clear = [clear]
        self.referee.setseatsrequired(set, clear)

    def getplayer(self, jid):
        if (isinstance(jid, jabber.interface.JID)):
            jid = unicode(jid)
        return self.referee.players.get(jid, None)

    def getplayerlist(self):
        return self.referee.players.values()

    def getplayerseat(self, jid):
        pla = self.getplayer(jid)
        if (not pla):
            return None
        return pla.seat

    def getstate(self):
        return self.referee.refstate

    def setopset(self, gameopset):
        if (not isinstance(gameopset, jabber.rpc.Opset)):
            gameopset = ObjMethodOpset(gameopset)
        self.referee.gamewrapperopset.setopset(gameopset)

        serv = self.referee.conn.getservice('rpcservice')

    def validatecalls(self, *calllist, **keywords):
        if (not calllist):
            self.referee.gamewrapperopset.validation(None, **keywords)
        else:
            for cal in calllist:
                self.referee.gamewrapperopset.validation(cal, **keywords)

    def unready(self):
        self.referee.unreadyall()

    def sendtable(self, methname, *args, **keywords):
        ls = self.getplayerlist()
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def sendobservers(self, methname, *args, **keywords):
        ls = [ pla for pla in self.getplayerlist() if not pla.seat ]
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def sendgame(self, methname, *args, **keywords):
        ls = [ pla for pla in self.getplayerlist() if pla.seat ]
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def sendplayer(self, player, methname, *args, **keywords):
        if (not isinstance(player, referee.Player)):
            player = self.getplayer(player)
        if (not player):
            return
        ls = [ player ]
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def sendseat(self, seat, methname, *args, **keywords):
        if (not isinstance(seat, Seat)):
            seat = self.getseat(seat)
        if (not seat):
            return
        ls = seat.playerlist
        self.queueaction(self.performsend, ls,
            methname, args, keywords)

    def performsend(self, playerlist, methname, args, keywords):
        if (not '.' in methname):
            methname = 'game.' + methname
        for player in playerlist:
            self.referee.sendone(player, methname, *args, **keywords)

    def queueaction(self, op, *args):
        return self.referee.queueaction(op, *args)
        
    def addtimer(self, op, *args, **dic):
        return self.referee.addtimer(op, *args, **dic)

    def gameover(self, *winners):
        ### canonicalize winners (into multi-level list)
        self.queueaction(self.referee.endgame, winners)

    def gamecancelled(self):
        pass ### call endgame with an extra argument?
    
    # The methods below are stubs, meant to be overridden by the game class.

    def requestparticularseat(self, player, seat):
        pass
    
    def requestanyseat(self, player):
        ls = [ seat for seat in self.getseatlist()
            if (seat.isrequired() and seat.isempty()) ]
        if (ls):
            return ls[0]
        ls = [ seat for seat in self.getseatlist()
            if ((not seat.isrequired()) and seat.isempty()) ]
        if (ls):
            return ls[0]
        return None

    def requestanyseatingame(self, player):
        ls = [ seat for seat in self.getseatlist()
            if (seat.isingame() and seat.isempty()) ]
        if (ls):
            return ls[0]
        return None

    def checkconfig(self):
        pass

    def checkseating(self):
        ls = [ seat for seat in self.getseatlist()
            if seat.isrequired() and seat.isempty() ]
        if (ls):
            raise FailureToken('volity.empty_seats')

    def begingame(self):
        pass

    def endgame(self, cancelled):
        pass

    def suspendgame(self):
        pass

    def unsuspendgame(self):
        pass

    def sendconfigstate(self, player):
        pass

    def sendgamestate(self, player, seat):
        pass

    def destroy(self):
        pass

class Seat:
    def __init__(self, game, id, required=True):
        if (not isinstance(game, Game)):
            raise Exception, 'game argument must be a Game instance'
        if (not (type(id) in [str, unicode])):
            raise TypeError, 'id argument must be a string'
        ref = game.referee
        if (not ref):
            raise Exception, 'game has no referee value'

        self.referee = ref
        self.id = id
        self.required = bool(required)
        self.ingame = False
        self.playerlist = []

        # This will fail if the referee is already running. You must
        # create all your seats at __init__ time.
        self.referee.addseat(self)

    def getplayerlist(self):
        return self.playerlist

    def isempty(self):
        return not self.playerlist

    def isrequired(self):
        return self.required

    def isingame(self):
        return self.ingame

    def send(self, methname, *args, **keywords):
        self.referee.game.sendseat(self, methname, *args, **keywords)
        
        
class ObjMethodOpset(jabber.rpc.Opset):
    def __init__(self, obj):
        self.object = obj
        
    def __call__(self, sender, callname, *callargs):
        val = getattr(self.object, 'rpc_'+callname, None)
        if (not val):
            raise jabber.rpc.CallNotFound
        self.precondition(sender, callname, '', *callargs)
        return val(sender, *callargs)

# late imports
import referee
from referee import STATE_SETUP, STATE_ACTIVE, STATE_DISRUPTED, STATE_ABANDONED, STATE_SUSPENDED

