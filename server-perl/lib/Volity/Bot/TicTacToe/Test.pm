package Volity::Bot::TicTacToe::Test;

# This is a bot that plays in a predictable way. Good for testing.

use warnings;
use strict;

use base qw(Volity::Bot);
use fields qw( squares );

Volity::Bot::TicTacToe::Test->name("Testy");
Volity::Bot::TicTacToe::Test->algorithm("http://volity.org/games/tictactoe/bot/test");

################
# RPC Handlers
################

# A handler for volity.start_game, an RPC defined by the core Volity
# protocol. (See http://www.volity.org/wiki/index.cgi?RPC_Requests)
# We react by resetting our idea of available squares.
sub volity_rpc_start_game {
    my $self = shift;
    $self->reset_board;
}

# A handler for volity.resume_game.
# There's a chance that one of the crafty humans has dragged me into a new
# seat, so I will make a state recovery request so I can get up to speed.
sub volity_rpc_resume_game {
    my $self = shift;
    $self->send_volity_rpc_to_referee("send_state");
}

# A handler for game.mark, an RPC defined by Tic Tac Toe's ruleset API.
# (See http://www.volity.org/games/tictactoe/)
# This means that a player has made a move.
sub game_rpc_mark {
    my $self = shift;
    my ($mark, $position) = @_;
    $self->mark_square($position, $mark);
}

# A handler for game.must_mark.
# This means that it's somebody's turn.
# If it's my turn, I will make a move.
sub game_rpc_must_mark {
    my $self = shift;
    my ($seat_id) = @_;
    if ($seat_id eq $self->seat_id) {
	# Whoops, it's my turn.
	$self->take_turn;
    }
}

#################
# Other methods
#################

# mark_square: Note that a given square (identified by number) has been
# marked.
sub mark_square {
    my $self = shift;
    my ($position, $mark) = @_;
    $self->squares->{$position} = $mark;
}

# reset_board: Initialize our hash of squares to all blank.
sub reset_board {
    my $self = shift;
    $self->squares({});
    foreach (0..8) {
	$self->squares->{$_} = '';
    }
}

# mark_at_position: Returns the mark at the given board position,
# or the null string if there is no mark there.
sub mark_at_positon {
    my $self = shift;
    my ($position) = @_;
    return $self->squares->{$position};
}

# take_turn: Take a turn, and send out an RPC.
# Choose the first unoccupied square.
sub take_turn {
    my $self = shift;
    my @empty_squares = grep($self->squares->{$_} eq '', sort(keys(%{$self->{squares}})));
    my $chosen_square = $empty_squares[0];
    $self->send_game_rpc_to_referee("mark", $chosen_square);
}
	
    

1;

=head1 NAME

Volity::Bot::TicTacToe::Test - Tic tac toe bot module for Volity

=head1 DESCRIPTION

This is a subclass of C<Volity::Bot> that defines an automated Tic Tac
Toe player. It is not a very good Tic Tac Toe player. On its turn, it
always chooses to mark the first empty square on the board. It first
looks at the upper-left square, and then scans across-then-down until
it hits the first empty square.

Since its move behavior is entirely and trivially deterministic, it
makes a good test subject.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2008 by Jason McIntosh.

=head1 SEE ALSO

=over

=item * 

L<volityd>

=item * 

L<Volity::Bot>

=item *

L<Volity::Game::TicTacToe>

=item * 

http://volity.org

=back
