package Volity::Game::TicTacToe;

use warnings;
use strict;

use base qw(Volity::Game);
use fields qw(x_marks o_marks winning_rows);

our $VERSION = "1.0";

# Set up our meta-info.
Volity::Game::TicTacToe->name("Tic Tac Toe");
Volity::Game::TicTacToe->uri("http://volity.org/games/tictactoe");
Volity::Game::TicTacToe->ruleset_version("1.0");
Volity::Game::TicTacToe->seat_ids(["x", "o"]);
Volity::Game::TicTacToe->required_seat_ids(["x", "o"]);
Volity::Game::TicTacToe->max_allowed_seats(2);

sub initialize {
    my $self = shift;
    $self->x_marks([]);
    $self->o_marks([]);
    $self->config_variables({});
    return $self->SUPER::initialize(@_);
}    

sub start {
    my $self = shift;
    $self->x_marks([]);
    $self->o_marks([]);
    $self->turn_order('x', 'o');
    $self->winning_rows([
			  [0, 1, 2],
			  [3, 4, 5],
			  [6, 7, 8],
			  [0, 3, 6],
			  [1, 4, 7],
			  [2, 5, 8],
			  [6, 4, 2],
			  [0, 4, 8],
			  ]);
    # Start off the game by promping X to move.
    $self->call_ui_function_on_everyone("must_mark", "x");
}

sub check_for_win {
    my $self = shift;
    my ($player_name) = @_;
    my $method = "${player_name}_marks";
    my @marks = $self->$method;
    my $won_row;
    for my $winning_row ($self->winning_rows) {
	my $marked = 0;
	$self->logger->debug("Checking the win-row @$winning_row against these marks: @marks");
	for my $winning_mark (@$winning_row) {
	    for my $present_mark (@marks) {
		if ($present_mark == $winning_mark) {
		    $marked++;
		    $self->logger->debug("I see that $winning_mark is in @marks.");
		    last;
		}
	    }
	}
	if ($marked >= 3) {
	    $self->logger->debug("Hey, a winner!");
	    $won_row = $winning_row;
	    last;
	}
    }
    if ($won_row) {
	return $won_row;
    } else {
	return undef;
    }
}

sub send_full_state_to_player {
    my $self = shift;
    my ($player) = @_;
    # First tell the player where all the marks are...
    for my $x_mark ($self->x_marks) {
	$player->call_ui_function("mark", "x", $x_mark);
    }
    for my $o_mark ($self->o_marks) {
	$player->call_ui_function("mark", "o", $o_mark);
    }

    # Then, tell them whose turn it is.
    $player->call_ui_function("must_mark", $self->current_seat->id);

}

sub rpc_mark {
    my $self = shift;
    my ($seat, $location) = @_;
    $location = int($location);
    foreach ($self->x_marks, $self->o_marks) {
	if ($_ == $location) {
	    return ("game.already_marked");
	}
    }
    if ($location < 0 || $location > 8) {
	return ("game.invalid_location");
    }
    if ($seat ne $self->current_seat) {
	$self->logger->debug("Hey, $seat is not " . $self->current_seat);
	return ("game.not_your_turn");
    }

    my $method = "add_" . $seat->id . "_mark";
    $self->$method($location);
    $self->logger->debug("Recording that " . $seat->id . " marked $location.");
    $self->call_ui_function_on_everyone("mark", $seat->id, $location);

    # Now check to see if the game's over.
    foreach my $seat_id (qw(x o)) {
	if (my $won_row = $self->check_for_win($seat_id)) {
	    $self->call_ui_function_on_everyone("win", $seat_id, @$won_row);
	    my @winners = sort {if($a->id eq $seat_id) { return -1 } else { return 1 }} $self->seats;
	    $self->winners->add_seat_to_slot($winners[0], 1);
	    $self->winners->add_seat_to_slot($winners[1], 2);
	    $self->end;
	}
    }
    if ($self->is_afoot) {
	# Maybe it's a tie?
	if ($self->x_marks + $self->o_marks >= 9) {
	    # There's nine marks made between the players, and no winner.
	    # What a surprise!
	    $self->call_ui_function_on_everyone("tie");
#	    $self->winners->add_seat_to_slot([$self->seats], 1);
	    for my $seat ($self->seats) {
		$self->winners->add_seat_to_slot($seat, 1);
	    }
	    $self->end;
	} else {
	    # Nope, we're still going.
	    $self->rotate_current_seat;
	    $self->call_ui_function_on_everyone("must_mark", $self->current_seat->id);
	}
    }


    return 'volity.ok';
}

sub add_x_mark {
    my $self = shift;
    my ($location) = @_;
    push (@{$self->{x_marks}}, $location);
}

sub add_o_mark {
    my $self = shift;
    my ($location) = @_;
    push (@{$self->{o_marks}}, $location);
}


1;

=head1 NAME

Volity::Game::TicTacToe - Tic tac toe game module for Volity

=head1 DESCRIPTION

This module is intended for use with Frivolity, the Perl libraries for
creating and running Volity game parlors. After you install this
module, run the C<volityd> program, specifying
C<Volity::Game::TicTacToe> as your game class. See the C<examples/>
directory in the Frivolity source distribution for a sample config
file that you can feed to C<volityd>.

More importantly, this module demonstrates how a simple turn-based
Volity game is written in Perl. It is relatively short, and I have
annotated its source code in a way that I hope will be useful to
people interested in Volity game programming. Feel free to contact me
with questions or comments.

The URI for this ruleset is http://volity.org/games/tictactoe
. Conveniently, that is also the URL to a webpage documenting this
ruleset.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2006 by Jason McIntosh.

=head1 SEE ALSO

=over

=item * 

L<volityd>

=item * 

L<Volity::Game>

=item *

L<Volity::Bot::TicTacToe::Random>

=item * 

http://volity.org

=item *

http://volity.org/games/tictactoe

=back
