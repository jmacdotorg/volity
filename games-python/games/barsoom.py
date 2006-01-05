"""barsoom -- an implementation of Barsoomite Go
    (or, Branches and Twigs and Thorns.)
    Game designed and implemented by Andrew Plotkin.

Two-player:
    
Ruleset URI: <http://eblong.com/zarf/volity/ruleset/barsoom/BarsoomGo.html>
Game URL:    <http://eblong.com/zarf/barsoom-go.html>
Default UI:  <http://volity.org/games/barsoom/barsoom-ui.zip>

Four-player:
    
Ruleset URI: <http://eblong.com/zarf/volity/ruleset/barsoom/BarsoomGo4.html>
Game URL:    <http://eblong.com/zarf/barsoom-go.html>
Default UI:  <http://volity.org/games/barsoom/barsoom4-ui.zip>
"""

from zymb import jabber
import zymb.jabber.rpc
import volity.game

class BarsoomGo(volity.game.Game):

    gamename = 'Two-Player Barsoomite Go'
    gamedescription = 'Barsoomite Go: or, Branches and Twigs and Thorns'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/barsoom/BarsoomGo.html'
    rulesetversion = '1.0'
    websiteurl = 'http://eblong.com/zarf/barsoom-go.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('move_root_square', afoot=False, args=[int, int])
        self.validatecalls('move_null_square', afoot=False, args=[int, int])
        self.validatecalls('move', afoot=True, seated=True,
            args=[int, int, int, int])

        self.whiteseat = GoSeat(self, 'white')
        self.blackseat = GoSeat(self, 'black')

        self.board = None
        self.penalties = None
        self.turn = None

        (self.nullsquarex, self.nullsquarey) = (7, 3)
        (self.rootsquarex, self.rootsquarey) = (3, 1)

    def begingame(self):
        self.board = []
        for ix in range(8):
            self.board.append([None] * 4)
        self.board[self.nullsquarex][self.nullsquarey] = 'null'
        self.board[self.rootsquarex][self.rootsquarey] = 'root'

        self.penalties = []
        
        self.whiteseat.begingame()
        self.blackseat.begingame()
    
        self.turn = self.whiteseat
        self.sendtable('turn', self.turn)
        
    def endgame(self, cancelled):
        self.board = None
        self.penalties = None
        self.turn = None
    
    def sendconfigstate(self, player):
        player.send('set_root_square', '', self.rootsquarex, self.rootsquarey)
        player.send('set_null_square', '', self.nullsquarex, self.nullsquarey)

    def sendgamestate(self, player, playerseat):
        if (self.board):
            for ix in range(len(self.board)):
                ls = self.board[ix]
                for jx in range(len(ls)):
                    val = ls[jx]
                    if (type(val) == tuple):
                        player.send('move', val[0], ix, jx, val[1], val[2])

        if (self.penalties):
            for val in self.penalties:
                player.send('penalty', *val)

        if (self.turn):
            player.send('turn', self.turn)
        
    def derefboard(self, xpos, ypos, dir=None):
        if (dir == 0):
            ypos -= 1
        if (dir == 2):
            ypos += 1
        if (dir == 3):
            xpos -= 1
        if (dir == 1):
            xpos += 1
        if (xpos < 0 or xpos >= 8 or ypos < 0 or ypos >= 4):
            raise ValueError('board position out of bounds')
        return self.board[xpos][ypos]

    # The following methods are RPC handlers.
                
    def rpc_move_root_square(self, sender, xpos, ypos):
        if (xpos < 0 or xpos >= 8 or ypos < 0 or ypos >= 4):
            raise volity.game.FailureToken('game.out_of_bounds')

        if ((self.nullsquarex, self.nullsquarey) == (xpos, ypos)):
            raise volity.game.FailureToken('game.root_null_overlap')

        (self.rootsquarex, self.rootsquarey) = (xpos, ypos)
        
        self.sendtable('set_root_square', sender,
            self.rootsquarex, self.rootsquarey)
        self.unready()

    def rpc_move_null_square(self, sender, xpos, ypos):
        if (xpos < 0 or xpos >= 8 or ypos < 0 or ypos >= 4):
            raise volity.game.FailureToken('game.out_of_bounds')

        if ((self.rootsquarex, self.rootsquarey) == (xpos, ypos)):
            raise volity.game.FailureToken('game.root_null_overlap')

        (self.nullsquarex, self.nullsquarey) = (xpos, ypos)
        
        self.sendtable('set_null_square', sender,
            self.nullsquarex, self.nullsquarey)
        self.unready()

    def rpc_move(self, sender, xpos, ypos, size, dir):
        seat = self.getplayerseat(sender)
        if (not seat):
            # the validator should prevent this from happening
            return

        if (seat != self.turn):
            raise volity.game.FailureToken('volity.not_your_turn')
        if (dir < 0 or dir >= 4):
            raise volity.game.FailureToken('game.invalid_direction')
        if (size < 1 or size > 3):
            raise volity.game.FailureToken('game.invalid_size')
        if (seat.stash[size] <= 0):
            raise volity.game.FailureToken('game.out_of_pieces')
        if (xpos < 0 or xpos >= 8 or ypos < 0 or ypos >= 4):
            raise volity.game.FailureToken('game.out_of_bounds')
        if (self.board[xpos][ypos] != None):
            raise volity.game.FailureToken('game.square_occupied')

        try:
            target = self.derefboard(xpos, ypos, dir)
        except ValueError:
            raise volity.game.FailureToken('game.no_target')
        if (target == None):
            raise volity.game.FailureToken('game.no_target')
        if (target == 'null'):
            raise volity.game.FailureToken('game.null_target')

        penalty = 0
        if (type(target) == tuple):
            (targseat, targsize, targdir) = target
            if (targseat != seat):
                penalty = targsize + size

        seat.stash[size] -= 1
        seat.penalties += penalty
        self.board[xpos][ypos] = (seat, size, dir)

        self.sendtable('move', seat, xpos, ypos, size, dir)
        if (penalty):
            self.penalties.append( (seat, penalty, xpos, ypos, dir) )
            self.sendtable('penalty', seat, penalty, xpos, ypos, dir)

        if (self.whiteseat.empty() and self.blackseat.empty()):
            winner = None
            if (self.whiteseat.penalties < self.blackseat.penalties):
                winner = self.whiteseat
            if (self.whiteseat.penalties > self.blackseat.penalties):
                winner = self.blackseat
            self.gameover(winner)
            return
            
        if (self.turn != self.whiteseat):
            self.turn = self.whiteseat
        else:
            self.turn = self.blackseat
        self.sendtable('turn', self.turn)
            
class BarsoomGo4(volity.game.Game):

    gamename = 'Four-Player Barsoomite Go'
    gamedescription = 'Barsoomite Go: or, Branches and Twigs and Thorns'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/barsoom/BarsoomGo4.html'
    rulesetversion = '1.0'
    websiteurl = 'http://eblong.com/zarf/barsoom-go.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('move_root_square', afoot=False,
            args=[int, int, int])
        self.validatecalls('move_null_square', afoot=False,
            args=[int, int, int])
        self.validatecalls('move', afoot=True, seated=True,
            args=[int, int, int, int])

        GoSeat(self, 'red', 15)
        GoSeat(self, 'blue', 15)
        GoSeat(self, 'yellow', 15)
        GoSeat(self, 'green', 15)

        self.board = None
        self.penalties = None
        self.turn = None

        self.nulls = [ (2,2), (5,5) ]
        self.roots = [ (2,5), (5,2) ]

    def begingame(self):
        self.board = []
        for ix in range(8):
            self.board.append([None] * 8)

        for ix in range(2):
            (xp, yp) = self.nulls[ix]
            self.board[xp][yp] = 'null'
        for ix in range(2):
            (xp, yp) = self.roots[ix]
            self.board[xp][yp] = 'root'

        self.penalties = []

        for seat in self.getseatlist():
            seat.begingame()
    
        self.turn = self.getseat('red')
        self.sendtable('turn', self.turn)
        
    def endgame(self, cancelled):
        self.board = None
        self.penalties = None
        self.turn = None
    
    def sendconfigstate(self, player):
        for ix in range(2):
            (xp, yp) = self.nulls[ix]
            player.send('set_null_square', '', ix, xp, yp)
        for ix in range(2):
            (xp, yp) = self.roots[ix]
            player.send('set_root_square', '', ix, xp, yp)

    def sendgamestate(self, player, playerseat):
        if (self.board):
            for ix in range(len(self.board)):
                ls = self.board[ix]
                for jx in range(len(ls)):
                    val = ls[jx]
                    if (type(val) == tuple):
                        player.send('move', val[0], ix, jx, val[1], val[2])

        if (self.penalties):
            for val in self.penalties:
                player.send('penalty', *val)

        if (self.turn):
            player.send('turn', self.turn)
            
    def derefboard(self, xpos, ypos, dir=None):
        if (dir == 0):
            ypos -= 1
        if (dir == 2):
            ypos += 1
        if (dir == 3):
            xpos -= 1
        if (dir == 1):
            xpos += 1
        if (xpos < 0 or xpos >= 8 or ypos < 0 or ypos >= 8):
            raise ValueError('board position out of bounds')
        return self.board[xpos][ypos]

    # The following methods are RPC handlers.
                
    def rpc_move_root_square(self, sender, num, xpos, ypos):
        if (xpos < 0 or xpos >= 8 or ypos < 0 or ypos >= 8):
            raise volity.game.FailureToken('game.out_of_bounds')
        if (not (num in [0,1])):
            raise volity.game.FailureToken('game.invalid_token')

        for ix in range(2):
            if ((xpos, ypos) == self.nulls[ix]):
                raise volity.game.FailureToken('game.token_overlap')
        for ix in range(2):
            if ((xpos, ypos) == self.roots[ix]):
                raise volity.game.FailureToken('game.token_overlap')

        self.roots[num] = (xpos, ypos)
        
        self.sendtable('set_root_square', sender,
            num, xpos, ypos)
        self.unready()

    def rpc_move_null_square(self, sender, num, xpos, ypos):
        if (xpos < 0 or xpos >= 8 or ypos < 0 or ypos >= 8):
            raise volity.game.FailureToken('game.out_of_bounds')
        if (not (num in [0,1])):
            raise volity.game.FailureToken('game.invalid_token')

        for ix in range(2):
            if ((xpos, ypos) == self.nulls[ix]):
                raise volity.game.FailureToken('game.token_overlap')
        for ix in range(2):
            if ((xpos, ypos) == self.roots[ix]):
                raise volity.game.FailureToken('game.token_overlap')

        self.nulls[num] = (xpos, ypos)
        
        self.sendtable('set_null_square', sender,
            num, xpos, ypos)
        self.unready()

    def rpc_move(self, sender, xpos, ypos, size, dir):
        seat = self.getplayerseat(sender)
        if (not seat):
            # the validator should prevent this from happening
            return

        if (seat != self.turn):
            raise volity.game.FailureToken('volity.not_your_turn')
        if (dir < 0 or dir >= 4):
            raise volity.game.FailureToken('game.invalid_direction')
        if (size < 1 or size > 3):
            raise volity.game.FailureToken('game.invalid_size')
        if (seat.stash[size] <= 0):
            raise volity.game.FailureToken('game.out_of_pieces')
        if (xpos < 0 or xpos >= 8 or ypos < 0 or ypos >= 8):
            raise volity.game.FailureToken('game.out_of_bounds')
        if (self.board[xpos][ypos] != None):
            raise volity.game.FailureToken('game.square_occupied')

        try:
            target = self.derefboard(xpos, ypos, dir)
        except ValueError:
            raise volity.game.FailureToken('game.no_target')
        if (target == None):
            raise volity.game.FailureToken('game.no_target')
        if (target == 'null'):
            raise volity.game.FailureToken('game.null_target')

        penalty = 0
        bonus = 0
        targseat = None
        if (type(target) == tuple):
            (oseat, targsize, targdir) = target
            if (oseat != seat):
                penalty = targsize
                bonus = size
                targseat = oseat

        seat.stash[size] -= 1
        seat.penalties += penalty
        seat.penalties -= bonus
        self.board[xpos][ypos] = (seat, size, dir)

        self.sendtable('move', seat, xpos, ypos, size, dir)
        if (penalty or bonus):
            tup = (seat, penalty, targseat, bonus, xpos, ypos, dir)
            self.penalties.append(tup)
            self.sendtable('penalty', *tup)

        allempty = True
        for seat in self.getseatlist():
            if (not seat.empty()):
                allempty = False
                
        if (allempty):
            winlist = self.sortseats(GoSeat.scorefunction)
            self.gameover(*winlist)
            return

        ls = self.getseatlist()
        pos = ls.index(self.turn)
        self.turn = ls[(pos+1) % len(ls)]
        self.sendtable('turn', self.turn)

            
class GoSeat(volity.game.Seat):
    def __init__(self, game, id, initialscore=0):
        volity.game.Seat.__init__(self, game, id)
        self.stash = None
        self.initialscore = initialscore

    def begingame(self):
        self.stash = [None, 5, 5, 5]
        self.penalties = self.initialscore

    def empty(self):
        return (self.stash[1] + self.stash[2] + self.stash[3] == 0)

    def scorefunction(self):
        return (-self.penalties)
        
