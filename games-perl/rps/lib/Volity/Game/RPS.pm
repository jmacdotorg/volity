package Volity::Game::RPS;

use warnings;
use strict;

use base qw(Volity::Game);
use fields qw(no_ties best_of);

# Volity variables
__PACKAGE__->uri("http://volity.org/games/rps");
__PACKAGE__->seat_class("Volity::Seat::RPS");
__PACKAGE__->name("Jmac's RPS");
__PACKAGE__->description("The offcial Volity.net RPS implementation, by Jason Mcintosh.");
__PACKAGE__->ruleset_version("1.5");
__PACKAGE__->seat_ids(["player1", "player2"]);
__PACKAGE__->required_seat_ids(__PACKAGE__->seat_ids);

# We'll use the initialize sub to set up the config variables.
sub initialize {
    my $self = shift;
    $self->SUPER::initialize(@_);
    $self->register_config_variables(qw(no_ties best_of));
    $self->best_of(1);
    $self->no_ties(0);
    return $self;
}

sub start {
    my $self = shift;
    for my $seat ($self->seats) { 
	$seat->hand(undef);
	$seat->hands_won(0);
    }
}

sub set_hand {
    my $self = shift;
    my ($seat, $hand) = @_;
    if (substr("rock", 0, length($hand)) eq lc($hand)) {
	$seat->hand('rock');
    } elsif (substr("paper", 0, length($hand)) eq lc($hand)) {
	$seat->hand('paper');
    } elsif (substr("scissors", 0, length($hand)) eq lc($hand)) {
	$seat->hand('scissors');
    } else {
	# I don't know what this hand type is.
	return (fault=>604, "Bogus hand type '$hand'. Must be one of 'rock', 'paper', 'scissors'.");
    }

    # Has everyone registered a hand?
    if (grep(defined($_), map($_->hand, $self->seats)) == $self->seats) {
	# Yes! Time for BATTLE!
	# Sort the seats into winning order.
	my @seats = sort( {
	    my $handa = $a->hand; my $handb = $b->hand;
	    if ($handa eq $handb) {
		return 0;
	    } elsif ($handa eq 'rock' and $handb eq 'scissors') {
		return -1;
	    } elsif ($handa eq 'scissors' and $handb eq 'paper') {
		return -1;
	    } elsif ($handa eq 'paper' and $handb eq 'rock') {
		return -1;
	    } else {
		return 1;
	    }
	}
			  $self->seats);
	# Tell both seats what their opponent chose
	for my $seat (@seats) {
	    $self->call_ui_function_on_everyone('player_chose_hand',
						$seat->id,
						$seat->hand,
						);
	}

	# Increment the winning seats' victory counters.
	if ($seats[0]->hand eq $seats[-1]->hand) {
	    unless ($self->no_ties) {
		foreach(@seats) { $_->win_hand }
	    }
	} else {
	    if ($seats[0]->hand eq 'rock') {
		$seats[0]->win_hand;
	    } elsif ($seats[0]->hand eq 'scissors') {
		$seats[0]->win_hand;
	    } else {
		$seats[0]->win_hand;
	    }
	}
	
	# Did anyone win?
	my @winners = (grep($self->check_seat_for_victory($_), @seats));
	if (@winners) {
	    # Well, maybe... check for a tie.
	    if (@winners == 2) {
		if ($seats[0]->hands_won == $seats[1]->hands_won) {
		    if ($seats[0]->hands_won == $self->best_of) {
			# It's a complete tie!!
			# So, only one slot in the winners array,
			# containing both seats.
			$self->winners->add_seat_to_slot(\@seats, 1);
		    } else {
			# We seem to be in sudden-death mode. Another game!
		    }
		} else {
		    # There were some ties, but one player clearly
		    # stomped the other.
		    # Winners array has two slots, in winning order.
		    @winners = sort {$a->hands_won <=> $b->hands_won} @seats;
		    my $slot_number = 1;
		    foreach (@winners) {
			$self->winners->add_seat_to_slot($_, $slot_number++);
		    }
		}
	    } else {
		# There is one clear winner.
		# Winners array has two slots, in winning order.
		@winners = sort {$a->hands_won <=> $b->hands_won} @seats;
		my $slot_number = 1;
		foreach (@winners) {
		    $self->winners->add_seat_to_slot($_, $slot_number++);
		}
	    }
	} else {
	    # Reset the seats' hands.
	    foreach (@seats) { $_->hand(undef) }
	}
    }

    # If the above boondoggle ended up setting the winners array, we know
    # that the game is done.
    $self->end if $self->winners->slots;

    # At any rate, return undef, so the calling seat gets a generic
    # success response message.
    return;
    
}

sub check_seat_for_victory {
    my $self = shift;
    my ($seat) = @_;
    if ($seat->hands_won >= $self->best_of / 2) {
	return 1;
    } else {
	return 0;
    }
}

####################
# Incoming RPC request handlers
###################

sub rpc_choose_hand {
    my $self = shift;
    $self->set_hand(@_);
}

sub rpc_no_ties {
    my $self = shift;
    my ($seat, $new_value) = @_;
    my $current_value = $self->no_ties;
    if ($new_value ne $current_value) {
	if ($new_value eq '0') {
	    $self->no_ties(0);
	    $self->call_ui_function_on_everyone(no_ties=>$seat->id, 0);
	    $self->unready_all_seats;
	    return $new_value;
	} elsif ($new_value eq '1') {
	    $self->no_ties(1);
	    $self->call_ui_function_on_everyone(no_ties=>$seat->id, 1);
	    $self->unready_all_seats;
	    return $new_value;
	} else {
	    return(fault=>604, "Invalid argument ($new_value) to no_ties. Must be 1 or 0.");
	}
    }
    $self->config_variable_setters->{no_ties} = $seat;
    return;
}

sub rpc_best_of {
    my $self = shift;
    my ($seat, $new_value) = @_;
    my $current_value = $self->best_of;
    if ($new_value ne $current_value) {
	if (($new_value > 0) and ($new_value % 2)) {
	    $self->best_of($new_value);
	    $self->call_ui_function_on_everyone('best_of', $seat->id, $new_value);
	    $self->unready_all_seats;
	    return;
	} else {
	    return(fault=>604, "Invalid argument ($new_value) to best_of. Must be a positive, odd number.");
	}
    }
    $self->config_variable_setters->{best_of} = $seat;
    return;
}

package Volity::Seat::RPS;

use base qw(Volity::Seat);
use fields qw(hand hands_won);

# win_hand: called by the game object when the seat wins a hand.
sub win_hand {
    my $self = shift;
    $self->{hands_won}++;
}

1;
