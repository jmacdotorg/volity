package Volity::WebClient::User;

use warnings;
use strict;

use Object::InsideOut;

use Volity::WebClient::Queue;
use Volity::WebClient::Window;

my @jabber
    :Field
    :Type('Volity::Jabber')
    :Acc(jabber)
    ;

my @is_connected
    :Field
    :Acc(is_connected)
    ;

my @session_id
    :Field
    :Acc(session_id)
    ;

my @window_hashref
    :Field
    :Type(HASH_ref)
    :Acc(window_hashref)
    ;

my @last_id
    :Field
    :Type(numeric)
    :Acc(last_id)
    ;

my @ui_download_directory
    :Field
    :Acc(ui_download_directory)
    ;

use Readonly;

Readonly my $PRE_WINDOW_ID => '_PRE_WINDOW_';

use Carp qw(carp croak);

sub initialize :Init {
    my $self = shift;
    my ($args) = @_;

    $self->window_hashref({});
    
    unless ($args->{alias}) {
        $args->{alias} = $args->{resource};
    };
    
    $args->{webclient_user} = $self;

    # Add a phony window for storing events prior to the first real
    # window getting opened up.
    my $pre_window
        = Volity::WebClient::Window->new( {id => $PRE_WINDOW_ID } );
    $self->add_window($pre_window);
    
    $self->jabber(Volity::WebClient::JabberUser->new($args));

    return $self;
}

sub disconnect {
    my $self = shift;
    return $self->jabber->disconnect;
}

sub add_window {
    my $self = shift;
    my ($window) = @_;
    unless ($window->isa('Volity::WebClient::Window')) {
        croak ('Argument to add_window must be a window object.');
    }
    $self->window_hashref->{$window} = $window;

    # If the pre-window is open (and not because we just opened it!),
    # dump its queues into this window.
    # Then remove the pre-window, as its job is done now.
    unless ($window->id eq $PRE_WINDOW_ID) {
        if (my $pre_window = $self->window_with_id($PRE_WINDOW_ID)) {
            $pre_window->transfer_queues($window);
            $self->remove_window($pre_window);
        }
    }
}

sub remove_window {
    my $self = shift;
    my ($window) = @_;
    unless (ref($window)) {
        $window = $self->window_hashref->{$window};
    }
    unless ($window->isa('Volity::WebClient::Window')) {
        croak ('Argument to remove_window must be a window object or ID.');
    }

#    $window->user(undef);
    delete $self->window_hashref->{$window};

    # If there are no windows left open, re-open the pre-window, so as
    # not to lose any events that occur before the next real window opens.
    unless ($self->windows) {
        my $pre_window
            = Volity::WebClient::Window->new( {id => $PRE_WINDOW_ID } );
        $self->add_window($pre_window);
    }
}

sub windows {
    my $self = shift;
    return values(%{$self->window_hashref});
}

sub window_with_id {
    my $self = shift;
    my ($id) = @_;
    return $self->window_hashref->{$id};
}

package Volity::WebClient::JabberUser;

use warnings;
use strict;

use base qw(Volity::Jabber);
use fields qw(webclient_user
              timeouts
              ui_info_requests
              ui_list_requests
              parlor_items_requests
              table_user_requests
              outstanding_rpcs
              delay_id_for_http_response
              http_response_queue
);

use Volity::WebClient::Queue;
use POE;
use Readonly;
use LWP;
use English;
use DateTime::Format::HTTP;
use HTTP::Status;
use Cwd;
use URI::Escape;
use Data::JavaScript::Anon;
use File::Copy;

Readonly my $CLIENT_URI         => 'http://volity.org/protocol/ui/html';
Readonly my $UI_TIMESTAMP_FILE  => 'timestamp';
Readonly my $BOOKKEEPER_JID     => 'bookkeeper@volity.net/stilton';

Readonly my $RPC_MAX_TIME       => 2;
Readonly my $RPC_GLUE_TIME      => .1;

my $ua = LWP::UserAgent->new;
$ua->agent('Volity webclient UI downloader (+http://volity.net)');

# Keep RPC queue objects around just to do RPC-to-JS conversions.
my $rpc_request_queue = Volity::WebClient::Queue::RPCRequest->new;
my $rpc_response_queue = Volity::WebClient::Queue::RPCResponse->new;
my $rpc_error_queue =  Volity::WebClient::Queue::Error->new;

my $delay_session;

sub init_finish {
    my $self = $_[OBJECT];
    $self->SUPER::init_finish(@_);
    $self->webclient_user->is_connected(1);

    # Init a bunch of instance variables to empty anonymous hashes.
    # These will be used for handling disco-query results, and their keys
    # are the IDs of these queries.
    foreach ( qw(timeouts ui_info_requests
                 ui_list_requests
                 table_user_requests
                 parlor_items_requests
                 outstanding_rpcs
                 delay_id_for_http_response
            ) ) {
        $self->{$_} = {};
    }

    $self->{http_response_queue} = [];

    # Set some POE states.
#    $poe_kernel->state('send_http_response', $self);
#    $poe_kernel->state('send_http_response', sub {die "kaka"});

    my $session = $poe_kernel->alias_resolve($self->resource);
    my $active = $poe_kernel->get_active_session;
#    # XXX These are DIFFERENT.
#    die "Active is $active. Expected is $session.";

#    my @aliases = $poe_kernel->alias_list;
#    die "Current session aliases: @aliases";

#    $poe_kernel->state('blargh', sub {die "Got the GOOD blargh event."});
}

sub prepare_http_response {
#    die "In prep.";
    my $self = shift;
    my ($rpc_id, $http_response) = @_;
    $self->outstanding_rpcs->{$rpc_id} = $http_response;
    push @{$self->{http_response_queue}}, $http_response;
    $delay_session ||= $poe_kernel->get_active_session;
    $poe_kernel->state('send_http_response', $self);
    $poe_kernel->state('adjust_http_response_delay', $self);
    my $delay_id = $poe_kernel->delay_set('send_http_response',
                                          $RPC_MAX_TIME,
                                          $http_response,
                                      );
    $self->delay_id_for_http_response->{$http_response} = $delay_id;
}

sub send_http_response {
#    my $self = shift;
    my $self = $_[OBJECT];
    my $http_response = $_[ARG0];
#    my ($http_response) = @ARG;
    shift @{$self->{http_response_queue}};
    $http_response->continue;
}

sub adjust_http_response_delay {
    my $self = $_[OBJECT];
    my $delay_id = $_[ARG0];
    my $http_response = $_[ARG1];
    if ($RPC_GLUE_TIME) {
        my $result = $poe_kernel->delay_adjust($delay_id, $RPC_GLUE_TIME);
    }
    else {
        my $result = $poe_kernel->alarm_remove($delay_id);
        $http_response->continue;
    }
}

sub handle_rpc_request {
    my $self = shift;
    my ($rpc_info_ref) = @_;

    delete($rpc_info_ref->{rpc_object});
    if (my $http_response = $self->{http_response_queue}->[0]) {
        # We're holding onto at least one unsent HTTP response from an
        # earlier user-originating RPC request.
        # Tack this new server-based RPC request onto it, so the client
        # can handle it as soon as it receives the HTTP response.
        my $js_line = $rpc_request_queue->item_as_js($rpc_info_ref);
        $http_response->content($http_response->content . "$js_line\n");

        # Adjust the timer so that this response will go back sooner.
        my $delay_id = $self->delay_id_for_http_response->{$http_response};
        $poe_kernel->post($delay_session, 'adjust_http_response_delay', $delay_id, $http_response);
        
    }
    else {
        foreach ($self->webclient_user->windows) {
            $_->rpc_request_queue->add($rpc_info_ref);
        }
    }
}

sub handle_rpc_response {
    my $self = shift;
    my ($rpc_info_ref) = @_;

    my $request_id = $rpc_info_ref->{id};
    my $http_response;

    if ($http_response = delete($self->ui_list_requests->{$request_id})) {
        if ($rpc_info_ref->{response}->[0] eq 'volity.ok') {
            shift @{$rpc_info_ref->{response}};
            $self->handle_ui_item_response($rpc_info_ref->{id},
                                           $rpc_info_ref->{response},
                                           $http_response,
                                       );
        }
    }
    elsif ($http_response = delete($self->ui_info_requests->{$request_id})) {
        if ($rpc_info_ref->{response}->[0] eq 'volity.ok') {
            $self->handle_ui_info_response($rpc_info_ref->{id},
                                           $rpc_info_ref->{response}->[1],
                                           $http_response,
                                       );
        }
    }
    elsif ($http_response = delete($self->outstanding_rpcs->{$request_id})) {
        my $js_line = $rpc_response_queue->item_as_js($rpc_info_ref);
        # This response object is also on a POE timer, and will get sent
        # when that runs out. So we can safely let this ref pointer go.
        $http_response->content($http_response->content . "$js_line\n");
    }
    else {
        foreach ($self->webclient_user->windows) {
            $_->rpc_response_queue->add($rpc_info_ref);
        }
    }
}

sub handle_rpc_fault {
    my $self = shift;
    my ($rpc_info_ref) = @_;
    $self->handle_rpc_response($rpc_info_ref);
}

sub handle_rpc_transmission_error {
    my $self = shift;
    my ($node, $error_code, $error_message) = @_;
    foreach ($self->webclient_user->windows) {
        $_->error_queue->add("ERROR $error_code: $error_message");
    }
}

sub handle_disco_items {
    my $self = shift;
    my ($from_jid, $request_id, $items_ref, $fields_ref) = @_;
    my $http_response;
    if ($http_response
        = delete($self->table_user_requests->{$request_id})) {
#        use Data::Dumper;
#        die Dumper($items_ref);
    }
    elsif ($http_response
        = delete($self->parlor_items_requests->{$request_id})) {
        $self->return_bot_info($items_ref, $http_response);
    }
}

sub return_bot_info {
    my $self = shift;
    my ($items_ref, $http_response) = @_;
    $http_response->code(RC_OK);
    $http_response->header('Content-type' => 'text/javascript');
    my $bot_list_ref = [];
    for my $item (@$items_ref) {
        my $bot_info_ref = {
                            jid  => $item->jid,
                            uri  => $item->node,
                            name => $item->name,
                        };
        push @$bot_list_ref, $bot_info_ref;
    }
    my $js_bot_list_ref = Data::JavaScript::Anon->anon_dump($bot_list_ref);
    $http_response->content("display_bots($js_bot_list_ref);");
    $http_response->continue;
}

sub handle_disco_info {
    my $self = shift;
    my ($from_jid, $request_id, $items_ref, $fields_ref) = @_;
    if (my $http_response
        = delete($self->table_user_requests->{$request_id})) {
        use Data::Dumper;
        die Dumper($items_ref);
    }
}


sub handle_chat_message {
    my $self = shift;
    foreach ($self->webclient_user->windows) {
        $_->chat_queue->add(@_);
    }

}

# Handle 'normal' messages just like 'chat' messages.
sub handle_normal_message {
    my $self = shift;
    $self->handle_chat_message(@_);
}

sub handle_groupchat_message {
    my $self = shift;
    my ($message_info) = @_;
    
    # Use the 'from' JID to figure out which table this message is from,
    # and which nickname sent it. Then replace that JID in the info hash
    # with the nickname, and file the whole deal under the table's bare JID.
    my $full_from_jid = $message_info->{from};
    my ($table_jid, $nickname) = $full_from_jid
        =~ /^(.*)\/(.*)$/;
    $message_info->{from} = $nickname;
    $message_info->{table_jid} = $table_jid;
    
    $self->handle_chat_message(@_);
    return;    
    # XXX Everything below this is ignored for now.
    
    for my $window ($self->webclient_user->windows) {
        # Make sure there's a queue already filed under this table's JID.
        my $queue;
        unless ($queue = $window
                ->chat_queues_by_table_jid->{$table_jid}) {
            $queue = $window
                ->chat_queues_by_table_jid->{$table_jid}
                    = Volity::WebClient::Queue::TableChat->new;
        }

        $queue->add($message_info);
    }
}

sub update_roster {
    my $self = shift;
    my ($presence_info) = @_;
    if ($self->roster->has_jid($presence_info->{jid})) {
        # Update the roster object...
        $self->roster->presence($presence_info->{jid},
                                {type => $presence_info->{type}});

        # ...then allow updating of the client's roster.
        for my $window ($self->webclient_user->windows) {
            my $queue = $window->roster_queue;
            $queue->add($presence_info);
        }
    }
}

sub update_muc_roster {
    my $self = shift;
    my ($presence_info) = @_;

    # Use the 'from' JID to figure out which table this message is from,
    # and which nickname sent it. Then replace that JID in the info hash
    # with the nickname, and file the whole deal under the table's bare JID.
    my $full_from_jid = $presence_info->{muc};
    my ($table_jid, $nickname) = $full_from_jid
        =~ /^(.*)\/(.*)$/;

    for my $window ($self->webclient_user->windows) {
        # Make sure there's a queue already filed under this table's JID.
        my $queue;
        unless ($queue = $window->roster_queues_by_table_jid->{$table_jid}) {
            $queue = $window->roster_queues_by_table_jid->{$table_jid}
                    = Volity::WebClient::Queue::Roster->new;
        }
        
        $queue->add($presence_info);
    }
    
}
    
# Ugh. I have to write a low-level presence stanza handler because my 2003
# self was dumb.
sub jabber_presence {
    my $self = shift;
    my ($node) = @_;

    # Abstract this gnarly XML node into a nice Perl hash.
    my %presence_info;
    foreach ( qw(show status priority) ) {
        if (my $subnode = ($node->get_tag($_))) {
            $presence_info{$_} = $subnode->data;
        }
    }
    $presence_info{type} = $node->attr('type');
    $presence_info{jid}  = $node->attr('from');
    if (not($presence_info{type}) && not($presence_info{show})) {
        $presence_info{show} = 'available';
    }

    # See if they have a caps (JEP-0115) element in their presence.
    # If so, this may clue us in to what sort of Volity entity this is.
    my $volity_role;
    if ((my $c = $node->get_tag('c')) && 
        ($node->get_tag('c')->attr('node') eq "http://volity.org/protocol/caps")) {
        $volity_role = $c->attr('ext');
    }
    else {
        # Hmm. We'll just assume they're a player, then.
        $volity_role = 'player';
    }
    $presence_info{role} = $volity_role;
    
    my $x_node;
    if (
        ( $node->get_tag('x') )
        and (
             ($x_node) =
             grep( { $_->attr('xmlns') eq "http://jabber.org/protocol/muc#user" }
                   $node->get_tag('x') )
         )
    ) {
        # Someone's presence changed inside a MUC.
        $presence_info{jid} = $x_node->get_tag('item')->attr('jid');
        $presence_info{muc}  = $node->attr('from');
        my ($nickname) = $node->attr('from') =~ m{/(.*)$};
        $presence_info{nickname} = $nickname;
        $self->update_muc_roster(\%presence_info);
    }
    else {
        $self->update_roster(\%presence_info);
    }
}

sub next_id {
    my $self = shift;
    my $id = $self->last_id + 1;
    $self->last_id($id);
    return $id;
}

# prepare_uis_for_parlor: Make a disco query to find what UIs the given
# ruleset has available, and then wait for a response.
# Also take an HTTP response object and store it so we can continue it later.
sub request_uis_for_ruleset {
    my $self = shift;
    my ($ruleset_uri, $http_response) = @_;
    my $id = $self->next_id;

    # XXX This is weird...
    $self->{ui_list_requests} ||= {};
    
    # XXX Add timeout delay here.
    $self->ui_list_requests->{$id} = $http_response;

    $self->make_rpc_request({
                             to         => $BOOKKEEPER_JID,
                             id         => $id,
                             methodname => 'volity.get_uis',
                             args       => [$ruleset_uri],
                         });

}

sub request_users_for_table {
    my $self = shift;
    my ($table_jid, $http_response) = @_;
    my $id = $self->next_id;

    # XXX Add timeout delay here.
    $self->table_user_requests->{$id} = $http_response;

    $self->request_disco_items({
                                to         => $table_jid,
                                id         => $id,
                            });
}

sub request_bot_info {
    my $self = shift;
    my ($parlor_jid, $http_response) = @_;
    my $id = $self->next_id;

    # XXX Add timeout delay here.
    $self->parlor_items_requests->{$id} = $http_response;

    $self->request_disco_items({
                                to         => $parlor_jid,
                                id         => $id,
                                node       => 'bots',
                            });
}

sub rpc_response_get_uis {
    my $self = shift;
    my ($args_ref) = @_;
    die "Aieee I got a response.";
}

# handle_ui_disco_items: Given a request ID and a list of disco item objects,
# figure out which ones are pointers to UI files we can use, and then do the
# right thing with this resulting list.
sub handle_ui_item_response {
    my $self = shift;
    my ($rpc_id, $urls_ref, $http_response) = @_;

    my %urls_by_id;

    my $id = $self->next_id;
    $self->ui_info_requests->{$id} = $http_response;
    $self->send_rpc_request({
                             to   => $BOOKKEEPER_JID,
                             id   => $id,
                             methodname => 'volity.get_ui_info',
                             args => $urls_ref,
                         });
    

#    # Grep out the URLs that this client can use.
#    my @compatible_ui_urls = map {
#        my ($from_jid, $info_request_id, $items_ref, $fields_ref) = @$_;
#        return $urls_by_id{$info_request_id};
#    } grep {
#        my ($from_jid, $info_request_id, $items_ref, $fields_ref) = @$_;
#        return $fields_ref->{'client-type'} eq $CLIENT_URI;
#    } @{$self->results->{$request_id}};

}

sub handle_ui_info_response {
    my $self = shift;
    my ($rpc_id, $urls_ref, $http_response) = @_;
    foreach (@$urls_ref) {
#        use Data::Dumper; die Dumper($_);
        $self->download_ui($_->{url});
    }

    # Finally, complete the HTTP response.
    $http_response->code(RC_OK);
    $http_response->content('Game UIs successfully prepared.');
    $http_response->continue;
    
}

sub download_ui {
    my $self = shift;
    my ($url) = @_;

    # First, get the URL's last-modified.
    my $head_request = HTTP::Request->new( HEAD => $url );
    my $head_result = $ua->request($head_request);
    unless ($head_result->is_success) {
        # XXX Error here.
        return;
    }
#    die $head_result->as_string;
    my $remote_last_modified = $head_result->header('Last-Modified');
    my $remote_last_modified_dt
        = DateTime::Format::HTTP->parse_datetime($remote_last_modified);

    # If this timestamp is newer than the local UI cache's last-modifed,
    # _or_ if the local UI cache for this UI URL doesn't even exist,
    # download a fresh copy.

    # Create an URI-encoded version of the UI URL, making it a safe
    # fs directory name.
    # Actuall, we further mutate the dirname to encode '-' characters,
    # then turn all '%'s into '-'. This gets around the issue of Apache v1
    # refusing to touch URLS wth %2f's in them.
    my $unsafe_uri_chars = q{^A-Za-z0-9_.!~*'()};
    my $escaped_url = uri_escape($url, $unsafe_uri_chars);
    $escaped_url =~ s/%/-/g;
    my $base_ui_directory = $self->webclient_user->ui_download_directory;
    my $ui_directory
        = File::Spec->catdir ($base_ui_directory, $escaped_url);
    foreach ($base_ui_directory, $ui_directory) {
        unless (-e $_) {
            mkdir $_ or die "Failed to mkdir $_: $OS_ERROR";
        }
    }

    my $timestamp_file
        = File::Spec->catfile ($ui_directory, $UI_TIMESTAMP_FILE);
    my @stat = stat($timestamp_file);
    if ( not(@stat) || $stat[9] < $remote_last_modified_dt->epoch ) {
        my $ui_request = HTTP::Request->new( GET => $url );
        my $ui_result = $ua->request($ui_request);
        my $zip_file = File::Spec->catfile($ui_directory, 'downloaded.zip');
        open my $ui_handle, '>', $zip_file
            or die "Couldn't write to $zip_file: $OS_ERROR";
        print $ui_handle $ui_result->content;
        close $ui_handle;

        # Then unpack it.
        system('unzip', $zip_file, '-d', $ui_directory);

        # Update the timestamp.
        open my $timestamp_handle, '>', $timestamp_file;
        print $timestamp_handle gmtime() . "\n";
        close $timestamp_handle;

        # Figure out where main.html is. Rename that directory to 'ui'.
        my $main_path = `find $ui_directory -name main.html -print`;
        my ($main_parent) = $main_path =~ m{^(.*)/main.html};
        unless ($main_parent) {
            next;
        }
        my $new_parent = File::Spec->catdir($ui_directory, 'ui');
        move ($main_parent, $new_parent)
            or die "Couldn't move $main_parent to $new_parent: $OS_ERROR";
    }
}
    

1;


=head1 NAME

Volity::WebClient::User - A single player-connection to the Jabber network.

=head1 DESCRIPTION

Objects of this class are sublcasses of Volity::Jabber, and therefore
represent individual connections to a Jabber network. They're intended
for use with the Volity web client software, and are usually found
within the embrace of a Volity::WebClient container object.

Invocations of this class probably won't occur outside of the
Volity::WebClient software itself.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2007 by Jason McIntosh.



