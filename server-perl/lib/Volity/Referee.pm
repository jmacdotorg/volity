package Volity::Referee;

use base qw(Volity::Jabber);
use fields qw(muc_jid game game_class players nicks starting_request_jid starting_request_id);

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

use warnings;
use strict;

use lib qw(/Users/jmac/Library/Perl/);
use Volity::Player;
use RPC::XML;

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

  # XXXXXXX
  # Setting stuff in a dumb manner!
  # XXXXXXX
  $self->muc_jid('game@conference.localhost');
  # XXXXXXX

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
  print "***REFEREE*** We have authed!\n";
  $kernel->post($self->alias, 'register', qw(iq presence message));

  # Join the game MUC.

  my $presence = PXR::Node->new('presence');
  $presence->attr(from=>$self->jid);
  $presence->attr(to=>"$self->{muc_jid}/volity");
  $presence->insert_tag('x', 'http://jabber.org/protocol/muc');
  $kernel->post($self->alias, 'output_handler', $presence);

  print "I think I sent something?!\n";

}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  my $method = $$rpc_info{method};
  # For security's sake, we explicitly accept only a few method names.
  # In fact, the only one we care about right now is 'start_game'.
  if ($method eq 'start_game') {
    $self->start_game($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
  } else {
    $self->debug( "Referee at " . $self->jid . " received a $$rpc_info{method} RPC request from $$rpc_info{from}. Eh?");
  }
}

sub jabber_presence {
  print "****REFEREE**** Got some presence.\n";
  my $self = shift;
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
      print "Let's configure, robots!\n";
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
  my ($from_nick) = $from_jid =~ /\/(.*)$/;
  my $requester_jid = $self->{nicks}{$from_nick};
  die "I have no record of a nickname '$from_nick'" unless $requester_jid;
  if ($requester_jid ne $self->starting_request_jid) {
    $self->debug( "Weird... expected a start_game request from $self->{starting_request_jid} but got one from $requester_jid instead.\n");
    return;
  }

  # Look at the sender funny, if we're already playing a game.
  if (defined($self->game)) {
    $self->send_message({
			 to=>$from_jid,
			 body=>"You asked to start a new game, but we seem to be playing one already. Sorry?",
		       });
    return;
  }

  # Try creating the new game object.
  my $game = $self->game_class->new({players=>[$self->players], server=>$self});
  if ($game->error_message) {
    # Something seems to have gone awry. Alas!
    $self->send_message({
			 to=>$self->muc_jid,
			 type=>"groupchat",
			 body=>"I can't create a new game right now! Error message: " . $game->error_message,
		       });
    $self->send_rpc_response($self->starting_request_jid, 'start_game', undef);
  } else {
    # No error message? Great... let's play!
    $self->game($game);
    $self->debug("Created a game!!\n");
    # Send back a positive RPC response.
    # XXX Eek, hardcoded ID attribute...
    $self->send_rpc_response($self->starting_request_jid, $id, "ok");
    $self->send_message({
			 to=>$self->muc_jid,
			 type=>"groupchat",
			 body=>"The game has begun!",
		       });
  }
}

# end_game: Not really an RPC call, but putting it here for now for
# symmetry's sake.
# It's called by a game object.

sub end_game {
  my $self = shift;
  # XXX Here is where the record-storing magic would go.
  $self->groupchat("The game is over.");
  delete($self->{game});
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


1;
