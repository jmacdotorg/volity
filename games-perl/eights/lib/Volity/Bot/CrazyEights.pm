package Volity::Bot::CrazyEights;

# This is a superclass for bots that can play rock-paper-scissors.

use warnings; use strict;

use base qw(Volity::Bot);
# Mmm, take note of which fields should go in a bot class.
use fields qw(match_card cards by_suit by_rank suit_sums eights suit_rank);

use POE;

__PACKAGE__->name("CrazyEddie");
__PACKAGE__->description("Generic Crazy Eights-playing bot.");
__PACKAGE__->user("rps-bot");
__PACKAGE__->host("volity.net");

## new: call parent's constructor, then init some instance variables.
sub new {
  my $class = shift;
  my $self = $class->SUPER::new(@_);
  $self->{by_rank} = {};
  $self->{by_suit} = {};
  return $self;
}

sub handle_rpc_request {
    my $self = shift;
    my ($rpc_info) = @_;
    if ($$rpc_info{method} =~ /game.(.*)/) {
	my $method = "rpc_$1";
	if ($self->can($method)) {
	    $self->$method(@{$$rpc_info{args}});
	}
	
    } else {
	return $self->SUPER::handle_rpc_request(@_);
    }
}

##################
# RPC methods
##################

sub rpc_player_played_card {
  my $self = shift;
  my ($player, $card) = @_;
  $self->{match_card} = uc($card);
}

sub rpc_starter_card {
  my $self = shift;
  my ($card) = @_;
  $self->{match_card} = uc($card);
}

sub rpc_receive_hand {
  my $self = shift;
  my ($cards) = @_;
  for my $card (@$cards) {
    $self->add_card($card);
  }
}

sub rpc_player_chose_suit {
  my $self = shift;
  my ($player, $suit) = @_;
  # Hacky, but who cares.
  $suit = uc(substr($suit, 0, 1));
  $self->{match_card} = '8' . $suit;
}

sub rpc_draw_card {
  my $self = shift;
  my ($card) = @_;
  $self->add_card($card);
  $self->take_turn;
}

sub rpc_start_turn {
  my $self = shift;
  my ($player) = @_;
  if ($player eq $self->nickname) {
    $self->take_turn;
  }
}

=begin algorithm_explanation

Here's what the following method (take_turn) does:

The internal suit_values hash is consulted to determine which suits
the bot most wants to play from. It's re-evaluated with every hand
change. Long suits are favored most. Among suits of equal length, ones
with a higher point total are favored. If there's still a tie, the
equivalent suits receive random favor.

The bot will first attempt to play a card of matching rank (except for
eights) and a stronger suit than the current one.

It will then seek to play the highest card (not counting eights) from
the proper suit.

If both of the above fail, it will seek to play an eight, and then
choose its most favored suit.

=end algorithm_explanation

=cut

sub take_turn {
  my $self = shift;
  my $card = $self->choose_card_to_play;
  return unless $card;
  $self->remove_card($card);
  my ($play_rank, $play_suit) = $self->split_card($card);
  $self->logger->debug("I'm now sending a turn-taking RPC request.");
  $self->make_rpc_request({
			   to=>$self->referee_jid,
			   id=>"go",
			   methodname=>"game.play_card",
			   args=>[$card],
			  });
  $self->logger->debug("I just sent a turn-taking RPC request.");
  if ($play_rank eq '8') {
    # We just played an eight. So we must follow up with a suit choice.
    # We'll declare our best suit.
    my $declaration_suit = ($self->evaluate_suits)[0];
    $self->make_rpc_request({
			     to=>$self->referee_jid,
			     id=>"declare",
			     methodname=>"game.choose_suit",
			     args=>[$declaration_suit],
			    });
  }

  # Maybe say something.
  if (values(%{$self->{cards}}) == 1) {
    $self->send_message({
			 type=>'groupchat',
			 to=>$self->muc_jid,
			 body=>"I have only one card left.",
			});
  }
}

sub choose_card_to_play {
  my $self = shift;
  my $match_card = $self->{match_card};
  my ($match_rank, $match_suit) = $self->split_card($match_card);
  my $match_suit_value = $self->{suit_rank}->{$match_suit};
  $self->logger->debug("I must match the suit $match_suit or the rank $match_rank.");

  #### Seek a suit-switch...
  # Find the most valuable card among the rank-matches.
  my @rank_matches = keys(%{$self->{by_rank}->{$match_rank}});
  if (@rank_matches) {
#      warn "I see rank matches.\n";
    my $best_rank_match;
    my $best_rank_match_value = 0;
    for my $rank_match (@rank_matches) {
      my ($rank_match_rank, $rank_match_suit) = $self->split_card($rank_match);
      if ($self->{suit_rank}->{$rank_match_suit} > $best_rank_match_value) {
#	  warn "Aha, $rank_match_suit seems good.";
	$best_rank_match = $rank_match;
	$best_rank_match_value = $self->{suit_rank}->{$rank_match_suit};
      }
    }
      
    if (($best_rank_match_value > $match_suit_value) or 
	(not(keys(%{$self->{by_suit}->{$match_suit}}))) ) {
      $self->logger->debug("I will match ranks with $best_rank_match.");
      return $best_rank_match;
    }
  }

  #### Seek the best same-suit card.
  if (my $suit_match = $self->find_highest_card_in_suit($match_suit)) {
    $self->logger->debug("I will match suits with $suit_match.");
    return $suit_match;
  }

  #### Seek an eight.
  if (my $eight = $self->remove_eight) {
    $self->logger->debug("I will play an 8.");
    return $eight;
  }

  #### If we get this far, we have no cards to play.
  $self->logger->debug("I have no cards to play!!");
  return;
}


###################
# Internal Methods
###################

# add_card: Given a card descriptor, add it variously to my own orginzational
# hashes.
sub add_card {
  my $self = shift;
  my ($card) = @_;
#  warn "Adding a $card.\n";
  my ($rank, $suit);
  unless (($rank, $suit) = $self->split_card($card)) {
    $self->expire("This doesn't look like a card: $card");
  }
  if ($rank eq '8') {
    push (@{$self->{eights}}, $card);
  } else {
    $self->{cards}->{$card} = 1;
    $self->{by_suit}->{$suit}->{$card} = 1;
    $self->{by_rank}->{$rank}->{$card} = 1;
    $self->{suit_sums}->{$suit} = $self->calculate_suit_sum($suit);
  }
  $self->evaluate_suits;
}

# remove_card: Given a card descriptor, remove it from my own orginzational
# hashes.
# This doesn't work for eights. Use remove_eight instead.
sub remove_card {
  my $self = shift;
  my ($card) = @_;
  my ($rank, $suit);
  unless (($rank, $suit) = $self->split_card($card)) {
    $self->expire("This doesn't look like a card: $card");
  }  
  delete($self->{cards}->{$card});
  delete($self->{by_suit}->{$suit}->{$card});
  delete($self->{by_rank}->{$rank}->{$card});
  $self->{suit_sums}->{$suit} = $self->calculate_suit_sum($suit);
  $self->evaluate_suits;
}

sub remove_eight {
  my $self = shift;
  return pop (@{$self->{eights}});
}

# split_card: If the given string can be split into rank and suit,
# returns them as a list. Otherwise, returns falsehood.
sub split_card {
  my $self = shift;
  my ($card) = @_;
  if (my ($rank, $suit) = $card =~ /^(10|[AKQJ2-9])([DCHS])$/) {
    return ($rank, $suit);
  } else {
    return ();
  }
}

# calculate suit_sum: What is the total point-value of the given suit, as
# represented in my hand?
sub calculate_suit_sum {
  my $self = shift;
  my ($suit) = @_;
  my @cards = keys (%{$self->{by_suit}->{$suit}});
  my $sum = 0;
  for my $card (@cards) {
    my ($rank, $suit) = $self->split_card($card);
    if ($rank =~ /\d/) {
      $sum += $rank;
    } elsif ($rank eq 'A') {
      $sum += 1;
    } elsif ($rank =~ /KQJ/) {
      $sum += 10;
    }
  }
  return $sum;
}

# evaluate_suits: Update the value of the suit_rank hash, based on the logic
# described in the POD under 'Algorithm Explanation'.
sub evaluate_suits {
  my $self = shift;
  my @suits = sort
    {
      my $a_length = values(%{$self->{by_suit}->{$a}});
      my $b_length = values(%{$self->{by_suit}->{$b}});
      if ($a_length > $b_length) {
	return -1;
      } elsif ($a_length < $b_length) {
	return 1;
      } else {
	my $a_value = $self->calculate_suit_sum($a);
	my $b_value = $self->calculate_suit_sum($b);
	if ($a_value > $b_value) {
	  return -1;
	} elsif ($a_value < $b_value) {
	  return 1;
	} else {
	  return 0;
	}
      }
    }
      qw(C D H S);
  my $rank = 1;
  foreach (@suits) {
    $self->{suit_rank}->{$_} = $rank++;
  }
  return @suits;
}

# find_highest_card_in_suit: What it says. Returns a whole card label.
sub find_highest_card_in_suit {
  my $self = shift;
  my ($suit) = @_;
  # We skip eights, since they're not part of any suit, in our reckoning.
  foreach (qw(K Q J 10 9 7 6 5 4 3 2 A)) {
    my $card = "$_$suit";
    if (exists($self->{cards}->{$card})) {
      return $card;
    }
  }
  # If we got this far, the suit is empty...
}

1;
