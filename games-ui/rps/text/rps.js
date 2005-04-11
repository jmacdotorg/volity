// These are called by the client

// client.registerCommand( "command", "short desc", "long desc" );
client.registerCommand(
    "select", "Make your selection for this round of RPS", 
    "select <rock|paper|scissors>\n"+
    "Select rock, paper or scissors.  The decision is yours!\n"
    );

client.registerCommand(
	"no_ties", "(Config) Declare that ties don't count",
	"no_ties\n"+
	"By calling this during game configuration, you declare that ties don't count,\nand must be replayed."
	);

client.registerCommand(
	"ties_ok", "(Config) Declare that ties _do_ count (defualt)",
	"ties_ok\n"+
	"By calling this during game configuration, you decalre that ties count as a win\n for both players. This is the game's default setting."
	);

client.registerCommand(
	"best_of", "(Config) Declare the length of this RPS match",
	"best_of [number]\n"+
	"By calling this during gam configuration, you set the length of this RPS match.\nPlayers who win more than half of this value's worth of hands win the match.\nThe default setting is 1."
	);

client.no_ties = function() {
    rpc("no_ties", 1);
}

client.ties_ok = function() {
    rpc("no_ties", 0);
}

client.best_of = function(number) {
    rpc("best_of", number);
}

client.select = function(type) {
    status.player = type;
    if ( type == "rock" ) {
        writeln( "Good old rock! Nothing beats rock." );
    } else if ( type == "scissors" ) {
        writeln( "You chose scissors." );
    } else if ( type == "paper" ) {
        writeln( "Paper it is." );
    } else {
        writeln( "You have some funky new type (it better not be dynamite): " + type );
    }
    rpc( "choose_hand", type );
}

// These are called by the client directly, at appropriate times.

config = new Object;
config.no_ties = 0;
config.best_of = 1;

START = function() {
    writeln( "RPS begins!  Have fun and good luck!" );
    status = new Object;
}

END = function() {
    writeln( "That's all for this game..." );
}

ERROR = function(message) {
    status.error = message;
    writeln( "An error has occured: " + message );
}

// These are called by the referee (via RPC)

game.best_of = function(player, game_count) {
    if (config.game_count != game_count) {
       config.game_count = game_count;
       writeln ("CONFIG CHANGE: " + player + " declares this to be a best-of-" + game_count + " match.");
    }
}

game.no_ties = function(player, value) {
    writeln ("Got " + player + " and " + value);
    if (value) {
        if (config.no_ties == 0) {
            writeln("CONFIG CHANGE: " + player + " declares that ties require a rethrow.");
            config.no_ties = 1;
        }
    } else {
        if (config.no_ties == 1) {
            writeln("CONFIG CHANGE: " + player + " declares that ties do not require a rethrow.");
            config.no_ties = 0;
        }
    }
}

game.player_chose_hand = function(player, hand) {
//    writeln (player + " threw " + hand + ".");
//    writeln ("I am " + info.nickname);
    if (player != info.nickname) {
      writeln (player + " threw " + hand + ".");
      var myHand = status.player;
      var result = 0;
      if (myHand == 'rock') {
        if (hand == 'scissors') {
          result = 1;
        } else if (hand == 'paper') {
          result = -1;
        }
      } else if (myHand == 'paper') {
        if (hand == 'scissors') {
          result = -1;
        } else if (hand == 'rock') {
          result = 1;
        }
      } else {
        if (hand == 'rock') {
          result = -1;
        } else if (hand == 'paper') {
          result = 1;
        }
      }
      if (result == -1) {
        writeln ("Oh no! You lost!");
      } else if (result == 1) {
        writeln ("All right... you won!!");
      } else {
        writeln ("This game is a draw.");
      }
    }
}

writeln( "rps.js Loaded!" );
