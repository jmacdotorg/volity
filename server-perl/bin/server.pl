#!/usr/bin/perl 

eval 'exec /usr/bin/perl  -S $0 ${1+"$@"}'
    if 0; # not running under some shell

use warnings;
use strict;

use Volity::Server;
use Getopt::Std;
use Volity::GameRecord;

my %opts;
getopts('u:p:h:r:d:g:o:b:G:K:P:R:m:B:f:', \%opts);

foreach ('user', 'host', 'password',) {
  unless (defined($opts{substr($_, 0, 1)})) {
    die "You must define a $_, with the " . substr($_, 0, 1) . " switch.\n";
  }
}

foreach ('key id (GPG)', 'passphrase (GPG)', 'GPG binary path',) {
  unless (defined($opts{uc(substr($_, 0, 1))})) {
    die "You must define a $_, with the " . uc(substr($_, 0, 1)) . " switch.\n";
  }
}


unless (defined($opts{g}) or defined($opts{R})) {
  die "You must define either a game class (-g) or a referee class (-R).\n";
}

if (defined($opts{f})) {
  open (PID, ">$opts{f}") or die "Can't write a PIDfile to $opts{f}: $!";
  print PID $$;
  close PID or die "Can't close PIDfile $opts{f}: $!";
}

# Parse the bot list, if present...
my @bot_classes;
if (defined($opts{B})) {
    @bot_classes = split(/\s*,\s*/, $opts{B});
    foreach (@bot_classes) {
	eval"(require $_);";
	if ($@) {
	    die "Can't use bot class $_: $@\n";
	}
    }
} else {
    @bot_classes = ();
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
				  referee_class=>$opts{R},
				  bookkeeper_jid=>$opts{b} || $default_bkp,
				  muc_host=>$opts{m} || 'conference.volity.net',
				}
				);
$server->bot_classes(@bot_classes);
$Volity::GameRecord::gpg_bin = $opts{G};
$Volity::GameRecord::gpg_secretkey = $opts{K};
$Volity::GameRecord::gpg_passphrase = $opts{P};
$server->start;
