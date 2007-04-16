package Volity::Bot::Hearts::Slick;

# This is a bot that tries to apply my knowledge of playing the game to its
# play.  Hopefully this will be a good competitive bot.

use warnings;
use strict;

use base qw(Volity::Bot::Hearts::Base);

our $VERSION = "0.1";

__PACKAGE__->name("Slick");
__PACKAGE__->algorithm("http://games.staticcling.org:8088/hearts/bot/slick");
__PACKAGE__->description("A card-counting bot with a human style of play");

####################  RPC Handlers ####################

# In almost every case, we want to call SUPER::rpc_handler first off, then do
# any extra stuff we need to do.

# we hook this RPC so that we can tell if we passed the QS, and so know who is
# holding it
sub game_rpc_pass_accepted {
    my $self = shift;
    my ($cards) = @_;

    # first off, do all of the regular things that every bot has to do
    $self->SUPER::game_rpc_pass_accepted($cards);

    # TODO
    # determine if the QS is one of the cards, then do appropriate things if
    # so.
}

# we hook this RPC in order to count cards.  This lets us make more
# intelligent choices about what cards to play
sub game_rpc_seat_played_card {
    my $self = shift;
    my ($seat_id, $card) = @_;

    # first off, do all of the regular things that every bot has to do
    $self->SUPER::game_rpc_seat_played_card($seat_id, $card);

    # TODO
    # now count some cards
}

# we hook this one to see who has taken points, so that we can stop a
# moon-shoot attempt if required
sub game_rpc_seat_won_trick {
    my $self = shift;
    my ($seat_id, $cards) = @_;

    # first off, do all of the regular things that every bot has to do
    $self->SUPER::game_rpc_seat_won_trick($seat_id, $cards);

    # TODO
    # now figure out who took points so we don't have to target them anymore
}

#################### Methods Implementing Play Logic ####################

# pass_cards: does the actual work of selecting the cards to pass, and returns
# a ref to an array containing the names of the cards.
sub pass_cards {
    my $self = shift;
    my ($dir, $count) = @_;

    # pass our 3 highest cards
    my $cards = $self->hand->cards;
    my @chosen_cards = ( $$cards[-1], $$cards[-2], $$cards[-3] );
    my @chosen_names = map { $_->name . $_->suit } @chosen_cards;

    $self->logger->info($self->log_prefix . " passing cards " . join(', ', @chosen_names));
    return \@chosen_names;
}

# take_turn: choose a card to play, return its name
sub take_turn {
    my $self = shift;
    my $chosen_card = 0;
    my $action = "";

    # check for a bizzarely empty hand, and spend some time waiting for one to
    # be delivered to us -- this could be cause by timing issues where
    # (somehow) we've gotten the notification to play before we've gotten the
    # receive_hand RPC
    if ($self->hand->size == 0) {
        $self->logger->error($self->log_prefix . " empty hand in take_turn");
        return;
    }

    # if we're leading, just pick the lowest card we have, unless it's a
    # heart and they haven't been broken.
    if ($self->led_suit eq "") {
        $chosen_card = $self->hand->cards->[0];

        if (not $self->hearts_broken and $chosen_card->suit eq 'H') {
            # find the lowest non-heart we have, if all we have is hearts, the
            # above choice will be used
            foreach my $card (@{$self->hand->cards}) {
                if ($card->suit ne 'H') {
                    $chosen_card = $card;
                    last;
                }
            }
        }

        # track the led suit here too
        $self->led_suit($chosen_card->suit);

        $action = "Leading ";
    } else {
        $action = "Playing ";
        # pick the lowest in-suit card we have, otherwise, pick the rightmost
        # card in the hand (ooh, a little teensy bit of intelligence here,
        # maybe I should remove it?)
        foreach my $card (@{$self->hand->cards}) {
            if ($card->suit eq $self->led_suit) {
                $chosen_card = $card;
                last;
            }
        }

        # We couldn't find an in-suit card.  Toss our highest card
        if ($chosen_card == 0) {
            $action = "Sloughing ";
            $chosen_card = $self->hand->cards->[-1];

            # if this is the first trick of a round, don't play points if it's
            # avoidable
            if ($self->hand->size == 13 and point_card($chosen_card)) {
                my $card_names = join(", ", map($_->name . $_->suit, @{$self->hand->cards}));
                $self->logger->info($self->log_prefix . " avoiding points in the first trick ($card_names)");

                foreach (reverse(@{$self->hand->cards})) {
                    $chosen_card = $_ if not point_card($_);
                }

                $card_names = join(", ", map($_->name . $_->suit, @{$self->hand->cards}));
                $self->logger->info($self->log_prefix . " after avoiding points: first trick ($card_names)");
            }
        }
    }

    if (not defined $chosen_card or $chosen_card == 0) {
        my $card_names = join(", ", map($_->name . $_->suit, @{$self->hand->cards}));
        $self->logger->error($self->log_prefix . " no card chosen while $action ($card_names)");

        # for the time being, just try to play every card until the ref lets
        # one through
        $self->flailing(1);
        $chosen_card = $self->hand->cards->[0];
        $self->logger->info($self->log_prefix . " flailing, playing " . 
                $chosen_card->name . $chosen_card->suit);
        return $chosen_card->name . $chosen_card->suit;
    }

    my $card_names = join(", ", map($_->name . $_->suit, @{$self->hand->cards}));
    $self->logger->info($self->log_prefix . " " . $action . $chosen_card->name . 
        $chosen_card->suit .  ", " . $self->led_suit . 
        " was led (hand: $card_names) ");
    return $chosen_card->name . $chosen_card->suit;

    return undef;
}

#################### Supporting Methods ####################

1;

# vim: ts=4 sw=4 et ai
