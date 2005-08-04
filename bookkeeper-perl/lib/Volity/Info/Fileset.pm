package Volity::Info::Fileset;

use warnings;
use strict;

use base qw(Volity::Info);

__PACKAGE__->table('ui_fileset');
__PACKAGE__->columns(All=>qw(id name description url));
__PACKAGE__->has_a(player_id=>"Volity::Info::Player");

1;
