package Volity::Info::Game;

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

use base qw(Volity::Info);

Volity::Info::Game->table('game');
Volity::Info::Game->columns(All=>qw(id start_time end_time server_id signature ruleset_id));
Volity::Info::Game->has_a(ruleset_id=>"Volity::Info::Ruleset");
Volity::Info::Game->has_a(server_id=>"Volity::Info::Server");
Volity::Info::Game->has_many(players=>["Volity::Info::GamePlayer" => 'player_id'], 'game_id');
Volity::Info::Game->has_many(quitters=>["Volity::Info::GameQuitter" => 'player_id'], 'game_id');

sub uri {
  my $self = shift;
  return $self->ruleset_id->uri;
}

sub name {
  my $self = shift;
  return $self->uri_id->name;
}

sub description {
  my $self = shift;
  return $self->uri_id->description;
}

1;
