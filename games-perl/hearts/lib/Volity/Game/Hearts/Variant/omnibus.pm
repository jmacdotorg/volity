# implements a Jack of Diamonds (omnibus) hearts variant
package Volity::Game::Hearts::Variant::omnibus;
use base qw(Volity::Game::Hearts::Variant::standard);

# Calculate the nubmer of points a card attracts.
# This variant has a -10 point score for the Jack of Diamonds
sub card_score ($$) {
	my $self = shift;
	my ($card) = @_;

	return 1 if $card->suit eq 'H';
	return 13 if $card->truename eq 'QS';
	return -10 if $card->truename eq 'JD';
}

# Check to see the moon has been shot.  
sub check_moon_shoot ($$$) {
	my $self = shift;
	my ($score, $seat) = @_;

	my $jd = 0;
	foreach my $card (@{$seat->cards_taken->cards}) {
		$jd = 1 if $card->truename eq 'JD';
	}

	return 1 if $score == 26 and not $jd;
	return 1 if $score == 16 and $jd;
	return 0;
}

1;
