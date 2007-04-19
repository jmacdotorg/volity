package Volity::Game::Hearts::Variant::standard;
use base qw( Volity );

use Scalar::Util qw(weaken);

sub initialize {
	my $self = shift;

	$self->SUPER::initialize(@_);

	return 1;
}

# returns the number of cards that need to be passed.  Presently only
# implements the standard rules: left, right, across, hold (3 cards)
sub pass_count ($$) {
    my $self = shift;
	my ($round_count) = @_;

    my @dirs = ('left', 'right', 'across', 'hold');
    my $count = 0;
    my $direction = $round_count % 4;
    $count = 3 unless $direction > 2;

    return ($dirs[$direction], $count);
}

# Calculate the nubmer of points a card attracts.  To implement a variant
# which differs only in card scoring, override this function, change the
# ruleset URI, and you're done
sub card_score ($$) {
	my $self = shift;
	my ($card) = @_;

	return 1 if $card->suit eq 'H';
	return 13 if $card->truename eq 'QS';

	return 0;
}

# Check to see the moon has been shot.  
# 	score is the score for the hand just ended
#   seat is the seat object in case it's needed to check moon shooting
# Similar comments about variants as above.
sub check_moon_shoot ($$$) {
	my $self = shift;
	my ($score, $seat) = @_;

	return 1 if $score == 26;
	return 0;
}

# Calculate and give out the points to the seats.  Can be overridden for
# variants where scoring is different.
sub assign_scores ($$) {
	my $self = shift;
	my ($seats_in_play) = @_;

	print ref $seats_in_play;

    # figure the scores out
    foreach my $seat (@$seats_in_play) {
        my $score = 0;
        foreach my $card (@{$seat->cards_taken->cards}) {
			$score += $self->card_score($card);
        }

        # Add 26 to everyone else's score if this player shot the moon.
		# Reduce the score which will be added to the player's total by 26 --
		# this lets us unconditionally add $score to their total below,
		# allowing this scoring routine to function for variants where more or
		# fewer point cards exist than just the hearts & queen of spades
        if ($self->check_moon_shoot($score, $seat)) {
			$score -= 26;
            foreach my $victim (@$seats_in_play) {
                next if $victim == $seat;
                $victim->add_points(26);
            }
        }

		# the player's score will either
		$seat->add_points($score);
    }
}

# Figure out if it's game over.  This may need overriding too
sub is_game_over ($$) {
	my $self = shift;
	my ($seats_in_play, $game_end_score) = @_;

    foreach my $seat (@$seats_in_play) {
        return 1 if $seat->score >= $game_end_score;
	}
	
	return 0;
}

1;
