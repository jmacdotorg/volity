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
server object's C<referee_class> instance variable, and varies from
game to game. Each Frivolity game implementation must define its own
referee class, which should inherit from Volity::Referee if it knows
what's good for it.

=head1 USAGE

Just create a new package for your game's referee

 package MyGame::Referee;

 use base qw(Volity::Referee);

...and now your package may use (or override) all the methods
described below. For simple games, you can just use some of the
accessors (see L<"Object accessors">) to set some configuration
options, and let the Volity::Referee superclass handle everything
else.

At any rate, most of your code and effort will probably go into your
actual game class; see L<Volity::Game> for more information.

=head1 METHODS

=head1 Object accessors

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

=cut

use base qw(Volity::Jabber);
use fields qw(muc_jid game game_class players nicks starting_request_jid starting_request_id bookkeeper_jid error_message server muc_host bot_classes active_bots active_bot_registry);

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
use PXR::Node;
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

  $self->debug("By the way, here's my password: " . $self->password);

  return $self;

}

################
# Jabber POE states
################

sub jabber_authed {
  my $kernel = $_[KERNEL];
  my $heap = $_[HEAP];
  my $session = $_[SESSION];
  my $self = $_[OBJECT];
  $self->debug("***REFEREE*** We have authed!\n");
  $kernel->post($self->alias, 'register', qw(iq presence message));

#  # Join the game MUC.

#  my $presence = PXR::Node->new('presence');
#  $presence->attr(from=>$self->jid);
#  $presence->attr(to=>"$self->{muc_jid}/volity");
#  $presence->insert_tag('x', 'http://jabber.org/protocol/muc');
#  $kernel->post($self->alias, 'output_handler', $presence);

  $self->join_muc({jid=>$self->muc_jid, nick=>'volity'});

  $self->debug("I think I sent something?!\n");

}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  my $method = $$rpc_info{method};
  # For security's sake, we explicitly accept only a few method names.
  # In fact, the only one we care about right now is 'start_game'.
  if ($method eq 'start_game') {
    $self->start_game($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
  } elsif ($method eq 'add_bot') {
    $self->add_bot($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
  } else {
    $self->debug( "Referee at " . $self->jid . " received a $$rpc_info{method} RPC request from $$rpc_info{from}. Eh?");
  }
}

sub jabber_presence {
  my $self = shift;
  $self->debug("****REFEREE**** Got some presence.\n");
  my ($node) = @_;
  if (my $x = $node->get_tag(x=>"http://jabber.org/protocol/muc#user")) {
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
      $self->debug("Let's configure, robots!\n");
      # Configure the MUC.
      $self->send_form({
			to=>$self->muc_jid,
			from=>$self->jid,
			id=>42,
			type=>"http://jabber.org/protocol/muc#owner",
			fields=>{
				 "muc#owner_roomname"=>"The house of fun and glass",
				 "muc#owner_whois"=>"anyone",
			       }
		      });

      # Send the game-creating JID an RPC response letting them know
      # where the action is.
      my $response = RPC::XML::response->new($self->muc_jid);
      my $rpc_iq = PXR::Node->new('iq');
      $rpc_iq->attr(type=>'result');
      $rpc_iq->attr(to=>$self->starting_request_jid);
      $rpc_iq->attr(id=>$self->starting_request_id) if defined($self->starting_request_id);
      # I don't like this so much, sliding in the response as raw data.
      # But then, I can't see why it would break.
      my $response_xml = $response->as_string;
      $response_xml = substr($response_xml, 22);
      $rpc_iq->insert_tag(query=>'jabber:iq:rpc')
	->rawdata($response_xml);
      $kernel->post($self->alias, 'output_handler', $rpc_iq);

    } else {
      # All right, some other yahoo has changed presence.
      # Note in my list of potential players, depending upon whether
      # they're coming or going.
      $self->debug("Looks like a player just joined.\n");
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
	$self->add_player({nick=>$nick, jid=>$new_person_jid});
	# Also store this player's nickname, for later lookups.
	$self->debug( "BWAAAAH $new_person_jid, under $nick ******");
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

  $self->{players}{$$args{jid}} = $player_class->new({jid=>$$args{jid}, nick=>$$args{nick}});
  $self->{nicks}{$$args{nick}} = $$args{jid};
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
# Takesa  JID, and returns the corresponding player object, or undef.
sub look_up_player_with_jid {
  my $self = shift;
  my ($jid) = @_;
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
  $self->debug("Player count is $player_count.");
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

=for comment

ARRGH I HATE THIS. The class/object thing... FOOEY. Think of a prettier way!!

=cut

 

sub handle_groupchat_message {
  my $self = shift;
  if (defined($self->game)) {
    $self->{game}->handle_groupchat_message(@_);
  } else {
    my $class = $self->game_class;
    eval "$class->handle_groupchat_message(\@_);";
  }
}  

sub handle_chat_message {
  my $self = shift;
  if (defined($self->game)) {
    $self->{game}->handle_chat_message(@_);
  } else {
    my $class = $self->game_class;
    eval "$class->handle_chat_message(\@_);";
  }
}  

sub handle_normal_message {
  my $self = shift;
  if (defined($self->game)) {
    $self->{game}->handle_normal_message(@_);
  } else {
    my $class = $self->game_class;
    eval "$class->handle_normal_message(\@_);";
  }
}  

####################
# RPC methods (receiving)
####################

# start_game: handle the start_game RPC call, send by a player sitting in a MUC
# with the game server, when said player wants to begin a game involving all
# players present.
sub start_game {
  my $self = shift;
  my ($from_jid, $id, @args) = @_;
  # Make sure the player who sent us this request is in the MUC.
  my $requester_jid;
  if ($self->look_up_player_with_jid($from_jid)) {
    $requester_jid = $from_jid;
  } else {
    my ($from_nick) = $from_jid =~ /\/(.*)$/;
    $requester_jid = $self->{nicks}{$from_nick};
  }
  unless (defined($requester_jid)) {
    $self->debug("Not starting a game, because I don't recognize JID $requester_jid.");
    $self->send_rpc_fault($id, 999, "I'm sorry, but you don't seem to be sitting at my table, so I won't start a game for you.");
    return;
  }
  if ($requester_jid ne $self->starting_request_jid) {
    $self->send_rpc_fault($from_jid, $id, 2, "You asked to start a game, but you did not initiate the game.");
    $self->debug( "Weird... expected a start_game request from $self->{starting_request_jid} but got one from $requester_jid instead.\n");
    return;
    
  }

  # Look at the sender funny, if we're already playing a game.
  if (defined($self->game)) {
    $self->debug("Not starting a game, because I think one is already running.");
    $self->send_rpc_fault($from_jid, $id, 1, "Can't start a new game, because we're playing one already.");
    return;
  }

  # Time for a sanity check.
  unless ($self->check_sanity) {
    # Something seems to have gone awry. Alas!
    $self->debug("Not starting a game; failed sanity check.");
    $self->send_message({
			 to=>$self->muc_jid,
			 type=>"groupchat",
			 body=>"I can't create a new game right now! Error message: " . $self->error_message,
			});
    $self->send_rpc_fault($from_jid, $id, 999, $self->error_message);
    return;
  }

  # No error message? Great... let's play!
  # Try creating the new game object.
  my $game = $self->game_class->new({players=>[$self->players], referee=>$self});
  $self->game($game);
  $self->debug("Created a game!!\n");
  # Send back a positive RPC response.
  $self->send_rpc_response($from_jid, $id, "ok");
  $self->send_message({
		       to=>$self->muc_jid,
		       type=>"groupchat",
		       body=>"The game has begun!",
		     });
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
  return $bot_class->new(
			    {
			     password=>$self->password,
			     resource=>$resource,
			     alias=>$resource,
			     debug=>$self->debug,
			     muc_jid=>$self->muc_jid,
			     referee=>$self,
			    }
			 );
  # XXXXXXXX Everything under this line is ignored....
#  push (@{$self->active_bots}, $bot);
#   $self->debug("***New bot has a jid of " . $bot->jid . "****\n");
#  $self->{active_bot_registry}->{$bot->jid} = $bot;
#  # Now command the bot to join the table.
##  $bot->muc_to_join($self->muc_jid);
#  return $bot;
  # XXXXXXXX Move the above somewhere useful, thx.
}

# end_game: Not really an RPC call, but putting it here for now for
# symmetry's sake.
# It's called by a game object.

sub end_game {
  my $self = shift;
  $self->groupchat("The game is over.");
  my $game = delete($self->{game});

  # Time to register this game with the bookkeeper!
  # Create an initialize a new game record object.

  $self->debug("Preparing game record.");
  my $record = Volity::GameRecord->new({
					server=>$self->basic_jid,
				      });
  $record->game_uri($self->game_class->uri);
  $record->end_time(scalar(localtime));
  foreach my $player_list (qw(players winners quitters)) {
    my @players = $game->$player_list;
    if (@players and defined($players[0])) {
      $record->$player_list(map($_->basic_jid, @players));
    }
  }

  $record->sign;
  
  # Send the record to the bookkeeper!
  $self->send_record_to_bookkeeper($record);

  # That's all.

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
			   methodname=>'record_game',
			   args=>$hash
			 });
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

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003 by Jason McIntosh.

=cut


1;
