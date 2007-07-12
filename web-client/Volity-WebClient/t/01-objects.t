# Basic tests of the internal dispatcher and user objects.

use warnings;
use strict;

use Test::More qw( no_plan );

use POE;                        # Just for access to the event loop.
use Readonly;

use POE::Component::Jabber;

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
    
Readonly our $JABBER_USERNAME => $config_ref->{jabber_username};
Readonly our $JABBER_PASSWORD => $config_ref->{jabber_password};
Readonly our $JABBER_RESOURCE => $config_ref->{jabber_resource};
Readonly our $JABBER_HOST     => 'volity.net';
Readonly our $JABBER_JID      => "$JABBER_USERNAME\@$JABBER_HOST/$JABBER_RESOURCE";

Readonly our $TEST_RESOURCE => 'perltest2';
Readonly our $TEST_JID      => "$JABBER_USERNAME\@$JABBER_HOST/$TEST_RESOURCE";

Readonly our $TIMEOUT         => 2;

my $test_user = TestUser->new({
                               user         => $JABBER_USERNAME,
                               password     => $JABBER_PASSWORD,
                               host         => $JABBER_HOST,
                               jid_host     => $JABBER_HOST,
                               resource     => $TEST_RESOURCE,
                               receiver_jid => $JABBER_JID,
                               alias        => 'webclienttest',
                           });
$poe_kernel->run;

package TestUser;

use warnings;
use strict;

use base qw(Volity::Jabber);
use fields qw(receiver_jid);

use Test::More;

use POE;
use Readonly;
use Data::JavaScript::Anon;

my $dispatcher;
my $user;

Readonly my $FIRST_MESSAGE => q{First Message. "It contains a quoted string," he said, "like this: 'Tra la la!!'"};
Readonly my $SECOND_MESSAGE => q{Second Message. Not so tricky.};

sub init_finish {
    my $self = $_[OBJECT];
    $self->SUPER::init_finish(@_);

    $dispatcher = Volity::WebClient::UserDispatcher->new;
    isa_ok($dispatcher, 'Volity::WebClient::UserDispatcher');

    $user = Volity::WebClient::User->new({
                                          user     => $main::JABBER_USERNAME,
                                          password => $main::JABBER_PASSWORD,
                                          jid_host     => $JABBER_HOST,
                                          host     => $main::JABBER_HOST,
                                          resource => $main::JABBER_RESOURCE,
                                          alias    => $main::JABBER_RESOURCE,
                                      });
    
    isa_ok($user, 'Volity::WebClient::User');
    isa_ok($user->jabber, 'Volity::Jabber');
    
    my @users = $dispatcher->users;
    is (@users, 0, 'users() returns an empty list');
    
    $dispatcher->add_user($user);
    @users = $dispatcher->users;
    is (@users, 1, 'users() returns a user');
    is_deeply($users[0], $user, 'users() returns the correct user');
    
    my $fetched_user = $dispatcher->get_user_with_jid($JABBER_JID);
    is_deeply($fetched_user, $user, 'get_user_with_jid()');

    my $deadline = time + $main::TIMEOUT;

    until ( (time >= $deadline) || ($user->is_connected) ) {
        $poe_kernel->run_one_timeslice;
    }

    is($user->is_connected, 1, '$user->is_connected');
    
    $self->send_message({
                              to   => $self->receiver_jid,
                              type => 'chat',
                              body => $FIRST_MESSAGE,
                          });
    $self->send_message({
                              to   => $self->receiver_jid,
                              type => 'chat',
                              body => $SECOND_MESSAGE,
                          });

    $poe_kernel->state("check_queues", $self);
    my $alarm_id = $poe_kernel->delay_set("check_queues", $main::TIMEOUT);

}

sub check_queues {
    my $self = shift;
    my $escaped_jid 
        = Data::JavaScript::Anon->anon_dump( $TEST_JID );
    my $escaped_first_message
        = Data::JavaScript::Anon->anon_dump( $FIRST_MESSAGE );
    my $escaped_second_message
        = Data::JavaScript::Anon->anon_dump( $SECOND_MESSAGE );
        
    my @expected_js_lines
        = (
           "display_chat_message($escaped_jid, $escaped_first_message)",
           "display_chat_message($escaped_jid, $escaped_second_message)",
       );

#    my @js_lines = $dispatcher->get_js_and_clear_queue_for_user($user);
#    my @js_lines = $user->
#    is_deeply(\@js_lines, \@expected_js_lines, 'get_js_and_clear_queue_for_user()');
#    like($js_lines[0], qr/display_chat/, 'get_js_and_clear_queue_for_user()');
        
    $poe_kernel->stop;

}
