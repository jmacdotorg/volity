#!/usr/bin/perl

use warnings;
use strict;

use Volity::Server;
use Getopt::Std;
use Volity::GameRecord;

my %opts;
getopts('u:p:h:r:d:g:o:b:G:K:P:', \%opts);

foreach ('user', 'host', 'password', 'resource', 'bookkeeper_jid') {
  unless (defined($opts{substr($_, 0, 1)})) {
    die "You must define a $_, with the " . substr($_, 0, 1) . " switch.\n";
  }
}

foreach ('key id (GPG)', 'passphrase (GPG)', 'GPG binary path',) {
  unless (defined($opts{uc(substr($_, 0, 1))})) {
    die "You must define a $_, with the " . uc(substr($_, 0, 1)) . " switch.\n";
  }
}


unless (defined($opts{substr('g', 0, 1)})) {
  die "You must define a game class, with the g switch\n";
}

# Hardcoded default bookkeeper JID.
my $default_bkp = 'bookkeeper@volity.com';

# Hardcoded alias.
my $alias = 'volity';

my $server = Volity::Server->new(
				 {
				  user=>$opts{u},
				  password=>$opts{p},
				  port=>$opts{o} || 5222,
				  host=>$opts{h},
				  resource=>$opts{r},
				  debug=>$opts{d} || 0,
				  alias=>$alias,
				  game_class=>$opts{g},
				  bookkeeper_jid=>$opts{b} || $default_bkp,
				}
				);
$Volity::GameRecord::gpg_bin = $opts{G};
$Volity::GameRecord::gpg_secretkey = $opts{K};
$Volity::GameRecord::gpg_passphrase = $opts{P};

$server->start;
