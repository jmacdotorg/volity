"""ttt -- an implementation of Tic Tac Toe
Ruleset URI: <http://volity.org/games/tictactoe>

This is my implementation of Tic Tac Toe.
"""

import random
from zymb import jabber
import zymb.jabber.rpc
import volity.game

class TicTacToe(volity.game.Game):
    """TicTacToe: A game class which plays guess what.
    """

    gamename = 'Zarf\'s Tic Tac Toe'
    gamedescription = 'A Volity.net Tic Tac Toe implementation, by Andrew Plotkin.'
    ruleseturi = 'http://volity.org/games/tictactoe'
    rulesetversion = '2.1'

    # A static list of all winning Tic Tac Toe positions.
    winlist = [
        (0,1,2), (3,4,5), (6,7,8),
        (0,3,6), (1,4,7), (2,5,8),
        (0,4,8), (2,4,6)
    ]
    
    def __init__(self, ref):
        """__init__(self, ref)

        Set up the game object.
        """
        
        volity.game.Game.__init__(self, ref)

        # Set ourself as the opset (so this instance's rpc_* methods are
        # called.)
        self.setopset(self)

        # Set up argument-type checkers and state checkers.
        
        # The mark call takes one integer argument.
        self.validatecalls('mark', args=int)
        
        # This RPC is game-time only...
        self.validatecalls('mark', afoot=True)
        # ...and is limited to seated players.
        self.validatecalls('mark', seated=True)

        # Construct the two seats.
        self.xseat = volity.game.Seat(self, 'x')
        self.oseat = volity.game.Seat(self, 'o')

        # The board will not exist until the game starts.
        self.board = None
        self.turn = None

    def begingame(self):
        """begingame() -> None

        Game-beginning conditions. The new board contains nine None marks,
        and it is X's turn.
        """
        
        self.board = [ None for ix in range(9) ]
        self.turn = self.xseat
        self.sendtable('must_mark', self.turn)
        
    def endgame(self, cancelled):
        """endgame() -> None

        Game-ending conditions. This just cleans up the data structures
        from begingame().
        """
        
        self.board = None
        self.turn = None
    
    def sendgamestate(self, player, seat):
        """sendgamestate(player, seat) -> None

        Send the current game configuration to a player who has just joined
        the table. This is a bunch of mark() calls -- the referee-to-client
        form, which has two arguments. We also send the must_mark() call
        to indicate whose turn it is.

        We ignore the seat argument, because all players (sitting or standing)
        get the same state information. There is no hidden information in
        Tic Tac Toe.
        """
        for ix in range(9):
            if (self.board[ix]):
                player.send('mark', self.board[ix], ix)
        player.send('must_mark', self.turn)

    def rpc_mark(self, sender, location):
        """rpc_mark() -- RPC handler

        A player tells us that he has made a move.

        Note that the argument types have already been checked, so
        *location* is guaranteed to be an integer. The *sender* is always
        the real JID of a player at the table.
        """
        
        # Grab the sender's seat.
        seat = self.getplayerseat(sender)

        # Check whether it's the sender's turn.
        if (self.turn != seat):
            raise volity.game.FailureToken('volity.not_your_turn')

        # Check whether the argument is a legal board square number.
        if (not (location in range(9))):
            raise volity.game.FailureToken('game.invalid_location')

        # Check whether the chosen square is empty.
        if (self.board[location]):
            raise volity.game.FailureToken('game.already_marked')

        # The move is legal. Update the board.
        self.board[location] = seat
        self.sendtable('mark', self.board[location], location)

        # Check whether the game has ended.
        
        for (pos1, pos2, pos3) in self.winlist:
            if (self.board[pos1] != None
                and self.board[pos1] == self.board[pos2]
                and self.board[pos2] == self.board[pos3]):
                
                # All three of these positions have the same mark. That
                # player has won.
                winner = self.board[pos1]
                self.sendtable('win', winner, pos1, pos2, pos3)
                self.gameover(winner)
                return

        unmarked = [ ix for ix in range(9) if (self.board[ix] == None) ]
        if (not unmarked):
            # No positions are unmarked. It's a tie.
            self.sendtable('tie')
            self.gameover(None)
            return

        # The game is not over. Update whose turn it is.
        if (self.turn == self.xseat):
            self.turn = self.oseat
        else:
            self.turn = self.xseat
        self.sendtable('must_mark', self.turn)
            
class RandomBot(volity.bot.Bot):
    """RandomBot: Bot class for playing Tic Tac Toe. This chooses moves
    randomly.

    We have to keep track of the board position, and whose turn it is.
    The board is needed to make legal moves -- even though we're playing
    randomly, we don't want to choose a space which has already been
    marked.
    """
    
    boturi = 'http://volity.org/games/tictactoe/randombot'
    gameclass = TicTacToe

    def __init__(self, act):
        volity.bot.Bot.__init__(self, act)
        self.setopset(self)

        self.board = None
        self.turn = None
                
    def begingame(self):
        """begingame() -> None

        Game-beginning conditions. The new board contains nine None marks.
        (We don't have to set the turn flag, because a must_mark() call will
        arrive next.)
        """
        
        self.board = [ None for ix in range(9) ]
        self.turn = None

    def gamehasbegun(self):
        """gamehasbegun() -> None

        This call arrives in the state-description burst if the game started
        before we showed up. Do the same stuff we do in begingame().
        """
        self.begingame()

    def endgame(self):
        """endgame() -> None

        Game-ending conditions. This just cleans up the data structures
        from begingame().
        """
        
        self.board = None
        self.turn = None

    def unsuspendgame(self):
        """unsuspendgame() -> None

        Game-unsuspend conditions. If the game resumes and it's our move,
        we'd better go. (Some rulesets prescribe that unsuspendgame() is
        followed by a your-move RPC, but Volity TTT is not designed that
        way.)
        """
        
        if (self.turn and self.turn == self.getownseat()):
            self.makemove()
        
    def rpc_mark(self, sender, seat, location):
        """rpc_mark() -- RPC handler

        The referee tells us that someone made a move.
        """
        
        self.board[location] = seat

    def rpc_must_mark(self, sender, seat):
        """rpc_must_mark() -- RPC handler

        The referee tells us that it's someone's turn to move. If
        that's us, we do.
        """
        
        self.turn = self.getseat(seat)
        if (self.turn and self.turn == self.getownseat()):
            self.makemove()

    def makemove(self):
        """makemove() -> None

        Choose a move randomly from the unmarked spaces on the board.
        (If there are no unmarked spaces, this method will choke. But
        it would be a referee bug if we got a must_mark() call for a
        full board, so we don't worry about that.)
        """
        
        unmarked = [ ix for ix in range(9) if (self.board[ix] == None) ]
        location = random.choice(unmarked)
        self.send('mark', location)

