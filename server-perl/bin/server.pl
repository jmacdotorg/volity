#!/usr/bin/perl

use warnings;
use strict;

use Volity::Server;
use Getopt::Std;

my %opts;
getopts('u:p:h:r:d:g:o:', \%opts);

foreach ('user', 'host', 'password', 'resource', 'game class') {
  unless (defined($opts{substr($_, 0, 1)})) {
    die "You must define a $_, with the " . substr($_, 0, 1) . " switch.\n";
  }
}

my $server = Volity::Server->new(
				 {
				  user=>$opts{u},
				  password=>$opts{p},
				  port=>$opts{o} || 5222,
				  host=>$opts{h},
				  resource=>$opts{r},
				  debug=>$opts{d} || 0,
				  alias=>'volity',
				  game_class=>$opts{g},
				}
				);
$server->start;
