# Test one: See if our test game (and all the things it depends on) compiles!

use warnings;
use strict;

use Test::More (tests=>1);

use lib qw(. t);

use_ok('TestRPS');
