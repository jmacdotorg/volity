package Volity::Bot;

# A package for automated Volity players.
# Should be suitable for both 'ronin' and 'retainer' bots.

use warnings;
use strict;
use base qw(Volity::Player Class::Data::Inheritable);
use fields qw(muc_jid);
use Carp qw(croak);

foreach (qw(name description user host password)) {
  __PACKAGE__->mk_classdata($_);
}

# We override Volity::Jabber's join_muc to set our muc_jid variable.
sub join_muc {
  my $self = shift;
  my $muc_jid = $self->SUPER::join_muc(@_);
  $self->muc_jid($muc_jid);
}

# We override the constructor to perform some sanity checks, and
# insert additional config information based on class data.
sub new {
  my $invocant = shift;
  my $class = ref($invocant) || $invocant;
  my ($config) = @_;
  foreach (qw(user host password resource)) {
    if (defined($$config{$_})) {
      next;
    } else {
      unless (defined($class->$_)) {
	croak("The $class class doesn't have any $_ data defined!!");
      }
      $$config{$_} = $class->$_;
    }
  }
  return $class->SUPER::new($config);
}

1;

