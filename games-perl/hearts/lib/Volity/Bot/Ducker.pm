package Volity::Bot::Hearts::Ducker;

# This is a bot that always plays its lowest card.  Not a good strategy, but
# one that's easy to implement (and might be amusing to watch).

use warnings;
use strict;

use base qw(Volity::Bot::Hearts::Base);

__PACKAGE__->name("Ducky");
__PACKAGE__->algorithm("http://games.staticcling.org:8088/hearts/bot/ducker");
__PACKAGE__->description("A bot that always ducks");

our $VERSION = "1.0";

#################### Overridden RPC methods ####################

sub game_rpc_receive_hand {
    my $self = shift;
    my ($cards) = @_;

    # exhibit all of the regular behaviour
    $self->SUPER::game_rpc_receive_hand($cards);

    return unless $self->am_seated;

    # we'd like these sorted strictly by value so we can always find the
    # lowest card :)
    $self->hand->sort_by_value();

    return;
}

#################
# Other methods
#################

# pass_cards: does the actual work of selecting the cards to pass, and returns
# a ref to an array containing the names of the cards
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
            if ($self->hand->size == 13 and $self->point_card($chosen_card)) {
                my $card_names = join(", ", map($_->name . $_->suit, @{$self->hand->cards}));
                $self->logger->debug($self->log_prefix . " avoiding points in the first trick ($card_names)");

                foreach (reverse(@{$self->hand->cards})) {
                    $chosen_card = $_ if not $self->point_card($_);
                }

                $card_names = join(", ", map($_->name . $_->suit, @{$self->hand->cards}));
                $self->logger->debug($self->log_prefix . " after avoiding points: first trick ($card_names)");
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
}

1;

=head1 NAME

Volity::Bot::Hearts::Ducker - A Hearts playing bot which always plays its
lowest card

=head1 DESCRIPTION

This is a subclass of C<Volity::Bot> that defines an automated Hearts
player. It is not a very good hearts player, always choosing the lowest
in-suit card when required, or the highest possible card when it can't follow
suit.

=head1 AUTHOR

Austin Henry jid:named@volity.net

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
