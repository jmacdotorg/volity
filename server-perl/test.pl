# Test one: See if our test game (and all the things it depends on) compiles!

use warnings;
use strict;

use Test::More "no_plan";
use Module::Build;

use lib qw(lib t);

use_ok('TestRPS');

our $build = Module::Build->current;
foreach (qw(jabber_user jabber_password jabber_host server_resource)) {
  die "Bleah, can't get $_ notes from the current Module::Build object. Are you running this script outside of the 'Build test' test harness? Don't do that! Sheesh!" unless defined($build->notes($_));
}

# Fake Bookkeeper jid; we're not actually going to talk to one.
my $bookkeeper_jid = 'fake@localhost';

our $referee_jid;
our $muc_jid;
our @expected_fault_codes = (999, 999, 1);
our $server = TestRPS::Server->new(
				 {
				  user=>$build->notes('jabber_user'),
				  password=>$build->notes('jabber_password'),
				  host=>$build->notes('jabber_host'),
				  resource=>$build->notes('server_resource'),
				  referee_class=>'TestRPS::Referee',
				  alias=>'volity',
				  bookkeeper_jid=>$bookkeeper_jid,
				  debug=>0,
				 }
				);

isa_ok($server, "Volity::Server");

my $server_pid;

#my @script = qw(auth table_creation table_configuration start_game insufficient_player_fault auth start_game start_game_response game_record auth);
my @script = qw(auth table_creation  insufficient_player_fault auth start_game_response game_record auth);
#our $checker = Volity::Test::Checker->new;

my %script_hash = set_script_hash();


$server->start;

our ($huey, $dewey, $louie);

sub has_authed {

#  print "in has_authed: $server\n";

  ok($server->has_authed, "Game connection and authentication to Jabber server");

  $huey = new_bot('huey');
    $huey->kernel->run;  
}

sub check_script {
  my ($arg) = @_;
  my $method = shift(@script);
  unless (defined($method)) {
    die "Uh oh... I got a call to check the script, but there's no script left.\n";
  }
#  $checker->log($arg);
#  $checker->$method($arg);
  my $value = $script_hash{$method};
  unless (defined($value)) {
    die "Hey, I daon't have any script value defined for '$method'. What gives?";
  }
  if (ref($value)) {
    if (ref($value) eq 'HASH') {
      # XXX Magic here.
      pass("A hash... yeah, whatever");
    } elsif (ref($value) eq 'CODE') {
      pass("Hmm, a coderef. Whatever.");
    } elsif (ref($value) eq 'Regexp') {
      # It's a qr to check against.
      my $xml = $arg->to_str;
      like($xml, $value, "Received expected element.");
    } else {
      die "Uhm, Don't know what to do with value of type " . ref($value);
    }
  }
}

sub new_bot {
  my ($nick) = @_;
  return Volity::Bot::RPS->new(
				{
				 user=>$build->notes('jabber_user'),
				 password=>$build->notes('jabber_password'),
				 host=>$build->notes('jabber_host'),
				 resource=>$nick,
				 alias=>$nick,
				 nickname=>$nick,
				});
}


sub bot_authed {
  my ($bot) = @_;
#  pass("Bot has connected successfully.");
  if ($bot->nickname eq 'huey') {
    $bot->make_rpc_request({
			     to=>$server->jid,
			     methodname=>'new_game',
			     id=>'new_game',
			    });
#    warn "Made an RPC req...\n";
  } elsif ($bot->nickname eq 'louie') {
    $bot->join_muc({jid=>$main::muc_jid, nick=>$bot->nickname});
    # XXX check for presence here???
#    warn "Should be in MUC now...\n";
#    warn $main::muc_jid;
  }

}

sub bot_invited {
  my ($bot, $rpc_data) = @_;
#  pass($bot->nickname ." was invited into a MUC.");
}

sub bot_joined {
  my ($bot) = @_;
#  pass ($bot->nickname . " has joined a MUC.");

}

sub bot_got_fault {
  my ($bot, $rpc_object) = @_;
  my $expected = shift(@expected_fault_codes);
#  warn "Mew... $expected\n";
#  is ($rpc_object->value->code, $expected, "Received expected RPC fault from referee");
  if ($bot->nickname eq 'huey') {
    # Awww, huey doesn't have any fwiends. Make 'im a new one.
    $louie = new_bot('louie');
    $louie->referee_jid($huey->referee_jid);
    $louie->kernel->run;
  }
}

sub end_test {
  $server->stop;
#  pass("Server has stopped.");

  foreach (grep(defined($_), $huey, $dewey, $louie)) {
    $_->kernel->post($_->alias, 'shutdown_socket', 0);
  }

}

sub started_game {
  my ($bot, $rpc_object) = @_;
#  pass("Game started successfully.");
}

sub examine_record {
  my ($record) = @_;
#  pass("Got a record.");
  end_test;
}

package Volity::Bot::RPS;

use base qw(Volity::Jabber);
# Mmm, take note of which fields should go in a bot class.
use fields qw(server_jid game_jid referee_jid nickname has_guessed has_started);

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
}

# Starting a game as soon as we're authed is one way to be a good bot.
sub jabber_authed {
  my $self = $_[OBJECT];
  # XXX
  main::bot_authed($self);
  main::check_script($_[ARG0]);
}

sub jabber_iq {
  my $self = shift;
  main::check_script(@_);
  return $self->SUPER::jabber_iq(@_);
}

sub jabber_presence {
  my $self = shift;
  my ($node) = @_;
  my $from = $node->attr('from');
  if (($from =~ /huey$/ or $from =~ /louie$/) and $self->nickname eq 'huey') {
    $self->start_new_game;
  }
}

sub handle_rpc_response {
  my $self = shift;
  my ($data) = @_;
  if ($$data{id} eq 'new_game') {
    $self->game_jid($$data{response});
    # Hardcoding 'volity' below, since it's the hardcoded nickname of the
    # referee's in-MUC presence. Tra la.
    $self->referee_jid("$$data{response}/volity");
    $main::referee_jid = $self->referee_jid;
    $main::muc_jid = $$data{response};
    main::bot_invited($self, $data);
    $self->join_muc({
		     jid=>$$data{response},
		     nick=>$self->nickname,
		   });
    main::bot_joined($self);
  } elsif ($$data{id} eq 'start') {
    # As an RPS-bot, I'm just going to make a guess, and that's all for me.
    if ($$data{rpc_object}->is_fault) {
      main::bot_got_fault($self, $$data{rpc_object});
    } else {
      main::started_game($self, $$data{rpc_object});
    }
  }
}

sub take_turn {
  my $self = shift;
  my $hand;
  if ($self->nickname eq 'huey') {
    $hand = 'rock';
  } else {
    $hand = 'scissors';
  }
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
  if ($$message{body} =~ /game has begun!/) {
#    warn "OK, I am " . $self->nickname . " and I'm taking my turn.\n";
    $self->take_turn;
  }

}

package Volity::Test::Checker;

use warnings; use strict;
use base qw(Class::Accessor);

use Data::Dumper;

sub new {
  unlink ("checker_log");
  return bless ({}, $_[0]);
}

sub log {
  my $self = shift;
  my ($thingy) = @_;
  open (LOG, ">>checker_log") or die "Can't open checker_log... $!";
  print LOG "******************************************************\n";
  print LOG "Received: $thingy\n";
  if (ref($thingy) eq 'HASH') {
    print LOG Dumper($thingy);
  } elsif ($thingy->isa("PXR::Node")) {
    print LOG "Node dump:\n";
    print LOG $thingy->to_str . "\n";
  }
  close LOG;
}

package main;

sub set_script_hash {
  # XXX Can build and drop in more specific patterns later.
  my $jid_pattern = '[\w-]+?@[\w-]+?(?:\.[\w-]+?)*(?:/[\w-]+)?';
  return (
	  auth=>
	  qr{<iq id='1' type='result'/>},
	  table_creation=>
	  qr{<iq to='$jid_pattern' from='$jid_pattern' id='new_game' type='result'><query xmlns='jabber:iq:rpc'><methodResponse><params><param><value><string>.*?</string></value></param></params></methodResponse></query></iq>},
	  table_configuration=>
	  qr{<iq to='$jid_pattern' from='$jid_pattern' type='result' id='42' xml:lang='en'/>},
	  start_game=>
	  qr{<iq to='$jid_pattern' from='$jid_pattern' type='set' id='start'><query xmlns='jabber:iq:rpc'><methodCall><methodName>start_game</methodName><params/></methodCall></query></iq>},
	  insufficient_player_fault=>
	  qr{<iq to='$jid_pattern' from='$jid_pattern' id='start' type='result'><query xmlns='jabber:iq:rpc'><methodResponse><fault><value><struct><member><name>faultString</name><value><string>This game takes between 2 and 2 players, but there are 1 players here. That&apos;s not enough!</string></value></member><member><name>faultCode</name><value><int>999</int></value></member></struct></value></fault></methodResponse></query></iq>},
	  start_game_response=>
	  qr{<iq to='$jid_pattern' from='$jid_pattern' id='start' type='result'><query xmlns='jabber:iq:rpc'><methodResponse><params><param><value><string>ok</string></value></param></params></methodResponse></query></iq>},
	  game_record=>
	  {},
	 );
 
}
