package Volity::WebClient::User;

use warnings;
use strict;

use Object::InsideOut;

use Volity::WebClient::Queue;

my @jabber
    :Field
    :Type('Volity::Jabber')
    :Acc(jabber)
    ;

my @rpc_queue
    :Field
    :Type('Volity::WebClient::Queue')
    :Acc(rpc_queue)
    ;

my @chat_queue
    :Field
    :Type('Volity::WebClient::Queue')
    :Acc(chat_queue)
    ;

my @roster_queue
    :Field
    :Type('Volity::WebClient::Queue')
    :Acc(roster_queue)
    ;

my @chat_queues_by_table_jid
    :Field
    :Type(HASH_ref)
    :Acc(chat_queues_by_table_jid)
    ;

my @is_connected
    :Field
    :Acc(is_connected)
    ;

my @session_id
    :Field
    :Acc(session_id)
    ;

use Readonly;
use Carp qw(carp croak);

sub initialize :Init {
    my $self = shift;
    my ($args) = @_;

    unless ($args->{alias}) {
        $args->{alias} = $args->{resource};
    };
    
    # Set up some empty queues.
    $self->rpc_queue(Volity::WebClient::Queue::RPC->new);
    $self->chat_queue(Volity::WebClient::Queue::Chat->new);
    $self->roster_queue(Volity::WebClient::Queue::Roster->new);
    $self->chat_queues_by_table_jid({});

    $args->{webclient_user} = $self;
    
    $self->jabber(Volity::WebClient::JabberUser->new($args));

    return $self;
}

sub disconnect {
    my $self = shift;
    return $self->jabber->disconnect;
}
    
package Volity::WebClient::JabberUser;

use warnings;
use strict;

use base qw(Volity::Jabber);
use fields qw(webclient_user);

use Volity::WebClient::Queue;
use POE;

sub init_finish {
    my $self = $_[OBJECT];
    $self->SUPER::init_finish(@_);
    $self->webclient_user->is_connected(1);
}

sub handle_rpc_request {
    my $self = shift;
    my ($rpc_info_ref) = @_;
    $self->webclient_user->rpc_queue->add($rpc_info_ref);
}

sub handle_chat_message {
    my $self = shift;
    $self->webclient_user->chat_queue->add(@_);

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
    
    # Make sure there's a queue already filed under this table's JID.
    my $queue;
    unless ($queue = $self->webclient_user
                     ->chat_queues_by_table_jid->{$table_jid}) {
        $queue = $self->webclient_user
                 ->chat_queues_by_table_jid->{$table_jid}
                   = Volity::WebClient::Queue::TableChat->new;
    }

    $queue->add($message_info);
}

#sub handle_roster_receipt {
#    my $self = shift;
#    my @roster_jids = $self->roster->jids;
#
#    my $queue = $self->webclient_user->roster_queue;
#        
#    # XXX This is BROKEN but good for the first go-through.
#    #     Just get the first presence, ignoring any others!!
#    for my $jid (@roster_jids) {
#        my ($presence_ref) = $self->roster->presence($jid);
#        use Data::Dumper; die Dumper($self->roster);
#        my $status = $presence_ref->{type};
#        $queue->add([$jid, $status]);
#    }
#}

#sub handle_roster_update {
#    my $self = shift;
#    my ($jid) = @_;
#    my $queue = $self->webclient_user->roster_queue;
#        
#    # XXX This is BROKEN but good for the first go-through.
#    #     Just get the first presence, ignoring any others!!
#    my ($presence_ref) = $self->roster->presence($jid);
#    my $status = $presence_ref->{type};
#    $queue->add([$jid, $status]);
#}

sub update_roster {
    my $self = shift;
    my ($args_ref) = @_;
    if ($self->roster->has_jid($args_ref->{jid})) {
        # Update the roster object...
        $self->roster->presence($args_ref->{jid},
                                {type => $args_ref->{type}});

        # ...then allow updating of the client's roster.
        my $queue = $self->webclient_user->roster_queue;
        $queue->add($args_ref);
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
    
    if (my $x = $node->get_tag('x', [xmlns=>"http://jabber.org/protocol/muc#user"])) {
        # Someone's presence changed inside a MUC.
    }
    else {
        $self->update_roster(\%presence_info);
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



