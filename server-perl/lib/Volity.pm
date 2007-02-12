package Volity;

use warnings;
use strict;
no warnings qw( deprecated );
our $VERSION = '0.7.1';

use base qw( Class::Accessor Class::Fields );
use fields qw( logger );

use Log::Log4perl qw( :nowarn );
use Carp;

sub new {
    my ( $proto, $fields ) = @_;
    my ($class) = ref $proto || $proto;

    if ( defined($fields) ) {
        croak('Second argument to constructor must be a hash reference!')
            unless ref($fields) eq 'HASH';
    }
    else {
        $fields = {};
    }

    my $self = fields::new($class);
    $self->create_accessors;
    while ( my ( $key, $val ) = each %$fields ) {
        eval { $self->$key($val); };
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
    my $self   = shift;
    my $class  = ref($self) ? ref($self) : $self;
    my @fields = $class->show_fields('Public');
    @fields = grep( { not $self->can($_) } @fields );
    $class->mk_accessors(@fields);
}

# get: this overrides C::A's get() to act smarter about returning internal
# arrayrefs as lists of a lists is what we want.
sub get {
    my $self = shift;
    my ($field) = @_;
    if ( defined( $self->{$field} ) and ref( $self->{$field} ) eq 'ARRAY' ) {
	return @{ $self->SUPER::get(@_) };
    }
    elsif (wantarray) {
        if ( not( defined( $self->{$field} ) ) ) {
            return ();
        }
        else {
            return ( $self->SUPER::get(@_) );
        }
    }
    return $self->SUPER::get(@_);
}

# logger: Returns the Log::Log4perl logger object,
# creating it first if necessary.
sub logger {
    my $self = shift;
    unless ( $self->{logger} ) {
        $self->{logger} = Log::Log4perl::get_logger( ref($self) );
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

# report_rpc_error: This is a utility method that logs a verbose error about
# an RPC handling that went awry. The argument is the same hashref that's
# passed to Volity::Jabber::handle_rpc_request().
sub report_rpc_error {
    my $self = shift;
    my ($rpc_info) = @_;
    my @rpc_args = @{$rpc_info->{args}};
    $self->logger->error("***RPC ERROR*** I got a Perl error from handling an RPC.\nRPC info:\nFrom: $$rpc_info{from}\nID: $$rpc_info{id}\nMethod: $$rpc_info{methodname}\nArgs: @rpc_args\nPerl error: $@\n\n");
}

=pod



=head1 NAME

Frivolity - A Perl implementation of the Volity game platform

=head1 DESCRIPTION

All the modules under the Volity namespace implement I<Frivolity>, an
implementation of the Volity Internet game system. The C<Volity>
module I<per se> holds only a few utility methods (see
L<"METHODS">). More interesting are its subclasses, which implement
other key system components.

=head2 Modules of interest to game developers

If you are primarily interested in writing your own Volity games in
Perl, then you really only need a deep understanding of the following
modules.

=over

=item Volity::Game

A framework library for creating Volity game modules. You can make
your own Volity-ready game modules by subclassing C<Volity::Game>.

L<Volity::Game> is a great starting point for learning how to create
your own Volity games with these libraries.

=item Volity::Seat

The base class for the entities that are actually playing a volity
game. Each discrete, role-bearing entity at the game table -- "black"
and "white" at Chess, or "North", "East", "West" and "South" at
Bridge, for example --is represented by an object of this class.

Creation and destruction of seat objects is handled for you, but
several methods of Volity::Game (and the subclasses you might make
from it) return seat objects, so you should know how to work with
them.

While the C<Volity::Seat> base class is full-featured enough to use
as-is, you have the option of subclassing C<Volity::Seat> and
configuring your game subclass to use it instead of the base class.

=item Volity::Player

A class that defines individual Volity users from a server-side
perspective. Active seats contain players, and game observers -- that
is, users at a table but not sitting down -- are also defined by this
class.

As with seats, creation and destruction of player objects is handled
for you, but several methods of both C<Volity::Game> and C<Volity::Seat>
deal with objects of this class.

=item Volity::WinnersList

This small but important class defines an object that will help you
create a bookkeeper-ready game record once each of your games is
finished being played.

=item Volity::Bot

A framework library for creating bots, game-playing automatons that
users can summon to a Volity table as artificial competition.

Writing bots is a recommended but not completely necessary part of
creating a Volity game. Therefore, L<Volity::Bot> should be your next
step in the process of game creation with Frivolity once you've gotten
your head around C<Volity::Game> and the other modules listed above.

=back

=head2 Modules of interest to deep-voodoo wizards

Should you wish to hack deeper into the system, there's plenty more to
see. All of these modules are subclasses of C<Volity::Jabber>.

=over

=item Volity::Server

A "black-box" game server engine; tell it what game class to use (probably a
subclass of C<Volity::Game>), call C<start>, and off you go.

The C<volityd> program, which is distributed with these libraries,
takes care of setting up and creating this object for you. See
L<volityd>.

=item Volity::Referee

A Volity referee is the entity that sits in a MUC (multi-user
conference) with some players, and arbitrates the game being played
therein. 

In this implementation, the C<Volity::Referee> object takes care of
all referee activities common to all Volity games, and delegates
game-specific logic to a C<Volity::Game> subclass object that it
contains. Developers using these modules can therefore put all their
programming work into this subclass and generally leave the referee
class alone.

=item Volity::Bookkeeper

Another black-box module, this time for running a Volity bookkeeper,
which manages game records and the like by acting as a front-end to a
database.

Unless you wish to run your own Volity network (as opposed to running your
games as part of the worldwide Volity network centered around volity.net),
you probably don't need to bother with this one.

Due to its separate dependencies, such as the need for a MySQL
database, this module is distributed separately from the rest of
Frivolity.

=back

=head1 USAGE

=head2 Creating new Volity games

Perl hackers who wish to write Volity game modules can easily do so by
creating an object class that inherits from C<Volity::Game>, and
optionally an automated opponent through a C<Volity::Bot>
subclass. See those modules' documentation for more information.

=head2 Running a Volity parlor

As with any Volity implementation, you don't need to host any Internet
services of your own to host a game module; you simply need a valid login to
a Jabber server somewhere. Frivolity includes a Perl program, C<volityd>,
that creates a Volity server for you, using a C<Volity::Game> subclass that
you provide it. See L<volityd> for more information.

Fully hooking a parlor into a greater Volity network (such as
volity.net) involves registering it with that network's
bookkeeper. Refer to
http://www.volity.org/wiki/index.cgi?Game_Developer's_Overview for
information on registering with volity.net.

=head1 METHODS

The following object methods are available to instances of all the
classes listed in L<"DESCRIPTION">.

=over

=item logger

Returns the object's attached Log::Log4perl object, which is automatically
created and initialized as needed. This lets Volity subclasses easily add
prioritized log and debug output to their code. See L<Log::Log4perl> for
documentation on this object's use.

You tell the C<volityd> program about a Log4perl-style config file to
use through its B<log_config> config option. See L<volityd> for
details.

=item expire ($message)

A convenience method which calls the logger object's C<fatal> method using
the given C<$message>, and then calls C<Carp:croak()> with the same message.

=back

=head1 SEE ALSO

The Volity developers' website at http://www.volity.org contains all
sorts of resources for developers, including a documentation wiki,
links to mailing lists, and client software downloads.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

Jabber ID: jmac@volity.net

=head1 COPYRIGHT

Copyright (c) 2003-2006 by Jason McIntosh.

=cut

1;
