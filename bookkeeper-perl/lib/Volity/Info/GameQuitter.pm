package Volity::Info::GameQuitter;

use warnings; use strict;

use base qw(Volity::Info);

Volity::Info::GameQuitter->table('game_quitter');
Volity::Info::GameQuitter->columns(Primary=>qw(game_id player_id));
Volity::Info::GameQuitter->has_a(game_id=>"Volity::Info::Game");
Volity::Info::GameQuitter->has_a(player_id=>"Volity::Info::Player");
