package Volity::Game::RPS;

use warnings;
use strict;

use base qw(Volity::Game);
use fields qw(no_ties best_of);

# Volity variables
__PACKAGE__->max_allowed_players(2);
__PACKAGE__->min_allowed_players(2);
__PACKAGE__->uri("http://volity.org/games/rps");
__PACKAGE__->player_class("Volity::Player::RPS");
__PACKAGE__->name("Jmac's RPS");
__PACKAGE__->description("The offcial Volity.net RPS implementation, by Jason Mcintosh.");
__PACKAGE__->ruleset_version("1.0");

# We'll use the initialize sub to set up the config variables.
sub initialize {
    my $self = shift;
    $self->SUPER::initialize(@_);
    $self->register_config_variables(qw(no_ties best_of));
    $self->best_of(1);
    $self->no_ties(0);
    return $self;
}

sub start {
  my $self = shift;
  for my $player ($self->players) { 
    $player->hand(undef);
    $player->hands_won(0);
  }
}
			      
sub set_hand {
  my $self = shift;
  my ($player, $hand) = @_;
  if (substr("rock", 0, length($hand)) eq lc($hand)) {
    $player->hand('rock');
  } elsif (substr("paper", 0, length($hand)) eq lc($hand)) {
    $player->hand('paper');
  } elsif (substr("scissors", 0, length($hand)) eq lc($hand)) {
    $player->hand('scissors');
  } else {
    # I don't know what this hand type is.
    return (fault=>901);
  }

  # Has everyone registered a hand?
  if (grep(defined($_), map($_->hand, $self->players)) == $self->players) {
    # Yes! Time for BATTLE!
    # Sort the players into winning order.
      my @players = sort( {
			 my $handa = $a->hand; my $handb = $b->hand;
			 if ($handa eq $handb) {
			   return 0;
			 } elsif ($handa eq 'rock' and $handb eq 'scissors') {
			   return -1;
			 } elsif ($handa eq 'scissors' and $handb eq 'paper') {
			   return -1;
			 } elsif ($handa eq 'paper' and $handb eq 'rock') {
			   return -1;
			 } else {
			   return 1;
			 }
		       }
			$self->players);
    # Tell both players what their opponent chose
    for my $player (@players) {
      $self->call_ui_function_on_everyone('player_chose_hand',
					  $player->nick,
					  $player->hand,
					 );
    }
    # Tell the players who won, using Jabber messaging (just because we can).
    my $victory_message;
    if ($players[0]->hand eq $players[-1]->hand) {
      $victory_message = sprintf("A tie! Both players chose %s.", $players[0]->hand);
      if ($self->no_ties) {
	  $victory_message .= " But we're not counting ties! The game continues.";
      } else {
	  foreach(@players) { $_->win_hand }
      }
    } else {
      if ($players[0]->hand eq 'rock') {
	$victory_message = sprintf("%s(rock) crushes %s(scissors)!", $players[0]->nick, $players[1]->nick);
#	$self->winners($players[0], $players[1]);
	$players[0]->win_hand;
      } elsif ($players[0]->hand eq 'scissors') {
	$victory_message = sprintf("%s(scissors) shreds %s(paper)!", $players[0]->nick, $players[1]->nick);
	$self->winners($players[0], $players[1]);
	$players[0]->win_hand;
      } else {
	$victory_message = sprintf("%s(paper) smothers %s(rock)!", $players[0]->nick, $players[1]->nick);
	$self->winners($players[0], $players[1]);
	$players[0]->win_hand;
      }
    }

      # Did anyone win?
      my @winners = (grep($self->check_player_for_victory($_), @players));
      if (@winners) {
	  $self->winners(@winners);
	  $victory_message .= " Game over!!";
      } else {
	  $victory_message .= " Play again!!";
	  # Reset the players' hands.
	  foreach (@players) { $_->hand(undef) }
      }

      $self->referee->groupchat($victory_message);
      $self->end if @winners;
  }

  # At any rate, return undef, so the calling player gets a generic
  # success response message.
  return;
  
}

sub check_player_for_victory {
    my $self = shift;
    my ($player) = @_;
    if ($player->hands_won >= $self->best_of / 2) {
	return 1;
    } else {
	return 0;
    }
}

####################
# Incoming RPC request handlers
###################

sub rpc_choose_hand {
  my $self = shift;
  $self->set_hand(@_);
}

sub rpc_no_ties {
    my $self = shift;
    my ($player, $new_value) = @_;
    my $current_value = $self->no_ties;
    if ($new_value ne $current_value) {
	if ($new_value eq '0') {
	    $self->no_ties(0);
	    $self->call_ui_function_on_everyone(no_ties=>$player->nick, 0);
	    $self->unready_all_players;
	    return $new_value;
	} elsif ($new_value eq '1') {
	    $self->no_ties(1);
	    $self->call_ui_function_on_everyone(no_ties=>$player->nick, 1);
	    $self->unready_all_players;
	    return $new_value;
	} else {
	    return(fault=>910, "Invalid argument ($new_value) to no_ties. Must be 1 or 0.");
	}
    }
    $self->config_variable_setters->{no_ties} = $player;
    return;
}
	
sub rpc_best_of {
    my $self = shift;
    my ($player, $new_value) = @_;
    my $current_value = $self->best_of;
    if ($new_value ne $current_value) {
	if (($new_value > 0) and ($new_value % 2)) {
	    $self->best_of($new_value);
	    $self->call_ui_function_on_everyone('best_of', $player->nick, $new_value);
	    $self->unready_all_players;
	    return;
	} else {
	    return(fault=>910, "Invalid argument ($new_value) to best_of. Must be a positive, odd number.");
	}
    }
    $self->config_variable_setters->{best_of} = $player;
    return;
}


package Volity::Player::RPS;

use base qw(Volity::Player);
use fields qw(hand hands_won);

# win_hand: called by the game object when the player wins a hand.
sub win_hand {
    my $self = shift;
    $self->{hands_won}++;
}

1;
