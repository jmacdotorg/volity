package Volity::Bot;

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

Volity::Bot - A framework for creating automated Volity players

=head1 SYNOPSIS

See L<Volity::Bot::TicTacToe::Random> and its source code for a simple
but full-featured example.

=head1 DESCRIPTION

This class provides a framework for writing Volity bots in Perl. A bot
is a program that acts like a human game player. Most bots are
servants of game parlors, and are instanced when a human at a table
asks the referee there to create one.

=head1 USAGE

To write your own bot, create a subclass of C<Volity::Bot>. The base
class takes care of a lot of things that all bots need to do,
including awareness of the table and reacting to players dragging it
into seats (or pushing it out of them).

All you need to do in your subclass is write logic specific to the
game that you wish your bot to play. This comes down to defining
callback methods that capture incoming RPCs from the table's referee,
and deciding which RPC calls back to the referee that the bot should
make in response.

You put your subclass into use by configuring the C<volityd> program
to use it as its bot class; see L<volityd>. This will have referees
spawned under that game parlor to instance your bot subclass whenever
players request bots to play against. You won't work with these bot
objects directly; they are entirely callback-driven.

Some things to keep in mind when creating your subclass...

=head2 It's a pseudohash

The object that results from your class will be a Perl pseudohash that
makes use of the C<fields> pragma. (See L<fields>.) As the example
shows, you should declare the the instance variables you intend to use
with a C<use fields()> invocation.

=head2 Use (but don't abuse) the initialize() method

The C<Volity::Bot> base class constructor calls C<initialize()> as a
final step, and you are welcome to override this in order to give your
object some final preparations before sending it out into the world.

If you do override this method, however, it I<must> have a return
value of C<$self->SUPER::initialize(@_)> or untold chaos will result.

=head1 METHODS

=head2 Information accessors

These methods take no arguments, and return bits of information about
the bot's place in the world. You can call them from within any of
your callback methods.

=over

=item muc_jid

The Jabber ID of the MUC where the bot finds itself.

=item referee_jid

The Jabber ID of the referee at the bot's table.

=item nickname

The bot's nickname at this table.

=item am_seated

Returns truth if the bot is seated; falsehood otherwise.

=item am_ready

Returns truth if the bot is seated I<and> ready to play, falsehood otherwise.

=item seat_id

Returns the ID of the seat where the bot current finds itself, if it
is sitting down.

I<Note> for backwards compatibility, the C<seat()> method returns the
same value. It does I<not> return a C<Volity::Seat> object... only a
string.

=back

=cut

# A package for automated Volity players.
# Should be suitable for both 'ronin' and 'retainer' bots.

use warnings;
use strict;
use base qw( Volity::Jabber Class::Data::Inheritable );
use fields
    qw( muc_jid referee_jid name_modifier nickname am_seated am_ready seat_id );

use Carp qw( croak );
use POE;
use Time::HiRes qw(gettimeofday);

foreach (qw( name description )) {
    __PACKAGE__->mk_classdata($_);
}

# seat: This is here for backwards compatibility.
sub seat {
    my $self = shift;
    return $self->seat_id(@_);
}

# We override the constructor to perform some sanity checks, and
# insert additional config information based on class data.
sub new {
    my $invocant = shift;
    my $class    = ref($invocant) || $invocant;
    my ($config) = @_;
    foreach (qw( user host password resource )) {
        if ( defined( $$config{$_} ) ) {
            next;
        }
        else {
            unless ( defined( $class->$_ ) ) {
                croak("The $class class doesn't have any $_ data defined!!");
            }
            $$config{$_} = $class->$_;
        }
    }
    my $self = $class->SUPER::new($config);
    $self->name_modifier(q{});
    return $self;
}

sub init_finish {
    my $self = $_[OBJECT];
    $self->logger->debug("New bot, reporting for duty.");
    $self->logger->debug(
        "I will try to join the MUC at this JID: " . $self->muc_jid );

    # XXX This doesn't handle the case for when the bot fails to join, due
    # to someone using its nickname already present in the MUC.
    # I'll need to add an error handler for when this happens.
    $self->join_table;

}

sub stop {
    my $self = shift;
    $self->kernel->post( $self->alias, 'shutdown_socket', 0 );
}

# This presence handler detects a table's referee through MUC attributes.
# It also watches for general presence updates, and updates the user's
# internal roster object as needed.
sub jabber_presence {
    my $self = shift;
    my ($node) = @_;
    my $x;    # Hey, that's the name of the element, OK?
    if ( defined( $node->attr('type') ) and $node->attr('type') eq 'error' ) {

        # Ruh roh. Just print an error message.
        my $error = $node->get_tag('error');
        my $code  = $error->attr('code');
        $self->logger->debug("Got an error ($code):");
        my $message = $error->data || "Something went wrong.";
        $self->logger->debug($message);
        if ( $code == 409 ) {

            # Aha, we have failed to join the conf.
            # Change our name and try again.
            if ( $self->name_modifier ) {
                $self->name_modifier( $self->name_modifier + 1 );
            }
            else {
                $self->name_modifier(1);
            }
            $self->join_table;
        }
        return;
    }
    if (
        ( $node->get_tag('x') )
        and (
            ($x) =
            grep( { $_->attr('xmlns') eq "http://jabber.org/protocol/muc#user" }
                $node->get_tag('x') )
        )
        )
    {

        # Aha, someone has joined the table.
        my $affiliation = $x->get_tag('item')->attr('affiliation');
        $self->logger->debug( "I see presence from " . $node->attr('from') );
	
	# See if they have a caps (JEP-0115) element in their presence.
	# If so, this may clue us in to what sort of Volity entity this is.
	if ((my $c = $node->get_tag('c')) && 
	    ($node->get_tag('c')->attr('node') eq "http://volity.org/protocol/caps")) {
	    my $volity_role = $c->attr('ext');
	    if ($volity_role eq "referee") {
		# We've found the referee!
		$self->referee_jid( $node->attr('from') );
	    }
        }
	
	
    }
}

# We override Volity::Jabber's send_presence in order to attach some
# additional JEP-0115 information.
sub send_presence {
    my $self = shift;
    my ($config) = @_;
    $config ||= {};
    $$config{caps} = {
	node => "http://volity.org/protocol/caps",
	ext => "bot",
	ver => "1.0",
    };
    return $self->SUPER::send_presence($config);
}

sub join_table {
    my $self = shift;

    # Attempt to join the MUC with our best idea of a nickname.
    # If this fails, the error handler will increment the nick-
    # modifier.
    my $nick = $self->name . $self->name_modifier;
    $self->join_muc( { jid => $self->muc_jid, nick => $nick } );
    $self->nickname($nick);
}

sub is_jid {
    my ($jid) = @_;
    if ( $jid =~ /^[\w-]+@[\w-]+(?:\.[\w-]+)*(?:\/[\w-]+)?/ ) {
        return $jid;
    }
    else {
        return;
    }
}

sub declare_readiness {
    my $self = shift;
    $self->logger->debug(
        "Sending a declaration of readiness to " . $self->referee_jid );
    $self->send_rpc_request(
        {
            to         => $self->referee_jid,
            id         => 'ready-request',
            methodname => 'volity.ready',
            handler    => 'ready',
        }
        );
}

sub sit {
    my $self = shift;
    $self->logger->debug(
        "Sitting down at table with referee " . $self->referee_jid );
    $self->send_rpc_request(
        {
            to         => $self->referee_jid,
            id         => 'sit',
            methodname => 'volity.sit',
            args       => [ $self->jid ],
            handler    => 'seat',
        }
        );
}

sub handle_rpc_request {
    my $self       = shift;
    my ($rpc_info) = @_;
    my $method     = $$rpc_info{method};

    if ( $method =~ /^game\.(.*)$/ ) {
	my $subclass_method = "game_rpc_$1";

	$self->try_to_call_subclass_method($subclass_method, @{$$rpc_info{args}});
    }
    elsif ( $method =~ /^volity\.(.*)$/ ) {
	my $subclass_method = "volity_rpc_$1";
	$self->try_to_call_subclass_method($subclass_method, @{$$rpc_info{args}});
	# If I'm seated and not ready, try to become ready.
	if ( $self->am_seated && not( $self->am_ready ) ) {
	    $self->declare_readiness;
	}
    }

}

sub try_to_call_subclass_method {
    my $self = shift;
    my ($subclass_method, @args) = @_;
    if ($self->can($subclass_method)) {
	$self->$subclass_method(@args);
    }
    else {
	$self->logger->warn("I seem to be lacking a $subclass_method method.");
    }
}

sub rpc_response_ready {
    my $self      = shift;
    my ($message) = @_;
    my ($flag)    = @{ $$message{response} };
    $self->logger->debug("Got a $flag response to my ready request.");
}

sub rpc_response_seat {
    my $self      = shift;
    my ($message) = @_;
    my ($flag)    = @{ $$message{response} };
    $self->logger->debug("Got a $flag response to my seat request.");

    # Possibly babble to the MUC, if I can't sit down.
    # XXX This should be internationalized.
    my $chat_message;
    if ( $flag eq 'volity.no_seats' ) {
        $chat_message = "I can't sit down; all the seats are full.";
    }
    if ($chat_message) {
        $self->send_message(
            {
                to   => $self->muc_jid,
                type => "groupchat",
                body => $message,
            }
            );
    }
}

# Here are some methods defining default behavior for basic volity.* calls.

sub volity_rpc_player_sat {
    my $self = shift;
    my ($player_jid, $seat_id) = @_;
    if ( $player_jid eq $self->jid ) {
	$self->am_seated(1);
	$self->seat( $seat_id );
    }
}

sub volity_rpc_player_stood {
    my $self = shift;
    my ($player_jid) = @_;
    if ( $player_jid eq $self->jid ) {
	$self->am_seated(0);
    }
}

sub volity_rpc_player_unready {
    my $self = shift;
    my ($player_jid) = @_;
    if ( $player_jid eq $self->jid ) {
	$self->am_ready(0);
    }

}

sub volity_rpc_player_ready {
    my $self = shift;
    my ($player_jid) = @_;
    if ( $player_jid eq $self->jid ) {
	$self->am_ready(1);
    }
}

sub volity_rpc_end_game {
    my $self = shift;
    $self->am_ready(0);
}

sub volity_rpc_suspend_game {
    my $self = shift;
    $self->am_ready(0);
}

=head2 Action methods

=over

=item send_game_rpc_to_referee ( $method, @args )

Makes an RPC call to the table's referee. It automatically puts it
into the "game." namespace for you. So, to call the ruleset's
C<game.move_piece( piece, location )> RPC, you'd call in your Perl
code C<$self->send_game_rpc_to_referee ( "move_piece", $piece,
$location )>.

Note that you I<should> define a separate callback method for every
distinct RPC call that your bot class can make. See L<"RPC Response
callbacks">. In fact, the base class will log a warning if you don't
have such a callback set up for an RPC you call with this method.

=back

=cut

sub send_game_rpc_to_referee {
    my $self = shift;
    my ($method, @args) = @_;
    my %args = (
		to=>$self->referee_jid,
		id=>scalar(gettimeofday()),
		methodname=>"game.$method",
		args=>\@args,
		);
    my $response_callback_method = "rpc_response_game_" . $method;
    if ($self->can($response_callback_method)) {
	$args{handler} = "game_" . $method;
    }
    else {
	$self->logger->warn("Calling RPC method game.$method without a corresponding callback method defined. (It would be called $response_callback_method.)");
    }

    $self->make_rpc_request(\%args);
}

sub send_volity_rpc_to_referee {
    my $self = shift;
    my ($method, @args) = @_;
    my %args = (
		to=>$self->referee_jid,
		id=>scalar(gettimeofday()),
		methodname=>"volity.$method",
		args=>\@args,
		);
    my $response_callback_method = "rpc_response_volity_" . $method;
    if ($self->can($response_callback_method)) {
	$args{handler} = "volity_" . $method;
    }
    else {
	$self->logger->warn("Calling RPC method volity.$method without a corresponding callback method defined. (It would be called $response_callback_method.)");
    }

    $self->make_rpc_request(\%args);
}

=head1 CALLBACKS

=head2 Ruleset callbacks

Your bot should define a callback method for every Referee-to-player
API defined by the game's ruleset document. 

The name of the callback method will be exacty the same as the name of
the RPC, except with the "game." prefix replaced by "game_rpc_".

The arguments to this method will simply be the arguments of the
orginal RPC.

So, for example, the PRC C<game.player_moved_piece> would trigger the
method C<game_rpc_move_piece( seat_id, piece, location )> in your
subclass, called with the arguments C<($seat_id, $piece, $location)>.

No particular return value, at either the Perl or the RPC level, is
expected, so don't worry about that too much.

=head2 Volity callbacks

=head2 RPC Response callbacks

You I<should> define a response callback method for each distinct
ruleset-defined method that your bot could call on the referee. This
lets you check the referee's response to your bot's RPC and check its
value.

There's I<always> a chance that the referee will return something you
didn't expect, even when both your bot and the referee are working
just fine. Your bot should be able to recognize these sorts of
exceptions and handle them gracefully.

When you make an RPC call to the referee through the
C<send_game_rpc_to_referee()> method (described above), the Bot base
class sets up a callback trigger. When it receives a response from the
referee, it will call an object object named after the method, with
the C<game.> namespace replaced with C<rpc_response_game_>.

For example, the response to the
RPC call C<game.move_piece()> would trigger your callback method
C<rpc_response_game_move_piece>.

The base class will log a warning whenever you call
C<send_game_rpc_to_referee()> but don't have an appropriate response
callback method set up.

=head1 ANDVANCED USE

For basic bot behavior, the methods described above should
suffice. However, if you want to add more sophisticated automation and
fine-grained handlers to your bot, note that C<Volity::Bot> is a
sublcass of C<Volity::Jabber>, so all the methods and techniques
described in L<Volity::Jabber> can be used with your bot subclass.

=head1 SEE ALSO

L<Volity::Game>

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2006 by Jason McIntosh.

=cut

# We want to respond to disco info requests with a declaration that we are
# a bot, in an attached JEP-0128 form with a "volity-role" field.
sub handle_disco_info_request {
    my $self = shift;
    my ($iq) = @_;
    my $query = $iq->get_tag('query');
    $self->logger->debug("I got a disco info request from " . $iq->attr('from'));
    # Build the list of disco items to return.
    my @items;
    my $identity = Volity::Jabber::Disco::Identity->new({category=>'volity',
							 type=>'bot',
						     });
    push (@items, $identity);

    my $role_field = Volity::Jabber::Form::Field->new({var=>"volity-role"});
    $role_field->values("bot");

    # Send them off.
    $self->send_disco_info({
	to=>$iq->attr('from'),
	id=>$iq->attr('id'),
	items=>\@items,
	fields=>[$role_field],
    });
}


1;

