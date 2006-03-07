package org.volity.client.protocols.vollocp;

import java.io.*;
import java.net.*;
import org.volity.client.protocols.StubConnection;
import org.volity.client.translate.TranslateToken;

/**
 * A URLStreamHandler for the "vollocp:" URL protocol. 
 *
 * The structure of this URL is:
 *     vollocp://locale/ENTITYFILE?LANGUAGES#SHORTNAME
 *
 * (Other variations are reserved for possible future extensions of the
 * localization system.)
 *
 * ENTITYFILE is the name of a file containing translation entities. This file
 * should be findable in each of the UI bundle's locale directories. (That is:
 * locale/en/ENTITYFILE, locale/de/ENTITYFILE, etc.)
 *
 * LANGUAGES is a comma-separated list of the locale directories in the UI
 * bundle. (For example, "en,de".) The first entry in the list is taken to be
 * the default.
 *
 * SHORTNAME is the name of an entity to be defined. (In fact, both %SHORTNAME;
 * and &SHORTNAME; will be defined.)
 *
 * To make this class visible to Java's URL system, add
 * "org.volity.client.protocols" to the "java.protocol.handler.pkgs" 
 * system property.
 *
 * For more information on the URLStreamHandler API, see the tutorial at:
 * http://java.sun.com/developer/onlineTraining/protocolhandlers/
 */
public class Handler 
    extends URLStreamHandler
{
    /**
     * Invoke the given URL. This does not do any network work; the result of a
     * volresp URL is a small block of text, which is generated right here.
     */
    protected URLConnection openConnection(URL url) 
        throws MalformedURLException {

        String authority = url.getHost();
        if (!authority.equals("locale")) {
            throw new MalformedURLException("The vollocp protocol requires that the URL begin with \"vollocp://locale/...\".");
        }

        String shortname = url.getRef();
        if (shortname == null || shortname.equals("")) {
            throw new MalformedURLException("The vollocp protocol requires that the URL end with a \"#ref\".");
        }

        String query = url.getQuery();
        if (query == null || query.equals("")) {
            throw new MalformedURLException("The vollocp protocol requires that the URL contain \"?lang\".");
        }

        String [] langs = query.split(",");
        if (langs.length == 0) {
            throw new MalformedURLException("The vollocp protocol requires that the URL contain \"?lang\".");
        }

        String path = url.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String currentLanguage = TranslateToken.getLanguage();

        String lang = langs[0];
        for (int ix=0; ix<langs.length; ix++) {
            if (langs[ix].equals(currentLanguage)) {
                lang = langs[ix];
                break;
            }
        }

        // <!ENTITY % lang "<!ENTITY &#37; lang_ SYSTEM 'locale/en/message.def'> &#37;lang_;">
        // <!ENTITY lang "en">

        StringBuffer buf = new StringBuffer();

        buf.append("<!ENTITY % " + shortname + " \"" 
            + "<!ENTITY &#37; " + shortname + "__" + " SYSTEM '"
            + authority + "/" + lang + "/" + path + "'> " 
            + "&#37;" + shortname + "__" + ";\">\n");
        buf.append("<!ENTITY " + shortname + " \"" + lang + "\">\n");

        String text = buf.toString();

        return new StubConnection(url, text);
    }
}

