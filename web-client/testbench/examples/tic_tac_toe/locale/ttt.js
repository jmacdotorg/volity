
// marks will hold an associative array
var marks = new Array;

var seat_whose_turn_it_is = '';

/********************
 ** EVENT HANDLERS **
 ********************/

// Click handler.
// If it's my turn, _and_ the square has not been marked, then we'll
// send the game.mark() RPC to the referee.
// You'll note that this document doesn't define the rpc() function.
// The rpc() function is part of Volity's ECMAScript API, and as such
// the client embedding this code has already defined it.
square_clicked = function(location_number) {
    if (it_is_my_turn() && !marks[location_number]) {
        rpc("mark", location_number);
    }
    else if (!it_is_my_turn()) {
        message('volity.not_your_turn');
    }
    else {
        message('game.already_marked');
    }
}

/***************************
 ** INCOMING RPC HANDLERS **
 ***************************/

volity.start_game = function() {
    // Set the background of all the squares to white, which will erase
    // any highlights made from a win in the previous game.
    // Also, clear the squares of any marks from the last game.
    var squares_group = document.getElementById("squares-group");
    for (var index = 0; index <= 8; index++) {
        var square_id = "square_" + index;
        $('tic-tac-toe').unhighlight_square(square_id);
        $('tic-tac-toe').clear_square(square_id);
    }
    // Empty out the marks array.
    marks = [];
}

volity.resume_game = function() {

}

game.mark = function(seat_id, location) {
    // Figure out which square is getting marked.
    var grid_square_id = "square_" + location;
    var grid_square = document.getElementById(grid_square_id);

    // Figure out what the mark symbol to add is, based on the seat ID.
    var mark_for = {x: 'X', o: 'O'};
    var mark_symbol;
    if (mark_symbol = mark_for[seat_id]) {
        // Note this mark in our internal hash of marks, so that other
        // functions know that this square has a mark in it now.
        marks[location] = "seat_id";

        // Tell the SWF to set the square's label to this mark.
        $('tic-tac-toe').set_square_label(grid_square_id, mark_symbol);
}
    else {
        literalmessage('The seat ' + seat_id + ' is neither x nor o.');
    }
}

// game.must_mark is called when someone's turn begins.
// We respond to this by calling the seatmark() function. This function is
// part of Volity's ECMAScript API, and causes the client to mark the given
// seat in a way we specify. You'll see it used more in the next two functions.
game.must_mark = function(seat_id) {
    // By calling seatmark with a single seat ID argument, the client will
    // take it to mean that it's that seat's turn.
    seatmark(seat_id);
    // Also, change the value of seat_whose_turn_it_is as appropriate.
    // The "info" object is another part of Volity's ECMAScript API,
    // and its "seat" field contains the ID of my own seat.
    seat_whose_turn_it_is = seat_id;
}

game.win = function(seat_id, location_1, location_2, location_3) {
    // Highlight the squares involved in the win.
    $('tic-tac-toe').highlight_square("square_" + location_1);
    $('tic-tac-toe').highlight_square("square_" + location_2);
    $('tic-tac-toe').highlight_square("square_" + location_3);
    // Mark the winning seat.
    // Here we will use the _other_ way you can call seatmark(), where
    // we'll instead pass a hash argument of the seats to mark, and which
    // mark (win, turn, first, or other) to mark them with.
    var seatmarks = {};
    seatmarks[seat_id] = "win";
    seatmark( seatmarks );
    seat_whose_turn_it_is = '';
}

game.tie = function () {
    // Mark both seats as winners!
    var seatmarks = [];
    seatmarks["x"] = "win";
    seatmarks["o"] = "win";
    seatmark(seatmarks);
    seat_whose_turn_it_is = '';
}


/*********************
 ** OTHER FUNCTIONS **
 *********************/

// Here are some custom functions that our handlers call.

remove_children = function(parent) {
    var obj, ls;
    ls = parent.childNodes;
    while (ls.length > 0) {
        obj = ls.item(0);
        parent.removeChild(obj);
    }
}

it_is_my_turn = function() {
    return info.seat == seat_whose_turn_it_is;
}

