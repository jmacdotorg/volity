package Volity::WebClient::User;

use warnings;
use strict;

use base qw(Volity::Jabber);

1;


=head1 NAME

Volity::WebClient::User - A single player-connection to the Jabber network.

=head1 DESCRIPTION

Objects of this class are sublcasses of Volity::Jabber, and therefore
represent individual connections to a Jabber network. They're intended
for use with the Volity web client software, and are usually found
within the embrace of a Volity::WebClient container object.

Invocations of this class probably won't occur outside of the
Volity::WebClient software itself.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2007 by Jason McIntosh.



