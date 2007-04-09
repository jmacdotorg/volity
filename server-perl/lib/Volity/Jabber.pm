package Volity::Jabber;
use Carp;

use Data::Dumper;

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

# This is a superclass for Volity objects, giving them super-duper Jabber
# powers. Poe::Component::Jabber powers, actually.

# Annoyingly "perldoc" doesn't support =encoding even though it's documented
# in perlpod.  It does make you wonder _who_ supports it of perldoc
# doesn't...

=encoding utf8

=begin TODO

The roster should be easier to use, by way of more methods. Make
methods for retrieving the online and offline JIDS.

=end TODO

=cut

=head1 NAME

Volity::Jabber - a base class for Jabber-speaking Volity objects

=head1 SYNOPSIS

 package My::Volity::Object;
 use base qw(Volity::Jabber);
 use fields qw(wubba_wubba);

 # Override the parent's initialize method to set values on construction.
 sub initialize {
   my $self = shift;
   $self->SUPER::initialize(@_);   # Don't forget to call the parent's init!
   # Initialization goes here
   $self->wubba_wubba('grink gronk');
 }

 # An example chat handler, defined by the base class
 sub handle_groupchat_message {
   my $self = shift;
   my ($message) = @_;  # A hashref with info about the incoming message.
   # Send a debug message.
   $self->logger->debug(sprintf("%s says, '%s'\n", $$message{from}, $$message{body}));
   # More use message-handling code goes here.
 }

=head1 DESCRIPTION

This package provides a base class for Volity objects that speak
Jabber. These objects will automatically connect to (and authenticate
with ) a Jabber server on construction, and then provide some methods
for doing some common jabbery things, as well as access the POE
kernel.

=head1 USAGE

=head2 For game authors

You don't need to know much of anything at this level. Here be dragons.

Stick to the modules listed in L<Volity/"Modules of interest to game
developers">, especially C<Volity::Game>.

=head2 For deep-voodoo wizards

To use this module, write an object class that subclasses
C<Volity::Jabber>, then override any event-handling methods which
should perform some action other than the default (which is usually a
no-op). See L<"CALLBACK METHODS"> for a list of these handlers.

Commonly, you'll want to respond to incoming Jabber stanzas by firing
off some stanzas of your own, and the methods listed under L<"JABBER
ACTION METHODS"> can help you here.

Keep in mind that every instance of your class will represent a
separate connection to a Jabber server. So, a single object can
represent a game server, a client connection, or a "bot", among other
things.

=head2 Object construction and initialization

The module inherits from Class::Accessor::Fields, so using it means
using the C<base> and C<fields> pragmas, respectively. If you don't
like this behavior, you can just overload the new() method. Otherwise,
you don't need to define new() at all.

If you want to initialize your object, override the C<initialize>
method. however, you B<must> call C<SUPER::initilialize>, otherwise
the connection won't open.

=head2 Localization

This module supports language localization compliant with the core
Jabber protocol. All stanzas automatically get an C<xml:lang>
attribute set on their top-level element (such as C<< <message> >> or
C<< <presence> >>) whose value is the object's current
C<default_language> value (C<en> being the default's default, and
you can change this through the C<default_language> accessor method).

Some methods, such as C<send_message> (described under L<"JABBER
ACTION METHODS">), allow you to specify either plain text strings in
the default language, or localized text strings in several
languages. To provide localized strings, pass the method a hash
reference where'd you'd normally pass in a plain string. The hash's
keys should be ISO 639-compliant two-letter language codes, with
their corresponding localized text as values. For example:

 { 
   'en' => "This is some localized text.",
   'es' => "Éste es un poco de texto localizado.",
   'fr' => "C'est un certain texte localisé.",
   'ru' => "Это будет некоторый локализованный текст.",
 }

Do make sure that you "use utf8" and enter UTF-8 only text if you're using
anything other then ASCII.  Remember, Latin-1 is NOT compatible with UTF-8.

If you aren't concerned at all with localization, you can simply
ignore all these methods and techniques, and nothing will break. So,
in the same place as you'd use the above hashref, you could instead
simply use a string like this:

 "This string is available in English only. C'est la vie."

=cut

use base qw(Volity);

use warnings; no warnings qw(deprecated);
use strict;

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
           Component::Jabber::Error
           Component::Jabber::ProtocolFactory
           Component::Jabber::Status
	  );
use POE::Filter::XML::Node;

use PXR::NS qw(:JABBER :IQ);
use Scalar::Util qw(weaken);
use Carp qw(croak);
use RPC::XML::Parser;

=head1 METHODS

=head2 Accessors

All these accessor methods take the same form, as defined by L<Class::Accessor>: all return a scalar value (or C<undef>) representing a current value set on the object, and will set this value first if called with an argument (which can be a scalar or a list, if appropriate).

Also in Class::Accessor style, you can set any of these values during construction by passing them in a hashref to the C<new> method.

=over

=item kernel

The current POE kernel. You shouldn't need to access this much, but it's there if you need it.

=item alias

The 'nickname' under which this object's own POE session will be known to the kernel. You should set this to something meaningful, and unique to the application.

=item host

The Jabber server's hostname (or IP address).

=item jid_host

The connection's concept of the hostname part of its Jabber ID. This
is almost always identical to the value of the C<host> key, but if
there's some proxy-connection magic afoot, these may be different.

=item port

The Jabber server's TCP port.

=item user

The username to use when connecting to the Jabber server.

=item password

The password to use when connecting to the Jabber server.

=item resource

The resource identifier to use when connecting to the Jabber server. (This is the part that gets stuck on the end of the JID, after the slash. So, setting this to "foo" will result in a JID like "bob@somejabberserver.com/foo")

=item default_language

The two-letter language code that the object will attach to all
outgoing Jabber stanzas to identify their default language. Defaults
to C<en>. (See L<"Localization"> more more information about how this
module handles different langauges.)

=item jid (read-only)

After connection, this will return the connection's JID.

=item basic_jid (read-only)

Like C<jid>, except it returns the non-resource part of the JID. (e.g. C<foo@bar.com> versus C<foo@bar.com/bazzle>.)

=back

=cut

use fields qw(kernel main_session_id alias host jid_host port user resource password jid rpc_parser default_language query_handlers roster iq_notification last_id response_handler error_notification last_node);

sub initialize {
    my $self = shift;
    $self->{kernel} = $poe_kernel;
    $self->{port} ||= 5222;
    $self->logger->debug("STARTING init. Password is " . $self->password);
    POE::Session->create(
			 object_states=>
			 [$self=>
			  [qw(jabber_iq jabber_presence _start jabber_message input_event status_event error_event)],
			  ],
			 );

    # Weaken some variables to prevent circularities & such.
    foreach (qw(kernel)) {
	weaken($self->{$_});
    }

    foreach (qw(user host resource)) {
	unless ($self->$_) {
	    die "Failed to make a Jabber connection with $self, because the $_ field is empty.";
	}
    }
    
    $self->jid(sprintf("%s@%s/%s", $self->user, $self->jid_host || $self->host, $self->resource));
    $self->rpc_parser(RPC::XML::Parser->new);
    $self->default_language('en') unless defined($self->default_language);
    
    # Give initial values to instance variables what needs 'em.
    $self->{query_handlers} = {
	'jabber:iq:roster'=>{
	    result => 'receive_roster',
	    set => 'update_roster',
	},
	'http://jabber.org/protocol/disco#items'=>{
	    get    => 'handle_disco_items_request',
	    result => 'receive_disco_items',
	},
	'http://jabber.org/protocol/disco#info'=>{
	    get    => 'handle_disco_info_request',
	    result => 'receive_disco_info',
	},
	'jabber:iq:register'=>{
	    error  => 'receive_registration_error'
	    },
	    };
    
    $self->{iq_notification} = {};
    $self->{last_id} = 0;
    
    return $self;
}

sub set_iq_notification {
  my $self = shift;
  my ($id, $methods) = @_;
  unless (ref($methods) eq 'HASH') {
    croak("The second arg to set_iq_notification must be a hashref.");
  }
  $self->{iq_notification}->{$id} = $methods;
}

sub next_id {
  my $self = shift;
  return ++$self->{last_id};
}

# post_node: send a given XML node object to the server.
# Rewrite this sub if our core Jabber/POE thing changes.
sub post_node {
  my $self = shift;
  my ($node) = @_;
  # I always set the stanza-level xml:lang attribute here.
  # Is it a bit much? Not sure. It's easy, anyway, and I figure it can't hurt.
  $node->attr('xml:lang'=>$self->default_language);
  $self->last_node($node);
  $self->kernel->post($self->alias, 'output_handler', $node);
}

################################
# POE States (core)
################################

sub _start {
    my $self = $_[OBJECT];
    my ($kernel, $session, $heap) = @_[KERNEL, SESSION, HEAP];
    $self->main_session_id($session->ID);
    $self->start_jabber_client;
}

sub start_jabber_client {
    my $self = shift;
    my $alias = $self->alias;
    unless (defined($self->alias)) {
	die "You haven't set an alias on $self! Please do that when constucting the object.";
    }

    foreach (qw(host jid_host port user password resource)) {
	unless (defined($self->$_)) {
	    $self->expire("I can't start the Jabber connection without my '$_' field defined. Please define it and try again.");
	}
    }

    my %config = (
		  Alias=>$alias,
#		  STATE_PARENT=>$self->main_session_id,
		  States=>{
                           StatusEvent=>'status_event',
                           InputEvent=>'input_event',
                           ErrorEvent=>'error_event',
                       },
#		  XMLNS => +NS_JABBER_CLIENT,
#		  STREAM => +XMLNS_STREAM,
		  IP=>$self->host,
		  Hostname=>$self->jid_host,
		  Port=>$self->port,
                  ConnectionType => +LEGACY,
		  Username=>$self->user,
		  Password=>$self->password,
		  Resource=>$self->resource,
		  );


    POE::Component::Jabber->new(%config);

    $poe_kernel->post($alias, 'connect');


}

################################
# POE States (Jabber)
################################

sub status_event {
  my $self = $_[OBJECT];
  my $event = $_[ARG0];
  if ($event == +PCJ_INIT_FINISHED) {
      $self->logger->debug("I got an init finished event!");
      $self->init_finish;
  }
  else {
#      $self->logger->debug("I got some other kind of status update event!");
  }
}

sub init_finish {
    my $self = shift;
    $self->kernel->post($self->alias, 'set_auth', 'jabber_authed', $self->user, $self->password, $self->resource);

    # Always request roster. The roster's receipt will trigger an 'available'
    # presence packet (see 'receive_roster').
    $self->request_roster;
}

sub input_event {
  my $self = $_[OBJECT];
  my $node = $_[ARG0];
  my $element_type = $node->name;
  my $method = "jabber_$element_type";
  $method =~ s/\W/_/g;
  if ($self->can($method)) {
      $self->$method($node);
  }
  elsif ($node->to_str eq "</stream:stream>") {
      $self->logger->warn("The Jabber stream shut down!");
  } else {
      $self->logger->error("Got an input event that I have no idea how to handle, so I'll ignore it and chug merrily along. Who knows what will happen next?\nThis was the XML:\n" . $node->to_str);
  }
}

sub error_event {
  my $self = $_[OBJECT];
  my $error = $_[ARG0];
  
  my $error_message;
  if($error == +PCJ_SOCKETFAIL)  {
      my ($call, $code, $err) = @_[ARG1..ARG3];
      $error_message = "Socket error: $call, $code, $err\n";
  } elsif ($error == +PCJ_SOCKETDISCONNECT) {
      $error_message = "We got disconnected.\n";
      $self->react_to_disconnection_error;
  } elsif ($error == +PCJ_AUTHFAIL) {
      $error_message = "Failed to authenticate\n";
  } elsif ($error == +PCJ_BINDFAIL) {
      $error_message = "Failed to bind a resource\n"; # XMPP/J2 Only
  } elsif ($error == +PCJ_SESSIONFAIL) {
      $error_message = "Failed to establish a session\n"; # XMPP Only
  } else {
      $error_message = "Unknown PCJ Error: $error";
  }

  $self->logger->warn($error_message);
}

# react_to_disconnection_error: Called as a result of the error_event method
# getting notifcation that the Jabber stream has closed.
# By default, it does nothing at all, and the object quietly accepts its fate.
# Subclasses can ovveride this in order to do other things, such as attempt to
# reconnect to the server
sub react_to_disconnection_error { }

# Actually, these are all just stubs. It's up to subclasses for making
# these do real stuff.

=head1 CALLBACK METHODS

=head2 Element-handling methods

All these object methods are called with a single argument: the XML
node that triggered them. See L<POE::Filter::XML::Node> for more about
this node object's API.

=over

=item jabber_presence

Called when a presence element is received.

=cut

sub jabber_presence { }

sub jabber_authed { }

=item jabber_iq

Called when a an IQ element is received.

If you override this, you should call SUPER::jabber_iq within the
method, since the base class already does a lot of work with incoming
IQ elements, such as handling RPC requests and responses.

=cut

# The IQ-handler checks for a bunch of special query types, like RPC calls.
# These then get delegated to other methods.
# Subclasses that ovveride this method should take care to call
# SUPER::jabber_iq in their own version of the method.

# This is a little sloppy, with namespace-handling. Er, sloppy? I meant to 
# say, "transitional".
sub jabber_iq {
  my $self = shift;
  $self->logger->debug("I ($self) got an IQ object.\n");
  my ($node) = @_;
#  warn $node->to_str;
  my $id = $node->attr('id'); my $from_jid = $node->attr('from');
  $id ||= $self->next_id;
  my $query;
  # Check to see if we should dispatch this to a predefined NS handler
  # method.
  return if $self->handle_query_element_ns($node);
  if ($node->attr('type') eq 'result') {
    if ($query = $node->get_tag('query') and $query->attr('xmlns') eq 'jabber:iq:rpc') {
      # Yep, that's an RPC response.
      my $raw_xml = join("\n", map($_->to_str, @{$query->get_children}));

      massage_rpc_numbers(\$raw_xml);

      # We should be getting only RPC responses, not requests.
      my $response_obj = $self->rpc_parser->parse($raw_xml);
      unless (ref($response_obj)) {
	  $self->logger->warn("Failed to parse this response: $raw_xml");
	  return;
      }
      $self->logger->debug("The response is: " . Dumper($response_obj->value->value) . "\n");
      if ($response_obj->value->is_fault) {
	$self->handle_rpc_fault({
				 id=>$id,
				 fault_code=>$response_obj->value->code,
				 fault_string=>$response_obj->value->string,
				 rpc_object=>$response_obj,
				 from=>$from_jid,
				});
      } else {
	$self->handle_rpc_response({id=>$id,
				    response=>$response_obj->value->value,
				    rpc_object=>$response_obj,
				    from=>$from_jid,
				   });
      }
    }
  } elsif ($node->attr('type') eq 'set') {
    if ($query = $node->get_tag('query') and $query->attr('xmlns') eq 'jabber:iq:rpc') {
      my $raw_xml = join("\n", map($_->to_str, @{$query->get_children}));

      # Hack, to deal with apparent RPC::XML bug?
      $raw_xml =~ s/<int\/>/<int>0<\/int>/g;

      massage_rpc_numbers(\$raw_xml);

      $self->logger->debug("$self got Apparent RPC XML from $from_jid: $raw_xml\n");
      my @kids = @{$query->get_children};
      my $rpc_obj = $self->rpc_parser->parse($raw_xml);
      unless (ref($rpc_obj)) {
	  die "Got bad rpc.\n$raw_xml";
      }
#      $self->logger->debug( "Finally, got $rpc_obj.\n");
      my $method = $rpc_obj->name;
      $self->handle_rpc_request({
				 rpc_object=>$rpc_obj,
				 from=>$from_jid,
				 id=>$id,
				 method=>$method,
				 args=>[map($_->value, @{$rpc_obj->args})],
			       });
    }
  } elsif ($node->attr('type') eq 'error') {
    if ($query = $node->get_tag('query') and $query->attr('xmlns') eq 'jabber:iq:rpc') {
      # This isn't an RPC fault, but an apparent error in trying to send the
      # RPC message at all.
      my $error_message = $node->get_tag('error')->data;
      my $code = $node->get_tag('error')->attr('code');
      $self->handle_rpc_transmission_error($node, $code, $error_message);
    } else {
      if (my $method = delete($self->{error_notification}->{$id})) {
	delete($self->{result_notification}->{$id});
	$self->$method($node);
      }
    }
  }
  if (my $methods = delete($self->{iq_notification}->{$id})) {
    if (my $method = $$methods{$node->attr('type')}) {
      $self->$method($node);
    }
  }
}

# error_with_node: convenience method that, given an XML node object and
# a string, sticks an error in the logger and also dumps the node as a
# string into it.
sub error_with_node {
    my $self = shift;
    my ($node, $error_message) = @_;
    $self->logger->error($error_message . "\nThe XML node in question was:\n" . $node->to_str);
}

# massage_rpc_numbers: Fixes Bug #1372065.
# Basically, if a <double> looks, walks and talks like an <int>, then
# an <int> it shall become.
sub massage_rpc_numbers {
    my ($raw_xml_ref) = @_;
    $$raw_xml_ref =~ s|<\s*double\s*>\s*(-?)(\d*?)\.0*\s*<\s*/\s*double\s*>|$2 ne '' ? "<int>$1$2</int>" : "<int>0<int>"|ge;
}

# Message handler! Figures out the message type, and calls a deleagating
# method.

sub jabber_message {
  my $self = shift;
  my ($node) = @_;
  my $info_hash;		# Will be the argument to the delegate method.
  my $type;			# What type of chat is this?
  $self->logger->debug( "I ($self) received a message...\n");

  foreach (qw(to from)) {
    $$info_hash{$_} = $node->attr($_);
  }
  foreach (qw(subject body thread)) {
    my $data;
    if (my $element = $node->get_tag($_)) {
      $data = $element->data;
    }
    $$info_hash{$_} = $data;
  }
  $type = $node->attr('type') || 'normal';
  my $method = "handle_${type}_message";
  $self->logger->debug( "Delegating it to the $method method.");
  $self->$method($info_hash);
  $self->logger->debug( "Done delegating to $method." );
}

=pod

The following related methods handle specific applications of the
<<iq>> element. As with C<jabber_iq>, the single argument in every
case is a POE::Filter::XML::Node object representing the triggering
XMPP <<iq>> element.

=item handle_disco_items

=item handle_disco_info

=item handle_disco_items_request

=item handle_disco_info_request

Define these methods in your subclass to let it respond to Jabber
service discovery (JEP-0030) requests and responses. The first two
methods handle other entities' response to requests that this one
sent; the latter two handle entities seeking disco information on this
object.

=back

=cut

# We could use some more useful stream-error handling...
sub jabber_stream_error { 
  my $self = shift;
  my ($node) = @_;
  $self->logger->debug("Got a jabber stream error. " . $node->to_str);
}
  

################################
# Jabber event delegate methods
################################

=head2 RPC handler methods

These methods are called by RPC events.

=over

=item handle_rpc_response({id=>$id, response=>$response, from=>$from, rpc_object=>$obj})

Called upon receipt of an RPC response. The argument is a hashref containing the response's ID attribute and response value, as well as an RPC::XML object representing the response.

=item handle_rpc_request({id=>$id, method=>$method, args=>[@args], from=>$from, rpc_object=>$obj})

Called upon receipt of an RPC request. The argument is a hashref containing the request's ID attribute, method, argument list (as an arrayref), and originating JID, as well as an RPC::XML object representing the request.

=item handle_rpc_fault({id=>$id, rpc_object=>$obj, from=>$from, fault_code=>$code, fault_string=>$string})

Called upon receipt of an RPC fault response.

=item handle_rpc_transmission_error($iq_node, $error_code, $error_message);

Called upon receipt of a Jabber IQ packet that's of type C<error>, but
that seems to contain a Jabber-RPC element. This usually means that
the RPC message failed to reach its destination for some reason. If
this reason is known, it will show up as a code and (maybe) a text
message in the callback's arguments.

Note that this is distinct from an RPC fault, which is something
returned from a network entity after successfully receiving an RPC
request.

=back

=cut

sub add_response_handler {
  my $self = shift;
  my ( $id, $sub ) = @_;
  my $response_handler = $self->{'response_handler'} ||= {};
  # If it's a code ref then store as is...
  if (ref $sub eq 'CODE') {
    $$response_handler{ $id } = $sub;
  } else { # else we assume it's a method name
    my $method = "rpc_response_$sub";
    if ( $self->can( $method ) ) {
      $$response_handler{ $id } = sub { $self->$method( @_ ) };
    } else {
      croak "Can't add response handler, unknown method $method.\n";
    }
  }
}

sub have_response_handler {
  my $self = shift;
  my ( $id ) = @_;
  return exists( $self->{'response_handler'}{ $id } ) ? 1 : 0;
}

sub call_response_handler {
  my $self = shift;
  my ( $id, $response ) = @_;
  return &{ $self->{'response_handler'}{ $id } }( $response );
}

sub delete_response_handler {
  my $self = shift;
  my ( $id ) = @_;
  delete( $self->{'response_handler'}{ $id } );
}

sub handle_rpc_response {
  my $self = shift;
  my ($message) = @_;
  if ($self->have_response_handler( $$message{'id'} )) {
    $self->call_response_handler( $$message{'id'}, $message );
    $self->delete_response_handler( $$message{'id'} );
  } else {
    $self->rpc_response_default( $message );
  }
}
# No default behavior for RPC stuff.
sub rpc_response_default { }
sub handle_rpc_request { }
sub handle_rpc_transmission_error { }
sub handle_rpc_fault { }

=head2 Message handler methods

All of the following methods are called with a single hashref as an argument, containing message information under the following keys: C<from>, C<to>, C<subject>, C<body>, C<thread>

=over

=item *

handle_normal_message

=item *

handle_groupchat_message

=item *

handle_chat_message

=item *

handle_headline_message

=item *

handle_error_message

=back

=cut

sub handle_normal_message { }
sub handle_groupchat_message { }
sub handle_chat_message { }
sub handle_headline_message { }
sub handle_error_message { }

# handle_query_element_ns:
# Returns truth if it performed a dispatch, falsehood otherwise.
sub handle_query_element_ns {
  my $self = shift;
  my ($node) = @_;
  my $element_type = $node->name;
  my $query_ns;
  if (my $query = $node->get_tag('query')) {
    $query_ns = $query->attr('xmlns');
  }  
  return unless defined($query_ns);

  $self->logger->debug("I am $self in handle_query_element_ns, for $query_ns...");
  return unless defined($query_ns);
  return unless defined($self->query_handlers);
  return unless defined($self->query_handlers->{$query_ns});

  $self->logger->debug("I'm handling a query of the $query_ns namespace.");

  if ($element_type eq 'iq') {
    # Locate a dispatch method, depending upon the type of the iq.
    my $method;
    my $type = $node->attr('type');
    unless (defined($type)) {
      $self->error_with_node($node, "No type attribute defined in query's parent node! Gak!");
      return;
    }
    $method = $self->query_handlers->{$query_ns}->{$type};
    if (defined($method)) {
      $self->logger->debug("Trying to call the $method method.");
      if ($self->can($method)) {
	$self->$method($node);
	return 1;
      } else {
	croak("I wanted to dispatch to the $method method, but I have no such method defined!");
      }
    } else {
      # No method for this situation is set; we'll return undef.
      # This probably will return control to the jabber_iq method.
      return;
    }
  } else {
    $self->error_with_node($node, "handle_query_element_ns called with a non-iq element. It was a $element_type.");
  }
}
    
################################
# Jabber element-sending methods
################################

=head1 JABBER ACTION METHODS

These methods will send messages and other data to the Jabber server.

=head2 send_rpc_request($args_hashref)

Send an RPC request. The single argument is a hashref with the following keys:

=over

=item to

The JID to which this request should be sent.

=item id

The ID of this request. (The RPC result will have the same ID.)

=item methodname

The name of the remote method to call.

=item args

The method's arguments, as a list reference. If there's only one
argument, and it's not itself an array reference, you can optionally
pass it in by itself. If there are no arguments, you can pass C<undef>
or just skip this key.

Each argument must be either a simple scalar, a hashref, or an
arrayref. In the latter cases, the argument will turn into an RPC
struct or array, respectively. All the datatyping magic is handled by
the RPC::XML module (q.v.).

=item handler

This is the response handler.  It's executed when we get an answer back.  If
it isn't passed then the default handler is used (which does nothing unless
overridden).  It can either be a CODE ref or the name of a premade response
handler.  CODE refs are passed only the response.  Premade response handler
are not provided here but may be available in subclasses.  The method name of
the handler is in the form "rpc_response_$handler".  So if $handler was
"start_game" then the method containing the response handler would be
"rpc_response_start_game".  Premade response handlers are called as methods
with the response as their argument.

=back

=cut

*make_rpc_request = \&send_rpc_request;

sub send_rpc_request {
  my $self = shift;
  $self->logger->debug("in make_rpc_request\n");
  my ($args) = @_;
  my $iq = POE::Filter::XML::Node->new('iq');  
  foreach (qw(to id)) {
      unless (defined($$args{$_})) {
	  $self->expire("send_rpc_request called without an $_ argument.");
      }
      $iq->attr($_, $$args{$_});
  }
  $iq->attr(type=>'set');
  my @args;
  if (defined($$args{args})) {
    if (ref($$args{args}) and ref($$args{args}) eq 'ARRAY') {
      @args = @{$$args{args}};
    } else {
      @args = ($$args{args});
    }
  } else {
    @args = ();
  }

  if ( exists $$args{'handler'} ) {
      $self->add_response_handler( $$args{'id'}, $$args{'handler'} );
  }

  my $request = RPC::XML::request->new($$args{methodname}, @args);
  $self->logger->debug("The request is $$args{methodname}, and the args: @args");
  $self->logger->debug("It's going out to $$args{to}.");

  # I don't like this so much, sliding in the request as raw data.
  # But then, I can't see why it would break.
  my $request_xml = $request->as_string;
  $request_xml =~ s/^<\?\s*xml\s.*?\?>//;
  $iq->insert_tag('query', [xmlns=>'jabber:iq:rpc'])->
    rawdata($request_xml);
  $self->logger->debug("Full, outgoing RPC request:\n" . $iq->to_str);
  $self->post_node($iq);
}

=head2 send_rpc_response ($receiver_jid, $response_id, $response_value)

Send an RPC response. The value can be any scalar.

=cut

sub send_rpc_response {
  my $self = shift;
  my ($receiver_jid, $id_attr, $value) = @_;
  my $response = RPC::XML::response->new($value);
  my $rpc_iq = POE::Filter::XML::Node->new('iq');
  $rpc_iq->attr(type=>'result');
  $rpc_iq->attr(from=>$self->jid);
  $rpc_iq->attr(to=>$receiver_jid);
  $rpc_iq->attr(id=>$id_attr);
  # I don't like this so much, sliding in the response as raw data.
  # But then, I can't see why it would break.
  my $response_xml = $response->as_string;
  # This s/// chops off the XML prolog.
  # (Ugly, yes. Suggestions welcome.)
  $response_xml =~ s/^<\s*\?\s*xml\s.*?\?\s*>//;
  $rpc_iq->insert_tag(query=>[xmlns=>'jabber:iq:rpc'])
    ->rawdata($response_xml);
  $self->logger->debug("Sending response: " . $rpc_iq->to_str);
  $self->post_node($rpc_iq);
  return 1;
}

=head2 send_rpc_fault ($receiver_jid, $response_id, $fault_code, $fault_string)

Send an RPC fault.

=cut

sub send_rpc_fault {
  my $self = shift;
  my ($receiver_jid, $response_id, $fault_code, $fault_string) = @_;
  my $fault = RPC::XML::fault->new($fault_code, $fault_string);
  $self->send_rpc_response($receiver_jid, $response_id, $fault);
}

=head2 send_message($args_hashref)

Send a Jabber message. The single argument is a hashref with the
following keys:

=over

=item to

The JID to which this message is to be sent.

=item type

The type of Jabber message this is. Should be one of: C<chat>,
C<groupchat>, C<normal>, C<headline> or C<error>. (See the Jabber
protocol for explanation on what these mean.)

=item thread

I<Optional> A string identifying the thread that this message belongs
to.

=item subject

I<Optional> The message's subject. Can be either a string, or a
hashref of the sort described in L<"Localization">.

=item body

I<Optional> The message's body. Can be either a string, or a hashref
of the sort described in L<"Localization">.

=item invitation

I<Optional> A hashref describing a Volity message-based
invitation. Keys include C<referee>, C<name>, C<player>, C<parlor>,
C<ruleset> and C<table>.

=back

=cut

sub send_message {
  my $self = shift;
  my ($config) = @_;
  my $message = POE::Filter::XML::Node->new('message');
  foreach (qw(to type from)) {
    $message->attr($_=>$$config{$_}) if defined($$config{$_});
  }
  foreach (qw(thread)) {
    $message->insert_tag($_)->data($$config{$_}) if defined($$config{$_});
  }
  # Handle possibly multiple subject and body elements, if the sender
  # uses localization.
  foreach (qw(subject body)) {
    if (defined($$config{$_})) {
      if (ref($$config{$_}) and ref($$config{$_}) eq 'HASH') {
	while (my($language, $text) = each(%{$$config{$_}})) {
	  unless ($language =~ /^\w\w$|^\w\w-\w\w$/) {
	    croak("Language must be of the form 'xx' or 'xx-xx', but you sent '$language'.");
	  }
	  my $tag = $message->insert_tag($_);
	  $tag->attr("xml:lang"=>$language);
	  $tag->data($text);
	}
      } elsif (not(ref($$config{$_}))) {
	$message->insert_tag($_)->data($$config{$_});
      } else {
	croak("$_ must be either a hashref (for localization) or a string (for default langauge)");
      }
    }
  }

  if ($$config{invitation}) {
      unless (ref($$config{invitation}) eq 'HASH') {
	  $self->expire("The 'invitation' key to the 'send_message' method has to contain a hash reference.");
      }
      my $form = Volity::Jabber::Form->new({type=>'result'});
      my @fields;
      my $type_field = Volity::Jabber::Form::Field->new({var=>"FORM_TYPE",
							 type=>"hidden",
						     });
      $type_field->values("http://volity.org/protocol/form/invite");
      push (@fields, $type_field);
      foreach (keys(%{$$config{invitation}})) {
	  my $field = Volity::Jabber::Form::Field->new({var=>$_});
	  $field->values($$config{invitation}{$_});
	  push (@fields, $field);
      }
      $form->fields(@fields);
      $message->insert_tag("volity", [xmlns=>"http://volity.org/protocol/form"])->insert_tag($form);
  }
	  
  $self->post_node($message);
}

=head2 send_query($args_hashref)

Send a Jabber <<query>> element, wrapped in an <<iq>> packet. The single argument is a hashref describing the query to send, and can take the following keys:

=over

=item to

The JID that this query will be sent to.

=item id

The ID attribute attached to the enfolding <<iq>> envelope.

=item type

The sort of <<iq>> this will be, e.g. C<set> or C<result>.

=item query_ns

The XML namespace to attach to the query. It's usually important to
set this to some value, since it lets the receiver know which Jabber
application the query applies to. For instance, a MUC configuration
form query would set this to "http://jabber.org/protocol/muc#owner",
as per JEP-0045.

=item content

An anonymous array containing POE::Filter::XML::Node objects (or
objects made from a subclass thereof), to be added as children to the
outgoing query.

=back

=cut

# send_query: accept a config hash, and send of a query element of a certain
# NS, maybe with a payload.
sub send_query {
    my $self = shift;
    my ($config) = @_;
    my $iq = POE::Filter::XML::Node->new('iq');
    foreach (qw(to from id type)) {
	$iq->attr($_=>$$config{$_});
    }
    my $query = $iq->insert_tag('query');
    $query->attr(xmlns=>$$config{query_ns});
    if ($$config{content}) {
	for my $kid (@{$$config{content}}) {	    
	    $query->insert_tag($kid);
	}
    }
    $self->post_node($iq);
}

=head2 join_muc($args_hashref)

Join a multi-user conference (MUC). The single argument is a hashref with the following keys:

=over

=item jid

The JID of the conference to join. You can specify the MUC either through this key, or the C<room> and C<server> keys.

=item nick

The nickname to use in the conference. If you don't specify this, the nick used will default to the object's username.

=item server

The server on which this MUC is located.

=item room

The name of the MUC.

=back

The return value is the JID of the MUC that presence was sent to.

=cut

sub join_muc {
  my $self = shift;
  my ($config) = @_;
  croak("You must call join_muc with an argument hashref!\n")
    unless ref($config) eq 'HASH';
  my $muc_jid;
  if ($muc_jid = $$config{jid}) {
    # We've been given the MUC's JID, but make sure there's a nick set.
    unless ($muc_jid =~ m|/.*$|) {
      if (defined($$config{nick})) {
	$muc_jid .= "/$$config{nick}";
      } else {
	  $muc_jid .= "/" . $self->user;
      }
    }
  } else {
    foreach (qw(room server nick)) {
      unless (defined($$config{$_})) {
	croak("You must specify either a full JID or a room, server, and nick in your call to join_muc().");
      }
    }
    $muc_jid = sprintf('%s@%s/%s', 
			      $$config{room}, $$config{server}, $$config{nick}
		      );
  }
  $self->logger->debug( "I want to join this muc: $muc_jid\n");
  my $presence = POE::Filter::XML::Node->new('presence');
  $presence->attr(to=>$muc_jid);
  $presence->insert_tag('x', [xmlns=>'http://jabber.org/protocol/muc']);
#  $self->post_node($presence);
  $self->send_presence({
      to=>$muc_jid,
      namespace=>'http://jabber.org/protocol/muc',
  });
  $self->logger->debug("Presence sent.\n");
  return $muc_jid;
}

=head2 leave_muc ($muc_jid)

Leave the multi-user conference whose JID matches the provided argument.

=cut

sub leave_muc {
    my $self = shift;
    my ($muc_jid) = @_;
    $self->send_presence({
	to   => $muc_jid,
	type => "unavailable",
    });
}

=head2 send_presence ($info_hashref)

Send a presence packet. Its optional argument is a hashref containing
any of the following keys:

=over

=item to

The destination of this presence packet (if it's a directed packet and
not just a 'ping' to one's Jabber server).

=item type

Sets the type attribute. See the XMPP-IM protocol for more information
as to their use and legal values.

=item show

=item status

=item priority

These all set sub-elements on the outgoing presence element. See the
XMPP-IM protocol for more information as to their use. You may set
these to localized values by setting their values to hashrefs instead
of strings, as described in L<"Localization">.

=item caps

This optional key has a value of another hashref containing entity
capabilities (JEP-0115) information. Its keys are C<node>, C<ver> and
C<ext>.

=item namespace

If you define this optional key, then the presence packet will include
an empty <<x/>> element with the given C<xmlns> attribute value.

=back

You can leave out the hashref entirely to send a blank <<presence/>>
element.

=cut

sub send_presence {
  my $self = shift;
  my $presence = POE::Filter::XML::Node->new('presence');
  my ($config) = @_;
  $config ||= {};
  foreach (qw(to type)) {
    $presence->attr($_=>$$config{$_}) if defined($$config{$_});
  }
  foreach (qw(show status priority)) {
    $self->insert_localized_tags($presence, $_, $$config{$_}) if defined($$config{$_});
  }
  if ($$config{namespace}) {
      $presence->insert_tag('x', [xmlns=>$$config{namespace}]);
  }
  if (my $caps_config = $$config{caps}) {
      if (ref($caps_config) eq 'HASH') {
	  my $caps_node = $presence->insert_tag('c');
	  $caps_node->attr(xmlns=>"http://jabber.org/protocol/caps");
	  foreach (qw(ext node ver)) {
	      $caps_node->attr($_=>$$caps_config{$_}) if defined($$caps_config{$_});
	  }
      }
      else {
	  $self->warn("The 'caps' argument in the send_presence() method must contain a hash reference.");
      }
  }
      
  $self->post_node($presence);
}

# insert_localized_tag: internal method. Receive a POE::Filter::XML::Node object, a child
# element name, and a value that might be either a plain string or a hashref
# containing localized text keyed on langauge abbreviation. Do the right thing.
# No return value; it sticks the right elements right into the supplied
# parent node.
sub insert_localized_tags {
  my $self = shift;
  my ($parent_node, $child_name, $value) = @_;
  if (ref($value) and ref($value) eq 'HASH') {
    while (my($language, $text) = each(%$value)) {
      unless ($language =~ /^\w\w$|^\w\w-\w\w$/) {
	croak("Language must be of the form 'xx' or 'xx-xx', but you sent '$language'.");
      }
      my $tag = $parent_node->insert_tag($child_name);
      $tag->attr("xml:lang"=>$language);
      $tag->data($text);
    }
  } elsif (not(ref($value))) {
    $parent_node->insert_tag($child_name)->data($value);
  }
}

=head2 request_roster

Requests the user's roster from its Jabber server. Takes no arguments.

This will result in a new roster object becoming available under the C<roster> accessor method. See L<"Volity::Jabber::Roster"> for this object's API.

=cut

sub request_roster {
  my $self = shift;
  my $iq = POE::Filter::XML::Node->new('iq');
  $iq->attr(type=>'get');
  $iq->insert_tag('query', [xmlns=>'jabber:iq:roster']);
  $self->post_node($iq);
}

sub receive_roster {
  my $self = shift;
  my ($iq) = @_;		# POE::Filter::XML::Node object
  my $items = $iq->get_tag('query')->get_children;
  return unless defined($items);
  my $roster = Volity::Jabber::Roster->new;
  for my $item (@$items) {
    my $item_hash = {};
    foreach (qw(jid name subscription)) {
      $$item_hash{$_} = $item->attr($_) if defined($item->attr($_));
    }
    if (my $groups = $item->get_children) {
      $$item_hash{group} = [];
      for my $group (@$groups) {
	push (@{$$item_hash{group}}, $group->data)
      }
    }
    $roster->add_item($item_hash);
  }
  $self->roster($roster);
  # XXX EXPERIMENTAL
  # Send presence after receipt of roster.
  $self->send_presence;
}

sub update_roster {
  my $self = shift;
  my ($iq) = @_;		# A POE::Filter::XML::Node object
  my $item = $iq->get_tag('query')->get_tag('item');
  my $roster = $self->roster;
  unless (defined($roster)) {
    $self->error_with_node($iq, "Uh oh, got a roster-modification result from the server, but I don't have a roster set. This is bizarre.");
    return;
  }
  my $item_hash = {};
  foreach (qw(jid name subscription)) {
    $$item_hash{$_} = $item->attr($_) if defined($item->attr($_));
  }
  if (my @groups = $item->get_children) {
    $$item_hash{group} = [];
    for my $group (@groups) {
      # XXX ?!
	eval {push (@{$$item_hash{group}}, $group->data)};
	warn "Whoa, burped with $group" if $@;
    }
  }
  # Now that we've made a chewable data structure from this item,
  # figure out how it applies to the roster.
  # As it happens, we _always_ want to remove this item from the roster,
  # as a first step. If it's an add or an update, we'll just re-add it,
  # with this new item data.
  $roster->remove_item($$item_hash{jid});
  if ($$item_hash{subscription} ne 'remove') {
    # OK, so it's either an add or an update.
    # In either case, we will add it this new data to the roster.
    $roster->add_item($item_hash);
  }
}

=head2 request_disco_info ($args_hashref)

Request service-discovery info from a JID/node combination. The
server's answer will trigger your module's C<receive_disco_info>
callback method (see L<"CALLBACK METHODS">).

The argument hashref can contain the following keys:

=over

=item to

A JID that the request will be sent to.

=item node

An optional string, specifying the node of the given JID.

=item id

The ID of this request.

=back

=cut

sub request_disco_info {
  my $self = shift;
  my ($info) = @_;
  my $iq = POE::Filter::XML::Node->new('iq');
  $iq->attr(type=>'get');
  if (not($info) or not(ref($info) eq 'HASH')) {
    croak("You must call request_disco with a hashref argument.");
  }
  unless ($$info{to}) {
    croak("The hash argument to request_disco() must contain at least a 'to' key, with a JID value.");
  }
  $iq->attr(to=>$$info{to});
  $iq->attr(id=>$$info{id}) if defined($$info{id});
  my $query = $iq->insert_tag('query', [xmlns=>"http://jabber.org/protocol/disco#info"]);
  $query->attr(node=>$$info{node}) if defined($$info{node});
  $self->post_node($iq);
}

=head2 request_disco_items ($args_hashref)

Request service-discovery items from a JID/node combination. The
server's answer will trigger your module's C<receive_disco_items>
callback method (see L<"CALLBACK METHODS">).

The argument hashref can contain the following keys:

=over

=item to

A JID that the request will be sent to.

=item node

An optional string, specifying the node of the given JID.

=item id

The ID of this request.

=back

=cut

sub request_disco_items {
  my $self = shift;
  my ($info) = @_;
  my $iq = POE::Filter::XML::Node->new('iq');
  $iq->attr(type=>'get');
  if (not($info) or not(ref($info) eq 'HASH')) {
    croak("You must call request_disco with a hashref argument.");
  }
  unless ($$info{to}) {
    croak("The hash argument to request_disco() must contain at least a 'to' key, with a JID value.");
  }
  $iq->attr(to=>$$info{to});
  $iq->attr(id=>$$info{id}) if defined($$info{id});
  my $query = $iq->insert_tag('query', [xmlns=>"http://jabber.org/protocol/disco#items"]);
  $query->attr(node=>$$info{node}) if defined($$info{node});
  $self->post_node($iq);
}


sub receive_disco_info {
  my $self = shift;
  $self->handle_disco_info($self->receive_disco(@_));
}

sub receive_disco_items {
  my $self = shift;
  $self->handle_disco_items($self->receive_disco(@_));
}

# Stubs, to override.
sub handle_disco_items { }
sub handle_disco_info { }

sub handle_disco_items_request { }
sub handle_disco_info_request { }

# receive_disco:
# Given a disco-response IQ, return its origin JID, the IQ's ID,
# a listref of disco items, and a hashref of JEP-0128 form fields.
sub receive_disco {
  my $self = shift;
  my ($iq) = @_;
  my @return = ($iq->attr('from'), $iq->attr('id'));
  my (@items, %fields);
  for my $child (@{$iq->get_tag('query')->get_children}) {
      if ($child->name eq 'x') {
	  for my $field ($child->get_tag('field')) {
	      next unless (ref($field));
	      bless ($field, "Volity::Jabber::Form::Field");
	      $fields{$field->var} = [$field->values];
	  }
      } else {
	  my $class = "Volity::Jabber::Disco::" . ucfirst($child->name);
	  bless($child, $class);
	  push (@items, $child);
      }
  }
  push (@return, \@items, \%fields);
  return @return;
}

sub send_disco_items {
  my $self = shift;
  $self->send_disco('items', @_);
}

sub send_disco_info {
  my $self = shift;
  $self->send_disco('info', @_);
}

sub send_disco {
    my $self = shift;
    my ($type, $info) = @_;
    if (not($info) or not(ref($info) eq 'HASH')) {
	croak("You must call send_disco_$type with a hashref argument.");
    }
    
#    my $iq = POE::Filter::XML::Node->new('iq');
#    $iq->attr(type=>'result');
#    $iq->attr(id=>$$info{id}) if (defined($$info{id}));
#    if ($$info{to}) {
#	$iq->attr(to=>$$info{to});
#    } else {
#	$self->expire("The hash argument to send_disco_$type contain at least a 'to' key, with a JID value.");
#    }
    
#    my $query = $iq->insert_tag('query', [xmlns=>"http://jabber.org/protocol/disco#$type"]);
    
    my @query_content;

    if (defined($$info{items})) {
	my @items_to_add = ref($$info{items})? @{$$info{items}} : ($$info{items});
	for my $item (@items_to_add) {
	    unless ($item->isa("Volity::Jabber::Disco::Node")) {
		croak("The items you add must be objects belonging to one of the Volity::Jabber::Disco::* classes. But you passed me this: $item");
	    }
#	    $query->insert_tag($item);
	    push (@query_content, $item);
	}
    }
    
    # There may also be a data form, as per JEP-0128.
    if (defined($$info{fields})) {
	my $form = Volity::Jabber::Form->new({type=>'result'});
	my @fields_to_add = ref($$info{fields})? @{$$info{fields}} : ($$info{fields});
	for my $field (@fields_to_add) {
	    unless ($field->isa("Volity::Jabber::Form::Field")) {
		croak("The fields you add must be objects belonging to the Volity::Jabber::Form::Field class. But you passed me this: $field");
	    }
	}
	$form->fields(@fields_to_add);
	push (@query_content, $form);
    }

#    $self->post_node($iq);
    $self->send_query({
	to=>$$info{to},
	id=>$$info{id},
	type=>'result',
	content=>\@query_content,
	query_ns=>"http://jabber.org/protocol/disco#$type",
    });
}

sub send_registration {
  my $self = shift;
  my ($config) = @_;
  my $iq = POE::Filter::XML::Node->new('iq');
  $iq->attr(type=>'set');
  $$config{id} ||= $self->next_id;
  $iq->attr(id=>$$config{id});
  my $query = $iq->insert_tag('query', [xmlns=>'jabber:iq:register']);
  foreach (keys(%$config)) {
    next if $_ eq 'id';
    $query->insert_tag($_)->data($$config{$_});
  }
  $self->set_iq_notification($$config{id}, 
			     {result=>'handle_registration_result'});
  $self->post_node($iq);
}

sub send_unregistration {
  my $self = shift;
  my ($id) = @_;
  $id ||= $self->next_id;
  my $iq = POE::Filter::XML::Node->new('iq');
  $iq->attr(type=>'set');
  $iq->attr(id=>$id) if defined($id);
  my $query = $iq->insert_tag('query', [xmlns=>'jabber:iq:register']);
  $query->insert_tag('remove');
  $self->set_iq_notification($id, 
			     {result=>'handle_unregistration_result'});
  $self->post_node($iq);
}  

sub handle_registration_result { }

sub handle_unregistration_result { }

sub receive_registration_error {
  my $self = shift;
  my ($iq) = @_;
  my $error = $iq->get_tag('error');
  $self->handle_registration_error(
				   {
				    id=>$iq->attr('id'),
				    error_node=>$error,
				    code=>$error->attr('code'),
				    type=>$error->attr('type'),
				    message=>$error->data,
				   }
				  );
}

# Stub:
sub handle_registration_error { }

# send_form: This doesn't actually work. You'll note that the incoming $form
# variable get validated but never used.
# Repair this once this method needs to become useful. --jmac 08/2006
sub send_form {
    my $self = shift;
    my ($config) = @_;
    my $form = $$config{form};
    unless ($form->isa("Volity::Jabber::Form")) {
	Carp::croak("The argument to send_form must be an object of class Volity::Jabber::Form.");
      }
    my $iq = POE::Filter::XML::Node->new('iq');
    foreach (qw(to id)) {
	$iq->attr($_=>$$config{$_}) if defined($$config{$_});
    }
    $iq->attr(type=>'set');
    $self->post_node($iq);
}

=head2 disconnect

Disconnects this entity from the Jabber server.

It sends out an 'unavailable' presence packet before doing so, just to be nice.

=cut

sub disconnect {
    my $self = shift;
    $self->send_presence({type=>'unavailable'});
    $self->kernel->post($self->alias, 'shutdown_socket', 0);
}

=head2 post_node($node)

Post the given XML node object to the POE kernel, which will then send it off to the Jabber server.

This is the method that is ultimately called by all the other action methods. You can use it too, if you find yourself knitting up raw nodes for some reason.

=cut

###########################
# Special accessors
###########################

# basic_jid: Return the non-resource part of my JID.
sub basic_jid {
  my $self = shift;
  if (defined($self->jid) and $self->jid =~ /^(.*)\//) {
    return $1;
  }
  return undef;
}

=head1 SUPPLEMENTARY PACKAGES

This module also include a handful of supplementary packages which
define some helper objects. You'll usually use them in conjunction with
the methods described above.

=cut

package Volity::Jabber::Roster;

=head2 Volity::Jabber::Roster

Objects of this class represent a Jabber roster, and its creation is
usually the result of a call to the C<request_roster> method of a
C<Volity::Jabber> object. Roster objects have methods appropriate for
storing and grouping Jabber IDs (JIDs), as follows:

=over

=cut

use warnings;
use strict;
use base qw(Volity);
use fields qw(jids groups names_by_jid jids_by_name groups_by_jid presence);
use Carp qw(carp croak);

sub initialize {
  my $self = shift;
  $self->{groups}->{_NONE} = [];
  $self->{names_by_jid} = {};
  $self->{jids_by_name} = {};
  return $self;
}

=item add_item ($item_hash)

Adds to the roster the JID described by the given hash reference. The
hash I<must> include a C<jid> key, whose value is the JID to add to
the roster. It can optionally contain a C<name>, whose value is a
nickname to associate with this roster JID, and a C<group> key, whose
value is an anonymous list of all the roster group names that this JID
should be filed under.

=cut

sub add_item {
  my $self = shift;
  my ($item_hash) = @_;
  $$item_hash{group} ||= ['_NONE'];
  $$item_hash{group} = [$$item_hash{group}] unless ref($$item_hash{group});
  my @current_groups_of_this_jid = $self->groups_for_jid($$item_hash{jid});
  for my $group_name (@{$$item_hash{group}}) {
    $group_name ||= '_NONE';
    $self->{groups}->{$group_name} ||= [];
    $self->{groups}->{$$item_hash{group}}->{$$item_hash{jid}} = 1;
    $self->{groups_by_jid}->{$$item_hash{jid}} ||= [];
    push (@{$self->{groups_by_jid}->{$$item_hash{jid}}}, $group_name);
  }
  if (defined($$item_hash{name})) {
    $self->{jids_by_name}->{$$item_hash{name}} = $$item_hash{jid};
    $self->{names_by_jid}->{$$item_hash{jid}} = $$item_hash{name};
  }
  $self->{jids}->{$$item_hash{jid}} = 1;
}

=item remove_item ($jid)

Removes the given JID from the roster.

=cut

sub remove_item {
  my $self = shift;
  my ($jid) = @_;
  # XXX A JID-syntax check would be nice here.
  unless (defined($jid)) {
    croak("You must call remove_item with a JID.");
  }
  if (defined($self->{names_by_jid}->{$jid})) {
    delete($self->{jids_by_name}->{delete($self->{names_by_jid}->{$jid})});
  }
  for my $group_name ($self->groups_for_jid($jid)) {
    delete($self->{groups}->{$group_name}->{$jid});
  }
  delete($self->{groups_by_jid}->{$jid});
  delete($self->{jids}->{$jid});
}

=item jids

Returns a list of all the JIDs on the roster.

=cut

sub jids {
  my $self = shift;
  return keys(%{$self->{jids}});
}

=item ungrouped_jids

Returns a list of all the JIDs which do not belong to any group.

=cut

sub ungrouped_jids {
  my $self = shift;
  return keys(%{$self->{groups}->{_NONE}});
}

=item jids_in_group ($group)

Returns a list of all the JIDs which belong to the given group.

=cut

sub jids_in_group {
  my $self = shift;
  my ($group) = @_;
  unless (defined($group)) {
    croak("You must call jids_in_group with a group name.");
  }
  if (defined($self->{groups}->{$group})) {
    return keys(%{$self->{groups}->{$group}});
  }
}

=item jid_for_name ($name)

Returns the JID corresponding to the given nickname, if any.

=cut

sub jid_for_name {
  my $self = shift;
  my ($name) = @_;
  unless (defined($name)) {
    croak("You must call jid_for_name with a name to look up.");
  }
  return $self->{jids_by_name}->{$name};
}

=item name_for_jid ($jid)

Returns the nickname associated with the given JID, if any.

=cut

sub name_for_jid {
  my $self = shift;
  my ($jid) = @_;
  unless (defined($jid)) {
    croak("You must call name_for_jid with a JID to look up.");
  }
  return $self->{names_by_jid}->{$jid};
}

=item groups_for_jid ($jid)

Returns a list of the groups that the given JID belongs to, if any.

=cut

sub groups_for_jid {
  my $self = shift;
  my ($jid) = @_;
  unless (defined($jid)) {
    croak("You must call groups_for_jid with a JID.");
  }
  if (defined($self->{groups_by_jid}->{$jid})) {
    return @{$self->{groups_by_jid}->{$jid}};
  } else {
    return ();
  }
}

=item has_jid ($jid)

Returns C<1> if the given jid is on the roster, and 0 if it isn't.

=cut

sub has_jid {
  my $self = shift;
  my ($jid) = @_;
  my $resource;
  ($jid, $resource) = $jid =~ m{^([^/]+)(?:/(.*))?$}
    or croak "Could not find jid and resource in $_[0]\n";
  if (exists($self->{jids}->{$jid})) {
    return 1;
  } else {
    return 0;
  }
}

=item presence ($jid, {type=>$presence_type)
 
Gets or sets a hash of information about the given JID's presence.
Note that the roster object doesn't listen to presence and do this all by
itself; this method has to be called from outside.

The JID in the required first argument may include a resource
string. If so, the method will set and return presence information
only for that one JID / resource combination.

At this time, only a single key, C<type>, is supported in the optional
second argument. If present, it sets the presence of the given JID
(and resource, if provided) to that key's value, e.g. "unavailable".

The return value is a list of anonymous hashes describing all known
presence information about this JID. Each hash has two keys,
C<resource> and C<type>.

=back

=cut

# presence: get or set a hashful of information about the given JID's presence.
# Note that the roster object doesn't listen to presence and do this all by
# itself; this method has to be called from outside.
sub presence {
  my $self = shift;
  my ($jid, $presence_hash) = @_;
  my $resource;
  ($jid, $resource) = $jid =~ /^(.*?)(?:\/(.*))?$/;
  if ($presence_hash) {
    if (defined($resource)) {
      $self->{presence}->{$jid}->{resources}->{$resource} = $presence_hash;
    } else {
      $self->{presence}->{$jid}->{general} = $presence_hash;
    }
  }
  my @presence_list;
  for my $resource (keys(%{$self->{presence}->{$jid}->{resources}})) {
    my $presence_hash = $self->{presence}->{$jid}->{resources}->{$resource};
    $$presence_hash{resource} = $resource;
#    $$presence_hash{jid} = $jid;
    push (@presence_list, $presence_hash);
  }
  push (@presence_list, $self->{presence}->{$jid}->{general}) if defined $self->{presence}->{$jid}->{general};
#  use Data::Dumper;
#  die Dumper(\@presence_list);
  
  return @presence_list;
}

package Volity::Jabber::Disco::Node;
use warnings; use strict;
use base qw(POE::Filter::XML::Node Class::Accessor);

sub new {
  my $class = shift;
  my ($node_type) = $class =~ /^.*::(.*?)$/;
  my $self = POE::Filter::XML::Node->SUPER::new(lc($node_type));
  bless ($self, $class);
  my ($init_hash) = @_;
  while (my($key, $val) = each(%$init_hash)) {
    if ($self->can($key)) {
      $self->$key($val);
    } else {
      $self->expire("I can't call the $key accessor on a $class object.");
    }
  }
  return $self;
}

sub set {
  my $self = shift;
  my ($key, $value) = @_;

  if (defined($value)) {
      # Apply XML escapes to the given value.
      $value =~ s/&/&amp;/g;
      $value =~ s/</&lt;/g;
      $value =~ s/>/&gt;/g;
      $value =~ s/'/&apos;/g;
      $value =~ s/"/&quot;/g;
  }

  # Now make it an attribute on the current object.
  $self->attr($key=>$value);
  return $value;
}

sub get {
    my $self = shift;
    my ($key) = @_;
    return $self->attr($key);
}

=head2 Volity::Jabber::Disco::Item

This object represents a Jabber Service Discovery item. A subclass of
POE::XML::Node, it may be inserted directly into disco responses you
are building, just as <<item>> elements in disco responses you receive
may be re-blessed into this class.

It contains the following simple accessor methods, whose ultimate function is described in JEP-0030:

=over

=item jid

=item node

=item name

=back

=cut

package Volity::Jabber::Disco::Item;

use warnings; use strict;
use base qw(Volity::Jabber::Disco::Node);

__PACKAGE__->mk_accessors(qw(jid node name));

=head2 Volity::Jabber::Disco::Identity

Just like Volity::Jabber::Disco::Item, except for disco <<identity>>
elements.

It contains the following simple accessor methods:

=over

=item category

=item type

=item name

=back

=cut

package Volity::Jabber::Disco::Identity;

use warnings; use strict;
use base qw(Volity::Jabber::Disco::Node);

__PACKAGE__->mk_accessors(qw(category type name));

=head2 Volity::Jabber::Disco::Feature

Just like Volity::Jabber::Disco::Item, except for disco <<feature>>
elements.

It contains the following simple accessor methods (er, method):

=over

=item var

=back

=cut

package Volity::Jabber::Disco::Feature;

use warnings; use strict;
use base qw(Volity::Jabber::Disco::Node);

__PACKAGE__->mk_accessors(qw(var));

=head2 Volity::Jabber::Form

B<Caution: incomplete implementation.>

A class for Jabber data forms, as defined by JEP-0004. An object of
this class is useful to stick under the C<content> key of of the
C<send_query> argument (see L<"ACTION METHODS">.

Simple accessors:

=over

=item type

=item title

=item instructions

=back

Other accessors:

=over

=item fields

Returns, as a list of Volity::Jabber::Form::Field objects, the form's
fields, with any values they may contain.

Optionally call with an array of Volity::Jabber::Form::Field objects
to first set the form's fields.

=item clear_fields

Erases all the form's fields.

=back

Other methods:

=over

=item invalid_fields

Returns a list of Volity::Jabber::Form::Field objects set as
C<required> but which have no values set.

=back

=cut

package Volity::Jabber::Form;

use warnings; use strict;
use base qw(Volity::Jabber::Disco::Node);

__PACKAGE__->mk_accessors(qw(type title instructions));

# Define which accessors get child elements, not attributes.
our %elements = (
		 title=>1,
		 instructions=>1,
		 );

sub new {
    my $class = shift;
    my $self = $class->SUPER::new(@_);
    $self->attr(xmlns=>"jabber:x:data");
    $self->name('x');
    return $self;
}

sub set {
    my $self = shift;
    my ($key, $value) = @_;
    if (exists($elements{$key})) {
	my $kid = $self->get_tag($key);
	unless ($kid) {
	    $kid = $self->insert_tag($key);
	}
	$kid->data($value);
    } else {
	return $self->SUPER::set(@_);
    }
}

sub get {
    my $self = shift;
    my ($key) = @_;
    if (exists($elements{$key})) {
	if (my $kid = $self->get_tag($key)) {
	    return $kid->data;
	} else {
	    return undef;
	}
    } else {
	return $self->SUPER::get(@_);
    }
}

sub fields {
    my $self = shift;
    my @fields = @_;
    if (@fields) {
	$self->clear_fields;
	if (grep(not($_->isa("Volity::Jabber::Form::Field")), @fields)) {
	    die "Arguments to fields() must all be Volity::Jabber::Form::Field  objects. I got these instead: @fields";
	}
	foreach (@fields) { $self->insert_tag($_) }
    }
    my @return_fields = map(bless($_, "Volity::Jabber::Form::Field"), grep(defined($_), $self->get_tag('field')));
    return @return_fields;
}

sub clear_fields {
    my $self = shift;
    for my $field (grep(defined($_), $self->get_tag('field'))) {
	$self->detach_child($field);
    }
}

sub invalid_fields {
    my $self = shift;
    return grep(not($_->is_valid), $self->fields);
}

=head2 Volity::Jabber::Form::Field

Just like Volity::Jabber::Disco::Item, except for JEP-0004 form-field
elements. 

It contains the following simple accessor methods:

=over

=item label

=item var

=item type

=item desc

=item required

=back

And the slightly less-simple accessors:

=over

=item values (@values)

If a list of arguments is provided, it becomes the values for this form field.

Returns a list of this field's current values.

=item clear_values

Clears this field's list of values.

=item options (@options)

If a list of arguments is provided, it becomes the options for this
form field. Each argument should be an anonymous hash, with a
C<values> key set to an anonymous list of the values this option
allows, and an optional C<label> key.

Returns a list of this field's current options, using the anonymous
hash format described above.

=item clear_options

Clears the options from this form element.

=back

Other methods:

=over

=item is_required ($is_required)

Set to a true value to define this field as C<required>. Call with a
false (but defined) value to set the field to not-C<required> (which
is the initial state of all new objects of this class).

Returns the current required-state of this object, expressed as 0 or 1.

=item is_valid

Returns 0 if this field is set C<required> and contains no values; 1
otherwise.

=back

=cut

package Volity::Jabber::Form::Field;

use warnings; use strict;
use base qw(Volity::Jabber::Disco::Node);

__PACKAGE__->mk_accessors(qw(label var type));

sub new {
    my $class = shift;
    my $self = $class->SUPER::new(@_);
    $self->name('field');
    return $self;
}

sub desc {
    my $self = shift;
    my $desc_node = $self->get_tag('desc');
    if (exists($_[0])) {
	my ($desc) = @_;
	$self->detach_child($desc_node) if defined($desc_node);
	$self->insert_tag('desc')->data($desc);
	return $desc;
    } else {
	if (defined($desc_node)) {
	    return $desc_node->data;
	} else {
	    return undef;
	}
    }
}
	
sub is_required {
    my $self = shift;
    my ($required) = @_;
    if (defined($required)) {
	if ($required) {
	    $self->insert_tag('required') unless $self->get_tag('required');
	} else {
	    if (my $tag = $self->get_tag('required')) {
		$self->detach_child($tag);
	    }
	}
    }
    if ($self->get_tag('required')) {
	return 1;
    } else {
	return 0;
    }
}

sub is_valid {
    my $self = shift;
    if ($self->is_required and not($self->values)) {
	return 0;
    } else {
	return 1;
    }
}
       

# values: Accessor to this field's value elements.
# Always returns a list of the current values, as simple strings.
sub values {
    my $self = shift;
    my (@values) = @_;
    if (@values) {
	$self->clear_values;
	for my $value (@values) {
	    $self->insert_tag('value')->data($value);
	}
	return $self;
    } else {
	@values = map($_->data, grep(defined($_), $self->get_tag('value')));
    }
    return @values;
}

# Each member of the @options argument is a hashref with two keys:
# label: the label of this option
# values: scalar or anon. array of values.
sub options {
    my $self = shift;
    my (@options) = @_;
    if (@options) {
	$self->clear_options;
	for my $option (@options) {
	    my $label = $$option{label};
	    my @values;
	    if ($$option{values}) {
		if (ref($$option{values})) {
		    @values = @{$$option{values}};
		} else {
		    @values = ($$option{values});
		}
	    }
	    my $option = $self->insert_tag('option');
	    $option->attr(label=>$label);
	    foreach (@values) { $option->insert_tag('value')->data($_) }
	}
    } else {
	for my $option_node ($self->get_tag('option')) {
	    my $label = $option_node->attr('label');
	    my @values = $option_node->get_tag('value');
	    push (@options, {label=>$label, values=>\@values});
	}
    }
    return @options;
}


# clear_values: Drop all the value elements.
sub clear_values {
    my $self = shift;
    map ($self->detach_child($_), grep(defined($_), $self->get_tag('value')));
}

sub clear_options {
    my $self = shift;
    map ($self->detach_child($_), grep(defined($_), $self->get_tag('option')));
}

################################
####### POSTSCRIPT (docs only after this point)
################################

=head1 NOTES

This class was originally written with the Volity internet game system
in mind, but it doesn't really have much Volity-specific code in
it. It might end up leaving the Volity namespace, if it stays as such
for a long time.

=head1 BUGS AND SUCH

JEP-0004 (data forms) is not yet fully implemented, especially where
handling incoming forms is concerned.

The module is only patchily object-oriented. Some things that I<really
ought> to have object classes lack them, such as Jabber
iq/message/presence packets. Future versions of this module. Backwards
compatibility will be attempted but is not guaranteed. (Therefore,
modules which subclass from Volity::Jabber should really be specific
about which version they require.)

=head1 SEE ALSO

=over 

=item *

Jabber Protocol information: http://www.jabber.org/protocol/

=item *

L<RPC::XML>

=item *

L<Volity>

=back

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003-2006 by Jason McIntosh.

=cut

1;


