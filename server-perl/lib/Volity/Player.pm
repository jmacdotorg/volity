package Volity::Player;

use warnings;
use strict;

use base qw(Class::Accessor::Fields);
use fields qw(jid name nick);

#Volity::Player->create_accessors;

# basic_jid: Return the non-resource part of my JID.
sub basic_jid {
  my $self = shift;
  if (defined($self->jid) and $self->jid =~ /^(.*)\//) {
    return $1;
  }
  return undef;
}


1;
