package Volity::Game::CrazyEights;

use warnings;
use strict;

use base qw(Volity::Game);
# Fields:
#  deck: A Cards::Games::Deck object.
#  discard_pile: A Cards::Games::Pile object.
#  last_8_suit: The initial of the suit that the last 8-player called.
#  last_card_player: The seat who has most recently played a card.
use fields qw(orig_deck deck discard_pile last_8_suit last_card_player);
use Games::Cards;

################
# Configuration
################

# Configure some class data, using field names inherited from Volity::Game.
__PACKAGE__->uri("http://volity.org/games/eights/index.html");
__PACKAGE__->seat_class("Volity::Seat::CrazyEights");
__PACKAGE__->name("Jmac's Crazy Eights");
__PACKAGE__->description("Crazy Eights, according to Hoyle, by Jason McIntosh.");
__PACKAGE__->ruleset_version("1.1");
__PACKAGE__->seat_ids([qw(player_1 player_2 player_3 player_4)]);
__PACKAGE__->required_seat_ids([qw(player_1)]);


################
# Callbacks
################

# The server will call start on us. Crack open a new deck,
# shuffle it, and kick the seats.
sub start {
  my $self = shift;

  my $game = Games::Cards::Game->new(
				     {cards_in_suit=>{
						      Ace=>1,
						      2=>2,
						      3=>3,
						      4=>4,
						      5=>5,
						      6=>6,
						      7=>7,
						      8=>50,
						      9=>9,
						      10=>10,
						      Jack=>10,
						      Queen=>10,
						      King=>10,
						     }
				     });

  $self->turn_order(qw(player_1 player_2 player_3 player_4));

  # Set up all the different card-holding objects we'll need.
  $self->orig_deck(Games::Cards::Deck->new($game, 'deck'))->shuffle;
  $self->deck(Games::Cards::Stack->new($game, 'draw'));
  $self->orig_deck->give_cards($self->deck, 'all');
  $self->discard_pile(Games::Cards::Stack->new($game, 'discard'));

  # Tell the seats that a new game has begun.
  foreach ($self->seats_in_play) { $_->hand(Games::Cards::Hand->new($game, "$_")) }

  # Deal 'em out...
  $self->deal_cards;
  # Flip the starter.
  $self->flip_starter;
  # Announce the first seat's turn.
  $self->call_ui_function_on_everyone(start_turn=>$self->current_seat->id);
  # Have the first seat draw cards, maybe.
  $self->make_seat_draw;
  # Now we sit back and let the first seat make his or her move...
}

################
# Seat Actions
################

sub rpc_play_card {
  my $self = shift;
  my ($seat, $card_name) = @_;
  unless ($seat eq $self->current_seat) {
    return(fault=>901, "It's not your turn!");
  }
  my $hand = $seat->hand;
  my $card_index;
  eval { $card_index = $hand->index($card_name); };
  unless (defined($card_index)) {
    return(fault=>902, "You don't have that card ($card_name).");
  }
  my $card = $hand->cards->[$card_index];
  # We have a card... is it a legal play?
  if (
      ($card->name eq '8') or
      ($card->name eq $self->last_card->name) or
      (
       ($self->last_card->name ne '8') and
       ($card->suit eq $self->last_card->suit)
      )
      or
      (
       ($self->last_card->name eq '8') and
       ($card->suit eq $self->last_8_suit)
      )
     ) {
    # Yep. Move the card to to discard pile.
    $hand->give_a_card($self->discard_pile, $card_index);
    # Reset the declared suit.
    $self->last_8_suit(undef);
    # Announce the play to  the seats.
    $self->call_ui_function_on_everyone(seat_played_card =>
					$seat->id,
					$card->name . $card->suit);
  } else {
    return(fault=>907, "That's not a legal play!");
  }
  # Set the current seat as the last card-seat. This will help in case
  # the seat later makes a 'change_suit()' call.
  $self->last_card_player($seat);

  # See if the game is over.
  return if $self->check_for_game_over;

  # Move the turn along, unless an 8 was played. In that case,
  # we must wait for the seat to choose a suit first.
  unless ($card->name eq '8') {
    $self->end_turn;
  }

  return;

}

sub rpc_choose_suit {
  my $self = shift;
  my ($seat, $suit) = @_;
  unless ($seat eq $self->current_seat) {
    return(fault=>901, "It isn't your turn.");
  }
  unless (
	  (defined($self->last_card_player)) and
	  ($self->last_card->name eq '8') and
	  ($self->last_card_player eq $seat)
	 ) {
    return(fault=>905, "You can't change the suit now!");
  }
  my $suit_letter = uc(substr($suit, 0, 1));
  if (grep($suit_letter eq $_, qw(D S H C))) {
    $self->call_ui_function_on_everyone(seat_chose_suit=>$seat->id, $suit);
    $self->last_8_suit($suit_letter);
    $self->end_turn;
  } else {
    return(fault=>906, "I can't turn '$suit' into a recognizable suit name.");
  }
}

sub rpc_draw_card {
  my $self = shift;
  my ($seat) = @_;
  unless ($seat eq $self->current_seat) {
    return(fault=>901, "It isn't your turn.");
  }
  unless (@{$self->deck->cards}) {
    return(fault=>904, "You can't draw a card; the draw pile has no cards left!");
  }
  my $drawn_card_name = $self->deck->top_card->name . $self->deck->top_card->suit;
  $self->deck->give_cards($seat->hand, 1);
  # Tell the seat about this new card.
  $seat->call_ui_function(draw_card=>$drawn_card_name);
  $self->call_ui_function_on_everyone(seat_drew_card=>$seat->id);
}

sub send_full_state_to_player {
    my $self = shift;
    my ($player) = @_;
    if (my $seat = $player->seat) {
	# Send the seat its hand.
	my @card_names = map($_->name . $_->suit, @{$seat->hand->cards});
	$seat->call_ui_function(receive_hand=>\@card_names);
	# Now send it everyone's card counts.
	# The order of sent seats matches the play order, conveniently.
	for my $opponent ($self->seats_in_play) {
	    my $card_count = @{$seat->hand->cards};
	    $self->logger->debug("I will now tell $seat about $opponent, who has $card_count cards.");
	    $seat->call_ui_function(set_seat_hand_size=>$opponent->id, $card_count);
	    $self->logger->debug("Done telling the seat about its opponent.");
	}
    }
    # Send whose turn it is.
    $player->call_ui_function("set_current_seat", $self->current_seat->id) if ($self->current_seat);
    return 1;
}

################
# Internal Methods
################

sub deal_cards {
  my $self = shift;
  # Two-seat games have seven-card hands; others get five-card hands.
  my $hand_size = scalar($self->seats_in_play) == 2? 7: 5;
  for my $seat ($self->seats_in_play) {
    $self->deck->give_cards($seat->hand, $hand_size);
  }
  # Tell everyone what just happened.
  for my $seat ($self->seats_in_play) {
      # Send the seat its hand.
      my @card_names = map($_->name . $_->suit, @{$seat->hand->cards});
      $seat->call_ui_function(receive_hand=>\@card_names);
      # Now send it everyone's card counts.
      # The order of sent seats matches the play order, conveniently.
      for my $opponent ($self->seats_in_play) {
	  my $card_count = @{$seat->hand->cards};
	  $self->logger->debug("I will now tell $seat about $opponent, who has $card_count cards.");
	  $seat->call_ui_function(set_seat_hand_size=>$opponent->id, $card_count);
	  $self->logger->debug("Done telling the seat about its opponent.");
      }
      # Send whose turn it is.
      $seat->call_ui_function("set_current_seat", $self->current_seat->id) if ($self->current_seat);
  }
}

sub flip_starter {
  my $self = shift;
  $self->deck->give_cards($self->discard_pile, 1);
  my $starter_name = $self->discard_pile->top_card->name . $self->discard_pile->top_card->suit;
  $self->call_ui_function_on_everyone(starter_card=>$starter_name);
  # Semi-hack... if the starter is an 8, silently set our notion of
  # the last 8 suit. This keeps the logic in the match-checking part of
  # rpc_play_card simpler.
  $self->last_8_suit($self->last_card->suit) if $self->last_card->name eq '8';
}

sub last_card {
  my $self = shift;
  return $self->discard_pile->top_card;
}

sub end_turn {
  my $self = shift;
  $self->rotate_current_seat;
  $self->call_ui_function_on_everyone(start_turn=>$self->current_seat->id);
  $self->make_seat_draw;
}

# make_seat_draw: Called at the start of a turn. Has the seat draw
# cards from the deck until a play becomes possible. (This will draw
# no cards if the seat starts the turn with legal plays already
# available.)
sub make_seat_draw {
  my $self = shift;
  my $seat = $self->current_seat;
  my @hand_cards = @{$seat->hand->cards};
  my $last_card = $self->last_card;
  my $last_value = $last_card->name;
  my $last_suit = defined($self->last_8_suit)?
      $self->last_8_suit : $last_card->suit;
  my $last_8_suit = $self->last_8_suit;
#  print "Drawing against $last_card, which has $last_value and $last_suit. The last 8 suit is $last_8_suit.\n";
  until (
	 grep(
	      (
	       ($_->name eq 8) or
	       ($_->suit eq $last_suit) or
	       ($_->name eq $last_value)
	      ),
	      @hand_cards,
	     ) or
	 @{$self->deck->cards} == 0
	) {
      if (@{$self->deck->cards} == 0) {
	# Ye gods, the draw pile has run out. This seat's turn is over.
	$self->call_ui_function_on_everyone(seat_passes=>$seat->id);
	$self->end_turn;
      }
    my $drawn_card_name = $self->deck->top_card->name . $self->deck->top_card->suit;
    $self->deck->give_cards($seat->hand, 1);
    if (@{$self->deck->cards} == 0) {
      # The stock's run out. Replenish by shuffling the discards into it.
      $self->discard_pile->shuffle;
      $self->discard_pile->give_cards($self->deck, 'all');
    }
    @hand_cards = @{$seat->hand->cards};
    $seat->call_ui_function(draw_card=>$drawn_card_name);
    # Tell the other seats that the seat drew a card.
    $self->call_ui_function_on_everyone(seat_drew_card=>$seat->id);
  }
}

sub check_for_game_over {
  my $self = shift;
  my $hand = $self->current_seat->hand;
  unless ($hand->cards->[0]) {
    # The current seat has no cards. Game over!
    # Sort the seats into a Frivolity winner list.
    # Each member of this list is a listref, containing all the seats
    # tied for that place. (This will often be just a single seat, if
    # there are no actual ties for that place.)
    my %seats_by_hand_value;
    my %scores_by_idname;
    for my $seat ($self->seats_in_play) {
      my $hand_value = $seat->evaluate_hand;
      $scores_by_idname{$seat->id} = $hand_value;
      if ($seats_by_hand_value{$hand_value}) {
	push (@{$seats_by_hand_value{$hand_value}}, $seat);
      } else {
	$seats_by_hand_value{$hand_value} = [$seat];
      }
    }
    my @winners;		# Sorted winner list!
    # We will add seats to the winner list according to their hand values.
    # A simple sort by these values will give us the right order, since 
    # the fewer points, the better you did. (The first-place winner has zero
    # points!)
    foreach (sort(keys(%seats_by_hand_value))) {
      push (@winners, $seats_by_hand_value{$_});
    }

    # The the seats about this.
    $self->call_ui_function_on_everyone(scores=>\%scores_by_idname);
    
    # Set this array as my winner list, and signal that we're all done,
    # by calling the end() method.
    # The ref will take care of the rest!
    my $position = 1;
    foreach (@winners) {
	$self->winners->add_seat_to_slot($_, $position++);
    }
    
    $self->end;
    return 1;
  } else {
    # Else, the seat still holds cards, so the game ain't over yet...
    return 0;
  }
}
    
################
# Seat Class
################

package Volity::Seat::CrazyEights;

use warnings;
use strict;

use base qw(Volity::Seat);
use fields qw(hand score);

# add_points: Convenience method to increase the seat's score.
# (This is useful for multi-deal games.)
sub add_points {
  my $self = shift;
  my ($points) = @_;
  $points ||= 0;
  $self->{score} += $points;
}

# evaluate_hand: Return the game-over score of this seat's hand.
sub evaluate_hand {
  my $self = shift;
  my $score = 0;
  my @cards = @{$self->hand->cards};
  for my $card (@cards) {
    $score += $card->value;
  }
  return $score;
}

1;

=head1 NAME

Volity::Game::CrazyEights - A Crazy Eights module for the Volity game system

=head1 DESCRIPTION

This module is intended for use with Frivolity, the Perl
implementation of the Volity game platform. While Crazy Eights is a
fun game, this module was specifically written to serve as an example
subclass for Frivolity-using game programmers. Please poke through the
source code to see how the magic works, and consult L<Volity::Game>
(from which this module inherits) as well.

=head2 Running a Crazy Eights server

If you would like to try running this module yourself as a game
server, you must have Volity::Server and its related Perl modules
already on your system. Consult L<Volity::Server> for more information.

=head2 Playing Crazy Eights

As with all other game modules, you don't need to actually run this
code (or any other Volity server code) just to play this game. Through
a Volity client (such as Friv) you can start a new game at
eights@volity.net, a server that just happens to be running (the most
recent version of) this very code...

=head1 SEE ALSO

* L<Volity::Game>

* L<Volity::Seat>

http://volity.org

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

Volity::Game::CrazyEights implements the ruleset for the card game
Crazy Eights, defined at http://volity.org/games/eights/index.html .
