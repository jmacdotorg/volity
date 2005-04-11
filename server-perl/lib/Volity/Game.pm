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

=item player_class

The class that this game's players belong to. When the game wants to make new players, it calls this class's constructor.

If you don't set this, it defaults to using the C<Volity::Player> class.

=item referee

Returns the C<Volity::Referee> object that owns this game. Use this
object to fetch player information while the game is afoot; see
L<Volity::Referee> for the salient methods.

I<Shortcut> You can call any of the referee's methods simply by
calling them on the game object. For example, calling
C<$game->players> is exactly equivalent to (and will return exactly
the same player objects as) calling C<$game->referee->players>.

=item is_afoot

If the game is still being set up, returns falsehood. If the game has
already started, returns truth.

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

=item max_allowed_players

An integer specifying the maximum number of players this game
allows. If undefined, then there is no limit to the number of players
who can join.

=item min_allowed_players

An integer specifying the minimum number of players this game requires
for play. Defaults to 1.

=item uri

I<Required.> The URI of the ruleset that this particular game module
implements. Consult the core Volity documentation for more information
on how this works.

=item ruleset_version

I<Required.> The version number of the ruleset that this particular
game module implements. A client-side UI file consults this number to
determine its own compatibility with a game server.

=back

=cut

use warnings; no warnings qw(deprecated);
use strict;

use base qw(Volity Class::Data::Inheritable);

use fields qw(winners quitters current_player current_player_index referee config_variables is_afoot config_variable_setters);

foreach (qw(uri name description ruleset_version website max_allowed_players min_allowed_players player_class)) {
  __PACKAGE__->mk_classdata($_);
}

use Scalar::Util qw(weaken);

use Carp qw(carp croak);

sub initialize {
  my $self = shift;
  $self->current_player_index(0);
  weaken($self->{referee});
  $self->config_variables({});
  $self->config_variable_setters({});
}

sub AUTOLOAD {
  my $self = shift;
  our $AUTOLOAD;
  my ($method) = $AUTOLOAD =~ /^.*::(.*)$/;
  if ($self->referee->can($method)) {
    return $self->referee->$method(@_);
  } else {
    croak ("Unknown method $method");
  }
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
  if ($index + 1< ($self->referee->players)) {
    $index++;
  } else {
    $index = 0;
  }
  $self->current_player_index($index);
  $self->current_player(($self->referee->players)[$index]);
}

# call_ui_function_on_everyone: A convenience method for blasting something
# at every single player.
sub call_ui_function_on_everyone {
  my $self = shift;
  map($_->call_ui_function(@_), $self->referee->players);
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
    use Data::Dumper;
    warn Dumper($self->config_variables);

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

# tell_player_about_config: Tell the given player about the current game
# configuaration. Called by the ref when a new player joins.
sub tell_player_about_config {
    my $self = shift;
    my ($player) = @_;
    while (my ($config_variable_name, $setter) = each(%{$self->config_variable_setters})) {
	unless ($setter) {
	    $setter = $self->referee->table_creator;
	}
	my $value = $self->$config_variable_name;
	$player->call_ui_function($config_variable_name, $setter->nick, $value);
    }
}

###################
# Game actions
###################

=head2 end

Ends the game. The referee will automatically handle player
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
  $self->referee->end_game;
}


############
# Callbacks
############

=head1 Callback methods

C<Volity::Game> provides default handlers for these methods, called on the game object by different parts of Frivolity. You may override these methods if you want your game module to behave in some way other than the default (usually a no-op).

=head2 start

Called by the referee after it creates the game object and is ready to
begin play. It gives the object a chance to perform whatever it would
like to do as its first actions, prior to players starting to send
messages to it.

The default behavior is a no-op. A common reason to override
this method is the need to send a set-up function call to the game's
players.

=cut

sub start { }

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

