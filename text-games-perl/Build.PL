use warnings;
use strict;
use Config;
use File::Spec;

use Module::Build;
my $libdir = File::Spec->catdir($Config{installsitelib}, 'Volity');

my $build = Module::Build->new
  (
   dist_name=>'volity-textgames',
   dist_version=>'0.2',
   license=>'lgpl',
   install_path=>{
		  lib=>$libdir,
		},
   requires=> {
	       'Volity'=>undef,
	     },
  );
$build->create_build_script;


