"""twijfel: An implementation of Twijfel.
   Twijfel is a particular version of Dudo/Perudo/Liar's Dice.
   This implementation is by Phil Bordelon.

Ruleset URI: <http://thenexusproject.org/non-canon/projects/volity/rulesets/twijfel.html>
Game URI:    None yet.
Default UI:  <http://thenexusproject.org/non-canon/projects/volity/uis/twijfel.zip>
"""

##############################################################################
##
## Twijfel Server Implementation
## Copyright 2006 Phil Bordelon <phil@thenexusproject.org>
##
## This program is free software; you can redistribute it and/or modify
## it under the terms of the GNU General Public License as published by
## the Free Software Foundation; version 2 (only) of the license.
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

# There is a minimum of 1 die and maximum of 10 for games.
MIN_DICE = 1
MAX_DICE = 10

# Bid/challenge types.
BID_TYPE = 'bid'
DOUBT_TYPE = 'doubt'
BELIEF_TYPE = 'belief'

# Star number.
STAR_VALUE = 6

class Twijfel(volity.game.Game):

   gamename = 'Twijfel'
   gamedescription = 'Twijfel'
   ruleseturi = 'http://thenexusproject.org/non-canon/projects/volity/rulesets/twijfel.html'
   rulesetversion = '1.1'
   websiteurl = 'None yet.'

   def __init__(self, ref):
      volity.game.Game.__init__(self, ref)

      self.setopset(self)

      # Client -> Referee validations.
      self.validatecalls('set_dice_count', afoot=False, args=int)
      self.validatecalls('toggle_speed', afoot=False, args=None)
      self.validatecalls('make_bid', afoot=True, args=[int, int])
      self.validatecalls('state_doubt', afoot=True, args=None)
      self.validatecalls('state_belief', afoot=True, args=None)
      
      self.seat_list = [
         volity.game.Seat(self, 'red', False),
         volity.game.Seat(self, 'orange', False),
         volity.game.Seat(self, 'yellow', False),
         volity.game.Seat(self, 'green', False),
         volity.game.Seat(self, 'cyan', False),
         volity.game.Seat(self, 'blue', False),
         volity.game.Seat(self, 'violet', False),
         volity.game.Seat(self, 'black', False)
      ]

      # Set some default values.

      self.round_number = 0
      self.starting_dice_count = 5
      self.total_dice_count = 0
      self.current_bidder = None
      self.current_bid_count = 0
      self.current_bid_value = 0
      self.current_seat_loc = -1
      self.elimination_list = []
      self.turn = None
      self.speed = False
      self.reveal_all = False

   def checkseating(self):

      # There must be at least two active seats.
      active_seats = 0
      for seat in self.seat_list:
         if (not (seat.isempty())):
            active_seats += 1
      if (active_seats < 2):
         raise volity.game.FailureToken("game.need_two_seats")
      
   def begingame(self):

      # Get the list of active players.
      self.game_info_list = []
      for seat in self.seat_list:
         if seat.isingame():
            # We're going to use a dictionary to store our data.
            # Set it up here.
            self.game_info_list.append(
            {
               "seat": seat,
               "eliminated": False,
               "dice_count": self.starting_dice_count,
               "dice": []
            })

      # Make sure everyone's at the right number of dice.
      self.sendtable('set_dice_count', '', self.starting_dice_count)

      # Set an empty move_list; we'll fill this as the game progresses.
      self.move_list = []

      # Start a new round with no previous bidder ...
      self.startNewRound(None)

      # And that conveniently sends the turn notice!

   def endgame(self, cancelled):
      self.game_info_list = None
      self_move_list = []
      self.turn = None
      self.total_dice_count = 0
      self.current_bidder = None
      self.current_bid_count = 0
      self.current_bid_value = 0
      self.elimination_list = []
      self.round_number = 0
      self.current_seat_loc = -1
    
   def unsuspendgame(self):

      # People may have moved around.  Send them their dice!
      for seat_info in self.game_info_list:
         self.sendseat(seat_info["seat"], 'reveal_dice', seat_info["seat"],
          seat_info["dice"])
      self.sendtable('turn', self.turn)

   def sendconfigstate(self, player):
      player.send('set_dice_count', '', self.starting_dice_count)
      player.send('set_speed', '', self.speed)

   def sendgamestate(self, player, playerseat):

      # Send the round number first.
      player.send('new_round', self.round_number)

      # Since we don't have a 'set number of dice' RPC, we're going to
      # send a number of remove_dice calls depending on just how many
      # dice this player has lost.
      if (self.game_info_list):
         for curr_seat in self.game_info_list:
            player_seat = curr_seat["seat"]
            player_dice_count = curr_seat["dice_count"]
            for missing_die in range(self.starting_dice_count - player_dice_count):
               player.send('remove_die', player_seat)
            # If we should reveal all dice, send them along.  Otherwise,
            # just send them if this player is this seat.
            if ((self.reveal_all) or (player_seat == playerseat)):
               player.send('reveal_dice', player_seat, curr_seat["dice"])

         # Now we need to send the moves for this round so far.
         for move in self.move_list:
            type, seat = move[0:2]
            if (type == BID_TYPE):
               player.send('bid', seat, move[2], move[3])
            elif (type == DOUBT_TYPE):
               player.send('doubted', seat, move[2])
            elif (type == BELIEF_TYPE):
               player.send('believed', seat, move[2])
            else:
               self.log.error("ERROR: Move list had an erroneous type!")

      # Lastly, send the current turn.
      if (self.turn):
         player.send('turn', self.turn)
        
   # The following methods are RPC handlers.
                
   def rpc_adjust_dice_count(self, sender, count):
      if (self.getstate() == volity.game.STATE_SETUP):

         if (count < MIN_DICE or count > MAX_DICE):
            # Bzzt.  Try again.
            raise volity.game.FailureToken('game.invalid_dice_count')

         # Else, adjust the count.
         self.starting_dice_count = count

         # Send to the players and unready since it's a config change.
         self.sendtable('set_dice_count', sender, self.starting_dice_count)
         self.unready()
      else:
         
         # Bzzt.  Can't do this while the game is going on!
         raise volity.game.FailureToken('game.not_setup')

   def rpc_toggle_speed(self, sender):
      if (self.getstate() == volity.game.STATE_SETUP):

         # Toggle state!
         if self.speed:
            self.speed = False
         else:
            self.speed = True
         self.sendtable('set_speed', sender, self.speed)
         self.unready()
      else:
         
         # Bzzt.  Can't do this while the game is going on!
         raise volity.game.FailureToken('game.not_setup')

   def rpc_make_bid(self, sender, count, value):
      seat = self.getplayerseat(sender)
      if (not seat):
         # The validator should prevent this from happening.
         return

      # Check various failure cases.
      if (seat != self.turn):
         raise volity.game.FailureToken('volity.not_your_turn')

      # Grab the current seat information; we'll be using this.
      curr_seat_info = self.game_info_list[self.current_seat_loc]

      # Okay, so the server thinks it should be this person's turn.
      # Verify that against our internal location counter.  If these
      # don't match, something bad has happened.
      if (curr_seat_info["seat"] != seat):
         self.log.error("ERROR: Internal seat doesn't match turn seat!")
         return

      # Okay, keep trucking.  Make sure the bid isn't for more dice than exist.
      if (count > self.total_dice_count):
         raise volity.game.FailureToken('game.bid_too_high')

      # Make sure the bid is for a valid value ...
      if ((value > 6) or (value < 1)):
         raise volity.game.FailureToken('game.invalid_bid_value')

      # Okay, so, it's a valid value.  Now we have to determine if the bid's
      # high enough.  These are the cases where it /is/ high enough:
      # IF PREVIOUS BID WAS NOT STAR AND THIS BID IS NOT STAR:
      # - Count is the same, but value > older value (this happens to hold for
      #   this implementation's STAR_VALUE too, but that is not necessarily
      #   true)
      # - Count > previous.
      # IF PREVIOUS BID WAS NOT STAR AND THIS BID IS STAR:
      # - Count > ((previous_bid - 1) / 2) (six -> three, seven -> four)
      # IF PREVIOUS BID WAS STAR AND THIS BID IS NOT STAR:
      # - Count > previous_bid * 2 (three stars -> seven whatevers)
      # IF PREVIOUS BID WAS STAR AND THIS BID IS STAR:
      # - Count > previous
      bid_is_too_low = True
      if (self.current_bid_value == STAR_VALUE):
         if (value == STAR_VALUE):
            if (count > self.current_bid_count):
               bid_is_too_low = False
         else: # value != STAR_VALUE
            if (count > (self.current_bid_count * 2)):
               bid_is_too_low = False
      else: # previous value != STAR_VALUE
         if (value == STAR_VALUE):
            if (count > ((self.current_bid_count - 1) / 2)):
               bid_is_too_low = False
         else: # Value != STAR_VALUE either
            if (((value > self.current_bid_value) and
             (count == self.current_bid_count)) or
             (count > self.current_bid_count)):
               bid_is_too_low = False

      # Well, /is/ the bid too low?
      if ((bid_is_too_low) or (count < 1)):
         raise volity.game.FailureToken('game.bid_too_low')

      # Nope!  This is now the current bid.  Fantastic!
      self.current_bid_value = value
      self.current_bid_count = count
      self.current_bidder = seat

      # Send the bid to everyone ...
      self.sendtable('bid', seat, count, value)

      # Record this in the move list ...
      self.move_list.append((BID_TYPE, seat, count, value))

      # ... and move on to the next person.  This requires actually
      # /determining/ the next player, which requires poking at the
      # game_info_list.
      found_next_player = False
      while (not found_next_player):
         self.current_seat_loc += 1

         # Loop around if too large ...
         if (self.current_seat_loc >= len (self.game_info_list)):
            self.current_seat_loc -= len (self.game_info_list)

         # Is this player still in the game?
         if (not self.game_info_list[self.current_seat_loc]["eliminated"]):
            found_next_player = True

      # Found the next player.  Send a turn notice, and we're done!
      self.turn = self.game_info_list[self.current_seat_loc]["seat"]
      self.sendtable('turn', self.turn) 

   def rpc_state_doubt(self, sender):
      seat = self.getplayerseat(sender)
      if (not seat):
         # The validator should prevent this from happening.
         return

      # Check various failure cases.
      if (seat != self.turn):
         raise volity.game.FailureToken('volity.not_your_turn')

      # Grab the current seat information; we'll be using this.
      curr_seat_info = self.game_info_list[self.current_seat_loc]

      # Okay, so the server thinks it should be this person's turn.
      # Verify that against our internal location counter.  If these
      # don't match, something bad has happened.
      if (curr_seat_info["seat"] != seat):
         self.log.error("ERROR: Internal seat doesn't match turn seat!")
         return

      # Okay, keep trucking.  Make sure the first player doesn't challenge.
      if (self.current_bid_count == 0):
         raise volity.game.FailureToken('game.must_bid')

      # Seems like a legitimate challenge.  Let everyone know and record it in
      # the move list.
      self.sendtable('doubted', seat, self.current_bidder)
      self.move_list.append((DOUBT_TYPE, seat, self.current_bidder))

      # Reveal the dice and get the bid result!
      bid_count = self.revealAndEvaluateBid()

      seat_info_to_tweak = None
      next_round_starter = None
      dice_to_lose = 0
      if bid_count >= self.current_bid_count: # D'oh, challenger loses a die.
         seat_info_to_tweak = curr_seat_info
         next_round_starter = self.current_bidder

         # If the number was exact, lose one die; otherwise the difference.
         if bid_count == self.current_bid_count:
            dice_to_lose = 1
         else:
            dice_to_lose = bid_count - self.current_bid_count
      else: # The bidder loses a die; the bid wasn't met.
         seat_info_to_tweak = self.game_info_list[self.getSeatLoc(self.current_bidder)]
         next_round_starter = curr_seat_info["seat"]
         dice_to_lose = self.current_bid_count - bid_count

      # Figure out the /real/ number of dice to lose; you can't lose more
      # than you have.
      if self.speed:
         dice_to_lose = min (seat_info_to_tweak["dice_count"], dice_to_lose)
      else:
         dice_to_lose = 1

      # Send the remove_die RPC to everyone ...
      for lost_die in range (dice_to_lose):
         self.sendtable('remove_die', seat_info_to_tweak["seat"])

      # Handle it locally; this may eliminate a player, which requires an RPC.
      seat_info_to_tweak["dice_count"] -= dice_to_lose
      if (seat_info_to_tweak["dice_count"] < 1):
         seat_info_to_tweak["eliminated"] = True
         self.elimination_list.append(seat_info_to_tweak["seat"])
         self.sendtable('eliminated', seat_info_to_tweak["seat"])

      # Finally, handle a new round and/or game over.
      self.handleCompletedChallenge(next_round_starter)

   def rpc_state_belief(self, sender):
      seat = self.getplayerseat(sender)
      if (not seat):
         # The validator should prevent this from happening.
         return

      # Check various failure cases.
      if (seat != self.turn):
         raise volity.game.FailureToken('volity.not_your_turn')

      # Grab the current seat information; we'll be using this.
      curr_seat_info = self.game_info_list[self.current_seat_loc]

      # Okay, so the server thinks it should be this person's turn.
      # Verify that against our internal location counter.  If these
      # don't match, something bad has happened.
      if (curr_seat_info["seat"] != seat):
         self.log.error("ERROR: Internal seat doesn't match turn seat!")
         return

      # Okay, keep trucking.  Make sure the first player doesn't challenge.
      if (self.current_bid_count == 0):
         raise volity.game.FailureToken('game.must_bid')

      # Seems like a legitimate challenge.  Let everyone know and record it in
      # the move list.
      self.sendtable('believed', seat, self.current_bidder)
      self.move_list.append((BELIEF_TYPE, seat, self.current_bidder))

      # Reveal the dice and get the bid result!
      bid_count = self.revealAndEvaluateBid()

      # The only person who gains or loses dice is the challenger.  Assume loss.
      next_round_starter = None
      dice_to_lose = 0
      lose_dice = True # This could be tracked with the above, but that's ugly.
      if bid_count == self.current_bid_count: # Wow!  The belief is true.
         next_round_starter = curr_seat_info["seat"]
         lose_dice = False
      else: # Whether higher or lower, it ain't exact.  Bidder fails it.
         next_round_starter = self.current_bidder
         if bid_count > self.current_bid_count:
            dice_to_lose = bid_count - self.current_bid_count
         else:
            dice_to_lose = self.current_bid_count - bid_count

      if lose_dice:

         # Determine the real number of dice to lose.
         if self.speed:
            dice_to_lose = min (curr_seat_info["dice_count"], dice_to_lose)
         else:
            dice_to_lose = 1

         # Send the remove_die RPC to everyone ...
         for lost_die in range (dice_to_lose):
            self.sendtable('remove_die', curr_seat_info["seat"])

         # Handle it locally; this may eliminate the player.
         curr_seat_info["dice_count"] -= dice_to_lose
         if (curr_seat_info["dice_count"] < 1):
            curr_seat_info["eliminated"] = True
            self.elimination_list.append(curr_seat_info["seat"])
            self.sendtable('eliminated', curr_seat_info["seat"])
      else:
         # Now, we only add a die if the player doesn't already have the max.
         if (curr_seat_info["dice_count"] < self.starting_dice_count):

            # Send the add_die RPC to everyone ...
            self.sendtable('add_die', curr_seat_info["seat"])

            # Add the die to the player's dice_count.
            curr_seat_info["dice_count"] += 1

      # Finally, handle a new round and/or game over.
      self.handleCompletedChallenge(next_round_starter)

   # The following methods are internal, non-RPC functions.

   # This function returns the number of non-eliminated players.
   def getActivePlayerCount(self):

      to_return = 0
      for curr_seat_info in self.game_info_list:
         if (not curr_seat_info["eliminated"]):
            to_return += 1
            
      return to_return

   # This function returns the numeric seat location for a given
   # seat.
   def getSeatLoc(self, seat):

      to_return = -1
      curr_loc = 0
      for curr_seat_info in self.game_info_list:
         if curr_seat_info["seat"] == seat:
            to_return = curr_loc
         curr_loc += 1

      return to_return

   # This function is called whenever a challenge has been completed.  It
   # ends the game if necessary, otherwise starting a new round.
   def handleCompletedChallenge(self, starting_seat):

      # Okay, so, there may be a winner.  Let's find out!
      if (self.getActivePlayerCount() == 1):
      
         # Only one player left.  A winner!  We've been tracking people who
         # get eliminated.  Add the last player (which will be the
         # starting_seat; clever, eh?) and then reverse the list, using that
         # for both game.over and endgame.
         self.elimination_list.append(starting_seat)
         self.elimination_list.reverse()
         self.sendtable('over', self.elimination_list)
         self.gameover(*self.elimination_list)
      else:

         # More than one player left.  We can just start a new round.
         self.startNewRound(starting_seat)

   # This function reveals everyone's dice, and evaluates the current bid,
   # determining whether it is successful or not.
   def revealAndEvaluateBid(self):

      # Set reveal_all for the edge case that someone pops in RIGHT AT THIS
      # MOMENT.
      self.reveal_all = True

      # Loop through each seat, revealing dice and counting 'em up.
      dice_totals = {
         1: 0,
         2: 0,
         3: 0,
         4: 0,
         5: 0,
         6: 0
      }
      for curr_seat_info in self.game_info_list:
         if not (curr_seat_info["eliminated"]):
            self.sendtable('reveal_dice', curr_seat_info["seat"],
             curr_seat_info["dice"])
            for die in curr_seat_info["dice"]:
               dice_totals[die] += 1

      # Now, determine what the actual result of the bid was.  If it was
      # for STAR_VALUE, just return that; otherwise, it's either 0 if there
      # were none of that value, or the total of that die and STAR_VALUE die
      # if at least one of that value existed.
      to_return = 0
      cbv = self.current_bid_value

      if cbv == STAR_VALUE:
         to_return = dice_totals[STAR_VALUE]
      elif dice_totals[cbv] > 0:
         to_return = dice_totals[cbv] + dice_totals[STAR_VALUE]
      # else dice_totals[cbv] == 0, which means we return zero.

      # Lastly, return the values that we determined.
      return to_return

   # This function is called whenever a new round is started.
   def startNewRound(self, starting_seat):

      # First, increment the round count and send the new_round RPC.
      # Also turn off reveal_all, if it was set.

      self.reveal_all = False
      self.round_number += 1
      self.sendtable('new_round', self.round_number)

      # Reset the internal move list.
      self.move_list = []

      # Next, build everyone's dice list (and our count of total dice)
      # and send them to each player individually.

      self.total_dice_count = 0
      for seat in self.game_info_list:
         if (not seat["eliminated"]):
            seat["dice"] = []
            self.total_dice_count += seat["dice_count"]
            for die in range(seat["dice_count"]):
               seat["dice"].append(random.randint(1,6))
            self.sendseat(seat["seat"], 'reveal_dice', seat["seat"],
             seat["dice"])
            
      # Now, if we don't have a starting seat (the beginning of the game
      # only), pick one randomly.
      if (not starting_seat):
         starting_seat = random.choice(self.game_info_list)["seat"]

      # Finally, send a turn(starting_seat) and record that internally!
      self.sendtable('turn', starting_seat)
      self.turn = starting_seat
      self.current_seat_loc = self.getSeatLoc(starting_seat)

      # Internal housekeeping now; set the current bid and value to 0, so
      # that we make sure that the first player doesn't get away with a
      # challenge.
      self.current_bid_count = 0
      self.current_bid_value = 0

      return
