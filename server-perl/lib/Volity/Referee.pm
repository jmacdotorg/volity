package Volity::Referee;

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

=begin TODO

Refactor the RPC dispatcher into something sexier than an ever-growing
if-elsif chain.

=end TODO

=head1 NAME

Volity::Referee - Class for in-MUC game overseers.

=head1 DESCRIPTION

An object of this class represents a Volity referee, the automated
process that sits in a MUC along with the players, and arbitrates the
game for them. See the general Volity documentation for a more
detailed description of how this works.

In the Frivolity system, a Volity::Server object automatically makes a
referee object and sends it to the appropriate MUC when a game
starts. The class of referee it makes depends upon the value of the
server object's C<referee_class> instance variable, but most games can
get away with using this default class, and putting all their game
logic in a Volity::Game subclass.

=head1 USAGE

After being instanced, your C<Volity::Game> subclass object will have
a referee object as its C<referee> instance variable, accessible
through the C<referee()> accessor.

In many cases, you can program your game module without ever directly
referring to the referee. While this class does make many important
methods available (such as C<seats()>), they are transparently
available from your game object as well. The referee simply sits in
the background and takes care of all the Volity protocol-level stuff
for you, letting you concentrate on making your game work.

=head1 METHODS

It's worth noting that this module subclasses from Volity::Jabber, and
therefore enjoys all the methods that it defines, as well as the ones
listed here.

Furthermore, all of these methods are also available with instances of
Volity::Game and its subclasses; they transparently pass them up to
their associated Volity::Referee objects, and pass back the return
values. For your convenience, the method documentation below also
appears in L<Volity::Game>.

=head2 Object accessors

All these are simple accessors which return the named object
attribute. If an argument is passed in, then the attribute's value is
first set to that argument.

It is through these accessors that you perform most game configuration
definition, such as the maximum number of players allowed per game.

This module inherits from Class::Accessor, so all the tips and tricks
detailed in L<Class::Accessor> apply here as well.

=over

=item bookkeeper_jid

The JID of the network's bookkeeper. Initially set by the server,
depending upon its own configuration.

=item muc_jid

The JID of the table's MUC. Set by various magic internal
methods, so you should treat this as read-only; things will probably
not work well if you reset this value yourself.

=item game_class

I<Important!> The Perl class of the actual game (usually a subclass of
Volity::Game).

You I<must> set this on object construction though the C<new> method's
argument hash, as detailed in L<Volity/"Object construction">. Not
doing so will result in an error.

=item game

The referee superclass already knows how and when to create a game
object from the class specified by the C<game_class> instance
variable, and when it does so, it stores that object under C<game>.

You should treat-this as a read-only variable. Generally, it will
always be defined, as a referee creates a new game object as soon as
it can. When a game ends, the object is destroyed a new one
automatically takes its place.

=back

=head2 Other Methods

=over

=item startup_time

Returns the time (in seconds since the epoch) when this palor started.

=item last_activity_time

Returns the time (in seconds since the epoch) when this referee last
handled a game.* RPC.

=item games_completed

Returns the number of games that have been begun and ended with this
referee.

=cut

use base qw(Volity::Jabber);
# See comment below for what all these fields do.
use fields qw(muc_jid game game_class players nicks starting_request_jid starting_request_id bookkeeper_jid server muc_host bot_configs bot_jids active_bots last_rpc_id invitations ready_players is_recorded is_hidden name language internal_timeout seats max_seats kill_switch startup_time last_activity_time games_completed);
# FIELDS:
# muc_jid
#   The JID of this game's MUC.
# game
#   The game object!
# game_class	
#   The Perl class of our game.
# players		
#   Hash of all the Volity::Player objects at the table.
# starting_request_jid 
#   The JID of the person who started the MUC.
# starting_request_id 
#   The ID of the MUC-starting request.
# bot_configs
#   An array reference of retainer-bot config info hashrefs.
# invitations
#   Hash of open invitations.
# ready_players
#   Hash of players who are ready to play.
# is_recorded
#   1 if the next game-ending event will result in a game record.
# is_hidden
#   1 if this ref's game hides itself from the bookkeeper's game finder.
# name
#  The referee's name, as it will appear in service discovery.
# language
#  Two-letter code representing this table's preferred human language.
# internal_timeout
#  Number of seconds for "small" internal timeouts, like waiting for bots.
# seats
#  Array of seat objects for this table. (It's an array since order matters.)
# kill_switch
#  1 if resuming the game at this point would kill it.
# startup_time
#  Unix-time when this ref started.
# last_activity_time
#  Unix-time of the most recent game.* call.
# games_completed
#  The number of games that have come to an end under this referee.

use warnings;  no warnings qw(deprecated);
use strict;

use lib qw(/Users/jmac/Library/Perl/);
use Volity::Player;
use Volity::Seat;
use Volity::GameRecord;
use RPC::XML;

use Carp qw(carp croak);

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
	   Component::Jabber;
	  );
use POE::Filter::XML::Node;

use Scalar::Util qw(weaken);
use Time::HiRes qw(gettimeofday);
use Locale::Language;
use List::Util qw(first);

###################
# Internal config variables 
###################

our $default_seat_class = "Volity::Seat";
our $default_internal_timeout = 180;
our $default_language = "en";

###################
# Object init
###################

sub initialize {
  my $self = shift;

  $self->SUPER::initialize(@_);

  my $game_class = $self->game_class or die
    "No game class specified at construction!";
  eval "require $game_class";
  if ($@) {
    die "Failed to require game class $game_class: $@";
  }

  for my $bot_config ($self->bot_configs) {
      eval "require $$bot_config{class}";
      if ($@) {
	  die "Failed to require bot class $$bot_config{class}: $@";
      }
      
  }      

  # Build the JID of our MUC.
  unless (defined($self->muc_host)) {
    croak ("You must define a muc_host on referee construction.");
  }
  $self->muc_jid($self->resource . '@' . $self->muc_host);

  # Set some query namespace handlers.
  $self->query_handlers->{'volity:iq:botchoice'} = {set=>'choose_bot'};
  $self->query_handlers->{'http://jabber.org/protocol/muc#owner'} = {
      result=>'muc_creation',
      error=>'muc_failure',
  };

  $self->{active_bots} = [];

  $self->logger->debug("By the way, here's my password: " . $self->password);

  $self->last_rpc_id(0);

  $self->invitations({});

  $self->ready_players({});

  $self->is_recorded(1);
  $self->is_hidden(0);
  
  $self->internal_timeout($default_internal_timeout);

  unless (defined($self->name)) {
      # XXX Fix this...
#      $self->name($self->table_creator->nick . "'s game");
      $self->name("Some game.");
  }

  unless (defined($self->language)) {
      $self->language($default_language);
  }

  # Create our first game object.
  $self->create_game;

  # Initialize the seats.
  $self->{seats} = [];
  $self->build_listed_seats;

  $self->startup_time(time);

  $self->games_completed(0);

  return $self;

}

################
# Jabber POE states
################

sub init_finish {
  my $kernel = $_[KERNEL];
  my $heap = $_[HEAP];
  my $session = $_[SESSION];
  my $self = $_[OBJECT];
  $self->logger->debug("***REFEREE*** We have authed!\n");
  $kernel->post($self->alias, 'register', qw(iq presence message));

  # Join the game MUC.
  $self->join_muc({jid=>$self->muc_jid, nick=>'referee'});

}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  my $method = $$rpc_info{method};

  # For security's sake, we explicitly accept only a few method names.
  # In fact, the only one we care about right now is 'start_game'.
  # XXX The above statement is no longer true... and the below if-chain
  # XXX is only going to get longer. Refactoring is needed.
  if ($method =~ /^volity\.(.*)$/) {
      # This appears to be a system-level call (as opposed to a
      # game-level one).
      $method = $1;
      if ($method eq 'start_game') {
          # Still here for backwards compatibility. Read as "ready()".
	  $self->handle_ready_player_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'add_bot') {
	  $self->add_bot($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'remove_bot') {
	  $self->remove_bot($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'invite_player') {
	  $self->invite_player($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'ready') {
	  $self->handle_ready_player_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'unready') {
	  $self->handle_unready_player_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'recorded') {
	  $self->handle_recorded_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'stand') {
	  $self->handle_stand_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'sit') {
	  $self->handle_sit_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'set_language') {
	  $self->handle_language_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'suspend_game') {
	  $self->handle_suspend_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'kill_game') {
	  $self->handle_kill_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'send_state') {
	  $self->handle_state_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});	  
      } else {
	  $self->logger->warn("Got weird RPC request 'volity.$method' from $$rpc_info{from}.");
	  $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 603, "Unknown method: volity.$method");
	  return;
      }
  } elsif ($method =~ /^game\.(.*)$/) {
      # This appears to be a call to the game object.
      # Reaction depends on whether or not the game is afoot.
      my $method = $1;
      my $ok_to_call = 0;
      if ($self->game->is_afoot) {
	  if ($self->game->is_config_variable($method)) {
	      $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 609, "You can't configure the game once it has started.");
	  } else {
	      $ok_to_call = 1;
	  }
      } else {
	  unless ($self->game->is_config_variable($method)) {
	      $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 609, "Can't call $method! The game hasn't started yet.");
	  } else {
	      $ok_to_call = 1;
	  }
      }
      if ($ok_to_call) {
	  $$rpc_info{method} = $method;
	  $self->handle_game_rpc_request($rpc_info);
	  $self->last_activity_time(time);
      }
  } elsif (my ($admin_method) = $$rpc_info{'method'} =~ /^admin\.(.*)$/) {
      # Check that the sender is allowed to make this call.
      my ($basic_sender_jid) = $$rpc_info{from} =~ /^(.*)\//;
      if (grep($_ eq $basic_sender_jid, $self->server->admins)) {
	  my $local_method = "admin_rpc_$admin_method";
	  if ($self->can($local_method)) {
	      $self->$local_method($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
	  } else {
	      $self->send_rpc_fault($$rpc_info{from},
				    $$rpc_info{id},
				    603,
				    "Unknown methodName: '$$rpc_info{method}'",
				    );
	  }
      } else {
	  $self->logger->warn("$$rpc_info{from} attempted to call $$rpc_info{method}. But I don't recognize that JID as an admin, so I'm rejecting it.");
	  $self->send_rpc_fault($$rpc_info{from},
				   $$rpc_info{id},
				   607,
				   "You are not allowed to make admin calls on this parlor.",
				   );
	  return;
      }

  } else {
      $self->logger->warn("Referee at " . $self->jid . " received a $$rpc_info{method} RPC request from $$rpc_info{from}. Eh?");
      $self->send_rpc_fault($$rpc_info{from},
			    $$rpc_info{id},
			    603,
			    "Unknown methodName: '$$rpc_info{method}'",
			    );
      
  }
}

# handle_game_rpc_request: Called by handle_rpc_request upon receipt of an
# RPC request in the 'game' namespace... i.e. an RPC request on the current
# game. Performs some sanity checking, then passes it on.
sub handle_game_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;

  unless ($self->game->is_active) {
    $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 609, "There is no active game.");
    return;
  }

  # We prepend an 'rpc_' to the method's name for security reasons.
  my $method = "rpc_$$rpc_info{method}";

  unless ($self->game->can($method)) {
    $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 603, "This game has no '$$rpc_info{method}' function.");
    $self->logger->warn("Got unknown request game.$method from $$rpc_info{from}.");
    return;
  }

  my $player = $self->look_up_player_with_jid($$rpc_info{from});
  unless ($player) {
    $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 607, "You don't seem to be playing this game!");
    return;
  }
  
  # I've we've come this far, then we can pass the request on to the game.
  my @args;
  if (defined($$rpc_info{args})) {
    if (ref($$rpc_info{args}) eq 'ARRAY') {
      @args = @{$$rpc_info{args}};
    } else {
      @args = $$rpc_info{args};
    }
  }


  # The first arg is always the seat of the player who made this call.
  unshift(@args, $player->seat);

#  warn "Calling $method with these args: @args\n";

  my @response = $self->game->$method(@args);

  if (@response) {
      my $response_flag = $response[0];
      if ($response_flag eq 'fault') {
	  # Oh, there's some in-game problem with the player's request.
	  # (This is here for backwards compatibility.)
	  $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, @response[1..$#response]);
      } elsif ($response_flag =~ /^\d\d\d$/) {
	  # Looks like a fault error code. So, send back a fault.
	  $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, @response);
      } else {
	  # The game has a specific, non-fault response to send back.
	  $self->send_rpc_response($$rpc_info{from}, $$rpc_info{id}, [@response]);
      }
  } else {
    # The game silently approved the request,
    # so send back a minimal positive response.
    $self->send_rpc_response($$rpc_info{from}, $$rpc_info{id}, ["volity.ok"]);
  }

}

# We'll set up an IQ catcher to look for a response to the MUC-creation call.
# Other IQs get popped up to the superclass.
sub jabber_iq {
    my $self = shift;
    my ($node) = @_;
    if ($node->attr('id') eq 'muc-creation') {
	if ($node->attr('type') eq 'result') {
	    # Send the table-creating JID an RPC response letting them know
	    # where the action is.
	    # The game server sends the response, since it was the critter
	    # that fielded the original request.
	    my $starting_jid = $self->starting_request_jid;
	    $self->server->send_rpc_response($starting_jid,
					     $self->starting_request_id,
					     ['volity.ok',
					      $self->muc_jid,
					      ],
					     );
	    # Now wait to see if the player actually comes by.
	    # If not, self-destruct.
	    my $deadline = time + $self->internal_timeout;
	    until ((time >= $deadline) or ($self->look_up_player_with_jid($starting_jid))) {
		$self->kernel->run_one_timeslice;
	    }
	    unless ($self->look_up_player_with_jid($starting_jid)) {
		$self->stop;
	    }
	} elsif ($node->attr('type') eq 'error') {
	    # Something went awry with our attempt to make a MUC. Um...
	    $self->logger->error("Failed to a MUC. Full error: " . $node->to_str);
	    $self->server->send_rpc_fault($self->starting_request_jid,
					  $self->starting_request_id,
					  608,
					  "Unable to create MUC (" . $self->muc_jid . ")",
					  );
	}
    } else {
	return $self->SUPER::jabber_iq(@_);
    }
}



sub jabber_presence {
  my $self = shift;
  $self->logger->debug("Got some presence.\n");
  my ($node) = @_;
  if (my $x = $node->get_tag('x', [xmlns=>"http://jabber.org/protocol/muc#user"])) {
      # Aha, someone has entered the game MUC.
      # Figure out who it's talking about.
      my $new_person_jid;
      # JID is always in an item tag, since the MUC is either non-anonymous
      # or semi-anonymous, so the moderator (that's me) will have access to
      # their full JIDs.
      return unless $x->get_tag('item');
      $new_person_jid = $x->get_tag('item')->attr('jid');
      my $kernel = $self->kernel;
      if (not(defined($new_person_jid)) or $new_person_jid eq $self->jid) {
	  # If there's no JID for this user, that means that the MUC hasn't
	  # been configured yet, and _that_ means that the user is me!
	  # I must have just created it. Well, then.
	  $self->logger->debug("Configuring the new MUC...\n");
	  
	  # Request a MUC configuration form. The muc_creation method will
	  # handle it when it comes in.
	  $self->send_query({
	      query_ns => "http://jabber.org/protocol/muc#owner",
	      type => "get",
	      to => $self->muc_jid,
	      id => 'muc-configuration',
	  });
	  
      } else {
	  # All right, some other yahoo has changed presence.
	  # Note in my list of potential players, depending upon whether
	  # they're coming or going.
	  $self->logger->debug("Looks like a player just joined.\n");
	  my ($nick) = $node->attr('from') =~ /\/(.*)$/;
	  if (defined($node->attr('type')) && ($node->attr('type') eq 'unavailable')) {
	      # Someone's left.
	      my $player = $self->look_up_player_with_jid($new_person_jid);
	      unless ($player) {
		  # Uh... never mind, then.
		  return;
	      }
	      $self->logger->debug("Looks like $nick just left the table.");
	      if ($self->game->is_active) {
		  # They bolted from an active game!
		  $player->is_missing(1);
		  # Check to see if that seat is now abandoned.
		  my $seat = $player->seat;
		  if ($seat && not($seat->is_under_control)) {
		      # The seat is uncontrolled!
		      if ($self->game->is_abandoned) {
			  # Holy crap, _everyone_ has left!
			  # Tell the onlookers (and the bots, I guess).
			  foreach ($self->players) {
			      $_->game_is_abandoned;
			  }
			  # All right, we'll wait for someone to come back.
			  my $deadline = time + $self->internal_timeout;
			  until ((time >= $deadline) or (not($self->game->is_abandoned))) {
			      $self->kernel->run_one_timeslice;
			  }
			  if ($self->game->is_abandoned) {
			      # OK, give up waiting.
			      $self->suspend_game;
			      # But now a new wait begins.
			      # If no replacement humans show up in 90 seconds,
			      # I'm outta here.
			      my $deadline = time + 90;
			      until (
				     (time >= $deadline) or
				     ($self->non_missing_check)
				     ) {
				  $self->kernel->run_one_timeslice;
			      }
			      if (not($self->non_missing_check)) {
				  # No humans came. I am unloved.
				  # I'll just shut down, then.
				  $self->logger->debug("There are no humans left here! I'm killing all the bots and leaving, too.");
				  if ($self->game->is_afoot) {
				      $self->logger->debug("But first, I'm sending a game record.");
				      $self->end_game;
				  }
				  $self->stop;
			      }

			  }
		      }
		      elsif ($self->game->is_disrupted) {
			  # Tell the players about the disruption.
			  foreach ($self->players) {
			      $_->game_is_disrupted;
			  }
		      }
		  }
	      } else {
		  # They left during game config? We'll just forget about them.
		  # If this player was seated, we'll stand them first.
		  # That way everyone gets zapped with the right status updates.
		  if ($player->seat) {
		      $self->stand_player($player);
		  }
		  # Now just forget about this person.
		  $self->remove_player_with_jid($new_person_jid);
		  # Remove this player from the nickanme lookup hash.
		  delete($self->{nicks}{$nick});
		  # If the last non-bot player has left, leaving us alone, disconnect.
		  if ($self->non_bot_check) {
		      $self->logger->debug("But there are still humans here, so I'll keep the table open.");
		  } else {
		      $self->logger->debug("There are no humans left here! I'm killing all the bots and leaving, too.");
		      if ($self->game->is_afoot) {
			  $self->logger->debug("But first, I'm sending a game record.");
			  $self->end_game;
		      }
		      $self->stop;
		  }
	      }
	  } else {
	      my $player;
	      # See if this is a known player, rejoining the table.
	      my $rejoined = 0;
	      if (my @players = $self->look_up_players_with_basic_jid($new_person_jid)) {
		  for my $player (@players) {
		      if ($player->is_missing) {
			  $self->logger->debug("$new_person_jid has rejoined the table.");
			  # Mark him as no longer MIA.
			  $player->is_missing(0);
			  
			  # The player's resource may have changed, so reflect this.
			  delete($self->{players}->{$player->jid});
			  $player->jid($new_person_jid);
			  $self->{players}->{$new_person_jid} = $player;
			  
			  # Tell all the players where this player is, seat-wise.
			  if (my $seat = $player->seat) {
			      for my $other_player (grep ($_ ne $player, $self->players)) {
				  $other_player->player_sat($player, $seat);
			      }
			  }
			  
			  # If this player's return shifted saved the game
			  # from disruption or abandonment, send out the
			  # good news via RPC.
			  if (not($self->game->is_disrupted) &&
			      not($self->game->is_abandoned)) {
			      foreach ($self->players) {
				  $_->game_is_active;
			      }
			  }
			  
			  $rejoined = 1;
			  last;

		      }
		  }
	      }
	
	      if (not($rejoined)) {
		  # OK, this player is new to us.
		  $player = $self->add_player({nick=>$nick, jid=>$new_person_jid});
		  # Also store this player's nickname, for later lookups.
		  $self->logger->debug( "Storing $new_person_jid, under $nick");
		  
	      }
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
	ext => "referee",
	ver => "1.0",
    };
    return $self->SUPER::send_presence($config);
}

# muc_creation: This handler is called when a blank MUC config form arrives
# from the MUC server. 
sub muc_creation {
    my $self = shift;
    my ($iq) = @_;

    my $config_form = Volity::Jabber::Form->new({type=>'submit'});
    
    # Figure out what this form wants from us, since there are so many
    # non-spec MUC implementatons out there.

    # To figure out what flavor of muc-config form this is, we'll
    # just run some regexes over it.
    my $text = $iq->to_str;

    my @field_info;
    if ($text =~ /var=['"]muc#owner.*?['"]/) {
        # Looks like an mu_conference server.
        @field_info = ("muc#owner_roomname", "muc#owner_whois", "anyone");
    }
    elsif ($text =~ /var=['"]anonymous['"]/) {
        # Looks like an old ejabberd server.
        @field_info = ("title", "anonymous", "0");
    }
    elsif ($text =~ /var=['"]muc#roomconfig.*?['"]/) {
	# Glory be, it's an actual spec-compliant form.
        @field_info = ("muc#roomconfig_roomname", "muc#roomconfig_whois", "anyone");
    }
    else {
	$self->logger->error("Received a configuration form from the MUC server but I can't figure out its level of spec compliance.");
	# Report an internal error via a fault, and then go away.
	$self->server->send_rpc_fault($self->starting_request_jid,
				      $self->starting_request_id,
				      608,
				      "Created a MUC but could not understand its configuration form.",
				      );
	$self->stop;
	return;			# Waste of breath, but just making it clear.
    }

    $config_form->fields(
			 Volity::Jabber::Form::Field->new({var=>$field_info[0]})->values($self->game_class->name),
			 Volity::Jabber::Form::Field->new({var=>$field_info[1]})->values($field_info[2]),
			 );
    
    # We'll listen for this query's return before telling the user
    # about the table.
    $self->send_query({
	to=>$self->muc_jid,
	from=>$self->jid,
	id=>'muc-creation',
	type=>'set',
	query_ns=>"http://jabber.org/protocol/muc#owner",
	content=>[$config_form],
    });
}

###################
# MUC user tracking
###################

# These methods refer to MUC users besides myself as 'players'.

sub add_player {
  my $self = shift;
  my ($args) = @_;

  my $player = Volity::Player->new({jid=>$$args{jid}, nick=>$$args{nick}, referee=>$self});
  $self->{players}{$$args{jid}} = $player;
  $self->{nicks}{$$args{nick}} = $$args{jid};

  # Set the new player's bot-bit if it has the JID of a known bot.
  if (exists($self->{bot_jids}{$$args{jid}})) {
      $player->is_bot(1);
  }

  return $player;
}

sub remove_player_with_jid {
  my $self = shift;
  my ($jid) = @_;
  delete($self->{players}{$jid});
}

sub players {
  my $self = shift;
  return values (%{$self->{players}});
}

# add_seat: Given the ID for a new seat, add it to the table's list of seats,
# and return the resulting seat object.
sub add_seat {
    my $self = shift;
    my ($seat_id) = @_;
    unless ($seat_id) {
	$self->logger->error("ERROR: Attempt to add a seat with no ID!!");
	return;
    }

    # Figure out which class to call a seat-constructor on.
    # Games can override Volity::Seat, the default class.
    my $seat_class = $self->game_class->seat_class || $default_seat_class;
    my $seat = $seat_class->new({id=>$seat_id});

    push (@{$self->{seats}}, $seat);
    return $seat;
}

# build_listed_seats: Create all seats referenced by the game's seat list,
# Seats that already happen to exist (perhaps from past games at this table)
# are let alone.
sub build_listed_seats {
    my $self = shift;
    for my $seat_id (@{$self->game_class->seat_ids}) {
	$self->add_seat($seat_id) unless $self->look_up_seat_with_id($seat_id);
    }
}

# clear_empty_seats: Usually called as a game starts. Blow away seats with
# no players in them.
sub clear_empty_seats {
    my $self = shift;
    my @good_seats = grep($_->players, $self->seats);
    $self->{seats} = \@good_seats;
}


# look_up_player_with_jid:
# Takes a JID, and returns the corresponding player object, or undef.
# For flexibility, if the JID appears to be a MUC-only JID using a nickname,
# it uses the internal nicknames table for lookups instead.
sub look_up_player_with_jid {
  my $self = shift;
  my ($jid) = @_;
# Commented out the logger lines here because this method is often in a
# while() loop, and therefore spams the log. -jmac
#  $self->logger->debug("Fetching player object for JID $jid.");
  my $muc_jid = $self->muc_jid;
  if (my ($nickname) = $jid =~ m|^$muc_jid/(.*)$|) {
#      $self->logger->debug("Oh, it was a table-based JID.");
      $jid = $self->look_up_jid_with_nickname($nickname);
#      $self->logger->debug("Right, then; doing a lookup on $jid instead.");
  }
  return $self->{players}{$jid};
}

# look_up_players_with_basic_jid:
# As above, except takes a basic JID as an argument, and returns a list
# of all players who have that basic JID, regardless of their full JID's
# resource part.
# For convenience, if you pass it a JID with a resource string attached
# anyway, it ignores it.
sub look_up_players_with_basic_jid {
    my $self = shift;
    my ($jid) = @_;
    $jid =~ s|\/.*$||;
    return grep($_->basic_jid eq $jid, $self->players);
}

# look_up_jid_with_nickname:
# Takes a nickname, and returns the full JID of the player using it.
# Returns undef if there's no such nick.
sub look_up_jid_with_nickname {
  my $self = shift;
  my ($nick) = @_;
  if ($nick =~ m|/(.*)$|) {
    # Ah, it's an entire MUC-style jid. Parse out the nickname part.
    $nick = $1;
  }
  return $self->{nicks}{$nick};
}

# look_up_player_with_nickname: Combines the previous two methods in a
# rather predictable fashion. Convenience method.
sub look_up_player_with_nickname {
    my $self = shift;
    my ($nick) = @_;
    my $jid = $self->look_up_jid_with_nickname($nick);
    if ($jid) {
	return $self->look_up_player_with_jid($jid);
    } else {
	return;
    }
}

# look_up_seat_with_id: Given a seat ID, returns a seat object, or undef if
# there is no such seat.
sub look_up_seat_with_id {
    my $self = shift;
    my ($seat_id) = @_;
    my ($seat) = first {$_->id eq $seat_id} $self->seats;
    return $seat;
}

=item groupchat ( $message )

Sends the given message string as a groupchat to the referee's table.

=back

=cut

# groupchat:
# Convenience method for sending a message to the game's MUC.
sub groupchat {
  my $self = shift;
  my ($message) = @_;
  $self->send_message({
		       to=>$self->muc_jid,
		       type=>"groupchat",
		       body=>$message,
		     });
}

# non_bot_check: Returns 1 if the MUC contains at least one player who is
# not a bot. Returns 0 otherwise.
sub non_bot_check {
    my $self = shift;
    for my $nickname (keys(%{$self->{nicks}})) {
	my $player = $self->look_up_player_with_nickname($nickname);
	unless ($player->is_bot) {
	    # This is a human.
	    return 1;
	}
    }
    # No humans found, if we came this far.
    return 0;
}

# non_missing_check: Returns 1 if the MUC contains at least one player who is
# not a bot _and_ not missing. Observers count. Returns 0 otherwise.
sub non_missing_check {
    my $self = shift;
    for my $nickname (keys(%{$self->{nicks}})) {
	my $player = $self->look_up_player_with_nickname($nickname);
	unless ($player->is_bot || $player->is_missing) {
	    # This is a non-missing human.
	    return 1;
	}
    }
    # No non-missing humans found, if we came this far.
    return 0;
}

=head1 JABBER EVENT HANDLING

Volity::Referee inherits from Volity::Jabber, and therefore uses all
the same callback methods defined in L<Volity::Jabber/"CALLBACK
METHODS">.

Don't be shy about overriding any handlers you wish, so long as you
call the parent class's handler at some point so that any special
Volity::Referee magic will be taken care of. This includes passing
chat messages along to the contained game object or class (see L<"The
Game Object">).

=cut

sub handle_groupchat_message {
  my $self = shift;
  $self->game->handle_groupchat_message(@_);
}  

sub handle_chat_message {
  my $self = shift;
  $self->game->handle_chat_message(@_);
}  

sub handle_normal_message {
  my $self = shift;
  $self->game->handle_normal_message(@_);
}  

# table_creator: Return the object of the player who created this table.
sub table_creator {
    my $self = shift;
    $self->logger->debug("Looking up starting player, based on the JID " . $self->starting_request_jid);
    return $self->look_up_player_with_jid($self->starting_request_jid);
}

####################
# RPC methods (receiving)
####################

sub handle_recorded_request {
    my $self = shift;
    my ($from_jid, $id, $recorded_boolean) = @_;
    if ($self->game->is_afoot) {
	$self->send_rpc_fault($from_jid, $id, 609, "You can't configure the game once it has started.");
    } else {
	unless (($recorded_boolean eq '0') or ($recorded_boolean eq '1')) {
	    $self->send_rpc_fault($from_jid, $id, 606, "The argument to recorded() must be 0 or 1.");
	    return;
	}
	$self->send_rpc_response($from_jid, $id, ["volity.ok"]);
	if ($recorded_boolean ne $self->is_recorded) {
	    # It's a change, so inform everyone.
	    $self->is_recorded($recorded_boolean);
	    my $nickname = $self->look_up_player_with_jid($from_jid)->nick;
	    foreach ($self->players) {
		$self->make_rpc_request({to=>$_->jid,
					 id=>'recorded',
					 methodname=>'volity.recorded',
					 args=>[$nickname],
					});
	    }
	}
    }
}


sub add_bot {
  my $self = shift;
  my ($from_jid, $id, @args) = @_;

  # First, check to see that we have bots, and return a fault if we don't.
  unless ($self->bot_configs) {
    $self->send_rpc_fault($from_jid, $id, 3, "Sorry, this game server doesn't host any bots.");
    return;
  }
  
  # If we offer only one flavor of bot, then Bob's your uncle.
  my @bot_configs = $self->bot_configs;
  if (@bot_configs == 1) {
    if (my $bot = $self->create_bot($self->{bot_configs}->[0])) {
      $self->send_rpc_response($from_jid, $id, ["volity.ok"]);
#      $bot->kernel->run;
    } else {
      $self->send_rpc_fault($from_jid, $id, 4, "I couldn't create a bot for some reason.");
    }
    return;
  }

  # We seem to have more than one bot. Send back a form.
  my @form_options;
  my $default_name_counter;
  for my $bot_config ($self->bot_configs) {
    my $label = $bot_config->{class}->name || 'Bot' . ++$default_name_counter;
    if (defined($bot_config->{class}->description)) {
      $label .= ": " . $bot_config->{class}->description;
    }
    push (@form_options, [$bot_config, $label]);
  }
  $self->send_form({
		    fields=>{bot=>{
				   type=>'list-single',
				   options=>\@form_options,
				   label=>"Choose a bot to add...",
				  }
			    },
		    to=>$from_jid,
		    id=>'bot_form',
		    type=>'form',
		   });
  # Form sent; we're done here.
  $self->send_rpc_response($from_jid, $id, ["volity.ok"]);
}

sub remove_bot {
  my $self = shift;
  my ($from_jid, $id, @args) = @_;
  my ($bot_jid) = @args;
  
  # Make sure that the given JID is, in fact, that of a seated bot.
  unless ($bot_jid) {
      $self->send_rpc_fault($from_jid, $id, 604, "No bot JID specified.");
      return;
  }
  my $bot = $self->look_up_player_with_jid($bot_jid);
  unless ($bot) {
      $self->send_rpc_fault($from_jid, $id, ["volity.jid_not_present"]);
      return;
  }
  unless ($bot->is_bot) {
      $self->send_rpc_fault($from_jid, $id, ["volity.not_bot"]);
      return;
  }
  if ($bot->seat) {
      $self->send_rpc_fault($from_jid, $id, ["volity.bot_seated"]);
  }

  # Having survived this obstacle course, we have determined that $bot
  # is, in fact a bot. Whom we will now eject from the table.
  my ($bot_object) = grep($bot->jid eq $_->jid, $self->active_bots);
  $bot_object->stop;
  $self->active_bots(grep($bot->jid ne $_->jid, $self->active_bots));
}
  


# choose_bot: Called on receipt of a form with bot choice.
# XXX CAUTION XXXX
# This won't work, since Volity::Jabber::Form is currently commented out.
sub choose_bot {
  my $self = shift;
  my ($iq) = @_;
  my $form = Volity::Jabber::Form->new_from_element($iq->get_tag('query')->get_tag('x'));
  my $chosen_bot_class = $form->field_with_var('bot');
  unless (defined($chosen_bot_class)) {
    carp("Received a bot-choosing form with no choice?");
    # XXX Send an error message here?
    return;
  }

  # Make sure that the chosen class is one that we actually offer...
  unless (grep($_->{class} eq $chosen_bot_class, $self->bot_configs)) {
    carp("Got a request for bot class $chosen_bot_class, but I don't offer that?");
    # XXX Send an error message here?
    return;
  }

  if (my $bot = $self->create_bot(($chosen_bot_class))) {
    # It's all good.
    $bot->kernel->run;
    return;
  } else {
    # Oh no, the bot didn't get added.
    carp ("Failed to add a bot of class $chosen_bot_class.");
    # XXX Do something errory here.
  }
}

sub create_bot {
  my $self = shift;
  my ($bot_config) = @_;
  my $bot_class = $bot_config->{class};
  # Generate a resource for this bot to use.
  my $resource = $bot_class->name . gettimeofday();
  my $bot = $bot_class->new(
			    {
			     password=>$bot_config->{password},
			     resource=>$resource,
			     alias=>$resource,
			     muc_jid=>$self->muc_jid,
			     user=>$bot_config->{username},
			     host=>$bot_config->{host},
			    }
			 );
  $self->logger->info("New bot (" . $bot->jid . ") created by referee (" . $self->jid . ").");
  $self->{bot_jids}->{$bot->jid} = 1;

  push (@{$self->{active_bots}}, $bot);

  return $bot;
}

# end_game: Not really an RPC call, but putting it here for now for
# symmetry's sake.
# It's called by a game object.
sub end_game {
  my $self = shift;

  # Tell the players (their clients, really) to wrap it up.
  foreach ($self->players) {
      $_->end_game;
  }

  # Time to register this game with the bookkeeper!
  # Create and initialize a new game record object.
  $self->logger->debug("Preparing game record.");
  my $record = Volity::GameRecord->new({
					parlor=>$self->basic_jid,
				      });
  $record->game_uri($self->game_class->uri);
  $record->end_time(scalar(localtime));

  my %recorded_seats = ();
  
  my @slots = $self->game->winners->slots;
  if (@slots and defined($slots[0])) {
      my @winners_list;
      for my $slot (@slots) {
	  my @seats = @$slot;
	  for my $seat (@seats) {
	      $recorded_seats{$seat->id} = [$seat->registered_player_jids];
	  }
	  push (@winners_list, [map($_->id, @seats)]);
      }
      $record->winners(\@winners_list);
  }

  $record->seats(\%recorded_seats);


  # Give it the ol' John Hancock, if possible.
  if (defined($Volity::GameRecord::gpg_bin) and defined($Volity::GameRecord::gpg_secretkey) and defined($Volity::GameRecord::gpg_passphrase)) {
      $record->sign;
  }

  # Mark whether or not the game actually finished.
  $record->finished($self->game->is_finished);
  
  # Send the record to the bookkeeper!
  $self->send_record_to_bookkeeper($record);

  # Reset seat histories.
  foreach ($self->seats) {
      $_->clear_registry;
  }

  # Create a fresh new game.
  delete($self->{game});
  $self->create_game;

  $self->games_completed($self->games_completed + 1);
}

# create_game: internal method that simply creates a new game object
# and stores it as an instance variable.
sub create_game {
    my $self = shift;
    my $game_class = $self->game_class;
    my $game = $self->game($game_class->new({referee=>$self}));
    $self->logger->debug("Created a game!!\n");
    unless ($game->has_initialized) {
	$self->expire("Created a new game object, but it failed to initialize. Perhaps the $game_class class overrode the initialize() method but neglected to call SUPER::initialize ?");
    }
    return $game;
}

# suspend_game: Suspend the game, and tell everyone about it.
# Optional argument is a suspending player object.
sub suspend_game {
    my $self = shift;
    my ($player) = @_;

    # Tell the game it's suspended.
    $self->game->is_suspended(1);

    # The the players what happened.
    foreach ($self->players) { 
	$_->last_active_seat($_->seat);
	$_->suspend_game($player);
    }

    # Make sure they're unready.
    $self->unready_all_players;

}

sub resume_game {
    my $self = shift;
    $self->game->is_suspended(0);

    $self->logger->debug("Resuming the game.");

    if ($self->kill_switch) {
	$self->logger->debug("But the kill-switch is set, so instead I'm just ending it.");
	$self->throw_game;
	return;
    }

    # Tell seats to remember their occupants (which may have changed).
    # Note that former occupants will stay registered. This is correct.
    map ($_->register_occupants, $self->seats);

    # Flush the ready-players list.
    $self->quietly_unready_all_players;

    foreach ($self->players) { $_->resume_game }
    
    # Call the game object's resume-game callback.
    $self->game->game_has_resumed;

}

# throw_game: Just make a fakey "winners" list out of the current players,
# and and the game. The game knows it's not finished and the resulting
# record will say as much.
sub throw_game {
    my $self = shift;
    $self->game->winners([[$self->seats]]);
    $self->end_game;
}
    
sub send_record_to_bookkeeper {
  my $self = shift;
  my ($record) = @_;
  unless (ref($record) and $record->isa('Volity::GameRecord')) {
    croak("You must call send_record_to_bookkeeper with a game record object.");
  }
  my $bkp_jid = $self->bookkeeper_jid;
  $self->send_message({
		       to=>$bkp_jid,
		       type=>'chat',
		       body=>'Hello, sailor!',
		     });
  my $hash = $record->render_as_hashref;
  $self->make_rpc_request({to=>$bkp_jid,
			   id=>'record_set',
			   methodname=>'volity.record_game',
			   args=>$hash
			 });
}

sub invite_player {
  my $self = shift;
  my ($from_jid, $rpc_id, $to_jid) = @_;

  # Figure out whether to format this invitation as an RPC, or as a message.
  # Our decision is based on whether or not there's a resource string
  # in the recipient's JID.
  if ($to_jid =~ /\//) {
      # This appears to be a full JID, with a resource string.
      # So we'll use an RPC-based invitation.

      my $invitation_id = $self->last_rpc_id;
      $invitation_id++; $self->last_rpc_id($invitation_id);
      $self->invitations->{$invitation_id} = [$rpc_id, $from_jid];
      $self->logger->debug("$from_jid will invite $to_jid. New ID is $invitation_id. Old id was $rpc_id.");

      $self->make_rpc_request({to=>$to_jid,
			       id=>$invitation_id,
			       methodname=>'volity.receive_invitation',
			       args=>[{
				   player=>$from_jid,
				   table=>$self->muc_jid,
				   referee=>$self->jid,
				   server=>$self->server->jid,
				   parlor=>$self->server->jid,
				   ruleset=>$self->game_class->uri,
				   name=>$self->game_class->name,
			       }],
			       handler=>'invitation',
			   });
  }
  else {
      # OK, so the recipient JID had no resource string.
      # We'll fall back to trying a message-based invitation.
      # First send back an RPC okey-dokey.
      $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
      $self->send_message({
	  to => $to_jid,
	  subject => "invitation",
	  body => "$from_jid has invited you to join a game of " . $self->game_class->name . " at " . $self->muc_jid,
	  invitation => {
	      player=>$from_jid,
	      table=>$self->muc_jid,
	      referee=>$self->jid,
	      server=>$self->server->jid,
	      parlor=>$self->server->jid,
	      ruleset=>$self->game_class->uri,
	      name=>$self->game_class->name,
	  },
      });
  }
}

sub handle_language_request {
    my $self = shift;
    my ($from_jid, $id, $language_code) = @_;
    unless ($language_code) {
	$self->send_rpc_fault($from_jid, $id, 604, "Missing language code");
	return;
    }
    if ($self->game->is_active) {
	$self->send_rpc_fault($from_jid, $id, 609, "The game is active.");
	return;
    }
    if (Locale::Language::code2language($language_code)) {
	$self->send_rpc_response($from_jid, $id, ["volity.ok"]);
	if ($language_code ne $self->language) {
	    $self->language($language_code);
	    map ($_->table_language($from_jid), $self->players);
	}
    } else {
	$self->send_rpc_fault($from_jid, $id, 606, "Unknown language code '$language_code'");
    }
}


sub handle_suspend_request {
    my $self = shift;
    my ($from_jid, $id) = @_;

    # To suspend a game, a player must be seated at an active game.
    my $player;
    unless ($player = $self->look_up_player_with_jid($from_jid)) {
	$self->send_rpc_fault($from_jid, $id, 607, "You don't seem to be at my table (Table JID: " . $self->muc_jid . ")");
	return;
    }	
    unless ($self->game->is_active) {
	$self->send_rpc_fault($from_jid, $id, 609, "The game is not active.");
	return;
    }
 
    $self->send_rpc_response($from_jid, $id, ["volity.ok"]);
    $self->suspend_game($player);
}

sub handle_kill_request {
    my $self = shift;
    my ($from_jid, $id, $kill_boolean) = @_;

    # To propose killing the game, a player must be seated in a suspended game.
    my $player;
    unless ($player = $self->look_up_player_with_jid($from_jid)) {
	$self->send_rpc_fault($from_jid, $id, 607, "You don't seem to be at my table (Table JID: " . $self->muc_jid . ")");
	return;
    }
    unless ($player->seat) {
	$self->send_rpc_fault($from_jid, $id, 607, "You are not seated.");
    }
    unless ($self->game->is_suspended) {
	$self->send_rpc_fault($from_jid, $id, 609, "The game is not suspended..");
	return;
    }

    # OK, it's a legit call. Make this our new kill value.
    $self->kill_switch($kill_boolean);

    # Tell everyone about this development.
    foreach ($self->players) { $_->kill_game($player) }

    # This is a config change, so...
    $self->unready_all_players;

}

#######################
# Player readiness
#######################

# ready_player: Set the given player as ready, and announce to the table.
sub ready_player {
    my $self = shift;
    my ($player) = @_;
    $self->ready_players->{$player} = $player;
    # Tell all the players about this.
    for my $other_player ($self->players) {
	$other_player->player_ready($player);
    }
    if ($self->are_all_players_ready) {
	$self->logger->debug("Everyone is ready to play!");
	if ($self->game->is_suspended) {
	    $self->resume_game;
	} else {
	    $self->start_game;
	}
    } else {
	$self->logger->debug("But there are still unready players.");
    }
}

sub handle_ready_player_request {
  my $self = shift;
  my ($from_jid, $rpc_id, @args) = @_;
  $self->logger->debug("$from_jid has announced readiness.");
  my $player = $self->look_up_player_with_jid($from_jid);
  if ($player) {
      if ($player->seat) {
	  # Make sure that the game is ready for readiness.
	  # First, check that the required seats are occupied.
	  my @empty_required_seats;
	  for my $required_seat_id (@{$self->game->required_seat_ids}) {
	      my $seat = $self->look_up_seat_with_id($required_seat_id);
	      unless ($seat) {
		  $self->logger->warn("Bad news. Required seat with ID $required_seat_id doesn't seem to exist (in handle_ready_player_request).");
		  return;
	      }
	      my @p = $seat->players;
	      if ($seat->players == 0) {
		  push (@empty_required_seats, $seat);
	      }
	  }
	  if (@empty_required_seats) {
	      # Nope, too many empty seats to start.
	      $self->send_rpc_response($from_jid, $rpc_id, ["volity.empty_seats"]);
	  } elsif ($player->is_bot && not(grep($_->seat && not($_->is_bot), $self->players))) {
	      # Nope, a bot can't ready unless a human is seated.
	      $self->send_rpc_fault($from_jid, $rpc_id, 609, "A bot can't declare readiness unless there are humans seated.");
	  } elsif ($self->game->has_acceptable_config) {
	      # All config is A-OK! Let's ready this player.
	      $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
	      $self->ready_player($player);
	  } else {
	      # No, the config is wedged.
	      $self->send_rpc_response($from_jid, $rpc_id, ["volity.bad_config"]);
	  }
      } else {
	  $self->logger->debug("But that player isn't sitting down!");
	  $self->send_rpc_fault ($from_jid, $rpc_id, 609, "You wish to state your readiness to play, but you are not seated at the table.");
      }
    } else {
  	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault ($from_jid, $rpc_id, 607, "You wish to state your readiness to play, but you don't seem to be actually playing.");
	return;
    }
}

# handle_stand_request: The caller wants the named player to vacate its seat.
sub handle_stand_request {
    my $self = shift;
    my ($from_jid, $rpc_id, @args) = @_;
    my ($standing_jid) = @args;
    unless ($standing_jid) {
	$self->send_rpc_fault($from_jid, $rpc_id, 604, "Missing JID parameter.");
	return;
    }
    if ($self->game->is_active) {
	$self->send_rpc_fault ($from_jid, $rpc_id, 609, "The game is active.");
	return;
    }
    $self->logger->debug("$from_jid wants $standing_jid to stand up.");
    my $standee = $self->look_up_player_with_jid($standing_jid);
    my $requester = $self->look_up_player_with_jid($from_jid);
    if ($requester) {
	if ($standee && $standee->seat) {
	    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
	    $self->stand_player($standee);
	} else {
	    $self->logger->debug("But that player isn't sitting down!");
	    $self->send_rpc_fault($from_jid, $rpc_id, 606, "Player $standing_jid doesn't appear to be seated at this table.");
	}
    } else {
  	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault ($from_jid, $rpc_id, 607, "You don't seem to be at this table.");
	return;
    }
}

# handle_sit_request: The caller wants the named player to sit. There is an
# optional second arg for the seat ID. If it's missing, we'll try to sit
# the named player in a new seat by itself.
# For the time being, we will always allow this, even when the two JIDs
# involved don't match (which is certainly acceptable input).
sub handle_sit_request {
    my $self = shift;
    my ($from_jid, $rpc_id, @args) = @_;
    my ($sitting_jid, $seat_id) = @args;
    unless ($sitting_jid) {
	$self->send_rpc_fault($from_jid, $rpc_id, 604, "Missing JID parameter.");
	return;
    }
    if ($self->game->is_active) {
	$self->send_rpc_fault ($from_jid, $rpc_id, 609, "The game is active.");
	return;
    }
    $self->logger->debug("$from_jid wants $sitting_jid to sit.");

    my $requester = $self->look_up_player_with_jid($from_jid);
    my $sitter = $self->look_up_player_with_jid($sitting_jid);
    # Bounce back any identity errors.
    unless ($requester) {
  	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault ($from_jid, $rpc_id, 607, "You don't seem to be at this table.");
	return;
    }
    unless ($sitter) {
	$self->logger->debug("But that player isn't at this table!");
	$self->send_rpc_fault($from_jid, $rpc_id, 606, "Player $sitting_jid doesn't appear to be present at this table.");
	return;
    }

    unless ($seat_id) {
	# No seat id specified. 

	# Return a fault if this player is already seated.
	if ($sitter->seat) {
	    $self->send_rpc_fault($from_jid, $rpc_id, 609, "Player is already seated.");
	    return;
	}

	# Try to get the ID of an available, empty seat.
	# First we look for empty seats among the required ones.
	# Then we look among all known seats.
	# If there aren't any, _AND_ we haven't hit our player maximum yet,
	# call a class method on the game to get a new seat name.
	my $empty_seat_id;
	my %tried_seats;
	for my $seat_id (@{$self->game_class->required_seat_ids},
			 @{$self->game_class->seat_ids}) {
	    my $seat = $self->look_up_seat_with_id($seat_id);
	    next if exists($tried_seats{$seat_id});
	    my @players = $seat->players;
	    if (@players) {
		$tried_seats{$seat_id} = 1;
	    } 
	    else {
		$empty_seat_id = $seat_id;
		last;
	    }
	}

	if ($empty_seat_id) {
	    $seat_id = $empty_seat_id;
	}
	unless ($seat_id) {
	    # We can't sit this player down. Signify this by returning
	    # an empty string.
	    $self->logger->debug("But there is no room to sit!");
	    $self->send_rpc_response($from_jid, $rpc_id, ['volity.no_seats']);
	    return;
	}
    }
    $self->logger->debug("$sitting_jid will sit in seat $seat_id.");
    my $seat = $self->look_up_seat_with_id($seat_id);
    unless ($seat) {
	$self->logger->warn("Refusing $from_jid\'s request to sit $sitting_jid in seat with id '$seat_id' since that seat doesn't exist.");
	$self->send_rpc_fault($from_jid, $rpc_id, 606, "Unknown seat id '$seat_id'.");
	return;
    } else {
	$self->send_rpc_response($from_jid, $rpc_id, ["volity.ok", $seat_id]);
	$self->sit_player($sitter, $seat);
	return;
    }
}

# ready_player_list: Return a list of ready player objects.
sub ready_player_list {
    my $self = shift;
    return (values(%{$self->ready_players}));
}

# unready_player: Set the given player as unready, and announce to the table.
sub unready_player {
    my $self = shift;
    my ($player) = @_;
    delete($self->ready_players->{$player});

    # Tell all the players about this.
    for my $other_player ($self->players) {
	$other_player->player_unready($player);
    }
}


sub handle_unready_player_request {
    my $self = shift;
    my ($from_jid, $rpc_id, @args) = @_;
    $self->logger->debug("$from_jid has announced UNreadiness.");
    my $player = $self->look_up_player_with_jid($from_jid);
    if ($player) {
	if ($self->game->is_active) {
	    $self->logger->debug("But they were slow on the trigger, because the game has already started!");
	    $self->send_rpc_fault($from_jid, $rpc_id, 609, "Too late, the game is already underway!");
	    return;
	} else {
	    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
	    $self->unready_player($player);
	}
    } else {
	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault ($from_jid, $rpc_id, 607, "You wish to state your unreadiness to play, but you don't seem to be actually playing.");
	return;
    }
}

# Are all players ready: returns truth if all the seated players are 
# ready to go, falsehood otherwise.
sub are_all_players_ready {
    my $self = shift;
    if (grep($_->seat, $self->players) == $self->ready_player_list) {
	return 1;
    } else {
	return 0;
    }
}

# quietly_unready_all_players: Reset the readiness status of all players.
# Useful after a game starts.
sub quietly_unready_all_players {
    my $self = shift;
    $self->ready_players({});
}

# unready_all_players: As above, except it tells all the players about it too.
# Useful after the config has changed.
sub unready_all_players {
    my $self = shift;
    # Quickly wipe out the ready-player list first, just to help dodge
    # race conditions.
    my @ready_players = $self->ready_player_list;
    $self->quietly_unready_all_players;
    # Now announce the affected players.
    foreach (@ready_players) { $self->unready_player($_) }
}

# sit_player: Put the given player in the given seat, and announce to the table.
sub sit_player {
    my $self = shift;
    my ($player, $seat) = @_;
    $seat->add_player($player);
    if (my $seat = $player->seat) {
	# First, silently pluck this player from its current seat.
	my $found = $seat->remove_player($player);
	unless ($found) {
	    $self->logger->error(sprintf ("Player %s not in expected seat %s.", $player->jid, $seat->id));
	    return;
	}
    }
    $player->seat($seat);
    # Tell the table what just happened.
    foreach ($self->players) { $_->player_sat($player, $seat) }
    # Sitting down is a config change, so everyone gets unreadied.
    $self->unready_all_players;
}

# stand_player: Set the given player as standing, and announce to the table.
sub stand_player {
    my $self = shift;
    my ($player) = @_;
    # Only do something if the player was actually seated.
    if (my $seat = $player->seat) {
	$player->seat(undef);
	$seat->remove_player($player);
	# Tell all the players about this.
	for my $other_player ($self->players) {
	    $other_player->player_stood($player);
	}
	# Standing up means this player doesn't want to play _at all_,
	# and that counts as a configuation change. Everyone loses
	# readiness.
	$self->unready_all_players;
    }
}

sub rpc_response_invitation {
  my $self = shift;
  my ($response) = @_;
  $self->logger->debug("Received an invitation repsonse, from $$response{from}");
  my $invitation_info = $self->invitations->{$$response{id}};
  if ($invitation_info) {
    my ($original_rpc_id, $inviter) = @$invitation_info;
    $self->send_rpc_response($inviter, $original_rpc_id, ["volity.ok", $$response{response}]);
    delete($self->invitations->{$$response{id}});
  } else {
    $self->logger->warn("Got unexpected invitation response from $$response{from}, id of $$response{id}.");
  }
}

sub handle_rpc_fault {
  my $self = shift;
  my ($fault_info) = @_;
  if (my $invitation_info = $self->invitations->{$$fault_info{id}}) {
    my ($original_rpc_id, $inviter) = @$invitation_info;
    $self->send_rpc_fault($inviter, $original_rpc_id, $$fault_info{code}, $$fault_info{string});
    delete($self->invitations->{$$fault_info{id}});
  } else {
    $self->logger->warn("Got unexpected RPC fault from $$fault_info{from}, id $$fault_info{id}: $$fault_info{code} - $$fault_info{string}");
  }
}

sub stop {
  my $self = shift;
  # Kick out all the bots.
  foreach (grep(defined($_), $self->active_bots)) {
      $_->stop;
  }
  $self->{active_bots} = [];
  $self->server->remove_referee($self);
  $self->kernel->post($self->alias, 'shutdown_socket', 0);
}

# current_state: return a short string (suitable for the 'state' field of disco
# info) about the state of this referee's game.
sub current_state {
    my $self = shift;
    unless ($self->game->is_afoot) {
	return 'setup';
    } elsif ($self->game->is_suspended) {
	return 'suspended';
    } elsif ($self->game->is_disrupted) {
	return 'disrupted';
    } elsif ($self->game->is_abandoned) {
	return 'abandoned';
    } else {
	return 'active';
    }
}

#sub DESTROY {
#  my $self = shift;
#  $self->server(undef);
#}

# start_game: Internal method called when all the players have confirmed
# their readiness to begin.
sub start_game {
  my $self = shift;

  $self->logger->debug("I am starting a game.");

  # No error message? Great... let's play!

  # Tell seats to remember their occupants.
  map ($_->register_occupants, $self->seats);

  # Tell the game to start itself.
  $self->game->is_afoot(1);
  
  # Tell the players' clients to get ready for some fun.
  for my $player ($self->players) {
      $player->start_game;
  }

  # Flush the ready-players list.
  $self->quietly_unready_all_players;

  # Tell the game object to do whatever it wants as its first action.
  $self->game->start;
}

sub handle_state_request {
    my $self = shift;
    my ($from_jid, $rpc_id, @args) = @_;
    $self->logger->debug("$from_jid is asking for a state update.");
    my $player = $self->look_up_player_with_jid($from_jid);
    if ($player) {
	$self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
	$player->update;
    } else {
	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault ($from_jid, $rpc_id, 607, "I don't recognize you as a player.");
    }
}

##############################
# Service Discovery handlers
##############################

# handle_disco_info_request: Tell 'em a little about the goings-on of
# this particular game.
sub handle_disco_info_request {
    my $self = shift;
    my ($iq) = @_;
    my $query = $iq->get_tag('query');
    $self->logger->debug("I got a disco info request from " . $iq->attr('from'));
    # Build the list of disco items to return.
    my @items;
    my $identity = Volity::Jabber::Disco::Identity->new({category=>'volity',
							 type=>'referee',
							 name=>$self->name,
						     });
    push (@items, $identity);
    # Now build up our list of JEP-0128 data form fields.
    my @fields;
    foreach ('max-players', 'parlor', 'table', 'state', 'players', 'language', 'name', 'volity-role') {
	push (@fields, Volity::Jabber::Form::Field->new({var=>$_}));
    }
    my $game_class = $self->game_class;
    $fields[0]->values($game_class->max_allowed_seats);
    $fields[1]->values($self->server->jid);
    $fields[2]->values($self->muc_jid);
    $fields[3]->values($self->current_state);
#    $fields[4]->values(scalar($self->players));
    $fields[4]->values(scalar(grep($_->registered_player_jids, $self->seats)));
    $fields[5]->values($self->language);
    $fields[6]->values($self->name);
    $fields[7]->values('referee');
    $self->send_disco_info({
	to=>$iq->attr('from'),
	id=>$iq->attr('id'),
	items=>\@items,
	fields=>\@fields,
    });
}

##########################
# Admin RPC stuff
##########################

# These are all dispatched to from the handle_rpc_request method.

# XXX TODO: seats, seat

sub admin_rpc_status {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    my %status = (
		  startup_time=>scalar(localtime($self->startup_time)),
		  last_activity_at=>localtime($self->last_activity_time),
		  players=>scalar($self->players),
		  bots=>scalar(@{$self->{active_bots}}),
		  agentstate=>"online",
		  state=>$self->current_state,
		  games_completed=>$self->games_completed,
		  );
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok", \%status]);
}

sub admin_rpc_players {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    my @jids = map($_->jid, $self->players);
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok", \@jids]);
}

sub admin_rpc_bots {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    my @jids = map($_->jid, map($_->active_bots, $self->referees));
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok", \@jids]);
}

sub admin_rpc_shutdown {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    $self->logger->info("Referee shut down via RPC, by $from_jid.");
    $self->wall("This referee is shutting down NOW. Goodbye!");
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
    $self->stop;
}

sub admin_rpc_announce {
    my $self = shift;
    my ($from_jid, $rpc_id, $message) = @_;
    $self->groupchat("Admin message: $message");
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
}


=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003-2006 by Jason McIntosh.

=cut


1;
