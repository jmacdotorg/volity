// These are called by the client

// client.registerCommand( "command", "short desc", "long desc" );
client.registerCommand(
    "play", "Play a card", 
    "play <cardname>\n"+
    "Specify <cardname> through the card's two-character name.\n"+
    "For example, 'KD' is the King of Diamonds, and\n"+
    "'2S' is the Two of Spades."
    );

client.registerCommand(
    "choose_suit", "Choose a suit",
    "choose_suit <suit>\n"+
    "You must choose a suit after playing a (crazy) eight.\n"
    );

client.registerCommand(
    "hand", "See your hand",
    "hand\n"+
    "Displays a list of the cards in your hand.\n"
    );

client.registerCommand(
    "draw", "Draw a card",
    "draw\n"+
    "Draws the top card from the draw pile.\n"
    );

client.registerCommand(
    "status", "View game status",
    "status\n"+
    "Displays your hand, the card to match,\n"+
    "and your opponents' hand sizes.\n"
    );

client.play = function(card) {
    rpc( "play_card", card );
}

client.choose_suit = function(suit) {
    rpc( "choose_suit", suit );
}

client.draw = function() {
    rpc( "draw_card" );
}

client.hand = function() {
    report_hand();
}

client.status = function() {
    report_status();
}

// These are called by the referee (via RPC)

game.start_game = function() {
    writeln( "Crazy Eights begins!  Have fun and good luck!" );
    status = new Object;

    //  Argh, I don't know a smarter way to tell the number of 
    //  elements in a JS object...
    var starting_card_count;
    var opponent_count = 0;

    for (nickname in info.opponents) {
        opponent_count++;
    }
    if (opponent_count < 2) {
        starting_card_count = 7;
    } else {
        starting_card_count = 5;
    }

    for (nickname in info.opponents) {
        writeln ("Leeeet's dooooo " + nickname);
        info.opponents[nickname].card_count = starting_card_count;
    }

}

game.error = function(message) {
    status.error = message;
    writeln( "An error has occured: " + message );
}

game.draw_card = function(card) {
    // push the given card unto to the player's hand.
    hand.cards[card] = card;
    writeln("You drew a card: " + card);
    report_hand();   
}

game.receive_hand = function(card_array) {
    // make the given cards become the player's hand.
    hand.cards = {};
    for (var index in card_array) {
	var card = card_array[index];
        hand.cards[card] = card;
    }
    report_hand();
}

game.starter_card = function(card) {
    match_card = card;
    writeln ("The starter card is: " + card);
}

game.player_played_card = function(player, card) {
    match_card = card;
    match_suit = null;
    writeln (player + " played " + card + ".");
    writeln("Hey, this is me: " + info.nickname);
    if (player == info.nickname) {
//        writeln( "Hey, that's me!" );
        delete(hand.cards[card]);
        report_hand();
    } else {
        info.opponents[player].card_count -= 1;
    }
}

game.player_chose_suit = function(player, suit) {
    writeln (player + " declares the suit to be " + suit);
    match_suit = suit;
}    

game.player_passes = function(player) {
    writeln (player + " must pass.");
}

game.player_drew_card = function(player) {
    writeln (player + " drew a card.");
    if (player != info.nickname) {
        info.opponents[player].card_count += 1;
    }
}

game.scores = function(scores) {
    for (player in scores) {
	writeln (player + " got " + scores[player] + " points.");
    }
}

function end_game() {
    writeln ("Game over!");
    match_card = null;
    match_suit = null;
}

game.start_turn = function(player) {
    turned_player = player;
    report_turn();
}

var hand = new Object;
var match_card;
var match_suit = null;
var hand_size = 5;
var turned_player;

function card_string() {
    cardz = new Array;
    var j = 0;
    var theLength = hand.cards.length;
    for (var foo in hand.cards) {
        cardz[j++] = foo;
    }
    cardz.sort();
    return cardz.join(' ');
}

function report_status() {
    report_hand();
    report_match_card();
    report_opponents();
    report_turn();
}

function report_match_card() {
    writeln( "The last card played was: " + match_card);
    if (match_suit != null) {
        writeln( "The declared suit is: " + match_suit);
    }
}

function report_hand() {
    writeln( "You are holding these cards:" );
    writeln( card_string() );
}

function report_opponents() {
    for (nickname in info.opponents) {
        writeln( nickname + " is holding " + info.opponents[nickname].card_count + " cards." );
    }
}

function report_turn() {
    writeln( "It is " + turned_player + "'s turn." );
}

writeln( "eights.js Loaded!" );
