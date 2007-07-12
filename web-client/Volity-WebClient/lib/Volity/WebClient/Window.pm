package Volity::WebClient::Window;

use warnings;
use strict;

use Object::InsideOut;

use Volity::WebClient::Queue;

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

my @user
    :Field
    :Type('Volity::WebClient::User')
    :Acc(user)
    ;

my @last_polled
    :Field
    :Type(numeric)
    :Acc(last_polled)
    ;

my @id
    :Field
    :Acc(id)
    :Arg( Name => 'id', Mandatory => 1 )
    ;

sub as_string :Stringify {
    my $self = shift;

    return $self->id;
}

sub initialize :Init {
    my $self = shift;

    # Set up some empty queues.
    $self->rpc_queue(Volity::WebClient::Queue::RPC->new);
    $self->chat_queue(Volity::WebClient::Queue::Chat->new);
    $self->roster_queue(Volity::WebClient::Queue::Roster->new);
    $self->chat_queues_by_table_jid({});

    return $self;
}

sub get_js_and_clear_queue {
    my $self = shift;
    my ($user) = @_;
    my @js_commands;
    foreach (qw(chat_queue roster_queue)) {
        my $queue = $user->$_;
        push @js_commands, $queue->as_js;
    }
    $user->chat_queue->clear;
    return @js_commands;
}
    
sub clear_all_queues {
    my $self = shift;
    foreach ( qw(rpc_queue chat_queue roster_queue) ) {
        $self->$_->clear;
    }
    foreach (values(%{$self->chat_queues_by_table_jid})) {
        $_->clear;
    }
}

1;
