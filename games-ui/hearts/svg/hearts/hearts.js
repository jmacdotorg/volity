/* constants */
var svg_ns = "http://www.w3.org/2000/svg";
var xlink_ns = "http://www.w3.org/1999/xlink";
var deck_ns = "http://volity.org/resources/carddeck/poker.html"
var widgets_ns = "http://games.staticcling.org/widgets/1.0"

// these heights & widths are all in the card coordinate systems, where the
// cards are represented in 1x1 spaces, not necessarily filling the rectangle
// -- the cards are as wide as the aspect-ratio metadata says they are.  This
// assumes, of course, cards that are taller than they are wide, like every
// other playing card I've ever seen.
var card_width = get_card_width();
var half_card_width = card_width / 2;
var card_height = 1; // always 1
var half_card_height = card_height / 2;

var hand_overlap = find_west_stacking();

// for translating the Games::Cards numbers to the poker-deck numbers
var t_nums = new Array(13);
t_nums["A"] = "ace";
t_nums[2] = "2";
t_nums[3] = "3";
t_nums[4] = "4";
t_nums[5] = "5";
t_nums[6] = "6";
t_nums[7] = "7";
t_nums[8] = "8";
t_nums[9] = "9";
t_nums[10] = "10";
t_nums["J"] = "jack";
t_nums["Q"] = "queen";
t_nums["K"] = "king";
// for translating the Games::Cards suits to the poker-deck suits
var t_suits = new Array(4);
t_suits["C"] = "club";
t_suits["D"] = "diamond";
t_suits["H"] = "heart";
t_suits["S"] = "spade";

/* global variables */
var passing = 0; // are we presently involved in passing cards?
var seats_passing = new Array(4); // storage for passing seat marks
var pass_hand = new Array(3); // which cards are being passed?
pass_hand['pass-card-0'] = null;
pass_hand['pass-card-1'] = null;
pass_hand['pass-card-2'] = null;
var first_card = true; // is this the first card of a trick?
var variants_selectionlist;
var end_score_selectionlist;
var want_config_dialog = 0; // does this version of the ref use the config vars?

// which directions exist for locating players' hands
var directions = ["player", "left", "across", "right"];
var seat_to_direction = new Array(4);

var current_player;

/* RPC callback functions */

volity.start_game = function() {
    seatmark();
    clear_playfield();
    clear_scores();
    set_up(true);
}

// show the config dialog again at the end of the game
volity.end_game = function() {
    if (want_config_dialog) {
        document.getElementById("config-dialog").setAttribute("display", "block");
    }
}

// Joining a game which has begin.
volity.game_has_started = function() {
    volity.start_game();
}

// resuming a game -- we might have been dragged into a new seat, so request
// an update on our hand.
volity.resume_game = function() {
    set_up(false); // arrange all of the nicknames properly
    if (am_seated()) { rpc("volity.send_state"); }
}

// Don't show the config dialog until we know what the state of the various
// configuration variables is.  This prevents bits of ugly flickering.  Also
// don't show it if we haven't received any configuration RPCs -- this means
// that we're running with an older version of the ref that doesn't support
// config.
volity.state_sent = function() {
    if (info.state == "setup" && want_config_dialog) {
        document.getElementById("config-dialog").setAttribute("display", "block");
    }
}

// Configuration RPCs

// At what score does the game end?
game.game_end_score = function(score) {
    end_score_selectionlist.showValue(score);
    want_config_dialog = 1;
}

// the server tells us which variants it supports
game.supported_variants = function(variants) {
    var supported = new Array();

    // copy the data out of the array passed to us, so that it will actually
    // work in the widgets library
    for (idx in variants) {
        supported[idx] = variants[idx]
    }

    variants_selectionlist.setChoices(supported);
    want_config_dialog = 1;
}

// What variant are we playing
game.variant = function(variant) {
    variants_selectionlist.showValue(variant);
    show_variant_description(variant);
}

// Game RPCs

// tells us that it is now a new player's turn
game.start_turn = function(seat_id) {
    seatmark(seat_id);
    current_player = seat_id;
}

// tells us what our hand is
game.receive_hand = function(hand) {
    clear_hand();
    draw_hand(hand);
}

// This routine tells the player they need to pass some cards in some
// direction.  Indirectly, it also tells the client that a new round has
// started.
game.must_pass = function(which_dir, how_many) {
    // clearing the last trick here makes it really hard to tell what happened
    // in the last trick of the round.  Don't do it

    // clear out any remnants of a previously unconcluded pass attempt here
    // (this is in relation to suspend/resume during passing)
    clear_passed_cards();

    // if this is a hold round, don't even go there
    if (how_many == 0) {
        return;
    }

    // set up the seat marks -- all seats are passing ("other" is the name
    // of the mark we want, see the docs for seatmark)
    for (idx in info.gameseats) {
        seats_passing[info.gameseats[idx]] = "other";
    }
    seatmark(seats_passing);

    // don't show the passing box to non-seated players
    if (!am_seated()) {
        return;
    }

    passing = 1;
    clear_passing_box();
    show_passing_box(localize("ui.pass_cards") + " " + localize("ui." + which_dir),
        localize("ui.pass"));
}

game.pass_accepted = function(cards) {
    passing = 0;
    hide_passing_box();
    clear_passing_box();
    clear_last_trick();
}

game.receive_pass = function(cards) {
    clear_passing_box();

    // Clear the passing flag, because clearly we can't be passing if we've
    // reveived a pass.  More suspend/resume work, because if you're passing 
    // while the game is suspended, and you change seats to a seat which has
    // already passed, the client never gets a pass_accepted RPC.
    passing = 0;

    // XXX this really should go after we've created the cards (logically
    // anyway), but it appears that the cards (zarf's cards anyway) don't like
    // to be unhidden.  I'm suspecting a batik bug involving SVG style
    // attributes, CSS styling, and the display attribute.
    show_passing_box(localize("ui.cards_passed"), localize("ui.ok"));

    var count = 0;
    for (card_idx in cards) {
        var slot = document.getElementById("pass-card-" + count++);

        var card = document.createElementNS(svg_ns, "use");
        card.setAttributeNS(xlink_ns, "href", 
            deckpath + "#" + translate_card_name(cards[card_idx]));
        card.setAttribute("id", "pass-received-card_" + cards[card_idx]);
        card.setAttribute("x", 0);
        card.setAttribute("y", 0);
        slot.appendChild(card);
    }

    // rather than add the cards to our hand & redraw it, just request the
    // server to send us the whole thing, and use the RPC functions to do
    // it :)  Ah, laziness, one of the 4 virtues.
    rpc("send_hand"); 
}

game.seat_passed = function(seat) {
    seats_passing[seat] = "";
    seatmark(seats_passing);
}

// this function exists for state recovery, so that the UI can update the
// passing related elements properly.  which_dir and how_many are pretty
// obvious, but passing is an array of the seats which are presently passing
game.passing_info = function(which_dir, how_many, passing) {
    // show the seat marks
    seats_passing.splice(1,3); // empty seats_passing
    for (seat_idx in passing) {
        seats_passing[passing[seat_idx]] = "other";
        if (passing[seat_idx] == info.seat) { passing = 1; }
    }
    seatmark(seats_passing);
}

// the place where the card will be laid should be set to display: none 
game.seat_played_card = function(seat_id, card_name) {
    var card = document.getElementById(seat_to_direction[seat_id] + "_trick-card");
    // set the card image to use appropriately
    card.setAttributeNS(xlink_ns, "href", 
        deckpath + "#" + translate_card_name(card_name));
    card.setAttribute("display", "block"); // and show it

    if (first_card) {
        var highlight = document.getElementById(seat_to_direction[seat_id] + "_trick-highlight");
        highlight.setAttribute("display", "block");
        // now that a card's been played, it's not the first one anymore, right?
        first_card = false;
    }

    // if it was us who played the card, make it disappear from the hand
    if (seat_id == info.seat) {
        card = document.getElementById("card_" + card_name);
        var clickbox = document.getElementById("player_hand_" + card_name);
        card.setAttribute("display", "none");
        clickbox.setAttribute("display", "none");
    }
}

game.seat_won_trick = function(seat_id, trick) {
    clear_current_trick();

    // it's now the first card of a trick again
    first_card = true;

    // set the last trick display up
    for (seat_idx in info.gameseats) {
        var seat = info.gameseats[seat_idx] + "";
        var cardname = trick[seat];

        var id =  seat + "-last_trick-card";
        var card = document.getElementById(id);
        card.setAttributeNS(xlink_ns, "href",
            deckpath + "#" + translate_card_name(cardname));

        // unhighlight all of them
        id =  seat + "-last_trick-text";
        card = document.getElementById(id);
        card.setAttribute("font-weight", "normal");

        id =  seat + "-last_trick-highlight";
        card = document.getElementById(id);
        card.setAttribute("display", "none");
    }

    // highlight the winner of the trick in the infopane
    var id =  seat_id + "-last_trick-text";
    var card = document.getElementById(id);
    card.setAttribute("font-weight", "bold");

    id =  seat_id + "-last_trick-highlight";
    card = document.getElementById(id);
    card.setAttribute("display", "block");
}

// display the freshly anounced score
game.seat_score = function(seat_id, score) {
    var text = document.getElementById(seat_id + "-score");
    for (seat_idx in info.gameseats) {
        var seat = info.gameseats[seat_idx];
        if (seat == seat_id) {
            replace_text(text, score);
        }
    }
}

// show the winners
game.winners = function(winners) {
    var marks = new Array();
    for (idx in winners) {
        marks[winners[idx]] = "win";
    }
    seatmark(marks);
}

/* regular ui-implementing functions */

// config functions

function show_variant_description(variant) {
    var id = "ui." + variant + "-variant-description";
    var localized = localize(id);

    if (/^\(Untranslatable/.test(localized)) {
        localized = localize("ui.unknown-variant");
    }

	// FIXME: this is an ugly hack to work around a limitation in batik where
	// flowed text that is currently displayed isn't updated on screen when 
	// mutated with DOM calls.  Commented out because the textflow stuff
	// doesn't work too good in a live game (tesbench is great)
	//document.getElementById("variant-description-group").setAttribute("display", "none");
    //document.getElementById("variant-description").textContent = localized; // keep this line
	//document.getElementById("variant-description-group").setAttribute("display", "block");

	// Given that I can't get the text flow stuff to happily work for me, I've
	// done the line breaking by hand, indicating breaks with === in the
	// localisation file.  I then split the big string that comes out of the
	// localize function into lines.  The lines get used to construct a text
	// element with trefs laid out vertically.  This arrangement simulates
	// wrapped text.  I then cache the created text element in the defs
	// section of the document so that on future passes through this routine,
	// I can just set the variant-description <use> element to point the
	// results of the previous hard work.
	var description = document.getElementById("variant-description");
	var linked = document.getElementById(variant + "-variant-description");

	if (!linked) {
		var lines = localized.split(/\s+===\s+/);
		var defs = document.getElementById("defs");
		var text = document.createElementNS(svg_ns, "text");
		text.setAttribute("id", variant + "-variant-description");

		for (idx in lines) {
			var tspan = document.createElementNS(svg_ns, "tspan");
			tspan.setAttribute("x", "0.5em");
			tspan.setAttribute("y", idx + "em");
			tspan.setAttribute("dy", "1em");
			tspan.textContent = lines[idx];
			text.appendChild(tspan);
		}

		defs.appendChild(text);
	}

	description.setAttributeNS(xlink_ns, "href", "#" + variant + "-variant-description");
}

// this function is called in an onload from main.svg, element variant-selection-group
function create_variants_config_selectionlist() {
    variants_selectionlist = new SelectionList("variant", "selectionlist-template");
    variants_selectionlist.setSize(18, 4);
    var element = document.getElementById("variant-selection-group");
    element.appendChild(variants_selectionlist.svgElement);

    // Define a capture event, and grab mouseover events for the choice 
    // event-boxes, and set the variant description box based in the info we
    // can extract
    variants_selectionlist.addEventListener("mouseover",
        function(evt) { 
            var target = evt.getTarget();
            if (target.hasAttributeNS(widgets_ns, "index")) {
                var index = target.getAttributeNS(widgets_ns, "index");
                show_variant_description(variants_selectionlist.choiceAtIndex(Number(index)));
            }
        }, 
        true);

    // send the appropriate RPC when a new selection has been made
    variants_selectionlist.addEventListener("selection-changed",
        function(evt) { 
            rpc("variant", variants_selectionlist.getValue()) 
        }, 
        false);

    // restore the correct variant description when the list is collapsed
    // without making a choices
    variants_selectionlist.addEventListener("selection-not-changed",
        function(evt) { 
            show_variant_description(variants_selectionlist.getValue()); 
        }, 
        false);
}

// this function is called in an onload from main.svg, element end_score-selection-group
function create_end_score_config_selectionlist() {
    var onselect = function(text) {};
    end_score_selectionlist = new SelectionList("game_end_score", "selectionlist-template");
    end_score_selectionlist.setChoices(["25", "50", "75", "100"]);
    end_score_selectionlist.setSize(8, 4);
    end_score_selectionlist.addEventListener("selection-changed",
        function(evt) { 
            rpc("game_end_score", end_score_selectionlist.getValue()) 
        }, 
        false);
    var element = document.getElementById("end_score-selection-group");
    element.appendChild(end_score_selectionlist.svgElement);
}

// these next two functions implement radio button groups, that happen to
// conveniently send RPCs to the referee about what was just clicked.  The
// radio-button stuff works like this: 
//      - the groups of buttons are created in the SVG in a <g>; 
//      - that group name gets passed in to this function from the onclick event
//          handler (onclick="config_button_clicked(evt, 'variant-buttons')"); 
//      - that <g> has a namespaced buttons attribute with the names of all of
//          the radio-group's buttons eg.
//              <g ... game:buttons="foo-button bar-button">
//      - the id of the button has a dash-separated name, the first part of
//          which is the value which will be sent as the config data to the
//          game;
//      - the second part of the button's id is the name of the config
//          directive.
//      - the third part of the button's id is typically "button", eg
//          id="standard-variant-button" would cause the rpc call
//          rpc("variant", "standard") to be issued
function light_radio_button(button_group, button_name) {
    var button = document.getElementById(button_name);
    var button_group = document.getElementById(button_group);
    var button_names = button_group.getAttributeNS(widgets_ns, "buttons").split(" ");
    for (idx in button_names) {
        document.getElementById(button_names[idx]).setAttribute("fill", "black");
    }

    button.setAttribute("fill", "lightgreen");
}

function config_button_clicked(evt, button_group) {
    var button = evt.getTarget();
    var id = button.getAttribute("id");

    var id_bits = id.split("-");
    if (id_bits.length < 2) {
        return; // no dashes, this is not the button we're looking for
    }
    var value = id_bits[0];
    var config_variable = id_bits[1];

    // do the appropriate graphical jiggery-pokery
    light_radio_button(button_group, id);

    // inform the ref of the desired change
    rpc(config_variable, value);
}

// gameplay functions

/* The algorithm for figuring out what seats are where in relation to the
 * current player depend on the concept of negative array indexing.  They're
 * used to simulate rotating the directions array, so that we get the proper
 * direction when we use the seat under consideration as an index into that
 * array.  A listing of the rotations follows with 0 = player, 1 = left,
 * across = 2, right = 3
 *
 * seat 1: 0 1 2 3 (starting index of 0)
 * seat 2: 3 0 1 2 (starting index of -1, which translates to 3)
 * seat 3: 2 3 0 1 (starting index of -2, which translates to 2)
 * seat 4: 1 2 3 0 (starting index of -3, which translates to 1)
 * 
 * The rotation of the array is based on the player's seat number 
 * (1 - player_num).  Add the 0-based number of the seat in consideration, and
 * you get the direction that the seat is relative to the player's seat.
 */ 
function set_up(clear) {
    current_player = null;

    // hide the configuration screen
    document.getElementById("config-dialog").setAttribute("display", "none");

    // make sure the UI knows where to show the played cards, even if the
    // player isn't seated
    var my_seat = info.seat + "";
    if (!am_seated()) {
        my_seat = "Seat_1";
    }

    // Set it so that cards from a given seat show up in the right places
    // in the interface. 
    var player_num = my_seat.substr(-1) - 0; // + "" is string conversion
    for (player_idx in info.gameseats) {
        var player = info.gameseats[player_idx] + ""; // + "" is string conversion

        var num = player.substr(-1) - 1; // make the number 0 based
        var direction_idx = (1 - player_num) + num;
        // js Arrays don't do negative indexing, so we fix 'em up
        if (direction_idx < 0) direction_idx += 4; 

        var dir = directions[direction_idx];
        seat_to_direction[player] = dir;
    }

    // set up the various player labels
    for (seat_idx in info.gameseats) {
        var seat_id = info.gameseats[seat_idx] + "";

        // the last trick labels
        var label = document.getElementById(seat_id + "-last_trick-text");
        replace_text(label, info.gameseats[seat_idx].nicknames[0]);

        // the seat labels
        label = document.getElementById(seat_to_direction[seat_id] + "_trick-label");
        replace_text(label, info.gameseats[seat_idx].nicknames[0]);
    }

    if (clear) { clear_playfield(); }
}

function is_my_turn() {
    if (info.state != "active") { return false; }
    if (passing) { return true; }
    if (current_player != info.seat) { return false; }
    return true;
}

function am_seated() {
    return /^Seat/.test(info.seat);
}

function draw_hand(cards) {
    var hand = document.getElementById("hand");
    var horiz_offset = -((card_width + (cards.length - 1) * hand_overlap) / 2);

    for (card_idx in cards) {
        // draw the card
        var card = document.createElementNS(svg_ns, "use");
        card.setAttributeNS(xlink_ns, "href", 
            deckpath + "#" + translate_card_name(cards[card_idx]));
        card.setAttribute("id", "card_" + cards[card_idx]);
        card.setAttribute("x", horiz_offset);
        card.setAttribute("y", 0);
        hand.appendChild(card);

        // add a transpanrent box over top of the cards, set the
        // id appropriately, and put the mouse event handlers on THAT
        var clickbox = document.createElementNS(svg_ns, "rect");
        clickbox.setAttribute("x", horiz_offset);
        clickbox.setAttribute("y", 0);
        clickbox.setAttribute("width", card_width);
        clickbox.setAttribute("height", card_height);
        clickbox.setAttribute("fill", "grey");
        clickbox.setAttribute("fill-opacity", "0.0");
        clickbox.setAttribute("stroke", "none");

        clickbox.setAttribute("id", "player_hand_" + cards[card_idx]);
        clickbox.setAttribute("onmouseover", "raise_card(evt)");
        clickbox.setAttribute("onmouseout", "lower_card(evt); change_opacity(evt, 0, false)");
        clickbox.setAttribute("onmousedown", "change_opacity(evt, 0.2, true)");
        clickbox.setAttribute("onmouseup", "change_opacity(evt, 0, false)");
        clickbox.setAttribute("onclick", "card_clicked(evt)");

        hand.appendChild(clickbox);

        horiz_offset += hand_overlap;
    }
}

// changes the fill-opacity of an element.  If check_turn is true, then it
// only does this on the player's turn
function change_opacity(evt, percent, check_turn) {
    if (check_turn) { if (!is_my_turn()) { return; } }
    var target = evt.getTarget();
    target.setAttribute("fill-opacity", percent);
}

function card_clicked(evt) {
    var id = evt.getTarget().getAttribute("id");

    // don't do anything if it's not our turn
    if (!is_my_turn()) { return; }

    var rv = id.match(/_((?:[\dJQKA]|\d{2})[CDHS])/)
    if (passing == 0) {
        if (rv) {
            rpc("play_card", rv[1]);
        }
    } else {
        for (idx in pass_hand) {
            if (pass_hand[idx] != null) continue;

            pass_hand[idx] = rv[1];

            // hide the card in the hand
            var card = document.getElementById("card_" + rv[1]);
            var clickbox = document.getElementById("player_hand_" + rv[1]);

            card.setAttribute("display", "none");
            clickbox.setAttribute("display", "none");

            // create a new card & clickbox for the pass box
            card = document.createElementNS(svg_ns, "use");
            card.setAttributeNS(xlink_ns, "href", 
                deckpath + "#" + translate_card_name(rv[1]));
            card.setAttribute("id", "pass-card_" + rv[1]);

            clickbox = document.createElementNS(svg_ns, "rect");
            clickbox.setAttribute("id", "pass-player_hand_" + rv[1]);
            clickbox.setAttribute("width", card_width);
            clickbox.setAttribute("height", card_height);
            clickbox.setAttribute("fill", "grey");
            clickbox.setAttribute("fill-opacity", "0.0");
            clickbox.setAttribute("stroke", "none");
            clickbox.setAttribute("onmousedown", "change_opacity(evt, 0.2, true)");
            clickbox.setAttribute("onmouseup", "change_opacity(evt, 0, false)");
            clickbox.setAttribute("onmouseout", "change_opacity(evt, 0, false)");
            clickbox.setAttribute("onclick", "remove_passed_card(evt)");

            var slot = document.getElementById(idx);
            slot.appendChild(card);
            slot.appendChild(clickbox);

            break; // only want to do this once
        }
    }
}

function remove_passed_card(evt) {
    var id = evt.getTarget().getAttribute("id");

    // don't do anything if it's not our turn (or if the game isn't active)
    if (!is_my_turn()) { return; }

    var rv = id.match(/_((?:[\dJQKA]|\d{2})[CDHS])/);
    if (rv == null) literalmessage(id);
    var slot = evt.getTarget().parentNode;
    var slot_id = slot.getAttribute("id");
    pass_hand[slot_id] = null; // clear the spot in the array
    remove_children(slot);

    var card = document.getElementById("card_" + rv[1]);
    var clickbox = document.getElementById("player_hand_" + rv[1]);

    // unhide the card
    card.setAttribute("display", "block");
    // unhide the clickbox, so it can receive events again
    clickbox.setAttribute("display", "block");
}

function pass_ok_clicked(evt) {
    // don't do anything if the game isn't active, but allow passing & such if
    // it's not our turn
    if (info.state != "active") { return; }

    if (passing) {
        // this involves sending the pass request to the server

        // create a normal array (rather than an associative one) to send
        // to the server -- the format of pass_hand is simply an artifact of
        // the svg file we're manipulating and my laziness.
        var count = 0;
        var pass = new Array(3);
        for (idx in pass_hand) {
            if (pass_hand[idx] == null) return; // if cards are missing, no go
            pass[count++] = pass_hand[idx];
        }

        // now that we've verified that everything is ready to go, clear the
        // global array
        clear_passed_cards();

        rpc("pass_cards", pass);
    } else {
        // this is just the player telling us that they've seen what crap was
        // passed to them
        hide_passing_box();
        clear_passing_box();
    }
}

function raise_card(evt) {
    var card = evt.getTarget();
    var id = card.getAttribute("id");

    // don't do anything if it's not our turn (or if the game isn't active)
    if (!is_my_turn()) { return; }

    var rv;
    if (rv = id.match(/_((?:[\dJQKA]|\d{2})[CDHS])/)) {
        card.setAttribute("y", -0.1); // stretch the clickbox
        var height = card.getAttribute("height");
        card.setAttribute("height", 1.1);

        // now raise the card
        var card_id = "card_" + rv[1];
        card = document.getElementById(card_id);
        card.setAttribute("y", -0.1);
    }
}

function lower_card(evt) {
    var card = evt.getTarget();
    var id = card.getAttribute("id");

    var rv;
    if (rv = id.match(/_((?:[\dJQKA]|\d{2})[CDHS])/)) {
        card.setAttribute("y", 0); // shrink the clickbox
        var height = card.getAttribute("height");
        card.setAttribute("height", 1);

        // now lower the card
        var card_id = "card_" + rv[1];
        card = document.getElementById(card_id);
        card.setAttribute("y", 0);
    }
}

function show_passing_box(msg, button_text) {
    replace_text(document.getElementById("pass_message"), msg);
    replace_text(document.getElementById("pass-button-text"), button_text);
    document.getElementById("passingbox").setAttribute("display", "block");
}

function hide_passing_box() {
    document.getElementById("passingbox").setAttribute("display", "none");
}


function show_passing_symbol(which_dir) {
    // rotate (and show) the passing arrow
    var angles = new Array();
    angles["left"] = 145;
    angles["right"] = -60;
    angles["across"] = 218;

    var symbol = document.getElementById("status_symbol");
    symbol.setAttribute("transform", 
        "translate(16 10) rotate(" + angles[which_dir] + ") scale(1.3)");
    symbol.setAttribute("display", "block");
}

function clear_playfield() {
    clear_hand();
    clear_current_trick();
}

function clear_hand() {
    remove_children(document.getElementById("hand"));
}

// the name says clear, but we're just oing to hide them
function clear_current_trick() {
    for (dir_idx in directions) {
        var card = document.getElementById(directions[dir_idx] + "_trick-card");
        card.setAttribute("display", "none"); // hide the card
        var highlight = document.getElementById(directions[dir_idx] + "_trick-highlight");
        highlight.setAttribute("display", "none"); // hide the highlight
    }
}

function clear_passing_box() {
    remove_children(document.getElementById("pass-card-0"));
    remove_children(document.getElementById("pass-card-1"));
    remove_children(document.getElementById("pass-card-2"));
}

// clears out all of the cards in the "currently being passed" handlet
function clear_passed_cards() {
    for (idx in pass_hand) {
        pass_hand[idx] = null;
    }
}

function clear_last_trick() {
    for (seat_idx in info.gameseats) {
        var seat_id = info.gameseats[seat_idx] + "";

        var id = seat_id + "-last_trick-card";
        var card = document.getElementById(id);
        card.setAttributeNS(xlink_ns, "href",
            deckpath + "#card-back");
        card.setAttribute("font-weight", "normal");

        // unhighlight all of them
        id =  seat_id + "-last_trick-text";
        card = document.getElementById(id);
        card.setAttribute("font-weight", "normal");

        id =  seat_id + "-last_trick-highlight";
        card = document.getElementById(id);
        card.setAttribute("display", "none");
    }
}

function clear_scores() {
    for (idx in info.gameseats) {
        var seat_id = info.gameseats[idx] + "";
        var text = document.getElementById(seat_id + "-score");
        replace_text(text, "0");
    }
}

// this function changes the text of a <text> node, assuming that the
// structure is always the same, with only one textNode child existing
function replace_text(parent, text) {
    parent.textContent = text;
}

function remove_children(parent) {
    var obj, ls;
    ls = parent.childNodes;
    while (ls.length > 0) {
        obj = ls.item(0);
        parent.removeChild(obj);
    }
}

// translates the Games::Cards name for a card into the standard card name for
// the poker deck resource
function translate_card_name(card) {
    var ret = card.match(/([\dJQKA]|\d{2})([CDHS])/); // pull the card apart
    return "card-" + t_nums[ret[1]] + "-" + t_suits[ret[2]];
}

// figure out how wide a card is -- currently just return the aspect ratio,
// see the comments near the definition for card_width.  If those assumptions
// change, I'll have to modify this routine
function get_card_width() {
    // the * 1 is just to force it to return a number rather than a string
    return (metadata.get(deck_ns, "aspect-ratio", 0.71) * 1);
}

// find a west stacking for us to use
function find_west_stacking() {
    var stackings = metadata.getall(deck_ns, "stack-spacing");

    for (idx in stackings) {
        bits = stackings[idx].split(" ");
        if (bits[0] == "west") { return (bits[1] * 1); }
    }

    // fallback in case there were none.
    return 0.30;
}
// vim: set ts=4 sw=4 et si
