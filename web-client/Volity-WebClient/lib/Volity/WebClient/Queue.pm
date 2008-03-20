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

    my $args_string
        = Data::JavaScript::Anon->anon_dump($message_info_ref);

    return "display_chat_message($args_string);";
}

package Volity::WebClient::Queue::RPCRequest;

use warnings;
use strict;

use Object::InsideOut qw(Volity::WebClient::Queue);

use Data::JavaScript::Anon;

sub item_as_js {
    my $self = shift;
    my ($message_info_ref) = @_;

    my $args_string
        = Data::JavaScript::Anon->anon_dump($message_info_ref);

    # Return a JS handle_rpc command.
    return "handle_rpc_request($args_string);";
}

1;

package Volity::WebClient::Queue::Roster;

use warnings;
use strict;

use Object::InsideOut qw(Volity::WebClient::Queue);

use Data::JavaScript::Anon;

sub item_as_js {
    my $self = shift;
    my ($presence_info_ref) = @_;

    my $jid = $presence_info_ref->{jid};
    my $status = $presence_info_ref->{type}
                 || $presence_info_ref->{show}
                 || 'available';
    my $message  = $presence_info_ref->{status} || '';
    my $role     = $presence_info_ref->{role}   || 'player';
    my $muc      = $presence_info_ref->{muc}    || '';
    my $nickname = $presence_info_ref->{nickname};
    # Return a JS handle_rpc command.
    my $args_string
        = Data::JavaScript::Anon->anon_dump({jid      => $jid,
                                             status   => $status,
                                             message  => $message,
                                             role     => $role,
                                             muc      => $muc,
                                             nickname => $nickname,
                                         });
    return "update_roster ($args_string);";
}

1;

package Volity::WebClient::Queue::RPCResponse;

use warnings;
use strict;

use Object::InsideOut qw(Volity::WebClient::Queue);

use Data::JavaScript::Anon;

sub item_as_js {
    my $self = shift;
    my ($response_info_ref) = @_;

    # Snip out the RPC object, convert the rest of the info hash into JS,
    # and send it along.
    delete $response_info_ref->{rpc_object};
    my $response_info_js
        = Data::JavaScript::Anon->anon_dump($response_info_ref);
    return "handle_rpc_response($response_info_js);";
}

1;

package Volity::WebClient::Queue::Error;

use warnings;
use strict;

use Object::InsideOut qw(Volity::WebClient::Queue);

use Data::JavaScript::Anon;

sub item_as_js {
    my $self = shift;
    my ($error_message) = @_;

    my $error_message_js
        = Data::JavaScript::Anon->anon_dump($error_message);
    return "handle_error_message($error_message_js);";
}

1;
