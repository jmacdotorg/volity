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
	       'IPC::Shareable'=>undef,
	       'Class::Accessor'=>undef,
	       'Class::Fields'=>undef,
	     },
   script_files=>[glob("bin/*.pl")],
   install_path=>{script=>'/usr/local/volity/bin'},
  );

print "To run tests, I need to connect to a Jabber server.\n";
print "Please provide me with some authentication info I can use.\n";
print "When a [default] appears, you can accept it by not specifying a value.\n";
print "See the README file for more details.\n";
$build->notes(jabber_host=>$build->prompt('Jabber host:'));
my $default_muc_host = "conference." . $build->notes("jabber_host");
$build->notes(jabber_muc_host=>$build->prompt("Jabber MUC host [$default_muc_host]:") || $default_muc_host);
$build->notes(jabber_user=>$build->prompt('Jabber user:'));
$build->notes(jabber_password=>$build->prompt('Jabber password:'));

print "You can specify the resources I'll use, or just hit RET to accept the deauts.\n";
$build->notes(server_resource=>
	      $build->prompt('Game server resource [game]:') || 'game');
$build->notes(player_resource1=>
	      $build->prompt('Test player resource [bot1]:') || 'bot1');
$build->notes(player_resource2=>
	      $build->prompt('Test player resource [bot2]:') || 'bot2');
$build->notes(player_resource3=>
	      $build->prompt('Test player resource [bot3]:') || 'bot3');


$build->create_build_script;

