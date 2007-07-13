var svg_ns = "http://www.w3.org/2000/svg";
var xlink_ns = "http://www.w3.org/1999/xlink";

var marks = [];

var seat_whose_turn_it_is = '';

// Mousedown handler.
// If it's my turn, _and_ the square has not been marked, the we'll
// darken the square. This provides some nice, immediate interactivity
// that shows the user that their clicking will have an effect, even though
// we haven't talked to the referee yet.
var this_box;
square_mousedown = function() {
    this_box = this;
    var location_number = this.id.substring(7);
    if (it_is_my_turn() && !marks[location_number]) {
        this.style.setProperty( 'fill', 'gray', null );
        this.addEventListener('mouseout',square_mouseout,false);
        this.addEventListener('click', square_clicked,false);
        this.addEventListener('mouseup',square_mouseup,false);
    }
    else if (!it_is_my_turn()) {
        message('volity.not_your_turn');
    }
    else {
        message('game.already_marked');
    }
}



// Mouseup handler.
// If it's my turn, set the square's fill to white.
// This clears away any graying-out cuased by the mousedown handler.
square_mouseup = function() {
    var location_number = this_box.id.substring(7);
    this_box.style.setProperty( 'fill', 'white', null );
    this_box.removeEventListener('mouseout',square_mouseout,false);
    this_box.removeEventListener('mouseup',square_mouseup,false);
}

square_mouseout = function() {
    this_box.removeEventListener('click',square_clicked,false);
    square_mouseup();
}

square_clicked = function() {
    this.removeEventListener('click', square_clicked,false);
    var location_number = this.id.substring(7);
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

it_is_my_turn = function() {
    return info.seat == seat_whose_turn_it_is;
}

highlight_square = function(square_id) {
    document.getElementById(square_id).style.setProperty( 'fill', 'yellow', null );
}

var game;

if ( ! game ) {
    game = {};
}

game.mark = function(seat_id, location) {
  // Conveniently, we can fetch both the mark symbol ("X" or "O") and the
  // grid square object we'll need based on the arguments we've been passed.
  // This is why we gave our SVG objects the ID values that we did!
  var mark_symbol_id = seat_id + "-mark";
  var grid_square_id = "square-" + location;
  var grid_square = document.getElementById(grid_square_id);
  
  // Figure out where the mark goes, based on the grid square's location.
  // The "- 0" is just to cast the attribute values into integers.
  var square_x = grid_square.getAttribute("x") - 0;
  var square_y = grid_square.getAttribute("y") - 0;

  // Create the <use> element that makes the mark.
  var mark = document.createElementNS(svg_ns, "use");
  mark.setAttributeNS(xlink_ns, "xlink:href", "#" + mark_symbol_id );
  mark.setAttribute("x", square_x + 20);
  mark.setAttribute("y", square_y + 20);

  document.rootElement.appendChild(mark);

  marks[location] = mark;

}

game.tie = function () {
    // Mark both seats as winners!
    var seatmarks = [];
    seatmarks["x"] = "win";
    seatmarks["o"] = "win";
    seatmark(seatmarks);
    seat_whose_turn_it_is = '';
}

game.win = function(seat_id, location_1, location_2, location_3) {
    // Highlight the squares involved in the win.
    highlight_square("square-" + location_1);
    highlight_square("square-" + location_2);
    highlight_square("square-" + location_3);
    // Mark the winning seat.
    // Here we will use the _other_ way you can call seatmark(), where
    // we'll instead pass a hash argument of the seats to mark, and which
    // mark (win, turn, first, or other) to mark them with.
    var seatmarks = {};
    seatmarks[seat_id] = "win";
    seatmark( seatmarks );
    seat_whose_turn_it_is = '';
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

var volity;
if ( ! volity ) {
    volity = {};
}

volity.start_game = function() {
    // Clear any marks made in the last game.
    // Also, set the background of all the squares to white, which will erase
    // any highlights made from a win in the previous game.
    for (i in marks) {
        document.rootElement.removeChild(marks[i]);
    }
    for (var index = 0; index <= 8; index++) {
        var square = document.getElementById("square-" + index);
        square.style.setProperty('fill','#ffffff',null);
    }
    // Empty out the marks array.
    marks = [];
}

volity.resume_game = function() {};

volity.end_game = function() {};

for ( var i = 0; i <=8; i++ ) {
    var square = document.getElementById('square-'+i);
    square.addEventListener('mousedown', square_mousedown,false);
}

