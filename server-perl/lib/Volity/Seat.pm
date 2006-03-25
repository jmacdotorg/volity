package Volity::Seat;

############################################################################
# LICENSE INFORMATION - PLEASE READ
############################################################################
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
############################################################################

=head1 NAME

Volity::Seat - A Volity seat, containing some players.

=head1 SYNOPSIS

 # From within a Volity::Game subclass's code:
 my @seats = $self->seats;

 # Or, given a Volity::Player object:
 my $seat = $player->seat;

 # Now tell the seat they just picked up the Three of Clubs:
 $seat->call_ui_function("draw_card", "3C") 

=head1 DESCRIPTION

An objects of this class represents a seat at a Volity table. Volity
players who are actually playing a game sit in seats, and referees
address game-specific RPC calls to seats, not to individual
players. See the main Volity documentation for more information about
the seat concept:
http://www.volity.org/wiki/index.cgi?action=browse&id=Seats

=head1 USAGE

As a game programmer, you need never create, modify, or destroy these
objects yourself; that is all handled for you by the other objects
that make up a Frivolity-run table, particularly the referee (see
L<Volity::Referee>). However, several methods of C<Volity::Game>, the
module you subclass to create your own Perl-based Volity game, will
return objects of this class. Several crucial functions of
communicating with a game's players involve calling methods on these
objects, including the all-important C<call_ui_function> method
(described below).

Your Volity::Game subclass must define some variables that assist the
referee in seat creation, particularly the C<seat_ids> and
C<required_seat_ids> class variables. If you wish to extend this class
for your game, you can speficy a C<Volity::Seat> subclass to use
through C<Volity::Game>'s C<seat_class> method. All these are
described in detail within L<Volity::Game>.

=head1 METHODS

=head2 Basic accessors

You should treat these as read-only instance variables; the referee will adjust their values as necessary over the lifetime of the seat.

=over

=item id

Returns the string that is this seat's ruleset-defined ID. Note that
it is not prefixed with the "seat." namespace designation, so you'll
have to add that yourself if using it in contexts where such is
necessary.

=item players

Returns the list of Volity::Player objects sitting in this seat (or a
reference to that list, in scalar context).

=item registered_player_jids

Returns the JIDs of all of the players who have ever sat in this seat
I<while the current game has been active>. In other words, it's the
list of JIDs that will appear as this seat's players on the final game
record, assuming that no other players sit in the seat before the game
ends.

=back

=head2 Other methods

=over

=cut

use base qw(Volity);
use fields qw(id players registered_player_jids is_eliminated);

use warnings; use strict;

sub initialize {
    my $self = shift;
    $self->{players} ||= [];
    $self->{registered_player_jids} = {};
    return $self;
}

=item is_eliminated ($boolean)

If called with an argument, sets the seat's "eliminated" bit
appropriately. In other words, if something happened in the game that
caused this seat to be permanently removed from play, you should set
this to 1.

Returns 1 if the seat is eliminated, 0 otherwise.

=cut

# is_eliminated: Just do a little data-cleaning on this one, when setting.
sub is_eliminated {
    my $self = shift;
    my ($boolean) = @_;
    if (defined($boolean)) {
	$boolean = $boolean? 1 : 0;
	$self->{is_eliminated} = $boolean;
    }
    return $self->{is_eliminated};
}

=item call_ui_function ($function, @args)

Sends the RPC request "game.$function(@args)" from the referee to the
clients of all the players sitting in this seat.

(This method also exists on player objects, if you need to call it on
individual players... but generally you won't have any reason to. From
a game's perspective, all the players in a seat are a single
game-playing entity.)

=cut

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
    my @jids = keys(%{$self->{registered_player_jids}});
    @jids = sort(@jids);
    return @jids;
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

# is_under_human_control: Return 1 if the seat contains _human_
# players who are not missing.
sub is_under_human_control {
    my $self = shift;
    return grep(not($_->is_bot) && not($_->is_missing), $self->players) && 1;
}

=back

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2005-2006 by Jason McIntosh.

=cut

1;
