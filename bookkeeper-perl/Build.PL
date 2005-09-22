use warnings;
use strict;

use Module::Build;
my $build = Module::Build->new
  (
   module_name=>'Volity::Bookkeeper',
   license=>'lgpl',
   requires=> {
	       'Volity'=>undef,
	       'Class::DBI'=>undef,
               'DBIx::Abstract'=>undef,
	     },
   script_files=>[glob("bin/*.pl")],
   install_path=>{script=>'/usr/local/volity/bin'},
  );
$build->create_build_script;


