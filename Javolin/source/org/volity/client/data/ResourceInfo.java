package org.volity.client.data;

import java.net.URL;

/**
 * Some information about a game resource document.
 */
// ### Not yet implemented! Because there's currently no way to retrieve this
// stuff from the bookkeeper.
public class ResourceInfo {
    String mName;
    URL mLocation;

    /**
     * Get the name of this game UI.
     */
    public String getName() { return mName; }

    /**
     * Get the location from where this game UI can be downloaded.
     */
    public URL getLocation() { return mLocation; }

}
