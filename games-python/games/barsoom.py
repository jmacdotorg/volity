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

import random
from zymb import jabber
import zymb.jabber.rpc
import volity.game

class BarsoomGo2(volity.game.Game):

    gamename = 'Two-Player Barsoomite Go'
    gamedescription = 'Barsoomite Go: or, Branches and Twigs and Thorns'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/barsoom/BarsoomGo.html'
    rulesetversion = '2.0'
    websiteurl = 'http://eblong.com/zarf/barsoom-go.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('move_root_square', afoot=False, args=[int, int])
        self.validatecalls('move_null_square', afoot=False, args=[int, int])
        self.validatecalls('move', afoot=True, seated=True,
            args=[int, int, int, int])
        self.validatecalls('debug_stash_size', admin=True, args=int)

        self.stashsize = 5

        self.whiteseat = GoSeat(self, 'white')
        self.blackseat = GoSeat(self, 'black')

        self.board = None
        self.bonuses = None
        self.turn = None

        (self.nullsquarex, self.nullsquarey) = (7, 3)
        (self.rootsquarex, self.rootsquarey) = (3, 1)

    def begingame(self):
        self.board = []
        for ix in range(8):
            self.board.append([None] * 4)
        self.board[self.nullsquarex][self.nullsquarey] = 'null'
        self.board[self.rootsquarex][self.rootsquarey] = 'root'

        self.bonuses = []
        
        self.whiteseat.begingame()
        self.blackseat.begingame()
    
        self.turn = self.whiteseat
        self.sendtable('turn', self.turn)
        
    def endgame(self, cancelled):
        self.board = None
        self.bonuses = None
        self.turn = None
    
    def unsuspendgame(self):
        self.sendtable('turn', self.turn)
        
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

        if (self.bonuses):
            for val in self.bonuses:
                player.send('bonus', *val)

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

        bonus = 0
        if (type(target) == tuple):
            (targseat, targsize, targdir) = target
            if (targseat != seat):
                bonus = targsize + size

        seat.stash[size] -= 1
        if (bonus):
            targseat.bonuses += bonus
        self.board[xpos][ypos] = (seat, size, dir)

        self.sendtable('move', seat, xpos, ypos, size, dir)
        if (bonus):
            tup = (targseat, bonus, xpos, ypos, dir)
            self.bonuses.append(tup)
            self.sendtable('bonus', *tup)

        if (self.whiteseat.empty() and self.blackseat.empty()):
            winlist = self.sortseats(GoSeat.scorefunction)
            self.gameover(*winlist)
            return
            
        if (self.turn != self.whiteseat):
            self.turn = self.whiteseat
        else:
            self.turn = self.blackseat
        self.sendtable('turn', self.turn)
            
    def rpc_debug_stash_size(self, sender, val):
        self.stashsize = val
    
class BarsoomGo4(volity.game.Game):

    gamename = 'Four-Player Barsoomite Go'
    gamedescription = 'Barsoomite Go: or, Branches and Twigs and Thorns'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/barsoom/BarsoomGo4.html'
    rulesetversion = '2.0'
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
        self.validatecalls('debug_stash_size', admin=True, args=int)

        self.stashsize = 5

        GoSeat(self, 'red', 15)
        GoSeat(self, 'blue', 15)
        GoSeat(self, 'yellow', 15)
        GoSeat(self, 'green', 15)

        self.board = None
        self.bonuses = None
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

        self.bonuses = []

        for seat in self.getseatlist():
            seat.begingame()
    
        self.turn = self.getseat('red')
        self.sendtable('turn', self.turn)
        
    def endgame(self, cancelled):
        self.board = None
        self.bonuses = None
        self.turn = None
    
    def unsuspendgame(self):
        self.sendtable('turn', self.turn)
        
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

        if (self.bonuses):
            for val in self.bonuses:
                player.send('bonus', *val)

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

        bonus = 0
        penalty = 0
        targseat = None
        if (type(target) == tuple):
            (oseat, targsize, targdir) = target
            if (oseat != seat):
                penalty = targsize
                bonus = size
                targseat = oseat

        seat.stash[size] -= 1
        if (penalty or bonus):
            targseat.bonuses += bonus
            seat.bonuses -= penalty
        self.board[xpos][ypos] = (seat, size, dir)

        self.sendtable('move', seat, xpos, ypos, size, dir)
        if (penalty or bonus):
            tup = (targseat, bonus, seat, penalty, xpos, ypos, dir)
            self.bonuses.append(tup)
            self.sendtable('bonus', *tup)

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

    def rpc_debug_stash_size(self, sender, val):
        self.stashsize = val
            
class GoSeat(volity.game.Seat):
    def __init__(self, game, id, initialscore=0):
        volity.game.Seat.__init__(self, game, id)
        self.stash = None
        self.initialscore = initialscore

    def begingame(self):
        val = self.getgame().stashsize
        self.stash = [None, val, val, val]
        self.bonuses = self.initialscore

    def empty(self):
        return (self.stash[1] + self.stash[2] + self.stash[3] == 0)

    def scorefunction(self):
        return (self.bonuses)
        

class BarsoomBot(volity.bot.Bot):
    boardsize = None

    def __init__(self, act):
        volity.bot.Bot.__init__(self, act)
        self.setopset(self)

        self.board = None
        self.nulls = list(self.initialnulls)
        self.roots = list(self.initialroots)

        self.turn = None
                
    def begingame(self):
        self.tryinitgame() ### lame

    def tryinitgame(self):
        ### lame but necessary test
        if (self.board):
            return
            
        self.board = Board(*self.boardsize)
        for (xp,yp) in self.roots:
            self.board.set(xp, yp, 'root')
        for (xp,yp) in self.nulls:
            self.board.set(xp, yp, 'null')

        for seat in self.getseatlist():
            self.board.scores[seat.id] = self.initialscore
            self.board.stashes[seat.id] = [None, 5, 5, 5]

        self.turn = None

        #print '### begingame'
        #self.board.dump() ###

    def endgame(self):
        self.board = None
        self.turn = None

    def rpc_move(self, sender, seat, xp, yp, size, dir):
        self.tryinitgame()
        self.board.set(xp, yp, (self.getseat(seat), size))
        self.board.stashes[seat][size] -= 1

    def rpc_turn(self, sender, seat):
        self.turn = self.getseat(seat)
        if (self.turn and self.turn == self.getownseat()):
            #print '### I am supposed to move now.'
            #self.board.dump() ###
            ls = self.allmoves(self.board, self.turn)
            if (ls):
                mmove = self.choosemove(self.board, self.turn, ls)
                (movexp, moveyp, movedir, movepain, movesize) = mmove
                self.send('move', movexp, moveyp, movesize, movedir)

    def allmoves(self, board, turn):
        stash = board.stashes[turn.id]
        if (stash[1]+stash[2]+stash[3] == 0):
            return []
            
        moves = []
        
        for ix in range(board.width):
            for jx in range(board.height):
                val = board.get(ix, jx)
                if (val):
                    continue

                pain = 10
                paindir = None
                for dx in range(4):
                    val = board.get(ix, jx, dx)
                    if (val == 'root'):
                        pain = 0
                        paindir = dx
                        break
                    if (type(val) == tuple):
                        (valseat, valsize) = val
                        if (valseat == turn):
                            pain = 0
                            paindir = dx
                            break
                        if (valsize < pain):
                            pain = valsize
                            paindir = dx

                if (pain < 10):
                    tup = (ix, jx, paindir, pain)
                    moves.append(tup)

        return moves
                    
    def choosemove(self, board, turn, moves):
        stash = board.stashes[turn.id]

        minpain = min( [move[3] for move in moves] )
        goodmoves = [ move for move in moves if move[3] == minpain ]
        move = random.choice(goodmoves)
        
        pieces = ([1] * stash[1]) + ([2] * stash[2]) + ([3] * stash[3])
        if (minpain == 0):
            piece = random.choice(pieces)
        else:
            piece = pieces[0]
        return move + (piece,)

class BarsoomBot2(BarsoomBot):
    gameclass = BarsoomGo2

    boardsize = (8,4)
    initialscore = 0
    initialnulls = [ (7,3) ]
    initialroots = [ (3,1) ]

    def rpc_set_root_square(self, sender, jid, xp, yp):
        self.roots[0] = (xp, yp)

    def rpc_set_null_square(self, sender, jid, xp, yp):
        self.nulls[0] = (xp, yp)

    def rpc_bonus(self, sender, seat, bonus, xp, yp, dir):
        self.board.addscore(seat, bonus)

class BarsoomBot4(BarsoomBot):
    gameclass = BarsoomGo4

    boardsize = (8,8)
    initialscore = 15
    initialnulls = [ (2,2), (5,5) ]
    initialroots = [ (2,5), (5,2) ]

    def rpc_set_root_square(self, sender, jid, num, xp, yp):
        self.roots[num] = (xp, yp)

    def rpc_set_null_square(self, sender, jid, num, xp, yp):
        self.nulls[num] = (xp, yp)

    def rpc_bonus(self, sender, bonusseat, bonusval, penalseat, penalval,
        xp, yp, dir):
        self.board.addscore(bonusseat, bonusval)
        self.board.addscore(penalseat, -penalval)

class Board:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.dat = []
        for ix in range(width):
            arr = [None] * height
            self.dat.append(arr)
        self.scores = {}
        self.stashes = {}

    def get(self, xp, yp, dir=None):
        if (dir != None):
            if (dir == 0):
                yp -= 1
            elif (dir == 1):
                xp += 1
            elif (dir == 2):
                yp += 1
            elif (dir == 3):
                xp -= 1
        if (xp < 0 or yp < 0 or xp >= self.width or yp >= self.height):
            return 'edge'
        return self.dat[xp][yp]

    def set(self, xp, yp, val):
        self.dat[xp][yp] = val

    def getscore(self, seat):
        if (isinstance(seat, GoSeat)):
            seat = seat.id
        return self.scores[seat]

    def addscore(self, seat, delta):
        if (isinstance(seat, GoSeat)):
            seat = seat.id
        self.scores[seat] += delta

    def dump(self):
        print self.scores
        for jx in range(self.height):
            ln = ''
            for ix in range(self.width):
                ln += ' '
                val = self.get(ix, jx)
                if (not val):
                    ln += '..'
                elif (val == 'root'):
                    ln += '++'
                elif (val == 'null'):
                    ln += '--'
                else:
                    (seat, size) = val
                    ln += seat.id[0] + str(size)
            print ln

