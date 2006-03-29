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
				       'Date::Parse'=>'',
				       'Log::Log4perl'=>'',
				       'LWP'=>'',
				       'Date::Parse'=>'',
				       'Date::Format'=>'',
				       'PXR::Node'=>'',
				       'Locale::Language'=>'',
				       ],
			   );

WriteMakefile(
	      AUTHOR => 'Jason McIntosh <jmac@jmac.org>',
	      ABSTRACT => "Perl libraries for creating Volity referees, parlors, and bots",
	      NAME => "Frivolity",
	      VERSION_FROM => "lib/Volity.pm",
	      DISTNAME => "Frivolity",
	      EXE_FILES => ["bin/volityd"],
	      INSTALLSCRIPT => "/usr/local/volity/bin",
	      );

