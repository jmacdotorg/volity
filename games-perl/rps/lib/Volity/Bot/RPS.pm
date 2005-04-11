package Volity::Bot::RPS;

# This is a superclass for bots that can play rock-paper-scissors.

use warnings; use strict;

use base qw(Volity::Bot);
# Mmm, take note of which fields should go in a bot class.
use fields qw(has_guessed);

use POE;

__PACKAGE__->name("RandomBot");
__PACKAGE__->description("Generic RPS-playing bot.");
__PACKAGE__->user("rps-bot");
__PACKAGE__->host("volity.net");

sub take_turn {
  my $self = shift;
  my @choices = qw(rock paper scissors);
  my $hand = $choices[rand(@choices)];
  $self->make_rpc_request({
			   to=>$self->referee->jid,
			   id=>"hand_choice",
			   methodname=>"game.choose_hand",
			   args=>[$hand],
#			   handler=>sub {},
			  });

  $self->send_message({
		       type=>'groupchat',
		       to=>$self->muc_jid,
		       body=>"I have made my move.",
		      });
}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
  if ($$rpc_info{method} eq 'volity.start_game') {
      $self->take_turn;
  } elsif ($$rpc_info{method} eq 'game.player_chose_hand') {
      # I'm just going to take another turn, whether the server will listen
      # or not. :)
      $self->take_turn;
  } else {
      $self->SUPER::handle_rpc_request(@_);
  }
}
1;
