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
	       'POE::Component::Jabber'=>'1.0',
	       'XML::Writer'=>undef,
	       'Data::Lazy'=>undef,
	       'IPC::Shareable'=>undef,
	       'Class::Accessor'=>undef,
	       'Class::Fields'=>undef,
	       'Time::HiRes'=>undef,
	       'JavaScript::SpiderMonkey'=>undef,
	       'Data::JavaScript::Anon'=>undef,
	       'YAML'=>undef,
	     },
   script_files=>[glob("bin/*")],
   install_path=>{script=>'/usr/local/volity/bin'},
  );

$build->create_build_script;

