# Basic tests of the internal dispatcher and user objects.

use warnings;
use strict;

use Test::More qw( no_plan );

use POE;                        # Just for access to the event loop.
use Readonly;

use Volity::WebClient::UserDispatcher;
use Volity::WebClient::User;
use YAML;
use English;

use Carp qw(carp croak);

Readonly my $CONFIG_FILE => 't/test_config.yml';

my $config_ref;
eval { $config_ref = YAML::LoadFile($CONFIG_FILE); };
if ($EVAL_ERROR) {
    croak ("Couldn'y read the test config file at $CONFIG_FILE:\n$EVAL_ERROR");
}
    
Readonly my $JABBER_USERNAME => $config_ref->{jabber_username};
Readonly my $JABBER_PASSWORD => $config_ref->{jabber_password};
Readonly my $JABBER_RESOURCE => $config_ref->{jabber_resource};
Readonly my $JABBER_HOST     => 'volity.net';
Readonly my $JABBER_JID      => "$JABBER_USERNAME\@$JABBER_HOST/$JABBER_RESOURCE";

Readonly my $TEST_RESOURCE => 'perltest2';
Readonly my $TEST_JID      => "$JABBER_USERNAME\@$JABBER_HOST/$TEST_RESOURCE";

Readonly my $TIMEOUT         => 5;

my $dispatcher = Volity::WebClient::UserDispatcher->new;
isa_ok($dispatcher, 'Volity::WebClient::UserDispatcher');

my $user = Volity::WebClient::User->new({
                                         username => $JABBER_USERNAME,
                                         password => $JABBER_PASSWORD,
                                         host     => $JABBER_HOST,
                                         resource => $JABBER_RESOURCE,
                                     });

isa_ok($user, 'Volity::WebClient::User');

my @users = $dispatcher->get_users;
is (@users, 0, 'get_users() returns an empty list');

$dispatcher->add_user($user);
@users = $dispatcher->get_users;
is (@users, 1, 'get_users() returns a user');
is_deeply($users[0], $user, 'get_users() returns the correct user');

my $fetched_user = $dispatcher->get_user_with_jid($JABBER_JID);
is_deeply($fetched_user, $user, 'get_with_with_jid()');

message_user();

my @expected_js_lines
    = (
       "display_chat_message('$TEST_JID', 'First message.')",
       "display_chat_message('$TEST_JID', 'Second message.')",
   );

my @js_lines = $dispatcher->get_and_clear_queued_js_for($user);
                         
display_chat_message('$TEST_JID', 'Second message.');
eq_array(\@js_lines, \@expected_js_lines, 'get_js_and_clear_queue_for()');


sub message_user {
    my $test_user = TestUser->new({
                                   username     => $JABBER_USERNAME,
                                   password     => $JABBER_PASSWORD,
                                   host         => $JABBER_HOST,
                                   resource     => $TEST_RESOURCE,
                                   receiver_jid => $JABBER_JID,
                               });
    
    my $deadline = time + $TIMEOUT;

    until ( (time >= $deadline) || ($test_user->is_done) ) {
        $poe_kernel->run_one_timeslice;
    }

    is($test_user->is_done, 1, 'Test user sent is messages');
}

package TestUser;

use warnings;
use strict;

use base qw(Volity::Jabber);
use fields qw(is_done receiver_jid);

use POE;

sub init_finish {
    my $self = $_[OBJECT];
    $self->SUPER::initialize(@_);

    $self->send_message({
                              to   => $self->receiver_jid,
                              type => 'chat',
                              body => 'First message.'
                          });
    $self->send_message({
                              to   => $self->receiver_jid,
                              type => 'chat',
                              body => 'Second message.'
                          });
    $self->is_done(1);
    $self->disconnect;
}
