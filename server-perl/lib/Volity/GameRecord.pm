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
use fields qw(players signature winners quitters start_time end_time game_uri_object game_name server);

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
  $class = ref($class) if defined(ref($class));
  my ($dbh, $id) = @_;
  # XXX Incomplete!!
}

# OBJECT METHODS
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
