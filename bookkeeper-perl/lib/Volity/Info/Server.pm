package Volity::Info::Server;

use warnings; use strict;

use base qw(Volity::Info);
use Volity::Info::Game;

__PACKAGE__->table('server');
__PACKAGE__->columns(All=>qw(id public_key jid player_id ruleset_id));
__PACKAGE__->has_a(player_id=>"Volity::Info::Player");
__PACKAGE__->has_a(ruleset_id=>"Volity::Info::Ruleset");
