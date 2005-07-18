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

 __PACKAGE__->min_allowed_seats(2);
 __PACKAGE__->max_allowed_seats(4);
 __PACKAGE__->uri("http://mydomain.com/games/mygame");
 __PACKAGE__->name("MyGame");
 __PACKAGE__->description("This is my awesome game. It's great.");
 __PACKAGE__->ruleset_version("1.2");

# Then we define a bunch of rpc-reacting methods, as described in the
# developers' guide...!

=head1 DESCRIPTION

This class provides a framework for writing Volity game modules in Perl.

If you downloaded and installed the Frivolity system (all these Perl
modules under the C<Volity> namespace) primarily so you could write
Volity game modules in Perl, then this is the class you should show
the most interest in! If you'd like to I<run> your game as a server
once you've written it, please direct your subsequent attention to
L<Volity::Server>.

=head1 USAGE

Create your own Perl package for your game, and have it inherit from
C<Volity::Game>. You can then have it do whatever you like, calling
the C<end_game> method when you're all done.

OK, that's a bit of a simiplifaction. Fortunately, I've written a
developer's guide that should teach and show you everything you need
to know about writing Volity games in Perl. You can always find the latest
edition here:

http://www.volity.org/docs/devguide_perl/

The remainder of this manpage serves as a reference to the specific
fields and methods that this class offers to programmers.

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

=item seat_class

The class that this game's seats belong to. When the game wants to make new seats, it calls this class's constructor.

If you don't set this, it defaults to using the C<Volity::Seat> class.

=item referee

Returns the C<Volity::Referee> object that owns this game. Use this
object to fetch seat information while the game is afoot; see
L<Volity::Referee> for the salient methods.

I<Shortcut> You can call any of the referee's methods simply by
calling them on the game object. For example, calling
C<$game->seats> is exactly equivalent to (and will return exactly
the same seat objects as) calling C<$game->referee->seats>.

=item is_afoot

If the game is still being set up, returns falsehood. If the game has
already started, returns truth.

=item is_suspended
 
If the game is in play but suspended (as a result of a player asking
the ref to suspend the game, or the ref reacting to a player's sudden
departure), this returns 1. Otherwise, returns 0.

=item is_active

Convenience method: If the game is afoot and not suspended, returns
truth. Otherwise, returns falsehood.

=back

=head2 Class Accessor methods

These methods are used to set some general configuration information
about the game, rather than specific information about any particualr
instance thereof.

=over

=item name

A I<brief> name of this game module, which the game server will use to advertise itself through service discovery and other means. If left undefined, a boring default value will be used (probably the JID that this server is running under.

=item description

A longer text description of this game module.

=item uri

I<Required.> The URI of the ruleset that this particular game module
implements. Consult the core Volity documentation for more information
on how this works.

=item ruleset_version

I<Required.> The version number of the ruleset that this particular
game module implements. A client-side UI file consults this number to
determine its own compatibility with a game server.

=item seat_ids 

An array of strings representing the IDs of I<all> the seats that this game implementation supports. In order words, if it support

=item required_seat_ids

An array of strings representing the IDs of role-differentiated seats that players should be aware of, as defined by the ruleset.

=back

=cut

use warnings; no warnings qw(deprecated);
use strict;

use base qw(Volity Class::Data::Inheritable);

use fields qw(winners quitters current_seat current_seat_index referee config_variables is_afoot is_suspended is_finished config_variable_setters);

# Define some class accessors.
foreach (qw(uri name description ruleset_version website max_allowed_seats min_allowed_seats seat_class seat_ids required_seat_ids)) {
  __PACKAGE__->mk_classdata($_);
}
foreach (qw(seat_ids required_seat_ids)) {
  __PACKAGE__->$_([]);
}

use Scalar::Util qw(weaken);

use Carp qw(carp croak);

sub initialize {
  my $self = shift;
  $self->current_seat_index(0);
#  $self->current_player(($self->players)[0]);
  weaken($self->{referee});
  $self->config_variables({});
  $self->config_variable_setters({});
  $self->winners([]);
  $self->is_finished(0);
}

sub AUTOLOAD {
  my $self = shift;
  our $AUTOLOAD;
  my ($method) = $AUTOLOAD =~ /^.*::(.*)$/;
  if ($self->can('referee') && $self->referee && $self->referee->can($method)) {
    return $self->referee->$method(@_);
  } else {
    Carp::confess ("Unknown method $method");
  }
}

=head2 Other methods

=over

=item current_seat ($seat)

Called with no arguments, returns the seat whose turn is up.

Called with a Volity::Seat object as an argument, sets that seat as the current seat.

=item rotate_current_seat

Convenience method that simply sets the next seat in the seats list as the current seat.

This method is useful to call at the end of a turn.

=item register_config_variables (@variables)

Registers the given instance variables (which should be declared in
your subclass's C<use fields> pragma) as holding game configuation
information. This will allow your game to accept RPC calls of the form
"game.$variable_name([args])" even when there is no game active. (The
referee normally kicks back such requests with an RPC fault.)

Normally you'll only call this method once, as part of your
C<initialize()> method definition.

=back

=cut

sub rotate_current_seat {
  my $self = shift;
  my $index = $self->current_seat_index;
  if ($index + 1< ($self->referee->seats)) {
    $index++;
  } else {
    $index = 0;
  }
  $self->current_seat_index($index);
  $self->current_seat(($self->referee->seats)[$index]);
}

=item call_ui_function_on_everyone

A convenience method for blasting a game.* call to I<all> players at a
table, seated and otherwise.

See the C<call_ui_function> method of L<Volity::Player> for arguments and return values.

=item call_ui_function_on_observers

A convenience method for blasting a game.* call to every player at the
table who is not seated.

See the C<call_ui_function> method of L<Volity::Player> for arguments and return values.

=item call_ui_function_on_seats

A convenience method for blasting a game.* call to every seat, but not
to players who are standing.

See the C<call_ui_function> method of L<Volity::Player> for arguments and return values.

=cut

# See the POD section above for what this group of methods does...
sub call_ui_function_on_everyone {
  my $self = shift;
  map($_->call_ui_function(@_), $self->referee->players);
}

sub call_ui_function_on_observers {
  my $self = shift;
  my @observers = grep(not(defined($_->seat)), $self->referee->players);
  map($_->call_ui_function(@_), @observers);
}

sub call_ui_function_on_seats {
  my $self = shift;
  map($_->call_ui_function(@_), $self->referee->seats);
}

# register_config_variables: Turn the given strings into class variables,
# and also add them to the internal list of configuration vars.
# We need to remember these for proper URI construction.
sub register_config_variables {
    my $self = shift;
    my $class = ref($self);
    foreach (@_) {
	$self->config_variables->{$_} = 1;
    }

}

# full_uri: Returns the base uri plus a query string based on current
# game config settings.
sub full_uri {
    my $self = shift;
    my $class = ref($self);
    my $base_uri = $class->uri;
    my @query_string_parts;
    for my $config_variable_name (@{$class->config_variables}) {
	push (@query_string_parts, "$config_variable_name=".
	      $class->$config_variable_name)
    }
    my $full_uri = "$base_uri?" . join('&', @query_string_parts);
    return $full_uri;
}

# is_config_variable: Returns truth if the given string is the name of
# a config variable.
sub is_config_variable {
    my $self = shift;
    my ($variable_name) = @_;
    return $self->config_variables->{$variable_name};
}

# tell_seat_about_config: Tell the given seat about the current game
# configuaration, complete with the setter info.
# This can be convenient to call in the middle of a state-sending blast.
sub tell_seat_about_config {
    my $self = shift;
    my ($seat) = @_;
    while (my ($config_variable_name, $setter) = each(%{$self->config_variable_setters})) {
	unless ($setter) {
	    $setter = $self->referee->table_creator;
	}
	my $value = $self->$config_variable_name;
	$seat->call_ui_function($config_variable_name, $setter->nick, $value);
    }
}

# is_active: Convenience method to determine whether the game is actually in
# play and not suspended.
sub is_active {
    my $self = shift;
    return $self->is_afoot && not($self->is_suspended);
}

# is_disrupted: Return 1 if at least one seat is lacking control. 0 otherwise.
sub is_disrupted {
    my $self = shift;
    if (grep(not($_->is_under_control), grep(not($_->is_eliminated), $self->seats)) && not($self->is_abandoned)) {
	return 1;
    } else {
	return 0;
    }
}

# is_abandoned: Return 1 if the game appears to be utterly devoid of controlled
# seats.
sub is_abandoned {
    my $self = shift;
    if (grep($_->is_under_control, grep(not($_->is_eliminated),$self->seats))) {
	return 0;
    } else {
	return 1;
    }
}


# send_full_state_to_player: Blast the given player (not seat) with full
# game state. By default it's a no-op. Subclasses should override this.
sub send_full_state_to_player {
    my $self = shift;
    my ($player) = @_;
    my $player_jid = $player->jid;
    $self->logger->warn("Base class recover_state called for $player_jid. Nothing to do.");
}

###################
# Game actions
###################

=item end

Ends the game. The referee will automatically handle seat
notification. The bookkeeper will be sent a record of the game's
results at this time, so be sure you have the game's winner-list
arranged correctly.

I<Note> that the balancing C<start> method is actually a callback; see
L<"Callback methods">.

=cut

# end: called when the game has come to a close, one way or another.
# Does very little right now.
sub end {
  my $self = shift;
  my ($args) = @_;
  $self->is_afoot(0);
  $self->is_finished(1);
  $self->referee->end_game;
}


############
# Callbacks
############

=head2 Callback methods

=over

C<Volity::Game> provides default handlers for these methods, called on the game object by different parts of Frivolity. You may override these methods if you want your game module to behave in some way other than the default (usually a no-op).

=item start

Called by the referee after it creates the game object and is ready to
begin play. It gives the object a chance to perform whatever it would
like to do as its first actions, prior to seats starting to send
messages to it.

The default behavior is a no-op. A common reason to override
this method is the need to send a set-up function call to the game's
seats.

=item has_acceptable_config

Called by the referee every time a player signals readiness. Returns 1
if the current configuration settings are OK to start a new
game. Returns 0 if the config settings are currently wedged in an
unplayable state. 

In the latter case, the referee will not allow the
player to delcare readiness.

By default, it just returns 1 without checking anything.

Note: You I<don't> need to check required-seat occupancy; this is
handled for you, before has_acceptable_config is called.

=item send_full_state_to_player ($player)

If your game is complex enough to carry a state between turns (and it
probably is), then you'll want to define this method. It will allow
players who are returning to the table after the game has started to
learn about the game's current state all at once, either because
they're observers wandering into the table or they're players
returning to the table (perhaps after a network drop or crash on their
end). It also allows bots who jump in to replace a vanished player to
catch up on what they've missed.

The argument is the Volity::Player object who needs to be brought up to speed.

B<Important note>: Use the C<state_seat> method of the player object,
I<not> the C<seat> method, to see what the player's point of view is
for purposes of sending state. This always returns the last seat that
the player sat in I<while the game was active>, preventing
"accidental" snooping of other seats' game states while the game is
suspended.

=back

=cut

sub start { }

sub has_acceptable_config {
    return 1;
}

sub handle_normal_message { }
sub handle_groupchat_message { }
sub handle_chat_message { }
sub handle_headline_message { }
sub handle_error_message { }


=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003 by Jason McIntosh.

=cut


1;

