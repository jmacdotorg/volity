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
server passes to a . Through the methods this class provides, it's
easy for a server to build and sign this RPC call, and just as easy
for the receiving bookkeeper.

Note that, since RPC is the neutral middle ground, a Frivolity game
server (a.k.a. a Perl object of a Volity::Game::Server subclass) can
pass a Volity::GameRecord object to any Volity bookkeeper, regardless
of its platform. Similarly, A Volity::Bookkeeper object can knit a
Volity::Gamerecord object out of any game server's RPC request,
whether or not that server runs Frivolity. (In reality, this latter
situation will probably be quite common.)

=head1 USAGE

Game module designers don't have to worry about using this module at
all. Records are automatically created by Volity::Referee objects when
a game wraps up, and incoming records are automatically parsed by the
bookkeeper.

The following doucmentation is really here for completeness' sake, but
those wishing to modify the Frivolity referee or bookkeeper behavior
might find it interesting.

=cut

use warnings;
use strict;

use XML::Writer;
use XML::SAX::ParserFactory;
use URI;
use Carp qw(croak carp);
use IO::Scalar;
use Date::Parse;
use Date::Format;

use base qw(Class::Accessor::Fields);
use fields qw(id players signature winners quitters start_time end_time game_uri_object game_name server);

# Set up package variables for GPG config.
our ($gpg_bin, $gpg_secretkey, $gpg_passphrase);

########################
# Special Constructors (Class methods)
########################

=head1 METHODS

=head2 Class methods (constructors)

=over

=item new_from_xml($xml)

Returns a new Volity::GameRecord object, given a scalar containing an XML document representation of same.

For the opposite functionality, see C<render_as_xml> under L<"Object methods">.

=cut

sub new_from_xml {
  my $class = shift;
  my $self = $class->new;
  my ($xml_string) = @_;
  unless (defined($xml_string)) {
    croak("You must pass new_from_xml a scalar holding game record XML.");
  }
  my $handler = Volity::GameRecord::XMLHandler->new;
  my $parser = XML::SAX::ParserFactory->parser(Handler=>$handler);
  $parser->parse_string($xml_string);
  return $handler->record;
}

=item new_from_db

=cut

sub new_from_db {
  my $class = shift;
  my ($dbh, $id) = @_;
  $dbh->select({
		tables=>[qw(
			    game
			    uri
			   )],
		fields=>['game.id',
			 'game.start_time',
			 'game.end_time',
			 'game.server_jid',
			 'game.signature',
			 'game.uri',
			 'uri.name as game_name',
			],
		where=>{id=>$id},
		join=>"game.uri = uri.uri",
	      });
  my $data = $dbh->fetchrow_hashref;
  my $self;
  if ($$data{id}) {
    $self = $class->new({id=>$id,
			    server=>$$data{server_jid},
			    signature=>$$data{signature},
			    game_name=>$$data{game_name},
			  });
    $self->game_uri($$data{uri});
    $self->end_time($$data{end_time});
    $self->start_time($$data{start_time});
  } else {
    carp("Could not find a DB record for game with ID '$id'.");
    return;
  }
  # Fetch the various player lists.
  foreach my $player_list (qw(players winners quitters)) {
    my $table = 'game_' . substr($player_list, 0, length($player_list) - 1);
    $dbh->select('player_jid', $table, {game_id=>$id});
    my @player_jids;
    while (my ($player_jid) = $dbh->fetchrow_array) {
      push (@player_jids, $player_jid);
    }
    $self->$player_list(@player_jids) if @player_jids;
  }
  return $self;
}

=item new_from_hashref($hashref)

Creates a new object based on the given hash reference, typically one
that has been freshly translated from an RPC E<lt>structE<gt>
argument. 

For the opposite functionality, see C<render_into_hashref> under L<"Object Methods">.

=back

=head2 Object accessors

All these are simple accessors which return the named object
attribute. If an argument is passed in, then the attribute's value is
first set to that argument.

In the case of lists, either an array or an array reference is
returned, depending upon context.

This module inherits from Class::Accessor, so all the tips and tricks
detailed in L<Class::Accessor> apply here as well.

=over

=item id

=item players

=item signature

=item winners

=item quitters

=item start_time

=item end_time

=item game_uri

=item game_name

=item server

=back

=cut

######################
# Object methods
######################

=head2 Object methods

=over

=item render_as_xml

Returns a string holding the game record as an XML document.

=cut

sub render_as_xml {
  my $self = shift;
  my $xml_string = IO::Scalar->new;
  my $w = XML::Writer->new(DATA_MODE=>1,
			   DATA_INDENT=>2,
			   @_, 	# Optional user-provided args
			   OUTPUT=>$xml_string);
  # XXX We'll have to drop in some NS info here.
  $w->startTag('signed-record') if defined($self->signature);
  $w->startTag('record');
  # Players!
  $w->startTag('players');
  foreach my $player ($self->players) {
    $w->dataElement('player', $player);
  }
  $w->endTag;
  # Winners!
  if ($self->winners) {
    $w->startTag('winners');
    foreach my $player ($self->winners) {
      $w->dataElement('player', $player);
    }
    $w->endTag;
  }
  # Quitters!
  if ($self->quitters) {
    foreach my $player ($self->quitters) {
      $w->dataElement('player', $player);
    }
  }
  # Timestamps!
  $w->dataElement('start-time', $self->start_time) if defined($self->start_time);
  $w->dataElement('end-time', $self->end_time);
  # Server info!
  $w->dataElement('game-uri', $self->game_uri);
  $w->dataElement('game-name', $self->game_name);
  $w->dataElement('server', $self->server);
  $w->endTag('record');
  if ($self->signature) {
    $w->dataElement('signature', $self->signature);
    $w->endTag('signed-record');
  }
  return "$xml_string";
}

########################
# Special Accessors
########################

# Most accessors are automatically defined by Class::Accessors::Fields.

sub game_uri {
  my $self = shift;
  # We store URI-class objects, and return stringy-dings.
  # You can pass in either URI objects or strings.
  if (exists($_[0])) {
    if (defined(ref($_[0])) and ref($_[0]) eq 'URI') {
      $self->game_uri_object(@_);
    } elsif (not(ref($_[0]))) {
      my $uri = URI->new($_[0]);
      unless (defined($uri)) {
	croak("The game_uri method thinks that this doesn't look like a URI: $_[0]");
      }
      $self->game_uri_object($uri);
    } else {
      croak("You must call game_uri() with either a URI string, or a URI-class object.");
    }
  }
  return $self->game_uri_object->as_string if defined($self->game_uri_object);
}



##############################
# Security methods
##############################

# These methods all deal with the attached signature somehow.

=begin unimplemented

=item confirm_record_owner

I<Bookkeeper only.> Confirms that the existing, DB-stored copy of this record assets the same server relationship as this record, and then re-verifies its signature (as per C<verify()>. Returns truth if everything looks OK, and falsehood otherwise.

=end unimplemented

=cut

# confirm_record_owner: Make sure that the stored copy of this record agrees
# with what this record asserts is its server, and that the record's signature
# is valid. This is a necessary step before performing an SQL UPDATE on this
# record's DB entry, lest stupid/evil servers stomp other servers' records.
sub confirm_record_owner {
  my $self = shift;
  unless ($self->id) {
    carp("This record has no ID, and thus no owner at all. You shouldn't have called confirm_record_owner on it!");
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
  my $self = shift;
  my $serialized = $self->serialize;
  unless ($serialized) {
    carp("Not signing, because I couldn't get a good serialization of this reciord.");
    return;
  }

  return unless $self->check_gpg_attributes;

  # XXX Very hacky, but good enough for now.
  my $filename = "/tmp/volity_record_$$";
  open (SERIALIZED, ">$filename") or die "Can't write to $filename: $!";
  print SERIALIZED $serialized;
  close (SERIALIZED) or die "Could not close $filename: $!";

  my $out_filename = "/tmp/volity_signature_$$";

  my $gpg_command = sprintf("%s --default-key %s -sba --passphrase-fd 0 --yes --output $out_filename $filename", $gpg_bin, $gpg_secretkey);
  open (GPG, "|$gpg_command") or die "Can't open a pipe into the gpg command: $!\nCommand was: $gpg_command";
  print GPG $gpg_passphrase . "\n";
  close (GPG) or die "Couldn't close gpg command pipe: $!";

  open (SIG, $out_filename) or die "Can't read $out_filename: $!";
  local $/ = undef; my $signature = <SIG>;
  close (SIG) or die "Can't close $out_filename: $!";

  # Clean up our messy mess...
  foreach ($filename, $out_filename) {
    unlink ($_) or die "Couldn't unlink $_: $!";
  }

  # Finally, attach the signature to the object.
  $self->signature($signature);
  return $signature;
}

=item verify

Verifies that the record is signed, and that said signature is
valid. Returns truth if everything looks OK, and falsehood otherwise.

=cut

sub verify {
  my $self = shift;
  unless (defined($gpg_bin)) {
    carp("Can't verify the record, because the path to the GPG binary isn't set!");
    return;
  }
  unless (defined($self->signature)) {
    carp("Can't verify the record, because there doesn't appear to be a signature attached to this record!!");
    return;
  }
  my $serialized = $self->serialize;
  unless (defined($serialized)) {
    carp("Can't verify this record, since it won't serialize.");
    return;
  }
  # XXX Very hacky, but good enough for now.
  my $serialized_filename = "/tmp/volity_record_$$";
  open (SERIALIZED, ">$serialized_filename") or die "Can't write to $serialized_filename: $!";
  print SERIALIZED $serialized;
  close (SERIALIZED) or die "Could not close $serialized_filename: $!";

  my $signature_filename = "/tmp/volity_signature_$$";
  open (SIGNATURE, ">$signature_filename") or die "Can't write to $signature_filename: $!";
  print SIGNATURE $self->signature;
  close (SIGNATURE) or die "Could not close $signature_filename: $!";

  
  my $gpg_command = $gpg_bin . " --verify $signature_filename $serialized_filename";
  my $result = system($gpg_command);

  # Clean up my messy mess.
  foreach ($signature_filename, $serialized_filename) {
    unlink($_) or die "Can't unlink $_: $!";
  }

  if ($result) {
    return 0;
  } else {
    return 1;
  }
}

sub check_gpg_attributes {
  my $self = shift;
  foreach ($gpg_bin, $gpg_secretkey, $gpg_passphrase,) {
    unless (defined($_)) {
      carp("You can't perform GPG actions unless you set all three package variables on Volity::Gamerecord: \$gpg_bin, \$gpg_secretkey and \$gpg_passphrase.");
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
  if (defined($self->end_time)) {
    return $self->end_time;
  } else {
    carp("This record lacks the information needed to serialize it!");
    return;
  }
}

##############################
# Data verification methods
##############################

sub set {
  my $self = shift;
  my ($field, @values) = @_;
  if ($field eq 'players' or $field eq 'winners' or $field eq 'quitters') {
    foreach (@values) {
      $_ = $self->massage_jid($_);
    }
  } elsif ($field eq 'start_time' or $field eq 'end_time') {
    $values[0] = $self->massage_time($values[0]);
  }
  return $self->SUPER::set($field, @values);
}

sub massage_jid {
  my $self = shift;
  my ($jid) = @_;
  if ($jid =~ /^(\w+@\w+[\.\w]+)(\/\w+)?/) {
    my ($main_jid, $resource) = ($1, $2);
    return $main_jid;
  } else {
    croak("This does not look like a valid JID: $jid");
  }
}

sub massage_time {
  my $self = shift;
  my ($time) = @_;
  # Cure possible MySQLization that Date::Parse can't handle.
  #  $time = '1979-12-31 19:00:00';
  $time =~ s/^(\d\d\d\d-\d\d-\d\d) (\d\d:\d\d:\d\d)$/$1T$2/;
  if (my $parsed = Date::Parse::str2time($time)) {
    # Transform it into W3C datetime format.
    return (Date::Format::time2str("%Y-%m-%dT%H:%M:%S%z", $parsed));
  } else {
    croak("I can't parse this timestamp: $time\nPlease use a time string that Date::Parse can understand.");
  }
}

#########################
# DB Access methods
#########################

sub store_in_db {
  my $self = shift;
  my ($dbh) = @_;
  unless ($dbh) {
    croak("You must call store_in_db with a database handle.");
  }
  # Decide: insert or update?
  # It's based on whether or not the game record has an ID.
  my $values = {start_time=>$self->start_time,
		end_time=>$self->end_time,
		server_jid=>$self->server,
		signature=>$self->signature,
		uri=>$self->game_uri,
	      };  if (defined($self->id)) {
    unless ($self->confirm_record_owner($self)) {
      carp("Yikes... I can't store this record because its ownership claims seem suspect.");
      return;
    }
    $dbh->update('game', $values, {id=>$self->id},);
  } else {
    $self->id($self->insert($dbh, 'game', $values));
  }
  # Now go through the player lists. It's always a case of drop-and-insert,
  # for they're all many-to-many linking tables.
  foreach my $player_list (qw(players winners quitters)) {
    my $table = 'game_' . substr($player_list, 0, length($player_list) - 1);
    $dbh->delete($table, {game_id=>$self->id});
    my @player_jids = $self->$player_list;
    for (@player_jids) {
      $dbh->insert($table, {game_id=>$self->id, player_jid=>$_}) if defined($_);
    }
  }
}

# insert: utility method to perform an SQL insert and retrun the ID of the
# new row. Performs chicken-waving appropriate to the DBI driver in use.
sub insert {
  my $self = shift;
  # $table is just the table name, $values is a hashref of column=>value.
  my ($dbh, $table, $values) = @_;
  my $id;			# Return value.
  # This subroutine assumes that table IDs are are kept in columns called
  # 'id', and have sequences named "${table}_id_seq" (if Oracle).
  # If this isn't the case, then the tables are insane. Shrug.
  if (substr($dbh->{connect}{data_source}, 0, 11) eq 'dbi:Oracle:') {
    # We're connected to an Oracle database.
    # Check for a table id seq.
    my $seq_name = "${table}_id_seq.nextval";
    ($id) = $dbh->select($seq_name, 'dual')->fetchrow_array;
    $$values{id} = $id;
    $dbh->insert($table, $values);
  } else {
    # We're connect to some other database.
    $dbh->insert($table, $values);
    $id = $self->get_last_insert_id($dbh, $table);
  }
  return $id;
}

sub get_last_insert_id {
  my $self = shift;
  my ($dbh, $table) = @_;
  # XXX This is MySQL-specific ONLY. Make this shmarter later on, yo.
  my ($id) = $dbh->select("last_insert_id()", $table)->fetchrow_array;
  return $id;
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
  my $self = shift;
  my $hashref = {};
  foreach (qw(id players winners quitters start_time end_time game_uri server signature)) {
    $$hashref{$_} = $self->$_ if defined($self->$_);
  }
  return $hashref;
}

# This here's a class method...
sub new_from_hashref {
  my $class = shift;
  my ($hashref) = @_;
  my $self = Volity::GameRecord->new;
  foreach (qw(id players winners quitters start_time end_time game_uri server signature)) {
    if (defined($$hashref{$_}) and ref($$hashref{$_}) eq 'ARRAY') {
      warn "Sip - a -sup wit $_";
      $self->$_(@{$$hashref{$_}});
    } elsif (defined($$hashref{$_})) {
      warn "Zup up wit $_";
      $self->$_($$hashref{$_});
    }
  }
  return $self;
}

#########################
# XML handler
#########################

package Volity::GameRecord::XMLHandler;

use warnings; use strict;

use base qw(XML::SAX::Base);
our ($record, @current_player_list, @elements);

sub record {
  return $record;
}

sub start_document {
  my $self = shift;
  $record = Volity::GameRecord->new;
}

sub start_element {
  my $self = shift;
  my ($info) = @_;
#  warn "Opening element $$info{LocalName}";
  push (@elements, $$info{LocalName});
}

sub characters {
  my $self = shift;
  my $data = $_[0]{Data};
  return unless $data =~ /\S/;
  my $element = $elements[-1];
  if ($element eq 'player') {
    push (@current_player_list, $data);
  } else {
    # My method names are the same as XML element names,
    # except '-'s are '_'s.
    $element =~ s/-/_/g;
    $record->$element($data);
  }
}

sub end_element {
  my $self = shift;
  my ($info) = @_;
#  warn "Closing element $$info{LocalName}";
  pop (@elements);
  if (@current_player_list and $$info{LocalName} ne 'player') {
    my $method = $$info{LocalName};
    $record->$method(@current_player_list);
    undef(@current_player_list);
  }
}

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003 by Jason McIntosh.

=cut

    
1;
