#!/usr/bin/perl 

eval 'exec /usr/bin/perl  -S $0 ${1+"$@"}'
    if 0; # not running under some shell

use warnings;
use strict;

use Volity::Bookkeeper;
use Getopt::Std;
use Volity::GameRecord;
use Volity::Info;

my %opts;
getopts('u:p:h:r:d:o:K:G:P:D:U:W:f:', \%opts);

Volity::Info->set_db('Main', $opts{D}, $opts{U}, $opts{W});

foreach ('user', 'host', 'password', 'resource',) {
  unless (defined($opts{substr($_, 0, 1)})) {
    die "You must define a $_, with the " . substr($_, 0, 1) . " switch.\n";
  }
}

foreach ('GPG binary path',) {
  unless (defined($opts{uc(substr($_, 0, 1))})) {
    die "You must define a $_, with the " . uc(substr($_, 0, 1)) . " switch.\n";
  }
}

if (defined($opts{f})) {
  open (PID, ">$opts{f}") or die "Can't write a PIDfile to $opts{f}: $!";
  print PID $$;
  close PID or die "Can't close PIDfile $opts{f}: $!";
}

my $bookkeeper = Volity::Bookkeeper->new(
				 {
				  user=>$opts{u},
				  password=>$opts{p},
				  port=>$opts{o} || 5222,
				  host=>$opts{h},
				  resource=>$opts{r},
				  debug=>$opts{d} || 0,
				  alias=>'bookkeeper',
				}
				);

$Volity::GameRecord::gpg_bin = $opts{G};

$bookkeeper->start;
