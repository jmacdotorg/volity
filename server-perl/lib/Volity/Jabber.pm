package Volity::Jabber;

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
   $self->debug(sprintf("%s says, '%s'\n", $$message{from}, $$message{body}));
   # More use message-handling code goes here.
 }

=head1 DESCRIPTION

This package provides a base class for Volity objects that speak
Jabber. These objects will automatically connect to (and authenticate
with ) a Jabber server on construction, and then provide some methods
for doing some common jabbery things, as well as access the POE
kernel.

=head1 USAGE

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
method. howevere, you B<must> call C<SUPER::initilialize>, otherwise
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
   'es' => "ste es un poco de texto localizado.",
   'fr' => "C'est un certain texte localis.",
 }

(Since Jabber uses UTF-8, there's no need to limit this to Latin-based
character sets, but translate.google.com doesn't help me provide such
examples here. ;) )

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
	   Component::Jabber;
	  );
use PXR::Node;
use Jabber::NS qw(:all);
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

=item port

The Jabber server's TCP port.

=item user

The username to use when connecting to the Jabber server.

=item password

The password to use when connecting to the Jabber server.

=item resource

The resource identifier to use when connecting to the Jabber server. (This is the part that gets stuck on the end of the JID, after the slash. So, setting this to "foo" will result in a JID like "bob@somejabberserver.com/foo")

=item default_language

The two-letter lanauge code that the object will attach to all
outgoing Jabber stanzas to identify their default lanaguge. Defaults
to C<en>. (See L<"Localization"> more more information about how this
module handles different langauges.)

=item jid (read-only)

After connection, this will return the connection's JID.

=item basic_jid (read-only)

Like C<jid>, except it returns the non-resource part of the JID. (e.g. C<foo@bar.com> versus C<foo@bar.com/bazzle>.)

=item debug

Set this to a true value to display verbose debugging messages on STDERR.

=back

=cut

use fields qw(kernel alias host port user resource password debug jid rpc_parser default_language query_handlers);

sub initialize {
  my $self = shift;
  unless (defined($self->user)) {
    croak("You have to initialize this object with a Jabber username!");
  }
  $self->{kernel} = $poe_kernel;
  $self->{port} ||= 5222;
  $self->debug("STARTING init. Password is " . $self->password);
  POE::Session->create(
		       object_states=>
		       [$self=>
			[qw(jabber_authed jabber_authfailed jabber_iq jabber_presence _start jabber_message input_event init_finish)],
		       ],
		      );
  # Weaken some variables to prevent circularities & such.
  foreach (qw(kernel)) {
    weaken($self->{$_});
  }

  $self->jid(sprintf("%s@%s/%s", $self->user, $self->host, $self->resource));
  $self->rpc_parser(RPC::XML::Parser->new);
  $self->default_language('en') unless defined($self->default_language);


  # Give initial values to instance variables what needs 'em.
  $self->{query_handlers} = {};

  $self->debug("LEAVING init. Password is " . $self->password);
  return $self;
}

sub debug {
  my $self = shift;
  warn ("@_\n") if $self->{debug};
}

# post_xml_node: send a given XML node object to the server.
# Rewrite this sub if our core Jabber/POE thing changes.
sub post_node {
  my $self = shift;
  my ($node) = @_;
  # I always set the stanza-level xml:lang attribute here.
  # Is it a bit much? Not sure. It's easy, anyway, and I figure it can't hurt.
  $node->attr('xml:lang'=>$self->default_language);
  $self->kernel->post($self->alias, 'output_handler', $node);
}

################################
# POE States (core)
################################

sub _start {
  my $self = $_[OBJECT];
  my ($kernel, $session, $heap) = @_[KERNEL, SESSION, HEAP];
  my $alias = $self->alias;
  unless (defined($self->alias)) {
    die "You haven't set an alias on $self! Please do that when constucting the object.";
  }
  POE::Component::Jabber->new(
			      ALIAS=>$alias,
			      STATE_PARENT=>$session->ID,
			      STATES=>{
				       INITFINISH=>'init_finish',
				       INPUTEVENT=>'input_event',
				     },
			      XMLNS => +NS_JABBER_CLIENT,
			      STREAM => +XMLNS_STREAM,
			      IP=>$self->host,
			      HOSTNAME=>$self->host,
			      PORT=>$self->port,
			      DEBUG=>$self->debug,
			     );
}

################################
# POE States (Jabber)
################################

sub init_finish {
  my $self = $_[OBJECT];
  $self->debug("In init_finish. Password is " . $self->password);
  $self->kernel->post($self->alias, 'set_auth', 'jabber_authed', $self->user, $self->password, $self->resource);
}

sub input_event {
  my $self = $_[OBJECT];
  my $node = $_[ARG0];
  my $element_type = $node->name;
  my $method = "jabber_$element_type";
  $method =~ s/\W/_/g;
  if ($self->can($method)) {
    $self->$method($node);
  } else {
    die "I got a $element_type element, and I don't know what to do with it. Sorry." . $node->to_str;
  }
}


# Actually, these are all just stubs. It's up to subclasses for making
# these do real stuff.

=head1 CALLBACK METHODS

=head2 Element-handling methods

All these object methods are called with a single argument: the XML
node that triggered them.

=over

=item jabber_presence

Called when a presence element is received.

=cut

sub jabber_presence { }

# XXX Hmmn, not sure what these do now. ;b

sub jabber_authed { }
sub jabber_authfailed { }

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
  $self->debug("I ($self) got an IQ object.\n");
  my ($node) = @_;
#  warn $node->to_str;
  my $id = $node->attr('id'); my $from_jid = $node->attr('from');
  my $query;
  # Check to see if we should dispatch this to a predefined NS handler
  # method.
  return if $self->handle_query_element_ns($node);
  if ($node->attr('type') eq 'result') {
    if ($query = $node->get_tag('query') and $query->attr('xmlns') eq 'jabber:iq:rpc') {
      # Yep, that's an RPC response.
      my $raw_xml = join("\n", map($_->to_str, @{$query->get_children}));
      # We should be getting only RPC responses, not requests.
      my $response_obj = $self->rpc_parser->parse($raw_xml);
      $self->debug("Finally, got $response_obj.\n");
      $self->debug("The response is: " . $response_obj->value->value . "\n");
      $self->handle_rpc_response({
				  id=>$id,
				  response=>$response_obj->value->value,
				  rpc_object=>$response_obj,
				  from=>$from_jid,
				});
    }
  } elsif ($node->attr('type') eq 'set') {
    if ($query = $node->get_tag('query') and $query->attr('xmlns') eq 'jabber:iq:rpc') {
      my $raw_xml = join("\n", map($_->to_str, @{$query->get_children}));
      $self->debug("Got Apparent RPC XML: $raw_xml\n");
      my @kids = @{$query->get_children};
      $self->debug("Got " . scalar(@kids) . " kids.\n");
      $self->debug("I like cheeze. Also: " . $kids[0]->get_id . "\n");
      my $rpc_obj = $self->rpc_parser->parse($raw_xml);
      $self->debug( "Finally, got $rpc_obj.\n");
      my $method = $rpc_obj->name;
      $self->handle_rpc_request({
				 rpc_object=>$rpc_obj,
				 from=>$from_jid,
				 id=>$id,
				 method=>$method,
				 args=>$rpc_obj->args,
			       });
    }
  } elsif ($node->attr('type') eq 'error') {
    if ($query = $node->get_tag('query') and $query->attr('xmlns') eq 'jabber:iq:rpc') {
      # This isn't an RPC fault, but an apparet error in trying to send the
      # RPC message at all.
      my $error_message = $node->get_tag('error')->data;
      my $code = $node->get_tag('error')->attr('code');
      $self->handle_rpc_transmission_error($node, $code, $error_message);
    }
  } else {
    $self->debug("Didn't do nuthin with it. Twas this: ");
    $self->debug( $node->to_str);
  }
}

# Message handler! Figures out the message type, and calls a deleagating
# method.

sub jabber_message {
  my $self = shift;
  my ($node) = @_;
  my $info_hash;		# Will be the argument to the delegate method.
  my $type;			# What type of chat is this?
  $self->debug( "I ($self) received a message...\n");
#  warn "<<<<<<<<<<<<<<<<<<<<<<<<<\n";
#  warn "I ($self) received a message...\n" . $node->to_str . "\n";  warn "<<<<<<<<<<<<<<<<<<<<<<<<<\n";

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
  $self->debug( "Delegating it to the $method method.");
  $self->$method($info_hash);
}

=back

=cut

# We could use some more useful stream-error handling...
sub jabber_stream_error { 
  my $self = shift;
  my ($node) = @_;
  $self->debug("Got a jabber stream error. " . $node->to_str);
}
  

################################
# Jabber event delegate methods
################################

=head2 RPC handler methods

These methods are called by RPC events.

=over

=item handle_rpc_respose({id=>$id, response=>$response, from=>$from, rpc_object=>$obj})

Called upon receipt of an RPC response. The argument is a hashref containing the response's ID attribute and response value, as well as an RPC::XML object representing the response.

=item handle_rpc_request({id=>$id, method=>$method, args=>[@args], from=>$from, rpc_object=>$obj})

Called upon receipt of an RPC request. The argument is a hashref containing the request's ID attribute, method, argument list (as an arrayref), and orginating JID, as well as an RPC::XML object representing the request.

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

# No default behavior for RPC stuff.
sub handle_rpc_response { }
sub handle_rpc_request { }
sub handle_rpc_transmission_error { }

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
  return unless defined($self->query_handlers);
  return unless defined($self->query_handlers->{$query_ns});

  if ($element_type eq 'iq') {
    # Locate a dispatch method, depending upon the type of the iq.
    my $method;
    my $type = $node->attr('type');
    unless (defined($type)) {
      croak("No type attribute defined in query's parent node! Gak!");
    }
    $method = $self->query_handlers->{$query_ns}->{$type};
    if (defined($method)) {
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
    croak("handle_query_element_ns called with a non-iq element. It was a $element_type.");
  }
}
    


################################
# Jabber element-sending methods
################################

=head1 JABBER ACTION METHODS

These methods will send messages and other data to the Jabber server.

=head2 make_rpc_request($args_hashref)

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
argument, and it's not itself an array reference, you can optinally
pass it in by itself. If there are no arguments, you can pass C<undef>
or just skip this key.

Each argument must be either a simple scalar, a hashref, or an
arrayref. In the latter cases, the argument will turn into an RPC
struct or array, respectively. All the datatyping magic is handled by
the RPC::XML module (q.v.).

=back

=cut

sub make_rpc_request {
  my $self = shift;
  $self->debug("in make_rpc_request\n");
  my ($args) = @_;
  my $iq = PXR::Node->new('iq');
  foreach (qw(to id)) {
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
  
  my $request = RPC::XML::request->new($$args{methodname}, @args);
  # I don't like this so much, sliding in the request as raw data.
  # But then, I can't see why it would break.
  my $request_xml = $request->as_string;
  # The susbtr() chops off the XML prolog. I know, I know.
  $request_xml = substr($request_xml, 22);
  $iq->insert_tag('query', 'jabber:iq:rpc')->
    rawdata($request_xml);
  $self->kernel->post($self->alias, 'output_handler', $iq);
}

=head2 send_rpc_response ($receiver_jid, $response_id, $response_value)

Send an RPC response. The value can be any scalar.

=cut

sub send_rpc_response {
  my $self = shift;
  my ($receiver_jid, $id_attr, $value) = @_;
  my $response = RPC::XML::response->new($value);
  my $rpc_iq = PXR::Node->new('iq');
  $rpc_iq->attr(type=>'result');
  $rpc_iq->attr(from=>$self->jid);
  $rpc_iq->attr(to=>$receiver_jid);
  $rpc_iq->attr(id=>$id_attr);
  # I don't like this so much, sliding in the response as raw data.
  # But then, I can't see why it would break.
  my $response_xml = $response->as_string;
  # The susbtr() chops off the XML prolog. I know, I know.
  $response_xml = substr($response_xml, 22);
  $rpc_iq->insert_tag(query=>'jabber:iq:rpc')
    ->rawdata($response_xml);
  $self->debug("Sending response: " . $rpc_iq->to_str);
  $self->kernel->post($self->alias, 'output_handler', $rpc_iq);
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

I<Optional> A string identifying the thread that this message belongs to.

=item subject

I<Optional> The message's subject. Can be either a string, or a hashref of the sort described in L<"Localization">.

=item body

I<Optional> The message's body. Can be either a string, or a hashref of the sort described in L<"Localization">.

=back

=cut

sub send_message {
  my $self = shift;
  my ($config) = @_;
  my $message = PXR::Node->new('message');
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
  $self->post_node($message);
#  warn ">>>>>>>>>>>>>>>>>>>>>>>>>>>\n";
#  warn "Message SENT.\n";
#  warn $message->to_str . "\n";
#  warn ">>>>>>>>>>>>>>>>>>>>>>>>>>>\n";
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
  $self->debug( "I want to join this muc: $muc_jid\n");
  my $presence = PXR::Node->new('presence');
  $presence->attr(to=>$muc_jid);
  $presence->insert_tag('x', 'http://jabber.org/protocol/muc');
  $self->kernel->post($self->alias, 'output_handler', $presence);
  $self->debug("Presence sent.\n");
  return $muc_jid;
}

=head2 send_form

Send a data-gathering form (compliant with JEP-0004). The single argument is a hashref with the following keys:

=over

=item to

The JID to which this form shall be sent.

=item type

The type of form to send. Should be a URI appropriate to this form.

=item fields

A hashref containing the form's fields and their submitted values.

The values can be simple scalars, in which case they're interpreted to be the simple values of unlabeled text fields, or they can be hash references, which allows more fine-tuned control over the fields. In the latter case, the hash reference may contain any or all of the following keys:

=over

=item value

The actual data value for this field.

=item type

The type of field.

=item label

The human-readable label to use with this field.

=item options

An array reference specifying the options that a C<list-single> or
C<list-multi> type of field can offer. Each list element can either be
a simple scalar (in which case it will act as both label and option
value), or list references (whose first element is the label to use,
and whose second element is the value that this label represents).

=back

=back

=cut

# send_form: Sends a form, as per JEP-0004.
sub send_form {
  my $self = shift;
  my ($config) = @_;
  my $iq = PXR::Node->new('iq');
  # Sanity check...
  unless (defined($$config{type})) {
    croak("You must specify a form type through the 'type' argument key.");
  }
  unless (defined($$config{to})) {
    croak("You must specify a desitination for the form through the 'to' argument key.");
  }
  foreach (qw(from to id)) {
    $iq->attr($_=>$$config{$_}) if defined($$config{$_});
  }
  $iq->attr(type=>'set');

  # The XML namespace of the query is just the form type.
  my $query = $iq->insert_tag('query', $$config{type});
  my $x = $query->insert_tag('x', "jabber:x:data");
  $x->attr(type=>'submit');
  
  # Send the fields and values.
  if (defined($$config{fields})) {
    # (We may support field lists in forms other than hashrefs later.)
    if (ref($$config{fields}) eq 'HASH') {
      while (my ($field, $value) = each(%{$$config{fields}})) {
	my $type; my $label; my @options;
	if (ref($value)) {
	  # It's a hashref describing the field.
	  $type = $$value{type} || '';
	  $label = $$value{label} || '';
	  @options = @{$$value{options}} || ();
	  $value = $$value{value} || '';
	} else {
	  # It's a straight-up value already.
	  $type = ''; $label = ''; @options = ();
	}
	my $field_element = $x->insert_tag('field');
	$field_element->attr(var=>$field);
	$field_element->attr(label=>$label);
	$field_element->attr(type=>$type);
	for my $option (@options) {
	  my ($value, $label);
	  if (ref($option)) {
	    ($value, $label) = @$option;
	  } else {
	    ($value, $label) = ($option, $option);
	  }
	  my $option_element = $field_element->insert_tag("option");
	  $option_element->attr(label=>$label);
	  $option_element->insert_tag('value')->data($value);
	  }
	$field_element->insert_tag('value')->data($value);
      }
    } else {
      croak("Form fields arg must be a hashref.");
    }
  }

  $self->post_node($iq);
  $iq->free;
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

=head1 NOTES

This class was originally written with the Volity internet game system
in mind, but it doesn't really have much Volity-specific code in
it. It might end up leaving the Volity namespace, if it stays as such
for a long time.

=head1 SEE ALSO

=over 

=item *

Jabber Protocol information: http://www.jabber.org/protocol/

=item *

L<RPC::XML>

=back

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2003 by Jason McIntosh.

=cut

1;

package Volity::Jabber::Form;

use warnings; use strict;
use PXR;

sub new_from_element {
  my $invocant = shift;
  my $class = ref($invocant) || $invocant;
  my ($x_element) = @_;
  unless (defined($x_element) or $x_element->isa("PXR::Node") or $x_element->name eq 'x') {
    croak("You must call new_from_element with a PXR::Node representing an 'x' element.");
  }
  my $self = bless ({}, $class);
  $self->{x} = $x_element;
  return $self;
}

sub items {
  my $self = shift;
  my @items;
  foreach ($self->{x}->get_tag('item')) {
    push (@items, Volity::Jabber::Form::Item->new_from_element($_));
  }
  return @items;
}

sub fields { $_[0]->call_item_method('fields', @_) }
sub field_with_var { $_[0]->call_item_method('field_with_var', @_) }

sub call_item_method {
  my $self = shift;
  my @items = $self->items;
  my $method = shift;
  if (@items > 1) {
    croak("Can't call $method on $self, since it contains more than one item.");
  } elsif (@items == 0) {
    croak("Can't call $method on $self, since it contains no items.");
  }
  return $items[0]->$method(@_);
}

package Volity::Jabber::Form::Item;

use warnings; use strict;
use PXR;

sub new_from_element {
  my $invocant = shift;
  my $class = ref($invocant) || $invocant;
  my ($item_element) = @_;
  unless (defined($item_element) or $item_element->isa("PXR::Node") or $item_element->name eq 'item') {
    croak("You must call new_from_element with a PXR::Node representing an 'item' element.");
  }
  my $self = bless ({}, $class);
  $self->{item} = $item_element;
  return $self;
}

sub fields {
  my $self = shift;
  my @fields;
  foreach ($self->{item}->get_tag('field')) {
    push (@fields, Volity::Jabber::Form::Item->new_from_element($_));
  }
  return @fields;
}

sub field_with_var {
  my $self = shift;
  my ($var) = @_;
  my $field = grep($_->var eq $var, $self->fields);
  return $field;
}

package Volity::Jabber::Form::Field;

sub new_from_element {
  my $invocant = shift;
  my $class = ref($invocant) || $invocant;
  my ($field_element) = @_;
  unless (defined($field_element) or $field_element->isa("PXR::Node") or $field_element->name eq 'field') {
    croak("You must call new_from_element with a PXR::Node representing an 'field' element.");
  }
  my $self = bless ({}, $class);
  $self->{field} = $field_element;
  return $self;
}

sub value {
  my $self = shift;
  return $self->{field}->get_tag('value')? $self->get_tag('value')->data: undef;
}

sub var {
  my $self = shift;
  return $self->{field}->attr('var');
}

sub label {
  my $self = shift;
  return $self->{field}->attr('label');
}
