package Volity::Info::Game;

use warnings;
use strict;

use base qw(Volity::Info);
use fields qw(start_time end_time server_jid signature uri players winners quitters name description);

use Data::Lazy;
use Carp qw(carp croak);

our %known_games;

sub initialize {
  my $self = shift;
  $self->SUPER::initialize(@_);
  foreach (qw(players winners quitters)) {
    my $method = "fetch_${_}_from_db";
    tie ($self->{$_}, "Data::Lazy", sub {$self->$method});
  }
}

# XXX Ew... refactor these methods, please.
sub fetch_players_from_db {
  my $self = shift;
  my $dbh = $self->dbh->clone;
  $dbh->select({fields=>'PLAYER.JID',
		table=>'GAME, PLAYER, GAME_PLAYER', 
		where=>{'GAME.ID'=>$self->id,},
		join=>['GAME_PLAYER.GAME_ID = GAME.ID',
		       'GAME_PLAYER.PLAYER_JID = PLAYER.JID',
		      ],
	      });
  my @players;
  while (my ($player_id) = $dbh->fetchrow_array) {
    my $player = Volity::Info::Player->new({jid=>$player_id});
    push (@players, $player);
  }
  $self->players(@players);
  return $self->{players};
}

sub fetch_winners_from_db {
  my $self = shift;
  my $dbh = $self->dbh->clone;
  $dbh->opt(saveSQL=>1);
  $dbh->select({fields=>'PLAYER_JID',
		table=>'GAME, GAME_WINNER', 
		where=>{'GAME.ID'=>$self->id,},
		join=>['GAME_WINNER.GAME_ID = GAME.ID',
		      ],
	      });
  my @players;
  while (my ($player_id) = $dbh->fetchrow_array) {
    my $player = Volity::Info::Player->new({jid=>$player_id});
    push (@players, $player);
  }
  $self->winners(@players);
  return $self->{winners};
}

sub fetch_quitters_from_db {
  my $self = shift;
  my $dbh = $self->dbh->clone;
  $dbh->select({fields=>'PLAYER_JID',
		table=>'GAME, GAME_QUITTER', 
		where=>{'GAME.ID'=>$self->id,},
		join=>['GAME_QUITTER.GAME_ID = GAME.ID',
		      ],
	      });
  my @players;
  while (my ($player_id) = $dbh->fetchrow_array) {
    my $player = Volity::Info::Player->new({jid=>$player_id});
    push (@players, $player);
  }
  $self->quitters(@players);
  return $self->{quitters};
}

sub fetch_attributes_from_db {
  my $self = shift;
  my $dbh = $self->dbh;
  $dbh->select('*', 'GAME', {ID=>$self->id});
  my $data = $dbh->fetchrow_hashref;
  foreach (keys(%$data)) {
    $self->$_($$data{$_});
  }

  # Fetch the game's name from the URI table, if we know this game's URI...
  if (defined($self->uri)) {
    $dbh->select('NAME, DESCRIPTION', 'URI', {uri=>$self->uri});
    my $data = $dbh->fetchrow_hashref;
    foreach (keys(%$data)) {
      my $method = lc($_);
      $self->$method($$data{$_});
    }
  }
}

sub known_object_hashref {
  return \%known_games;
}

1;
