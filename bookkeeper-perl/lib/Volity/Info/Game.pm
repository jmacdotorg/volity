package Volity::Info::Game;

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

Volity::Info::Game->table('game');
Volity::Info::Game->columns(All=>qw(id start_time end_time server_id referee_jid signature ruleset_id));
Volity::Info::Game->has_a(ruleset_id=>"Volity::Info::Ruleset");
Volity::Info::Game->has_a(server_id=>"Volity::Info::Server");
Volity::Info::Game->has_many(seats=>["Volity::Info::GameSeat" => 'seat_id'], 'game_id');
Volity::Info::Game->has_many(game_seats=>"Volity::Info::GameSeat", "game_id");

sub uri {
  my $self = shift;
  return $self->ruleset_id->uri;
}

sub name {
  my $self = shift;
  return $self->uri_id->name;
}

sub description {
  my $self = shift;
  return $self->uri_id->description;
}

Volity::Info::Game->set_sql(with_player =>
			    "select distinct game_seat.game_id from game_seat, player_seat where game_seat.seat_id = player_seat.seat_id and player_seat.player_id = ?");

Volity::Info::Game->set_sql(with_player_and_player =>
			    "
    SELECT DISTINCT game_seat.game_id
    FROM game_seat
    INNER JOIN player_seat ON
        game_seat.seat_id = player_seat.seat_id AND                       
        player_seat.player_id = ?
    INNER JOIN game_seat AS gs2 ON game_seat.game_id=gs2.game_id
    INNER JOIN player_seat AS ps2 ON
        gs2.seat_id = ps2.seat_id AND
        ps2.player_id = ?"
			    );

Volity::Info::Game->set_sql(with_player_and_limits_by_end_time =>
			    "select distinct game.id from game, game_seat, player_seat where game.id = game_seat.game_id and game_seat.seat_id = player_seat.seat_id and player_seat.player_id = ? order by game.end_time desc limit ?, ?");

Volity::Info::Game->set_sql(with_player_and_ruleset =>
			    "select distinct game.id from game, game_seat, player_seat where game.id = game_seat.game_id and game_seat.seat_id = player_seat.seat_id and player_seat.player_id = ? and game.ruleset_id = ?");

Volity::Info::Game->set_sql(with_seat_and_ruleset =>
			    "select distinct game.id from game, game_seat where game.id = game_seat.game_id and game_seat.seat_id = ? and game.ruleset_id = ?");

Volity::Info::Game->set_sql(with_ruleset_by_end_time =>
                            "select distinct game.id from game where game.ruleset_id = ? order by game.end_time desc limit ?, ?");

Volity::Info::Game->set_sql(with_ruleset_and_seat_by_end_time =>
                            "select distinct game.id from game, game_seat where game.ruleset_id  = ? and game.id = game_seat.game_id and game_seat.seat_id = ? order by game.end_time desc limit ?, ?");

Volity::Info::Game->set_sql(by_end_time =>
                            "select distinct game.id from game order by game.end_time desc limit ?, ?");

Volity::Info::Game->set_sql(unfinished_with_referee_jid =>
			    "select id from game where end_time is null and referee_jid = ?");

1;
