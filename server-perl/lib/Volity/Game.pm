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

=head1 NAME

Volity::Game - base class for Volity game modules

=head1 SYNOPSIS

 package MyGame;

 use base qw(Volity::Game);

 # Set some configuration information.

 __PACKAGE__->min_allowed_players(2);
 __PACKAGE__->max_allowed_players(4);

 __PACKAGE__->uri("http://mydomain.com/games/mygame");

 # Override some Volity::Jabber methods.

 sub handle_chat_message {
   my $self = shift;
   my ($message) = @_;
   if (ref($self)) {
     # I'm an active game object! Talk back to the player!
     $self->send_message({to=>$$message{from}, body=>"Duh, hello!"});
   }
 }

 # ... later on ...

 sub end_game {
   my $self = shift;
   # If our end condition is met (whatever that may be), tell the ref.
   # It will take care of the rest.
   if ($greeble = 42) {
     $self->end_game;
   }
 }

=head1 DESCRIPTION

This class provides a framework for writing Volity game modules in Perl.

If you downloaded and installed the Frivolity system (all these Perl
modules under the C<Volity> namespace) primarily so you could write
Volity game modules in Perl, then this is the man page you should show
the most interest in! If you'd like to I<run> your game as a server
once you've written it, please direct your subsequent attention to
L<Volity::Server>.

=head1 USAGE

Create your own Perl package for your game, and have it inherit from
C<Volity::Game>. You can then have it do whatever you like, calling
the C<end_game> method when you're all done.

=head1 METHODS

First of all, see L<Volity::Jabber/"CALLBACK METHODS"> to learn about
all the methods you can override. Your game module will probably work
by overriding some of these.

I<Note> that the message-handling methods described in
L<Volity::Jabber/"Message handler methods"> can be called as I<either>
either class or object methods, and you should check the C<ref>ness of
the first argument if it makes any difference. (This is possibly dumb,
and might change in future versions of this module.)

So here are some object methods peculiar to C<Volity::Game>...

=head2 Object accessor methods

=over 

=item player_class

The class that this game's players belong to. When the game wants to make new players, it calls this class's constructor.

If you don't set this, it defaults to using the C<Volity::Player> class.

=item players

An array of this game's players, in turn order. (If turn order doesn't
matter, then neither does the order of this array, so: hah.)

=item referee

Returns the C<Volity::Referee> object that owns this game.

=back

=head2 Class Accessor methods

These methods are used to set some general configuration information
about the game, rather than specific information about any particualr
instance thereof.

=over

=item max_allowed_players

An integer specifying the maximum number of players this game
allows. If undefined, then there is no limit to the number of players
who can join.

=item min_allowed_players

An integer specifying the minimum number of players this game requires
for play. Defaults to 1.

=item uri

I<Required.> The game URI that this particular game module
"provides". Consult the core Volity documentation for more information
on how this works.

=back

=cut

use warnings; no warnings qw(deprecated);
use strict;

use base qw(Class::Accessor::Fields Class::Data::Inheritable);
use fields qw(players winners quitters current_player current_player_index referee player_jids debug);

foreach (qw(uri max_allowed_players min_allowed_players player_class)) {
  __PACKAGE__->mk_classdata($_);
}

use Scalar::Util qw(weaken);

use Carp qw(carp croak);

# Ehh, these globals...
# The idea is that subclasses will define them, if they want to. I dunno.
our $player_class;
our $max_allowed_players;
our $min_allowed_players;


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

sub DESTROY {
  my $self = shift;
  weaken($self->{referee});
}

sub AUTOHANDLER {
  my $self = shift;
  our $AUTOHANDLER;
  if ($self->referee->can($AUTOHANDLER)) {
    return $self->referee->$AUTOHANDLER(@_);
  } else {
    croak ("Unknown method $AUTOHANDLER");
  }
}

#################
# Class methods (mostly for fetching pre-game setup info)
#################

sub player_class {
  my $class = shift;
  if (exists($_[0])) {
    $player_class = $_[0];
  }
  return $player_class;
}

=begin old

XXX

I think these methods are obsolete and may deserve deletion before the next
package release.

#################
# Subclass-overridable stub methods
#################

sub receive_normal_message { }

# XXX The following method will break if called; it uses Net::Jabber syntax!
sub receive_chat_message {
  my $self = shift;
  my ($message, $j)  = @_;
  unless (ref($self)) {
    my $reply_body = "Hi! I am a Volity game referee.";
    $self->referee->send_message({
				 to=>$message->GetFrom,
				 body=>$reply_body,
			       });
  }
}
  
sub receive_groupchat_message { }

=end old

=cut

#################
# Basic player management
#################

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

=head2 muc_jid

I<Read-only> accessor for the JID of the MUC which this game is
running in. (Really, it justs asks the parent referee which MUC it's
in.)

=cut

sub muc_jid {
  my $self = shift;
  return ($self->referee->muc_jid(@_));
}

=head2 get_player_with_jid ($jid)

Returns the Volity::Player object corresponding to the given JID.

=cut

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
    $real_jid = $self->referee->look_up_jid_with_nickname($jid);
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

=head2 end_game

Ends the game! The referee and referee will handle cleanup.

=cut

# end_game: called when the game has come to a close, one way or another.
# Does very little right now.
sub end_game {
  my $self = shift;
  my ($args) = @_;
  $self->referee->end_game;
}

sub debug {
  my $self = shift;
  warn "@_\n" if $self->{debug};
}

=head1 BUGS

Volity is, at the time of this writing, waddling through its early
alpha stages. As such, this framework only does so much; you'll note
the lack of any graphics-handling features, since the
graphics-handling protocols of Volity have yet to be defined. For one
thing.

For the latest Volity news, please see the project's homepage, at
http://volity.sf.net .

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003 by Jason McIntosh.

=cut


1;

