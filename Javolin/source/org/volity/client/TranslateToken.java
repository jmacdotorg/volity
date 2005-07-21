package org.volity.client;

import java.util.List;
import java.util.Hashtable;
import java.util.Map;
import java.util.Arrays;
import org.volity.client.TokenFailure;

/**
 * A function which takes a failure token list, and translates it into a
 * natural-language string.
 *
 * This is a stub implementation. It only does one language (English).
 * And I haven't hooked it into the UI file (which has the translations
 * for game.* and seat.* tokens).
 *
 * For the moment, this is a non-instantiable class with one static method.
 * A future version will no doubt have instances (one per table, I expect)
 * and will have a way to set the user language.
 *
 * @author Andrew Plotkin (erkyrath@eblong.com)
 */
public class TranslateToken {
    public static String translate(TokenFailure ex) {
	return translate(ex.getTokens());
    }

    public static String translate(String tokens[]) {
	return translate(Arrays.asList(tokens));
    }

    public static String translate(List tokens) {
	if (tokens.isEmpty())
	    return "";

	String token = (String)tokens.get(0);
	String ns;

	int pos = token.indexOf('.');
	if (pos < 0) {
	    ns = "none";
	}
	else {
	    ns = token.substring(0, pos);
	    token = token.substring(pos+1);
	}

	String trans = translate(token, ns);

	if (ns.equals("literal")) {
	    /* Special case: don't replace interpolation points in a literal
	       token. */
	    return trans;
	}

	if (trans.indexOf('\\') < 0) {
	    /* No interpolation points. */
	    return trans;
	}

	StringBuffer buf = new StringBuffer(trans);
	int ix = 0;

	while (true) {
	    pos = buf.indexOf("\\", ix);
	    if (pos < 0) {
		break;
	    }
	    if (pos+1 >= buf.length()) {
		break;
	    }
	    char ch = buf.charAt(pos+1);
	    if (ch == '\\') {
		ix = pos+2;
		continue;
	    }
	    if (ch < '1' || ch > '9') {
		ix = pos+2;
		continue;
	    }

	    int interptoken = ch - '0';
	    String substr;
	    if (interptoken >= tokens.size()) {
		substr = "?\\" + ch + "?";
	    }
	    else {
		String subtok = (String)tokens.get(interptoken);
		substr = translate(subtok, ns);
	    }
	    buf.replace(pos, pos+2, substr);
	    ix = pos + substr.length();
	}

	return buf.toString();
    }

    public static String translate(String token, String defaultNamespace) {
	String ns;
	int pos = token.indexOf('.');
	if (pos < 0) {
	    ns = defaultNamespace;
	}
	else {
	    ns = token.substring(0, pos);
	    token = token.substring(pos+1);
	}
	if (ns.equals("literal")) {
	    return token;
	}
	if (ns.equals("volity")) {
	    if (currentVolityTable.containsKey(token)) {
		return (String)currentVolityTable.get(token);
	    }
	    /* else, fall through */
	}
	if (ns.equals("game")) {
	    /* XXX look in UI file translation table */
	}
	if (ns.equals("seat")) {
	    /* XXX look in UI file translation table */
	    /* Seat not found... */
	    return translate(new String[] {
		"volity.invalid_seat", "literal."+token });
	}
	return translate(new String[] {
	    "volity.unknown_token", "literal."+ns+"."+token });
    }

    private static boolean setCurrentLanguage(String lang) {
	if (volityTableCache.containsKey(lang)) {
	    currentVolityTable = (Map)volityTableCache.get(lang);
	    return true;
	}

	String msglist[];

	if (lang.equals("en")) {
	    msglist = VolityMessageList_EN;
	}
	else {
	    return false;
	}

	Map tab = new Hashtable();

	/* It would be super-bad for a translation map to be missing
	 * "unknown_token" -- we'd get into an infinite loop. So let's
	 * put in a default value, just in case the MessageList fails
	 * to define it. */
	tab.put("unknown_token", "??? \\1");

	for (int ix=0; ix<msglist.length; ix+=2) {
	    tab.put(msglist[ix], msglist[ix+1]);
	}

	volityTableCache.put(lang, tab);
	currentVolityTable = tab;
	return true;
    }

    private static Map volityTableCache = new Hashtable();
    private static Map currentVolityTable;

    private static String VolityMessageList_EN[] = {
	/* "ok" never passes through the translation service, so we
	   have no entry for it */
	"unknown_token", "(Untranslatable message: \\1)",
	"invalid_seat", "(Invalid seat \"\\1\")",
	"offline", "The service has been disabled by the administrator.",
	"no_seat", "No seats are available.",
	"seat_not_available", "That seat is not available.",
	"bad_config", "The game cannot begin, because the current table configuration is not allowed.",
	"empty_seats", "The game cannot because, because not all required seats have players.",
	"referee_not_ready", "The referee is not yet ready to service requests.",
	"game_in_progress", "The game is in progress.",
	"game_not_in_progress", "The game has not yet started.",
	"jid_not_present", "The player \\1 has not joined the table.",
	"not_seated", "You are not seated.",
	"are_seated", "You are seated."
    };

    static {
	/* This stanza must be last -- after the volityTableCache and
	   VolityMessageList_* definitions. Otherwise, you'll hit
	   null value exceptions. */

	/* Set up English as the current language. */
	setCurrentLanguage("en");
    }

}
