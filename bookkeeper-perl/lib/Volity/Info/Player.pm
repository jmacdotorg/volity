package Volity::Info::Player;

use warnings;
use strict;

use base qw(Volity::Info);
use fields qw(email first last jid games quits wins);

our %known_players;

use Data::Lazy;
use Carp qw(carp croak);
use Volity::Info::Game;
#use Volity::GameRecord;

sub initialize {
  my $self = shift;
  $self->SUPER::initialize(@_);
  unless (defined($self->jid)) {
    croak ("You must initialize a player-info object with a JID!");
  }
  $self->id($self->jid);
  foreach (qw(games quits wins)) {
    my $method = "fetch_${_}_from_db";
    tie ($self->{$_}, "Data::Lazy", sub {$self->$method});
  }
}

sub fetch_attributes_from_db {
  my $self = shift;
  my $dbh = $self->dbh;
  $dbh->select('*', 'PLAYER', {JID=>$self->jid});
  my $data = $dbh->fetchrow_hashref;
  foreach (keys(%$data)) {
    $self->$_($$data{$_});
  }
}

sub fetch_games_from_db {
  my $self = shift;
  my @games = $self->fetch_gamestats_from_db('game_player');
  $self->games(@games);
  return $self->{games};
}

sub fetch_wins_from_db {
  my $self = shift;
  my @games = $self->fetch_gamestats_from_db('game_winner');
  $self->wins(@games);
  return $self->{wins};
}

sub fetch_quits_from_db {
  my $self = shift;
  my @games = $self->fetch_gamestats_from_db('game_quitter');
  $self->quits(@games);
  return $self->{quits};
}

sub fetch_gamestats_from_db {
  my $self = shift;
  my $dbh = $self->dbh->clone;
  my $linking_table = $_[0];

  $dbh->select({fields=>'GAME.ID',
		table=>"GAME, PLAYER, $linking_table",
		where=>{"PLAYER.JID"=>$self->jid,},
		join=>[
		"$linking_table.PLAYER_JID = PLAYER.JID",
		"$linking_table.GAME_ID = GAME.ID",
		      ],
	      });
  my @games;
  while (my $data = $dbh->fetchrow_hashref) {
    my $game = Volity::Info::Game->new({id=>$$data{ID}});
#    my $game = Volity::GameRecord->new({id=>$$data{ID}});
    push (@games, $game);
  }
  return @games;
}

sub store_in_db {
  my $self = shift;
  my $dbh = $self->dbh;
  # Store attributes
  my $values = {last=>$self->last,
		first=>$self->first,
		email=>$self->email,
		jid=>$self->jid,
	      };
  $dbh->replace('PLAYER', $values);

  # We don't do anything with game links here... that's handled
  # only by the add_game methods. (Nope, no accessors for deleting
  # game records from a player. No need.)
}

# Game-adding methods
# These all take a game info object as an argument, and link the player
# with that game in the specified manner.
# They also perform DB writes as needed.

sub add_game {
  my $self = shift;
  my ($game) = @_;
  $self->check_game_object($game);
  my $dbh = $self->dbh;
  push (@{$self->games}, $game);
  $dbh->insert('GAME_PLAYER', {PLAYER_JID=>$self->jid,
			       GAME_ID=>$game->id,
			     });
}

sub add_winner {
  my $self = shift;
  my ($game) = @_;
  $self->check_game_object($game);
  my $dbh = $self->dbh;
  push (@{$self->wins}, $game);
  $dbh->insert('GAME_WINNER', {PLAYER_JID=>$self->jid,
			       GAME_ID=>$game->id,
			     });
}

sub add_quit {
  my $self = shift;
  my ($game) = @_;
  $self->check_game_object($game);
  my $dbh = $self->dbh;
  push (@{$self->quits}, $game);
  $dbh->insert('GAME_QUITTER', {PLAYER_JID=>$self->jid,
			       GAME_ID=>$game->id,
			     });
}

sub check_game_object {
  my $self = shift;
  my ($game) = @_;
  unless ($game->isa("Volity::Info::Game")) {
    croak ("You must pass in a game info object. Instead I got this: $game");
  }
}

sub known_object_hashref {
  return \%known_players;
}
  
sub id_column { "jid" }

1;
