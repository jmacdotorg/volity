use warnings;
use strict;

use lib "inc";
use ExtUtils::AutoInstall (
			   -core   => [
				       "Volity"=>"0.5.2",
				       ],
			   
			   );

WriteMakefile(
	      AUTHOR => 'Jason McIntosh <jmac@jmac.org>',
	      ABSTRACT => "Perl modules for running a Tic Tac Toe Volity parlor",
	      NAME => "Volity::Game::TicTacToe",
	      VERSION_FROM => "lib/Volity/Game/TicTacToe.pm",
	      );
