// These are called by the client

// client.registerCommand( "command", "short desc", "long desc" );
client.registerCommand(
    "select", "Make your selection for this round of RPS", 
    "select <rock|paper|scissors>\n"+
    "Select rock, paper or scissors.  The decision is yours!\n"
    );

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

// These are called by the referee (via RPC)

game.start_game = function() {
    writeln( "RPS begins!  Have fun and good luck!" );
    status = new Object;
}

game.end_game = function() {
    writeln( "That's all for this game..." );
}

game.error = function(message) {
    status.error = message;
    writeln( "An error has occured: " + message );
}


game.player_chose_hand = function(player, hand) {
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
