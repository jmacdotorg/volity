use warnings;
use strict;

use Module::Build;
my $build = Module::Build->new
  (
   module_name=>'Volity::Bookkeeper',
   license=>'lgpl',
   requires=> {
	       'Volity'=>undef,
	     }
  );
$build->create_build_script;


