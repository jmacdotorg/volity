package Volity::Info::Player;

############################################################################
# LICENSE INFORMATION - PLEASE READ
############################################################################
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
############################################################################

use warnings;
use strict;

use base qw(Volity::Info);

Volity::Info::Player->table('player');
Volity::Info::Player->columns(All=>qw(id jid name email));
Volity::Info::Player->has_many(games=>["Volity::Info::GamePlayer" => 'game_id'], 'player_id');
Volity::Info::Player->has_many(quits=>["Volity::Info::GameQuitter" => 'game_id'], 'player_id');

# Set some Ima::DBI SQL statements.
Volity::Info::Player->set_sql(current_rating_by_uri=>qq{select rating from game_player, game, ruleset where game.id = game_player.game_id and ruleset.id = game.ruleset_id and game_player.player_id = ? and ruleset.uri = ? order by game.end_time desc limit 0,1});

Volity::Info::Player->set_sql(current_rating_by_ruleset=>qq{select rating from game_player, game where game.id = game_player.game_id  and game_player.player_id = ? and game.ruleset_id = ? order by game.end_time desc});

Volity::Info::Player->set_sql(number_of_games_played_by_ruleset=>qq{select count(game_player.game_id) from game_player, game where game.id = game_player.game_id and game_player.player_id = ? and game.ruleset_id = ?});

# current_rating_for_uri: Return the player's current ranking for the given
# ruleset URI. Defaults to 1500, if the player has no ranking.
sub current_rating_for_uri {
  my $self = shift;
  my ($uri) = @_;
  my $sth = $self->sql_current_rating_by_uri;
  $sth->execute($self->id, $uri);
  my ($rating) = $sth->fetch;
  $rating ||= 1500;
  $sth->finish;
  return $rating;
}

# current_rating_for_ruleset: as above, but takes a Volity::Info::Ruleset
# object instead.
sub current_rating_for_ruleset {
  my $self = shift;
  my ($ruleset) = @_;
  my @caller = caller;
  my $sth = $self->sql_current_rating_by_ruleset;
  $sth->execute($self->id, $ruleset->id);
  my ($rating) = $sth->fetch;
  # This second attempt with eval is a hack, but I dunno how to work around it.
  # I kept getting undef where I shouldn't have... bizzare. Oh well.
  ($rating) = eval { $sth->fetch } unless defined($rating);
  $rating ||= 1500;
  $sth->finish;
  return $rating;
}

sub number_of_games_played_for_ruleset {
  my $self = shift;
  my ($ruleset) = @_;
  my $sth = $self->sql_number_of_games_played_by_ruleset;
  $sth->execute($self->id, $ruleset->id);
  my ($number) = $sth->fetch;
  $sth->finish;
  return $number;
}

1;
