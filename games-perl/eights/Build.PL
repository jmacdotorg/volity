use warnings;
use strict;
use Config;
use Module::Build;

my $build = Module::Build->new
  (
   dist_name=>'volity-eights',
   dist_version=>'0.3.5',
   license=>'lgpl',
   requires=> {
	       'Volity'=>'0.3.5',
	     },
  );
$build->create_build_script;


