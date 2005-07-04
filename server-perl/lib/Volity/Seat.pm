package Volity::Seat;

use base qw(Volity);
use fields qw(id players registered_player_jids);

use warnings; use strict;

sub initialize {
    my $self = shift;
    $self->{players} ||= [];
    return $self;
}

# call_ui_function: Usually called by a game object. We'll just mirror the
# call to every player in this seat. (Volity::Player defines its own
# call_ui_function() method.)
sub call_ui_function {
    my $self = shift;
    foreach ($self->players) { $_->call_ui_function(@_) }
}

# add_player: Adds the given player object to my player list.
sub add_player {
    my $self = shift;
    my ($player) = @_;
    push (@{$self->{players}}, $player);
    return $player;
}

# remove_player: Removes the given player object from my player list.
# Returns the player, if found in the list (and removed).
# This doesn't affect the player's presence in the registry, though.
sub remove_player {
    my $self = shift;
    my ($player) = @_;
    my $found;
    for (my $i = 0; $i < @{$self->{players}}; $i++) {
	if ($self->{players}->[$i] eq $player) {
	    splice (@{$self->{players}}, $i, 1);
	    $found = $player;
	    last;
	}
    }
    return $found;
}

# register player: Permanently mark a player as having once sat in this seat.
# This just stores a basic JID, instead of an actual player object; we don't
# distinguish between one real player using the seat over two distinct
# connections with different resource strings, for example.
sub register_player {
    my $self = shift;
    my ($player) = @_;
    $self->{registered_player_jids}->{$player->basic_jid} = 1;
}

# registered_player_jids: Returns an alpha-sorted list of registered player
# JIDs.
sub registered_player_jids {
    my $self = shift;
    return sort(keys(%{$self->{registered_player_jids}}));
}

# clear_registry: Just wipe out the history of who has sat here.
sub clear_registry {
    my $self = shift;
    $self->{registered_player_jids} = {};
}

# register_occupants: Make sure that each occupant's basic JID is in the
# history of who has sat here.
sub register_occupants {
    my $self = shift;
    map ($self->register_player($_), $self->players);
}

# is_under_control: Return 1 if the seat contains players who are not missing.
sub is_under_control {
    my $self = shift;
    return grep(not($_->is_missing), $self->players) && 1;
}

1;
