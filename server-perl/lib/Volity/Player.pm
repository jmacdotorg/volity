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

#use base qw(Class::Accessor::Fields);
use base qw(Volity);

use fields qw(jid name nick referee);

#Volity::Player->create_accessors;

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
#  warn "Gonna call the ui function $rpc_request_name with args @args.\n";
  $self->referee->send_rpc_request({
				    methodname=>$rpc_request_name,
				    to=>$self->jid,
				    args=>\@args,
				   });
}

1;
