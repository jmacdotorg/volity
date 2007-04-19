# The classes instantiated by this factory allow the parlor to switch up
# how it behaves in key parts of the game-flow.  It allows the config
# variables (which are settable by the players) to control things like what
# conditions make a moon-shoot, etc

package Volity::Game::Hearts::VariantFactory;

use strict; 
use warnings;

our @supported_variants = qw( standard omnibus );

sub instantiate {
	my $self          = shift;
	my $requested_type = shift;

	my $location       = "Volity/Game/Hearts/Variant/$requested_type.pm";
	my $class          = "Volity::Game::Hearts::Variant::$requested_type";

	# only allow supported variants
	return undef unless grep($requested_type eq $_, @supported_variants);

	require $location;
	return $class->new(@_);
}

1;
