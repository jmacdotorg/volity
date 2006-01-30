use warnings;
use strict;

use lib "inc";
use ExtUtils::AutoInstall (
			   -core   => [
				       "POE"=>'',
				       "RPC::XML"=>'',
				       "Scalar::Util"=>'',
				       'POE::Component::Jabber'=>'1.0',
				       'POE::Filter::XML'=>'0.24',
				       'Class::Accessor'=>'',
				       'Class::Fields'=>'',
				       'Class::Data::Inheritable'=>'',
				       'Time::HiRes'=>'',
				       'Tie::Exechash'=>'',
				       'Data::JavaScript::Anon'=>'',
				       'Date::Parse'=>'',
				       'Log::Log4perl'=>'',
				       'LWP'=>'',
				       'Archive::Zip'=>'',
				       'Date::Parse'=>'',
				       'Date::Format'=>'',
				       'PXR::Node'=>'',
				       'Locale::Language'=>'',
				       ],
			   "Friv (command-line client)"  => [
				       'Term::Readkey'=>'',
				       'JavaScript::SpiderMonkey'=>'',
				       'YAML'=>'',
				       ],
			   
			   );

WriteMakefile(
	      AUTHOR => 'Jason McIntosh <jmac@jmac.org>',
	      ABSTRACT => "Perl libraries for creating Volity referees, parlors, and bots",
	      NAME => "Frivolity",
	      VERSION_FROM => "lib/Volity.pm",
	      DISTNAME => "Frivolity",
	      EXE_FILES => ["bin/friv", "bin/volityd"],
	      INSTALLSCRIPT => "/opt/volity/bin",
	      );
