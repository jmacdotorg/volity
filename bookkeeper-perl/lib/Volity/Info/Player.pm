package Volity::Info::Player;

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

sub initialize {
  my $self = shift;
  $self->SUPER::initialize(@_);
  unless (defined($self->jid)) {
    croak ("You must initialize a player-info object with a JID!");
  }
}

Volity::Info::Player->table('player');
Volity::Info::Player->columns(All=>qw(id jid name email));
Volity::Info::Player->has_many(games=>["Volity::Info::GamePlayer" => 'game_id'], 'player_id');
Volity::Info::Player->has_many(wins=>["Volity::Info::GameWinner" => 'game_id'], 'player_id');
Volity::Info::Player->has_many(quits=>["Volity::Info::GameQuitter" => 'game_id'], 'player_id');

1;
