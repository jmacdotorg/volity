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

See L<Volity::Game::TicTacToe> and its source code for a simple
but full-featured example.

=head1 DESCRIPTION

This class provides a framework for writing Volity game modules in
Perl. A Volity game module will be a subclass of this class. 

To turn your subclass into an active Volity parlor, you can pass it to
the C<volityd> program via its C<game_class> config option (see L<volityd>).

=head1 USAGE

To use this module, subclass it. Create your own Perl package for your
game, and have it inherit from C<Volity::Game>. Then define game logic
and other behavior primarily by writing callback methods, as described in
L<"CALLBACK METHODS">.

See L<Volity::Game::TicTacToe> for a simple but complete example of a
Volity::Game subclass.

Some things to keep in mind while writing your subclass:

=head2 It's a pseudohash

The object that results from your class will be a Perl pseudohash that
makes use of the C<fields> pragma. (See L<fields>.) As the example
shows, you should declare the the instance variables you intend to use
with a C<use fields()> invocation.

Other than that, an instance if your subclass will work just like a
hash-based Perl object.

=head2 Use (but don't abuse) the initialize() method

The C<Volity::Game> base class constructor calls C<initialize()> as a
final step.

If you override this method to peform game-specific initialization on
your subclass, it I<must> have a return value of
C<$self-E<gt>SUPER::initialize(@_)>.

=head1 METHODS

=head2 Class methods

These methods are used to set some general configuration information about
the game, rather than specific information about any particular instance
thereof.

=over

=item name

A I<brief> name of this game module, which the game server will use to
advertise itself through service discovery and other means. If left
undefined, a boring default value will be used (probably the JID that this
server is running under.

=item description

A longer text description of this game module.

=item uri

I<Required.> The URI of the ruleset that this particular game module
implements. Consult the core Volity documentation for more information on
how this works: http://www.volity.org/wiki/index.cgi?Ruleset_URI

=item ruleset_version

I<Required.> The version number of the ruleset that this particular game
module implements. A client-side UI file consults this number to determine
its own compatibility with a game server.

=item seat_ids 

An array reference of strings representing the IDs of I<all> the seats
that this game implementation supports. Example:

 My::Game->seat_ids([qw(black white)]);
 my $seat_ids = My::Game->seat_ids; # $seat_ids is now ['black', 'white']

=item required_seat_ids

An array reference of strings representing the IDs of
role-differentiated seats that players should be aware of, as defined
by the ruleset.

=item seat_class

The class that this game's seats belong to. When the game wants to make new
seats, it calls this class's constructor.

It defaults to using the base C<Volity::Seat> class.

=back

=head2 Object methods

=over 

=item seats

Returns a list of all the seat objects currently at the table.

The objects will be instances of C<Volity::Seat>, unless you specified
another class to use with the C<seat_class()> method.

=item players

Returns a list of all the player objects currently at the table. This
includes all seated and standing players, and doesn't discriminate
between humans and bots. (Call methods such as C<seat()> and
C<is_bot()> on the resulting objects to help you sort out which is
which.)

The objects will be of the C<Volity::Player> class.

=item is_afoot

If the game is still being set up, returns falsehood. If the game has
already started, returns truth.

=item is_suspended
 
If the game is in play but suspended (as a result of a player asking the ref
to suspend the game, or the ref reacting to a player's sudden departure),
this returns 1. Otherwise, returns 0.

=item is_active

Convenience method: If the game is afoot and not suspended, returns truth.
Otherwise, returns falsehood.

=item turn_order ($seat_id_1, $seat_id_2, ...)

This is an accessor to an internal list of seat IDs representing the
game's turn order. Sets the list if called with arguments, and returns
it in any case. If set, has the side effect of setting the value of
the C<current_seat> instance variable to the seat whose ID is first on
the list.

However, if any of the provided seat IDs aren't those of known seats,
you'll get a fatal error here.

This is mainly useful if you plan on using the C<rotate_current_seat>
method (see L<"Other object methods">), which is itself just a
convenience method for the common case of having a fixed,
round-the-table turn order, which not every game has.

Note that a game module implementing a ruleset that doesn't use turns
won't use this.

=item seats_in_play

Returns a list of the seats in play -- that is, seats that contained
players the last time the game started or resumed, with eliminated
seats filtered out. Each member of the list is an instance of
Volity::Seat or the sublcass your game module specified through the
C<seat_class> accessor method.

This is distinct from the referee object's C<seats> methods, which
returns I<all> seats at the table, regardless of status or population.

=cut

use warnings;
no warnings qw( deprecated );
use strict;

use base qw( Volity Class::Data::Inheritable );

use fields
    qw( winners quitters turn_order turn_queue referee current_seat
    config_variables is_afoot is_suspended is_finished
    config_variable_setters has_initialized );

# Define some class accessors.
foreach (
    qw( uri name description ruleset_version website max_allowed_seats
    min_allowed_seats seat_class seat_ids required_seat_ids )
    )
{
    __PACKAGE__->mk_classdata($_);
}
foreach (qw( seat_ids required_seat_ids )) {
    __PACKAGE__->$_( [] );
}

use Scalar::Util qw( weaken );
use Volity::WinnersList;

use Carp qw( carp croak );

sub initialize {
    my $self = shift;

    #  $self->current_player(($self->players)[0]);
    weaken( $self->{referee} );
    $self->config_variables(        {} );
    $self->config_variable_setters( {} );
    $self->winners( Volity::WinnersList->new );
    $self->{turn_order} = [] unless defined($self->{turn_order});
    $self->{turn_queue} = [] unless defined($self->{turn_queue});
    $self->is_finished(0);
    $self->has_initialized(1);
}

sub AUTOLOAD {
    my $self = shift;
    our $AUTOLOAD;
    my ($method) = $AUTOLOAD =~ /^.*::(.*)$/;
    if (   $self->can('referee')
        && $self->referee
        && $self->referee->can($method) )
    {
        return $self->referee->$method(@_);
    }
    elsif ($method eq "DESTROY") {
	return;
    }
    else {
        Carp::cluck("Unknown method $method");
    }
}

sub turn_order {
    my $self = shift;
    my $class = ref($self);
    my @known_seat_ids = @{$class->seat_ids};
    my @seat_ids = @_;
    if (@seat_ids) {
	# Perform a sanity check.
	for my $seat_id (@seat_ids) {
	    unless (grep($_ eq $seat_id, @known_seat_ids)) {
		$self->expire("You want to set the turn order to (@seat_ids), but I know of no seat with ID $seat_id.");
	    }
	}
	# Set the current seat.
	$self->current_seat($self->look_up_seat_with_id($seat_ids[0]));
	# Set this variable's value.
	$self->{turn_order} = \@seat_ids;
	$self->{turn_queue} = \@seat_ids;
    }
    return @{$self->{turn_order}};
}

=item current_seat ($seat)

Called with no arguments, returns the seat whose turn is up.

Called with a Volity::Seat object as an argument, sets that seat as
the current seat, and then returns it. Called with anything else, it
logs a warning and returns the current seat (regardless of your
argument).

If you are making use of the C<turn_order> list, setting a new current
seat does not affect the list, but it does advance the internal
turn-order pointer to this seat's position on it, I<if the seat is a
member of the list>. Subsequently calling C<rotate_current_seat()>
will advance the pointer to (and return) the seat that is after the
given one on the turn order list.

If the given seat doesn't exist in the turn order list (as is the case
when the list is not defined), then the list remains unaffected.

Note that a game module implementing a ruleset that doesn't use turns
needn't use this method at all.

=cut

sub current_seat {
    my $self = shift;
    my ($seat) = @_;
    if ($seat) {
	if (ref($seat) && $seat->isa("Volity::Seat")) {
	    $self->{current_seat} = $seat;
	    # Now advance the turn queue so that this seat is at the front,
	    # but only if the seat's ID is a member of the turn queue.
	    if (grep($seat->id eq $_, $self->turn_queue)) {
		while ($seat->id ne $self->{turn_queue}->[0]) {
		    push (@{$self->{turn_queue}}, shift(@{$self->{turn_queue}}));
		}
	    }
	}
	else {
	    my ($package, $filename, $line) = caller;
	    $self->logger->warn("Volity::Game::current_seat must be called with a Volity::Game::Seat object or subclass. Instead, it was called with this: '$seat'. (Called from $filename, line $line.)");
	}
    }
    
    return $self->{current_seat};
}

=item rotate_current_seat

Convenience method that simply sets the next seat in the turn order
list, skipping over any eliminated seats. Returns that seat object.

If said list is empty, the current seat remains the same, and a
warning is logged. If the list is not empty but the current seat is
not a member of the list, then the pointer advances to the next player
based on the last current player who I<was> a member (or the first
member if there weren't any) and you'll also get a warning because
that's kind of weird, don't you think?

This method is useful to call at the end of a turn, at least in games
where the turn order is stable enough for the C<turn_order> method to
be useful as well. Game modules can always advance the turn manually
by calling the C<current_player> accessor with arguments. (And some
games don't have turns at all...)

Note that a game module implementing a ruleset that doesn't use turns
won't use this.

=item register_config_variables (@variables)

Registers the given instance variables (which should be declared in your
subclass's C<use fields> pragma) as holding game configuration information.
This will allow your game to accept RPC calls of the form
"game.$variable_name([args])" even when there is no game active. (The
referee normally kicks back such requests with an RPC fault.)

Normally you'll only call this method once, as part of your C<initialize()>
method definition.

=item winners

Returns this game's Volity::WinnersList object. If you want your game
do generate proper game records for storage with the Volity
bookkeeper, then you must use this object to specify the seats'
winning order I<before> you call the game object's C<end> method. See
L<Volity::WinnersList> for the list object's API.

=cut

sub rotate_current_seat {
    my $self  = shift;
    my $current_seat = $self->current_seat;

    # Get the seats represented by the turn order list
    my @seats = map($self->referee->look_up_seat_with_id($_), $self->turn_queue);

    # Check for weirdness.
    if ($current_seat && not(grep($_ eq $current_seat, @seats))) {
	my $turn_order_string = join(', ', $self->turn_order);
	my $current_seat_id = $current_seat->id;
	$self->logger->warn("rotate_current_seat() called when the current seat ($current_seat_id) isn't in the turn order (consisting of: $turn_order_string)");
    }

    # Give the seat list a rotation, then filter out eliminated seats
    # as well as seats without any registered players.
    push(@seats, shift(@seats));
    @seats = grep(not($_->is_eliminated) && $_->registered_player_jids, @seats);
    
    if (@seats) {
	$self->current_seat($seats[0]);
	return $seats[0];
    }
    else {
	$self->logger->warn("rotate_current_seat() called when there's no non-eliminated seats with IDs on the turn list. No seat returned.");
	return;
    }
}

sub seats_in_play {
    my $self = shift;
    return grep(not($_->is_eliminated) && $_->registered_player_jids, $self->seats);
}

=item call_ui_function_on_everyone ($function, @args)

A convenience method for blasting a game.* call to I<all> players at a
table, seated and otherwise.

=item call_ui_function_on_observers ($function, @args)

A convenience method for blasting a game.* call to every player at the
table who is not seated.

=item call_ui_function_on_seats ($function, @args)

A convenience method for blasting a game.* call to every seat, but not to
players who are standing.

=back

=cut

# See the POD section above for what this group of methods does...
sub call_ui_function_on_everyone {
    my $self = shift;
    map( { $_->call_ui_function(@_) } $self->referee->players );
}

sub call_ui_function_on_observers {
    my $self = shift;
    my @observers = grep( { not defined $_->seat } $self->referee->players );
    $_->call_ui_function(@_) for @observers;
}

sub call_ui_function_on_seats {
    my $self = shift;
    $_->call_ui_function(@_) for $self->referee->seats;
}

# register_config_variables: Turn the given strings into class variables,
# and also add them to the internal list of configuration vars.
# We need to remember these for proper URI construction.
sub register_config_variables {
    my $self  = shift;
    my $class = ref($self);
    foreach (@_) {
        $self->config_variables->{$_} = 1;
    }

}

# full_uri: Returns the base uri plus a query string based on current
# game config settings.
sub full_uri {
    my $self     = shift;
    my $class    = ref($self);
    my $base_uri = $class->uri;
    my @query_string_parts;
    for my $config_variable_name ( @{ $class->config_variables } ) {
        push( @query_string_parts,
            "$config_variable_name=" . $class->$config_variable_name );
    }
    my $full_uri = "$base_uri?" . join( '&', @query_string_parts );
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
    while ( my ( $config_variable_name, $setter ) =
        each( %{ $self->config_variable_setters } ) )
    {
        unless ($setter) {
            $setter = $self->referee->table_creator;
        }
        my $value = $self->$config_variable_name;
        $seat->call_ui_function( $config_variable_name, $setter->nick, $value );
    }
}

# is_active: Convenience method to determine whether the game is actually in
# play and not suspended.
sub is_active {
    my $self = shift;
    return $self->is_afoot && not( $self->is_suspended );
}

# is_disrupted: Return 1 if at least one seat is lacking control. 0 otherwise.
sub is_disrupted {
    my $self = shift;
    if (
        grep( { not $_->is_under_human_control }
            grep( { not $_->is_eliminated } $self->seats ) )
        && not( $self->is_abandoned )
        )
    {
        return 1;
    }
    else {
        return 0;
    }
}

# is_abandoned: Return 1 if the game appears to be utterly devoid of
# human-controlled seats.
sub is_abandoned {
    my $self = shift;
    if (
        grep( { $_->is_under_human_control }
            grep( { not $_->is_eliminated } $self->seats ) )
        )
    {
        return 0;
    }
    else {
        return 1;
    }
}

# send_config_state_to_player: Bast the given player (not seat) with
# config state. By default it's a no-op. Subclasses can override this.
sub send_config_state_to_player {
    my $self = shift;
    my ($player)   = @_;
    my $player_jid = $player->jid;
    $self->logger->warn("Base class send_config_state_to_player called for $player_jid. Nothing to do.");
}

# send_game_state_to_player: Blast the given player (not seat) with
# game state. By default it's a no-op. Subclasses should override this.
sub send_game_state_to_player {
    my $self       = shift;
    my ($player)   = @_;
    my $player_jid = $player->jid;
    # For backwards (pre-0.5.2) compatibility, see if we instead have a 
    # "send_full_state_to_player" method instead.
    if ($self->can("send_full_state_to_player")) {
	return $self->send_full_state_to_player(@_);
    } else {
	$self->logger->warn("Base class send_full_state_to_player called for $player_jid. Nothing to do.");
    }
}

###################
# Game actions
###################

=over

=item end

Ends the game. The referee will automatically handle seat
notification. The bookkeeper will be sent a record of the game's
results at this time, so be sure you have the game's winner-list
arranged correctly.

I<Note> that the balancing C<start> method is actually a callback; see
L<"Callback methods">.


=back

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

=head1 CALLBACK METHODS

=head2 Ruleset-level callbacks

You must define a callback in your subclass for every
player-to-referee method defined in the ruleset that your module
implements.

The name of the callback method will be exacty the same as the name of
the RPC, except with the "game." prefix replaced by "rpc_". So, for
example, the PRC "game.move_piece" would trigger the method
C<rpc_move_piece()> in your subclass.

The first argument to the method (after the usual reference to the
object) is the C<Volity::Seat> object that made the call, and any
remaining arguments are the arguments of the RPC itself. Therefore, if
the ruleset decrees that the arguments to C<game.move_piece> are
C<piece_id> and C<destination>, then the first few lines of your
callback might look like this:

 sub rpc_move_piece {
     my $self = shift;
     my ($seat, $piece_id, $destination) = @_;
     # Game logic here....
     return "volity.ok";
 }

The callback's return value I<must> be a Volity token. (See
http://www.volity.org/wiki/index.cgi?Token). This will most commonly
be a ruleset-defined error token to express a rejection of the
caller's move or request, or a "volity.ok" token otherwise.

If the token takes additional arguments, simply add them to the
return-value list after the token.

See C<Volity::Game::TicTacToe> for an illustration of both successful
and errorful token returns, particularly in its C<rpc_mark()> method.

=head2 Volity-level callbacks

C<Volity::Game> provides default handlers for these methods, called on the
game object by different parts of Frivolity. You may override these methods
if you want your game module to behave in some way other than the default
(usually a no-op).

=over

=item start

Called by the referee after it creates the game object and is ready to begin
play. It gives the object a chance to perform whatever it would like to do
as its first actions, prior to seats starting to send messages to it.

The default behavior is a no-op. A common reason to override this method is
the need to send a set-up function call to the game's seats.

=item has_acceptable_config

Called by the referee every time a player signals readiness. Returns 1 if
the current configuration settings are OK to start a new game. Returns 0 if
the config settings are currently wedged in an unplayable state.

In the latter case, the referee will not allow the player to declare
readiness.

By default, it just returns 1 without checking anything.

Note: You I<don't> need to check required-seat occupancy; this is handled
for you, before has_acceptable_config is called.

=item send_config_state_to_player ($player)

This method should update the given player about the table's current
game-specific configuration, probably through a series of
C<$player-E<gt>call_ui_function> calls. The argument is the Volity::Player
object who needs to be brought up to speed.

As an example, imagine that your game implements a ruleset where
players can set a goal score before play. The referee-to-player RPC
that anncounces this option happens to be
C<game.goal_score($score)>. When a player joins a table running this
game, its client will request the current state, and will ultimately
fire the C<send_config_state_to_player($player)> method on your game
object. So it's your responsibility to make sure that it responds by
calling C<$player-E<gt>call_ui_function("goal_score", $self->goal_score)>,
assuming that your Game object has a field called C<goal_score> that
holds this number.

By default, does nothing. If your game doesn't have configuation
beyond the usual sit/stand/ready stuff, then you can probably get away
with this default.

This method is not to be confused with the active game state, which is
requested through the C<send_game_state_to_player> method, described
below.

=item send_game_state_to_player ($player)

If your game is complex enough to carry a state between turns (and it
probably is), then you'll want to define this method. It will allow
players who join the table after the game has started to learn about
the game's current state all at once, either because they're observers
wandering into the table or they're players returning to the table
(perhaps after a network drop or crash on their end). It also allows
bots who jump in to replace a vanished player to catch up on what
they've missed.

The argument is the Volity::Player object who needs to be brought up to
speed.

B<Important note>: Use the C<state_seat> method of the player object, I<not>
the C<seat> method, to see what the player's point of view is for purposes
of sending state. This always returns the last seat that the player sat in
I<while the game was active>, preventing "accidental" snooping of other
seats' game states while the game is suspended.

=item game_has_resumed ( )

This is called after the players at a table have suspended and then
resumed the game. Since there's a chance that players have joined or
switched seats since the last time the game was active, you may wish
to override this method in order to update players' UIs. This is
particularly true for games with private information, such as hands of
cards.

You don't need to do anything else to implement game suspension or
resumption; the base classes take care of everything for you,
including updating the table's seat and player objects.

Overriding this method is optional; by default, it does nothing.

=back

=cut

sub start { }

sub has_acceptable_config {
    return 1;
}

sub game_has_resumed { }

sub handle_normal_message    { }
sub handle_groupchat_message { }
sub handle_chat_message      { }
sub handle_headline_message  { }
sub handle_error_message     { }

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003-2006 by Jason McIntosh.

=cut

1;

