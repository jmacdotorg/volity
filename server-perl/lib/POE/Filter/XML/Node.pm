package POE::Filter::XML::Node;
use warnings;
use strict;

use constant tagname => 0;
use constant attrs => 1;
use constant tagdata => 2;
use constant kids => 3;
use constant id => 4;
use constant start => 5;
use constant end => 6;

our $VERSION = '0.22.1';

my $id = 0;

sub new() 
{
	my ($class, $name, $attr) = @_;

	my $node = [
		 $name,		#name
		 {},		#attr
		 '',		#data
		 {},		#kids
		 ++$id,		#id
		 0,			#start
		 0,			#end
	];

	bless($node, $class);
	$node->insert_attrs($attr) if defined($attr);
	return $node;

}

sub stream_start()
{
	my ($self, $bool) = @_;
	
	if(defined($bool))
	{
		$self->[+start] = $bool;
		return $self;
	
	} else {
	
		return $self->[+start];
	}
}

sub stream_end()
{
	my ($self, $bool) = @_;

	if(defined($bool))
	{
		$self->[+end] = $bool;
		return $self;
	
	} else {

		return $self->[+end];
	}
}
		

sub clone()
{
	my $self = shift;

	my $new_node = POE::Filter::XML::Node->new($self->[+tagname]);
	$new_node->[+tagdata] = $self->[+tagdata];
	
	my %attrs = %{$self->[+attrs]};
	$new_node->[+attrs] = \%attrs;
	
	my $kids = $self->get_sort_children();
	foreach my $key (@$kids)
	{
		$new_node->insert_tag($key->clone());
	}
	
	return $new_node;
}

sub get_id()
{
	my $self = shift;

	return $self->[+id];
}

sub name() 
{
	my ($self, $name) = @_;
	
	if(defined $name)
	{
		$self->[+tagname] = $name;
	}
	
	return $self->[+tagname];
}

sub insert_attrs()
{
	my ($self, $array) = @_;

	for(my $i = 0; $i < scalar(@$array); $i++)
	{
		$self->attr($array->[$i], $array->[++$i]);
	}
	
	return $self;
}

sub attr() 
{

	my ($self, $attr, $val) = @_;
	
	if (defined $val) 
	{
		if ($val eq '') 
		{
			return delete $self->[+attrs]->{$attr};
			
		} else {
		
			$self->[+attrs]->{$attr} = $val;
			return $self;
		}
	}

	return $self->[+attrs]->{$attr};
 
}

sub get_attrs()
{
	my $self = shift;

	return $self->[+attrs];
}
						
sub data() 
{
	my ($self, $data) = @_;
	
	if (defined $data) 
	{
		$self->[+tagdata] = _encode($data);
		return $self;
	}

	return _decode($self->[+tagdata]);
 
}

sub _encode() 
{
	my $data = shift;

	$data =~ s/&/&amp;/go;
	$data =~ s/</&lt;/go;
	$data =~ s/>/&gt;/go;
	$data =~ s/'/&apos;/go;
	$data =~ s/"/&quot;/go;

	return $data;

}

						
sub _decode() 
{
	my $data = shift;

	$data =~ s/&amp;/&/go;
	$data =~ s/&lt;/</go;
	$data =~ s/&gt;/>/go;
	$data =~ s/&apos;/'/go;
	$data =~ s/&quot;/"/go;

	return $data;

}

sub rawdata()
{
	my ($self, $data) = @_;
	
	if (defined $data) 
	{
		$self->[+tagdata] = $data;
	}

	return $self->[+tagdata];
 
}

sub insert_tag() 
{
	my ($self, $tagname, $ns) = @_;

	my $tag;

# This is one of my changes. --jmac	
#	if(ref($tagname) eq ref($self))
	if( (ref($tagname)) and ($tagname->isa("POE::Filter::XML::Node")) )

	{
		$tag = $tagname;
		$tagname = $tag->[+tagname];
	
	} else {
		
		if(defined($ns))
		{
			$tag = POE::Filter::XML::Node->new($tagname, $ns);
		
		} else {

			$tag = POE::Filter::XML::Node->new($tagname);
		}
	}
	
	if(exists($self->[+kids]->{$tagname}))
	{
		if(ref($self->[+kids]->{$tagname}) eq 'ARRAY')
		{
			push(@{$self->[+kids]->{$tagname}}, $tag);
			return $tag;
		
		} else {

			my $first = $self->[+kids]->{$tagname};
			$self->[+kids]->{$tagname} = [];

			push(@{$self->[+kids]->{$tagname}}, $first);
			push(@{$self->[+kids]->{$tagname}}, $tag);
			return $tag;
		}
	}
	
	$self->[+kids]->{$tagname} = $tag;
	
	return $tag;

}

sub to_str() 
{
	my $self = shift;
	
	if($self->[+end])
	{
		my $str = '</'.$self->[+tagname].'>';
		return $str;
	}
	
	my $str = '<' . $self->[+tagname];
	
	foreach my $attr (keys %{$self->[+attrs]})
	{
		$str .= ' ' . $attr . "='" . $self->[+attrs]->{$attr} . "'";
	}
	
	if (length $self->[+tagdata] or keys %{$self->[+kids]})
	{
		$str .= '>' . $self->[+tagdata];
		
		my $kids = $self->get_sort_children();
		
		foreach my $kid (@$kids)
		{
			$str .= $kid->to_str();
		}
		
		$str .= '</'.$self->[+tagname].'>';
	
	} else {
		
		if($self->[+start])
		{
			$str .= '>';
		
		} else {

			$str .= '/>';
		}
			
	}
	
	return $str;
 
}

sub get_tag() 
{
	my ($self, $tagname) = @_;
	
	my $return = [];

	if(ref($self->[+kids]->{$tagname}) eq 'ARRAY')
	{
		return wantarray ? @{$self->[+kids]->{$tagname}} :
			$self->[+kids]->{$tagname}->[0];
	
	} else {
		
		return wantarray ? @{[$self->[+kids]->{$tagname}]} :
			$self->[+kids]->{$tagname};
	}

}

sub detach_child()
{
	my ($self, $prodigal) = @_;
	
	my $tag = $self->[+kids]->{$prodigal->name()};
	
	if(ref($tag) eq 'ARRAY')
	{
		my $index = 0;
		foreach my $child (@$tag)
		{
			if($child->[+id] == $prodigal->[+id])
			{
				return splice(@$tag, $index, 1);
			
			} else {

				++$index;
			}
		}
		
	} else {

		return delete $self->[+kids]->{$prodigal->name()};
	}
}

sub get_children() 
{
	my ($self) = @_;
	my $return = [];
	foreach my $kid (values %{$self->[+kids]})
	{
		if(ref($kid) eq 'ARRAY')
		{
			push(@$return, @$kid);
		
		} else {
		
			push(@$return, $kid);
		}
	}
	return $return;

}

sub get_children_hash()
{
	my $self = shift;

	return $self->[+kids];
}

sub get_sort_children()
{
	my $self = shift;

	my $return = $self->get_children();
	
	@$return = sort { $a->[+id] <=> $b->[+id] } @$return;
	
	return $return;
}

1;

__END__

=pod

=head1 NAME

POE::Filter::XML::Node - Fully featured XML node representation.

=head1 SYNOPSIS

use POE::Filter::XML::Node;

my $node = POE::Filter::XML::Node->new('iq'); 

$node->attr('to', 'nickperez@jabber.org'); 

$node->attr('from', 'POE::Filter::XML::Node@jabber.org'); 

$node->attr('type', 'get'); 

my $query = $node->insert_tag('query', 'jabber:iq:foo');
$query->insert_tag('foo_tag')->data('bar');

my $foo = $query->get_tag('foo_tag');

my $foo2 = $foo->clone();
$foo2->data('new_data');

$query->insert_tag($foo2);

print $node->to_str() . "\n";

$node->free();

-- 

(newlines and tabs for example only)

 <iq to='nickperez@jabber.org' 
  from='POE::Filter::XML::Node@jabber.org' type='get'>
   <query xmlns='jabber:iq:foo'>
     <foo_tag>bar</foo_tag>
     <foo_tag>new_data</foo_tag>
   </query>
 </iq>

=head1 DESCRIPTION

POE::Filter::XML::Node aims to be a very simple yet powerful, memory/speed 
conscious module that allows JABBER(tm) developers to have the flexibility 
they need to build custom nodes, use it as the basis of their higher level
libraries, or manipulating XML and then putting it out to a file.

Note that this is not a parser. This is merely the node representation that 
can be used to build XML objects that will give stringified versions of 
themselves.

=head1 METHODS

=over 4

=item new()

new() accepts as arguments the (1) name of the actual tag (ie. 'iq'), (2) an 
arrayref of attribute pairs for insert_attrs(). All of the arguments are 
optional and can be specified through other methods at a later time.

=item stream_start()

stream_start() called without arguments returns a bool on whether or not the
node in question is the top level document tag. In an xml stream such as
XMPP this is the <stream:stream> tag. Called with a single argument (a bool)
sets whether this tag should be considered a stream starter.

This method is significant because it determines the behavior of the to_str()
method. If stream_start() returns bool true, the tag will not be terminated.
(ie. <iq to='test' from='test'> instead of <iq to='test' from='test'B</>>)

=item stream_end()

stream_end() called without arguments returns a bool on whether or not the
node in question is the closing document tag in a stream. In an xml stream
such as XMPP, this is the </stream:stream>. Called with a single argument (a 
bool) sets whether this tag should be considered a stream ender.

This method is significant because it determines the behavior of the to_str()
method. If stream_end() returns bool true, then any data or attributes or
children of the node is ignored and an ending tag is constructed. 

(ie. </iq> instead of <iq to='test' from='test'><child/></iq>)

=item clone()

clone() does a B<deep> copy of the node and returns it. This includes all of
its children, data, attributes, etc. The returned node stands on its own and 
does not hold any references to the node cloned.

=item get_id()

get_id() returns the creation ID of the node.

=item name()

name() with no arguments returns the name of the node. With an argument, the
name of the node is changed.

=item insert_attrs()

insert_attrs() accepts a single arguement: an array reference. Basically you
pair up all the attributes you want to be into the node (ie. [attrib, value])
and this method will process them using attr(). This is just a convenience 
method.

=item attr()

attr() with one argument returns the value of that attrib (ie. 
my $attrib = $node->attr('x') ). With another argument it sets that attribute
to the value supplie (ie. $node->attr('x', 'value')). If the second argument
is an empty string, then that attribute will be deleted and returned.

=item get_attrs()

get_attrs() returns a hash reference to the stored attribute/value pairs
within the node.

=item data()

data() with no arguments returns the data stored in the node B<decoded>. With
one argument, data is stored into the node B<encoded>. To access raw data with
out going through the encoding mechanism, see rawdata().

=item rawdata()

rawdata() is similar to data() but without the encoding/decocing implictly
occuring. Be cautious with this, because you may inadvertently send malformed
xml over the wire if you are not careful to encode your data for transport.

=item insert_tag()

insert_tag() accepts two arguments, with (1) being either the name as a string 
of a new tag to build and then insert into the parent node, or (1) being a 
POE::Filter::XML::Node reference to add to the parents children, and (2) if 
(1) is a string then you pass along an optional arrayref of attribute pairs 
for insert_attrs() to be built into the new child. insert_tag() returns either
newly created node, or the reference passed in originally, respectively.

=item to_str()

to_str() returns a string representation of the entire node structure. Note
there is no caching of stringifying nodes, so each operation is expensive. It
really should wait until it's time to serialized to be sent over the wire.

=item get_tag()

get_tag() takes two arguments, (1) the name of the tag wanted, and (2) an 
optional namespace to filter against. Depending on the context of the return 
value (array or scalar), get_tag() either returns an array of nodes match the 
name of the tag/filter supplied, or a single POE::Filter::XML::Node reference
that matches, respectively.

=item detach_child()

detach_child() takes one argument: a POE::Filter::XML::Node reference that is 
a child of the node. It then removes that child from its children and returns 
it as a stand alone node on its own.

=item get_children()

get_children() returns an array reference to all the children of that node.

=item get_children_hash()

get_children_hash() returns a hash reference to all the children of that node.
Note: for more than one child with the same name, the entry in the hash will be
an array reference.

=item get_sort_children()

get_sort_children() returns an array reference to all children of that node
in sorted order according to their creation ID.

=back

=head1 BUGS AND NOTES

If you were specifically making use of the parent() and parent related 
functions, then I apologize for their rather quick demise. It is too much to
ask people to keep track of things with circular references. So there is no 
longer the concept of nodes have parents, only parents being containers with 
children.

=head1 AUTHOR

Copyright (c) 2003 Nicholas Perez. Released and distributed under the GPL.

=cut

