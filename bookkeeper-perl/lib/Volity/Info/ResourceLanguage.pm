package Volity::Info::ResourceLanguage;

use warnings;
use strict;

use base qw(Volity::Info);

__PACKAGE__->table('resource_language');
__PACKAGE__->columns(All=>qw(id resource_id language_code));
__PACKAGE__->has_a(resource_id=>"Volity::Info::Resource");

1;
