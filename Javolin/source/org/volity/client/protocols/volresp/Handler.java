package org.volity.client.protocols.volresp;

import java.io.*;
import java.net.*;
import org.volity.client.protocols.StubConnection;

/**
 * A URLStreamHandler for the "volresp:" URL protocol. 
 *
 * The structure of this URL is:
 *     volresp://resource/DEFAULTRESOURCE?URI#SHORTNAME
 *
 * (Other variations are reserved for possible future extensions of the
 * localization system.)
 * 
 * DEFAULTRESOURCE is the pathname of the default resource file to use, if the
 * user does not have another preference. It should be given relative to the UI
 * bundle's root.
 *
 * The URI identifies the resource type.
 *
 * SHORTNAME is the name of an entity to be defined.
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
        if (!authority.equals("resource")) {
            throw new MalformedURLException("The volresp protocol requires that the URL begin with \"volresp://resource/...\".");
        }

        String shortname = url.getRef();
        if (shortname == null || shortname.equals("")) {
            throw new MalformedURLException("The volresp protocol requires that the URL end with a \"#ref\".");
        }

        String path = url.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String query = url.getQuery();
        if (query == null || query.equals("")) {
            throw new MalformedURLException("The volresp protocol requires that the URL contain \"?URI\".");
        }

        String value = path;
        if (resourcePrefs != null) {
            // Try to get preference matching URI
            URI uri = URI.create(query);
            File fl = resourcePrefs.getResource(uri);
            if (fl != null) {
                value = fl.getAbsolutePath();
            }            
        }

        String text = "<!ENTITY " + shortname + " \"" + value + "\">\n";

        return new StubConnection(url, text);
    }

    protected static ResourcePrefs resourcePrefs = null;

    /**
     * Set the preference object which knows all the user's preferences for
     * resources. The application should call this when it starts up. If it
     * does not, the Handler will always choose the default resource.
     */
    public static void setResourcePrefs(ResourcePrefs prefs) {
        resourcePrefs = prefs;
    }
}

