package Volity::Info::ScheduledGame;

use warnings;
use strict;

use DateTime;

use base qw(Volity::Info);
Volity::Info::ScheduledGame->table('scheduled_game');
Volity::Info::ScheduledGame->columns(All=>qw(id parlor_id creator_id time message is_open));
Volity::Info::ScheduledGame->has_a(creator_id=>"Volity::Info::Player");
Volity::Info::ScheduledGame->has_a(parlor_id=>"Volity::Info::Server");

Volity::Info::ScheduledGame->set_sql(with_ruleset=>"select scheduled_game.id from scheduled_game, server, ruleset where scheduled_game.parlor_id = server.id and ruleset.id = server.ruleset_id and ruleset.id = ?");

sub search_with_current_minute {
    my $class = shift;
    my $dt = DateTime->now;
    my $time_string = sprintf ("%02d-%02d-%02d %02d:%02d:00",
			       $dt->year,
			       $dt->month,
			       $dt->mday,
			       $dt->hour,
			       $dt->minute,
			       );
    return $class->search(time=>$time_string);
}

1;
