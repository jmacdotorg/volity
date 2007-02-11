package Volity::PaymentSystem::Free;

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

use base qw(Volity::PaymentSystem);

sub get_credit_balance_for_player {
    return 0;
}

sub get_payment_status_for_player_with_parlor {
    return "free";
}

=head1 NAME

Volity::PaymentSystem::Free - Default class for Volity's payment system

=head1 DESCRIPTION

This suclass of Volity::PaymentSystem simply allows all players to
play all parlors for free, and treats all players as having an account
balance of 0. 

The C<bookkeeper.pl> script distributed with the Volity::Bookkeeper
module uses this class as the default payment system class. It allows
one to run a Volity::Bookkeeper instance that is not hooked into any
kind of payment system.

=head1 SEE ALSO

=over

=item *

L<Volity::PaymentSystem>

=back

=head1 AUTHOR

Jason McIntosh <jmac@volity.com>

=head1 COPYRIGHT

Copyright (c) 2006 by Volity Games.

=cut

1;
