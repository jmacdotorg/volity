package Volity::Referee::RPS;

# Game referee for Rock Paper Scissors.

use warnings;
use strict;

use base qw(Volity::Referee);
use Volity::GameRecord;

sub initialize {
  my $self = shift;
  # Set up GPG config.
#  # XXX HARDCODED
#  $Volity::GameRecord::gpg_bin = '/usr/local/bin/gpg';
#  $Volity::GameRecord::gpg_secretkey = '4964391E';
#  $Volity::GameRecord::gpg_passphrase = "I've seen things, I've seen them with my eyes";
#  # XXX END HARDCODED
  # Set up game-start requirements.
  $self->max_allowed_players(2);
  $self->min_allowed_players(2);
  $self->uri('http://volity.org/games/rps');
  $self->game_class("Volity::Game::RPS");
  $self->SUPER::initialize(@_);
}

1;
