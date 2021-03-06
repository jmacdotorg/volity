"""hex: An implementation of Hex.
   Hex was invented independently by Piet Hein and John Nash.
   This implementation is by Phil Bordelon.

Ruleset URI: <http://thenexusproject.org/non-canon/projects/volity/rulesets/hex.html>
Game URI:    <http://en.wikipedia.org/wiki/Go_(board_game)>
Default UI:  <http://thenexusproject.org/non-canon/projects/volity/uis/hex.zip>
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
HEX_MIN_SIZE = 4
HEX_MAX_SIZE = 26

# Deltas for hexagonal boards.
#
# 0 1 2
#  . . . 0
#   . . . 1
#    . . . 2
#
# (1, 1) is adjacent to (1, 0), (2, 0), (0, 1), (2, 1), (0, 2), and (1, 2).

HEX_DELTAS = ((0, -1), (0, 1), (-1, 0), (1, 0), (-1, 1), (1, -1))

class Hex(volity.game.Game):

   gamename = 'Hex'
   gamedescription = 'Hex'
   ruleseturi = 'http://thenexusproject.org/non-canon/projects/volity/rulesets/hex.html'
   rulesetversion = '1.0'
   websiteurl = 'http://en.wikipedia.org/wiki/Go_(board_game)'

   def __init__(self, ref):
      volity.game.Game.__init__(self, ref)

      self.setopset(self)

      # Client -> Referee validations.
      self.validatecalls('adjust_size', afoot=False, args=int)
      self.validatecalls('toggle_head_start', afoot=False, args=None)
      self.validatecalls('make_move', afoot=True, seated=True, args=[int, int])
      self.validatecalls('swap', afoot=True, seated=True, args=None)
      self.validatecalls('resign', afoot=True, seated=True, args=None)
       

      self.whiteseat = volity.game.Seat(self, 'white')
      self.blackseat = volity.game.Seat(self, 'black')

      # Set some default values.

      self.board = None
      self.size = 13
      self.turn = None
      self.head_start = False


   def begingame(self):

      # Build the board.
      self.board = []
      for x in range(self.size):
         self.board.append([None] * self.size)

      self.sendtable('set_size', '', self.size)

      # Set the move number to one; we have to keep track of this for the
      # swap rule.
      self.move_number = 1
      self.move_list = []

      # If this is a Head Start game, make the four opening moves.
      if self.head_start:
         middle = self.size / 2
         self.board[0][middle] = self.blackseat;
         self.board[self.size - 1][middle] = self.blackseat;
         self.board[middle][0] = self.whiteseat;
         self.board[middle][self.size - 1] = self.whiteseat;

         # Send these moves.  Even if it's a Kriegspiel game, we should
         # send them.  They're known.

         self.sendtable('move', self.blackseat, 0, middle, 0)
         self.sendtable('move', self.blackseat, self.size - 1, middle, 0)
         self.sendtable('move', self.whiteseat, middle, 0, 0)
         self.sendtable('move', self.whiteseat, middle, self.size - 1, 0)

         self.move_list.append(('move', self.blackseat, 0, middle, 0))
         self.move_list.append(('move', self.blackseat, self.size - 1, middle, 0))
         self.move_list.append(('move', self.whiteseat, middle, 0, 0))
         self.move_list.append(('move', self.whiteseat, middle, self.size - 1, 0))


      self.turn = self.whiteseat
      self.sendtable('turn', self.turn)

   def endgame(self, cancelled):
      self.board = None
      self.turn = None
    
   def unsuspendgame(self):
      self.sendtable('turn', self.turn)
        
   def sendconfigstate(self, player):
      player.send('set_size', '', self.size)
      player.send('head_start', '', self.head_start)

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
         if (size < HEX_MIN_SIZE or size > HEX_MAX_SIZE):
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
      if (x < 0 or x >= self.size or y < 0 or y >= self.size):
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

      # Okay, the player can swap.  To do this, we have to change
      # the first piece to its equivalent for the second player.
      # Thanks to the dual nature of Hex's goals, this is as simple as
      # swapping the X and Y values.  Lo, magic!
      new_x = self.first_y
      new_y = self.first_x
      
      self.sendtable('remove', seat, self.first_x, self.first_y,
         self.move_number)
      self.sendtable('move', seat, new_x, new_y, self.move_number)

      self.move_list.append(('remove', seat, self.first_x, self.first_y,
         self.move_number))
      self.move_list.append(('move', seat, new_x, new_y, self.move_number))

      self.board[self.first_x][self.first_y] = None
      self.board[new_x][new_y] = seat

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

   def rpc_toggle_head_start(self, sender):
      if (self.getstate() == volity.game.STATE_SETUP):
         if self.head_start:
            self.head_start = False
         else:
            self.head_start = True
         self.sendtable('head_start', sender, self.head_start)
         self.unready()

   def isGameComplete(self):

      # All right.  Hex has the property such that there must always be,
      # at most, a single winner.  Not only that, but there's a fairly
      # simple algorithm for finding a winner:
      # * Start on one edge.
      # * Recursively mark pieces of the same colour adjacent to that one.
      # * If you're about to mark a piece on the opposite edge, there's a
      #   winner!
      # You only have to run this algorithm on two edges, one for each
      # player.  Which is precisely what we're going to do.

      self.winner = None
      
      self.adjacency_board = []
      for i in range (self.size):
         self.adjacency_board.append([None] * self.size)

      for i in range (self.size):
         self.updateAdjacencyBoard(0, i, self.whiteseat)

      # If White won, no reason to keep going.
      if self.winner:
         return self.winner

      # Nope; how about Black?
      for i in range (self.size):
         self.updateAdjacencyBoard(i, 0, self.blackseat)

      # Either black won, or there's no winner.  Either way, self.winner
      # holds the status.  Return it.
      return self.winner

   def updateAdjacencyBoard(self, x, y, seat):

      # If we've already determined a winner, return immediately.
      if self.winner:
         return

      # If we're off the board, return immediately.
      if (x < 0 or x >= self.size or y < 0 or y >= self.size):
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

      # If we're on the "winning edge" for the player, success!  They have
      # won.
      if ((seat == self.whiteseat and x == self.size - 1) or
          (seat == self.blackseat and y == self.size - 1)):
          self.winner = seat
          return

      # Otherwise, run blindly on the six cells surrounding us.
      for x_delta, y_delta in HEX_DELTAS:
         self.updateAdjacencyBoard (x + x_delta, y + y_delta, seat)
