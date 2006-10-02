#!/usr/bin/perl 

eval 'exec /usr/bin/perl  -S $0 ${1+"$@"}'
    if 0; # not running under some shell

use warnings;
use strict;

use Volity::Bookkeeper;
use Getopt::Long;
use Volity::GameRecord;
use Volity::Info;

use YAML;

Getopt::Long::Configure(qw(no_ignore_case no_auto_abbrev));

my %opts;
my %valid_options = (
		     host=>"h",
		     jid_host=>"s",
		     username=>"u",
		     password=>"p",
		     game=>"g",
		     jabber_id=>"J",
		     pidfile=>"f",
		     log_config=>"l",
		     port=>"o",
		     resource=>"r",
		     config=>"C",
		     db_username=>"U",
		     db_password=>"W",
		     db_datasource=>"D",
		     gpg=>"G",
		     payment=>"P",
		    );

my @getopt_options = map("$_|$valid_options{$_}=s", keys(%valid_options));

GetOptions(\%opts,
	   @getopt_options,
	   );

# Take care of possible config-file loading.
if ($opts{config}) {
    my $from_config;
    eval {
	$from_config = YAML::LoadFile($opts{config});
    };
    if ($@) {
	die "No data loaded from the config file path '$opts{config}.'\nYAML error:\n$@\n";
    }
    if (ref($from_config) eq "HASH") {
	for my $option_from_config (keys(%$from_config)) {
	    if ($valid_options{$option_from_config}) {
		$opts{$option_from_config} = $$from_config{$option_from_config};
	    }
	    else {
		die "Unrecognized option '$option_from_config' in config file $opts{config}.\n";
	    }
	}
    }
    else {
	die "The config file must be a YAML file representation of a simple hash. Please see the volityd manpage for more information.\n";
    }

}

# Figure out the payment module, and barf if it's not a tenable solution.
# The default payment module is Volity::PaymentSystem::Free.
my $payment_class = $opts{payment} || "Volity::PaymentSystem::Free";
eval "require $payment_class";
if ($@) {
    die "I couldn't load the payment module $payment_class: $@\n";
}
unless ($payment_class->isa("Volity::PaymentSystem") && $payment_class ne "Volity::PaymentSystem") {
    die "Error: The class $payment_class is not a subclass of Volity::PaymentSystem.\n";
}
	   
# Check to see if there's a J option, with a full login JID. If so,
# break it up and stuff the parts into other opts. Collisions are bad,
# though.

if ($opts{jabber_id}) {
    if ($opts{username} || $opts{jid_host} || $opts{resource}) {
	die "If you provide a full login JID with the J flag, then you can't use the u, s, or r flags. Make up your mind and use one style or the other!\n";
    }
    ($opts{username}, $opts{jid_host}, $opts{resource}) = $opts{jabber_id} =~ m|^(.*?)@(.*?)(?:/(.*?))?$|;
    unless ($opts{username}) {
	die "I couldn't parse '$opts{jabber_id}' as a valid JID. Sorry!\n";
    }
}

Volity::Info->set_db('Main', $opts{db_datasource}, $opts{db_username}, $opts{db_password});

# Set an undef host option to a defined jid_host, and vice versa.
$opts{host} ||= $opts{jid_host};
$opts{jid_host} ||= $opts{host};

foreach (qw(username host password)) {
    unless ($opts{$_}) {
	die "You must define a $_, either on the command line or in a config file.\n";
  }
}

if (defined($opts{pidfile})) {
  open (PID, ">$opts{pidfile}") or die "Can't write a PIDfile to $opts{pidfile}: $!";
  print PID $$;
  close PID or die "Can't close PIDfile $opts{pidfile}: $!";
}

if (defined($opts{log_config})) {
    my $logger_config_filename = $opts{log_config};
    Log::Log4perl::init_and_watch($logger_config_filename, 5);
    my $logger = Log::Log4perl->get_logger("Volity");
}

my $bookkeeper = Volity::Bookkeeper->new(
				 {
				  user=>$opts{username},
				  password=>$opts{password},
				  port=>$opts{port} || 5222,
				  host=>$opts{host},
				  jid_host=>$opts{jid_host},
				  resource=>$opts{resource},
				  alias=>'bookkeeper',
				  payment_class=>$payment_class,
				}
				);

$Volity::GameRecord::gpg_bin = $opts{gpg};

$bookkeeper->start;

=head1 NAME

bookkeeper.pl -- A Volity bookkeeper daemon

=head1 DESCRIPTION

This program is a Volity bookkeeper, meant to be run as a daemon.

=head2 Do I need to use this?

The bookkeeper acts as the nerve center of a Volity network, holding
meta-information about palors, rulesets, UI files and players, and
helping players' client software find games to play and people to play
with. Bookkeepers do not talk to each other; each bookkeeper is its
own Volity network, separate from any other.

The Volity project runs its own bookkeeper, which is continually
online with the Jabber ID C<bookkeeper@volity.net/volity>. Anyone can
register rulesets and parlors with it through the Volity.net website
<http://volity.net> (though this is currently in closed beta; please
contact Jason McIntosh <jmac@jmac.org> if you'd like to participate).

Therefore, if you wish to write your own Volity games and add them to
the volity.net network, I<you don't need to run a bookkeeper>. That
said, you are welcome to mess around with a local copy anyway for
testing purposes or whatnot.

=head2 Database setup

This program requires MySQL, though it would probably work with other
SQL-based databases with minimal hacking of the source or table
descriptions. You feed the program database connection information at
launch (see L<"CONFIGURATION">).

The tables it requires are defined by the file mysql_tables.sql, which
should have been distributed with this program (probably in a
directory marked C<sql>). You don't need to prime the tables with any
particular data, though admittedly the bookkeeper is of limited value
until some data exists there. At this time, there are no easy tools to
add info to the database, even though volity.net does offer a
web-based solution to add and modify data to the Volity.net
bookkeeper.

=head1 CONFIGURATION

You can run bookkeeper.pl with a configuration file, or by supplying a
list of command-line options at runtime. You can also mix the two
methods, in which case options you specify on the command line will
override any of their counterparts in the config file. See L<"Config
file format"> for more about the config file.

In the following documentation, each option has two names listed: its
one-letter abbreviation followed by its long name. Either is usable on
the command line, and So, for example, to set the Jabber ID that your
bookkeeper should use as C<bkp@example.com>, you can either supply
B<-J foo@bar.com> or B<--jabber_id=bkp@example.com> on the command
line, or the line C<jabber_id: foo@example.com> in the config file.

=head2 Required parameters

The program will immediately die (with a specific complaint) if you
don't specify enough information on the command line to allow the
parlor to authenticate with the Jabber server, or connect to the
database. Use an appropriate combination of the following flags to
achieve this.

=over

=item h host

The hostname of the Jabber server that the bookkeeper will use.

If this is not defined but C<jid_host> is, then this will be set to
the value of C<jid_host>.

=item s jid_host

The hostname that the bookkeeper will use in its own Jabber ID.

By default, this will be the same as the value of C<host>. You usually
won't need to set this unless you are connecting to the jabber server
via proxy, and the name of the host you are connecting to is different
than the host part of the bookkeeper's intended JID.

=item u username

The Jabber username that the bookkeeper will use whean connecting.

=item p password

The password that the bookkeeper will use when authenticating with
the Jabber server.

=item C config

The path to a volityd config file. See L<"Config file format">.

If you specify any command-line options beyond this one, they will
override any config options specified in the file.

B<Default>: None, and volityd will look for all options to come from
the command line.

=item D db_datasource

The DBI resource string to use, such as 'dbi:mysql:volity'.

=item P payment

The Volity::PaymentSystem subclass that this bookkeeper will use. See L<Volity::Payment> for more information.

B<Default>: C<Volity::PaymentSystem::Free>, which will have the
bookkeeper treat all parlors as free. This is what you want if you
aren't actually implementing your own payment system.

=item U db_username

The database username.

=item W db_password

The database password.

=item G gpg

I<(Optional)> A filesystem path leading to a GPG executable. Note that
this is no longer required, since the Volity protocol doesn't
presently make use of secure signatures.

=back

=head2 Optional parameters

Each of the following are optional. Not defining them at runtime will
result in default behavior as described.

=over

=item f pidfile

The filesystem pathname of the pidfile to be created when the
bookkeeper starts.

B<Default>: None, and no pidfile is used.

=item l log_config

The filesystem pathname of a L<Log::Log4perl> configuration file,
which defines the behavior of the volityd logger. The logger works
through various Log4perl invocations already spread throughout the
Volity modules, set at appropriate priority levels, ranging from
'DEBUG' to 'INFO' to 'FATAL'.

B<Default>: None, and no logging occurs. Showstopping events will
still trigger descriptive output to STDERR.

=item o port

The Jabber server's TCP port.

B<Default>: 5222 (the standard Jabber connection port)

=item r resource

The Jabber resource string that the bookkeeper will use after
authenticating. The string 'volity' (the default string) is a good
choice for 'live' bookkeepers; use something like 'testing'
otherwise.

B<Default>: 'volity'

=back

=head1 BUGS

=over

=item *

It's a pain in the neck to put any useful information into the
database, whether using raw SQL statements or the Volity::Info
modules. Heck, it's not even documented anywhere what the different
tables are for.

=back

=head1 SEE ALSO

=over

=item *

L<Volity>

=item *

L<Volity::Game>

=back

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=cut

