package Volity::Factory;

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

Volity::Factory - A Volity bot factory.

=head1 SYNOPSIS

 use Volity::Factory;
 
 # Create a new factory
 my $factory = Volity::Factory->new(
				 {
				  user=>'bob_the_factory',
				  password=>'secret',
				  host=>'volity.net',
				  resource=>'volity',
				  alias=>'volity',
				  ruleset_uri=>"http://example.com/my_game/",
				  ruleset_version=>"1.0",
				}
				);

 # And there you go.
 $factory->start;

 # ... elsewhere ...

 $factory->stop;

However, it is much more likely that you'll be starting a factory from
the C<volityd> program than from within code. Here's how you'd launch
the above factory from the command line (and without using a C<volityd>
config file):

 $ volityd -J bob_the_factory@volity.net/volity -G MyFactory::Class -p secret

=head1 DESCRIPTION

An object of this class is a Volity bot factory. As the synopsis
suggests, it's more or less a black-box application class. Construct
the object with a configuratory hash reference, call the C<start>
method, and if XMPP authentication worked out, you've got a running
server.

You generally never need to use this class directly, no matter what
you're doing with Volity. Factory objects of this class are created and
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

=item ruleset_uri

The URI of the Volity ruleset that this bot factory supports.

=item ruleset_version

The version number of the ruleset that this bot factory supports.

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

=item startup_time

Returns the time (in seconds since the epoch) when this palor started.

=back

=cut

use base qw(Volity::Jabber);
use fields qw(description website ruleset_uri ruleset_version bot_count bots bot_configs contact_email contact_jid volity_version visible startup_time admins in_graceful_shutdown volityd_command volityd_cwd volityd_argv reconnection_alarm_id);

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
use Time::HiRes qw(gettimeofday);

# Set some magic numbers.
our $RECONNECTION_TIMEOUT = 5;

sub initialize {
  my $self = shift;
  $self->SUPER::initialize(@_);

  $self->{bots} = [];
  $self->startup_time(time);
  $self->{admins} ||= [];
  $self->bot_count(0);
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
	ext => "factory",
	ver => "1.0",
    };
    return $self->SUPER::send_presence($config);
}

# start: run the kernel.
sub start {
  my $self = shift;
  $self->logger->info("Factory started.");
  $self->kernel->run;
}

sub stop {
  my $self = shift;
  $self->logger->info("Factory stopped.");
  $self->kernel->post($self->alias, 'shutdown_socket', 0);
  foreach (grep (defined($_), $self->bots)) {
    $_->stop;
  }
}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  my $ok = 0;
  if ($$rpc_info{'method'} eq 'volity.new_bot') {
      $self->new_bot($$rpc_info{from}, $$rpc_info{id}, @{$$rpc_info{args}});
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
				   "You are not allowed to make admin calls on this factory.",
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
}

sub new_bot {
    my $self = shift;
    my ($from, $rpc_id, $algorithm_uri, $table_jid) = @_;

    # Do various fault-checks.
    if (not(defined($algorithm_uri)) || not(defined($table_jid))) {
	$self->send_rpc_fault($from,
			      $rpc_id,
			      604,
			      "You must apply an algorithm URI and a table JID to volity.new_bot()."
			      );
	return;
    }
    elsif (not(URI->new($algorithm_uri))) {
	$self->send_rpc_fault($from,
			      $rpc_id,
			      606,
			      "This does not look like a valid URI: $algorithm_uri",
			      );
	return;
    }
    # XXX Should check JID well-foredness, too.

    # Fetch the bot config that matches the requested algorithm URI.
    my ($bot_config) = grep($_->{class}->algorithm eq $algorithm_uri,
			    $self->bot_configs,
			    );
    
    unless ($bot_config) {
	$self->send_rpc_response($from, $rpc_id, ["volity.bot_not_available"]);
	return;
    }

    # Try to create the bot.
    if (my $bot = $self->create_bot($bot_config, $table_jid)) {
	$self->send_rpc_response($from, $rpc_id, ["volity.ok", $bot->jid]);
    } else {
	$self->send_rpc_fault($from, $rpc_id, 608, "I couldn't create a bot for some reason.");
    }

}

sub create_bot {
  my $self = shift;
  my ($bot_config, $table_jid) = @_;
  my $bot_class = $bot_config->{class};
  # Generate a resource for this bot to use.
  my $resource = $self->new_bot_resource;
    
  my $bot = $bot_class->new(
			    {
			     password=>$bot_config->{password},
			     resource=>$resource,
			     alias=>$resource,
			     muc_jid=>$table_jid,
			     user=>$bot_config->{username},
			     host=>$bot_config->{host},
			     jid_host=>$bot_config->{jid_host},
			     port=>$bot_config->{port} || "5222",
			    }
			 );
  $self->logger->info("New bot (" . $bot->jid . ") created by factory (" . $self->jid . ").");

  push (@{$self->{bots}}, $bot);

  return $bot;
}


sub add_bot {
  my $self = shift;
  my ($bot) = @_;
  croak("You must provide a bot object with the add_bot() method") unless $bot->isa("Volity::Bot");
  push (@{$self->{bots}}, $bot);
  return $bot;
}

sub remove_bot {
  my $self = shift;
  my ($bot) = @_;
  my @new_bots;
  for (my $i = 0; $i <= $self->bots - 1; $i++) {
      if ($self->{bots}->[$i] eq $bot) {
	  splice(@{$self->{bots}}, $i, 1);
	  # If we're admidst a graceful shutdown, and this was the last
	  # bot, then we stop too.
	  $self->graceful_shutdown_check;
	  return;
      }
  }   
  $self->logger->error("I was asked to remove the bot with jid " . $bot->jid . " but I can't find it!!");
}

sub graceful_shutdown_check {
    my $self = shift;
    if ($self->in_graceful_shutdown and not(scalar(@{$self->{bots}}))) {
	$self->logger->info("No bots left, and we're in graceful-shutdown mode, so I'm going away now.");
	exit;
    }
}

sub new_bot_resource {
  my $self = shift;
  return $$ . "_" . gettimeofday();
}

sub wall {
    my $self = shift;
    my ($message) = @_;
    for my $bot ($self->bots) {
	$bot->groupchat("Server message: $message");
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
							 type=>'factory',
						     });
    my $node = $query->attr('node');
    if (not(defined($node))) {
	$identity->name("Bot factory for " . $self->ruleset_uri);
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
	if ($_ eq 'volity-role') {
	    $value = 'factory';
	}
	elsif ($_ eq 'ruleset') {
	    $value = $self->ruleset_uri;
	}
	else {
	    $value = $self->$_;
	}
	my @values = (ref($value) and ref($value) eq 'ARRAY')?
	    @$value : ($value);
	$field_name = $_;
	$field_name =~ s/_/-/g;
	warn "I am setting -$field_name- to @values.";
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

    if (defined($nodes[0])) {
	if ($nodes[0] eq 'bots') {
	    foreach ($self->bot_configs) {
		my $algorithm = $_->{class}->algorithm;
		my $name = $_->{class}->name || "Anonymous bot";
		push (@items, Volity::Jabber::Disco::Item->new({
		    jid  => $self->jid,
		    node => $algorithm,
		    name => $name,
		}));
	    }
	}
    } else {
	# Just return the two node we support.
	foreach (qw(bots)) {
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
