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

Volity::Server - A Volity game parlor.

=head1 SYNOPSIS

 use Volity::Server;
 
 # Create a new parlor
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

However, it is much more likely that you'll be starting a server from
the C<volityd> program than from within code. Here's how you'd launch
the above server from the command line (and without using a C<volityd>
config file):

 $ volityd -J bob_the_game@volity.net/server -G MyGame::Class -p secret

=head1 DESCRIPTION

An object of this class is a Volity parlor. (It's still called
Volity::Server due to historical tradition.) As the synopsis suggests,
it's more or less a black-box application class. Construct the object
with a configuratory hash reference, call the C<start> method, and if
XMPP authentication worked out, you've got a running server.

You generally never need to use this class directly, no matter what
you're doing with Volity. Parlor objects of this class are created and
managed for you by the C<volityd> program, so unless you want to get
into the guts of the Volity system you may be more interested in
L<volityd>. 

If your main goal involves creating Volity games in Perl, I direct
your attention to L<Volity::Game>, which is far more relevant to such
pursuits. The rest of this manpage is probably of more interest to
folks wishing to perform deep voodoo with these libraries.

=cut

use warnings;
use strict;

=head1 CONFIGURATION

When constructing the object, you can use all the keys described in
L<Volity::Jabber/"Accessors">, for this class inherits from that
one. You can also use any of the following keys, which also function
as simple accessor methods after the object is created:

=over

=item game_class

The Perl class of the game that this server will use. It will pass it
along to all the referee objects it creates. See L<Volity::Referee>
and L<Volity::Game>.

=item bookkeeper_jid

The JID of the bookkeeper this server will use for fetching game
records and such. Defaults to "bookkeeper@volity.net", which is
probably exactly what you want.

=item contact_email

The email address of the person responsible for this server.

=item contact_jid

The Jabber ID of the person responsible for this server.

=item bot_configs

A list of hashrefs containing bot config information. The keys of each hashref include C<username>, C<host>, C<password> and C<class>. The latter should probably be the name of a C<Volity::Bot> subclass.

=item volity_version

The version number of the Volity protocol that this server supports.

=item visible

Whether or not this parlor is visble to Volity's game finder. Set to 1
if it is, or 0 if it should go unlisted.

=item admins

A list of JIDs that are allowed to call this server's admin.* RPCs.

=back

=head1 OTHER METHODS

=over

=item start

Starts the server.

=item stop

Stops the server, and furthermore calls C<stop> on all its child
referee objects, if it has any still hanging around.

=item graceful_stop

Stops the parlor, but doesn't affect any live referees. 

=item referees

Returns a list of all server's currently active referee objects. The
server will take care of adding and removing referees from this list
as they come and go.

You can set this if you want, but it will probably break things and
you will be sad.

=item startup_time

Returns the time (in seconds since the epoch) when this palor started.

=item wall ( $message )

Every referee will "speak" $message (with the preamble "Server
message: ") into its table's groupchat.

=back

=cut

use base qw(Volity::Jabber);
use fields qw(referee_class game_class bookkeeper_jid referees referee_host referee_user referee_password muc_host bot_configs contact_email contact_jid volity_version visible referee_count startup_time admins in_graceful_shutdown volityd_command volityd_cwd volityd_argv reconnection_alarm_id);

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
	   Component::Jabber;
	  );

use RPC::XML::Parser;
use Volity::Referee;
use Carp qw(croak carp);

# Set some magic numbers.
our $RECONNECTION_TIMEOUT = 5;

sub initialize {
  my $self = shift;
  $self->SUPER::initialize(@_);

  # We deal with a lot of classes for games, referees, and bots.
  # Make sure that they all compile!
  if (my $referee_class = $self->referee_class) {
      eval "require $referee_class";
      if ($@) {
	  die "Failed to require referee class $referee_class: $@";
      }
  }
  if (my $game_class = $self->game_class) {
      eval "require $game_class";
      if ($@) {
	  die "Failed to require game class $game_class: $@";
      }
  }

  $self->{referees} = [];
  $self->startup_time(time);
  $self->{admins} ||= [];
  $self->referee_count(0);
  return $self;
}

# require_bot_configs: Called by volityd as a volidation step.
# It simply require()s each Bot class, and dies on compilation errors.
sub require_bot_configs {
  my $self = shift;
  for my $bot_config ($self->bot_configs) {
      eval "require $$bot_config{class}";
      if ($@) {
	  die "Failed to require bot class $$bot_config{class}: $@";
      }
  }
}

sub init_finish {
    my $self = shift;
    $self->kernel->alarm_remove($self->reconnection_alarm_id) if defined($self->reconnection_alarm_id);
    $self->reconnection_alarm_id(undef);

    return $self->SUPER::init_finish;
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

# We override Volity::Jabber's send_presence in order to attach some
# additional JEP-0115 information.
sub send_presence {
    my $self = shift;
    my ($config) = @_;
    $config ||= {};
    $$config{caps} = {
	node => "http://volity.org/protocol/caps",
	ext => "parlor",
	ver => "1.0",
    };
    return $self->SUPER::send_presence($config);
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
				       jid_host=>$self->jid_host,
				       muc_host=>$self->muc_host,
				       game_class=>$self->game_class,
				       alias=>$resource,
				       bookkeeper_jid=>$self->bookkeeper_jid,
				       bot_configs=>$self->{bot_configs},
				       port=>$self->port,
				      }
				     );

  $self->add_referee($ref);
  $ref->server($self);

  $self->logger->info("New referee (" . $ref->jid . ") initialized, based on table-creation request from $from_jid.");
  $self->referee_count($self->referee_count + 1);
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
  eval {
      my $ok = 0;
      if ($$rpc_info{'method'} eq 'volity.new_table') {
	  $self->new_table($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
	  $ok = 1;
      } elsif (my ($admin_method) = $$rpc_info{'method'} =~ /^admin\.(.*)$/) {
	  # Check that the sender is allowed to make this call.
	  my ($basic_sender_jid) = $$rpc_info{from} =~ /^(.*)\//;
	  if (grep($_ eq $basic_sender_jid, $self->admins)) {
	      my $local_method = "admin_rpc_$admin_method";
	      if ($self->can($local_method)) {
		  $self->$local_method($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
		  $ok = 1;
	      } else {
		  $ok = 0;
	      }
	  } else {
	      $self->logger->warn("$basic_sender_jid (as $$rpc_info{from}) attempted to call $$rpc_info{method}. But I don't recognize that JID as an admin, so I'm rejecting it.");
	      $self->send_rpc_fault($$rpc_info{from},
				    $$rpc_info{id},
				    607,
				    "You are not allowed to make admin calls on this parlor.",
				    );
	      return;
	  }
      } else {
	  $ok = 0;
      }
      unless ($ok) {
	  $self->logger->warn("Got weird rpc request $$rpc_info{method} from $$rpc_info{from}.");
	  $self->send_rpc_fault($$rpc_info{from},
				$$rpc_info{id},
				603,
				"Unknown methodName: '$$rpc_info{method}'",
				);
      }
  };
  if ($@) {
      $self->report_rpc_error(@_);
      return;
  }
}

sub add_referee {
  my $self = shift;
  my ($referee) = @_;
  croak("You must provide a referee object with the add_referee() method") unless $referee->isa("Volity::Referee");
  push (@{$self->{referees}}, $referee);
  return $referee;
}

sub remove_referee {
  my $self = shift;
  my ($referee) = @_;
  my @new_referees;
  for (my $i = 0; $i <= $self->referees - 1; $i++) {
      if ($self->{referees}->[$i] eq $referee) {
	  splice(@{$self->{referees}}, $i, 1);
	  # If we're admidst a graceful shutdown, and this was the last
	  # referee, then we stop too.
	  $self->graceful_shutdown_check;
	  return;
      }
  }   
  $self->logger->error("I was asked to remove the referee with jid " . $referee->jid . " but I can't find it!!");
}

sub graceful_shutdown_check {
    my $self = shift;
    if ($self->in_graceful_shutdown and not(scalar(@{$self->{referees}}))) {
	$self->logger->info("No referees left, and we're in graceful-shutdown mode, so I'm going away now.");
	exit;
    }
}

sub new_referee_resource {
  my $self = shift;
  return $$ . "_" . time;
}

sub wall {
    my $self = shift;
    my ($message) = @_;
    for my $referee ($self->referees) {
	$referee->groupchat("Server message: $message");
    }
}

sub graceful_stop {
    my $self = shift;
    $self->kernel->post($self->alias, 'shutdown_socket', 0);
}

sub exec_volityd {
    my $self = shift;
    chdir($self->volityd_cwd) or die "Can't chdir to " . $self->volityd_cwd . ": $!";
    exec { $self->volityd_command } ($self->volityd_command, $self->volityd_argv);
}

sub react_to_disconnection_error {
    my $self = shift;
    $self->logger->debug("Attempting to reconnect to the server...\n");
    $self->attempt_reconnection;
}

sub attempt_reconnection {
    my $self = shift;
    $self->kernel->state("reconnection_timeout", $self);
    my $alarm_id = $self->kernel->delay_set("reconnection_timeout", $RECONNECTION_TIMEOUT);
    $self->reconnection_alarm_id($alarm_id);
    $self->alias("volity" . time);
    $self->logger->warn("Trying to reconnect..." . $self->host . $self->port);
    $self->start_jabber_client;
}

sub reconnection_timeout {
    my $self = shift;
    $self->logger->warn("Reconnection timeout!");
    $self->logger->warn("I'll try again.");
    $self->attempt_reconnection;
}

####################
# Service Discovery
####################

sub handle_disco_info_request {
    my $self = shift;
    my ($iq) = @_;
    my $query = $iq->get_tag('query');
    $self->logger->debug("I got a disco info request from " . $iq->attr('from'));
    # Build the list of disco items to return.
    my @items;
    my $identity = Volity::Jabber::Disco::Identity->new({category=>'volity',
							 type=>'parlor',
						     });
    my $game_class = $self->game_class;
    my $node = $query->attr('node');
    if (not(defined($node))) {
	eval "\$identity->name($game_class->name);";
    }
    push (@items, $identity);
    push (@items, Volity::Jabber::Disco::Feature->new({var=>'http://jabber.org/protocol/disco#info'}));
    my $items_feature = Volity::Jabber::Disco::Feature->new({var=>'http://jabber.org/protocol/disco#items'});
    push (@items, $items_feature);

    # Prepare JEP-0128 data form fields!
    my @fields;
    # We must perform these two separate loops since some info is found
    # on the server object, and some on the loaded game module class.
    foreach (qw(description ruleset ruleset_version website volity-role)) {
	my $field_name;
	my $value;
	if ($_ eq 'ruleset') {
	    $value = $game_class->uri;
	} else {
	    if ($_ eq 'volity-role') {
		$value = 'parlor';
	    } else {
		$value = $game_class->$_;
	    }
	}
	$field_name = $_;
	$field_name =~ s/_/-/g;
	my @values = (ref($value) and ref($value) eq 'ARRAY')?
	    @$value : ($value);
	my $field = Volity::Jabber::Form::Field->new({var=>$field_name});
	$field->values(@values);
	push (@fields, $field);
    }
    foreach (qw(contact_email contact_jid volity_version visible)) {
	my $field_name;
	my $value;
	$value = $self->$_;
	$field_name = $_;
	$field_name =~ s/_/-/g;
	my @values = (ref($value) and ref($value) eq 'ARRAY')?
	    @$value : ($value);
	my $field = Volity::Jabber::Form::Field->new({var=>$field_name});
	$field->values(@values);
	push (@fields, $field);
    }

    # Send them off.
    $self->send_disco_info({
	to=>$iq->attr('from'),
	id=>$iq->attr('id'),
	items=>\@items,
	fields=>\@fields,
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

    my $game_class = $self->game_class;

    if (defined($nodes[0])) {
	if( $nodes[0] eq 'ruleset') {
	    # Return a pointer to the bookeeper's info about this ruleset.
	    # The JID reveals our bookkeeper, while the node reveals our
	    # ruleset URI.
	    my $uri;
	    $uri = $self->game_class->uri; 
	    push (@items, Volity::Jabber::Disco::Item->new({
		jid=>$self->bookkeeper_jid,
		node=>$uri,
		name=>"ruleset information (URI: $uri )",
	    }));
	}
	elsif ( $nodes[0] eq 'open_games' ) {
	    $self->logger->debug("It's an open_games request.");
	    # Get a list of referees with open games, and return
	    # pointers to them.
	    my @open_referees = grep(
				     $_->isa("Volity::Referee") && not($_->is_hidden),
				     @{$self->{referees}},
	    );
	    $self->logger->debug("I am returning " . @open_referees . " referee nodes.");
	    foreach (@open_referees) {
		push (@items, Volity::Jabber::Disco::Item->new({
		    jid=>$_->jid,
		    name=>$_->name,
		}));
	    } 
	}
	elsif ( $nodes[0] eq 'bots' ) {
	    $self->logger->debug("It's a bots request.");
	    # Get a list of bots, and return pointers to them.
	    for my $bot_config ($self->bot_configs) {
		my $item = Volity::Jabber::Disco::Item->new({
		    jid=>$self->jid,
		});
		$item->name($bot_config->{class}->name) if $bot_config->{class}->name;
		$item->node($bot_config->{class}->algorithm) if $bot_config->{class}->algorithm;
		push (@items, $item);
	    }
	}
    } else {
	# Just return the two nodes we support.
	foreach (qw(ruleset open_games)) {
	    push (@items,
		  Volity::Jabber::Disco::Item->new({
		      jid=>$self->jid,
		      node=>$_,
		  })
		  );
	}
    }

    $self->send_disco_items(
			    {
				to=>$iq->attr('from'),
				id=>$iq->attr('id'),
				items=>\@items,
			    }
			    );
	
	
}

##########################
# Admin RPC stuff
##########################

# These are all dispatched to from the handle_rpc_request method.

sub admin_rpc_status {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    my %status = (
		  online         => 1,
		  tables_running => scalar(@{$self->{referees}}),
		  tables_started => $self->referee_count,
		  startup_time   => scalar(localtime($self->startup_time)),
		  );
    my $latest_time = '0';
    for my $referee_startup_time (map($_->startup_time, $self->referees)) {
	$latest_time = $referee_startup_time if $referee_startup_time > $latest_time;
    }
    if ($latest_time) {
	$status{last_new_table} = scalar(localtime($latest_time));
    }
		  
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok", \%status]);
}

sub admin_rpc_list_tables {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    my @jids = map($_->jid, $self->referees);
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok", \@jids]);
}

sub admin_rpc_list_bots {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    my @jids = map($_->jid, map($_->active_bots, $self->referees));
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok", \@jids]);
}

sub admin_rpc_shutdown {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    $self->logger->info("Server shut down via RPC, by $from_jid.");
    $self->wall("This parlor is shutting down NOW. Goodbye!");
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
    exit;
}

sub admin_rpc_announce {
    my $self = shift;
    my ($from_jid, $rpc_id, $message) = @_;
    $self->wall($message);
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
}

sub admin_rpc_graceful_shutdown {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    $self->logger->info("Graceful shut down via RPC, by $from_jid.");
    $self->graceful_stop;
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
}

sub admin_rpc_restart {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    $self->logger->info("Graceful restart via RPC, by $from_jid.");
    $self->logger->info("Making system call to launch new process: $0");
    $self->wall("This parlor is shutting down NOW. Goodbye!");
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
    $self->exec_volityd;
}

sub admin_rpc_graceful_restart {
    my $self = shift;
    my ($from_jid, $rpc_id) = @_;
    $self->logger->info("Graceful restart via RPC, by $from_jid.");
    $self->logger->debug("Making system call to launch new process: $0");
    $self->in_graceful_shutdown(1);
    $self->graceful_stop;
    $self->send_rpc_response($from_jid, $rpc_id, ["volity.ok"]);
    if (fork) {
	# I'm the parent.
	$self->graceful_shutdown_check;
    } else {
	# I'm the child. Restart!
	$self->exec_volityd;
    }
}

1;

=head1 NOTES

=head2 Automatic roster acceptance

When an object of this class receives an XMPP request, it will
automatically and immediately . If you don't like this behavior,
consider creating a subclass that overrides the C<jabber_presence>
method. (That said, I can't think of a reason you'd want to have such an
antisocial game server...)

=head1 SEE ALSO

L<Volity>

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003-2006 by Jason McIntosh.

=cut
