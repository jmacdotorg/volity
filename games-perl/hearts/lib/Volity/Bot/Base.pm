package Volity::Bot::Hearts::Base;

# This is a base class that implements the common functionality for hearts
# bots.  Hopefully it's general enough because it started life as the Ducker
# bot.

use warnings;
use strict;

use base qw(Volity::Bot);

__PACKAGE__->name("dont_use_me");
__PACKAGE__->algorithm("http://canoe.staticcling.org/games/hearts/bot/dont_use_me");
__PACKAGE__->description("A totally neutered bot");

# game is the Games::Cards instance
# deck is a holding place for all of the cards in the game
# master_hand is a hand to hold all cards in the game, because the ::Deck
#       object doesn't have the API that I need
# hand is the hand that the bot is holding
# led_suit is the suit that was led for this trick, or "" if the bot is
#       leading
# flailing is a state variable that determines whether we've played an
#       incorrect card, and don't know why.  Scan through the hand and try to
#       play every card until the ref lets us play one.  We're in a flailing
#       state if the value is > 0.  If > 0, this is the next index in the hand
#       to try when the rpc response to the played card comes back negative.
use fields qw( game deck master_hand hand led_suit hearts_broken flailing );
use Games::Cards;

our $VERSION = "1.0";

################
# RPC Handlers
################

# A handler for volity.start_game, an RPC defined by the core Volity
# protocol. (See http://www.volity.org/wiki/index.cgi?RPC_Requests)
# We react by resetting our game state
sub volity_rpc_start_game {
    my $self = shift;

    return unless $self->am_seated;

    my $game = Games::Cards::Game->new(
        {cards_in_suit=>{
            2=>2,
            3=>3,
            4=>4,
            5=>5,
            6=>6,
            7=>7,
            8=>8,
            9=>9,
            10=>10,
            Jack=>11,
            Queen=>12,
            King=>13,
            Ace=>14 }
     });
    $self->game($game); # store this for later

    # this exists so that there's somewhere to pull the cards from into our
    # hand.  Apparently there's no API to create cards out of thin air.
    $self->deck(Games::Cards::Deck->new($game, 'deck'));
    $self->master_hand(Games::Cards::Hand->new($game, 'master'));
    $self->deck->give_cards($self->master_hand, 'all'); # transfer them all

    # uh, do I need to explain this one? :)
    $self->hand(Games::Cards::Hand->new($game, 'hand'));

    $self->led_suit("");
    $self->hearts_broken(0);
    $self->flailing(-1);
}

sub volity_rpc_game_has_started {
    my $self = shift;

    return unless $self->am_seated;

    $self->volity_rpc_start_game(@_);

    return;
}

# suppress a warning, I'm pretty sure this is useless info for us
sub volity_rpc_seat_list {
    my $self = shift;

    return unless $self->am_seated;

    # empty
}

# suppress a warning, I'm pretty sure this is useless info for us
sub volity_rpc_required_seat_list {
    my $self = shift;

    return unless $self->am_seated;
    # empty
}

# suppress a warning, but this info might be useful
sub volity_rpc_receive_state {
    my $self = shift;

    return unless $self->am_seated;

    # empty, for now
}

# suppress a warning, I'm pretty sure this is useless info for us
sub volity_rpc_state_sent {
    my $self = shift;

    return unless $self->am_seated;

    # empty
}

# A handler for volity.resume_game.
# There's a chance that one of the crafty humans has dragged me into a new
# seat, so I will make a state recovery request so I can get up to speed.
sub volity_rpc_resume_game {
    my $self = shift;

    return unless $self->am_seated;

    $self->send_volity_rpc_to_referee("send_state");

    return;
}

# Suppress a warning. This seems to be called when the last human bails on the
# table.
sub volity_rpc_game_activity {
    my $self = shift;

    return unless $self->am_seated;
}

sub game_rpc_receive_hand {
    my $self = shift;
    my ($cards) = @_;

    return unless $self->am_seated;

    # just in case -- there should never be cards left, but...
    for (my $i = scalar(@{$self->hand->cards}) - 1; $i >= 0; $i--) {
        $self->hand->give_a_card($self->master_hand, $i); 
    }

    foreach my $card (@$cards) {
        $self->master_hand->give_a_card($self->hand, $card);
    }

    my $sent_cards = join(",", @$cards);
    my $card_names = join(",", map($_->name . $_->suit, @{$self->hand->cards}));
    $self->logger->debug($self->log_prefix . " received hand $sent_cards -> $card_names");

    return;
}

# game.must_pass is the sure indicator that a new round has begun.  Do the
# start-of-round maintenance here.
sub game_rpc_must_pass {
    my $self = shift;
    my ($dir, $count) = @_;

    return unless $self->am_seated;

    # start-of-round maintenance
    $self->led_suit("");
    $self->hearts_broken(0);
    $self->flailing(-1);

    $self->logger->debug($self->log_prefix . " to pass $count cards $dir");
    return unless $count > 0;

    # do the passing
    my $chosen_cards = $self->pass_cards($dir, $count);

    # send the RPC
    $self->send_game_rpc_to_referee("pass_cards", $chosen_cards);

    return;
}

sub game_rpc_pass_accepted {
    my $self = shift;
    my ($cards) = @_;

    return unless $self->am_seated;

    $self->logger->debug($self->log_prefix . " our pass was accepted");

    # remove the cards we passed from our hand
    foreach my $card (@$cards) {
        $self->hand->give_a_card($self->master_hand, $card);
    }

    return;
}

sub game_rpc_receive_pass {
    my $self = shift;
    my ($cards) = @_;

    return unless $self->am_seated;

    $self->logger->debug($self->log_prefix . " received cards " . join(', ', @$cards));
    # put the cards we were passed into our hand
    foreach my $card (@$cards) {
        $self->master_hand->give_a_card($self->hand, $card);
    }

    return;
}

# in existance to suppress warnings
sub game_rpc_seat_passed {
    my $self = shift;

    return unless $self->am_seated;
}

# Called as part of state recovery, this gives information about what the
# current passing scheme is, and who remains to pass.  A seated player/bot
# will never be in this list without also having been sent a previous
# must_pass RPC (immediately after the receive_hand RPC was sent)
sub game_rpc_passing_info {
    my $self = shift;
    my ($dir, $count, $passing) = @_;

    return unless $self->am_seated;
}

sub game_rpc_start_turn {
    my $self = shift;
    my ($seat_id) = @_;

    return unless $self->am_seated;

    # My turn
    if ($self->am_seated and $seat_id eq $self->seat_id) {
        $self->logger->debug($self->log_prefix . " our turn");
        $self->flailing(-1);
        my $chosen_card = $self->take_turn();
        $self->send_game_rpc_to_referee("play_card", $chosen_card);
    }

    return;
}

# We only care about our card plays, so we know what's in our hand.  No
# consideration of the other cards in play is done
sub game_rpc_seat_played_card {
    my $self = shift;
    my ($seat_id, $card) = @_;

    return unless $self->am_seated;

    # if this was our play, put the card back in the great pile of cards in 
    # the sky
    if ($self->am_seated and $seat_id eq $self->seat_id) {
        $self->logger->debug($self->log_prefix . " there's our card, the $card");
        $self->flailing(-1); # stop trying every card
        $self->hand->give_a_card($self->master_hand, $card);
    }

    # if this is the first card of a trick, record the suit
    if ($self->led_suit eq "") {
        $self->led_suit(substr($card, -1, 1));
        $self->logger->debug($self->log_prefix . " " . $self->led_suit . " was led");
    }

    # if this is a heart, record that hearts have been broken
    if (not $self->hearts_broken and substr($card, -1, 1) eq "H") {
        $self->logger->debug($self->log_prefix . " hearts broken");
        $self->hearts_broken(1);
    }

    return;
}

# don't care who won, just reset the led_suit parameter
sub game_rpc_seat_won_trick {
    my $self = shift;

    return unless $self->am_seated;

    $self->led_suit("");

    return;
}

# don't care what the scores are, just suppress the warning
sub game_rpc_seat_score {
    my $self = shift;

    return unless $self->am_seated;

}

# don't care who won, just suppress the warning
sub game_rpc_winners {
    my $self = shift;

    return unless $self->am_seated;
}

#################### callback responses ####################

sub rpc_response_volity_send_state {
    my $self = shift;
    my ($response) = @_;

    if ($response->{response}->[0] ne 'volity.ok') {
        $self->logger->error($self->log_prefix . " response to state send request: " . 
                join(', ', @{$response->{response}}))
    }
}

sub rpc_response_game_pass_cards {
    my $self = shift;
    my ($response) = @_;

    if ($response->{response}->[0] ne 'volity.ok') {
        $self->logger->error($self->log_prefix . " response to passing card: " . 
                join(', ', @{$response->{response}}))
    }
}

sub rpc_response_game_play_card {
    my $self = shift;
    my ($response) = @_;

    if ($response->{response}->[0] ne 'volity.ok') {
        $self->logger->error($self->log_prefix . " response to playing card: " . 
                join(', ', @{$response->{response}}));

        # continue to flail if that's what we're doing
        if ($self->flailing >= 0) {
            if ($self->flailing >= $self->hand->size) {
                $self->logger->error($self->log_prefix . " flailing failed");
                $self->flailing(-1);
                return;
            }

            my $chosen_card = $self->hand->cards->[$self->flailing];
            $self->flailing($self->flailing + 1);
            $self->logger->info($self->log_prefix . " flailing, playing " . 
                $chosen_card->name . $chosen_card->suit);
            $self->send_game_rpc_to_referee("play_card", 
                $chosen_card->name . $chosen_card->suit);
        }
    }
}

#################
# Other methods
#################

# pass_cards: does the actual work of selecting the cards to pass, and returns
# a ref to an array containing the names of the cards.
# NOTE: This is a stub, it needs to be overridden in a subclass
sub pass_cards {
    my $self = shift;
    my ($dir, $count) = @_;

    $self->logger->error("The bot Base class is being used as a bot!");

    return undef;
}

# take_turn: choose a card to play, return its name
# NOTE: This is a stub, it needs to be overridden in a subclass
sub take_turn {
    my $self = shift;

    $self->logger->error("The bot Base class is being used as a bot!");

    return undef;
}

sub point_card {
    my $self = shift; # only a method so we can inherit it
    my $card = shift;

    return 1 if $card->suit eq 'H';
    return 1 if $card->name eq 'Q' and $card->suit eq 'S';
    return 0;
}

sub log_prefix {
        my $self = shift;

        my @bits = split('@', $self->referee_jid);
        my $id = "observer";
        $id = $self->seat_id if $self->am_seated;
        return $bits[0] . ":" . $id;
}

1;

=head1 NAME

Volity::Bot::Hearts::Base - A base class for Hearts playing bots.

=head1 DESCRIPTION

This is a subclass of C<Volity::Bot> that implements common functions for automated Hearts
players. It implements no actual play logic, that's left to subclasses.

=head1 AUTHOR

Austin Henry jabber: named@volity.net

=head1 COPYRIGHT

Copyright (c) 2006 by Austin Henry.

=head1 SEE ALSO

=over

=item * 

L<volityd>

=item * 

L<Volity::Bot>

=item *

L<Volity::Game::Hearts>

=item * 

http://volity.org

=back

# vim: ts=4 sw=4 et ai
