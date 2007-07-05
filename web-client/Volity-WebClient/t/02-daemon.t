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
use DateTime::Format::MySQL;

Readonly my $CONFIG_FILE => 't/test_config.yml';
Readonly my $COMMAND     => q{bin/volity-webclientd -c $CONFIG_FILE};

Readonly my $LOGIN_TIMEOUT => 10;

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

Readonly my $TEST_SESSION_ID => 'blahblahtestsession12345';
Readonly my $TEST_SESSION_KEY => 'doodleydootestkey54321';

my $dbh = DBI->connect($DB_DSN, $DB_USERNAME, $DB_PASSWORD);
if ($dbh) {
    pass ("Database handle created");
}
else {
    die "Couldn't create DB handle with ($DB_DSN, $DB_USERNAME, $DB_PASSWORD)";
}

my $pid;

set_up_session_tables();
launch_daemon();

my %user = (
            username => $JABBER_USERNAME,
            host     => $JABBER_HOST,
            password => $JABBER_PASSWORD,
            resource => 'webclienttestuser',
        );

eval { run_tests() };

clean_up_daemon();
clean_up_session_tables();

sub run_tests {
    my $ua = LWP::UserAgent->new;
    $ua->agent('Web Client test script');
    my $user_url = "$SERVER_URL/login?key=$user{key}";
    my $request = HTTP::Request->new(GET => $user_url);
    
    my $result = $ua->request($request);
    ok($result->is_success, 'User login');

    sleep(1);                   # Give it a chance to get its roster
    my $js_url = "$SERVER_URL/login/js";
    my $js_request = HTTP::Request->new(GET => $js_url);
    my $js_result = $ua->request($js_request);

    ok($js_result->is_success, 'JS request');
    my @js_commands = split("\n", $js_result->content);
    ok($js_commands[0] =~ /roster/, 'roster JS function call found');

    diag("Napping $LOGIN_TIMEOUT seconds to see if the user logs out...");
    sleep($LOGIN_TIMEOUT);
    my $delayed_result = $ua->request($js_request);
    ok(not($js_result->is_success), 'User login timeout');
}

sub set_up_session_tables {
    # How we set this up depends on whether there's already a row for the
    # test user.
    my $query = qq{SELECT id FROM $SESSION_TABLE WHERE username = ?};
    my $sth = $dbh->prepare($query);
    my ($session_id) = $sth->execute($query)->fetchrow_array;
    unless ($session_id) {
        $session_id = $TEST_SESSION_ID;
        my $query = "INSERT INTO $SESSION_TABLE (id, username) VALUES (?, ?)";
        my $sth = $dbh->prepare($query);
        $sth->execute($session_id, $JABBER_USERNAME);
    }

    my $now_dt = DateTime->now->add(minutes => 1);
    my $now_mysql = DateTime::Format::MySQL->format_datetime($now_dt);
    $dbh->do(qq{INSERT INTO $WEBCLIENT_SESSION_TABLE (session_id, key, timeout) values ('session_id', '$TEST_SESSION_KEY', '$now_mysql')});
}
            
sub clean_up_session_tables {
    $dbh->do(qq{DELETE FROM $SESSION_TABLE WHERE id = '$TEST_SESSION_ID'});
    $dbh->do(qq{DELETE FROM $WEBCLIENT_SESSION_TABLE WHERE session_id = '$TEST_SESSION_ID'});
}

sub launch_daemon {
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

}

sub clean_up_daemon {
    kill $pid or warn "Can't kill daemon at PID $pid: $OS_ERROR";
    unlink $PID_FILE or warn "Can't unlink pidfile $PID_FILE: $OS_ERROR";
}
