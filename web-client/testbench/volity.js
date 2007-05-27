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

// load_localized_files: Given a two-letter language code, figure out the
// (locally-based) URLs of local files, load them, and handle them.
load_localized_files = function(language_code) {
    translation_for = {};
    // Get the seat definitions.
    var seat_file = 'locale/' + language_code + '/seattokens.xml';
        try {
        importXML(seat_file,
                  'process_seat_xml');
        }
        catch (error) {
            alert("Failed to import the seat definition file " + seat_file + ": " + error);
        }
    
    // Get the token definitions.
    var tokens_file = 'locale/' + language_code + '/gametokens.xml';
    try {
        importXML(tokens_file,
                  'process_token_xml');
    }
    catch (error) {
        alert("Failed to import the token translation file " + tokens_file + ": " + error);
    }

    var volity_tokens_file = 'volity_locale/' + language_code + '/volitytokens.xml';
    try {
        importXML(volity_tokens_file,
                  'process_volity_token_xml');
    }
    catch (error) {
        alert("Failed to import the token translation file " + volity_tokens_file + ": " + error);
    }

    
}

// XXX process_seat_xml and similar functions ought to be
//     merged and refactored.

// process_seat_xml: Handle a seattokens.xml file.
process_seat_xml = function(xml_doc) {
    var root_element = xml_doc.firstChild;
    var token_nodes = root_element.getElementsByTagName('token');
    token_nodes = $A(token_nodes);
    var seat_ids = new Array;
    token_nodes.each(function (node) {
        var key_list = node.getElementsByTagName('key');
        var seat_id = key_list.item(0).firstChild.nodeValue;
        seat_ids.push(seat_id);
        var value_list = node.getElementsByTagName('value');
        var translation = value_list.item(0).firstChild.nodeValue;
        set_translation('seat.' + seat_id, translation);
    } );

    // Build <option> elements under the seat selector, if it
    // doesn't have any (beyond the one default seat).
    if ($('seat-select').getElementsByTagName('option').length <= 1) {
        seat_ids.each(function(seat_id) { create_seat_option(seat_id) });
    }
}

set_translation = function(token, translation) {
    if ($('ui-frame')) {
        //        alert("Setting a translation in the child.");
        $('ui-frame').contentWindow.translation_for[token] = translation;
    }
    else {
        translation_for[token] = translation;
    }
}

// process_token_xml: Handle a gametokens.xml file.
process_token_xml = function(xml_doc) {
    var root_element = xml_doc.firstChild;
    var token_nodes = root_element.getElementsByTagName('token');
    token_nodes = $A(token_nodes);
    token_nodes.each(function (node) {
        var key_list = node.getElementsByTagName('key');
        var token = key_list.item(0).firstChild.nodeValue;
        var value_list = node.getElementsByTagName('value');
        var translation = value_list.item(0).firstChild.nodeValue;
        set_translation('game.' + token, translation);
    } );
}

// process_volity_token_xml: Handle a volitytokens.xml file.
process_volity_token_xml = function(xml_doc) {
    var root_element = xml_doc.firstChild;
    var token_nodes = root_element.getElementsByTagName('token');
    token_nodes = $A(token_nodes);
    token_nodes.each(function (node) {
        var key_list = node.getElementsByTagName('key');
        var token = key_list.item(0).firstChild.nodeValue;
        var value_list = node.getElementsByTagName('value');
        var translation = value_list.item(0).firstChild.nodeValue;
        set_translation('volity.' + token, translation);
    } );
}

// translate: Return the translated version of a token.
function translate (token) {
    var literal_regex = /^literal\./;
    if (token.match(literal_regex)) {
        // It's a token in the 'literal' namespace.
        // Do no translation other than snipping off the namespace.
        token = token.substr(8, token.length);
        return token;
    }
    else {
        // It's a token in some other namespace.
        // Return its value in the translation hash.
        // Throw an exception if there is no such value.
        var translation = translation_for[token];
        if (translation) {
            return translation;
        }
        else {
            throw("No translation for token: " + token);
        }
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

