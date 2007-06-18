#!perl -T

use Test::More tests => 1;

BEGIN {
	use_ok( 'Volity::WebClient' );
}

diag( "Testing Volity::WebClient $Volity::WebClient::VERSION, Perl $], $^X" );
