package Volity::GameRecord;

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

=head1 NAME

Volity::GameRecord - Information about a completed Volity game.

=head1 SYNOPSIS

use Volity::GameRecord;

=head1 DESCRIPTION

An object of this class represents an information record about. In
practical terms, it's an abstraction of the information that a game
referee sends to its Volity network's bookkeeper once a game has
finished. Through the methods this class provides, it's easy for a
parlor to build and sign this RPC call, and just as easy for the
receiving bookkeeper.

Note that, since RPC is the neutral middle ground, a Frivolity game
parlor (a.k.a. a Perl object of a Volity::Game::Server subclass) can
pass a Volity::GameRecord object to any Volity bookkeeper, regardless
of its platform. Similarly, A Volity::Bookkeeper object can knit a
Volity::Gamerecord object out of any game parlor's RPC request,
whether or not that parlor runs Frivolity. (In reality, this latter
situation will probably be quite common.)

=head1 USAGE

Game module designers don't have to worry about using this module at
all. Records are automatically created by Volity::Referee objects when
a game wraps up, and incoming records are automatically parsed by the
bookkeeper. Your main responsibility with game record handling
involves the winners list, available through the C<winners()> method
of your C<Volity::Game> subclass and manipulatable through the methods
described in L<Volity::WinnersList>.

The following documentation is really here for completeness' sake, but
those wishing to modify the Frivolity referee or bookkeeper behavior
might find it interesting.

=cut

use warnings;
use strict;

use URI;
use Carp qw( croak carp );
use Date::Parse;
use Date::Format;

use base qw( Volity );
use fields qw( id signature winners start_time end_time game_uri_object
    game_name parlor finished seats );

# Set up package variables for GPG config.
our ( $gpg_bin, $gpg_secretkey, $gpg_passphrase );

########################
# Special Constructors (Class methods)
########################

=head1 METHODS

=head2 Class methods (constructors)

=over

=item new_from_hashref($hashref)

Creates a new object based on the given hash reference, typically one that
has been freshly translated from an RPC E<lt>structE<gt> argument.

For the opposite functionality, see C<render_into_hashref> under L<"Object
Methods">.

=back

=head2 Object accessors

All these are simple accessors which return the named object attribute. If
an argument is passed in, then the attribute's value is first set to that
argument.

In the case of lists, either an array or an array reference is returned,
depending upon context.

This module inherits from Class::Accessor, so all the tips and tricks
detailed in L<Class::Accessor> apply here as well.

=over

=item id

=item signature

=item winners

=item start_time

=item end_time

=item game_uri

=item game_name

=item parlor

=item finished

=back

=cut

######################
# Object methods
######################

=head2 Object methods

=over

=cut

########################
# Special Accessors
########################

# Most accessors are automatically defined by Class::Accessors::Fields.

sub game_uri {
    my $self = shift;

    # We store URI-class objects, and return stringy-dings.
    # You can pass in either URI objects or strings.
    if ( exists( $_[0] ) ) {
        if ( defined( ref( $_[0] ) ) and ref( $_[0] ) eq 'URI' ) {
            $self->game_uri_object(@_);
        }
        elsif ( not( ref( $_[0] ) ) ) {
            my $uri = URI->new( $_[0] );
            unless ( defined($uri) ) {
                croak(
"The game_uri method thinks that this doesn't look like a URI: $_[0]"
                    );
            }
            $self->game_uri_object($uri);
        }
        else {
            croak(
"You must call game_uri() with either a URI string, or a URI-class object."
                );
        }
    }
    return $self->game_uri_object->as_string
        if defined( $self->game_uri_object );
}

##############################
# Security methods
##############################

# These methods all deal with the attached signature somehow.

# confirm_record_owner: Make sure that the stored copy of this record agrees
# with what this record asserts is its parlor, and that the record's signature
# is valid. This is a necessary step before performing an SQL UPDATE on this
# record's DB entry, lest stupid/evil parlors stomp other parlors' records.
sub confirm_record_owner {
    my $self = shift;
    unless ( $self->id ) {
        $self->logger->warn(
"This record has no ID, and thus no owner at all. You shouldn't have called confirm_record_owner on it!"
            );
        return 0;
    }
    return $self->verify_signature;
}

=item sign

I<Referee only.>Generates a signature for this record, and attaches it.

The signature is based on a specific subset of the record's
information, which both sender and receiver agree upon. Refer to the
Volity protocol documentation for more information on this procedure.

=cut

# sign: generate a signature based on the serialized version of this record,
# and sign the sucker.
sub sign {
    my $self       = shift;
    my $serialized = $self->serialize;
    unless ($serialized) {
        $self->logger->warn(
"Not signing, because I couldn't get a good serialization of this reciord."
            );
        return;
    }

    unless ( $self->check_gpg_attributes ) {
        $self->logger->warn(
"The sign() method was called on a game record, but the GPG attrubutes aren't properly set on the Volity::GameRecord class."
            );
        return;
    }

    # XXX Very hacky, but good enough for now.
    my $filename = "/tmp/volity_record_$$";
    unless ( open( SERIALIZED, ">$filename" ) ) {
        $self->expire("Can't write to $filename: $!");
    }
    print SERIALIZED $serialized;
    close(SERIALIZED) or $self->expire("Could not close $filename: $!");

    my $out_filename = "/tmp/volity_signature_$$";

    my $gpg_command =
        sprintf(
"%s --no-tty --default-key %s -sba --passphrase-fd 0 --yes --output $out_filename $filename",
        $gpg_bin, $gpg_secretkey );
    open( GPG, "|$gpg_command" )
        or $self->expire(
        "Can't open a pipe into the gpg command: $!\nCommand was: $gpg_command"
        );
    print GPG $gpg_passphrase . "\n";
    close(GPG) or $self->expire("Couldn't close gpg command pipe: $!");

    open( SIG, $out_filename ) or $self->expire("Can't read $out_filename: $!");

    #  local $/ = undef; my $signature = <SIG>;
    my $signature = '';
    while (<SIG>) { $signature .= $_ }
    close(SIG) or $self->expire("Can't close $out_filename: $!");

    # Clean up our messy mess...
    foreach ( $filename, $out_filename ) {
        unlink($_) or $self->expire("Couldn't unlink $_: $!");
    }

    # Finally, attach the signature to the object.
    # XXX HACK, to get around apparent bug where P::F::X::N strips newlines
    $signature =~ s/\n/==NEWLINE==/g;

    $self->signature($signature);
    return $signature;
}

=item verify

Verifies that the record is signed, and that said signature is
valid. Returns truth if everything looks OK, and falsehood otherwise.

=cut

sub verify {
    my $self = shift;
    unless ( defined($gpg_bin) ) {
        $self->logger->warn(
"Can't verify the record, because the path to the GPG binary isn't set!"
            );
        return;
    }
    unless ( defined( $self->signature ) ) {
        $self->logger->warn(
"Can't verify the record, because there doesn't appear to be a signature attached to this record!!"
            );
        return;
    }
    my $serialized = $self->serialize;
    unless ( defined($serialized) ) {
        $self->logger->warn(
            "Can't verify this record, since it won't serialize.");
        return;
    }

    # XXX Very hacky, but good enough for now.
    my $serialized_filename = "/tmp/volity_record_$$";
    open( SERIALIZED, ">$serialized_filename" )
        or $self->expire("Can't write to $serialized_filename: $!");
    print SERIALIZED $serialized;
    close(SERIALIZED)
        or $self->expire("Could not close $serialized_filename: $!");

    my $signature_filename = "/tmp/volity_signature_$$";
    open( SIGNATURE, ">$signature_filename" )
        or $self->expire("Can't write to $signature_filename: $!");
    print SIGNATURE $self->signature;
    close(SIGNATURE)
        or $self->expire("Could not close $signature_filename: $!");

    my $gpg_command =
        $gpg_bin . " --verify $signature_filename $serialized_filename";

    my $result = system($gpg_command);

    # Clean up my messy mess.
    foreach ( $signature_filename, $serialized_filename ) {
        unlink($_) or $self->expire("Can't unlink $_: $!");
    }

    if ($result) {
        return 0;
    }
    else {
        return 1;
    }
}

sub check_gpg_attributes {
    my $self = shift;
    foreach ( $gpg_bin, $gpg_secretkey, $gpg_passphrase, ) {
        unless ( defined($_) ) {
            $self->logger->warn(
"You can't perform GPG actions unless you set all three package variables on Volity::Gamerecord: \$gpg_bin, \$gpg_secretkey and \$gpg_passphrase."
                );
            return 0;
        }
    }
    return 1;
}

=item unsign

Removes the signature from the record, if it has one.

=cut

# unsign: toss out the key. Just a hey-why-not synonym.
sub unsign {
    my $self = shift;
    return $self->signature(undef);
}

# serialize: return a string that represents a signable (and, after sending,
# verifyable version of this record. Fails if the record lacks certain
# information.
# XXX For now, it just returns the end_time timestamp!! It will be more
# complex when the Volity standard for this is made.
sub serialize {
    my $self = shift;
    if ( defined( $self->end_time ) ) {
        return $self->end_time;
    }
    else {
        $self->logger->warn(
            "This record lacks the information needed to serialize it!");
        return;
    }
}

##############################
# Data verification methods
##############################

sub set {
    my $self = shift;
    my ( $field, @values ) = @_;

    # XXX This needs to do more JID-checking.
    if ( $field eq 'quitters' ) {
        foreach (@values) {
            $_ = $self->massage_jid($_);
        }
    }
    elsif ( $field eq 'start_time' or $field eq 'end_time' ) {
        $values[0] = $self->massage_time( $values[0] );
    }
    return $self->SUPER::set( $field, @values );
}

sub massage_jid {
    my $self = shift;
    my ($jid) = @_;
    if ( $jid =~ /^([\w-]+@[\w-]+(?:\.[\w-]+)*)(?:\/([\w-]+))?/ ) {
        my ( $main_jid, $resource ) = ( $1, $2 );
        return $main_jid;
    }
    else {
        $self->logger->warn("This does not look like a valid JID: $jid");
    }
}

sub massage_time {
    my $self = shift;
    my ($time) = @_;

    # Cure possible MySQLization that Date::Parse can't handle.
    #  $time = '1979-12-31 19:00:00';
    $time =~ s/^(\d\d\d\d-\d\d-\d\d) (\d\d:\d\d:\d\d)$/$1T$2/;
    if ( my $parsed = Date::Parse::str2time($time) ) {

        # Transform it into W3C datetime format.
        return ( Date::Format::time2str( "%Y-%m-%dT%H:%M:%S%z", $parsed ) );
    }
    else {
        croak(
"I can't parse this timestamp: $time\nPlease use a time string that Date::Parse can understand."
            );
    }
}

#########################
# RPC param prep
#########################

=item render_as_hashref

Returns an unblessed hash reference describing the game record. It
just so happens that this hash reference is in the very same format
that the Volity C<record_game> RPC request requires as its
E<lt>sructE<gt> argument. Fancy that!

=cut

sub render_as_hashref {
    my $self    = shift;
    my $hashref = {};

    # First, directly copy some fields from the object into the hashref...
    foreach (
        qw( id winners start_time end_time parlor signature
        finished seats )
        )
    {
        $$hashref{$_} = $self->{$_} if defined( $self->$_ );
    }

    # ...then define some others based on the results of method calls.
    foreach ( qw( game_uri ) ) {
        $$hashref{$_} = $self->$_ if defined( $self->$_ );
    }

    return $hashref;
}

# This here's a class method...
sub new_from_hashref {
    my $class = shift;
    my ($hashref) = @_;

    # XXX HACK, to get around apparent bug where P::F::X::N strips newlines
    $$hashref{signature} =~ s/==NEWLINE==/\n/g;

    return $class->new($hashref);
}

=back

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003-2006 by Jason McIntosh.

=cut

1;
