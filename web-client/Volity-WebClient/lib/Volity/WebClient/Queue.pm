package Volity::WebClient::Queue;

use warnings;
use strict;

use Object::InsideOut;

use Carp qw(carp croak);

my @items
    :Field
    :Type(ARRAY_ref)
    :Acc(items)
    ;

sub initialize :Init {
    my $self = shift;
    $self->items([]);
}

# add: Add an item to the queue.
sub add {
    my $self = shift;
    my ($item) = @_;
    push @{$self->items}, $item;
}

# as_js: Return a list of all the items in the queue, expressed as JS calls.
sub as_js {
    my $self = shift;
    my @js_commands;
    $self->items([]) unless $self->items;
    for my $item (@{$self->items}) {
        push @js_commands, $self->item_as_js($item);
    }
    return @js_commands;
}

sub clear {
    my $self = shift;
    $self->items([]);
}

sub item_as_js {
    my $self = shift;
    croak (ref($self)
           . q{doesn't appear to override the item_as_js() method. Oops!!} );
}

package Volity::WebClient::Queue::Chat;

use warnings;
use strict;

use Carp qw(carp croak);
use Data::Dumper;

use Object::InsideOut qw(Volity::WebClient::Queue);
use Data::JavaScript::Anon;

sub item_as_js {
    my $self = shift;
    my ($message_info_ref) = @_;

    # Return a display_chat_message command.
    my $jid = $message_info_ref->{from};
    my $text = $message_info_ref->{body};

    my $escaped_jid 
        = Data::JavaScript::Anon->anon_dump( $jid );

    my $escaped_text
        = Data::JavaScript::Anon->anon_dump( $text );

    return "display_chat_message($escaped_jid, $escaped_text)";
    
}

package Volity::WebClient::Queue::TableChat;

use warnings;
use strict;

use Carp qw(carp croak);
use Data::Dumper;

use Object::InsideOut qw(Volity::WebClient::Queue);

sub item_as_js {
    my $self = shift;
    my ($message_info_ref) = @_;

    # Return a display_table_message command.
    my $nick = $message_info_ref->{from};
    my $table = $message_info_ref->{table_jid};
    my $text = $message_info_ref->{body};

    return "display_table_message($table, $nick, $text)";
    
}

package Volity::WebClient::Queue::RPC;

use warnings;
use strict;

use Object::InsideOut qw(Volity::WebClient::Queue);

use Data::JavaScript::Anon;

sub item_as_js {
    my $self = shift;
    my ($message_info_ref) = @_;

    my $method = $message_info_ref->{method};

    # Turn the Perl args array into a string of JavaScript code describing
    # the same array in JS.
    my $args_string
        = Data::JavaScript::Anon->anon_dump(@{$message_info_ref->{args}});

    # Return a JS handle_rpc command.
    return "handle_rpc($method, $args_string)";
}

1;

package Volity::WebClient::Queue::Roster;

use warnings;
use strict;

use Object::InsideOut qw(Volity::WebClient::Queue);

use Data::JavaScript::Anon;

sub item_as_js {
    my $self = shift;
    my ($roster_info_ref) = @_;

    my $jid = $roster_info_ref->{jid};
    my $status = $roster_info_ref->{type}
                 || $roster_info_ref->{show}
                 || 'available';
    my $message = $roster_info_ref->{status} || '';
    # Return a JS handle_rpc command.
    my $args_string
        = Data::JavaScript::Anon->anon_dump({jid     => $jid,
                                             status  => $status,
                                             message => $message,
                                         });
    return "update_roster ($args_string)";
}

1;

