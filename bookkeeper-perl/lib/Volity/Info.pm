package Volity::Info;

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

use warnings;
use strict;

use base qw(Class::Accessor::Fields);
use fields qw(dbh id);
our ($dbh);

use Carp qw(carp croak);

# XXX Warning... this may be possibly retarded. Might be better to simply
# XXX store the dbh as a global in HTS and be done wi' it.
sub set_dbh {
  my $self = shift;
  if ($self->dbh) {
    if (ref($self->dbh) eq 'DBI') {
      $Volity::Info::dbh = DBIx::Abstract->new($self->dbh);
      $self->dbh($HTS::dbh);
    } else {
      $dbh = $self->dbh;
    }
  } elsif (defined ($dbh)) {
    $self->dbh($dbh);
  } else {
    my $class = ref($self);
    croak ("You must set a dbh when creating a new $class object.");
  }
}

# Stub method for recursive db storage.
sub db_store {
}

# initialize: Call fetch_attributes_from_db if the id is passed in during
# construction.
sub initialize {
  my $self = shift;
  $self->SUPER::initialize(@_);
  $self->set_dbh;
  if (defined($self->id)) {
    $self->fetch_attributes_from_db;
  }
}

sub dbh {
  my $self = shift;
  if (exists($_[0])) {
    $self->{dbh} = $_[0];
  }
  if ($self->{dbh}) {
    return $self->{dbh};
  } else {
    return $HTS::dbh;
  }
}

# id: Accessor. If called as a mutator, possibly update the class's notion of
# 'known' objects. This allows constructors of this class to pass back an
# existing object instead of a new one, if requested to create an object with
# a known database ID.
sub id {
  my $self = shift;
  my $id_field = $self->id_column;
  if (exists($_[0])) {
    my $previous_id = $self->{$id_field}; my $new_id = $_[0];
    $self->{$id_field} = $new_id;
    if (my $known_object_hashref = $self->known_object_hashref) {
      if (defined($previous_id)) {
	delete($$known_object_hashref{$previous_id});
      }
      if (defined($new_id)) {
	$$known_object_hashref{$new_id} = $self;
      }
    }
  }
  return $self->{$id_field};
}
	  
# known_object_hashref: return a reference to the hash of known objects of
# this class, or return undef if this class doesn't care about known objects.
sub known_object_hashref { }

# get_last_insert_id: utility method for getting the ID of the most recent
# insert of the given DB table.
sub get_last_insert_id {
  my $self = shift;
  my ($table) = @_;
  # XXX This is MySQL-specific ONLY. Make this shmarter later on, yo.
  my $dbh = $self->dbh;
  my ($id) = $dbh->select("last_insert_id()", $table)->fetchrow_array;
  return $id;
}

# insert: utility method to perform an SQL insert and retrun the ID of the
# new row. Performs chicken-waving appropriate to the DBI driver in use.
sub insert {
  my $self = shift;
  # $table is just the table name, $values is a hashref of column=>value.
  my ($table, $values) = @_;
  my $dbh = $self->dbh;
#  die $dbh;
  my $id;			# Return value.
  $dbh->insert($table, $values);
  $id = $self->get_last_insert_id($table);
  return $id;
}

# new: If we're being asked to constuct a 'known' object, return that instead
# of a fresh one.


sub new {
  my $class = shift;
  my ($args) = @_;
  if (defined(my $known_object_hashref = $class->known_object_hashref)) {
    if (defined($$args{$class->id_column}) and defined($$known_object_hashref{$$args{$class->id_column}})) {
      return $$known_object_hashref{$$args{$class->id_column}};
    } else {
      return $class->SUPER::new(@_);
    }
  } else {
    return $class->SUPER::new($args);
  }
}

# make a new db record, and set this thingy's ID.
# Args is a tablename and a hashref of field=>newval, suitable for passing to
# DBI::Abstract's 'insert' or 'update' methods.
sub insert_or_update {
  my $self = shift;
  my $dbh = $self->dbh;
  my ($table, $data) = @_;
#  warn "Honey honey! Got @_";
  if (defined($self->{$self->id_column})) {
#    warn "Update time.";
    $dbh->update($table, $data, {$self->id_column=>$self->{$self->id_column}}) if %$data;
  } else {
#    warn "Insert! Let's.";
    $self->id($self->insert($table, $data));
  }
}
  
# fetch_attributes_from_db: Get all the 'simple' values of this object from the
# database. It's up to subclasses to figure out what to do with this.
sub fetch_attributes_from_db { }

sub id_column { "id" }
1;
