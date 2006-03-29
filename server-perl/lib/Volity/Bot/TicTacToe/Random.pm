package Volity::Bot::TicTacToe::Random;

# This is a bot that plays randomly.
# Challenge for the reader: write one that plays with some strategy.

use warnings;
use strict;

use base qw(Volity::Bot);

Volity::Bot::TicTacToe::Random->name("Tacky");

# Package variable of squares and the marks that fill them.
our %squares;

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
    $squares{$position} = $mark;
}

# reset_board: Initialize our hash of squares to all blank.
sub reset_board {
    my $self = shift;
    foreach (0..8) {
	$squares{$_} = '';
    }
}

# mark_at_position: Returns the mark at the given board position,
# or the null string if there is no mark there.
sub mark_at_positon {
    my $self = shift;
    my ($position) = @_;
    return $squares{$position};
}

# take_turn: Take a turn, and send out an RPC.
# Becuase we're a dumb bot, we'll just pick a random unoccupied square.
# ( I note that one could try making a smarter bot by replacing this method. )
sub take_turn {
    my $self = shift;
    my @empty_squares = grep($squares{$_} eq '', keys(%squares));
    my $index = int(rand(@empty_squares));
    my $chosen_square = $empty_squares[$index];
    $self->send_game_rpc_to_referee("mark", $chosen_square);
}
	
    

1;

=head1 NAME

Volity::Bot::TicTacToe::Random - Tic tac toe bot module for Volity

=head1 DESCRIPTION

This is a subclass of C<Volity::Bot> that defines an automated Tic Tac
Toe player. It is not a very good Tic Tac Toe player, choosing a
random legal move to make every time its turn comes up.

More importantly, this module demonstrates how a simple Volty bot is
written in Perl. It is relatively short, and I have annotated its
source code in a way that I hope will be useful to people interested
in Volity game programming. Feel free to contact me with questions or
comments.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=head1 COPYRIGHT

Copyright (c) 2006 by Jason McIntosh.

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
