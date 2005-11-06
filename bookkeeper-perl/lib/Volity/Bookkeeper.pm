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

use base qw(Volity::Jabber);

our $VERSION = '0.5.2';
use Carp qw(carp croak);

use Volity::GameRecord;
use Volity::Info::Server;
use Volity::Info::Game;
use Volity::Info::Player;
use Volity::Info::File;

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
	   Component::Jabber;
	  );

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
    if ($self->can($method)) {
      my @response = $self->$method($$rpc_info{from}, @{$$rpc_info{args}});
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
	  # We have silently approved the request,
	  # so send back a minimal positive response.
	  $self->send_rpc_response($$rpc_info{from}, $$rpc_info{id}, ["volity.ok"]);
      }
    } else {
      $self->logger->warn("I received a $$rpc_info{method} RPC request from $$rpc_info{from}, but I don't know what to do about it.\n");
    }
  } else {
    $self->logger->warn("Received a $$rpc_info{method} RPC request; it's not in the volity namespace, so I'm ignoring it.");
  }
}

# This presence handler takes care of auto-approving all subscription
# requests. Volity servers are very social like that.
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
	    $fields{"contact-email"} = [$file->player_id->email];
	    $fields{"contact-jid"} = [$file->player_id->jid];
	    $fields{description} = [$file->description];
							
	} elsif (my $ruleset = $self->get_ruleset_with_uri($nodes[0])) {
	    $fields{description} = [$ruleset->description];
	    $fields{name} = [$ruleset->name];
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
		if ($nodes[1] eq 'servers') {
		    my @servers = Volity::Info::Server->search({
			ruleset_id=>$ruleset->id,
		    });
		    for my $server (@servers) {
			push (@items, Volity::Jabber::Disco::Item->new({
			    jid=>$server->jid . "/volity",
#			    jid=>$server->jid . "/testing",
			    name=>$server->jid,
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
		    # The lobby's JID is based on the ruleset URI.
		    # First, transform said URI into JID-legal characters.
		    my $jid_legal_uri = $ruleset->uri;
		    $jid_legal_uri =~ tr/:\//;|/;
		    my $lobby_jid = "$jid_legal_uri\@conference.volity.net";
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
			  node=>"$ruleset_uri|servers",
			  name=>"List of servers for this game",
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
	
sub get_server_with_jid {
    my $self = shift;
    my ($jid) = @_;
    my ($server) = Volity::Info::Server->search({jid=>$jid});
    return $server;
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
# the server, and then store the record in the DB.
sub _rpc_record_game {
  my $self = shift;
  my ($sender_jid, $game_record_hashref) = @_;
  my $game_record = Volity::GameRecord->new_from_hashref($game_record_hashref);
  unless (defined($game_record)) {
    $self->logger->warn("Got bad game struct from $sender_jid.\n");
    # XXX Error response here.
    return ('606', 'Bad game struct.');
  }

  # Verify the signature!
  # XXX ...or don't. I'm hobbling this action while the whole notion of game
  # records enters a probationary period.
#  unless ($game_record->verify) {
#    $self->logger->warn("Uh oh... signature on game record doesn't seem to verify!!\n");
#    # XXX Error response here.
#    return ('999', 'Bad signature.');
#  }

  # Looks good. Store it.
  return $self->store_record_in_db($game_record);
}

# store_record_in_db: This must return a value like into an _rpc_* method.
sub store_record_in_db {
  my $self = shift;
  my ($game_record) = @_;
  use Data::Dumper; warn Dumper($game_record);
  my ($server) = Volity::Info::Server->search({jid=>$game_record->parlor});
  unless ($server) {
      $self->logger->warn("Bizarre... got a record with parlor JID " . $game_record->parlor . ", but couldn't get a parlor object from the DB from it. No record stored.");
      return (608=>"Internal error: Failed to fetch the parlor with JID " . $game_record->server . " from internal database. No record stored.");
  }
  my ($ruleset) = Volity::Info::Ruleset->search({uri=>$game_record->game_uri});
  unless ($ruleset) {
      $self->logger->warn("Bizarre... got a record with ruleset URI " . $game_record->game_uri . ", but couldn't get a ruleset object from the DB from it. No record stored.");
      return (608=>"Internal error: Failed to fetch the ruleset with URI " . $game_record->game_uri . " from internal database. No record stored.");
  }
  my $game;			# Volity::Info::Game object
  if (defined($game_record->id)) {
    $game = Volity::Info::Game->retrieve($self->id);
    # XXX Confirm that the record's owner is legit.
    # XXX Do other magic here to update the values.
    # XXX This is all kinds of not implemented yet.
  } else {
    $game = Volity::Info::Game->create({start_time=>$game_record->start_time || undef,
					end_time=>$game_record->end_time || undef,
					server_id=>$server->id,
					ruleset_id=>$ruleset->id,
#					signature=>$game_record->signature,
				       });
					
    $game_record->id($game->id);
  }

  # Winners list handling takes of the rest of this method...
  my $current_place = 1;	# Start with first place, work down.
  my @places = $game_record->winners;
  my @seats_to_update;

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

  for my $place (@places) {
    my @seats = @$place;
    for my $seat (@seats) {
      # Record how this seat placed!
      my $game_seat = Volity::Info::GameSeat->
	  find_or_create({
			  game_id=>$game->id,
			  seat_id=>$seat->id,
			 });
      $game_seat->place($current_place);
      

      # Figure out the seat's new ranking for this game.
      my $last_rating = $seat->current_rating_for_ruleset($ruleset);
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
      my $number_of_games_played = $seat->number_of_games_played_for_ruleset($ruleset);
      my $k_delta = int($number_of_games_played / 50) * 5;
      $k_delta = 20 if $k_delta > 20;
      my $k_value = 30 - $k_delta;
      my $rating_delta = 0;
      for my $tied_seat (@tied_seats) {
	my $opponent_rating = $tied_seat->current_rating_for_ruleset($ruleset);
	$rating_delta += $k_value * (.5 - $self->get_rating_delta($last_rating, $opponent_rating, $k_value));
      }
      for my $beaten_seat (@beaten_seats) {
	my $opponent_rating = $beaten_seat->current_rating_for_ruleset($ruleset);
	$rating_delta += $k_value * (1 - $self->get_rating_delta($last_rating, $opponent_rating, $k_value));
      }	
      for my $winning_seat (@winning_seats) {
	my $opponent_rating = $winning_seat->current_rating_for_ruleset($ruleset);
	$rating_delta += $k_value * (0 - $self->get_rating_delta($last_rating, $opponent_rating, $k_value));
      }
      my $new_rating = $last_rating + $rating_delta;
      $game_seat->rating($new_rating);
      push (@seats_to_update, $game_seat);
#      $game_seat->update;
    }
    $current_place++;
  }
  map($_->update, @seats_to_update);
  return;			# This results in a volity.ok response.
}

sub get_rating_delta {
  my $self = shift;
  my ($current_rating, $opponent_rating) = @_;
  return 1 / (1 + (10 ^ (abs($current_rating - $opponent_rating) / 400)));
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

sub _rpc_get_game_records_for_player_and_server {
  my $self = shift;
  my ($sender_jid, $player_jid, $server_jid) = @_;
  my @game_records = $self->fetch_game_records_for_player($player_jid, {SERVER_JID=>$server_jid});
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

sub _rpc_get_all_totals_for_player_and_server {
  my $self = shift;
  my ($sender, $player_jid, $server_jid) = @_;
  my %totals = $self->fetch_totals_for_player($player_jid, {SERVER_JID=>$server_jid});
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

1;
