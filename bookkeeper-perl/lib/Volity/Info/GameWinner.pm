package Volity::Info::GameWinner;

use warnings; use strict;

use base qw(Volity::Info);

Volity::Info::GameWinner->table('game_winner');
Volity::Info::GameWinner->columns(Primary=>qw(game_id player_id));
Volity::Info::GameWinner->has_a(game_id=>"Volity::Info::Game");
Volity::Info::GameWinner->has_a(player_id=>"Volity::Info::Player");
