package Volity::Bot;

# A package for automated Volity players.
# Should be suitable for both 'ronin' and 'retainer' bots.

use warnings;
use strict;
use base qw(Volity::Jabber Class::Data::Inheritable);
use fields qw(muc_jid referee referee_jid opponents);
# Why separate referee and referee_jid fields?
# Because the 'referee' field is to hold a Volity::Referee object, if one
# is available (i.e. this is being used as a Retainer-bot).
# 'referee_jid' is set when the ref is detected by its presence packets.
use Carp qw(croak);
use POE;

foreach (qw(name description user host password)) {
  __PACKAGE__->mk_classdata($_);
}

# We override the constructor to perform some sanity checks, and
# insert additional config information based on class data.
sub new {
  my $invocant = shift;
  warn("***A bot is being created.***");
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
  warn("Bot constructor about to be called.");
  my $self = $class->SUPER::new($config);
  warn("Bot constructor called.");
  return $self;
}

sub init_finish {
    my $self = $_[OBJECT];
    $self->logger->debug("THE BOT LIVES!!!");
    $self->logger->debug("Its password is: " . $self->password);
    $self->logger->debug("I will try to join the MUC at this JID: " . $self->muc_jid);
    # XXX This doesn't handle the case for when the bot fails to join, due
    # to someone using its nickname already present in the MUC.
    # I'll need to add an error handler for when this happens.
    $self->join_muc({jid=>$self->muc_jid, nick=>$self->name});
}

# This presence handler detects a table's referee through MUC attributes.
# It also watches for general presence updates, and updates the user's
# internal roster object as needed.
sub jabber_presence {
  my $self = shift;
  my ($node) = @_;
  my $x; # Hey, that's the name of the element, OK?
  if (defined($node->attr('type')) and $node->attr('type') eq 'error') {
    # Ruh roh. Just print an error message.
    my $error = $node->get_tag('error');
    my $code = $error->attr('code');
    $self->terminal->put("Got an error ($code):");
    my $message = $error->data || "Something went wrong.";
    $self->terminal->put($message);
    return;
  }
  if (($node->get_tag('x')) and (($x) = grep($_->attr('xmlns') eq "http://jabber.org/protocol/muc#user", $node->get_tag('x')))) {
    # Aha, someone has joined the table.
    my $new_person_jid = $x->get_tag('item')->attr('jid');
    my $affiliation = $x->get_tag('item')->attr('affiliation');
    if ($affiliation eq 'owner') {
      # This is the table's ref.
      $self->referee_jid($new_person_jid);
    } else {
      # This is a potential opponent!
      unless ($node->attr('type')) {
	# No 'type' attribute means they're joining us...
	# Save the nickname. We'll pass it to the UI file later.
	$self->add_opponent_nickname($node->attr('from'));
      } elsif ($node->attr('type') eq 'unavailable') {
	# Oh, they're leaving...
	$self->remove_opponent_nickname($node->attr('from'));
      }
    }
  }
}

# add_opponent_nickname: Add the given nickname (either a full JID in MUC
# format, or a bare string containing only the nickname) to our internal
# list of known opponents at the current table.
sub add_opponent_nickname {
  my $self = shift;
  my ($name) = @_;
  my $nickname;
  if (main::is_jid($name)) {
    ($nickname) = $name =~ /\/(.*)$/;
    unless ($nickname) {
      die "GACK. I couldn't tease a nickname out of the jid $name.";
    }
  } else {
    $nickname = $name;
  }
  my $my_nickname = $self->name;
  unless ($my_nickname eq $nickname) {
    $self->{opponents}{$nickname} = 1;
  }
}

sub remove_opponent_nickname {
  my $self = shift;
  my ($name) = @_;
  my $nickname;
  if (main::is_jid($name)) {
    ($nickname) = $name =~ /\/(.*)$/;
    unless ($nickname) {
      die "GACK. I couldn't tease a nickname out of the jid $name.";
    }
  } else {
    $nickname = $name;
  }
  return(delete($self->{opponents}{$nickname}));
}

sub opponent_nicknames {
  my $self = shift;
  return keys(%{$self->{opponents}});
}

sub clear_opponent_nicknames {
  my $self = shift;
  $self->{opponents} = {};
}

1;

