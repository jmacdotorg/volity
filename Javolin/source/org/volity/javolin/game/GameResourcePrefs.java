package org.volity.javolin.game;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.prefs.Preferences;
import org.volity.client.protocols.volresp.ResourcePrefs;
import org.volity.javolin.ErrorWrapper;

public class GameResourcePrefs implements ResourcePrefs
{
    public final static String NODENAME = "GameResourcePrefs";
    // keys are URIs

    UIFileCache mCache;

    public GameResourcePrefs(UIFileCache cache) {
        mCache = cache;
    }

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
            File uiMainFile;
            File[] entries = uiDir.listFiles();

            if (entries.length == 1 && !entries[0].isDirectory()) {
                uiMainFile = entries[0];
            }
            else {
                uiMainFile = UIFileCache.findFileCaseless(uiDir, "main.svg");
                if (uiMainFile == null)
                    throw new IOException("unable to locate UI file in cache");
            }

            return uiMainFile;
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);
            return null;            
        }
    }

}
