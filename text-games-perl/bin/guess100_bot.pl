#!/usr/bin/perl

# This is a bot that starts a number-guessing game and plays it.
# It does not play it very well.

use warnings;
use strict;

#########
# Config
#########

my $gameserver_username = 'test';
my $gameserver_host = 'localhost';
my $gameserver_resource = 'guess100server';
my $host = 'localhost';
my $user = 'jmac';
my $password = 'zipperz';
my $resource = 'guess_bot';
my $debug = 1;

#########
# End config
#########

my $game_jid;
my $game_over;
my $last_guess = 1;

my $bot = Volity::Bot::Guess100->new({
				 user=>$user,
				 host=>$host,
				 password=>$password,
				 resource=>$resource,
				 debug=>$debug,
				 alias=>'guess100_bot',
			       });

$bot->kernel->run;

package Volity::Bot::Guess100;

use base qw(Volity::Jabber);
# Mmm, take note of which fields should go in a bot class.
use fields qw(game_jid referee_jid nickname has_guessed has_started last_guess);

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
  $self->nickname('guess100_bot');
  $self->last_guess(0);
}


sub jabber_authed {
  my $self = shift;
  $self->debug("All right, we're connected. Let's try some calls.\n");
  $self->debug("Attempting to send a new_game call to $gameserver_username\@$gameserver_host/$gameserver_resource\n");
  $self->make_rpc_request(
			  {
			  to=>"$gameserver_username\@$gameserver_host/$gameserver_resource",
			  methodname=>'new_game',
			  id=>'new_game',
			 }
			 );
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
    # I will wait patiently until I'm told it's my turn, via groupchat.
  }
}

sub handle_groupchat_message {
  my $self = shift;
  my ($info) = @_;
  if (not($self->has_started)) {
    $self->make_rpc_request({
			  to=>$self->referee_jid,
			  id=>'start',
			  methodname=>'start_game',
			});
    $self->has_started(1);
  } elsif ($$info{body} =~ /guess100_bot's turn/) {
    $self->take_turn;
  }
}



sub take_turn {
  my $self = shift;
  $self->send_message({to=>$self->game_jid ."/volity",
		type=>"chat",
		body=>$self->last_guess,
	      });
 $self->{last_guess}++; 
}

sub jabber_presence {
  my $self = shift;
  my ($node) = @_;
  print $node->to_str . "\n";
}
