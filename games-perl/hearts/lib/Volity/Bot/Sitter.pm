package Volity::Bot::Hearts::Sitter;

# This just subclasses a given bot, and makes it sit down when any other
# player does so.  It'll make testing go faster.

use warnings;
use strict;

use base qw(Volity::Bot::Hearts::Crafty);

__PACKAGE__->name("LazyC");
__PACKAGE__->algorithm("http://games.staticcling.org:8088/hearts/bot/sitterc");
__PACKAGE__->description("A crafty bot that always sits itself down");

sub volity_rpc_player_sat {
	my $self = shift;

	$self->sit if (!$self->am_seated);
	$self->SUPER::volity_rpc_player_sat(@_);

	return;
}

sub rpc_response_volity_sit {
    my $self = shift;
    my ($response) = @_;

    if ($response->{response}->[0] ne 'volity.ok') {
        $self->logger->error($self->log_prefix . " response to sit request: " . 
                join(', ', @{$response->{response}}))
    }
}
