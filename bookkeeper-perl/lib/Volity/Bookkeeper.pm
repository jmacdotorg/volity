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
use fields qw(dbh);

our $VERSION = '0.1';
use DBIx::Abstract;
use Carp qw(carp croak);

use Volity::GameRecord;
use Volity::Info::Player;
use Volity::Info::Game;

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
	   Component::Jabber;
	  );

sub initialize {
  my $self = shift;
  unless (defined($self->dbh)) {
    my $class = ref($self);
    croak("A new $class object must be initialized with a database handle!");
  }
  return $self->SUPER::initialize(@_);

}

####################
# Jabber event handlers
####################

# handle_rpc_request: Since this module will define a wide variety of RPC
# methods, instead of elsif-ing through a long list of possible request
# names, we will call only methods that begin with "_rpc_". This offers pretty
# good security, I think.
sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  my $method = "_rpc_" . $$rpc_info{method};
  if ($self->can($method)) {
    $self->$method($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
  } else {
    warn "I received a $$rpc_info{method} RPC request from $$rpc_info{from}, but I don't know what to do about it.\n";
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
# the server, and then store the record in the DB.
sub _rpc_record_game {
  my $self = shift;
  my ($sender_jid, $rpc_id_attr, $game_record_hashref) = @_;
  my $game_record = Volity::GameRecord->new_from_hashref($game_record_hashref->value);
  unless (defined($game_record)) {
    warn "Got bad game struct from $sender_jid.\n";
    # XXX Error response here.
    return;
  }

  # Verify the signature!
  unless ($game_record->verify) {
    warn "Uh oh... signature on game record doesn't seem to verify!!\n";
    # XXX Error response here.
    return;
  }

  # Looks good. Store it.
  $game_record->store_in_db($self->dbh);
}

sub _rpc_set_my_attitude_toward_player {
  my $self = shift;
  my ($sender_jid, $id, $target_jid, $rank) = @_;
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
  my ($sender, $id, $target) = @_;
  my $dbh = $self->dbh;
  $dbh->select('ATTITUDE', 'PLAYER_ATTITUDE', {FROM_JID=>$sender,
					       TO_JID=>$target});
  my ($att) = $dbh->fetchrow_array;
  $self->send_rpc_response($sender, $id, $att);
}

# This method returns three scalars, with counts of -1, 0, and 1.
sub _rpc_get_all_attitudes_toward_player {
  my $self = shift;
  my ($sender, $id, $target) = @_;
  my $dbh = $self->dbh;
  $dbh->select('ATTITUDE', 'PLAYER_ATTITUDE', {FROM_JID=>$sender,
					       TO_JID=>$target});
  my @atts = (0, 0, 0);
  while (my ($att) = $dbh->fetchrow_array) {
    $atts[$att + 1]++;
  }

  $self->send_rpc_response($sender, $id, \@atts);
}

sub _rpc_get_all_game_records_for_player {
  my $self = shift;
  my ($sender_jid, $rpc_id_attr, $player_jid) = @_;
  my @game_records = $self->fetch_game_records_for_player($player_jid);
  $self->send_rpc_response($sender_jid, $rpc_id_attr,
			   [map($_->render_as_hashref, @game_records)]);
}

sub _rpc_get_game_records_for_player_and_game {
  my $self = shift;
  my ($sender_jid, $rpc_id_attr, $player_jid, $game_uri) = @_;
  my @game_records = $self->fetch_game_records_for_player($player_jid, {GAME_URI=>$game_uri});
  $self->send_rpc_response($sender_jid, $rpc_id_attr,
			   [map($_->render_as_hashref, @game_records)]);
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
  my ($sender_jid, $rpc_id_attr, $player_jid, $server_jid) = @_;
  my @game_records = $self->fetch_game_records_for_player($player_jid, {SERVER_JID=>$server_jid});
  $self->send_rpc_response($sender_jid, $rpc_id_attr,
			   [map($_->render_as_hashref, @game_records)]);
}
  
sub _rpc_get_all_totals_for_player {
  my $self = shift;
  my ($sender, $rpc_id, $player_jid) = @_;
  my %totals = $self->fetch_totals_for_player($player_jid);
  $self->send_rpc_response($sender, $rpc_id, \%totals);
}

sub _rpc_get_all_totals_for_player_and_game {
  my $self = shift;
  my ($sender, $rpc_id, $player_jid, $game_uri) = @_;
  my %totals = $self->fetch_totals_for_player($player_jid, {GAME_URI=>$game_uri});
  $self->send_rpc_response($sender, $rpc_id, \%totals);
}

sub _rpc_get_all_totals_for_player_and_server {
  my $self = shift;
  my ($sender, $rpc_id, $player_jid, $server_jid) = @_;
  my %totals = $self->fetch_totals_for_player($player_jid, {SERVER_JID=>$server_jid});
  $self->send_rpc_response($sender, $rpc_id, \%totals);
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
  $self->debug("We have authed!\n");
  $self->debug("My jid: " . $self->jid);
  unless ($node->name eq 'handshake') {
    warn $node->to_str;
  }
}

########################
# Disco Stuff (I think??)
########################

# These methods cover stuff you'd find through service discovery (JEP-0030).

# servers_of_game: For a given game URI, return the JIDs of the servers that
# provide it.
sub servers_of_game {
  my $self = shift;
  my $dbh = $self->dbh;
  my ($game_uri) = @_;
  $dbh->select('JID', 'SERVER', {GAME_URI=>$game_uri});
  my @server_jids;
  while (my($jid) = $dbh->fetchrow_array) {
    push (@server_jids, $jid);
  }

  return @server_jids;
}

1;
