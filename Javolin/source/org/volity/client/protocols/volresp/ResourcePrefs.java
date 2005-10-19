package org.volity.client.protocols.volresp;

import java.io.File;
import java.net.URI;

/**
 * Interface for an object that knows the user's preferences for various game
 * resources. (Card decks, etc.)
 */
public interface ResourcePrefs
{
    /**
     * Get the file containing the user's preferred resource of the type
     * referred to by the URI. If the user has no preference, or wishes to use
     * each game's own resource, return null.
     */
    public File getResource(URI uri);
}
