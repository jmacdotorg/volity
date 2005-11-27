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
getopts('u:p:h:r:l:o:G:D:U:W:f:', \%opts);

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

if (defined($opts{l})) {
    my $logger_config_filename = $opts{l};
    Log::Log4perl::init_and_watch($logger_config_filename, 5);
    my $logger = Log::Log4perl->get_logger("Volity");
}

my $bookkeeper = Volity::Bookkeeper->new(
				 {
				  user=>$opts{u},
				  password=>$opts{p},
				  port=>$opts{o} || 5222,
				  host=>$opts{h},
				  resource=>$opts{r},
				  alias=>'bookkeeper',
				}
				);

$Volity::GameRecord::gpg_bin = $opts{G};

$bookkeeper->start;

=head1 NAME

bookkeeper.pl -- A simple Volity bookkeeper

=head1 DESCRIPTION

This is a simple Volity bookkeeper. Unless you are Jason McIntosh, you
probably have little reason to run this.

At this time, the only way to configure this program is through
command-line switches. Yes, this is rather unfortunate. We'll support
a file-based configuration system, in time.

You must pass in SQL database information among the arguments. These will
be passed directly to the underlying Class::DBI-based system.

=head1 CONFIGURATION

=head2 Required parameters

The program will immediately die (with a specific complaint) if any of
the following parameters are not defined at runtime.

=over

=item h

The hostname of the Jabber server that the game server will use.

=item u

The Jabber username that the game server will use when connecting.

=item p

The password that the game server will use when authenticating with
the Jabber server.

=item D

The DBI resource string to use, such as 'dbi:mysql:volity'.

=item U

The database username.

=item W

The database password.

=item G

A filesystem path leading to a GPG executable.

=back

=head2 Optional parameters

Each of the following are optional. Not defining them at runtime will
result in default behavior as described.

=over

=item f

The filesystem pathname of the pidfile to be created when the server
starts.

B<Default>: None, and no pidfile is used.

=item l

The filesystem pathname of a L<Log::Log4perl> configuration file,
which defines the behavior of the volityd logger. The logger works
through various Log4perl invocations already spread throughout the
Volity modules, set at appropriate priority levels, ranging from
'DEBUG' to 'INFO' to 'FATAL'.

B<Default>: None, and no logging occurs. Showstopping events will
still trigger descriptive output to STDERR.

B<Default>: conference.volity.net

=item o

The Jabber server's TCP port.

B<Default>: 5222 (the standard Jabber connection port)

=item r

The Jabber resource string that the game server will use after
authenticating. The string 'volity' (the default string) is a good
choice for 'live' game servers; use something like 'testing'
otherwise.

B<Default>: 'volity'

=back

=head1 BUGS

=over

=item *

More docs are needed.

=item *

The server should be launchable with a pointer to a configuration file, as opposed to a giant list of command-line configuration switches.

=back

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=cut

