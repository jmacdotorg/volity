package Volity::WinnersList;

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

# Object class for a winners list, into which a game object places seats
# after a game ends. The referee uses information from this winners list
# when buidling the game record to send to the bookkeeper.
# Volity::Game objects automatically create, destroy, and recreate these
# list objects as needed, and they are accessible to Game sublcasses through
# the "winners" method.

=head1 NAME

Volity::WinnersList - class for Volity game record winners lists

=head1 SYNOPSIS

Here's code you might see in a Volity::Game subclass implementing a
game where there is one winner and a bunch of losers, the latter of
whom are all effectively tied for second place (we assume that the
methods called in the first two lines are defined elsewhere):

 if ($self->game_has_been_won) {
     my ($winner, @losers) = $self->get_winning_seat_order;
     $self->winners->add_seat_to_slot($winner, 1);
     $self->winners->add_seat_to_slot(\@losers, 2);
     $self->end;
 }

And here's what you might see in a subclass defining a score-using
games where each player has a discrete ordinal place, and ties and
ties are not possible (again assuming the presence of some magic
methods defined somewhere else in the subclass):

 if ($self->game_has_been_won) {
     my @ordered_seats = $self->get_winning_seat_order;
     for (my $index = 0; $index <= $#ordered_seats; $index++) {
	 my $place = $index + 1;
	 $self->winners->add_seat_to_slot($ordered_seats[$index], $place);
     }
     $self->end;
 }

=head1 DESCRIPTION

Attached to every Volity::Game-subclass object is a WinnersList
object, accessible through the game object's C<winners> method. When a
game wraps up, it should use the methods listed in this document to
place the table's seats in a winning order I<before> calling the
C<end> method. The referee will then use this information when it
builds the game record to send to the Volity bookkeeper.

=head1 METHODS

=head2 slots

Accessor to the raw list of winner slots. Returns an array of
anonymous arrays, each representing a single slot, in winning order:
the one at index [0] is the winningest slot, and the one at [-1] is
the losingest. Each of these slot-arrays contains a number of
Volity::Seat objects.

=cut

use warnings;
use strict;
use base qw(Volity);

use fields qw(slots);

sub initialize {
    my $self = shift;
    $self->{slots} = [];
    return $self->SUPER::initialize(@_);
}

=head2 add_seat_to_slot ($seat, $position)

Adds the given seat to the winners list at the given position. Note
that the position is expressed in game-rank, so the first-place
position is 1, not 0.

If there are already seats at the current position, the given seat
will share the slot with them. As a shortcut, you can add several
seats at once to the same slot by passing an arrayref of seats as the
first argument.

=cut

# add_seat_to_slot: Add the given seat to the given slot position. Position
# starts at 1, for first place. This may be wrong thinking. We'll see.
# The seat argument can be an array reference containing seat objects.
sub add_seat_to_slot {
    my $self = shift;
    my ($seat, $position) = @_;
    unless ($position && $position > 0) {
	$self->expire("The second argument to add_seat_to_slot must be a position argument, which is a positive integer.");
    }
    # Some heavy-duty sanity checking...
    if (not($seat)) {
	$self->expire("The first argument to add_seat_to_slot must be a seat object, or a list reference containing seats.");
    }
    my @seats_to_add;
    if (ref($seat) eq 'ARRAY') {
	@seats_to_add = @$seat;
    }
    else {
	@seats_to_add = ($seat);
    }
    if (grep(not(ref($_) =~ /Volity/ && $_->isa("Volity::Seat")), @seats_to_add)) {
	$self->expire("The first argument to add_seat_to_slot must be a seat object, or a list reference containing seats.");
    }
    $self->{slots}->[$position - 1] ||= [];
    push (@{$self->{slots}->[$position - 1]}, @seats_to_add);
}

=head2 seats_at_slot ($position)

Returns the list of seats the given position in the winners list. Note
that the position is expressed in game-rank, so the first-place
position is 1, not 0.

=cut

sub seats_at_slot {
    my $self = shift;
    my ($position) = @_;
    unless ($position && $position > 0) {
	$self->expire("You called seats_at_slot with ($position), which is bad. You need to call seats_at_slot with a position argument, which is a positive integer.");
    }
    my $slot = $self->{slots}->[$position - 1];
    if ($slot) {
	return @{$slot};
    }
    else {
	return undef;
    }
}

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2005-2006 by Jason McIntosh.

=cut

1;
