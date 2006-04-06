package Volity::Info::GameSeat;

use warnings; use strict;

use base qw(Volity::Info);

Volity::Info::GameSeat->table('game_seat');
Volity::Info::GameSeat->columns(All=>qw(id game_id seat_id rating place seat_name));
Volity::Info::GameSeat->has_a(game_id=>"Volity::Info::Game");
Volity::Info::GameSeat->has_a(seat_id=>"Volity::Info::Seat");

Volity::Info::GameSeat->set_sql(with_seat_and_ruleset_most_recently =>
				"select distinct game_id, seat_id from game, game_seat where game.id = game_seat.game_id and seat_id = ? and ruleset_id = ? order by end_time desc limit 0, 1");

1;
