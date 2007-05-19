"""y: An implementation of Y.
   Y was invented by Claude Shannon.
   This implementation is by Phil Bordelon.

Ruleset URI: <http://thenexusproject.org/non-canon/projects/volity/rulesets/y.html>
Game URI:    <http://en.wikipedia.org/wiki/Y_(game)>
Default UI:  <http://thenexusproject.org/non-canon/projects/volity/uis/y.zip>
"""

##############################################################################
##
## Hex Server Implementation
## Copyright 2006 Phil Bordelon <phil@thenexusproject.org>
##
## This program is free software; you can redistribute it and/or modify
## it under the terms of the GNU General Public License as published by
## the Free Software Foundation, version 2 (only) of the license.
## 
## This program is distributed in the hope that it will be useful,
## but WITHOUT ANY WARRANTY; without even the implied warranty of
## MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
## GNU General Public License for more details.
## 
## You should have received a copy of the GNU General Public License
## along with this program; if not, write to the Free Software
## Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
##############################################################################

import random
from zymb import jabber
import zymb.jabber.rpc
import volity.game

# Set minimum and maximum sizes for the board.
Y_MIN_SIZE = 2
Y_MAX_SIZE = 52

# Deltas for hexagonal boards.  This board is angled differently than Hex's
# board is, so the adjacency list is different.
#
#      
#      . 0
#     . . 1
#    . . . 2
#   . . . . 3
#  0 1 2 3
#
# (1, 2) is adjacent to (1, 1), (2, 2), (0, 2), (1, 3), (2, 3), and (0, 1).

HEX_DELTAS = ((0, -1), (0, 1), (-1, 0), (1, 0), (1, 1), (-1, -1))
#                       These are different. ------^--------^

INVALID_LOCATION_STRING = "INVALID_LOCATION"

class Y(volity.game.Game):

   gamename = 'Y'
   gamedescription = 'Y'
   ruleseturi = 'http://thenexusproject.org/non-canon/projects/volity/rulesets/y.html'
   rulesetversion = '1.0'
   websiteurl = 'http://en.wikipedia.org/wiki/Y_(game)'

   def __init__(self, ref):
      volity.game.Game.__init__(self, ref)

      self.setopset(self)

      # Client -> Referee validations.
      self.validatecalls('adjust_size', afoot=False, args=int)
      self.validatecalls('make_move', afoot=True, seated=True, args=[int, int])
      self.validatecalls('swap', afoot=True, seated=True, args=None)
      self.validatecalls('resign', afoot=True, seated=True, args=None)
       

      self.whiteseat = volity.game.Seat(self, 'white')
      self.blackseat = volity.game.Seat(self, 'black')

      # Set some default values.

      self.board = None
      self.size = 19
      self.turn = None


   def begingame(self):

      # Build the board.
      self.board = []
      for x in range(self.size):
         self.board.append([None] * self.size)
         # This builds a square board.  We have to mark some of the
         # locations on the board as invalid.  For a given x coordinate,
         # y values under x are unplayable locations.  (The last column
         # has only one piece, and it's in the bottom row.)
         for y in range(x):
            self.board[x][y] = INVALID_LOCATION_STRING

      # Set the move number to one; we have to keep track of this for the
      # swap rule.
      self.move_number = 1
      self.move_list = []

      self.turn = self.whiteseat
      self.sendtable('set_size', '', self.size)
      self.sendtable('turn', self.turn)

   def endgame(self, cancelled):
      self.board = None
      self.turn = None
    
   def unsuspendgame(self):
      self.sendtable('turn', self.turn)
        
   def sendconfigstate(self, player):
      player.send('set_size', '', self.size)

   def sendgamestate(self, player, playerseat):
      if (self.board):
         for move in self.move_list:
            type, seat, x, y, number = move
            player.send(type, seat, x, y, number)
            
      if (self.turn):
         player.send('turn', self.turn)
        
   # The following methods are RPC handlers.
                
   def rpc_adjust_size(self, sender, size):
      if (self.getstate() == volity.game.STATE_SETUP):
         if (size < Y_MIN_SIZE or size > Y_MAX_SIZE):
            # Bzzt.  Try again.
            raise volity.game.FailureToken('game.invalid_board_size')

         # Else, adjust the size.
         self.size = size

         self.sendtable('set_size', sender, self.size)
         self.unready()

   def rpc_make_move(self, sender, x, y):
      seat = self.getplayerseat(sender)
      if (not seat):
         # the validator should prevent this from happening
         return

      # Check various failure cases.
      if (seat != self.turn):
         raise volity.game.FailureToken('volity.not_your_turn')
         
      # Given a coordinate (x, y), x can never be greater than y, as
      # the number of pieces on the first row is 1, second row is 2,
      # and so on.  Similarly, y must be more than x, otherwise it
      # would be "above" the valid locations on the board.
      if (x < 0 or x > y or y < x or y >= self.size):
         raise volity.game.FailureToken('game.out_of_bounds')
      if self.board[x][y]:
         raise volity.game.FailureToken('game.cell_occupied')

      # Okay, that all passed.  Actually put the piece down.
      self.board[x][y] = seat

      self.sendtable('move', seat, x, y, self.move_number)
      self.move_list.append(('move', seat, x, y, self.move_number))

      # If this is the first move of the game, record the move; this
      # is useful for if the second player wants to swap.

      if self.move_number == 1:
         self.first_x = x
         self.first_y = y
         
      # Increment the move count.
      self.move_number += 1

      # See if the game is over thanks to that move.
      winner = self.isGameComplete()
      if winner:

         # Yup.  Signal and game over, man!
         self.sendtable('over', winner)
         self.gameover([winner])
      else:
         # Nope.  Next player's turn!
      
         if (self.turn != self.whiteseat):
            self.turn = self.whiteseat
         else:
            self.turn = self.blackseat
         self.sendtable('turn', self.turn)

   def rpc_swap(self, sender):

      # The only time a swap is valid is if:
      #    * Move number is 2 (after White's first move)
      #    * Sender is 'black'

      seat = self.getplayerseat(sender)
      if (not seat):
         # the validator should prevent this from happening
         return

      # Check various failure cases.
      if (seat != self.turn):
         raise volity.game.FailureToken('volity.not_your_turn')
      if (seat != self.blackseat):
         raise volity.game.FailureToken('game.cannot_swap')
      if (self.move_number != 2):
         raise volity.game.FailureToken('game.cannot_swap')

      # Okay, the player can swap.  In Y, that's just changing the
      # piece directly (unlike Hex, where we have to translate it.)

      self.sendtable('remove', seat, self.first_x, self.first_y,
         self.move_number)
      self.sendtable('move', seat, self.first_x, self.first_y,
         self.move_number)

      self.move_list.append(('remove', seat, self.first_x, self.first_y,
         self.move_number))
      self.move_list.append(('move', seat, self.first_x, self.first_y,
         self.move_number))

      self.board[self.first_x][self.first_y] = seat

      # Now it's White's turn again.

      self.move_number += 1
      self.turn = self.whiteseat
      self.sendtable('turn', self.turn)

   def rpc_resign(self, sender):

      seat = self.getplayerseat(sender)
      if (not seat):
         # the validator should prevent this from happening
         return

      # Well, then, game's over!  Wasn't that fun?
      if seat == self.whiteseat:
         winner = self.blackseat
      else:
         winner = self.whiteseat

      self.sendtable('over', winner)
      self.gameover(winner)

   def isGameComplete(self):

      # Calculating the winner in Y isn't quite as trivial as it is
      # in Hex.  What we can do is:
      #    - Pick a side.
      #    - For each piece on that side, see if it's connected to
      #      both other sides.  If so, that player is a winner.
      #    - If not, there is no winner (as winners must connect all
      #      three sides).
      self.winner = None
      
      self.adjacency_board = []
      for i in range (self.size):
         self.adjacency_board.append([None] * self.size)

      for i in range (self.size):
         
         # Don't bother running against an empty cell.
         if self.board[0][i]:

            # Since we check each piece separately (regardless of
            # player), we need to reinitialize these variables each time.
            self.touch_bottom = False

            # self.touch_left = True, of course, since we're running on
            # the left side of the board.  No need to even use it as a
            # variable.
            self.touch_right = False
            self.updateAdjacencyBoard(0, i, self.board[0][i])

            # If that iteration found a winner, return that winner.
            if self.winner:
               return self.winner

      # No winner yet.
      return None

   def updateAdjacencyBoard(self, x, y, seat):

      # If we've already determined a winner, return immediately.
      if self.winner:
         return

      # If we're off the board, return immediately.
      if (x < 0 or x > y or y < x or y >= self.size):
         return

      # If we've already visited this location (for either player),
      # return immediately.
      if self.adjacency_board[x][y]:
         return

      # If it's an empty cell or a cell of the other player, return.
      this_cell = self.board[x][y]
      if (this_cell != seat):
         return

      # Okay, it's this player's cell.  Mark it.
      self.adjacency_board[x][y] = seat

      # If we're on either the bottom edge or the right edge of the
      # board, note that, and check for the winning condition.
      if (y == self.size - 1):
         self.touch_bottom = True
      if (x == y):
         self.touch_right = True

      if self.touch_bottom and self.touch_right:
         self.winner = seat
         return

      # Not a winner yet; run blindly on the six cells surrounding us.
      for x_delta, y_delta in HEX_DELTAS:
         self.updateAdjacencyBoard (x + x_delta, y + y_delta, seat)
