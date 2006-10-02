package Volity::PaymentSystem;

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

# Test whether the public API is satisfied.
#foreach (qw(get_balance_for_player get_payment_status_for_player_with_parlor)) {
#    my $class = __PACKAGE__;
#    unless ($class->can($_)) {
#	die ("Error: $class is not a valid subclass of Volity::PaymentSystem, since it does not defined a $_ method.\n");
#    }
#}

=head1 NAME

Volity::PaymentSystem - Base class for Volity's payment system

=head1 DESCRIPTION

Starting with version 0.7, the Volity bookkeeper defined by the
Volity::Bookkeeper class must run with some sort of payment system in mind. This system defines 

=head1 METHODS

This class defines a public API conatining only these class
methods. Subclasses I<must> define these class methods for themselves.

=over

=item get_balance_for_player ($player)

Given a Volity::Info::Player object, return the credit balance for
that player.

=item get_payment_status_for_player_with_parlor ($player, $parlor)

Given a Volity::Info::Player object and a Volity::Info::Server object,
return one or two scalars. The first is always a short code describing
this player's payment status with this parlor. If necessary, the
second scalar is an integer describing the fee, in credits, that the
player must pay (at minumum) to play this game.

=item charge_player ($player, $credits)

=item credit_player ($palyer, $credits)

=item get_payment_url_for_parlor ($parlor)

=back

=head1 SEE ALSO

=over

=item *

L<Volity::Bookkeeper>

=back

=head1 AUTHOR

Jason McIntosh <jmac@volity.com>

=head1 COPYRIGHT

Copyright (c) 2006 by Volity Games.

=cut

1;
