package Volity;

our $VERSION = '0.2.2';

use warnings; use strict;
no warnings qw(deprecated);

use base qw(Class::Accessor Class::Fields);

sub new {
  my($proto, $fields) = @_;
  my($class) = ref $proto || $proto;

  if (defined($fields)) {
    croak("Second argument to constructor must be a hash reference!") unless ref($fields) eq 'HASH';
  } else {
    $fields = {};
  }

  my $self = fields::new($class);
  while (my ($key, $val) = each %$fields) {
    eval {$self->{$key} = $val;};
    if ($@) {
      Carp::confess "COuldn't set the $key key of $self: $@";
    }
  }
  $self->create_accessors;
  $self->initialize;
  return $self;
}

sub initialize {
  return $_[0];
}

sub create_accessors {
    my $self = shift;
    my $class = ref($self)? ref($self): $self;
    my @fields = $class->show_fields('Public');
    @fields = grep(not($self->can($_)), @fields);
    $class->mk_accessors(@fields);
}


# get: this overrides C::A's get() to act smarter about returning internal
# arrayrefs as lists of a lists is what we want.
sub get {
  my $self = shift;
  my ($field) = @_;
  if (wantarray) {
    if (defined($self->{$field}) and ref($self->{$field}) eq 'ARRAY') {
      return @{$self->SUPER::get(@_)};
    } else {
      return ($self->SUPER::get(@_));
    }
  }
  return $self->SUPER::get(@_);
}

=pod



=head1 NAME

Frivolity - A Perl implementation of the Volity game system

=head2 DESCRIPTION

All the modules under the Volity namespace implement I<Frivolity>, an
implementation of the Volity Internet game system. The C<Volity>
module I<per se> holds only this manpage; other modules implement
other system components, thus:

=over

=item Volity::Server

A "black-box" game server engine; tell it what game class to use
(probably a subclass of C<Volity::Game>), call C<start>, and off you
go.

=item Volity::Referee

A Volity referee is the entity that sits in a MUC (multi-user
conference) with some players, and arbitrates the game being played
therein.

Frivolity game designers may override this class if they wish. By
default, a Frivolity server will create base C<Volity::Referee>
objects.

=item Volity::Game

A framework library for creating Volity game modules. In frivolity terms, a game module is implemented as a Perl object class that inherits from C<Volity::Game>.

=item Volity::Bookkeeper

Another black-box module, this time for running a Volity bookkeeper,
which manages game records and the like by acting as a front-end to an
SQL database.

Unless you wish to run your own Volity network (as opposed to running
your games as part of the worldwide Volity network centered around
volity.net), you probably don't need to bother with this one.

=back

=head1 USAGE

Which modules you use, and how you use them, depends upon what you
want to do. That said, they all work on a similar principle: Creating
any of the objects listed above results in a new connection to a
Jabber server (whose address and authentication info you specify upon
construction), and from there they call various object methods when
different Jabber events are received. If you write your own
subclasses, you can define their behavior by overriding these callback
methods.

These callback methods are described in L<Volity::Jabber>. You should
read that manual page before diving into any of the more specific
Volity modules (as they all happen to inherit from C<Volity::Jabber>).

=head2 Creating new Volity games

Perl hackers who wish to write Volity game modules can easily do so by
creating an object class that inherits from C<Volity::Game>. Once
that's done, you can then actually make it available to the world
through C<Volity::Server>.

=head2 Hosting Volity games

As with any Volity implementation, you don't need to host any Internet
services of your own to host a game module; you simply need a valid
login to a Jabber server somewhere. With Frivolity, you pass this
login information to the C<<Volity::Server->new()>> method, and it
takes care of everything from there!

=head1 SEE ALSO

For more information about Volity from a hacker's point of view, see
the Volity hacker site at http://www.volity.org.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003 by Jason McIntosh.

=cut


1;
