package Volity::Game;

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

# This is a base class for Volity game classes.

use warnings;
use strict;

use base qw(Class::Accessor::Fields);
use fields qw(players winners quitters current_player current_player_index server player_jids debug);

use Scalar::Util qw(weaken);

use Carp qw(carp croak);

# Ehh, these globals...
# The idea is that subclasses will define them, if they want to. I dunno.
our $player_class;
our $max_allowed_players;
our $min_allowed_players;


=head1 NAME

Volity::Game - Base class for Volity game modules

=head1 DESCRIPTION

This is a base class for Volity game modules. To create a game module, simply subclass Volity::Game, then override the message-handling methods as described in this documentation.

For information about creating a game server to work with game modules, see L<Volity::Server>.

=cut

sub initialize {
  my $self = shift;
#  unless ($self->check_sanity) {
#    return;
#  }
  $self->current_player_index(0);
  if (defined($self->{players})) {
    $self->current_player($self->{players}->[0]);
    $self->create_player_jid_lookup_hash;
  }
}

=head1 Class methods

Class methods that this module definEs Includes A standard Perl constructor (I<new>), plus some Jabber-message callbacks that handle chatter received by an inactive game. See the list of object message for active-game callback methods.

=head2 new({%config})

Constructor. Accepts an optional config hash, whose keys are the same as this class's accessor methods. Commonly, you'll use the 'players' key, with a list of Volity::Player objects as its value.

You don't need to ovveride this method in your subclass; see I<initialize>.

=head2 receive_message($message, $client)

A callback method, invoked by the game server when it receives a private (non-chat) Jabber message while no game is active. 

The first argument is a Net::Jabber::Message object representing the incoming message, and the second a "live" Net::Jabber::Client object that you can use to send out a response.

=head2 receive_chat($message, $client)

A callback method, invoked by the game server when it receives a private chat message from a jid while no game is active. This might happen when the game server and several players are in a multi-user conference room together but have yet to begin play, or have played an entire game but haven't started a new one yet.

The first argument is a Net::Jabber::Message object representing the incoming message, and the second a "live" Net::Jabber::Client object that you can use to send out a response.

=head2 receive_groupchat($message, $client)

A callback method, invoked by the game server when it receives a private (non-chat) Jabber message while no game is active. This might happen when the game server and several players are in a multi-user conference room together but have yet to begin play, or have played an entire game but haven't started a new one yet.
The first argument is a Net::Jabber::Message object representing the incoming message, and the second a "live" Net::Jabber::Client object that you can use to send out a response.

=cut

sub DESTROY {
  my $self = shift;
  weaken($self->{server});
}

sub AUTOHANDLER {
  my $self = shift;
  our $AUTOHANDLER;
  if ($self->server->can($AUTOHANDLER)) {
    return $self->server->$AUTOHANDLER(@_);
  } else {
    croak ("Unknown method $AUTOHANDLER");
  }
}

#################
# Class methods (mostly for fetching pre-game setup info)
#################

sub player_class {
  my $class = shift;
  return $player_class;
}

#################
# Subclass-overridable stub methods
#################

sub receive_normal_message { }

# XXX The following method will break if called; it uses Net::Jabber syntax!
sub receive_chat_message {
  my $self = shift;
  my ($message, $j)  = @_;
  unless (ref($self)) {
    my $reply_body = "Hi! I am a Volity game server.";
    $self->server->send_message({
				 to=>$message->GetFrom,
				 body=>$reply_body,
			       });
  }
}
  
sub receive_groupchat_message { }

#################
# Basic player management
#################

=head1 Object Methods

=head2 players ([@players])

Simple accessor to get or set the game's internal list of
Volity::Player objects. If called with an array reference as an
argument, that will become the new player list. If called with no
arguments, returns an array of player objects.

=cut

sub players {
  my $self = shift;
  if (exists($_[0])) {
    if (not(defined($_[0])) || ref($_[0]) eq 'ARRAY') {
      $self->{players} = $_[0];
    } else {
      $self->{players} = [@_];
    }
    $self->create_player_jid_lookup_hash;
  }
  return @{$self->{players}};
}

=head2 current_player ($player)

Called with no arguments, returns the player whose turn is up.

Called with a Volity::Player object as an argument, sets that player as the current player.

=head2 rotate_current_player

Convenience method that simply sets the next player in the players list as the current player.

This method is useful to call at the end of a turn.

=cut

sub rotate_current_player {
  my $self = shift;
  my $index = $self->current_player_index;
  if ($index + 1< (@{$self->{players}})) {
    $index++;
  } else {
    $index = 0;
  }
  $self->current_player_index($index);
  $self->current_player($self->{players}->[$index]);
}

sub create_player_jid_lookup_hash {
  my $self = shift;
  map ($self->{player_jids}{$_->jid} = $_, @{$self->{players}});
}

sub muc_jid {
  my $self = shift;
  return ($self->server->muc_jid(@_));
}

#sub j {
#  my $self = shift;
#  return $self->server->j;
#}

sub get_player_with_jid {
  my $self = shift;
  my ($jid) = @_;
  unless (defined($jid)) {
    carp("get_player_with_jid() called with no arguments. That's not going to work, pal.");
    return;
  }
  my $muc_jid = $self->muc_jid;
  my $real_jid;			# The keyed JID of this user.
  if (defined($muc_jid) and $jid =~ m|^$muc_jid/|) {
    # Looks like a player within a MUC.
    $real_jid = $self->server->look_up_jid_with_nickname($jid);
    croak("Uhh, I can't find any real jids for MUC-based JID $jid. This shouldn't happen.") unless defined($real_jid);
  } else {
    $real_jid = $jid;
  }
  my $player = $self->{player_jids}->{$real_jid};
  unless (defined($player)) {
    warn("Received message from apparent non-player JID $jid (\$real_jid was: $real_jid). Ignoring!!\n");
    return;
  }
  return $player;
}

# end_game: called when the game has come to a close, one way or another.
# Does very little right now.
sub end_game {
  my $self = shift;
  my ($args) = @_;
  $self->server->end_game;
}

sub debug {
  my $self = shift;
  warn "@_\n" if $self->{debug};
}

1;

