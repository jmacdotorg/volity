package Volity;

our $VERSION = '0.4.1';

use warnings; use strict;
no warnings qw(deprecated);

use base qw(Class::Accessor Class::Fields);
use fields qw(logger);
use Log::Log4perl qw(:nowarn);

use Carp;

sub new {
  my($proto, $fields) = @_;
  my($class) = ref $proto || $proto;

  if (defined($fields)) {
    croak("Second argument to constructor must be a hash reference!") unless ref($fields) eq 'HASH';
  } else {
    $fields = {};
  }

  my $self = fields::new($class);
  $self->create_accessors;
  while (my ($key, $val) = each %$fields) {
      eval {$self->$key($val);};
    if ($@) {
      Carp::confess "Couldn't set the $key key of $self: $@";
    }
  }
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
    } elsif (not(defined($self->{$field}))) {
      return ();
    } else {
      return ($self->SUPER::get(@_));
    }
  }
  return $self->SUPER::get(@_);
}

# logger: Returns the Log::Log4perl logger object,
# creating it first if necessary.
sub logger {
  my $self = shift;
  unless ($self->{logger}) {
    $self->{logger} = Log::Log4perl::get_logger(ref($self));
  }
  return $self->{logger};
}

# expire: Log a fatal error message, and then die with that same message.
sub expire {
  my $self = shift;
  my ($last_words) = @_;
  $self->logger->fatal($last_words);
  Carp::confess($last_words);
}


=pod



=head1 NAME

Frivolity - A Perl implementation of the Volity game system

=head2 DESCRIPTION

All the modules under the Volity namespace implement I<Frivolity>, an
implementation of the Volity Internet game system. The C<Volity>
module I<per se> holds only a few utility methods (see
L<"METHODS">). More interesting are its subclasses, which implement
other key system components, thus:

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
creating an object class that inherits from C<Volity::Game>, and
optionally an automated opponent through a C<Volity::Bot>
subclass.

Detailed information about creating new Volity games with these
modules can be found in the Volity Developer's Guide, at
<http://www.volity.org/docs/devguide_perl/>.

=head2 Hosting Volity games

As with any Volity implementation, you don't need to host any Internet
services of your own to host a game module; you simply need a valid
login to a Jabber server somewhere. Frivolity includes a Perl program,
C<volityd>, that creates a Volity server for you, using a
C<Volity::Game> subclass that you provide it. See the volityd manpage
for more information.

=head1 METHODS

First of all, see L<Class::Accessor>, from which this module inherits.

In addition, the following object methods are, available to instances
of all the classes listed in L<"DESCRIPTION">.

=over

=item logger

Returns the object's attached Log::Log4perl object, whcih is
automatically created and initialized as needed. This lets Volity
subclasses easily add prioritized log and debug output to their
code. See L<Log::Log4perl> for documentation on this object's use.

=item expire ($message)

A convenience method which calls the logger object's C<fatal> method
using the given C<$message>, and then calls C<Carp:croak()> with the same
message.

=back

=head1 SEE ALSO

For more information about Volity from a hacker's point of view, see
the Volity developers' website at http://www.volity.org.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org> 
Jabber ID: jmac@volity.net

=head1 COPYRIGHT

Copyright (c) 2003-2004 by Jason McIntosh.

=cut


1;
