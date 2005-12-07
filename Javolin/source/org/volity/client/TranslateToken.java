package org.volity.client;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import org.volity.client.TokenFailure;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A utility class which takes a failure token list, and translates it
 * into a natural-language string.
 *
 * When loaded, this class is set to English. Call setLanguage() to
 * change the output language. (This is a static, process-wide
 * setting.)
 *
 * A TranslateToken instance reads token translation tables as they
 * are needed, and caches them internally in a hash table. However,
 * there is no sharing between instances. (This is a possible future
 * improvement. But we'd have to be careful about tables that get
 * updated between games -- say, if the client's UI file cache got
 * refreshed.)
 *
 * (Another possible feature: if a token isn't found in the current
 * language, try English. This is biased but pragmatic: there's a
 * chance English will work, and a chance the player will understand
 * it.) (More subtle: for "volity" tokens, try English as the
 * fallback, since the volity namespace was invented by English-
 * speakers and the English table is certainly complete. For "game"
 * tokens, try as the fallback whatever the native language is of the
 * author of the UI file. There would have to be a way for the UI file
 * to specify that...)
 *
 * @author Andrew Plotkin (erkyrath@eblong.com)
 */
public class TranslateToken {
    /**
     * Construct a translator object. The argument is a directory
     * containing subdirectories "en", "fr", etc -- one per two-letter
     * language code the translator wishes to support. A subdirectory
     * may contain files gametokens.xml and/or seattokens.xml, in
     * order to translate tokens in the "game", "seat", and "ui"
     * namespaces.
     * 
     * @param localeDir a File referring to the locale directory
     */
    public TranslateToken(File localeDir) {
        this.localeDir = localeDir;
    }

    /**
     * Set the language used for all TranslateToken output.
     *
     * @param lang the two-letter language code
     */
    public static void setLanguage(String lang) {
        currentLanguage = lang.toLowerCase();
    }

    /**
     * Return the language being used for TranslateToken output.
     *
     * @return the two-letter language code
     */
    public static String getLanguage() {
        return currentLanguage;
    }

    /**
     * Translate tokens.
     *
     * @param tokens a list of tokens
     * @return a natural-language string
     */
    public String translate(String tokens[]) {
        return translate(Arrays.asList(tokens));
    }

    /**
     * Translate one token.
     *
     * @param token a token
     * @return a natural-language string
     */
    public String translate(String token) {
        String tokens[] = new String[1];
        tokens[0] = token;
        return translate(Arrays.asList(tokens));
    }

    /**
     * Translate a pair of tokens.
     *
     * @param token1 a token
     * @param token2 a token
     * @return a natural-language string
     */
    public String translate(String token1, String token2) {
        String tokens[] = new String[] { token1, token2 };
        return translate(Arrays.asList(tokens));
    }

    /**
     * Translate tokens received in an RPC failure message.
     *
     * @param ex an exception containing a list of tokens
     * @return a natural-language string
     */
    public String translate(TokenFailure ex) {
        return translate(ex.getTokens());
    }

    /**
     * Translate tokens.
     *
     * @param tokens a list of tokens.
     * @return a natural-language string
     */
    public String translate(List tokens) {
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

        String trans = translateOne(token, ns);

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
                String subns;
                int subpos = subtok.indexOf('.');
                if (subpos < 0) {
                    subns = ns;
                }
                else {
                    subns = subtok.substring(0, subpos);
                    subtok = subtok.substring(subpos+1);
                }
                substr = translateOne(subtok, subns);
            }

            buf.replace(pos, pos+2, substr);
            ix = pos + substr.length();
        }

        return buf.toString();
    }

    /**
     * Translate one token (whose namespace has already been parsed).
     *
     * @param token the token (without namespace prefix)
     * @param ns the token namespace
     * @return a natural-language string
     */
    protected String translateOne(String token, String ns) {
        if (ns.equals("literal")) {
            return token;
        }
        if (ns.equals("volity")) {
            Map tab = getCurrentTableVolity();
            if (tab.containsKey(token)) {
                return (String)tab.get(token);
            }
            /* else, fall through */
        }
        if (ns.equals("game") && localeDir != null) {
            Map tab = getCurrentTableGame();
            if (tab.containsKey(token)) {
                return (String)tab.get(token);
            }
            /* else, fall through */
        }
        if (ns.equals("ui") && localeDir != null) {
            Map tab = getCurrentTableUi();
            if (tab.containsKey(token)) {
                return (String)tab.get(token);
            }
            /* else, fall through */
        }
        if (ns.equals("seat") && localeDir != null) {
            Map tab = getCurrentTableSeat();
            if (tab.containsKey(token)) {
                return (String)tab.get(token);
            }
            /* else, seat not found... */
            return translate("volity.invalid_seat", "literal."+token);
        }
        return translate("volity.unknown_token", "literal."+ns+"."+token);
    }

    /**
     * Translate a seat identifier. The difference between this and
     * translate("seat."+token) is that this method returns null if no
     * translation is available (instead of creating a human-readable "no
     * translation available" string).
     *
     * @param token the ID of a seat
     * @return a natural-language string, or null if no translation is
     *     available.
     */
    public String translateSeatID(String token) {
        if (localeDir != null) {
            Map tab = getCurrentTableSeat();
            if (tab.containsKey(token)) {
                return (String)tab.get(token);
            }
        }
        return null;
    }

    /**
     * Fetch the map for "volity" tokens, in the current language.
     * This is fast if the map has already been built.
     *
     * This method is static, because "volity" tokens are translated
     * the same in all translator instances.
     *
     * @return a Map which maps token names to Strings.
     */
    protected static Map getCurrentTableVolity() {
        if (tableCacheVolity.containsKey(currentLanguage)) {
            return (Map)tableCacheVolity.get(currentLanguage);
        }

        Map tab = new Hashtable();

        String msglist[] = null;

        if (currentLanguage.equals("en")) {
            msglist = VolityMessageList_EN;
        }
        // XXX else other languages
        
        if (msglist != null) {
            /* It would be super-bad for a translation map to be missing
             * "unknown_token" -- we'd get into an infinite loop. So let's
             * put in a default value, just in case the MessageList fails
             * to define it. */
            tab.put("unknown_token", "??? \\1");

            for (int ix=0; ix<msglist.length; ix+=2) {
                tab.put(msglist[ix], msglist[ix+1]);
            }
        }

        tableCacheVolity.put(currentLanguage, tab);
        return tab;
    }

    /**
     * Fetch the map for "game" tokens, in the current language.
     * This is fast if the map has already been built.
     *
     * @return a Map which maps token names to Strings.
     */
    protected Map getCurrentTableGame() {
        if (tableCacheGame.containsKey(currentLanguage)) {
            return (Map)tableCacheGame.get(currentLanguage);
        }

        /* We want to go through this exactly once, even if the XML
         * table is missing or unreadable. Therefore, we create an
         * empty Map, and we'll stick that Map into tableCacheGame
         * even if the importLocaleTable call fails. */
        Map tab = new Hashtable();

        File localeSubdir = new File(localeDir, currentLanguage);
        File localeFile = new File(localeSubdir, "gametokens.xml");
        if (!localeFile.exists()) {
            localeFile = new File(localeSubdir, "GAMETOKENS.XML");
        }
        if (localeFile.exists()) {
            try {
                importLocaleTable(tab, localeFile);
            }
            catch (Exception ex) { 
                /* No obvious way to smuggle exception messages out of
                 * here, so we just drop them. */
                System.err.println("Error importing \"" 
                    + currentLanguage + "\" gametokens: " + ex.toString());
            }
        }

        tableCacheGame.put(currentLanguage, tab);
        return tab;
    }

    /**
     * Fetch the map for "ui" tokens, in the current language.
     * This is fast if the map has already been built.
     *
     * @return a Map which maps token names to Strings.
     */
    protected Map getCurrentTableUi() {
        if (tableCacheUi.containsKey(currentLanguage)) {
            return (Map)tableCacheUi.get(currentLanguage);
        }

        /* We want to go through this exactly once, even if the XML
         * table is missing or unreadable. Therefore, we create an
         * empty Map, and we'll stick that Map into tableCacheUi
         * even if the importLocaleTable call fails. */
        Map tab = new Hashtable();

        File localeSubdir = new File(localeDir, currentLanguage);
        File localeFile = new File(localeSubdir, "uitokens.xml");
        if (!localeFile.exists()) {
            localeFile = new File(localeSubdir, "UITOKENS.XML");
        }
        if (localeFile.exists()) {
            try {
                importLocaleTable(tab, localeFile);
            }
            catch (Exception ex) { 
                /* No obvious way to smuggle exception messages out of
                 * here, so we just drop them. */
                System.err.println("Error importing \"" 
                    + currentLanguage + "\" uitokens: " + ex.toString());
            }
        }

        tableCacheUi.put(currentLanguage, tab);
        return tab;
    }

    /**
     * Fetch the map for "seat" tokens, in the current language.
     * This is fast if the map has already been built.
     *
     * @return a Map which maps token names to Strings.
     */
    protected Map getCurrentTableSeat() {
        if (tableCacheSeat.containsKey(currentLanguage)) {
            return (Map)tableCacheSeat.get(currentLanguage);
        }

        /* We want to go through this exactly once, even if the XML
         * table is missing or unreadable. Therefore, we create an
         * empty Map, and we'll stick that Map into tableCacheSeat
         * even if the importLocaleTable call fails. */
        Map tab = new Hashtable();

        File localeSubdir = new File(localeDir, currentLanguage);
        File localeFile = new File(localeSubdir, "seattokens.xml");
        if (!localeFile.exists()) {
            localeFile = new File(localeSubdir, "SEATTOKENS.XML");
        }
        if (localeFile.exists()) {
            try {
                importLocaleTable(tab, localeFile);
            }
            catch (Exception ex) { 
                /* No obvious way to smuggle exception messages out of
                 * here, so we just drop them. */
                System.err.println("Error importing \"" 
                    + currentLanguage + "\" seattokens: " + ex.toString());
            }
        }

        tableCacheSeat.put(currentLanguage, tab);
        return tab;
    }

    /**
     * Clear the cached tables we loaded from the UI package. This has no value
     * in Javolin, but it's useful in Testbench when we reload changes.
     */
    public void clearCache() {
        tableCacheGame.clear();
        tableCacheUi.clear();
        tableCacheSeat.clear();
    }

    /**
     * Parse an XML token translation table, and store its entries into
     * a provided Map.
     *
     * @param tab the Map to store entries in.
     * @param tokenFile the XML file.
     */
    protected void importLocaleTable(Map tab, File tokenFile) 
        throws XmlPullParserException, IOException
    {
        XmlPullParser xpp = new MXParser();
        xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

        FileReader in = new FileReader(tokenFile);
        xpp.setInput(in);
        int tuple[] = new int[2];
        boolean intoken = false;
        boolean instring = false;
        String key = null;
        String trans = null;
        StringBuffer buf = new StringBuffer();

        int eventType = xpp.getEventType();
        do {
            if (eventType == xpp.START_TAG) {
                if (xpp.getDepth() == 2 && xpp.getName().equals("token")) {
                    intoken = true;
                }
                if (intoken && xpp.getDepth() == 3
                    && xpp.getName().equals("key")) {
                    instring = true;
                }
                if (intoken && xpp.getDepth() == 3 
                    && xpp.getName().equals("value")) {
                    instring = true;
                }
            }
            if (eventType == xpp.TEXT) {
                if (instring) {
                    char ch[] = xpp.getTextCharacters(tuple);
                    int start = tuple[0];
                    int length = tuple[1];
                    buf.append(ch, start, length);
                }
            }
            if (eventType == xpp.END_TAG) {
                if (xpp.getDepth() == 2 && intoken) {
                    if (key != null && trans != null
                        && key.length() > 0) {
                        tab.put(key, trans);
                    }
                    key = null;
                    trans = null;
                    intoken = false;
                }
                if (xpp.getDepth() == 3 && instring) {
                    if (xpp.getName().equals("key")) {
                        key = buf.toString();
                    }
                    if (xpp.getName().equals("value")) {
                        trans = buf.toString();
                    }
                    instring = false;
                    buf.setLength(0);
                }
            }
            eventType = xpp.next();
        } while (eventType != xpp.END_DOCUMENT);

        in.close();
    }

    static protected String currentLanguage = "en";
    private static Map tableCacheVolity = new Hashtable();

    protected File localeDir;
    private Map tableCacheGame = new Hashtable();
    private Map tableCacheUi = new Hashtable();
    private Map tableCacheSeat = new Hashtable();

    /* Source tables for the "volity" namespace. Each of these is just
     * a String array of length 2*N. Ultimately, we'll want one table
     * for every language the client supports. */

    /* But today, we only have English. */
    private static String VolityMessageList_EN[] = {
        /* "ok" never passes through the translation service, so we
           have no entry for it */
        "unknown_token", "(Untranslatable message: \\1)",
        "invalid_seat", "(Invalid seat \"\\1\")",
        "offline", "The service has been disabled by the administrator.",
        "no_seat", "No seats are available.",
        "seat_not_available", "That seat is not available.",
        "bad_config", "The game cannot begin, because the current table configuration is not allowed.",
        "empty_seats", "The game cannot begin, because not all required seats have players.",
        "referee_not_ready", "The referee is not yet ready to service requests.",
        "game_in_progress", "The game is in progress.",
        "game_not_in_progress", "The game has not yet started.",
        "jid_present", "The player \\1 has already joined the table.",
        "jid_not_present", "The player \\1 has not joined the table.",
        "jid_not_ready", "The player \\1 is not yet ready to take part in the game.",
        "not_seated", "You are not seated.",
        "are_seated", "You are seated.",
        "not_your_turn", "It's not your turn.",
        "relay_failed", "Your message could not be delivered to \\1.",
        "no_bots_provided", "This game parlor does not provide bots."
    };
}
