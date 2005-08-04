package Volity::Info::Ruleset;

use warnings; use strict;

use base qw(Volity::Info);
use Volity::Info::Game;

__PACKAGE__->table('ruleset');
__PACKAGE__->columns(All=>qw(id uri name description));

##########################
# Ima::DBI class methods
##########################

# search_with_player: returns the rulesets associated with the given player.
Volity::Info::Ruleset->set_sql(with_player => "select distinct ruleset.id from ruleset, game_seat, seat_player, game where game.id = game_seat.game_id and game_seat.seat_id = seat_player.seat_id and seat_player.player_id = ? and game.ruleset_id = ruleset.id");

# search_with_seat: returns the rulesets associated with the given seat.
Volity::Info::Ruleset->set_sql(with_seat => "select distinct ruleset.id from ruleset, game_seat, game where game.id = game_seat.game_id and game_seat.seat_id = ? game.ruleset_id = ruleset.id");

1;
