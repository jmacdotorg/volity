package Volity::Info::Ruleset;

use warnings; use strict;

use base qw(Volity::Info);
use Volity::Info::Game;

__PACKAGE__->table('ruleset');
__PACKAGE__->columns(All=>qw(id uri name description));
