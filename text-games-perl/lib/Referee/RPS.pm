package Volity::Referee::RPS;

# Game referee for Rock Paper Scissors.

use warnings;
use strict;

use base qw(Volity::Referee);
use Volity::GameRecord;

sub initialize {
  my $self = shift;
  # Set up game-start requirements.
  $self->max_allowed_players(2);
  $self->min_allowed_players(2);
  $self->uri('http://volity.org/games/rps');
  $self->game_class("Volity::Game::RPS");
  $self->SUPER::initialize(@_);
}

1;
