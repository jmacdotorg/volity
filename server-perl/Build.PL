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
	       'POE::Filter::XML'=>'0.24',
	       'Class::Accessor'=>undef,
	       'Class::Fields'=>undef,
	       'Class::Data::Inheritable'=>undef,
	       'Time::HiRes'=>undef,
	       'Tie::Exechash'=>undef,
	       'JavaScript::SpiderMonkey'=>undef,
	       'Data::JavaScript::Anon'=>undef,
	       'Date::Parse'=>undef,
	       'YAML'=>undef,
	       'Log::Log4perl'=>undef,
	       'LWP'=>undef,
	       'Archive::Zip'=>undef,
	       'Date::Parse'=>undef,
	       'Date::Format'=>undef,
	       'PXR::Node'=>undef,
	       'Locale::Language'=>undef,
	       'Term::Readkey'=>undef,
	     },
   script_files=>['bin/volityd', 'bin/friv'],
   install_path=>{script=>'/usr/local/volity/bin'},
  );

$build->create_build_script;

