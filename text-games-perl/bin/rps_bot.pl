#!/usr/bin/perl

# This is a bot that starts a rock-paper-scissors game, and plays it.

use warnings;
use strict;

#########
# Config
#########

my $gameserver_username = 'test';
my $gameserver_host = 'localhost';
my $gameserver_resource = 'rpsserver';
my $host = 'localhost';
my $user = 'jmac';
my $password = 'zipperz';
my $resource = 'rps_bot';
my $debug = 1;

#########
# End config
#########

my $game_jid;
my $game_over;

my $bot = Volity::Bot::RPS->new({
				 user=>$user,
				 host=>$host,
				 password=>$password,
				 resource=>$resource,
				 debug=>$debug,
				 alias=>'rps_bot',
			       });

$bot->kernel->run;

package Volity::Bot::RPS;

use base qw(Volity::Jabber);
# Mmm, take note of which fields should go in a bot class.
use fields qw(game_jid referee_jid nickname has_guessed has_started);

use warnings; use strict;

use POE qw(
	   Wheel::SocketFactory
	   Wheel::ReadWrite
	   Filter::Line
	   Driver::SysRW
	   Component::Jabber;
	  );

sub initialize {
  my $self = shift;
  $self->SUPER::initialize;
  $self->nickname('rps_bot');
}

# Starting a game as soon as we're authed is one way to be a good bot.
sub jabber_authed {
  my $self = $_[OBJECT];
  $self->make_rpc_request({
			to=>"$gameserver_username\@$gameserver_host/$gameserver_resource",
			methodname=>'new_game',
			id=>'new_game',
		      });
}


sub handle_rpc_response {
  my $self = shift;
  my ($data) = @_;
  if ($$data{id} eq 'new_game') {
    $self->game_jid($$data{response});
    $self->referee_jid("$$data{response}/volity"); # XXX Lame.
    $self->join_muc({
		     jid=>$$data{response},
		     nick=>$self->nickname,
		   });
  } elsif ($$data{id} eq 'start_game') {
    # As an RPS-bot, I'm just going to make a guess, and that's all for me.
    $self->take_turn;
  }
}

sub take_turn {
  my $self = shift;
  my @hands = qw(rock paper scissors);
  my $hand = $hands[rand(3)];
  $self->send_message({
		       type=>'chat',
		       to=>$self->referee_jid,
		       body=>$hand,
		     });
}

sub start_new_game {
  my $self = shift;
  $self->make_rpc_request({
			  to=>$self->referee_jid,
			   id=>'start',
			   methodname=>'start_game',
			 });
  $self->has_started(1);
  $self->has_guessed(0);
}

sub handle_groupchat_message {
  my $self = shift;
  my ($message) = @_;
  if (not($self->has_started)) {
    $self->make_rpc_request({
			  to=>$self->referee_jid,
			  id=>'start',
			  methodname=>'start_game',
			});
    $self->has_started(1);
  } elsif (not($self->has_guessed)) {
    $self->take_turn;
    $self->has_guessed(1);
  } elsif ($$message{from} eq $self->referee_jid) {
    if ($$message{body} =~ /^$resource/) {
      $self->send_message({
			   body=>"Ha ha ha! I win! Let's play again!",
			   type=>"groupchat",
			   to=>$self->game_jid
			 });
      $self->start_new_game;
    } elsif ($$message{body} =~ /^A tie!/) {
      $self->send_message({
			   body=>"Well, that's no fun. Let's play again.",
			   type=>"groupchat",
			   to=>$self->game_jid
			 });
      $self->start_new_game;
    } elsif ($$message{body} =~ / $resource/) {
      $self->send_message({
			   body=>"Curses! I demand a rematch!",
			   type=>"groupchat",
			   to=>$self->game_jid
			 });
      $self->start_new_game;
    }
  }
}
