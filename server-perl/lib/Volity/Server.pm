package Volity::Server;

use warnings;
use strict;

use base qw(Volity::Jabber);
use fields qw(game_class);

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
	   Component::Jabber;
	  );
use Jabber::NS qw(:all);
use RPC::XML::Parser;
use Volity::Referee;

sub initialize {
  my $self = shift;
  if ($self->SUPER::initialize(@_)) {
    my $game_class = $self->game_class or die
      "No game class specified at construction!";
    eval "require $game_class";
    if ($@) {
      die "Failed to require game class $game_class: $@";
    }
  }
  return $self;
}

# XXX This is dumb.
sub jabber_authed {
  my $kernel = $_[KERNEL];
  my $heap = $_[HEAP];
  my $session = $_[SESSION];
  my $node = $_[ARG0];
  print "We have authed!\n";
  my $self = $_[OBJECT];
  unless ($node->name eq 'handshake') {
    warn $node->to_str;
#    die;
  }
}

sub new_game {
  my $self = shift;
  print "The new game RPC method has been called, looks like.\n";
  # Start a new session to play this game.

  my ($from_jid, $id, @args) = @_;

  Volity::Referee->new(
		       {
			starting_request_jid=>$from_jid,
			starting_request_id=>$id,
			user=>$self->user,
			password=>$self->password,
			resource=>'volity',
			host=>$self->host,
			alias=>'game',
			debug=>$self->debug,
			game_class=>$self->game_class,
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
