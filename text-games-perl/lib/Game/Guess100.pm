package Volity::Game::Guess100;

use warnings;
use strict;

use base qw(Volity::Game);
use fields qw(the_number);

sub player_class {
  return;			# The default class is fine for us.
}

sub initialize {
  my $self = shift;
  $self->SUPER::initialize(@_);
  print "Yippeee.\n";
  $self->the_number(int(rand(100)) + 1);
#  $self->the_number(3);
  $self->end_turn;
}

sub handle_normal_message {
  my $self = shift;
  my ($message) = @_;
  $self->debug("Whoop, got a normal message.");
}

sub handle_groupchat_message {
  my $self = shift;
  my ($message) = @_;
  $self->debug( "Whoop, got a groupchat message");
}

sub handle_chat_message {
  my $self = shift;
  unless (ref($self)) {
    return $self->SUPER::receive_chat_message(@_);
  }
  my ($message)  = @_;
  my $body = $$message{body};
  $self->debug( "I got a message!! $body");
  my $chatter_jid = $self->server->look_up_jid_with_nickname($$message{from});
  my $player = $self->{player_jids}->{$chatter_jid};
  $self->debug( "Wanna look up the player under $chatter_jid\n");
  $self->debug( "$player and " . $self->current_player);
  unless ($player eq $self->current_player) {
    # The current player is not the player who talked to me. Grrr!
    $self->debug( "Non-turned player made a guess!! That's no good.\n");
    return;
  }
  if (my ($guess) = $body =~ /^\s*(\d+)\s*$/) {
    $self->debug( "Ahhhah! A guess! Comparing $guess with " . $self->the_number . "\n");
    # Announce to the nice people what the players' guess was.
    $self->server->send_message({
				 to=>$self->muc_jid,
				 body=>$player->nick . " made a guess: $guess.",
				});
    if ($guess == $self->the_number) {
      # The game is over.
      $self->server->send_message({
			 to=>$self->muc_jid,
			 body=>"And that is correct! " . $player->nick . " wins!",
				  });
      $self->end_game({status=>"completed", winners=>[$player]});
    } elsif ($guess < $self->the_number) {
      # The guess is too low.
      $self->server->send_message({
			 to=>$self->muc_jid,
			 body=>"But that number is too low.",
				   });
      # Move to the next player.
      $self->end_turn;
    } else {
      # The guess is too hah.
      $self->server->send_message({
			 to=>$self->muc_jid,
			 body=>"But that number is too high.",
				 });
      # Move to the next player.
      $self->end_turn;
    }
  } else {
    $self->debug( "Dur, I donno what I was just told...\n");
  }
}

sub end_turn {
  my $self = shift;
  $self->rotate_current_player;
  $self->server->send_message({
			       to=>$self->muc_jid,
			       body=>"It is now " . $self->current_player->nick . "'s turn.",
			     });
}

sub jid_name {
  return "guess100";
}

1;
