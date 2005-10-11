"""rps -- an implementation of Rock Paper Scissors
Ruleset URI: <http://volity.org/games/rps>

This is my implementation of Rock Paper Scissors.
"""

import random
from zymb import jabber
import zymb.jabber.rpc
import volity.game

class RPS(volity.game.Game):

    gamename = 'Zarf\'s RPS'
    gamedescription = 'An unofficial Volity.net RPS implementation, by Andrew Plotkin.'
    ruleseturi = 'http://volity.org/games/rps' ### doesn't actually follow the ruleset at this address, because the ruleset is out of date.
    rulesetversion = '1.5'

    def __init__(self, ref):
        """__init__(self, ref)

        Set up the RPS game.
        """
        
        volity.game.Game.__init__(self, ref)

        # Set ourself as the opset (so this instance's rpc_* methods are
        # called.)
        self.setopset(self)

        # Set up argument-type checkers and state checkers.

        # These two RPCs are setup-time only. (Not allowed during suspension.)
        self.validatecalls('no_ties', 'best_of', state=volity.game.STATE_SETUP)
        
        # This RPC is game-time only.
        self.validatecalls('choose_hand', afoot=True)
        
        # Add argument-checking conditions. The choose_hand call is also
        # limited to seated players.
        self.validatecalls('choose_hand', args=str, seated=True)
        self.validatecalls('no_ties', args=int)
        self.validatecalls('best_of', args=int)

        # Initial default values.
        self.no_ties = 1
        self.best_of = 1

        # Construct the two seats. These are RPSSeat objects, our customized
        # subclass of Seat.
        self.whiteseat = RPSSeat(self, 'white')
        self.blackseat = RPSSeat(self, 'black')

    def begingame(self):
        """begingame() -> None

        Game-beginning conditions.
        """
        self.gamecount = 0
        self.whiteseat.hand = None
        self.whiteseat.wins = 0
        self.blackseat.hand = None
        self.blackseat.wins = 0

    def sendconfigstate(self, player):
        """sendconfigstate(player) -> None

        Send the current game configuration to a player who has just joined
        the table.
        """
        player.send('no_ties', '', self.no_ties)
        player.send('best_of', '', self.best_of)

    # The following methods are RPC handlers. Note that the argument types
    # have already been checked, so we can pull arguments out of the *args*
    # array with no fear of an IndexError or TypeError. The *sender* is
    # always the real JID of a player at the table.
                
    def rpc_no_ties(self, sender, *args):
        """rpc_no_ties(int)

        Configure the game to allow or disallow ties. Notify the players
        of the change, and mark them all unready.
        """

        val = args[0]
        if (not val in [0,1]):
            raise jabber.rpc.RPCFault(606, 'no_ties must be 0 or 1')

        if (val == self.no_ties):
            return

        self.no_ties = val

        self.sendtable('no_ties', sender, self.no_ties)
        self.unready()

    def rpc_best_of(self, sender, *args):
        """rpc_best_of(int)

        Configure the game to "best of N", where N is odd. Notify the players
        of the change, and mark them all unready.
        """

        val = args[0]
        if (val <= 0 or (val & 1) == 0):
            raise jabber.rpc.RPCFault(606, 'best_of must be positive and odd')

        if (val == self.best_of):
            return

        self.best_of = val

        self.sendtable('best_of', sender, self.best_of)
        self.unready()

    def rpc_choose_hand(self, sender, *args):
        """rpc_choose_hand(move)

        The *move* is a string, which must be 'rock', 'paper', or 'scissors'.
        After each move, we queue the checkoutcome() method, which checks
        to see if the game is over. (We queue the method rather than calling
        it, because we don't want the end-of-game operation to occur inside
        the RPC handler.)
        """

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
        """checkoutcome() -> None

        Check the game state; see if the game is over, and who has won.
        This is invoked after every player move.
        """
        white = self.whiteseat
        black = self.blackseat

        if ((not white.hand) or (not black.hand)):
            # no outcome yet
            return

        # We have an outcome, so make all the moves public.
                        
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
            # More rounds still to go.
            return

        # We've completed best_of N rounds, so the game is over.
            
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
    """RPSSeat: A Seat class which tracks each player's pending move, and
    how many wins he has (if best_of is greater than 1).
    """
    def __init__(self, game, id):
        volity.game.Seat.__init__(self, game, id)
        self.hand = None
        self.wins = 0


class RPSBot(volity.bot.Bot):
    """RPSBot: A bot to play Rock Paper Scissors. It plays randomly, so
    good luck beating it.

    ###BUG: This bot does not recognize tie situations; it only makes one move
    per game. If a tie occurs, the game will get stuck. This bot also does
    not recognize the game.best_of and game.no_ties RPCs, but since the UI
    doesn't either, I don't sweat it.
    """
    
    gameclass = RPS

    def begingame(self):
        """begingame() -> None

        Game on? Make a move.
        """
        
        val = self.choosehand()
        self.send('choose_hand', val)

    def resumegame(self):
        """begingame() -> None

        Game resumed? Make a move. (A move might already have been made before
        the suspension -- if the bot was seated, it definitely moved. But
        we don't care.)
        """
        
        val = self.choosehand()
        self.send('choose_hand', val)
        
    def choosehand(self):
        """choosehand() -> str

        Choose a move. Returns 'rock', 'paper', or 'scissors'. This method
        is broken out so that subclasses of the Bot can have different
        strategies.
        """
        return random.choice(['rock', 'paper', 'scissors'])

class RPSScissorsBot(RPSBot):
    """Designed to beat PaperBot.
    """
    def choosehand(self):
        return 'scissors'

class RPSPaperBot(RPSBot):
    """Designed to beat RockBot.
    """
    def choosehand(self):
        return 'paper'

class RPSRockBot(RPSBot):
    """Good old rock! Nothing beats rock.
    """
    def choosehand(self):
        return 'rock'

