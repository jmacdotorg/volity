package Volity::GameRecord;

use warnings;
use strict;

use XML::Writer;
use XML::SAX::ParserFactory;
use URI;
use Carp qw(croak);
use IO::Scalar;
use Date::Parse;
use Date::Format;

use base qw(Class::Accessor::Fields);
use fields qw(id players signature winners quitters start_time end_time game_uri_object game_name server);

########################
# Special Constructors (Class methods)
########################

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

sub new_from_db {
  my $class = shift;
  my ($dbh, $id) = @_;
  $dbh->select({
		tables=>[qw(
			    game
			    uri
			   )],
		fields=>['game.id',
			 'game.started',
			 'game.finished',
			 'game.server_jid',
			 'game.server_signature',
			 'game.uri',
			 'uri.name as game_name',
			],
		where=>{id=>$id},
		join=>"game.uri = uri.uri",
	      });
  my $data = $dbh->fetchrow_hashref;
  my $self;
  if ($$data{id}) {
    $self = $class->new({id=>$id, start_time=>$$data{started},
			    end_time=>$$data{finished},
			    server=>$$data{server_jid},
			    signature=>$$data{server_signature},
			    game_name=>$$data{game_name},
			  });
    $self->game_uri($$data{uri});
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


######################
# Object methods
######################

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
  return $xml_string;
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

# 'sign' and 'unsign' are just sexy alternatives to the 'signature' accessor.

sub sign {
  my $self = shift;
  return $self->signature(@_);
}

sub unsign {
  my $self = shift;
  return $self->signature(undef);
}

##############################
# Security methods
##############################

# These methods all deal with the attached signature somehow.

# confirm_record_owner: Make sure that the stored copy of this record agrees
# with what this record asserts is its server, and that the record's signature
# is valid. This is a necessary step before performing an SQL UPDATE on this
# record's DB entry, lest stupid/evil servers stomp other servers' records.
sub confirm_record_owner {
  # XXX !!!HACK!!! Since jmac can't get Perl-PGP stuff to work yet on his
  # Mac OS X machine, this always returns truth. This shouldn't be the case.
  my $self = shift;
  unless ($self->id) {
    carp("This record has no ID, and thus no owner at all. You shouldn't have called confirm_record_owner on it!");
    return 0;
  }
  # XXX Signature checking junk goes here.
  return 1;
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
  if ($jid =~ /^(\w+@\w+\.\w+)(\/\w+)?/) {
    my ($main_jid, $resource) = ($1, $2);
    return $main_jid;
  } else {
    croak("This does not look like a valid JID: $jid");
  }
}

sub massage_time {
  my $self = shift;
  my ($time) = @_;
#  if (my ($ss,$mm,$hh,$day,$month,$year,$zone) = Date::Parse::strptime($time)) {
  if (my $parsed = Date::Parse::str2time($time)) {
    # Transform it into W3C datetime format.
    return (Date::Format::time2str("%Y-%m-%dT%H:%M:%S%z"));
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
  my $values = {started=>$self->start_time,
		finished=>$self->end_time,
		server_jid=>$self->server,
		server_signature=>$self->signature,
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
    
1;
