package Volity::WebClient::UserDispatcher;

use warnings;
use strict;

use Object::InsideOut;

my @users_by_jid
    :Field
    :Type(HASH_ref)
    :Acc(users_by_jid)
    ;

sub initialize :Init {
    my $self = shift;
    $self->users_by_jid({});
}

# add_user: Adds the given user object to the users_by_jid hash.
sub add_user {
    my $self = shift;
    my ($user) = @_;
    unless ( eval { $user->isa('Volity::WebClient::User') } ) {
        croak ('add_user requires a Volity::WebClient::User object as an argument,');
    }
    $self->users_by_jid->{$user->jabber->jid} = $user;
}

# remove_user: Removes the given user from the users_by_jid hash.
sub remove_user {
    my $self = shift;
    my ($user) = @_;
    unless ( eval { $user->isa('Volity::WebClient::User') } ) {
        croak ('add_user requires a Volity::WebClient::User object as an argument,');
    }
    # XXX How to delete items from the internal hash? This just takes up
    # XXX memory otherwise...
    delete($self->users_by_jid->{$user->jabber->jid});
}

# users: Returns the values of the users_by_jid hash.
sub users {
    my $self = shift;
    return values (%{$self->users_by_jid});
}

# get_user_with_jid: Returns the user with the given JID.
sub get_user_with_jid {
    my $self = shift;
    my ($jid) = @_;
    return $self->users_by_jid->{$jid};
}

sub get_js_and_clear_queue_for_user {
    my $self = shift;
    my ($user) = @_;
    my @js_commands;
    push @js_commands, $user->chat_queue->as_js;
    $user->chat_queue->clear;
    return @js_commands;
}

1;

=head1 NAME

Volity::WebClient::UserDispatcher - Contains web client player connections.

=head1 METHODS

=over

=item add_user ( $user )

Adds the given user (an object of the Volity::WebClient::User class)
to the dispacther.

=item remove_user ( $user )

Removes the given user (an object of the Volity::WebClient::User
class) from the dispatcher.

This won't disrupt or otherwise affect any existing connection to the
Jabber network that that user might have.

=item users

Returns all user objects currently held by the dispatcher.

=item user_with_jid ( $jid )

Returns the user object with the given Jabber ID, or C<undef> if there
is no match.

=back

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2007 by Jason McIntosh.
