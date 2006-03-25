package Volity::Player;

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

Volity::Player - Volity players, from a referee's perspective.

=head1 DESCRIPTION

An object of this class represents a Volity player present at a
table. The referee creates one of these objects for every player who
comes to that ref's table. The player might not actually I<play> the
game (i.e. sit in a seat), but is nonetheless recognized by the referee
as a potential game player and table presence.

In certain circumstances a ref may choose to keep an object for a
given player persistent, even after that player leaves the table,
while other times the player's departure results in the object's
destruction. Generally, it just does the right thing.

=head1 USAGE

You should never need to create or destroy player objects yourself;
the referee object takes care of that. However, there are a number of
methods defined by Volity::Referee and Volity::Seat that return player
objects, so you may find yourself interacting with them anyway.

Volity::Game subclasses refer to seats more often than to individual
players, since most game interaction takes place at the seat level.

=head1 METHODS

This class defines two kinds of object methods: accessors to basic,
informational attributes about the player, and triggers to send RPC
methods to the player's client.

=head2 Basic attributes

Consider these methods as read-only accessors to attributes that the
referee sets. (Well, you can write to them if you'd like, but I can't
predict what might happen if you do, so don't.)

=over

=item jid

This player's full JID.

=item basic_jid

The player's JID, minus the resource part.

=item nick

This player's MUC nickname.

=item referee

The Volity::Referee object of the table this player is at.

=item is_bot

1 if this player is a referee-created bot, 0 otherwise.

=item seat

The Volity::Seat object this player occupies. undef if the player
isn't sitting or missing.

=item state_seat

A Volity::Seat object that's most appropriate to use when sending
state to the player, preventing suspension-mode state-snooping. See
the C<send_game_state_to_player> method documented in L<Volity::Game>.

=item is_missing

1 if the player has abruptly vanished while sitting at an active game, 0 otherwise.


=back

=head2 Volity RPC methods

These methods all send volity-namespaced RPC methods to the player's client.

Generally, you shouldn't have to call any of these yourself. The ref
takes care of all this stuff.

=over

=item start_game

=item end_game

=item player_ready ( $ready_player )

=item player_stood ( $standing_player, $seat )

=item player_sat ( $sitting_player, $seat )

=item suspend_game ( $suspending_player )

=item resume_game

=item player_unready ( $unready_player )

=item seat_list

=item required_seat_list

=item timeout

=item timeout_reaction

=item table_language

=back

=head2 Other methods

=over

=cut

use warnings;
use strict;

use base qw(Volity);

use fields qw(jid nick referee rpc_count is_bot seat is_missing last_active_seat);
# FIELDS:
# jid
#   This player's full jid.
# nick
#   This player's MUC nickname.
# referee
#   The Volity::Referee object of the table this player is at.
# rpc_count
#   Counter that helps generate unique RPC request IDs.
# is_bot
#   1 if this player is a referee-created bot.
# seat
#   The Volity::Seat object this player occupies.
# last_active_seat
#   The Volity::Seat object this player occupies prior to the last suspension.
# is_missing
#   1 if the player has abruptly vanished while sitting at an active game.

# basic_jid: Return the non-resource part of my JID.
sub basic_jid {
  my $self = shift;
  if (defined($self->jid) and $self->jid =~ /^(.*)\//) {
    return $1;
  }
  return undef;
}

# seat: override the default accessor to always return a scalar (or undef).
sub seat {
    my $self = shift;
    if (@_) {
	$self->{seat} = $_[0];
    }
    return $self->{seat};
}


=item call_ui_function ($function, @args)

Sends the RPC request "game.$function(@args)" from the referee to the
player's client.

I<Note> that in most cases, you'll actually want to call UI functions
on the seat level, not on individual players. Luckily, the
Volity::Seat class defines this same method, which works in the same
manner. (To tell you the truth, it just mirrors the
C<call_ui_function> call to all of the player objects it holds...)

=cut

# call_ui_function: Usually called by a game object. It tells us to
# pass along a UI function call to this player's client.
# We'll have the referee do the dirty work for us.
sub call_ui_function {
  my $self = shift;
  my ($function, @args) = @_;
  my $rpc_request_name = "game.$function";
  $self->referee->send_rpc_request({
                                    id=>$self->next_rpc_id,
				    methodname=>$rpc_request_name,
				    to=>$self->jid,
				    args=>\@args,
				   });
}

sub start_game {
  my $self = shift;
  $self->referee->send_rpc_request({
      id=>'start_game',
				    methodname=>'volity.start_game',
				    to=>$self->jid,
				   });
  $self->referee->logger->debug("Sent volity.start_game to " . $self->jid);
}

sub end_game {
  my $self = shift;

  $self->referee->send_rpc_request({
      id=>'end_game',
      methodname=>'volity.end_game',
      to=>$self->jid,
  });
}

sub player_ready {
    my $self = shift;
    my ($other_player) = @_;
    my $jid = $other_player->jid;
    $self->referee->send_rpc_request({
	id=>'ready-announce',
	methodname=>'volity.player_ready',
	to=>$self->jid,
	args=>[$jid],
    });
}

sub player_stood {
    my $self = shift;
    my ($other_player) = @_;
    my $jid = $other_player->jid;
    $self->referee->send_rpc_request({
	id=>'ready',
	methodname=>'volity.player_stood',
	to=>$self->jid,
	args=>[$jid],
    });
}


sub kill_game {
    my $self = shift;
    my ($other_player) = @_;
    my $args = [$self->referee->kill_switch];
    if (defined($other_player)) {
	push (@$args, $other_player->jid);
    }
    $self->referee->send_rpc_request({
	id=>'timeout',
	methodname=>'volity.kill_game',
	to=>$self->jid,
	args=>[$args],
    });
}

sub suspend_game {
    my $self = shift;
    my ($other_player) = @_;
    my $args = [];
    if (defined($other_player)) {
	$args = [$other_player->jid];
    }
    $self->referee->send_rpc_request({
	id=>'suspend-game',
	methodname=>'volity.suspend_game',
	to=>$self->jid,
	args=>$args,
    });
}

sub resume_game {
    my $self = shift;
    $self->referee->send_rpc_request({
	id=>'resume-game',
	methodname=>'volity.resume_game',
	to=>$self->jid,
    });
}

sub player_sat {
    my $self = shift;
    my ($other_player, $seat) = @_;
    my $jid = $other_player->jid;
    my $seat_id = $seat->id;
    $self->referee->send_rpc_request({
	id=>'ready',
	methodname=>'volity.player_sat',
	to=>$self->jid,
	args=>[$jid, $seat_id],
    });
}

sub player_unready {
    my $self = shift;
    my ($other_player) = @_;
    my $jid = $other_player->jid;
    $self->referee->send_rpc_request({
	id=>'unready',
	methodname=>'volity.player_unready',
	to=>$self->jid,
	args=>[$jid],
    });
}

sub seat_list {
    my $self = shift;
    my (@seat_ids) = @{$self->referee->game_class->seat_ids};
    $self->referee->send_rpc_request({
	id=>'seat-list',
	methodname=>'volity.seat_list',
	to=>$self->jid,
	args=>[\@seat_ids],
    });
}

sub required_seat_list {
    my $self = shift;
    my (@seat_ids) = @{$self->referee->game_class->required_seat_ids};
    $self->referee->send_rpc_request({
	id=>'required-seat-list',
	methodname=>'volity.required_seat_list',
	to=>$self->jid,
	args=>[\@seat_ids],
    });
}

sub game_is_abandoned {
    my $self = shift;
    $self->send_game_activity_rpc("abandoned");
}

sub game_is_active {
    my $self = shift;
    $self->send_game_activity_rpc("active");
}

sub game_is_disrupted {
    my $self = shift;
    $self->send_game_activity_rpc("disrupted");
}

sub send_game_activity_rpc {
    my $self = shift;
    my ($game_state) = @_;
    $self->referee->send_rpc_request({
	id=>'game-activity',
	methodname=>'volity.game_activity',
	to=>$self->jid,
	args=>[$game_state],
    });
}

sub timeout {
    my $self = shift;
    my ($setting_jid) = @_;
    $self->referee->send_rpc_request({
	id=>'timeout',
	methodname=>'volity.timeout',
	to=>$self->jid,
	args=>[$setting_jid, $self->referee->timeout],
    });
}

sub timeout_reaction {
    my $self = shift;
    my ($setting_jid) = @_;
    $self->referee->send_rpc_request({
	id=>'timeout-reaction',
	methodname=>'volity.timeout_reaction',
	to=>$self->jid,
	args=>[$setting_jid, $self->referee->timeout_reaction],
    });
}

sub table_language {
    my $self = shift;
    my ($setting_jid) = @_;
    $self->referee->send_rpc_request({
	id=>'language',
	methodname=>'volity.language',
	to=>$self->jid,
	args=>[$setting_jid, $self->referee->language],
    });
}

sub receive_game_state {
    my $self = shift;
    $self->referee->send_rpc_request({
	id=>'receive-state',
	methodname=>'volity.receive_state',
	to=>$self->jid,
	args=>[{state=>$self->referee->current_state}],
    });
    $self->referee->game->send_config_state_to_player($self);

    if ($self->referee->game->is_afoot) {
	$self->referee->send_rpc_request({
	    id=>'game-has-started',
	    methodname=>'volity.game_has_started',
	    to=>$self->jid,
	});
	$self->referee->game->send_game_state_to_player($self);
    }

    $self->referee->send_rpc_request({
	id=>'state-sent',
	methodname=>'volity.state_sent',
	to=>$self->jid,
    });
}

=item update 

Updates the player about the game state and seats, sending it the proper
volity RPCs.

=cut
    
# update: Convenience method that updates the player about the game state,
# the seat lists, and seat occupants.
sub update {
    my $self = shift;
    $self->seat_list;
    $self->required_seat_list;
    $self->seat_occupants;
    $self->receive_game_state;   
}

# seat_occupants: Call player_sat for every seated player.
sub seat_occupants {
    my $self = shift;
    for my $player ($self->referee->players) {
	if (my $seat = $player->seat) {
	    $self->player_sat($player, $seat);
	}
    }
}

# state_seat: Return the seat safest to use for state-sending POV purposes.
sub state_seat {
    my $self = shift;
    if ($self->referee->game->is_suspended) {
	return $self->last_active_seat;
    } else {
	return $self->seat;
    }
}

# next_rpc_id: Simple method that returns a unique (for this object) RPC id.
sub next_rpc_id {
    my $self = shift;
    my $number;
    unless ($number = $self->rpc_count) {
	$number = $self->rpc_count(1);
    }
    $self->rpc_count($number++);
    return "player-$number";
}

1;

=back

=head1 SEE ALSO

=over

=item *

L<Volity::Referee>

=item *

L<Volity::Seat>

=back

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2005-2006 by Jason McIntosh.

=cut
