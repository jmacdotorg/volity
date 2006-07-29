package Volity::Info::ScheduledGamePlayer;

use base qw(Volity::Info);

Volity::Info::ScheduledGamePlayer->table('scheduled_game_player');
Volity::Info::ScheduledGamePlayer->columns(Primary=>qw(scheduled_game_id player_id));

Volity::Info::ScheduledGamePlayer->has_a(scheduled_game_id=>"Volity::Info::ScheduledGame");
Volity::Info::ScheduledGamePlayer->has_a(player_id=>"Volity::Info::Player");

Volity::Info::Player->has_many(scheduled_games=>["Volity::Info::ScheduledGamePlayer" => scheduled_game_id]);
Volity::Info::ScheduledGame->has_many(players=>["Volity::Info::ScheduledGamePlayer" => player_id]);

1;

