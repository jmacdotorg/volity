#!/usr/bin/perl

use warnings;
use strict;

use Volity::Bookkeeper;
use Getopt::Std;
use DBIx::Abstract;
use Volity::GameRecord;

my %opts;
getopts('u:p:h:r:d:o:K:G:P:D:U:W:', \%opts);

my $dbh = DBIx::Abstract->connect({dsn=>$opts{D},
				   user=>$opts{U},
				   password=>$opts{W},})
  or die "Failed to open a database connection.";


foreach ('user', 'host', 'password', 'resource',) {
  unless (defined($opts{substr($_, 0, 1)})) {
    die "You must define a $_, with the " . substr($_, 0, 1) . " switch.\n";
  }
}

# foreach ('key id (GPG)', 'passphrase (GPG)', 'GPG binary path',) {
foreach ('GPG binary path',) {
  unless (defined($opts{uc(substr($_, 0, 1))})) {
    die "You must define a $_, with the " . uc(substr($_, 0, 1)) . " switch.\n";
  }
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
				  dbh=>$dbh,
				}
				);

$Volity::GameRecord::gpg_bin = $opts{G};
#$Volity::GameRecord::gpg_secretkey = $opts{K};
#$Volity::GameRecord::gpg_passphrase = $opts{P};

$bookkeeper->start;
