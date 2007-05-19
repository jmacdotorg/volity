"""frames: An implementation of Frames.
   Frames is a simultaneous-move abstract strategy game invented by Marcos Donnantuoni.
   This implementation is by Phil Bordelon.

Ruleset URI: <http://thenexusproject.org/non-canon/projects/volity/rulesets/frames.html>
Game URI:    <http://boardgames.about.com/od/freesimultaneous/a/frames.htm>
Default UI:  <http://thenexusproject.org/non-canon/projects/volity/uis/frames.zip>
"""

##############################################################################
##
## Frames Server Implementation
## Copyright 2006 Phil Bordelon <phil@thenexusproject.org>
##
## This program is free software; you can redistribute it and/or modify
## it under the terms of the GNU General Public License as published by
## the Free Software Foundation; version 2 (only) of the License.
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
BOARD_MIN_SIZE = 5
BOARD_MAX_SIZE = 26

# Define white, black, and neutral pieces.
BLACK_PIECE = 'black'
WHITE_PIECE = 'white'
NEUTRAL_PIECE = 'neutral'

class Frames(volity.game.Game):

   gamename = 'Frames'
   gamedescription = 'Frames'
   ruleseturi = 'http://thenexusproject.org/non-canon/projects/volity/rulesets/frames.html'
   rulesetversion = '1.0'
   websiteurl = 'http://boardgames.about.com/od/freesimultaneous/a/frames.htm'

   def __init__(self, ref):
      volity.game.Game.__init__(self, ref)

      self.setopset(self)

      # Client -> Referee validations.
      self.validatecalls('adjust_size', afoot=False, args=int)
      self.validatecalls('adjust_goal', afoot=False, args=int)
      self.validatecalls('make_move', afoot=True, seated=True, args=[int, int])
      self.validatecalls('resign', afoot=True, seated=True, args=None)
       

      self.whiteseat = volity.game.Seat(self, 'white')
      self.blackseat = volity.game.Seat(self, 'black')

      # Set some default values.

      self.board = None
      self.size = 19
      self.goal = 10

   def begingame(self):

      # Build the board.
      self.board = []
      for x in range(self.size):
         self.board.append([None] * self.size)

      self.sendtable('set_size', '', self.size)
      self.sendtable('set_goal', '', self.goal)

      # Set the move number to one.
      self.move_number = 1
      self.move_list = []

      self.white_moved = False
      self.black_moved = False
      self.white_score = 0
      self.black_score = 0
      self.sendtable('turn')

   def endgame(self, cancelled):
      self.board = None
      self.turn = None
    
   def unsuspendgame(self):
      self.sendtable('turn')
        
   def sendconfigstate(self, player):
      player.send('set_size', '', self.size)
      player.send('set_goal', '', self.goal)

   def sendgamestate(self, player, playerseat):
      if (self.board):
         for move in self.move_list:
            seat, x, y, number = move
            player.send(seat, x, y, number)
            
      if (self.turn):
         player.send('turn')
         player.send('score', whiteseat, white_score)
         player.send('score', blackseat, black_score)
        
   # The following methods are RPC handlers.
                
   def rpc_adjust_size(self, sender, size):
      if (self.getstate() == volity.game.STATE_SETUP):
         if (size < BOARD_MIN_SIZE or size > BOARD_MAX_SIZE):
            # Bzzt.  Try again.
            raise volity.game.FailureToken('game.invalid_board_size')

         # Else, adjust the size.
         self.size = size

         self.sendtable('set_size', sender, self.size)

         # We may also have to adjust the goal, if it's too large.
         if self.goal > self.size:
            self.goal = self.size
            self.sendtable('set_goal', '', self.goal)

         self.unready()

   def rpc_make_move(self, sender, x, y):
      seat = self.getplayerseat(sender)
      game_over = False
      if (not seat):
         # the validator should prevent this from happening
         return

      # Check various failure cases.
      if (((seat == self.whiteseat) and self.white_moved) or
       ((seat == self.blackseat) and self.black_moved)):
         raise volity.game.FailureToken('game.already_moved')
      if (x < 0 or x >= self.size or y < 0 or y >= self.size):
         raise volity.game.FailureToken('game.out_of_bounds')
      if self.board[x][y]:
         raise volity.game.FailureToken('game.cell_occupied')

      # Okay, that's okay.  Now let's record the piece, note
      # who moved, and send an RPC to that effect.
      if seat == self.blackseat:
         self.black_move = (x, y)
         self.black_moved = True
      else:
         self.white_move = (x, y)
         self.white_moved = True

      self.move_list.append((seat, x, y, self.move_number))
      self.sendtable('moved', seat)

      # Have both players moved?  If so, time to do some work!
      if self.white_moved and self.black_moved:

         # First, the easy case: if the players selected the
         # same space, put a neutral piece there.
         if self.black_move == self.white_move:
            self.board[x][y] = NEUTRAL_PIECE
            self.sendtable('move', '', x, y, self.move_number)
         else:
            
            # Okay, this is where the magic happens.  We need
            # to make the moves that the players made, send
            # them, then calculate the score that these moves
            # give.  If this gives a win, we need to handle
            # that.

            black_x, black_y = self.black_move
            white_x, white_y = self.white_move
            self.board[black_x][black_y] = BLACK_PIECE
            self.board[white_x][white_y] = WHITE_PIECE

            self.sendtable('move', self.blackseat, black_x, black_y,
             self.move_number)
            self.sendtable('move', self.whiteseat, white_x, white_y,
             self.move_number)

            # Now, loop to determine how many pieces were enclosed.
            enclosed_black = 0
            enclosed_white = 0

            # Don't forget that frames don't include the pieces played,
            # and that python ranges have 'one less'ness on the top.
            start_x, end_x = black_x + 1, white_x
            start_y, end_y = black_y + 1, white_y

            # Swap if they need to be swapped.
            if start_x > end_x:
               start_x, end_x = end_x, start_x
            if start_y > end_y:
               start_y, end_y = end_y, start_y

            for row in range(start_x, end_x):
               for cell in range(start_y, end_y):
                  if self.board[row][cell] == BLACK_PIECE:
                     enclosed_black += 1
                  elif self.board[row][cell] == WHITE_PIECE:
                     enclosed_white += 1

            # Yay!  A total.  Did one player enclose more?  If so, add
            # to their score, and potentially end the game.
            if enclosed_black > enclosed_white:
               self.black_score += 1
               self.sendtable('score', self.blackseat, self.black_score)
               if self.black_score > self.goal:
                  self.sendtable('over', self.blackseat)
                  self.gameover(self.blackseat, self.whiteseat)
                  game_over = True
            elif enclosed_white > enclosed_black:
               self.white_score += 1
               self.sendtable('score', self.whiteseat, self.white_score)
               if self.white_score > self.goal:
                  self.sendtable('over', self.whiteseat)
                  self.gameover(self.whiteseat, self.blackseat)
                  game_over = True
               
         # Lastly, do stuff we have to do at the end of every
         # turn where someone hasn't won: See if the board is
         # full; if not, reset who has moved, and lastly send
         # a new turn() signal.
         if not game_over:
            if self.freeCellCount() == 0:
   
               # The board is full.  Yikes!  The winner is the seat
               # with the highest score; otherwise, a tie.
               if self.black_score > self.white_score:
                  self.sendtable('over', self.blackseat)
                  self.gameover(self.blackseat, self.whiteseat)
               elif self.white_score > self.black_score:
                  self.sendtable('over', self.whiteseat)
                  self.gameover(self.whiteseat, self.blackseat)
               else:
   
                  # Tied score.
                  self.sendtable('over', '')
                  self.gameover(None)
            else:
   
               # This turn is over.  Reset values, increment the turn number,
               # and let everyone know it's their turn again.
               self.white_moved = False
               self.black_moved = False
               self.move_number += 1
               self.sendtable('turn')

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

   def freeCellCount(self):

      count = 0

      for row in self.board:
         for cell in row:
            if cell:
               count += 1

      return count
