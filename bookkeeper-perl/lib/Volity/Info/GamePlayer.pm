package Volity::Info::GamePlayer;

use warnings; use strict;

use base qw(Volity::Info);

Volity::Info::GamePlayer->table('game_player');
Volity::Info::GamePlayer->columns(Primary=>qw(game_id player_id));
Volity::Info::GamePlayer->columns(Essential=>qw(rating place));
Volity::Info::GamePlayer->has_a(game_id=>"Volity::Info::Game");
Volity::Info::GamePlayer->has_a(player_id=>"Volity::Info::Player");
