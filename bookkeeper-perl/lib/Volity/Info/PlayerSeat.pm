package Volity::Info::PlayerSeat;

use warnings; use strict;

use base qw(Volity::Info);

Volity::Info::PlayerSeat->table('player_seat');
Volity::Info::PlayerSeat->columns(All=>qw(id seat_id player_id));
Volity::Info::PlayerSeat->has_a(player_id=>"Volity::Info::Player");
Volity::Info::PlayerSeat->has_a(seat_id=>"Volity::Info::Seat");
