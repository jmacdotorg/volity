package Volity::Game::Hearts;

use warnings;
use strict;

use base qw(Volity::Game);
# Fields:
#  game_end_score: a configuration variable that says when the game's over
#  deck: A Cards::Games::Deck object.
#  cards_in_play: how many cards are on the table
#  led_suit: for the current trick, what suit was led
#  hearts_broken: whether hearts have been played yet
#  trick_count: how many tricks have passed in this round
#  round_count: how many rounds have passed in this game
#  pass_info: an array of info about the current passing scheme: (direction, how_many)
#  variant: the name of the variant we're playing
#  variant_rules: an object that performs certain sets of rules processing that vary
#  	depending on which game variant we're playing
use fields qw( game_end_score deck cards_in_play led_suit hearts_broken trick_count
    round_count pass_info variant variant_rules );
use Games::Cards;
use Volity::Game::Hearts::VariantFactory;

our $VERSION = "1.3";

# package data
my @supported_variants = @Volity::Game::Hearts::VariantFactory::supported_variants;

################
# Configuration
################

# Configure some class data, using field names inherited from Volity::Game.
__PACKAGE__->uri("http://games.staticcling.org:8088/hearts");
__PACKAGE__->seat_class("Volity::Seat::Hearts");
__PACKAGE__->name("Hearts");
__PACKAGE__->description("Hearts according to some dude on the Internet");
__PACKAGE__->ruleset_version("1.0");
# for now, we'll only concern ourselves with the 4 person game
__PACKAGE__->seat_ids(["Seat_1", "Seat_2", "Seat_3", "Seat_4"]);
__PACKAGE__->required_seat_ids(["Seat_1", "Seat_2", "Seat_3", "Seat_4"]);
__PACKAGE__->max_allowed_seats(4);

# Set up the variables that the players can configure
sub initialize {
	my $self = shift;

	$self->SUPER::initialize(@_);
	$self->register_config_variables(qw( game_end_score variant ));

	# XXX debugging
	#$self->referee->is_recorded(0);

    # initial configuration state for all games
    $self->game_end_score(100);
	$self->variant("standard");

	return 1;
}

################
# Callbacks
################

# The server will call start on us. Crack open a new deck,
# shuffle it, and kick the seats.
sub start {
  my $self = shift;

  # Instantiate the proper object to play the requested variant, error
  # checking has already been done to see if the desired variant is supported,
  # but we can check again anyway
  $self->variant_rules(Volity::Game::Hearts::VariantFactory->instantiate($self->variant));
  unless ($self->variant_rules) {
	  $self->logger->error("Unsuppoted variant " . $self->variant . " requested, " .
		  "playing standard variant instead");
	  $self->variant("standard");
	  $self->variant_rules(Volity::Game::Hearts::VariantFactory->instantiate($self->variant));
  }

  # configure the card suits & values.  The suit order listed below affects
  # the ->sort_by_suit_and_value routine (and others) and makes the sort
  # become the same as in MS Hearts
  my $game = Games::Cards::Game->new({
	  suits => ["clubs", "diamonds", "spades", "hearts"],
	  cards_in_suit => {
		  2=>2, 3=>3, 4=>4, 5=>5, 6=>6, 7=>7, 8=>8, 9=>9,
		  10=>10, Jack=>11, Queen=>12, King=>13, Ace=>14,
	  } });

  $self->turn_order("Seat_1", "Seat_2", "Seat_3", "Seat_4");

  # Set up all the different card-holding objects we'll need.
  $self->deck(Games::Cards::Deck->new($game, 'deck'));

  foreach ($self->seats_in_play) { 
        $_->hand(Games::Cards::Hand->new($game, $_->id));
        $_->card_played(Games::Cards::Stack->new($game, $_->id . "_played"));
        $_->cards_taken(Games::Cards::Stack->new($game, $_->id . "_taken"));
        $_->inbound_cards(Games::Cards::Stack->new($game, $_->id . "_inbound"));
        $_->score(0); # also set up the seat's inital score, and tell everyone
		$self->call_ui_function_on_everyone(seat_score => $_->id, 0);
        $_->passing(0);
  }

  # initial game state
  $self->round_count(0);
  $self->hearts_broken(0);
  $self->trick_count(0);
  $self->led_suit("");
  $self->cards_in_play(0);
  $self->pass_info(["", 0]);

  # Deal 'em out... and let the fun begin
  $self->deal_cards();

  #XXX check what the full URI of these games will be now
  # $self->logger->info($self->full_uri);
}

# send the configuration of the game to the player
sub send_config_state_to_player {
    my $self = shift;
    my ($player) = @_;

	$player->call_ui_function(supported_variants => \@supported_variants);
    $player->call_ui_function(game_end_score => $self->game_end_score);
    $player->call_ui_function(variant => $self->variant);

	return 1;
}

sub send_game_state_to_player {
    my $self = shift;
    my ($player) = @_;

	$self->logger->debug("Game State: ", $self->referee->current_state);

	# Send a playing seat its hand, but only if the game is active, ditto for
	# the must_pass rpc
    if (my $seat = $player->state_seat and $self->is_active) {
		$self->logger->debug("Seated Player: " . $player->basic_jid . ", " . $seat->id);
        my @card_names = map($_->truename, @{$seat->hand->cards});
        $player->call_ui_function(receive_hand=>\@card_names);
		$self->logger->debug($seat->id . " sent hand");

		if ($seat->passing) {
			$self->logger->debug($seat->id . " is passing");
			my ($which_dir, $how_many) = $self->pass_info;
			$self->logger->debug($seat->id . " pass_info called");
			$player->call_ui_function(must_pass => $which_dir, $how_many);
			$self->logger->debug($seat->id . " must_pass sent");
		}
    }

	# send all of the cards presently in play, record who's still passing, and
	# send all of the scores
	my @passing;
	foreach my $seat ($self->seats_in_play) {
		$self->logger->debug($seat->id . " examining played cards");
        $player->call_ui_function(seat_played_card => $seat->id,
            $seat->card_played->top_card->truename)
                if $seat->card_played->top_card;
		$self->logger->debug($seat->id . " sending score");
		$player->call_ui_function(seat_score => $seat->id, $seat->score);

		if ($seat->passing) {
			push(@passing, $seat->id);
			$self->logger->debug($seat->id . " is passing (2)");
		}
	}

	# tell about the current passing scheme, and who's presently passing
	$self->logger->debug($player->basic_jid . " setting up to send passing_info");
	my ($direction, $count) = $self->pass_info;
	$self->logger->debug($player->basic_jid . " pass_info called");
	$player->call_ui_function(passing_info => $direction, $count, \@passing);
	$self->logger->debug($player->basic_jid . " passing_info sent");

    # Send whose turn it is.
    $player->call_ui_function("start_turn", $self->current_seat->id) if ($self->current_seat);
	$self->logger->debug($player->basic_jid . " start_turn sent");

    return 1;
}

sub game_has_resumed {
	my $self = shift;

	$self->logger->debug("Game has resumed");

	return 1;
}

################
# Seat Actions
################

# handle the player configuration of what variant we're playing
sub rpc_variant {
    my $self = shift;
    my ($seat, $variant) = @_;

    if (grep($variant eq $_, @supported_variants)) {
        # don't unseat everyone if there's no change
        if ($self->variant ne $variant) {
            $self->variant($variant);
            $self->unready_all_players();
            $self->call_ui_function_on_everyone(variant => $variant);
        }
        return;
    }

    return ("game.invalid_configuration", "variant", $variant);
}

# handle the player configuration of how many points we want to play to
sub rpc_game_end_score {
    my $self = shift;
    my ($seat, $score) = @_;

    $score = int($score);
    if ($score != 0) {
        # don't unseat everyone if there's no change
        if ($self->game_end_score != $score) {
            $self->game_end_score($score);
            $self->unready_all_players();
            $self->call_ui_function_on_everyone(game_end_score => $score);
        }
        return;
    }

    return ("game.invalid_configuration", "game_end_score", $score);
}

sub rpc_pass_cards {
    my $self = shift;
    my ($seat, $card_names) = @_;

    # Don't allow passing at odd times
    unless ($seat->passing) {
        return "game.not_passing";
    }
    
    # providing the correct number of cards would be good
    my ($direction, $count) = $self->variant_rules->pass_count($self->round_count);
    if ($count != scalar @$card_names) {
        return ("game.wrong_pass_count", $count);
    }

    my @cards;
    my $hand = $seat->hand;

    # passing only cards you own is also good
    foreach my $card_name (@$card_names) {
        my $card_index;
        eval { $card_index = $hand->index($card_name); };
        unless (defined($card_index)) {
            return ("game.unowned_card", $card_name);
        }
    }

    # we've accepted the cards
    $seat->passing(0);

    # who the hell are we passing to, again?
    my @seats = $self->seats_in_play;
    my %dirs = ( left => 1, right => 3, across => 2);
    my $seat_num = substr($seat->id, -1);
    my $target_num = ($seat_num + $dirs{$direction}) % 4;
    $target_num = 4 if $target_num == 0; # convert from 0 based to 1 based
    my $target = $seats[$target_num - 1];
    $self->logger->info($self->log_prefix . "Passing " . 
        join(', ', @$card_names) .  " $direction from " .  $seat->id . 
        " to " . $target->id);

    # finally pass the damn cards
    foreach my $card_name (@$card_names) {
        $seat->hand->give_a_card($target->inbound_cards, $card_name);
    }

    # let the seat know that the referee is beaming with adoration at a player
    # who can meet some simple requirements
    $seat->call_ui_function(pass_accepted => $card_names);
    $self->call_ui_function_on_everyone(seat_passed => $seat->id);

    # has everyone done their passing like good little girls and boys?
    foreach my $s (@seats) {
        return "volity.ok" if $s->passing;
    }

    # everyone's done, distribute the cards & tell everyone what they got
    foreach my $s (@seats) {
        @$card_names = map { $_->name . $_->suit } @{$s->inbound_cards->cards};
        $s->inbound_cards->give_cards($s->hand, "all");
        $s->hand->sort_by_suit_and_value(); # mandated in the ruleset spec
        $s->call_ui_function(receive_pass => $card_names);
    }

    # kick the round off by playing the 2 of Clubs
    $self->start_round();

    return "volity.ok";
}

sub rpc_play_card {
    my $self = shift;
    my ($seat, $card_name) = @_;

    # basic validation
    unless ($seat eq $self->current_seat) {
        return "volity.not_your_turn";
    }

    foreach my $s ($self->seats_in_play) {
        return "game.no_play_during_passing" if $s->passing;
    }

    my $hand = $seat->hand;
    my $card_index;
    eval { $card_index = $hand->index($card_name); };
    unless (defined($card_index)) {
        return "game.unowned_card";
    }
    my $card = $hand->cards->[$card_index];

    # players must follow suit if possible (unless they're leading)
    if ($self->led_suit ne $card->suit && $self->led_suit ne "") {
        foreach (@{$seat->hand->cards}) {
            return ("game.follow_suit", $self->led_suit) 
				if ($_->suit eq $self->led_suit);
        }
    }

    # if this is the first trick, check that hearts aren't being broken (and
    # that the queen isn't being played). Points /can/ be laid in the first
    # round if the rare occasion of someone having nothing but hearts occurs.
    if ($self->trick_count == 0 && 
        ($card->suit eq 'H' || $card->name . $card->suit eq 'QS'))
    {
        foreach (@{$seat->hand->cards}) {
            return "game.no_points_first_trick" if ($_->suit ne 'H');
        }
    }

    # hearts can't be led until they've been broken unless the player has
    # nothing else
    if ($card->suit eq 'H' && 
        $self->cards_in_play == 0 && 
        ! $self->hearts_broken)
    {
        foreach (@{$seat->hand->cards}) {
            return "game.no_lead_hearts" if ($_->suit ne 'H');
        }
    }

    # move is legal, now go through and do all of the accounting
    $self->hearts_broken(1) if $card->suit eq 'H';
    $self->led_suit($card->suit) unless $self->led_suit;
    $self->play_card($seat, $card);

    # figure out who won the trick and assign the lead for next trick
    if ($self->cards_in_play >= 4) {
        my $winner = $self->process_trick_winner();

        # Assign the lead and start the next trick, unless the round is over.
        # Both clauses set the current player
        unless ($self->trick_count >= 13) {
            $self->current_seat($winner);
        } else { 
            # do end-of-round processing
            $self->process_end_of_round();
            return "volity.ok";
        }

        # start the next trick or round, without changing the player (it was
        # already set above)
        $self->end_turn(0);
        return "volity.ok";
    }

    # tell the next person to play
    $self->end_turn();
    return "volity.ok";
}

# a request by a seat to have its hand refreshed... pure laziness on my part
sub rpc_send_hand {
    my $self = shift;
    my ($seat) = @_;

	return "volity.ok" unless defined $seat;

    my @card_names = map($_->name . $_->suit, @{$seat->hand->cards});
    $seat->call_ui_function(receive_hand=>\@card_names);

    return "volity.ok";
}

################
# Internal Methods
################

# Deals a new round of cards, tells everyone what their hands are, and (for
# now) how many cards their opponents have, and tells everyone they must pass
sub deal_cards {
    my $self = shift;
    # May need logic here for hand size if I implement rules for 3 & 5 player
    # games
    my $hand_size = 13;

    # shuffle the deck first
    $self->deck->shuffle();

    for my $seat ($self->seats_in_play) {
        $self->deck->give_cards($seat->hand, $hand_size);
        $seat->hand->sort_by_suit_and_value();
    }

	# figure out the passing for the round, and record it
	my ($pass_dir, $pass_count) = $self->variant_rules->pass_count($self->round_count);
	$self->pass_info([$pass_dir, $pass_count]);

    # Tell the players about their shiny new hands
    for my $seat ($self->seats_in_play) {
        # Send the seat its hand.
        my @card_names = map($_->name . $_->suit, @{$seat->hand->cards});
        $self->logger->info($self->log_prefix . "sending hand to " . 
            $seat->id .  "(".  join(', ', @card_names) . ")");
        $seat->call_ui_function(receive_hand=>\@card_names);

        # the seat is passing only it's not a hold round
        $seat->passing(1) if $pass_count != 0;
    }

	# tell everyone about the passing that's happening
	$self->call_ui_function_on_everyone(must_pass => $pass_dir, $pass_count);
}

sub start_round ($) {
    my $self = shift;

    # find the 2 of clubs to start the round
    my $starting_seat = $self->find_card("2C");
    unless (defined $starting_seat) {
        $self->logger->error($self->log_prefix . 
            "Uh, nobody has the 2 of Clubs? Can't continue");
        return "volity.bad_config"; # XXX this isn't the right token, but...
    }

    # Set up the first seat's turn, but don't announce it, because it can
    # be confusing -- the ref plays the card for the player.
    $self->current_seat($starting_seat);
#   $self->call_ui_function_on_everyone(start_turn=>$self->current_seat->id);

    # this always happens, so might as well do it automatically
    my $start_card_idx = $self->current_seat->hand->index("2C");
    $self->play_card($self->current_seat, 
            $self->current_seat->hand->cards->[$start_card_idx]);
    $self->led_suit("C");
    $self->end_turn();
}

# Handles the grunt work of playing a card.  This routine assumes that this
# is a valid play.
# seat should be a seat object
# card should be a Games::Cards::Card object
sub play_card ($$$) {
    my ($self, $seat, $card) = @_;

    $seat->hand->give_a_card($seat->card_played, $card);
    $self->cards_in_play($self->cards_in_play + 1);
    $self->call_ui_function_on_everyone(seat_played_card => $seat->id, 
        $card->name . $card->suit);
}

# Figures out who won a trick, assumes that all cards have been played.  This
# function also does all of the side effects of the seat winning the trick
# Returns the winning seat
sub process_trick_winner ($) {
    my $self = shift;
    my %cards;

    $self->logger->debug($self->log_prefix . "Checking trick winner");
    # first, figure out who won the trick
    my $winner = undef;
    foreach my $seat ($self->seats_in_play) {
        my $card = $seat->card_played->top_card;
        $self->logger->error($self->log_prefix . "seat " . $seat->id . 
            " didn't play a card") if $card == 0;
        $cards{$seat->id} = $card->name . $card->suit;

        next unless $card->suit eq $self->led_suit;
        $winner = $seat 
            if (! defined $winner || 
                $card->value > $winner->card_played->top_card->value);
    }

    $self->logger->debug($self->log_prefix . "And the winner is ". $winner->id);
	
	# Set up the logging that could allow us to analyse the play of people,
	# and perhaps do some sort of machine learning on the accumulated play of
	# hundreds of games.  That could make a fun bot, no?
	my $logstr = $self->log_prefix . ": trick = { game => " . ($self->referee->games_completed + 1) . ", ";
	$logstr .= "round => " . $self->round_count . ", ";
	$logstr .= "trick => " . $self->trick_count . ", ";
	$logstr .= "winner => '" . $winner->id . "', ";

    # do the accounting (and logging for now)
    foreach my $seat ($self->seats_in_play) {
		$logstr .= $seat->id . " => '" . $seat->card_played->top_card->truename .  "', ";
        $seat->card_played->give_cards($winner->cards_taken, "all"); # accounting
    }
	$logstr .= "};";

	# log a perl hash of the trick, for easy analysis later (if desired)
	$self->logger->info($logstr);

    $self->cards_in_play(0);
    $self->led_suit("");
    $self->trick_count($self->trick_count + 1);

    # tell everyone about the smashing win
    $self->call_ui_function_on_everyone(seat_won_trick => $winner->id, 
        \%cards);

    return $winner;
}

# Does all of the processing for the end of a round, including calling the
#   appropriate function for ending the game
# No return value.  Does the appropriate work to end the game if the game's 
#  over.
sub process_end_of_round ($) {
    my $self = shift;

	# calculate and give out the points
	my @seats_in_play = $self->seats_in_play;
	$self->variant_rules->assign_scores(\@seats_in_play);

    # Tell everyone the scores
    foreach my $seat ($self->seats_in_play) {
        $self->call_ui_function_on_everyone(seat_score => $seat->id, $seat->score);

        # give the cards back to the dealer, all cards should be in
        # the various cards_taken piles, but for insurance (and
        # debugging) I'm transferring the (hopefully nonexistant) cards
        # from the players' hands to the dealer
        $seat->cards_taken->give_cards($self->deck, "all");
        for (my $i = scalar(@{$seat->hand->cards}) - 1; $i >= 0; $i--) {
            $seat->hand->give_a_card($self->deck, $i); 
        }
    }

    unless ($self->variant_rules->is_game_over(\@seats_in_play, $self->game_end_score)) {
        # most of these variables were reset in process_trick_winner, but
        # doing it twice doesn't hurt (during testing anyway)
        $self->hearts_broken(0);
        $self->trick_count(0);
        $self->led_suit("");
        $self->cards_in_play(0);
        $self->round_count($self->round_count + 1);

        # and sling 'em out again.
        $self->deal_cards();

        # if there's to be no passing, kickstart the round by playing
        # the 2 of clubs automatically -- this is otherwise handled in
        # the passing logic
        my ($pass_dir, $pass_count) = $self->variant_rules->pass_count($self->round_count);
        $self->start_round() if $pass_count == 0;
    } else  {
        $self->process_winners();
        $self->end();
    }
}

# Sets up the winners array.  Ties result in the next position being empty; a
# three-way tie for first would result in 3 people in slot 1, and 1 person in
# slot 4.  This routine also sends the clients notification of who won.
sub process_winners ($) {
    my $self = shift;

	# sort by ascending score
    my @winners = sort {$a->score <=> $b->score} $self->seats_in_play;

    my ($place, $last_score) = (0, -1000);
    for (my $i = 0; $i < scalar @winners; $i++) {
        if ($winners[$i]->score > $last_score) {
            if ($place == 0) {
                $place++;
            } else {
                $place += scalar $self->winners->seats_at_slot($place);
            }
            $last_score = $winners[$i]->score;
        }

        $self->winners->add_seat_to_slot($winners[$i], $place);
    }

    my @first_place_ids = map { $_->id } $self->winners->seats_at_slot(1);
    $self->call_ui_function_on_everyone(winners => \@first_place_ids);
}

# Figures out whose hand holds a certain card (2C for instance).
# Takes a card name, 
# Returns a seat, or undef if the card wasn't in anyone's hand
sub find_card ($$) {
        my $self = shift;
        my $card = shift;

        foreach my $seat ($self->seats_in_play) {
                return $seat if defined $seat->hand->index($card);
        }

        return undef;
}

# Ends the turn, selecte the currently playing seat, unless the optional
#   argument is provided
sub end_turn {
  my $self = shift;
  my $norotate = shift;

  $self->rotate_current_seat unless defined $norotate;
  $self->call_ui_function_on_everyone(start_turn=>$self->current_seat->id);
}

sub log_prefix {
    my $self = shift;

    my @bits = split('@', $self->referee->muc_jid);
    return "$bits[0] ";
}

################
# Seat Class
################

package Volity::Seat::Hearts;

use warnings;
use strict;

use base qw(Volity::Seat);
use fields qw(hand passing inbound_cards card_played cards_taken score);

# add_points: Convenience method to increase the seat's score.
# (This is useful for multi-deal games.)
sub add_points {
    my $self = shift;
    my ($points) = @_;
    $points ||= 0;
    $self->{score} += $points;
}

1;

=head1 NAME

Volity::Game::Hearts - A Hearts module for the Volity game system

=head1 DESCRIPTION

This module is intended for use with Frivolity, the Perl
implementation of the Volity game platform. 

=head2 Running a Hearts server

If you would like to try running this module yourself as a game
server, you must have Volity::Server and its related Perl modules
already on your system. Consult L<Volity::Server> for more information.

=head2 Playing Hearts

As with all other game modules, you don't need to actually run this
code (or any other Volity server code) just to play this game.

=head1 SEE ALSO

* L<Volity::Game>

* L<Volity::Seat>

http://volity.org

=head1 AUTHOR

Austin Henry <ahenry@sf.net>
based on code by 
Jason McIntosh <jmac@jmac.org>

Volity::Game::Hearts implements the ruleset for the card game
Hearts, defined at http://games.staticcling.org:8088/hearts.html
