// Volity library for TESTBENCH.
// It will probably be refactored a lot once it's time to make a library
// for the HTML-based Volity client, since this and that will have
// so much in common.

/***********************************
 * VOLITY ECMASCRIPT API FUNCTIONS *
 ***********************************/

// The following functions implement the ECMAScript API set down by
// the Volity protocol. See:
// http://www.volity.org/wiki/index.cgi?ECMAScript_API

rpc = function() {
    var method_arguments = $A(arguments);
    var method_name = method_arguments.shift();
    var string_to_report = "Send RPC: " + method_name + "(";
    var argument_string = method_arguments.join(', ');
    string_to_report += argument_string + ")";
    report(string_to_report);
}

seatmark = function(arg) {
    // arg might be a string, and it might be a hash.
    var seat_report_hash = new Hash;
    if (arg.sub) {
        // Looks like a string.
        seat_report_hash[arg] = 'turn';
    }
    else {
        // Not a string. Treat it as a hash.
        seat_report_hash = $H(arg);
    }
    //    alert(seat_report_hash.keys);
    seat_report_hash.keys().each(function(key) {
        //        alert("I see a key " + key);
        report(key + " has received the " + seat_report_hash[key] + " flag.");
    } );
}

literalmessage = function(string) {
    report(string);
}

localize = function(tokens) {
    // The first token may contain interpolation points, so lets' treat
    // it like a template, and the rest as arguments.
    var template_token = tokens.shift();
    var translated_template = translate(template_token);
    
    // Count the total number of interpolation points.
    var interpolation_count;
    var interpolation_regex = new RegExp("\\\\\\\d+", "g");
    var interpolation_count = 0;
    //    alert("Looking for " + interpolation_regex.toString() + " in " + translated_template);

    while (interpolation_regex.test(translated_template)) {
        interpolation_count++;
    }
    if (interpolation_count > tokens.length) {
        throw("Too few supporting tokens provided for " + template_token + ". (Expected " + interpolation_count + ", got " + tokens.length + ".)");
    }

    // Build the translated string, and return it.
    var current_interpolation = 1;
    tokens.each ( function (token) {
        var translated_token = translate(token);
        var regex = new RegExp('\\\\' + current_interpolation++);
        translated_template
            = translated_template.replace(regex, translated_token);
    });
    
    return translated_template;               
}

message = function() {
    var tokens = $A(arguments);

    //    var template_token = tokens.shift();
    //    alert ('templdate: ' + template_token);

    //    tokens.each(function(poo) { alert("farg " + valueOf(poo)) });
    var translated_string = localize(tokens);
    report(translated_string);
}

audio = function() {
    report('***Sorry, audio not implememented in HTML Testbench yet!');
}

/*******************
 * OTHER FUNCTIONS *
 *******************/

import_xml = function(url, handler) {
	var xml_request = new Ajax.Request(
					   url,
	    {
		method: 'get',
		onComplete: handler
	    });
}

// load_localized_files: Given a two-letter language code, figure out the
// (locally-based) URLs of local files, load them, and handle them.
load_localized_files = function(language_code) {
    translation_for = {};
    // Get the seat definitions.
    var seat_file = 'locale/' + language_code + '/seattokens.xml';
        try {
        import_xml(seat_file,
		   process_seat_xml);
        }
        catch (error) {
            alert("Failed to import the seat definition file " + seat_file + ": " + error);
        }
    
    // Get the token definitions.
    var tokens_file = 'locale/' + language_code + '/gametokens.xml';
    try {
        import_xml(tokens_file,
		   process_gametoken_xml);
    }
    catch (error) {
        alert("Failed to import the token translation file " + tokens_file + ": " + error);
    }

    var volity_tokens_file = 'volity_locale/' + language_code + '/volitytokens.xml';
    try {
        import_xml(volity_tokens_file,
		   process_volity_token_xml);
    }
    catch (error) {
        alert("Failed to import the token translation file " + volity_tokens_file + ": " + error);
    }

    
}

// XXX process_seat_xml and similar functions ought to be
//     merged and refactored.

// process_seat_xml: Handle a seattokens.xml file.
process_seat_xml = function(xml_request) {
    process_token_xml(xml_request, 'seat');

    // Go through the list of seats as they're now defined, building
    // the seat_ids array and also the seat-selection menu.
    // First, blow away existing contents of these things.
    seat_ids = new Array;
    var options = $A($('seat-select').getElementsByTagName('option'));
    options.each( function(option) { 
	if (option.value) {
	    $('seat-select').removeChild(option);
	}
    } );

    // Now rebuild them using the freshly defined seat translation table.
    var seat_translations = get_translation('seat', null);
    seat_translations.keys().each( function(key) {
	seat_ids.push(key);
        create_seat_option(key);
    } );
}

set_translation = function(prefix, token, translation) {
    var revelant_window;

    if ($('ui-frame')) {
        //        alert("Setting a translation in the child.");
	relevant_window = $('ui-frame').contentWindow;
    }
    else {
	relevant_window = window;
    }

    if (!relevant_window.translation_for[prefix]) {
	relevant_window.translation_for[prefix] = {};
    }
    relevant_window.translation_for[prefix][token] = translation;
    //    alert(translate(prefix + '.' + token));
}

get_translation = function (prefix, token) {
    var revelant_window;

    if ($('ui-frame')) {
        //        alert("Setting a translation in the child.");
	relevant_window = $('ui-frame').contentWindow;
    }
    else {
	relevant_window = window;
    }

    if (token) {
	var translation;
	if (relevant_window.translation_for[prefix]) {
	    translation = relevant_window.translation_for[prefix][token];
	}
	
	return translation;
    }
    else {
	// Only the prefix was provided, so return the whole hash under it.
	return $H(relevant_window.translation_for[prefix]);
    }
}

// process_gametoken_xml: Handle a gametokens.xml file.
process_gametoken_xml = function(xml_doc) {
    process_token_xml(xml_doc, 'game');
}

process_token_xml = function(xml_request, prefix) {
    // DOMParser() is defined by the sarissa.js library.
    // We use it here for maximum cross-browser happiness.
    var xml_doc = new DOMParser().parseFromString(xml_request.responseText,
					      'text/xml'
					      );
    var root_element = xml_doc.documentElement;

    var token_nodes = root_element.getElementsByTagName('token');
    token_nodes = $A(token_nodes);
    token_nodes.each(function (node) {
        var key_list = node.getElementsByTagName('key');
        var token = key_list.item(0).firstChild.nodeValue;
        var value_list = node.getElementsByTagName('value');
        var translation = value_list.item(0).firstChild.nodeValue;
	//        set_translation(prefix + '.' + token, translation);
	set_translation(prefix, token, translation);
    } );
}

// process_volity_token_xml: Handle a volitytokens.xml file.
process_volity_token_xml = function(xml_doc) {
    process_token_xml(xml_doc, 'volity');
}

// translate: Return the translated version of a token.
function translate (full_token) {
    // Work out the prefix and tokeny parts of the full token.
    var token_regex = /^(.*?)\.(.*)$/;
    var match_info;
    if (match_info = full_token.match(token_regex)) {
	var prefix = match_info[1];
	var token = match_info[2];
	if (prefix == 'literal') {
	    // It's a token in the 'literal' namespace.
	    // Do no translation other than snipping off the namespace.
	    token = token.substr(8, token.length);
	    return token;
	}
	else {
	    // It's a token in some other namespace.
	    // Return its value in the translation hash.
	    // Throw an exception if there is no such value.
	    var translation = get_translation(prefix, token);
	    if (translation) {
		return translation;
	    }
	    else {
		throw("No translation for token: " + full_token);
	    }
	}
    }
    else {
	throw("Badly formatted token: " + full_token);
    }
}

// Report: Write a string to the testbench output console.
report = function(string_to_report) {
    var console = window.parent.document.getElementById('console-textarea');
    var console_text = console.value;
    //    alert("Bah." + console_text_node + ' bah ' + fuck);
    console_text += string_to_report + "\n";
    console.value = console_text;

    // Scroll the textarea down to the bottom.
    console.scrollTop = console.scrollHeight;
}

initialize_volity_globals = function() {
    if (!info) {
        info = new Object;
    }
    if (!volity) {
        volity = new Object;
    }
    if (!game) {
        game = new Object;
    }
    if (!translation_for) {
        translation_for = {};
    }
}

var info;
var volity;
var game;
var translation_for;

initialize_volity_globals();

