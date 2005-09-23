"""rps -- an implementation of Rock Paper Scissors
Ruleset URI: <http://volity.org/games/rps>

This is my implementation of Rock Paper Scissors.
"""

from zymb import jabber
import zymb.jabber.rpc
import volity.game

class RPS(volity.game.Game):

    gamename = 'Zarf\'s RPS'
    gamedescription = 'An unofficial Volity.net RPS implementation, by Andrew Plotkin.'
    ruleseturi = 'http://volity.org/games/rps' ### doesn't actually follow the ruleset at this address, because the ruleset is out of date.
    rulesetversion = '1.5'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('no_ties', 'best_of', afoot=False)
        self.validatecalls('choose_hand', afoot=True)
        self.validatecalls('no_ties', args=int)
        self.validatecalls('best_of', args=int)
        self.validatecalls('choose_hand', args=str, seated=True)

        self.no_ties = 1
        self.best_of = 1
        
        self.whiteseat = RPSSeat(self, 'white')
        self.blackseat = RPSSeat(self, 'black')

    def begingame(self):
        self.gamecount = 0
        self.whiteseat.hand = None
        self.whiteseat.wins = 0
        self.blackseat.hand = None
        self.blackseat.wins = 0

    def sendconfigstate(self, player):
        player.send('no_ties', '', self.no_ties)
        player.send('best_of', '', self.best_of)

    # The following methods are RPC handlers.
                
    def rpc_no_ties(self, sender, *args):

        val = args[0]
        if (not val in [0,1]):
            raise jabber.rpc.RPCFault(606, 'no_ties must be 0 or 1')

        if (val == self.no_ties):
            return

        self.no_ties = val

        self.sendtable('no_ties', sender, self.no_ties)
        self.unready()

    def rpc_best_of(self, sender, *args):

        val = args[0]
        if (val <= 0 or (val & 1) == 0):
            raise jabber.rpc.RPCFault(606, 'best_of must be positive and odd')

        if (val == self.best_of):
            return

        self.best_of = val

        self.sendtable('best_of', sender, self.best_of)
        self.unready()

    def rpc_choose_hand(self, sender, *args):

        val = args[0]

        if (not val in ['rock', 'paper', 'scissors']):
            raise jabber.rpc.RPCFault(606, 'choose_hand must be "rock", "paper", or "scissors"')

        seat = self.getplayerseat(sender)
        if (not seat):
            # the validator should prevent this from happening
            raise ValueError('sender %s is not playing' % unicode(sender))

        seat.hand = val
        self.queueaction(self.checkoutcome)

    def checkoutcome(self):
        white = self.whiteseat
        black = self.blackseat

        if ((not white.hand) or (not black.hand)):
            # no outcome yet
            return
                
        self.sendtable('player_chose_hand', white.id, white.hand)
        self.sendtable('player_chose_hand', black.id, black.hand)

        if (white.hand == black.hand):
            # tie
            if (self.no_ties):
                self.log.info('players tied, rethrowing')
                white.hand = None
                black.hand = None
                return
            self.log.info('both players won')
            black.wins += 1
            white.wins += 1
        else:
            if ((black.hand == 'rock' and white.hand == 'scissors')
                or (black.hand == 'scissors' and white.hand == 'paper')
                or (black.hand == 'paper' and white.hand == 'rock')):
                winner = black
            else:
                winner = white
            winner.wins += 1
            self.log.info(winner.id + ' won')

        self.gamecount += 1
        white.hand = None
        black.hand = None

        if (self.gamecount < self.best_of):
            return

        if (black.wins > white.wins):
            winner = black
            self.log.info('black wins the match')
        elif (black.wins < white.wins):
            winner = white
            self.log.info('white wins the match')
        else:
            winner = None
            self.log.info('the match is a tie')

        self.gameover(winner)

    
class RPSSeat(volity.game.Seat):
    def __init__(self, game, id):
        volity.game.Seat.__init__(self, game, id)
        self.hand = None
        self.wins = 0


