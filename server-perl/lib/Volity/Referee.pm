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

Volity::Referee - Superclass for in-MUC game overseers.

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

=head2 For game programmers

After being instanced, your game object will have a referee object as
its C<referee> instance variable, accessible through the C<referee()>
accessor. You can call all the methods on it defined in this
manpage. However, you should avoid doing anything that would mess up
the game, such as having the referee leave the table! It's probably
more useful for accessing methods like C<groupchat()>.

In many cases, you can program your game module without ever directly
referring to the referee. It's just there if you need it (and for the
use of the lower-level Volity::Game base class, which does carry on a
continual conversation with its embedded referee object).

=head2 For everyone else

Unless you're writing a game module, you can probably happily ignore
this module. The game server will use it just fine, all by itself.

=head1 METHODS

The following methods describe this module's public API (insofar as
Perl modules can say that they have any public/private
distinction). You are welcome to snoop around in the code to see what
the other, internal methods do, but you probably wouldn't need to call
them during a game.

It's worth noting that this module suclasses from Volity::Jabber, and
therefore enjoys all the methods that it defines, as well as the ones
listed here.

=head2 Object accessors

All these are simple accessors which return the named object
attribute. If an argument is passed in, then the attribute's value is
first set to that argument.

It is through these accessors that you perform most game configuration
definition, such as the maximum number of players allowed per game.

This module inherits from Class::Accessor, so all the tips and tricks
detailed in L<Class::Accessor> apply here as well.

=over

=item error_message

A string to display to the MUC if something goes wrong.

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

You should treat-this as a read-only variable. This variable pulls
double-duty as a quick way to check whether a game is actively being
played, or if the ref is "idle" and waiting for a player to kick it
into action: if C<game> is defined, then a game is underway. (The
referee will undefine this variable after a game ends.)

=back

=head2 Other Methods

=over

=cut

use base qw(Volity::Jabber);
use fields qw(muc_jid game game_class players nicks starting_request_jid starting_request_id bookkeeper_jid error_message server muc_host bot_classes active_bots active_bot_registry last_rpc_id invitations ready_players is_recorded is_hidden name language seated_players);

#	      jid 		# This session's JID.
#	      muc_jid 		# The JID of this game's MUC.
#	      game 		# The game object!
#	      game_class	# The Perl class of our game.
#	      players		# The MUC's roster, as Volity::Player objects.
#	      starting_request_jid # The JID of the person who started the MUC.
#	      starting_request_id # The ID of the MUC-starting request.
# session
#   This referee's POE session.
# kernel
#   This referee's POE kernel.
# bot_classes
#   An array reference of retainer-bot classes.
# invitations
#   Hash of open invitations.
# ready_players
#   Hash of players who are ready to play.
# is_recorded
#   1 if the next game-ending event will result in a game record.
# is_hidden
#   1 if this ref's game hides itself from the bookkeeper's game finder.

use warnings;  no warnings qw(deprecated);
use strict;

use lib qw(/Users/jmac/Library/Perl/);
use Volity::Player;
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
use Jabber::NS qw(:all);
use Scalar::Util qw(weaken);
use Time::HiRes qw(gettimeofday);

###################
# Configgish variables 
###################

our $default_player_class = "Volity::Player";

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

  # Build the JID of our MUC.
  unless (defined($self->muc_host)) {
    croak ("You must define a muc_host on referee construction.");
  }
  $self->muc_jid($self->resource . '@' . $self->muc_host);

  # Set some query namespace handlers.
  $self->query_handlers->{'volity:iq:botchoice'} = {set=>'choose_bot'};

  $self->{active_bots} = [];

  $self->logger->debug("By the way, here's my password: " . $self->password);

  $self->last_rpc_id(0);

  $self->invitations({});

  $self->ready_players({});
  $self->seated_players({});

  $self->is_recorded(1);
  $self->is_hidden(0);

  unless (defined($self->name)) {
      # XXX Fix this...
#      $self->name($self->table_creator->nick . "'s game");
      $self->name("Some game.");
  }

  unless (defined($self->language)) {
      $self->language("en");
  }

  # Create our first game object.
  $self->create_game;

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

#  # Join the game MUC.

  $self->join_muc({jid=>$self->muc_jid, nick=>'volity'});

  $self->logger->debug("I think I sent something?!\n");

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
	  $self->handle_ready_player_request($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
      } elsif ($method eq 'add_bot') {
	  $self->add_bot($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
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
      } else {
	  $self->logger->warn("Got weird RPC request 'volity.$method' from $$rpc_info{from}.");
	  $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 999, "Unknown method: volity.$method");
	  return;
      }
  } elsif ($method =~ /^game\.(.*)$/) {
      # This appears to be a call to the game object.
      # Reaction depends on whether or not the game is afoot.
      my $method = $1;
      my $ok_to_call = 0;
      if ($self->game->is_afoot) {
	  if ($self->game->is_config_variable($method)) {
	      $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 999, "You can't configure the game once it has started.");
	  } else {
	      $ok_to_call = 1;
	  }
      } else {
	  unless ($self->game->is_config_variable($method)) {
	      $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 999, "Can't call $method! The game hasn't started yet.");
	  } else {
	      $ok_to_call = 1;
	  }
      }
      if ($ok_to_call) {
	  $$rpc_info{method} = $method;
	  $self->handle_game_rpc_request($rpc_info);
      }
  } else {
      $self->logger->warn("Referee at " . $self->jid . " received a $$rpc_info{method} RPC request from $$rpc_info{from}. Eh?");
  }
}

# handle_game_rpc_request: Called by handle_rpc_request upon receipt of an
# RPC request in the 'game' namespace... i.e. an RPC request on the current
# game. Performs some sanity checking, then passes it on.
sub handle_game_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;

  unless ($self->game) {
    $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 999, "There is no active game.");
    warn "No game.";
    return;
  }

  # We prepend an 'rpc_' to the method's name for ssecurity reasons.
  my $method = "rpc_$$rpc_info{method}";

  unless ($self->game->can($method)) {
    $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 999, "This game has no '$$rpc_info{method}' function.");
    warn "No function $method on " . $self->game;
    return;
  }

  my $player = $self->look_up_player_with_jid($$rpc_info{from});
  unless ($player) {
    $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 999, "You don't seem to be playing this game!");
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


  # The first arg is always the player who made thiis call.
  unshift(@args, $player);

#  warn "Calling $method with these args: @args\n";

  my @response = $self->game->$method(@args);

  if (@response) {
    my $response_type = shift(@response);
    if ($response_type eq 'fault') {
      # Oh, there's some in-game problem with the player's request.
      $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, @response);
    } else {
      # The game has a specific, non-fault response to send back.
      $self->send_rpc_response($$rpc_info{from}, $$rpc_info{id}, @response);
    }
  } else {
    # The game silently approved the request,
    # so send back a minimal positive response.
    $self->send_rpc_response($$rpc_info{from}, $$rpc_info{id}, "ok");
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
    $new_person_jid = $x->get_tag('item')->attr('jid');
    my $kernel = $self->kernel;
    if (not(defined($new_person_jid)) or $new_person_jid eq $self->jid) {
      # If there's no JID for this user, that means that the MUC hasn't
      # been configured yet, and _that_ means that the user is me!
      # I must have just created it. Well, then.
      $self->logger->debug("Let's configure, robots!\n");
      # Configure the MUC.

      my $config_form = Volity::Jabber::Form->new({type=>'submit'});
      $config_form->fields(
		           Volity::Jabber::Form::Field->new({var=>"muc#owner_roomname"})->values("A volity game..."),
		           Volity::Jabber::Form::Field->new({var=>"muc#owner_whois"})->values("anyone"),
			   );

      $self->send_query({
	  to=>$self->muc_jid,
	  from=>$self->jid,
	  id=>42,
	  type=>'set',
	  query_ns=>"http://jabber.org/protocol/muc#owner",
	  content=>[$config_form],
      });


      # Send the game-creating JID an RPC response letting them know
      # where the action is.
      $self->send_rpc_response($self->starting_request_jid,
			       $self->starting_request_id,
			       $self->muc_jid,
			       );
    } else {
      # All right, some other yahoo has changed presence.
      # Note in my list of potential players, depending upon whether
      # they're coming or going.
      $self->logger->debug("Looks like a player just joined.\n");
      my ($nick) = $node->attr('from') =~ /\/(.*)$/;
      if (defined($node->attr('type')) && ($node->attr('type') eq 'unavailable')) {
	$self->remove_player_with_jid($new_person_jid);
	# Remove this player from the nickanme lookup hash.
	delete($self->{nicks}{$nick});
	# If the last player left, leaving us alone, disconnect.
	unless (keys(%{$self->{nicks}})) {
	  $kernel->post($self->alias, 'disconnect');
	}
      } else {
	my $player = $self->add_player({nick=>$nick, jid=>$new_person_jid});
	# Also store this player's nickname, for later lookups.
	$self->logger->debug( "Storing $new_person_jid, under $nick");
	$self->game->tell_player_about_config($player);
      }
    }
  }
}



###################
# MUC user tracking
###################

# These methods refer to MUC users besides myself as 'players'.

sub add_player {
  my $self = shift;
  my ($args) = @_;
  # Figure out which class to call a player-constructor on.
  # Games can override Volity::Player, the default class.

  my $player_class = $self->game_class->player_class || $default_player_class;

  my $player = $player_class->new({jid=>$$args{jid}, nick=>$$args{nick}, referee=>$self});
  $self->{players}{$$args{jid}} = $player;
  $self->{nicks}{$$args{nick}} = $$args{jid};

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

# look_up_player_with_jid:
# Takes a JID, and returns the corresponding player object, or undef.
# For flexibility, if the JID appears to be a MUC-only JID using a nickname,
# it uses the internal nicknames table for lookups instead.
sub look_up_player_with_jid {
  my $self = shift;
  my ($jid) = @_;
  $self->logger->debug("Fetching player object for JID $jid.");
  my $muc_jid = $self->muc_jid;
  if (my ($nickname) = $jid =~ m|^$muc_jid/(.*)$|) {
      $self->logger->debug("Oh, it was a table-based JID.");
      $jid = $self->look_up_jid_with_nickname($nickname);
      $self->logger->debug("Right, then; doing a lookup on $jid instead.");
  }
  return $self->{players}{$jid};
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

######################
# Game config info & pregame sanity checking
######################

# check_sanity: Runs some tests on this 
# new object to make sure that all is well.
# XXX This could use better exception handling, eh?
sub check_sanity {
  my $self = shift;
  my $player_count = $self->players;
  $self->logger->debug("Player count is $player_count.");
  my $player_statement = $self->state_allowed_players;
  if (defined($self->game_class->max_allowed_players) and $self->game_class->max_allowed_players < $player_count) {
    $self->error_message("This game takes $player_statement players, but there are $player_count players here. That's too many!");
    return 0;
  } elsif (defined($self->game_class->min_allowed_players) and $self->game_class->min_allowed_players > $player_count) {
    $self->error_message("This game takes $player_statement players, but there are $player_count players here. That's not enough!");
    return 0;
  }
  return 1;
}

# state_allowed_players: Utility method for stating the number of players
# that this game supports.
sub state_allowed_players {
  my $self = shift;
  my $max = $self->game_class->max_allowed_players;
  my $min = $self->game_class->min_allowed_players;
  my $statement;
  if (not($min) and $max) {
    $statement = "$max or fewer";
  } elsif ($min and not($max)) {
    $statement = "$min or more";
  } elsif (not($min) and not($max)) {
    $statement = "any number";
  } else {
    $statement = "between $min and $max";
  }
  return $statement;
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
	$self->send_rpc_fault($from_jid, $id, 999, "You can't configure the game once it has started.");
    } else {
	unless (($recorded_boolean eq '0') or ($recorded_boolean eq '1')) {
	    $self->send_rpc_fault($from_jid, $id, 999, "The argument to recorded() must be 0 or 1.");
	    return;
	}
	$self->send_rpc_response($from_jid, $id, "ok");
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
  unless ($self->bot_classes) {
    $self->send_rpc_fault($from_jid, $id, 3, "Sorry, this game server doesn't host any bots.");
    return;
  }
  
  # If we offer only one flavor of bot, then Bob's your uncle.
  my @bot_classes = $self->bot_classes;
  if (@bot_classes == 1) {
    if (my $bot = $self->create_bot(($self->bot_classes)[0])) {
      $self->send_rpc_response($from_jid, $id, "ok");
      $bot->kernel->run;
    } else {
      $self->send_rpc_fault($from_jid, $id, 4, "I couldn't create a bot for some reason.");
    }
    return;
  }

  # We seem to have more than one bot. Send back a form.
  my @form_options;
  my $default_name_counter;
  for my $bot_class ($self->bot_classes) {
    my $label = $bot_class->name || 'Bot' . ++$default_name_counter;
    if (defined($bot_class->description)) {
      $label .= ": " . $bot_class->description;
    }
    push (@form_options, [$bot_class, $label]);
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
  $self->send_rpc_response($from_jid, $id, "ok");
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
  unless (grep($_ eq $chosen_bot_class, $self->bot_classes)) {
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
  my ($bot_class) = @_;
  # Generate a resource for this bot to use.
  my $resource = $bot_class->name . gettimeofday();
  my $bot = $bot_class->new(
			    {
			     password=>$self->password,
			     resource=>$resource,
			     alias=>$resource,
			     muc_jid=>$self->muc_jid,
			     referee=>$self,
			    }
			 );
  $self->logger->info("New bot (" . $bot->jid . ") created by referee (" . $self->jid . ").");
  return $bot;
}

# end_game: Not really an RPC call, but putting it here for now for
# symmetry's sake.
# It's called by a game object.
sub end_game {
  my $self = shift;
  $self->groupchat("The game is over.");

  my $game = delete($self->{game});

  # Tell the players (their clients, really) to wrap it up.
  foreach ($self->players) {
      $_->end_game;
  }

  # Time to register this game with the bookkeeper!
  # Create and initialize a new game record object.
  $self->logger->debug("Preparing game record.");
  my $record = Volity::GameRecord->new({
					server=>$self->basic_jid,
				      });
  $record->game_uri($self->game_class->uri);
  $record->end_time(scalar(localtime));
  foreach my $player_list (qw(players winners quitters)) {
    my @players = $game->$player_list;
    if (@players and defined($players[0])) {
      my @player_jids;
      for my $player (@players) {
	if (ref($player) eq 'ARRAY') {
	  push (@player_jids, [map($_->basic_jid, @$player)]);
	} else {
	  push (@player_jids, $player->basic_jid);
	}
      }
      # This is hacky... swerving around the accessor like this. OH WELL.
      $record->{$player_list} = \@player_jids;
    }
  }


  # Give it the ol' John Hancock, if possible.
  if (defined($Volity::GameRecord::gpg_bin) and defined($Volity::GameRecord::gpg_secretkey) and defined($Volity::GameRecord::gpg_passphrase)) {
      $record->sign;
  }
  
  # Send the record to the bookkeeper!
  $self->send_record_to_bookkeeper($record);

  # Create a fresh new game.
  $self->create_game;
}

# create_game: internal method that simply creates a new game object
# and stores it as an instance variable.
sub create_game {
    my $self = shift;
    my $game_class = $self->game_class;
    my $game = $self->game($game_class->new({referee=>$self}));
    $self->logger->debug("Created a game!!\n");
    return $game;
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
  my ($from_jid, $rpc_id, @args) = @_;
  my $invitation_id = $self->last_rpc_id;
  $invitation_id++; $self->last_rpc_id($invitation_id);
  $self->invitations->{$invitation_id} = [$rpc_id, $from_jid];
  $self->logger->debug("$from_jid will invite $args[0]. New ID is $invitation_id. Old id was $rpc_id.");
  $self->send_rpc_response($from_jid, $rpc_id, "ok");			    
  $self->make_rpc_request({to=>$args[0],
			   id=>$invitation_id,
			   methodname=>'volity.receive_invitation',
			   args=>[{player=>$from_jid,
				   table=>$self->muc_jid,
				   referee=>$self->jid,
				   server=>$self->server->jid,
				   ruleset=>$self->game_class->uri,
			       }],
			   handler=>'invitation',
			  });
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
	$self->start_game;
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
      if ($self->seated_players->{$player}) {
	  $self->send_rpc_response($from_jid, $rpc_id, "ok");
	  $self->ready_player($player);
      } else {
	  $self->logger->debug("But that player isn't sitting down!");
	  $self->send_rpc_fault ($from_jid, $rpc_id, 999, "You wish to state your readiness to play, but you are not seated at the table.");
      }
    } else {
  	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault ($from_jid, $rpc_id, 999, "You wish to state your readiness to play, but you don't seem to be actually playing.");
	return;
    }
}

sub stand_player {
    my $self = shift;
    my ($player) = @_;
    delete ($self->seated_players->{$player});
}

sub handle_stand_request {
    my $self = shift;
    my ($from_jid, $rpc_id, @args) = @_;
    $self->logger->debug("$from_jid wishes to stand up.");
    my $player = $self->look_up_player_with_jid($from_jid);
    if ($player) {
	if ($self->seated_players->{$player}) {
	    $self->send_rpc_response($from_jid, $rpc_id, "ok");
	    $self->stand_player($player);
	} else {
	    $self->logger->debug("But that player isn't sitting down!");
	    $self->send_rpc_fault ($from_jid, $rpc_id, 999, "You seem to be standing already.");
	}
    } else {
  	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault ($from_jid, $rpc_id, 999, "You don't seem to be at this table.");
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
    # The message we sent depends on whether or not this player was sitting.
    if ($self->seated_players->{$player}) {
	for my $other_player ($self->players) {
	    $other_player->player_unready($player);
	}
    } else {
	$self->seated_players->{$player} = $player;
	for my $other_player ($self->players) {
	    $other_player->player_sat($player);
	}
	# This player's sitting down has changed the configuration.
	# So, everyone loses readiness.
	$self->unready_all_players;
    }
}


sub handle_unready_player_request {
    my $self = shift;
    my ($from_jid, $rpc_id, @args) = @_;
    $self->logger->debug("$from_jid has announced UNreadiness.");
    my $player = $self->look_up_player_with_jid($from_jid);
    if ($player) {
	if ($self->game->is_afoot) {
	    $self->logger->debug("But they were slow on the trigger, because the game has already started!");
	    $self->send_rpc_fault($from_jid, $rpc_id, 999, "Too late, the game is already underway!");
	    return;
	} else {
	    $self->send_rpc_response($from_jid, $rpc_id, "ok");
	    $self->unready_player($player);
	}
    } else {
	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault ($from_jid, $rpc_id, 999, "You wish to state your unreadiness to play, but you don't seem to be actually playing.");
	return;
    }
}

# Are all players ready: returns truth if all the players are ready to go,
# falsehood otherwise.
sub are_all_players_ready {
    my $self = shift;
    if ($self->players == $self->ready_player_list) {
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

# handle_stand_player_request: Handle a player's request to stand up.
sub handle_stand_player_request {
  my $self = shift;
  my ($from_jid, $rpc_id, @args) = @_;
  $self->logger->debug("$from_jid wishes to stand up.");
  my $player = $self->look_up_player_with_jid($from_jid);
  if ($player) {
      $self->send_rpc_response($from_jid, $rpc_id, "ok");
      $self->stand_player($player);
    } else {
  	$self->logger->debug("But I don't recognize that JID as a player.");
	$self->send_rpc_fault($from_jid, $rpc_id, 999, "You wish to stand up, but you don't seem to be in the room.");
	return;
    }
}

# stand_player: Set the given player as standing, and announce to the table.
sub stand_player {
    my $self = shift;
    my ($player) = @_;
    # Only do something if the player was actually in the "seated" hash.
    if (delete($self->seated_players->{$player})) {
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
    $self->send_rpc_response($inviter, $original_rpc_id, $$response{response});
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
    $self->logger->warn("Got unexpected RPC fault, id $$fault_info{id}: $$fault_info{code} - $$fault_info{string}");
  }
}

sub stop {
  my $self = shift;
  # Kick out all the bots.
  foreach (grep(defined($_), $self->active_bots)) {
      $_->stop;
  }
  $self->kernel->post($self->alias, 'shutdown_socket', 0);
  $self->server->remove_referee($self);
}

sub DESTROY {
  my $self = shift;
  $self->server(undef);
}

# start_game: Internal method called when all the players have confirmed
# their readiness to begin.
sub start_game {
  my $self = shift;
  # Time for a sanity check.
  unless ($self->check_sanity) {
    # Something seems to have gone awry. Alas!
    $self->logger->debug("Not starting a game; failed sanity check.");
    $self->send_message({
			 to=>$self->muc_jid,
			 type=>"groupchat",
			 body=>"I can't create a new game right now! Error message: " . $self->error_message,
			});
    return;
  }

  # No error message? Great... let's play!

  # Tell the game to start itself.
  $self->game->start;
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
    foreach ('max-players', 'server', 'table', 'afoot', 'players', 'language', 'name') {
	push (@fields, Volity::Jabber::Form::Field->new({var=>$_}));
    }
    my $game_class = $self->game_class;
    $fields[0]->values($game_class->max_allowed_players);
    $fields[1]->values($self->server->jid);
    $fields[2]->values($self->muc_jid);
    $fields[3]->values($self->game->is_afoot);
    $fields[4]->values(scalar($self->players));
    $fields[5]->values($self->language);
    $fields[6]->values($self->name);
    $self->send_disco_info({
	to=>$iq->attr('from'),
	id=>$iq->attr('id'),
	items=>\@items,
	fields=>\@fields,
    });
}


=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003 by Jason McIntosh.

=cut


1;
