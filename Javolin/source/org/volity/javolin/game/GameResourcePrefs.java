package org.volity.javolin.game;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.prefs.Preferences;
import org.volity.client.protocols.volresp.ResourcePrefs;
import org.volity.javolin.ErrorWrapper;

/**
 * Provides access to the game-resource preferences.
 */
public class GameResourcePrefs implements ResourcePrefs
{
    public final static String NODENAME = "GameResourcePrefs";
    // keys are URIs

    UIFileCache mCache;

    /** Constructor. */
    public GameResourcePrefs(UIFileCache cache) {
        mCache = cache;
    }

    /** 
     * Set the resource URL for the given resource URI. Pass null to specify
     * the default as the preference.
     */
    public void setURL(URI uri, URL url) {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        if (url != null)
            prefs.put(uri.toString(), url.toString());
        else
            prefs.remove(uri.toString());
    }

    /** 
     * Get the resource URL for the given resource URI. If the preference is
     * for the default resource, returns null.
     */
    public URL getURL(URI uri) {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        String urlstr = prefs.get(uri.toString(), null);
        if (urlstr == null)
            return null;

        try {
            URL url = new URL(urlstr);
            return url;
        }
        catch (Exception ex) {
            return null;
        }
    }

    /**
     * Get the main file of the preferred resource for the given resource URI.
     * (This downloads the resource if necessary, and returns a reference to
     * the local cache.) If the preference is for the default resource, returns
     * null.
     */
    public File getResource(URI uri) {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        String urlstr = prefs.get(uri.toString(), null);
        if (urlstr == null)
            return null;

        try {
            URL url = new URL(urlstr);
            File uiDir = mCache.getUIDir(url);
            uiDir = UIFileCache.locateTopDirectory(uiDir);

            // If there's exactly one file, that's it. Otherwise, look for
            // main.svg or MAIN.SVG.
            File uiMainFile = UIFileCache.locateMainFile(uiDir);
            return uiMainFile;
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);
            return null;            
        }
    }

}
