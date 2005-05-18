package Volity::Player;

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

use base qw(Volity);

use fields qw(jid name nick referee rpc_count is_bot);

# basic_jid: Return the non-resource part of my JID.
sub basic_jid {
  my $self = shift;
  if (defined($self->jid) and $self->jid =~ /^(.*)\//) {
    return $1;
  }
  return undef;
}

# call_ui_function: Usually called by a game object. It tells us to
# pass along a UI function call to this player's client.
# We'll have the referee do the dirty work for us.
sub call_ui_function {
  my $self = shift;
  my ($function, @args) = @_;
  my $rpc_request_name = "game.$function";
  $self->referee->send_rpc_request({
                                    id=>$self->next_rpc_id,
				    methodname=>$rpc_request_name,
				    to=>$self->jid,
				    args=>\@args,
				   });
}

sub start_game {
  my $self = shift;
  $self->referee->send_rpc_request({
      id=>'start_game',
				    methodname=>'volity.start_game',
				    to=>$self->jid,
				   });
  $self->referee->logger->debug("Sent volity.start_game to " . $self->jid);
}

sub end_game {
  my $self = shift;
  $self->referee->send_rpc_request({
      id=>'end_game',
				    methodname=>'volity.end_game',
				    to=>$self->jid,
				   });
}

sub player_ready {
    my $self = shift;
    my ($other_player) = @_;
    my $jid = $other_player->jid;
    $self->referee->send_rpc_request({
	id=>'ready-announce',
	methodname=>'volity.player_ready',
	to=>$self->jid,
	args=>[$jid],
    });
}

sub player_stood {
    my $self = shift;
    my ($other_player) = @_;
    my $jid = $other_player->jid;
    $self->referee->send_rpc_request({
	id=>'ready',
	methodname=>'volity.player_stood',
	to=>$self->jid,
	args=>[$jid],
    });
}

sub player_sat {
    my $self = shift;
    my ($other_player) = @_;
    my $jid = $other_player->jid;
    $self->referee->send_rpc_request({
	id=>'ready',
	methodname=>'volity.player_sat',
	to=>$self->jid,
	args=>[$jid],
    });
}

sub player_unready {
    my $self = shift;
    my ($other_player) = @_;
    my $jid = $other_player->jid;
    $self->referee->send_rpc_request({
	id=>'unready',
	methodname=>'volity.player_unready',
	to=>$self->jid,
	args=>[$jid],
    });
}


# next_rpc_id: Simple method that returns a unique (for this object) RPC id.
sub next_rpc_id {
    my $self = shift;
    my $number;
    unless ($number = $self->rpc_count) {
	$number = $self->rpc_count(1);
    }
    $self->rpc_count($number++);
    return "player-$number";
}

1;
