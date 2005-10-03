use warnings;
use strict;
use Config;
use Module::Build;

my $build = Module::Build->new
  (
   dist_name=>'volity-rps',
   dist_version=>'0.5.1',
   license=>'lgpl',
   requires=> {
	       'Volity'=>'0.5.1',
	     },
  );
$build->create_build_script;


