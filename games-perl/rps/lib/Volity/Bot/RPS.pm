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

sub handle_groupchat_message {
  my $self = shift;
  my ($message) = @_;
#  warn "Got a message: $$message{body}\n";
#  if ($$message{body} =~ /game has begun!/) {
#    warn "OK, I am " . $self->nickname . " and I'm taking my turn.\n";
#    $self->take_turn;
#  }
#  $self->groupchat("Same to you.");
}

sub handle_rpc_request {
  my $self = shift;
  my ($rpc_info) = @_;
#  $self->groupchat("Ooh, got rpc request $$rpc_info{method}.");
  if ($$rpc_info{method} eq 'game.start_game') {
    $self->take_turn;
  }
}
1;
