package Volity::Bot;

# A package for automated Volity players.
# Should be suitable for both 'ronin' and 'retainer' bots.

use warnings;
use strict;
use base qw(Volity::Jabber Class::Data::Inheritable);
use fields qw(muc_jid referee referee_jid opponents name_modifier nickname);
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
  my $self = $class->SUPER::new($config);
  $self->name_modifier('');
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
    $self->join_table;

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
    $self->logger->debug("Got an error ($code):");
    my $message = $error->data || "Something went wrong.";
    $self->logger->debug($message);
    if ($code == 409) {
	# Aha, we have failed to join the conf.
	# Change our name and try again.
	if ($self->name_modifier) {
	    $self->name_modifier($self->name_modifier + 1);
	} else {
	    $self->name_modifier(1);
	}
	$self->join_table;
    }
    return;
  }
  if (($node->get_tag('x')) and (($x) = grep($_->attr('xmlns') eq "http://jabber.org/protocol/muc#user", $node->get_tag('x')))) {
      # Aha, someone has joined the table.
#      my $new_person_jid = $x->get_tag('item')->attr('jid');
      my $affiliation = $x->get_tag('item')->attr('affiliation');
      $self->logger->debug("I see presence from " . $node->attr('from'));
      my ($nickname) = $node->attr('from') =~ m|/(.*)$|;
      if ($nickname eq $self->nickname) {
	  if ($nickname eq $self->nickname) {
	      # Oh, it's me.
	      $self->referee_jid($self->muc_jid . "/volity");
	      $self->declare_readiness;
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
}

sub join_table {
    my $self = shift;
    # Attempt to join the MUC with our best idea of a nickname.
    # If this fails, the error handler will increment the nick-
    # modifier.
    my $nick = $self->name . $self->name_modifier;
    $self->join_muc({jid=>$self->muc_jid, nick=>$nick});
    $self->nickname($nick);
}

# add_opponent_nickname: Add the given nickname (either a full JID in MUC
# format, or a bare string containing only the nickname) to our internal
# list of known opponents at the current table.
sub add_opponent_nickname {
  my $self = shift;
  my ($name) = @_;
  my $nickname;
  if (is_jid($name)) {
    ($nickname) = $name =~ /\/(.*)$/;
    unless ($nickname) {
      die "GACK. I couldn't tease a nickname out of the jid $name.";
    }
  } else {
    $nickname = $name;
  }
  my $my_nickname = $self->nickname;
  unless ($my_nickname eq $nickname) {
    $self->{opponents}{$nickname} = 1;
  }
}

sub remove_opponent_nickname {
  my $self = shift;
  my ($name) = @_;
  my $nickname;
  if (is_jid($name)) {
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

sub is_jid {
  my ($jid) = @_;
  if ($jid =~ /^[\w-]+@[\w-]+(?:\.[\w-]+)*(?:\/[\w-]+)?/) {
    return $jid;
  } else {
    return;
  }
}

sub declare_readiness {
    my $self = shift;
    $self->logger->debug("Sending a declaration of readiness to " . $self->referee_jid);
    $self->send_rpc_request({
	to=>$self->referee_jid,
	id=>'ready',
	methodname=>'volity.ready',
    });
}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  my $method = $$rpc_info{method};
  if ($method eq 'volity.end_game') {
      # I wanna play again!!!
      $self->declare_readiness;
  } elsif ($method eq 'volity.player_unready') {
      if ($rpc_info->{args}->[0] eq $self->nickname) {
	  # The config must have changed, since I just lost readiness.
	  # I don't care! I'm still ready!!      
	  $self->declare_readiness;
      }
  }
}

1;

