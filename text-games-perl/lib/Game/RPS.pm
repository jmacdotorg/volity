package Volity::Game::RPS;

use warnings;
use strict;

use base qw(Volity::Game);

__PACKAGE__->max_allowed_players(2);
__PACKAGE__->min_allowed_players(2);
__PACKAGE__->uri("http://volity.org/games/rps");
__PACKAGE__->player_class("Volity::Player::RPS");

sub start_game {
  my $self = shift;
  for my $player ($self->players) { 
    $player->hand(undef);
  }
}
			      
sub set_hand {
  my $self = shift;
  my ($player, $hand) = @_;
#  warn "Got: $player, $hand";
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
      $self->referee->groupchat("Ok...");
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
      $self->winners([[@players]]);
    } else {
      if ($players[0]->hand eq 'rock') {
	$victory_message = sprintf("%s(rock) crushes %s(scissors)!", $players[0]->nick, $players[1]->nick);
	$self->winners($players[0], $players[1]);
      } elsif ($players[0]->hand eq 'scissors') {
	$victory_message = sprintf("%s(scissors) shreds %s(paper)!", $players[0]->nick, $players[1]->nick);
	$self->winners($players[0], $players[1]);
      } else {
	$victory_message = sprintf("%s(paper) smothers %s(rock)!", $players[0]->nick, $players[1]->nick);
	$self->winners($players[0], $players[1]);
      }
    }
    $self->referee->groupchat($victory_message);
    $self->end_game;
} else {
    $self->referee->groupchat("Enh...");
}

  # At any rate, return undef, so the calling player gets a generic
  # success response message.
  return;
  
}

####################
# Incoming RPC request handlers
###################

sub rpc_choose_hand {
  my $self = shift;
  $self->set_hand(@_);
}

package Volity::Player::RPS;

use base qw(Volity::Player);
use fields qw(hand);

1;
