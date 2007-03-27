package Volity::Bookkeeper;

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

use warnings;
use strict;
use Data::Dumper;
use DateTime::Format::MySQL;

use base qw(Volity::Jabber);
use fields qw(scheduled_game_info_by_rpc_id 
	      scheduled_game_info_by_muc_jid
	      reconnection_alarm_id
	      payment_class
	      payment_object
	      outstanding_verify_game_calls_by_rpc_id
	      outstanding_verify_game_calls_by_referee
	      outstanding_prepare_game_responses_by_referee
	      slacker_players_by_referee
	      refusing_players_by_referee
	      );

our $VERSION = '0.7';
use Carp qw(carp croak);

use Volity::GameRecord;
use Volity::Info::Server;
use Volity::Info::Game;
use Volity::Info::Player;
use Volity::Info::File;
use Volity::Info::ScheduledGamePlayer;
use Volity::Info::Resource;
use Volity::Info::Factory;

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
	   Component::Jabber;
	  );

# Lazily set some constants for timeouts and such.
our $NEW_TABLE_TIMEOUT = 60;
our $JOIN_TABLE_TIMEOUT = 60;
our $SCHEDULED_GAME_CHECK_INTERVAL = 60;
our $INVITEE_JOIN_TIMEOUT = 3600;
our $RECONNECTION_TIMEOUT = 5;
our $VERIFY_GAME_TIMEOUT = 30;

sub initialize {
  my $self = shift;

  $self->SUPER::initialize(@_);

   foreach (qw(scheduled_game_info_by_rpc_id scheduled_game_info_by_muc_jid outstanding_verify_game_calls_by_rpc_id outstanding_verify_game_calls_by_referee outstanding_prepare_game_responses_by_referee slacker_players_by_referee refusing_players_by_referee)) {
       $self->{$_} = {};
   }

  unless ($self->payment_class) {
      $self->payment_class("Volity::PaymentSystem::Free");
  }

  my $payment_class = $self->payment_class;
  eval "require $payment_class;";
  if ($@) {
      croak ("Failed to require the payment class " . $self->payment_class . ":\n$@");
  }
  
  $self->payment_object($self->payment_class->new);

  return $self;
}

sub init_finish {
    my $self = shift;
    
    # Cancel any pending reconnection alarms.
    $self->kernel->alarm_remove($self->reconnection_alarm_id) if defined($self->reconnection_alarm_id);
    $self->reconnection_alarm_id(undef);

    # Set up some POE event handlers.
    foreach (qw(time_to_check_the_schedule new_table_rpc_timed_out new_table_join_attempt_timed_out waiting_for_invitees_timed_out verify_game_timeout)) {
	$self->kernel->state($_, $self);
    }

    # Perform the first schedule-check.
    $self->time_to_check_the_schedule;
    $self->SUPER::init_finish(@_);
}

# We override Volity::Jabber's send_presence in order to attach some
# additional JEP-0115 information.
sub send_presence {
    my $self = shift;
    my ($config) = @_;
    $config ||= {};
    $$config{caps} = {
	node => "http://volity.org/protocol/caps",
	ext => "bookkeeper",
	ver => "1.0",
    };
    return $self->SUPER::send_presence($config);
}


sub react_to_disconnection_error {
    my $self = shift;
    $self->logger->debug("Attempting to reconnect to the server...\n");
    $self->attempt_reconnection;
}

sub attempt_reconnection {
    my $self = shift;
    $self->kernel->state("reconnection_timeout", $self);
    my $alarm_id = $self->kernel->delay_set("reconnection_timeout", $RECONNECTION_TIMEOUT);
    $self->reconnection_alarm_id($alarm_id);
    $self->alias("volity" . time);
    $self->logger->warn("Trying to reconnect..." . $self->host . $self->port);
    $self->start_jabber_client;
}

sub reconnection_timeout {
    my $self = shift;
    $self->logger->warn("Reconnection timeout!");
    $self->logger->warn("I'll try again.");
    $self->attempt_reconnection;
}

####################
# Jabber event handlers
####################

# handle_rpc_request: Since this module will define a wide variety of RPC
# methods, instead of elsif-ing through a long list of possible request
# names, we will call only methods that begin with "_rpc_". This offers pretty
# good security, I think.
# If the request is doable (this method exists on the bookkeeper object), then
# it captures the return list. If it has zero or one value, then it passes
# this back to the caller as an RPC resposne. If it has two values, then it
# passes it back as an RPC fault, using the first value as an error cose and
# the second as an error string.
sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  # Make sure that the namespace is correct...
  if ($$rpc_info{method} =~ /^volity\.(.*)$/) {
    my $method = $1;
    $method = "_rpc_" . $method;
    warn "******$method******\n";
    if ($self->can($method)) {
      my @response = eval{$self->$method($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});};
      if ($@) {
	  $self->report_rpc_error(@_);
	  return;
      }
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
	      $self->send_rpc_response($$rpc_info{from}, $$rpc_info{id}, @response);
	  }
      } else {
	  # We have silently approved the request,
	  # so send back a minimal positive response.
	  $self->send_rpc_response($$rpc_info{from}, $$rpc_info{id}, ["volity.ok"]);
      }
    } else {
      $self->logger->warn("I received a $$rpc_info{method} RPC request from $$rpc_info{from}, but I don't know what to do about it, so I'm sending back a fault.\n");
      $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 603, "Unknown method.");
    }
  } else {
    $self->logger->warn("Received a $$rpc_info{method} RPC request; it's not in the volity namespace, so I'm sending back a fault.");
    $self->send_rpc_fault($$rpc_info{from}, $$rpc_info{id}, 603, "Unknown namespace. I accept only RPCs in the 'volity.*' namespace.");
  }
}

# This presence handler takes care of auto-approving all subscription
# requests. Volity entities are very social like that.
# It also handles all the work of joining MUCs and figuring out what to
# do about it.
sub jabber_presence {
  my $self = shift;
  my ($presence) = @_;		# POE::Filter::XML::Node object
  if ($presence->attr('type') and $presence->attr('type') eq 'subscribe') {
    # A subscription request! Shoot back approval.
    $self->send_presence(
			 {
			  to=>$presence->attr('from'),
			  type=>'subscribed',
			 }
			);
  }
  elsif (my $x = $presence->get_tag('x', [xmlns=>"http://jabber.org/protocol/muc#user"])) {
      my ($muc_jid) = $presence->attr('from') =~ /^(.*?)\//;
      my $info = $self->scheduled_game_info_by_muc_jid->{$muc_jid};
      # Aha, someone has entered the game MUC.
      # Figure out who it's talking about.
      my $new_person_jid;
      # JID is always in an item tag, since the MUC is either non-anonymous
      # or semi-anonymous, so the moderator (that's me) will have access to
      # their full JIDs.
      return unless $x->get_tag('item');
      $new_person_jid = $x->get_tag('item')->attr('jid');
      my $new_person_basic_jid;
      if ($new_person_jid) {
	  ($new_person_basic_jid) = $new_person_jid =~ /^(.*?)\//;
      }
      if (ref($info->{scheduled_game}) && grep ($new_person_basic_jid eq $_, map($_->jid, $info->{scheduled_game}->players))) {
	  # Hey, this is one of the people I've been waiting for!
	  # That means I can go.
	  $self->kernel->alarm_remove($info->{alarm_id});
	  $self->send_message({
	      to => $muc_jid,
	      type => "groupchat",
	      body => {
		  en => "One of the people who wished to play this game has arrived. I'll be leaving, then. Have fun!",
		  es => "Una de las personas que desea participar en este juego ha llegado. Hasta otro momento, Ábuen juego!",
	      }
	  });
	  $self->leave_muc($muc_jid);
	  delete($self->scheduled_game_info_by_muc_jid->{$muc_jid});
	  $info->{scheduled_game}->delete;
      }
      elsif ((my $c = $presence->get_tag('c')) && 
	     ($presence->get_tag('c')->attr('node') eq "http://volity.org/protocol/caps")) {
	  my $volity_role = $c->attr('ext');
	  if ($volity_role eq "referee") {
	      # We've found the referee!
	      # Therefore, we have all the information we need to send out
	      # the invitations, so let's do it.

	      $self->logger->debug("I have determined that the referree for the MUC with JID $muc_jid is $new_person_jid. I shall now send out invitations.");

	      # Cancel the can't-join-the-table alarm.
	      $self->kernel->alarm_remove($info->{alarm_id});

	      # Set a new alarm in case nobody responds to our invitation.
	      my $alarm_id = $self->kernel->delay_set("waiting_for_invitees_timed_out",
						      $INVITEE_JOIN_TIMEOUT,
						      $muc_jid,
						      );
	      $info->{alarm_id} = $alarm_id;

	      # Now fire off the actual invitations.
	      # XXX This needs alarms for response timeouts...
	      # XXX Also, need to include a message.
	      eval {
		  for my $player ($info->{scheduled_game}->players) {
		      $self->logger->debug("I am sending an invitation to " . $player->jid);
		      $self->send_rpc_request({
			  id         => "player-invitation",
			  to         => $new_person_jid,
			  methodname => "volity.invite_player",
			  args       => [$player->jid,
					 ],
		      });
		  }
	      };
	      if ($@) {
		  use Data::Dumper;
		  $self->logger->error("Yikes! a scheduled game info hash was broken. Here's the full dump.\n" . Dumper($info));
		  return;
	      }
	  }
      }
      
  }
}

# We're expecting only one RPC response, so I'll keep the RPC response
# handler simple for now.
sub handle_rpc_response {
    my $self = shift;
    my ($args) = @_;
    if ($args->{id} =~ /^scheduled-game-(\d+)$/) {
	# It's a good response from a new_table request!
	# Cancel the new_table timeout alarm,
	# then join the table.
	my $info = delete($self->scheduled_game_info_by_rpc_id->{$args->{id}});
	$self->kernel->alarm_remove($info->{alarm_id});
	if ($args->{response}->[0] eq 'volity.ok') {
	    my $muc_jid = $args->{response}->[1];
	    my $alarm_id = $self->kernel->delay_set("new_table_join_attempt_timed_out",
						    $JOIN_TABLE_TIMEOUT,
						    $muc_jid,
						    );
	    $info->{alarm_id} = $alarm_id;
	    $self->join_muc({
		nick => "schedule_bot",
		jid  => $muc_jid,
	    });
	    # Remember this MUC JID.
	    $self->{scheduled_game_info_by_muc_jid}{$muc_jid} = $info;
	}
	else {
	    # XXX Ooh, we got some kind of other response?!
	}
    }
    elsif ($args->{id} =~ /^verify-game-(\d+)$/) {
	# It's a good response from a player about a verify_game call.
	# Get the stored info about this call, cancel this player's
	# timeout alarm, and clear this player from the people we're
	# waiting for.
        $self->logger->debug("Received a verify-game response from $args->{from}.");
	my $info_hash = delete($self->outstanding_verify_game_calls_by_rpc_id->{$args->{id}});
	$self->kernel->alarm_remove($info_hash->{alarm_id});

	delete($self->outstanding_verify_game_calls_by_referee->{$info_hash->{referee_jid}}->{$info_hash->{player_jid}});

	if ($args->{response}) {
            $self->logger->debug("They are accepting this game.");
        }
        else {
	    # The player has refused this verify_game call. Well now.
            $self->logger->debug("They are refusing this game.");
	    $self->refusing_players_by_referee->{$info_hash->{referee_jid}} ||= [];
	    push (@{$self->refusing_players_by_referee->{$info_hash->{referee_jid}}}, $info_hash->{player_jid});
	}

    }
    else {
	$self->logger->warn("Received an unexpected RPC repsonse, with the ID $$args{id}.");
    }
}

sub handle_rpc_fault {
    my $self = shift;
    my ($args) = @_;
}

sub handle_rpc_transmission_error {
    my $self = shift;
    my ($iq_node, $error_code, $error_message) = @_;
}

sub handle_disco_info_request {
    my $self = shift;

    my ($iq) = @_;
    $self->logger->debug("I got a disco info request from " . $iq->attr('from'));
    # I'm making up my own category and type stuff, here.
    # I'll have to ask the Jabber folks what I actually ought to be doing.
    my @items = (
		 Volity::Jabber::Disco::Feature->new({var=>'http://jabber.org/protocol/disco#info'}),
		 Volity::Jabber::Disco::Feature->new({var=>'http://jabber.org/protocol/disco#items'}),
		 );
    my $identity = Volity::Jabber::Disco::Identity->new({category=>'volity',
							 type=>'bookkeeper',
						     });
    my %fields;			# JEP-1028 form fields to send back.
    if (my $node_text = $iq->get_tag('query')->attr('node')) {
	my @nodes = split(/\|/, $node_text);
	if (my $file = $self->get_file_with_url($nodes[0])) {
	    $identity->type('ui');
	    my $ruleset_uri = $file->ruleset_id->uri;
	    my @features = $file->features;
	    my @language_codes = $file->language_codes;
	    $fields{"client-type"} = [map($_->uri, @features)];
	    $fields{languages} = \@language_codes;
	    $fields{ruleset} = [$ruleset_uri];
	    $fields{reputation} = [$file->reputation || 0];
#	    $fields{"contact-email"} = [$file->player_id->email];
	    $fields{"contact-jid"} = [$file->player_id->jid];
	    $fields{description} = [$file->description];
							
	} elsif (my $ruleset = $self->get_ruleset_with_uri($nodes[0])) {
	    $fields{description} = [$ruleset->description];
	    $fields{name} = [$ruleset->name];
	    $fields{uri} = [$ruleset->uri];
	}
    } else {
	$identity->name('the volity.net bookkeeper');
	$fields{"volity-role"} = ["bookkeeper"];
    }
    
    push (@items, $identity);

    # Build the JEP-0128 form-field objects.
    my @fields;
    while (my($var, $values) = each(%fields)) {
	my $field = Volity::Jabber::Form::Field->new({var=>$var});
	$field->values(@$values);
	push (@fields, $field);
    }

    $self->send_disco_info({
	to=>$iq->attr('from'),
	id=>$iq->attr('id'),
	items=>\@items,
	fields=>\@fields,
    });

}

sub handle_disco_items_request {
    my $self = shift;
    my ($iq) = @_;
    $self->logger->debug("I got a disco items request.");
    my $query = $iq->get_tag('query');
    my @nodes;
    if (defined($query->attr('node'))) {
	@nodes = split(/\|/, $query->attr('node'));
    }
    
    my @items;			# Disco items to return to the requester.

    # The logic in the following section is dirty and yukky coz I dunno
    # what I'm doing yet.
    if (@nodes) {
	if ($nodes[0] eq 'rulesets') {
	    # 'rulesets' node requested, but nothing else.
	    # So let's return a list of all known rulesets.
	    # NOTE: This isn't a scalable solution!!
	    my @rulesets = Volity::Info::Ruleset->retrieve_all;
	    for my $ruleset (@rulesets) {
		push (@items, Volity::Jabber::Disco::Item->new({
		    jid=>$self->jid,
		    node=>$ruleset->uri,
		    name=>$ruleset->description,
		}),
		      );
	    }
	} elsif (my $ruleset = $self->get_ruleset_with_uri($nodes[0])) {
	    my $ruleset_uri = $ruleset->uri;
	    if ($nodes[1]) {
		if ($nodes[1] eq 'parlors') {
		    my @parlors = Volity::Info::Server->search({
			ruleset_id=>$ruleset->id,
		    });
		    for my $parlor (@parlors) {
			push (@items, Volity::Jabber::Disco::Item->new({
			    jid=>$parlor->jid . "/volity",
#			    jid=>$parlor->jid . "/testing",
			    name=>$parlor->jid,
			}),
			      );
		    }
		} elsif ($nodes[1] eq 'uis') {
		    my @files = Volity::Info::File->search({
			ruleset_id=>$ruleset->id,
		    });
		    for my $file (@files) {
			my $reputation = defined($file->reputation)?
			    $file->reputation : 0;
			my $pretty_name = "($reputation) " .
			    $file->name;
			$pretty_name .= ": " . $file->description
			    if defined($file->description);
			push (@items, Volity::Jabber::Disco::Item->new({
			    node=>$file->url,
			    jid=>$self->jid,
			    name=>$pretty_name,
			})
			      );
		    }
		} elsif ($nodes[1] eq 'lobby') {
		    my ($lobby_jid) = $self->lobby_jids_for_ruleset($ruleset);
		    push (@items,
			  Volity::Jabber::Disco::Item->new({
			      jid=>$lobby_jid,
			      name=>"Lobby for " . $ruleset->name . " discussion.",
			  }),
			  );
		} else {
		    # There should be a disco-error here.
		}
	    } else {
		push (@items, 
		      Volity::Jabber::Disco::Item->new({
			  jid=>$self->jid,
			  node=>"$ruleset_uri|parlors",
			  name=>"List of parlors for this game",
		      }),
		      Volity::Jabber::Disco::Item->new({
			  jid=>$self->jid,
			  node=>"$ruleset_uri|uis",
			  name=>"List of uis for this game",
		      }),
		      );
	    }
	} elsif (my $file = $self->get_file_with_url($nodes[0])) {
	    # Actually, in this case, no further items are generated.
	    # I'm leaving this inefficient elsif here to make things
	    # more explicit for myself; may drop later.
	}
	
    } else {
	# No nodes defined; this is a top-level request.
	push (@items, Volity::Jabber::Disco::Item->new({
	    jid=>$self->jid,
	    node=>"rulesets",
	    name=>"List of known Volity rulesets",
	})
	      );
    }

    $self->send_disco_items({to=>$iq->attr('from'),
			     id=>$iq->attr('id'),
			     items=>\@items});
}

# get_file_from_url: return a file object from the given URI, or undef.
sub get_file_with_url {
    my $self = shift;
    my ($url) = @_;
    my ($file) = Volity::Info::File->search({url=>$url});
    return $file;
}

sub get_ruleset_with_uri {
    my $self = shift;
    my ($uri) = @_;
    my ($ruleset) = Volity::Info::Ruleset->search({uri=>$uri});
    return $ruleset;
}
	
sub get_parlor_with_jid {
    my $self = shift;
    my ($jid) = @_;
    my ($parlor) = Volity::Info::Server->search({jid=>$jid});
    return $parlor;
}

# check_schedule: Called as the result of a time_to_check_the_shcedule POE event.
# Sees if any games are supposed to happen this minute, and sets things in
# motion for each one.
sub check_schedule {
    my $self = shift;
    $self->logger->debug("Checking the schedule.");
    for my $scheduled_game (Volity::Info::ScheduledGame->search_with_current_minute) {
#    for my $scheduled_game (Volity::Info::ScheduledGame->retrieve_all) {
	# Check for monkey business.
	unless (ref($scheduled_game->parlor_id) && $scheduled_game->parlor_id->isa("Volity::Info::Server")) {
	    $self->logger->error("The scheduled game with ID $scheduled_game doesn't appear to have a valid parlor_id.");
	    next;
	}

	$self->logger->debug("There is a scheduled game to take care of! Its ID is $scheduled_game.");

	# We will send a new_table RPC to this game's parlor.
	# But first, we'll set an alarm to error out politely if we don't get
	# a positive response back.

	my $rpc_id = "scheduled-game-" . $scheduled_game->id;

	my $alarm_id = $self->kernel->delay_set("new_table_rpc_timed_out", 
						$NEW_TABLE_TIMEOUT,
						$rpc_id,
						);

	# Remember the alarm ID, so we can cancel it later.
	$self->scheduled_game_info_by_rpc_id->{$rpc_id} = {
	    alarm_id       => $alarm_id,
	    scheduled_game => $scheduled_game,
	};

	# We're clear to send the RPC.
	$self->send_rpc_request({
	    id         => $rpc_id,
	    to         => $scheduled_game->parlor_id->jid . '/volity',
	    methodname => "volity.new_table",
	});
    }
}

####################
# RPC methods
####################

# For security reasons, all of these methods' names must start with "_rpc_".
# All these methods receive the following arguments:
# ($sender_jid, $rpc_id_attribute, @rpc_arguments)

####
# DB-Writing methods
####

# record_game: (That's 'record' as a verb, here.) Accept a game record and
# a signature as args. Use the sig to confirm that the record came from
# the parlor, and then store the record in the DB.
sub _rpc_record_game {
  my $self = shift;
  my ($sender_jid, $rpc_id, $game_record_hashref) = @_;
  
  unless (ref($game_record_hashref) && ref($game_record_hashref) eq 'HASH') {
      $self->logger->warn("Got a non-struct game record from $sender_jid.\n");
      return ('606', "Game record wasn't a struct");
  }

  my $game_record = Volity::GameRecord->new_from_hashref($game_record_hashref);
  unless (defined($game_record)) {
    $self->logger->warn("Got bad game struct from $sender_jid.\n");
    # XXX Error response here.
    return ('606', 'Bad game struct.');
  }

  # Looks good. Store it.
  return $self->store_record_in_db($sender_jid, $game_record);
}

# store_record_in_db: This must return a value like into an _rpc_* method.
sub store_record_in_db {
  my $self = shift;
  my ($referee_jid, $game_record) = @_;
  my ($parlor) = Volity::Info::Server->search({jid=>$game_record->parlor});
  unless ($parlor) {
      $self->logger->warn("Bizarre... got a record with parlor JID " . $game_record->parlor . ", but couldn't get a parlor object from the DB from it. No record stored.");
      return (608=>"Internal error: Failed to fetch the parlor with JID " . $game_record->parlor . " from internal database. No record stored.");
  }
  my ($ruleset) = Volity::Info::Ruleset->search({uri=>$game_record->game_uri});
  unless ($ruleset) {
      $self->logger->warn("Bizarre... got a record with ruleset URI " . $game_record->game_uri . ", but couldn't get a ruleset object from the DB from it. No record stored.");
      return (608=>"Internal error: Failed to fetch the ruleset with URI " . $game_record->game_uri . " from internal database. No record stored.");
  }
  my $game;			# Volity::Info::Game object
  if (defined($game_record->id)) {
      $game = Volity::Info::Game->retrieve($self->id);
  } else {
      # XXX NO UNDEFS ALLOWED IN TIMES. So, yeah, fix this.
      ($game) = Volity::Info::Game->search_unfinished_with_referee_jid($referee_jid);
      unless ($game) {
	  $self->logger->warn("Creating a NEW game record for a game that was just finished by the referee with JID $referee_jid. This is naughty; there should have already existed a record for this game.");
	  $game = Volity::Info::Game->create({
	      start_time  => $game_record->start_time || undef,
	      server_id   => $parlor->id,
	      ruleset_id  => $ruleset->id,
	      referee_jid => $referee_jid
	      });
      }
      $game->end_time($game_record->end_time || undef);
      $game->update;

      # XXX Is the game record's ID ever used, after we set it here?
      $game_record->id($game->id) unless $game_record->id;
  }

  # Winners list handling takes up the rest of this method...

  if ($game_record->seats) {
      return $self->record_winners($game_record, $game, $ruleset);
  }
  else {
      return $self->record_winners_legacy($game_record, $game, $ruleset);
  }
}

# record_winners: post-200602-style game record reading.
sub record_winners {
    my $self = shift;
    my ($game_record, $game, $ruleset) = @_;
    
    # First, make some seat objects, and stick em in a hash, keyed by
    # seat ID.
    # NOTE that I call seat IDs $seat_name in this code, to distinguish
    # the from database IDs.
    my %seats;
    unless (ref($game_record->seats) eq "HASH") {
        return ('606', 'Bad game struct: The value of "seats" must be a struct.');
    }
    for my $seat_name (keys(%{$game_record->seats})) {
        unless (ref($game_record->seats->{$seat_name}) eq "ARRAY") {
            warn "C";
            return ('606', "Bad game struct: The value of the '$seat_name' key under 'seats' must be an array.");
        }
        my @players = map(
                          Volity::Info::Player->find_or_create({jid=>$_}),
                          @{$game_record->seats->{$seat_name}}
                          );
        my ($seat) = Volity::Info::Seat->search_with_exact_players(@players);
        unless ($seat) {
            # This seat is brand new to me! Make some new DB records for it.
            $seat = Volity::Info::Seat->create({});
            for my $player (@players) {
                Volity::Info::PlayerSeat->create({seat_id=>$seat->id, player_id=>$player->id});
              }
        }
        $seats{$seat_name} = $seat;
    }

    # Now go over the place listings to see who scored what.
    unless (ref($game_record->{winners}) eq "ARRAY") {
        warn "D boppa " . ref($game_record->winners) . $game_record->winners;
        return ('606', "Bad game struct: The value of the 'winners' key must be an array.");
    }

    # First pass: build a hash that labels the seats with their rank at this
    # game.
    my %ranks;
    my $rank_number = 1;
    for my $place ($game_record->winners) {
        unless (ref($place) eq "ARRAY") {
            warn "A";
            return ('606', "Bad game struct: Each member of the 'winners' array must be an array.");
        }
        for my $seat_name (@$place) {
            $ranks{$seat_name} = $rank_number;
        }
        $rank_number++;
    }

    # Second pass: Calculate all the seats' new ELO ratings for this ruleset,
    # and write records to the database.
    my %new_ratings;            # Keys are seat names.
    for my $seat_name (keys(%ranks)) {
        my $my_rank = $ranks{$seat_name};
        my @other_seat_names = grep ($_ ne $seat_name, keys(%ranks));
        # Get the seats that beat this one.
        my @winning_seats = map($seats{$_}, grep($ranks{$_} < $my_rank, @other_seat_names));
        # Get the seats tied with this one.
        my @tied_seats = map($seats{$_}, grep($ranks{$_} == $my_rank, @other_seat_names));
        # Get the seats this one defeated.
        my @beaten_seats = map($seats{$_}, grep($ranks{$_} > $my_rank, @other_seat_names));
        
        my $last_rating = $seats{$seat_name}->current_rating_for_ruleset($ruleset);
        $new_ratings{$seat_name} ||= $last_rating;

        # Get this seat's 'K' rating, based on the number of games
        # they have played, of this ruleset.
        my $k_value;
        my $number_of_games_played = $seats{$seat_name}->number_of_games_played_for_ruleset($ruleset);
        if ($number_of_games_played < 20) {
            $k_value = 30;
        }
        else {
            $k_value = 15;
        }
        my $rating_delta = 0;
        for my $tied_seat (@tied_seats) {
            my $opponent_rating = $tied_seat->current_rating_for_ruleset($ruleset);
            $rating_delta += $k_value * (.5 - $self->get_rating_delta($last_rating, $opponent_rating));
        }
        for my $beaten_seat (@beaten_seats) {
            my $opponent_rating = $beaten_seat->current_rating_for_ruleset($ruleset);
            $rating_delta += $k_value * (1 - $self->get_rating_delta($last_rating, $opponent_rating));
        }	
        for my $winning_seat (@winning_seats) {
            my $opponent_rating = $winning_seat->current_rating_for_ruleset($ruleset);
            $rating_delta += $k_value * (0 - $self->get_rating_delta($last_rating, $opponent_rating));
        }
        $new_ratings{$seat_name} += $rating_delta;
    }

    # Third pass: Write to the DB.
    for my $seat_name (keys(%new_ratings)) {
        Volity::Info::GameSeat->create({seat_name=>$seat_name, rating=>$new_ratings{$seat_name}, place=>$ranks{$seat_name}, game_id=>$game, seat_id=>$seats{$seat_name}});
      }
    
    return "volity.ok";
    
}

# record_winners: pre-200602-style game record reading.
sub record_winners_legacy {
  my $self = shift;
  my ($game_record, $game, $ruleset) = @_;
  my $current_place = 1;	# Start with first place, work down.
  my @places = $game_record->winners;
#  my @seats_to_update;

  # First, we will convert the seat descriptions in the winners list
  # to actual seat objects.

  # A "seat description", here, is a list of player JIDs in a seat.

  for my $place_index (0..$#places) {
    my $place = $places[$place_index];
    my @seat_descriptions = @$place;
    my @seats; # Actual seat objects.
    for my $seat_description (@seat_descriptions) {
      # Get the DB object for this seat.
      # To do this, we have to get the player objects first.
      my @players;
      unless (ref($seat_description) eq "ARRAY") {
	  $self->logger->error("I just got a bad game record which didn't do the right things with seats.\nHere it is:\n" . Dumper($game_record));
	  return;
      }
      for my $player_jid (@$seat_description) {
	  my ($player) = Volity::Info::Player->find_or_create({jid=>$player_jid});
	  push (@players, $player);
      }
      my ($seat) = Volity::Info::Seat->search_with_exact_players(@players);
      unless ($seat) {
	  # This seat is brand new to me! Make some new DB records for it.
	  $seat = Volity::Info::Seat->create({});
	  for my $player (@players) {
	      Volity::Info::PlayerSeat->create({seat_id=>$seat->id, player_id=>$player->id});
	  }
      }
      push (@seats, $seat);
    }
    # Replace the seat descriptions with the real seat objects.
    $places[$place_index] = \@seats;
  }

  # That done, loop through the places a second time, creating the actual
  # result and ranking records (in the game_seat table).
  my %new_ratings;

  for my $place (@places) {
    my @seats = @$place;
    for my $seat (@seats) {
      # Record how this seat placed!
      my $game_seat = Volity::Info::GameSeat->
	  create({
              game_id=>$game->id,
              seat_id=>$seat->id,
          });
      $game_seat->place($current_place);
      $game_seat->update;

      # Figure out the seat's new ranking for this game.
      my $last_rating = $seat->current_rating_for_ruleset($ruleset);
      $new_ratings{$seat} ||= $last_rating;
      my @beaten_seats; my @tied_seats; # Volity::Info::Seat objects.
      my @winning_seats;	            # Ibid.
      # Get the seats that beat this one.
      foreach (1..($current_place - 1)) {
	my $index = $_ - 1;
	push (@winning_seats, @{$places[$index]});
      }      
      # Get the seats tied with this one.
      @tied_seats = grep($seat ne $_, @{$places[$current_place - 1]});
      # Get the seats this one defeated.
      foreach (($current_place + 1)..scalar(@places)) {
	my $index = $_ - 1;
	push (@beaten_seats, @{$places[$index]});
      }

      # Get this seat's 'K' rating, based on the number of games
      # they have played, of this ruleset.
      my $k_value;
      my $number_of_games_played = $seat->number_of_games_played_for_ruleset($ruleset);
      if ($number_of_games_played < 20) {
          $k_value = 30;
      }
      else {
          $k_value = 15;
      }
      my $rating_delta = 0;
      for my $tied_seat (@tied_seats) {
	my $opponent_rating = $tied_seat->current_rating_for_ruleset($ruleset);
	$rating_delta += $k_value * (.5 - $self->get_rating_delta($last_rating, $opponent_rating));
      }
      for my $beaten_seat (@beaten_seats) {
	my $opponent_rating = $beaten_seat->current_rating_for_ruleset($ruleset);
	$rating_delta += $k_value * (1 - $self->get_rating_delta($last_rating, $opponent_rating));
      }	
      for my $winning_seat (@winning_seats) {
	my $opponent_rating = $winning_seat->current_rating_for_ruleset($ruleset);
	$rating_delta += $k_value * (0 - $self->get_rating_delta($last_rating, $opponent_rating));
      }
      $new_ratings{$seat} += $rating_delta;
#      $game_seat->rating($new_rating);
#      push (@seats_to_update, $game_seat);
    }
    $current_place++;
  }
#  map($_->update, @seats_to_update);

  # Write the updates to the DB.
  for my $seat_id (keys(%new_ratings)) {
      for my $game_seat (Volity::Info::GameSeat->search(game_id=>$game, seat_id=>$seat_id)) {
          $game_seat->rating($new_ratings{$seat_id});
          $game_seat->update;
      }
      
  }

  return "volity.ok";			# This results in a volity.ok response.
}

sub get_rating_delta {
  my $self = shift;
  my ($current_rating, $opponent_rating) = @_;
  return 1 / (1 + (10 ** (($opponent_rating - $current_rating) / 400)));
}

# lobby_jids_for_ruleset: Return a list of JIDs of lobbies for the given
# ruleset object. Even though this always returns only one item right now,
# we return an array because it might be expanded later.
sub lobby_jids_for_ruleset {
    my $self = shift;
    my ($ruleset) = @_;
    my @lobby_jids;		# Return value
    # The lobby's JID is based on the ruleset URI.
    # First, transform said URI into JID-legal characters.
    my $jid_legal_uri = $ruleset->uri;
    $jid_legal_uri =~ tr/:\//;|/;
    my $lobby_jid = "$jid_legal_uri\@conference.volity.net";
    @lobby_jids = ($lobby_jid);
    return @lobby_jids;
}

##########################
# Other RPC handlers
##########################

sub _rpc_get_reputation {
    my $self = shift;
    my ($sender_jid, $rpc_id, $target_identifier) = @_;
}

sub _rpc_get_stance {
    my $self = shift;
    my ($sender_jid, $rpc_id, $target_identifier, $player_jid);
}

sub _rpc_get_stances {
    my $self = shift;
    my ($sender_jid, $rpc_id, $target_identifier) = @_;
}

sub _rpc_set_stance {
    my $self = shift;
    my ($sender_jid, $rpc_id, $target_identifier, $stance, $reason) = @_;
}

sub _rpc_get_rulesets {
    my $self = shift;
    my @uris = map($_->uri, Volity::Info::Ruleset->retrieve_all);
    return ("volity.ok", \@uris);
}

sub _rpc_get_ruleset_info {
    my $self = shift;
    my ($from_jid, $rpc_id, $uri) = @_;
    my $ruleset = Volity::Info::Ruleset->search(uri=>$uri);
    unless ($ruleset) {
	return ("volity.unknown_uri", "literal.$uri");
    }
    my %info_hash;
    foreach (qw(description name uri)) {
	$info_hash{$_} = $ruleset->$_;
    }
    return ["volity.ok", \%info_hash];
}

sub _rpc_get_uis {
    my $self = shift;
    my ($from_jid, $rpc_id, $uri, $constraints) = @_;
    $constraints ||= {};
    unless (ref($constraints eq 'HASH')) {
	return (606, "The second argument to get_uis(), if present, must be a struct.");
    }
    my $uri_object = URI->new($uri);
    my ($ruleset) = Volity::Info::Ruleset->search(uri => $uri);
    if ($ruleset) {
	my $version;
	if ($uri_object->frag) {
	    if ($constraints->{"ruleset-version"}) {
		return (606, "You cannot supply both a version-spec fragment in the URI and a separate ruleset-version constraint to a get_resources() call.");
	    }
	    $version = $uri_object->frag;
	}
	# This could be a complex statement, so we'll roll our own SQL.
	my @from = ("ui_file");
	my @where = ("ui_file.ruleset_id = ?");
	my @bind_values = ($ruleset->id);

	if ($constraints->{language}) {
	    push (@from, "ui_file_language");
	    push (@where, "ui_file_language.ui_file_id = ui_file.id");
	    push (@where, "ui_file_language.language_code = ?");
	    push (@bind_values, $constraints->{language});
	}
	      

	if ($version) {
	    push (@where, "version = ?");
	    push (@bind_values, $version);
	}

	if ($constraints->{reputation}) {
	    push (@where, "reputation >= ?");
	    push (@bind_values, $constraints->{reputation});
	}

	if ($constraints->{"ecmascript-api"}) {
	    push (@where, "ecmascript_api_version = ?");
	    push (@bind_values, $constraints->{"ecmascript-api"});
	}

	if ($constraints->{"client-type"}) {
	    push (@from, "ui_file_feature", "ui_feature");
	    push (@where, "ui_file_feature.ui_file_id = ui_file.id");
	    push (@where, "ui_file_feature.ui_feature_id = ui_feature.id");
	    push (@where, "ui_file_feature.uri = ?");
	    push (@bind_values, $constraints->{"client-type"});
	}

	my $sth = Volity::Info->db_Main->prepare_cached
	    (
	     "select distinct ui_file.url from " . 
	     join (", ", @from) .
	     " where " .
	     join(" and ", @where)
	     );
	$sth->execute(@bind_values);
	
	my @urls;
	while (my ($url) = $sth->fetchrow_array) {
	    push (@urls, $url);
	}

	return ["volity.ok", \@urls];

    }
    else {
	return ["volity.unknown_uri", "literal.$uri"];
    }

}

sub _rpc_get_ui_info {
    my $self = shift;
    my ($from_jid, $rpc_id, $urls) = @_;
    my @urls;
    my $format;			# How does the user want the return value?
    if (not(ref($urls))) {
	@urls = ($urls);
	$format = "scalar";
    }
    elsif (ref($urls) || ref($urls) eq 'ARRAY') {
	@urls = @$urls;
	$format = "list";
    }
    else {
	return (606, "The argument to get_ui_info() must be either a single URL or a list of URLs.");
    }
    my @info_hashes;
    for my $url (@urls) {
	my %info_hash;
	my $file = $self->get_file_with_url($url);
	unless ($file) {
	    return ["volity.unknown_url", "literal.$url"];
	}
	my $ruleset_uri = $file->ruleset_id->uri;
	my @features = $file->features;
	my @language_codes = $file->language_codes;
	$info_hash{"client-type"} = map($_->uri, @features);
	$info_hash{languages} = \@language_codes;
	$info_hash{ruleset} = $ruleset_uri;
	$info_hash{reputation} = $file->reputation || 0;
	$info_hash{"contact-jid"} = $file->player_id->jid;
	$info_hash{description} = $file->description;
	push (@info_hashes, \%info_hash);
    }

    if ($format eq "scalar") {
	return ["volity.ok", $info_hashes[0]];
    }
    else {
	return ["volity.ok", \@info_hashes];
    }
}

sub _rpc_get_lobbies {
    my $self = shift;
    my ($from_jid, $rpc_id, $uri) = @_;
    my ($ruleset) = Volity::Info::Ruleset->search(uri => $uri);
    if ($ruleset) {
	return ["volity.ok", [$self->lobby_jids_for_ruleset]];
    }
    else {
	return ["volity.unknown_uri", "literal.$uri"];
    }
}

sub _rpc_get_resource_uris {
    my $self = shift;
    my @resources = Volity::Info::Resource->retrieve_all;
    my @resource_uris = map($_->uri, @resources);
    return ["volity.ok", \@resource_uris];
}

sub _rpc_get_resources {
    my $self = shift;
    my ($from_jid, $rpc_id, $uri, $constraints) = @_;
    $constraints ||= {};
    unless (ref($constraints) eq 'HASH') {
	return (606, "The second argument to get_resources(), if present, must be a struct.");
    }
    my $uri_object = URI->new($uri);
    my ($resource_uri) = Volity::Info::ResourceURI->search(uri => $uri);
    if ($resource_uri) {
	my $version;
	if ($uri_object->fragment) {
	    if ($constraints->{"resource-version"}) {
		return (606, "You cannot supply both a version-spec fragment in the URI and a separate resource-version constraint to a get_resources() call.");
	    }
	    $version = $uri_object->fragment;
	}
	# This could be a complex statement, so we'll roll our own SQL.
	my $from;
	my @where = ("resource.resource_uri_id = ?");
	my @bind_values = ($resource_uri->id);
	if ($constraints->{language}) {
	    $from = "resource, resource_language";
	    push (@where, "resource_language.resource_id = resource.id");
	    push (@where, "resource_language.language_code = ?");
	    push (@bind_values, $constraints->{language});
	}
	else {
	    $from = "resource";
	}
	
	if ($version) {
	    push (@where, "version = ?");
	    push (@bind_values, $version);
	}

	if ($constraints->{reputation}) {
	    push (@where, "reputation >= ?");
	    push (@bind_values, $constraints->{reputation});
	}

	my $sth = Volity::Info->db_Main->prepare_cached("select distinct url from $from where " . join(" and ", @where));
	$sth->execute(@bind_values);
	
	my @urls;
	while (my ($url) = $sth->fetchrow_array) {
	    push (@urls, $url);
	}

	return ["volity.ok", \@urls];
    }
    else {
	return ["volity.unknown_uri", "literal.$uri"];
    }
}


sub _rpc_get_resource_info {
    my $self = shift;
    my ($from_jid, $rpc_id, $urls) = @_;

    my @urls;
    my $format;			# How does the user want the return value?
    if (not(ref($urls))) {
	@urls = ($urls);
	$format = "scalar";
    }
    elsif (ref($urls) || ref($urls) eq 'ARRAY') {
	@urls = @$urls;
	$format = "list";
    }
    else {
	return (606, "The argument to get_ui_info() must be either a single URL or a list of URLs.");
    }
    my @info_hashes;

    for my $url (@urls) {
	my ($resource) = Volity::Info::Resource->search(url=>$url);
	unless ($resource) {
	    return ["volity.unknown_url", "literal.$url"];
	}
	my %info_hash;
	my $resource_uri = $resource->resource_uri_id;
	my $player = $resource->player_id;
	unless ($resource_uri) {
	    return (608, "Internal error: couldn't find a resource URI for known reource at URL $url.");
	}
	unless ($player) {
	    return (608, "Internal error: couldn't find any contact info associated with the resource at URL $url.");
	}
	
	$info_hash{"provides-resource"} = $resource_uri->uri;
	$info_hash{reputation} = $resource->reputation;
	$info_hash{name} = $resource->name;
	$info_hash{languages} = [map($_->language_code, 
				     Volity::Info::ResourceLanguage->search
				     (
				      resource_id=>$resource,
				      )
				     )];
	$info_hash{description} = $resource->description,
	$info_hash{"contact-jid"} = $player->jid,
	push (@info_hashes, \%info_hash);
    }

    if ($format eq "scalar") {
	return ["volity.ok", $info_hashes[0]];
    }
    else {
	return ["volity.ok", \@info_hashes];
    }

}

sub _rpc_get_factories {
    my $self = shift;
    my ($from_jid, $rpc_id, $uri) = @_;
    my ($ruleset) = Volity::Info::Ruleset->search(uri => $uri);
    if ($ruleset) {
	return ["volity.ok", [map ($_->jid, $ruleset->factories)]];
    }
    else {
	return ["volity.unknown_uri", "literal.$uri"];
    }
}    


sub _rpc_prepare_game {
    my $self = shift;
    my ($from_jid, $rpc_id, $referee_jid, $is_newgame, $player_jid_list) = @_;
    my $basic_jid = $from_jid;
    $basic_jid =~ s|^(.*?)/.*$|$1|;
    my ($parlor) = Volity::Info::Server->search(jid=>$basic_jid);

    # Basic sanity-checking.
    unless ($parlor) {
	return (607, "You are not a parlor that I recognize! Sending JID: $from_jid. Basic JID: $basic_jid.");
    }
    unless (defined($is_newgame) && $player_jid_list) {
	return (604, "You must call volity.prepare_game with three arguments: the referee of this game, a new-game boolean, and a player JID list.");
    }
    unless (ref($player_jid_list) eq "ARRAY") {
	return (605, "The third argument to volity.prepare_game must be an array.");
    }
    
    # Is there a game record already in the DB? Should there be one?
    my $game_record;
    ($game_record) = Volity::Info::Game->search_unfinished_with_referee_jid($from_jid);
    
    if (not($is_newgame) && not($game_record)) {
	return ["game_record_missing"];
    }
    elsif ($is_newgame && $game_record) {
	return ["game_record_conflict"];
    }

    # Remember this parlor for later.
    $self->outstanding_prepare_game_responses_by_referee->{$referee_jid} = {
	parlor_db_object => $parlor,
	rpc_id           => $rpc_id,
    };

    my @unauthorized_players;
    my @players_to_ping;
    my %players_to_charge;
    for my $full_player_jid (@$player_jid_list) {
	my $player_jid = $full_player_jid;
	$player_jid =~ s|^(.*?)/.*$|$1|;
	
	my ($player) = Volity::Info::Player->search(jid=>$player_jid);
	unless ($player) {
	    return (606, "There is no player on the system with the JID '$player_jid'.");
	}
	my $credit_balance = $self->get_credit_balance_for_player($player);
	my $fee_to_play;
	my ($payment_status, $arg) = $self->get_payment_status_for_player_with_parlor($player, $parlor);

	$self->logger->debug("The payment status for this player is '$payment_status'.");
	if (defined($arg)) {
	    $self->logger->debug("The fee, in credits, will be $arg.");
	}
	else {
	    $self->logger->debug("No fee.");
	}
#	if ($payment_status eq "must_pay") {
	if ($payment_status eq "fee") {
	    if ($arg > $credit_balance) {
		$self->logger->debug("Oh, this player can't afford to pay, though.");
		# This player can't afford to play at this parlor.
		push (@unauthorized_players, $player->jid);
	    }
	    else {
		$fee_to_play = $arg;
		push (@players_to_ping, [$full_player_jid, $fee_to_play]);
		$players_to_charge{$player} = {player=>$player, credits=>$fee_to_play};
	    }
	}
#	elsif ($payment_status eq "not_subscribed") {
	elsif ($payment_status eq "noauth") {
	    # This parlor offers no pay-per-play, and this player
	    # is not subscribed. So, fooey on them.
            $self->logger->debug("Refusing to authorize a player, because the parlor offers no pay-per-play and the player is not subscribed.");
	    push (@unauthorized_players, $player->jid);
	}
	else {
	    # The request is sane, and the player has the right payment
	    # status. Send a ping their way.
	    push (@players_to_ping, [$full_player_jid, $arg]);
	}
    }
    
    if (@unauthorized_players) {
	return ["players_not_authorized", \@unauthorized_players];
    }

    
    $self->logger->debug("Players to ping: @players_to_ping");
    foreach (@players_to_ping) {
	my ($full_player_jid, $fee_to_play) = @$_;

	$fee_to_play ||= 0;
	$self->logger->debug("I'm about to tell the player with the JID $full_player_jid that they have to pony up $fee_to_play credits.");

	my $outgoing_rpc_id = "verify-game-" . $self->next_id;
	
	# First, set an alarm in case of timeout.
	my $alarm_id = $self->kernel->delay_set("verify_game_timeout", $VERIFY_GAME_TIMEOUT, $outgoing_rpc_id);
	
	# Store this for later.
	# Either the timeout alarm or the RPC result handler will use it.
	my %info_hash = 
	(
	    rpc_id      => $outgoing_rpc_id,
	    alarm_id    => $alarm_id,
	    player_jid  => $full_player_jid,
	    referee_jid => $referee_jid,
	);

	$self->{outstanding_verify_game_calls_by_rpc_id}->{$outgoing_rpc_id} = \%info_hash;

	$self->{outstanding_verify_game_calls_by_referee}->{$referee_jid}->{$full_player_jid} = \%info_hash;

	my @outgoing_rpc_args = ($referee_jid);
	if (defined($fee_to_play)) {
	    push (@outgoing_rpc_args, $fee_to_play);
	}
	$self->send_rpc_request({
	    id         => $outgoing_rpc_id,
	    to         => $full_player_jid,
	    methodname => "volity.verify_game",
	    args       => \@outgoing_rpc_args,
	});
    }

    # Twiddle thumbs until all the players report in.
    $self->logger->debug("Waiting for all pinged players to report back.");
    while ($self->get_outstanding_verify_game_calls($referee_jid)) {
	$self->kernel->run_one_timeslice();
    }
    $self->logger->debug("All pinged players have reported back.");

    # OK, all players we pinged are spoken for. Time to take action.
    # We need to send an RPC response to the parlor that made the call,
    # and we might need to make a new game record as well.

    my $rpc_response_value;
    my $info_hash = delete($self->outstanding_prepare_game_responses_by_referee->{$referee_jid});
    
    # Check for slackerly or refusing players at this table?
    if (my $slackers = $self->slacker_players_by_referee->{$referee_jid}) {
	$self->logger->debug("I will tell the parlor at $from_jid that there are players not responding.");
	$rpc_response_value = ["players_not_responding",
			       $slackers,
			       ];
    }
    elsif (my $refusers = $self->refusing_players_by_referee->{$referee_jid}) {
	$self->logger->debug("I will tell the parlor at $from_jid that there are players refusing.");
	$rpc_response_value = ["players_not_authorized",
			       $refusers,
			       ];
    }
    else {
	$self->logger->debug("All players approve of starting this game.");
	$rpc_response_value = RPC::XML::boolean->new("true");
    }

    # Now make a new game record, if there's need of one.
    my $game;
    unless (($game) = Volity::Info::Game->search_unfinished_with_referee_jid($referee_jid)) {
	my $start_time = DateTime::Format::MySQL->format_datetime(DateTime->now);
        # It's rather redundant to store the ruleset ID when the referee
        # is also getting stored, but for now we do this for historical
        # reasons.
	$game = Volity::Info::Game->create({
	    referee_jid => $referee_jid,
            ruleset_id  => $parlor->ruleset_id->id,
	    server_id   => $info_hash->{parlor_db_object},
	    start_time  => $start_time,
	});
    }

    # Charge the players' accounts as necessary.
    foreach (values(%players_to_charge)) {
	$self->charge_player_for_game($_->{player},
				       $game,
				       $_->{credits},
				       );
    }

    # And finally, return the RPC response value.
    return $rpc_response_value;
}


sub _rpc_game_player_authorized {
    my $self = shift;
    my ($from_jid, $rpc_id, $parlor_jid, $player_jid) = @_;
    
    my $basic_parlor_jid = $parlor_jid;
    my $basic_sender_jid = $from_jid;
    my $basic_player_jid = $player_jid;
    foreach ($basic_parlor_jid, $basic_sender_jid, $basic_player_jid) {
	s|^(.*?)/.*$|$1| if defined($_);
    }

    $self->logger->debug("I got a game_player_authorized call from $basic_sender_jid, a-wondering about how $basic_player_jid stands in the eyes of $basic_parlor_jid.");

    # For now, this call is allowed only from the player in question.
    # (if a player is specified).
    if (defined($player_jid)) {
	unless ($basic_sender_jid eq $basic_player_jid) {
	    return (607, "You don't have the authority to ask about the player with JID '$basic_player_jid'.");
	}
    }

    my ($parlor) = Volity::Info::Server->search(jid=>$basic_parlor_jid);

    unless ($parlor) {
	return (606, "$basic_parlor_jid not a parlor that I recognize! Sending JID: $from_jid. Basic JID: $basic_sender_jid.");
    }
    
    my ($player) = Volity::Info::Player->search(jid=>$basic_player_jid);
    unless ($player) {
	return (606, "There is no player on the system with the JID '$basic_player_jid'.");
    }
   
    my %return_hash;
    $return_hash{credits} = $self->get_credit_balance_for_player($player);

    ($return_hash{status}, $return_hash{fee}, $return_hash{options}) = $self->get_payment_status_for_player_with_parlor($player, $parlor);
    
    if ($return_hash{options}) {
	$return_hash{options} = RPC::XML::boolean->new("true");
    }
    else {
	$return_hash{options} = RPC::XML::boolean->new("false");
    }

    $return_hash{parlor} = $parlor_jid;
    # XXX This will need to be updated once we have a webpage.
    $return_hash{url} = $self->get_payment_url_for_parlor($parlor);

    return ["volity.ok", \%return_hash];

}

##################
# Credit balance fetching
##################

# get_credit_balance_for_player: Convenience method for getting both the 
# refundanble and nonrefundable balances for this player, summed together.
sub get_credit_balance_for_player {
    my $self = shift;
    my ($player) = @_;
    return $self->payment_object->get_credit_balance_for_player($player);
}

sub charge_player_for_game {
    my $self = shift;
    return $self->payment_object->charge_player_for_game(@_);
}

sub get_payment_url_for_parlor {
    my $self = shift;
    return $self->payment_object->get_payment_url_for_parlor(@_);
}

###############################
# CAUTION
###############################
# All the following methods (up until the POE stuff) are nonfunctional.
# I'm keeping them in the code because they _used_ to work, with my
# older DB model, and I want to rewrite them sometime.
###############################

sub _rpc_set_my_attitude_toward_player {
  my $self = shift;
  my ($sender_jid, $target_jid, $rank) = @_;
  $rank = int($rank);
  if ($rank < -1 or $rank > 1) {
    # XXX Error!
    return;
  }
  my $dbh = $self->dbh;
  $dbh->delete('PLAYER_ATTITUDE', {FROM_JID=>$sender_jid, TO_JID=>$target_jid});
  $dbh->insert('PLAYER_ATTITUDE', {FROM_JID=>$sender_jid, TO_JID=>$target_jid,
				   ATTITUDE=>$rank});
}

sub _rpc_get_my_attitude_toward_player {
  my $self = shift;
  my ($sender, $target) = @_;
  my $dbh = $self->dbh;
  $dbh->select('ATTITUDE', 'PLAYER_ATTITUDE', {FROM_JID=>$sender,
					       TO_JID=>$target});
  my ($att) = $dbh->fetchrow_array;
#  $self->send_rpc_response($sender, $id, $att);
}

# This method returns three scalars, with counts of -1, 0, and 1.
sub _rpc_get_all_attitudes_toward_player {
  my $self = shift;
  my ($sender, $target) = @_;
  my $dbh = $self->dbh;
  $dbh->select('ATTITUDE', 'PLAYER_ATTITUDE', {FROM_JID=>$sender,
					       TO_JID=>$target});
  my @atts = (0, 0, 0);
  while (my ($att) = $dbh->fetchrow_array) {
    $atts[$att + 1]++;
  }

#  $self->send_rpc_response($sender, $id, \@atts);
}

sub _rpc_get_all_game_records_for_player {
  my $self = shift;
  my ($sender_jid, $player_jid) = @_;
  my @game_records = $self->fetch_game_records_for_player($player_jid);
#  $self->send_rpc_response($sender_jid, $rpc_id_attr,
#			   [map($_->render_as_hashref, @game_records)]);
}

sub _rpc_get_game_records_for_player_and_game {
  my $self = shift;
  my ($sender_jid, $player_jid, $game_uri) = @_;
  my @game_records = $self->fetch_game_records_for_player($player_jid, {GAME_URI=>$game_uri});
#  $self->send_rpc_response($sender_jid, $rpc_id_attr,
#			   [map($_->render_as_hashref, @game_records)]);
}

sub fetch_game_records_for_player {
  my $self = shift;
  my ($player_jid, $where_args) = @_;
  my $dbh = $self->dbh;
  $where_args ||= {};
  $$where_args{'GAME_PLAYER.PLAYER_JID'} = $player_jid;
  $$where_args{'GAME_PLAYER.GAME_ID'} = 'GAME.ID';

  $dbh->select('ID', 'GAME, GAME_PLAYER', $where_args);
  my @game_records;
  while (my($id) = $dbh->fetchrow_array) { 
    push (@game_records, Volity::GameRecord->new({id=>$id}));
  }
  return @game_records;
}

sub _rpc_get_game_records_for_player_and_parlor {
  my $self = shift;
  my ($sender_jid, $player_jid, $parlor_jid) = @_;
  my @game_records = $self->fetch_game_records_for_player($player_jid, {SERVER_JID=>$parlor_jid});
#  $self->send_rpc_response($sender_jid, $rpc_id_attr,
#			   [map($_->render_as_hashref, @game_records)]);
}
  
sub _rpc_get_all_totals_for_player {
  my $self = shift;
  my ($sender, $player_jid) = @_;
  my %totals = $self->fetch_totals_for_player($player_jid);
#  $self->send_rpc_response($sender, $rpc_id, \%totals);
}

sub _rpc_get_all_totals_for_player_and_game {
  my $self = shift;
  my ($sender, $rpc_id, $player_jid, $game_uri) = @_;
  my %totals = $self->fetch_totals_for_player($player_jid, {GAME_URI=>$game_uri});
#  $self->send_rpc_response($sender, $rpc_id, \%totals);
}

sub _rpc_get_all_totals_for_player_and_parlor {
  my $self = shift;
  my ($sender, $player_jid, $parlor_jid) = @_;
  my %totals = $self->fetch_totals_for_player($player_jid, {SERVER_JID=>$parlor_jid});
#  $self->send_rpc_response($sender, $rpc_id, \%totals);
}
  
sub fetch_totals_for_player {
  my $self = shift;
  my ($player_jid, $where_args) = @_;
  my $dbh = $self->dbh;
  $where_args ||= {};
  $$where_args{'GAME_PLAYER.PLAYER_JID'} = $player_jid;
  $$where_args{'GAME_PLAYER.GAME_ID'} = 'GAME.ID';
  my %totals;

  $dbh->select('COUNT(ID)', 'GAME, GAME_PLAYER', $where_args);
  $totals{played} = ($dbh->fetchrow_array)[0];
  
  delete($$where_args{'GAME_PLAYER.PLAYER_JID'});
  delete($$where_args{'GAME_PLAYER.GAME_ID'});
  $$where_args{'GAME_WINNER.PLAYER_JID'} = $player_jid;
  $$where_args{'GAME_WINNER.GAME_ID'} = 'GAME.ID';

  $dbh->select('COUNT(ID)', 'GAME, GAME_WINNER', $where_args);
  $totals{won} = ($dbh->fetchrow_array)[0];

  delete($$where_args{'GAME_WINNER.PLAYER_JID'});
  delete($$where_args{'GAME_WINNER.GAME_ID'});
  $$where_args{'GAME_QUITTER.PLAYER_JID'} = $player_jid;
  $$where_args{'GAME_QUITTER.GAME_ID'} = 'GAME.ID';

  $dbh->select('COUNT(ID)', 'GAME, GAME_QUITTER', $where_args);
  $totals{quit} = ($dbh->fetchrow_array)[0];

  return %totals;
}

sub _rpc_get_urls_for_ruleset_and_client_type {
    my $self = shift;
    my ($sender, $ruleset_ui, $client_type) = @_;
    my ($ruleset) = Volity::Info::Ruleset->search({uri=>$ruleset_ui, type=>$client_type});
    my @files = Volity::Info::File->search({ruleset_id=>$ruleset});
    my @return_value;
    for my $file (@files) {
	my %info = (
		    url=>$file->url,
		    rating=>$file->rating || 0,
		    name=>$file->name,
		    description=>$file->description,
		    );
	push (@return_value, \%info);
    }
    return \@return_value;
}

####################
# Game verification and payment stuff
####################

# Possible return values:
# ("must_pay", $credit_amount)
# ("subscribed", $time)
# ("demo_timed", $time)
# ("demo_plays", $plays_left)
# ("free")
# XXX This could be sexier, using more efficient SQL. Right now I'll do it this
# XXX way coz it works.
sub get_payment_status_for_player_with_parlor {
    my $self = shift;
    my ($player, $parlor) = @_;
    return $self->payment_object->get_payment_status_for_player_with_parlor($player, $parlor);
}

# verify_game_timeout: This is a POE state that gets called if a player
# doesn't respond in a timely way to a volity.verify_game() RPC.
sub verify_game_timeout {
    my $self = shift;
    my ($rpc_id) = @_;

    # Get the info we stored about the original RPC call,
    # and clear this player from the list of people we're waiting to hear from.
    my ($info_hash) = delete($self->{outstanding_verify_game_calls_by_rpc_id});
    unless ($info_hash && ref($info_hash eq "HASH")) {
	$self->logger->warn("I got a timeout from a verify_game RPC with id $rpc_id, but I don't seem to remember that RPC anymore. Very strange.");
	return;
    }
    delete($self->{outstanding_verify_game_calls_by_referee}->{$info_hash->{referee_jid}}->{$info_hash->{player_jid}});

    # Remember this player as a slacker.
    $self->slacker_players_by_referee->{$info_hash->{referee_jid}} ||= [];
    push (@{$self->slacker_players_by_referee->{$info_hash->{referee_jid}}}, $info_hash->{player_jid});

}


# get_outstanding_verify_game_calls: Given a referee JID, return a list of
# info hashes describing volity.verify_game calls are are still outstanding.
sub get_outstanding_verify_game_calls {
    my $self = shift;
    my ($referee_jid) = @_;
    return values(%{$self->{outstanding_verify_game_calls_by_referee}->{$referee_jid}});
}

####################
# POE stuff
####################

# start: run the kernel.
sub start {
  my $self = shift;
  $self->kernel->run;
}

sub jabber_authed {
  my $self = $_[OBJECT];
  my $node = $_[ARG0];
  $self->logger->info("The bookkeeper has logged in!\n");
  $self->logger->info("Its JID: " . $self->jid);
  unless ($node->name eq 'handshake') {
#    warn $node->to_str;
  }
}

# A bunch of scheduling-related POE events follows.

# This event checks for new scheduled games, and then sets an alarm to
# call itself again in one minute.
sub time_to_check_the_schedule {
    my $self = shift;
    # Set this event to happen again in a minute.
    $self->kernel->delay_set("time_to_check_the_schedule", $SCHEDULED_GAME_CHECK_INTERVAL);
    # Actually check the schedule!
    $self->check_schedule;
}

# This one is called when a new_table RPC times out.
sub new_table_rpc_timed_out {
    my $self = $_[OBJECT];
    my $rpc_id = $_[ARG0];
    $self->logger->warn("Failed to get a response from a new_table RPC.");
    my $info = delete($self->scheduled_game_info_by_rpc_id->{$rpc_id});
    # Tell all the players the bad news.
    $self->apologize_to_players($info->{scheduled_game});
    $info->{scheduled_game}->delete;
}

# This is called when the bookkeeper got a response from new_table, but then
# finds that it can't actually join that new table.
sub new_table_join_attempt_timed_out {
    my $self = $_[OBJECT];
    my $muc_jid = $_[ARG0];
    $self->logger->warn("Failed to join the table at $muc_jid.");
    my $info = delete($self->scheduled_game_info_by_muc_jid->{$muc_jid});
    # Tell all the players the bad news.
    $self->apologize_to_players($info->{scheduled_game});   
    $info->{scheduled_game}->delete;
}

# This is called if none of the people invited to a table show up after
# a long time.
sub waiting_for_invitees_timed_out {
    my $self = $_[OBJECT];
    my $muc_jid = $_[ARG0];
    $self->logger->warn("None of the people invited to a table showed up.");
    my $info = delete($self->scheduled_game_info_by_muc_jid->{$muc_jid});
    $self->grumble_at_players($info->{scheduled_game});   
    $self->leave_muc($muc_jid);
    $info->{scheduled_game}->delete;
}

# apologize_to_players: Oh no, I couldn't make a table for a scheduled game.
# The players deserve to know what happened.
sub apologize_to_players {
    my $self = shift;
    my ($scheduled_game) = @_;
    my $time = $scheduled_game->time;
    for my $player ($scheduled_game->players) {
	$self->send_message({
	    to => $player->jid,
	    body => {
		en => "Hello, this is the Volity Network game scheduling service.\n\nYou were on the invitation list for a game that was supposed to start at $time (GMT), but something went wrong when I tried to start the game. Sorry!",
		es => "ÁHola!, este es el servicio de reservacion de la Red Volity. Usted estaba en la lista de invitacion para un juego que debia empezar a las $time (GMT), pero algo fallo cuando trate de empezar el juego. ÁLo siento!"
		}
	});
    }
}

# grumble_at_players: I made a game and nobody showed up for it.
# Whatever, guys.
sub grumble_at_players {
    my $self = shift;
    my ($scheduled_game) = @_;
    my $time = $scheduled_game->time;
    for my $player ($scheduled_game->players) {
	$self->send_message({
	    to => $player->jid,
	    body => {
		en => "Hello, this is the Volity Network game scheduling service.\n\nYou were on the invitation list for a game that was supposed to start at $time (GMT), but a long time went by and nobody showed up so I left. Sorry!",
		es => "ÁHola!, este es el servicio de reservacion de la Red Volity. Usted estaba en la lista de invitacion para un juego que debia empezar a las $time (GMT), pero paso el tiempo y nadie aparecio, por tanto me fui. ÁLo siento!",
		}
	});
    }
}

1;
