package Volity::Info;

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

use base qw(Class::DBI);

# These use statements laod all the table-classes at once.
use Volity::Info::Player;
use Volity::Info::Server;
use Volity::Info::Ruleset;
use Volity::Info::File;
use Volity::Info::Feature;
use Volity::Info::FileFeature;
use Volity::Info::Game;
use Volity::Info::Seat;
use Volity::Info::PlayerSeat;
use Volity::Info::GameSeat;

1;
