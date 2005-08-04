package Volity::Info::GameSeat;

use warnings; use strict;

use base qw(Volity::Info);

Volity::Info::GameSeat->table('game_seat');
Volity::Info::GameSeat->columns(Primary=>qw(game_id seat_id));
Volity::Info::GameSeat->columns(Essential=>qw(rating place));
Volity::Info::GameSeat->has_a(game_id=>"Volity::Info::Game");
Volity::Info::GameSeat->has_a(seat_id=>"Volity::Info::Seat");


1;
