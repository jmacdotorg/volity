package Volity::Server;

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

Volity::Server - A Volity game server!

=head1 SYNOPSIS

 use Volity::Server;
 
 # Create a new server
 my $server = Volity::Server->new(
				 {
				  user=>'bob_the_game',
				  password=>'secret',
				  host=>'volity.net',
				  resource=>'server',
				  alias=>'volity',
				  game_class=>'MyGame::Class',
				}
				);

 # And there you go.
 $server->start;

 # ... elsewhere ...

 $server->stop;


=head1 DESCRIPTION

An object of this class is a Volity game server. As the synopsis
suggests, it's more or less a black-box application class. Construct
the object with a configuratory hash reference, call the C<start>
method, and you'd got a running server, unless you don't.

=cut

use warnings;
use strict;

=head1 CONFIGURATION

When constructing the object, you can use all the keys described in
L<Volity::Jabber/"Accessors">, for this class inherits from that one, you see. You can also use any of the following keys:

=over

=begin old

=item referee_class

The Perl class of the referee this server will use. When a new game
starts, the server will call tshi constructor of this class, and then
hurl the resulting referee object into its own MUC, ready for action!

This value defaults to C<Volity::Referee>. If you set it to something
else, it shoudl probably be a subclass of that. See L<Volity::Referee>
for more information.

=end old

=item game_class

The Perl class of the game that this server will use. It will pass it
along to all the referee objects it creates. See L<Volity::Referee>
and L<Volity::Game>.

=item bookkeeper_jid

The JID of the bookkeeper this server will use for fetching game
records and such. Defaults to "bookkeeper@volity.net", which is
probably exactly what you want.

=back

=head1 OTHER METHODS

=over

=item start

Starts the server.

=item stop

Stops the server, and furthermore calls C<stop> on all its child
referee objects, if it has any still hanging around.

=item referees

Returns a list of all server's currently active referee objects. The
server will take care of adding and removing referees from this list
as they come and go.

You can set this if you want, but it will probably break things and
you will be sad.

=back

=cut

use base qw(Volity::Jabber);
use fields qw(referee_class game_class bookkeeper_jid referees referee_host referee_user referee_password muc_host bot_classes);

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
use Carp qw(croak carp);

sub initialize {
  my $self = shift;
  if ($self->SUPER::initialize(@_)) {
    if (my $referee_class = $self->referee_class) {
      eval "require $referee_class";
      if ($@) {
	die "Failed to require referee class $referee_class: $@";
      }
    }
  }
  return $self;
}

# This presence handler takes care of auto-approving all subscription
# requests. Volity servers are very social like that.
sub jabber_presence {
  my $self = shift;
  my ($presence) = @_;		# POE::Filter::XML::Node object
  if ($presence->attr('type') and $presence->attr('type') eq 'subscribe') {
    # A subscription request! Shoot back approval.
    $self->send_presence(
			 {
			  to=>$presence->attr('from'),
			  type=>'subscribed',
			 }
			);
  }
}

sub new_table {
  my $self = shift;
  # Start a new session to play this game.

  my ($from_jid, $id, @args) = @_;

  my $resource = $self->new_referee_resource;
  
  my $referee_class = $self->referee_class || "Volity::Referee";

  my $ref = $referee_class->new(
				      {
				       starting_request_jid=>$from_jid,
				       starting_request_id=>$id,
				       user=>$self->user,
				       password=>$self->password,
				       resource=>$resource,
				       host=>$self->host,
				       muc_host=>$self->muc_host,
				       game_class=>$self->game_class,
				       alias=>$resource,
				       bookkeeper_jid=>$self->bookkeeper_jid,
				      }
				     );
  $ref->bot_classes($self->bot_classes);
  $self->add_referee($ref);
  $ref->server($self);

  $self->logger->info("New referee (" . $ref->jid . ") initialized, based on table-creation request from $from_jid.");
}

# start: run the kernel.
sub start {
  my $self = shift;
  $self->logger->info("Server started.");
  $self->kernel->run;
}

sub stop {
  my $self = shift;
  $self->logger->info("Server stopped.");
  $self->kernel->post($self->alias, 'shutdown_socket', 0);
  foreach (grep (defined($_), $self->referees)) {
    $_->stop;
  }
}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  # For security's sake, we explicitly accept only a few method names.
  # In fact, the only one we care about right now is 'new_table'.
  if ($$rpc_info{'method'} eq 'volity.new_table') {
    $self->new_table($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
  } else {
#    warn "I received a $$rpc_info{method} RPC request from $$rpc_info{from}. Eh?";
  }
}

sub add_referee {
  my $self = shift;
  my ($referee) = @_;
  croak("You must provide a referee object with the add_referee() method") unless $referee->isa("Volity::Referee");
  if (defined($self->{referees})) {
    push (@{$self->{referees}}, $referee);
  } else {
    $self->{referees} = [$referee];
  }
}

sub remove_referee {
  my $self = shift;
  my ($referee) = @_;
  $self->referees(grep($_ ne $referee, $self->referees));
}

sub new_referee_resource {
  my $self = shift;
  return $$ . "_" . time;
}

####################
# Service Discovery
####################

sub handle_disco_info_request {
    my $self = shift;
    my ($iq) = @_;
    $self->logger->debug("I got a disco info request from " . $iq->attr('from'));
    $self->send_disco_info({
	to=>$iq->attr('from'),
	id=>$iq->attr('id'),
	# I'm making up my own category and type stuff, here.
	# I'll have to ask the Jabber folks what I actually ought to be doing.
	items=>[Volity::Jabber::Disco::Identity->new({category=>'volity',
						      type=>'game-server',
						      name=>'A game server',
						  }),
		Volity::Jabber::Disco::Feature->new({var=>'http://jabber.org/protocol/disco#info'}),
		Volity::Jabber::Disco::Feature->new({var=>'http://jabber.org/protocol/disco#items'}),
		],
    });
}

sub handle_disco_items_request {
    my $self = shift;
    my ($iq) = @_;
    $self->logger->debug("I got a disco items request.");
    my $query = $iq->get_tag('query');
    my @nodes;
    if (defined($query->attr('node'))) {
	@nodes = split(/\//, $query->attr('node'));
    }
    
    my @items;			# Disco items to return to the requester.

    # It should probably return a list of conference tables, or something?
    # Now things get interesting...

    # For now, it will just return no items.
    $self->send_disco_items({to=>$iq->attr('from'),
			     id=>$iq->attr('id'),
			     items=>[],
			 }
			     );

}

1;

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003 by Jason McIntosh.

=cut
