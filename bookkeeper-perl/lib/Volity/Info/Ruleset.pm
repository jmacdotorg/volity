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
Volity::Info::Ruleset->set_sql(with_player => "select distinct ruleset.id from ruleset, game_player, game where game.id = game_player.game_id and game_player.player_id = ? and game.ruleset_id = ruleset.id");
