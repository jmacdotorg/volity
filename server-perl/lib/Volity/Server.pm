package Volity::Server;

use warnings;
use strict;

use base qw(Volity::Jabber);
use fields qw(referee_class bookkeeper_jid);

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
	   Component::Jabber;
	  );
use Jabber::NS qw(:all);
use RPC::XML::Parser;

sub initialize {
  my $self = shift;
  if ($self->SUPER::initialize(@_)) {
    my $referee_class = $self->referee_class or die
      "No referee class specified at construction!";
    eval "require $referee_class";
    if ($@) {
      die "Failed to require referee class $referee_class: $@";
    }
  }
  return $self;
}

sub jabber_authed {
  my $self = $_[OBJECT];
  my $node = $_[ARG0];
  $self->debug("We have authed!\n");
  unless ($node->name eq 'handshake') {
    warn $node->to_str;
  }
}

sub new_game {
  my $self = shift;
  print "The new game RPC method has been called, looks like.\n";
  # Start a new session to play this game.

  my ($from_jid, $id, @args) = @_;

#  Volity::Referee->new(
  $self->referee_class->new(
		       {
			starting_request_jid=>$from_jid,
			starting_request_id=>$id,
			user=>$self->user,
			password=>$self->password,
			resource=>'volity',
			host=>$self->host,
			alias=>'game',
			debug=>$self->debug,
			bookkeeper_jid=>$self->bookkeeper_jid,
		      }
		      );
}

# start: run the kernel.
sub start {
  my $self = shift;
  $self->kernel->run;
}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  # For security's sake, we explicitly accept only a few method names.
  # In fact, the only one we care about right now is 'new_game'.
  if ($$rpc_info{'method'} eq 'new_game') {
    $self->new_game($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
  } else {
    warn "I received a $$rpc_info{method} RPC request from $$rpc_info{from}. Eh?";
  }
}

1;
