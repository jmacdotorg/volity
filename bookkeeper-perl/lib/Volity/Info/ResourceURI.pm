package Volity::Info::ResourceURI;

use warnings; use strict;

use base qw(Volity::Info);
use Volity::Info::Game;

__PACKAGE__->table('resource_uri');
__PACKAGE__->columns(All=>qw(id uri description));
__PACKAGE__->has_many(resources=>"Volity::Info::Resource", "resource_id");

1;
