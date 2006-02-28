package org.volity.client.protocols.volresp;

import java.io.File;
import java.net.URI;
import java.net.URL;

/**
 * Interface for an object that knows the user's preferences for various game
 * resources. (Card decks, etc.)
 */
public interface ResourcePrefs
{
    /**
     * Get the URL containing the user's preferred resource of the type
     * referred to by the URI. If the user has no preference, or wishes to use
     * each game's own resource, return null.
     */
    public URL getURL(URI uri);

    /**
     * Get the file containing the user's preferred resource of the type
     * referred to by the URI. If the user has no preference, or wishes to use
     * each game's own resource, return null.
     *
     * Unlike getURL, this should return a reference to a local file.
     * Note that, in some error cases, getResource() may return null even when
     * getURL() does not.
     */
    public File getResource(URI uri);

}
