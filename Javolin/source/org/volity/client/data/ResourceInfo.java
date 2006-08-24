package org.volity.client.data;

import java.net.URL;
import java.util.Map;

/**
 * Some information about a game resource document.
 */
public class ResourceInfo {
    String mName;
    URL mLocation;

    /**
     * Construct a resource info object, given its URL and the data structure
     * returned by the bookkeeper about it.
     */
    public ResourceInfo(URL url, Map info) {
        mLocation = url;

        Object val;

        /* Note that empty fields are stored as null, not as the empty
         * string.
         */

        val = info.get("name");
        if (val != null && val instanceof String && ((String)val).length() != 0)
            mName = (String)val;
    }

    /**
     * Get the name of this game UI.
     */
    public String getName() { return mName; }

    /**
     * Get the location from where this game UI can be downloaded.
     */
    public URL getLocation() { return mLocation; }

}
