use warnings;
use strict;

use Test::More qw( no_plan );

use POE;                        # Just for access to the event loop.
use Readonly;
use Perl6::Slurp;
use English;
use LWP;
use Carp qw(carp croak);
use YAML;
use DBI;

Readonly my $CONFIG_FILE => 't/test_config.yml';
Readonly my $COMMAND     => q{bin/volity-webclientd -c $CONFIG_FILE};


my $config_ref;
eval { $config_ref = YAML::LoadFile($CONFIG_FILE); };
if ($EVAL_ERROR) {
    croak ("Couldn't read the test config file at $CONFIG_FILE:\n$EVAL_ERROR");
}
    
Readonly my $JABBER_USERNAME => $config_ref->{jabber_username};
Readonly my $JABBER_PASSWORD => $config_ref->{jabber_password};
Readonly my $JABBER_HOST     => 'volity.net';
Readonly my $PID_FILE        => $config_ref->{pid_file};

Readonly my $SESSION_TABLE   => 'websession';
Readonly my $WEBCLIENT_SESSION_TABLE => 'webclient_session_key';
Readonly my $DB_DSN          => $config_ref->{db_dsn};
Readonly my $DB_USERNAME     => $config_ref->{db_username};
Readonly my $DB_PASSWORD     => $config_ref->{db_password};

Readonly my $DAEMON_HOST     => 'localhost';
Readonly my $DAEMON_PORT     => $config_ref->{daemon_port};
Readonly my $SERVER_URL      => "http://$DAEMON_HOST:$DAEMON_PORT";

my $dbh = DBI->connect($DB_DSN, $DB_USERNAME, $DB_PASSWORD);
if ($dbh) {
    pass ("Database handle created");
}
else {
    die "Couldn't create DB handle with ($DB_DSN, $DB_USERNAME, $DB_PASSWORD)";
}

my $pid;
if ($pid = slurp($PID_FILE)) {
    diag("Looks like there's already a PID file at $PID_FILE (PID: $pid)\n");
    diag("I'll kill that process if it exists.\n");
    kill $pid;
    unlink $PID_FILE or die "Couldn't unlink $PID_FILE: $OS_ERROR";
}

system $COMMAND;
sleep 1;
$pid = slurp($PID_FILE);
if ($pid) {
    pass("Daemon launched (PID: $pid)");
}
else {
    die "The daemon failed to launch (no PID file found at $PID_FILE.\nCommand was:\n$COMMAND";
}

# Add a couple of test users to the daemon.
my %user1 = (
             username => $JABBER_USERNAME,
             host     => $JABBER_HOST,
             password => $JABBER_PASSWORD,
             resource => 'webclienttestuser1',
         );

my %user2 = %user1;
$user2{resource} = 'webclienttestuser2';

# XXX Magic here.

my $ua = LWP::UserAgent->new;
$ua->agent('Web Client test script');
my $user1_url = "$SERVER_URL/login?key=$user1{key}";
my $request = HTTP::Request->new(GET => $user1_url);

my $result = $ua->request($request);
ok($result->is_success, 'User1 login');

