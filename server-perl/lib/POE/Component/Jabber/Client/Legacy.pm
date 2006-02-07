package POE::Component::Jabber::Client::Legacy;
use POE::Preprocessor;
const XNode POE::Filter::XML::Node
use warnings;
use strict;

use POE qw/ Wheel::ReadWrite Component::Client::TCP /;
use POE::Component::Jabber::Error;
use POE::Filter::XML;
use POE::Filter::XML::Node;
use POE::Filter::XML::NS qw/ :JABBER :IQ /;
use Digest::SHA1 qw/ sha1_hex /;

our $VERSION = '1.1.1';

sub new()
{
	my $class = shift;
	my $me = $class . '->new()';
	die "$me requires an even number of arguments" if(@_ & 1);
	
	my $args = {};
	while($#_ != -1)
	{
		my $key = lc(shift(@_));
		my $value = shift(@_);
		if(ref($value) eq 'HASH')
		{
			my $hash = {};
			foreach my $sub_key (keys %$value)
			{
				$hash->{lc($sub_key)} = $value->{$sub_key};
			}
			$args->{$key} = $hash;
			next;
		}
		$args->{$key} = $value;
	}

	$args->{'alias'} = $class unless defined $args->{'alias'};
	$args->{'xmlns'} = +NS_JABBER_CLIENT;
	$args->{'stream'} = +XMLNS_STREAM unless defined $args->{'stream'};
	$args->{'plaintext'} = 0 unless defined $args->{'plaintext'};
	$args->{'debug'} = 0 unless defined $args->{'debug'};
	$args->{'resource'} = &sha1_hex(time().rand().$$.rand().$^T.rand())
		unless defined $args->{'resource'};
	
	die "$me requires USERNAME to be defined" if not defined
		$args->{'username'};
	die "$me requires PASSWORD to be defined" if not defined
		$args->{'password'};
	
	die "$me requires InputEvent to be defined" if not defined
		$args->{'states'}->{'inputevent'};
	die "$me requires ErrorEvent to be defined" if not defined
		$args->{'states'}->{'errorevent'};
	die "$me requires InitFinish to be defined" if not defined
		$args->{'states'}->{'initfinish'};

	POE::Component::Client::TCP->new
	(
		SessionParams => 
		[ 
			options => 	
			{ 
				debug => $args->{'debug'}, 
				trace => $args->{'debug'} 
			} 
		],
		
		RemoteAddress => $args->{'ip'},
		RemotePort => $args->{'port'},
		ConnectTimeout => 160,
		
		Filter => 'POE::Filter::XML',

		Connected => \&init_connection,
		Disconnected => \&disconnected,

		ServerInput => \&init_input_handler,
		ServerError => \&server_error,

		InlineStates => {
			output_handler => \&output_handler,
			shutdown_socket => \&shutdown_socket,
			set_auth => \&set_auth,
			return_to_sender => \&return_to_sender,
			reconnect_to_server => \&reconnect_to_server,
		},
		
		Alias => $args->{'alias'},
		Started => \&start,
		Args => [ $args ],
	);
}

sub reconnect_to_server()
{
	my ($kernel, $heap, $session, $ip, $port) =
		@_[KERNEL, HEAP, SENDER, ARG0, ARG1];
	
	$kernel->state('got_server_input', \&init_input_handler);
	$heap->{'PENDING'} = {};
	$heap->{'sid'} = 0;
	$heap->{'id'}->reset();
	$heap->{'id'}->add(time().rand().$$.rand().$^T.rand());

	if(defined($ip) and defined($port))
	{
		$kernel->yield('connect', $ip, $port);

	} else {

		$kernel->yield('reconnect');
	}
}

sub return_to_sender()
{
	my ($self, $kernel, $heap, $session, $event, $node) = 
		@_[SESSION, KERNEL, HEAP, SENDER, ARG0, ARG1];
	
	my $attrs = $node->get_attrs();
	my $pid;

	if(exists($attrs->{'id'}))
	{
		if(exists($heap->{'PENDING'}->{$attrs->{'id'}}))
		{
			warn "COLLISION DETECTED!";
			warn "OVERRIDING USER ID!";

			$pid = $heap->{'id'}->add($heap->{'id'}->clone()->hexdigest())
				->clone()->hexdigest();

			$node->attr('id', $pid);
		}

		$pid = $attrs->{'id'};
	
	} else {

		$pid = $heap->{'id'}->add($heap->{'id'}->clone()->hexdigest())
			->clone()->hexdigest();

		$node->attr('id', $pid);
	}

	$heap->{'PENDING'}->{$pid}->[0] = $session->ID();
	$heap->{'PENDING'}->{$pid}->[1] = $event;
	
	$kernel->yield('output_handler', $node);
}

sub set_auth()
{
	my ($self, $kernel, $heap) = @_[SESSION, KERNEL, HEAP];

	my $node = XNode->new('iq', ['type', +IQ_SET, 'id', 'AUTH']);
	my $query = $node->insert_tag('query', ['xmlns', +NS_JABBER_AUTH]);
	$query->insert_tag('username')->data($heap->{'CONFIG'}->{'username'});

	if($heap->{'CONFIG'}->{'plaintext'})
	{
		$query->insert_tag('password')->data($heap->{'CONFIG'}->{'password'});
	
	} else {

		my $hashed = sha1_hex($heap->{'sid'}.$heap->{'CONFIG'}->{'password'});
		$query->insert_tag('digest')->data($hashed);
	}
	
	$query->insert_tag('resource')->data($heap->{'CONFIG'}->{'resource'});

	$kernel->yield('output_handler', $node);

	$heap->{'jid'} = $heap->{'CONFIG'}->{'username'}.'@'.
	$heap->{'CONFIG'}->{'hostname'}.'/'.
	$heap->{'CONFIG'}->{'resource'};
}

sub start()
{
	my ($heap, $config) = @_[HEAP, ARG0];
	
	$heap->{'CONFIG'} = $config;
	$heap->{'PENDING'} = {};
	$heap->{'id'} = Digest::SHA1->new();
	$heap->{'id'}->add(time().rand().$$.rand().$^T.rand());
	$heap->{'sid'} = 0;
}

sub init_connection()
{
	my ($kernel, $heap) = @_[KERNEL, HEAP];

	my $cfg = $heap->{'CONFIG'};
	
	my $element = XNode->new('stream:stream',
	['to', $cfg->{'hostname'}, 
	'xmlns', $cfg->{'xmlns'}, 
	'xmlns:stream', $cfg->{'stream'}]
	)->stream_start(1);

	$kernel->yield('output_handler', $element);

	return;
}

sub disconnected()
{
	$_[KERNEL]->post($_[HEAP]->{'CONFIG'}->{'state_parent'},
		$_[HEAP]->{'CONFIG'}->{'states'}->{'errorevent'},
		+PCJ_SOCKDISC);
}

sub shutdown_socket()
{
	my ($kernel, $time) = @_[KERNEL, ARG0];

	$kernel->delay('shutdown', $time);
	return;
}

sub output_handler()
{
	my ($heap, $data) = @_[HEAP, ARG0];

	if ($heap->{'CONFIG'}->{'debug'})
	{
		my $xml;
		if (ref $data eq 'XNode')
		{
			$xml = $data->to_str();
		} else {
			$xml = $data;
		}
		
		debug_message( "Sent: $xml" );
	}

	$heap->{'server'}->put($data);
	return;
}

sub input_handler()
{
	my ($self, $kernel, $heap, $node) = @_[SESSION, KERNEL, HEAP, ARG0];
	my $attrs = $node->get_attrs();

	if ($heap->{'CONFIG'}->{'debug'})
	{
		debug_message( "Recd: ".$node->to_str() );
	}

	if(exists($attrs->{'id'}))
	{
		if(defined($heap->{'PENDING'}->{$attrs->{'id'}}))
		{
			my $array = delete $heap->{'PENDING'}->{$attrs->{'id'}};
			$kernel->post($array->[0], $array->[1], $node);
			return;
		}
	}
	
	$kernel->post($heap->{'CONFIG'}->{'state_parent'},
		$heap->{'CONFIG'}->{'states'}->{'inputevent'} , $node);
	return;
}

sub init_input_handler()
{
	my ($self, $kernel, $heap, $node) = @_[SESSION, KERNEL, HEAP, ARG0];

	if ($heap->{'CONFIG'}->{'debug'})
	{
		debug_message( "Recd: ".$node->to_str() );
	}
	
	if($node->name() eq 'stream:stream')
	{
		$heap->{'sid'} = $node->attr('id');
		$kernel->yield('set_auth');
		return;
	
	} elsif($node->name() eq 'iq') {
	
		if($node->attr('type') eq +IQ_RESULT and $node->attr('id') eq 'AUTH')
		{
			$kernel->state('got_server_input', \&input_handler);
			$kernel->post($heap->{'CONFIG'}->{'state_parent'},
				$heap->{'CONFIG'}->{'states'}->{'initfinish'}, 
				$heap->{'jid'});
			return;
		
		} elsif($node->attr('type') eq +IQ_ERROR and 
			$node->attr('id') eq 'AUTH') {

			warn "AUTHENTICATION FAILED";
			$kernel->yield('shutdown');
			$kernel->post($heap->{'CONFIG'}->{'state_parent'},
				$heap->{'CONFIG'}->{'states'}->{'errorevent'},
				+PCJ_AUTHFAIL);
		}
	}
}

sub server_error()
{
	my ($kernel, $heap, $call, $code, $err) = @_[KERNEL, HEAP, ARG0..ARG2];
	
	warn "Server Error: $call: $code -> $err\n";
	$kernel->post($heap->{'CONFIG'}->{'state_parent'},
		$heap->{'CONFIG'}->{'states'}->{'errorevent'},
		+PCJ_SOCKFAIL, $call, $code, $err);
}

sub debug_message()
{
        my $message = shift;
	print STDERR "\n", scalar (localtime (time)), ": $message\n";
}

1;

__END__

=pod

=head1 NAME

POE::Component::Jabber::Client::Legacy - A POE Component for communicating over Jabber

=head1 SYNOPSIS

 use POE qw/ Component::Jabber::Client::Legacy Component::Jabber::Error /;
 use POE::Filter::XML::Node;
 use POE::Filter::XML::NS qw/ :JABBER :IQ /;

 POE::Component::Jabber::Client::Legacy->new(
   IP => 'jabber.server',
   PORT => '5222'
   HOSTNAME => 'jabber.server',
   USERNAME => 'username',
   PASSWORD => 'password',
   ALIAS => 'POCO',
   STATE_PARENT => 'My_Session',
   STATES => {
	 INITFINISH => 'My_Init_Finished',
	 INPUTEVENT => 'My_Input_Handler',
	 ERROREVENT => 'My_Error_Handler',
   }
 );
 
 $poe_kernel->post('POCO', 'output_handler, $node);
 $poe_kernel->post('POCO', 'return_to_sender', $node);

=head1 DESCRIPTION

POE::Component::Jabber::Client::Legacy is a simple connection broker to enable
communication using the legacy Jabber protocol prior to the IETF Proposed
Standard XMPP. All of the steps to initiate the connection and authenticate
inband with plain text or DIGEST-SHA1 are handled for the end developer. Once
INITFINISH is fired the developer has a completed Jabber connection for which
to send either raw XML or POE::Filter::XML::Nodes.

=head1 METHODS

=over 4

=item new()

Accepts many arguments: 

=over 2

=item IP

The IP address in dotted quad, or the FQDN for the server.

=item PORT

The remote port of the server to connect.

=item HOSTNAME

The hostname of the server. Used in addressing.

=item USERNAME

The username to be used in inband authentication.

=item PASSWORD

The password to be used in inband authentication.

=item RESOURCE

The resource used in inband authentiation and addressing.

=item PLAINTEXT

If bool true, sends the password as plain text over the wire.

=item ALIAS

The alias the component should register for use within POE. Defaults to
the class name.

=item STATE_PARENT

The alias or session id of the session you want the component to contact.

=item STATES

A hashref containing the event names the component should fire upon finishing
initialization and receiving input from the server. 

INITFINISH, INPUTEVENT, and ERROREVENT must be defined.

INITFINISH is fired after connection setup and authentication.

ARG0 in INITFINISH will be your jid as a string.
ARG0 in INPUTEVENT will be the payload as a POE::Filter::XML::Node.
ARG0 in ERROREVENT will be a POE::Component::Jabber::Error

See POE::Component::Jabber::Error for possible error types and constants.

See POE::Filter::XML and its accompanying documentation to properly manipulate
incoming data in your INPUTEVENT, and creation of outbound data.

=item DEBUG

If bool true, will enable debugging and tracing within the component. All XML
sent or received through the component will be printed to STDERR

=back

=back

=head1 EVENTS

=over 4

=item 'output_handler'

This is the event that you use to push data over the wire. Accepts either raw
XML or POE::Filter::XML::Nodes.

=item 'return_to_sender'

This event takes (1) a POE::Filter::XML::Node and gives it a unique id, and 
(2) a return event and places it in the state machine. Upon receipt of 
response to the request, the return event is fired with the response packet.

=item 'shutdown_socket'

One argument, time in seconds to call shutdown on the underlying Client::TCP

=item 'reconnect_to_server'

This event can take (1) the ip address of a new server and (2) the port. This
event may also be called without any arguments and it will force the component
to reconnect. 

=back

=head1 NOTES AND BUGS

This is a connection broker. This should not be considered a first class
client. All upper level functions are the responsibility of the end developer.

return_to_sender() no longer overwrites end developer supplied id attributes. 
Instead, it now checks for a collision, warning and replacing the id, if there 
is a collision.

=head1 AUTHOR

Copyright (c) 2003, 2004, 2005 Nicholas Perez. Distributed under the GPL.

=cut
