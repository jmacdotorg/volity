use warnings;
use strict;

use Module::Build;
my $build = Module::Build->new
  (
   module_name=>'Volity',
   license=>'perl',
   requires=> {
	       'POE'=>undef,
	       'Scalar::Util'=>undef,
	       'RPC::XML'=>undef,
	       'POE::Component::Jabber'=>'0.3.3',
	       'XML::Writer'=>undef,
	       'Data::Lazy'=>undef,
	     },
   script_files=>[glob("bin/*.pl")],
   install_path=>{script=>'/usr/local/volity/bin'},
  );
$build->create_build_script;


