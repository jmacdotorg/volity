package org.volity.javolin.game;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.volity.client.data.Metadata;
import org.volity.client.data.ResourceInfo;
import org.volity.client.data.VersionNumber;
import org.volity.client.data.VersionSpec;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.JavolinApp;

/**
 * This class handles the entire process of selecting a resource file. That
 * includes downloading the UI into the cache and checking its metadata.
 *
 * The SelectResource object is transient; it contains the state for the
 * selection process, and you throw it away when selection is over.
 */
public class SelectResource
{
    public final static String NODENAME = "Resource";
    public final static String NODENAMELAST = NODENAME+"/LastChosen";
    public final static String NODENAMENAMES = NODENAME+"/ResNames";

    /**
     * The interface for a SelectResource callback. Exactly one of succeed()
     * and fail() will be called.
     *
     * The arguments to succeed() are the resource URL which was chosen, and
     * main file of that resource in the local cache. (SelectResource always
     * loads the resource into the cache, as part of checking it out. So you
     * don't have to do that.)
     */
    public interface Callback {
        public void succeed(URL ui, File localui);
        public void fail();
    }

    URI mResURI;
    VersionSpec mVersionSpec;

    Callback mCallback;

    URL mLastChoice;
    List mResourceInfoList;
    URL mURL;
    File mLocalUI;

    /**
     * Create a SelectResource. This does no work; it just sets up the context
     * for you to use later.
     *
     * @param resuri A resource URI (possibly with version spec fragment).
     */
    public SelectResource(URI resuri)
        throws VersionNumber.VersionFormatException,
               URISyntaxException {
        VersionSpec versionspec = VersionSpec.fromURI(resuri);

        // Strip off the fragment
        resuri = VersionSpec.onlyURI(resuri);

        mResURI = resuri;
        mVersionSpec = versionspec;

        constructor();
    }

    /**
     * Create a SelectResource. This does no work; it just sets up the context
     * for you to use later.
     *
     * @param resuri A resource URI (with no version spec fragment).
     * @param versionspec A version spec (or null, meaning "any").
     */
    public SelectResource(URI resuri, VersionSpec versionspec) {
        if (resuri.getFragment() != null)
            throw new IllegalArgumentException("ruleset URI may not have a fragment");
        mResURI = resuri;
        mVersionSpec = versionspec;

        constructor();
    }

    /** Finish constructing. */
    private void constructor() {
        mLastChoice = null;
        mURL = null;
        mLocalUI = null;
        mResourceInfoList = null;

        if (mVersionSpec == null)
            mVersionSpec = new VersionSpec(); // matches anything
    }

    /**
     * Begin the work of selection. When this is complete, one of the callback
     * methods will be called.
     */
    public void select(Callback callback) {
        mCallback = callback;

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELAST);
        String urlstr = prefs.get(mResURI.toString(), null);
        if (urlstr != null) {
            try {
                mLastChoice = new URL(urlstr);
            }
            catch (MalformedURLException ex) {
                // Don't know why this would happen
                new ErrorWrapper(ex);
            }
        }

        // Queue up the first function.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    contFromTop();
                }
            });
    }

    /**
     * Begin the selection cycle. (We can get back here if an initial selection
     * fails.)
     */
    private void contFromTop() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (!JavolinApp.getSoleJavolinApp().isConnected()) {
            // No way this is good.
            callbackFail();
            JOptionPane.showMessageDialog(null, 
                "You are not connected.",
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        /* ### we should query the bookkeeper here, but those methods don't
         * exist yet. */

        mResourceInfoList = new ArrayList();
        contPopDialog();
    }

    private void contPopDialog() {
        String lastname = null;

        if (mLastChoice != null) {
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMENAMES);
            lastname = prefs.get(mLastChoice.toString(), "Game interface");
        }

        //###
        ChooseUIDialog box = new ChooseUIDialog(mResourceInfoList,
            mLastChoice, lastname);
        box.show();

        URL url = box.getResult();
        if (url == null) {
            // cancelled.
            callbackFail();
            return;
        }

        //### if default, set null

        mURL = url;
        contCheckChoice();
    }

    private void contCheckChoice() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        assert (mURL != null);

        mLocalUI = null;

        UIFileCache cache = JavolinApp.getSoleJavolinApp().getUIFileCache();
        try {
            File dir = cache.getUIDir(mURL);
            mLocalUI = dir;
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);

            JOptionPane.showMessageDialog(null, 
                "Cannot download resource:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
            contCheckBail();
            return;
        }

        File uiMainFile = null;

        try {
            File uiDir = UIFileCache.locateTopDirectory(mLocalUI);
            uiMainFile = UIFileCache.locateMainFile(uiDir);
        }
        catch (IOException ex) {
            new ErrorWrapper(ex);

            JOptionPane.showMessageDialog(null, 
                "Cannot read resource:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
            contCheckBail();
            return;
        }

        Metadata metadata = null;

        try {
            metadata = Metadata.parseSVGMetadata(uiMainFile);
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);

            JOptionPane.showMessageDialog(null, 
                "Cannot parse resource metadata:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
            contCheckBail();
            return;
        }

        // Look at the metadata, see if it matches
        List rulesets = metadata.getAll(Metadata.VOLITY_PROVIDES_RESOURCE);
        if (rulesets.size() == 0) {
            // Old UI, no metadata. Accept it at face value.
        }
        else {
            boolean match = false;

            // Look for a volity:provides-resource entry which matches.
            for (int ix=0; ix<rulesets.size(); ix++) {
                String uristr = (String)rulesets.get(ix);
                try {
                    URI uri = VersionNumber.onlyURI(uristr);
                    if (mResURI.equals(uri)) {
                        VersionNumber versionnum = VersionNumber.fromURI(uristr);
                        /* If there was no number fragment, version will be a
                         * "1.0". */
                        if (mVersionSpec.matches(versionnum)) {
                            match = true;
                        }
                    }
                }
                catch (Exception ex) {
                    // No good match on this line
                }
            }

            if (!match) {
                JOptionPane.showMessageDialog(null, 
                    "The resource you have selected does not match this game's requirements.",
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
                contCheckBail();
                return;
            }
        }

        // Store the game name into the name mapping store, for future display
        String name = null;

        if (mResourceInfoList != null) {
            for (int ix=0; ix<mResourceInfoList.size(); ix++) {
                ResourceInfo info = (ResourceInfo)mResourceInfoList.get(ix);
                URL url = info.getLocation();
                if (mURL.equals(url)) {
                    name = info.getName();
                    break;
                }
            }        
        }
        if (name == null) {
            name = metadata.get(Metadata.DC_TITLE);            
        }
        if (name != null) {
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMENAMES);
            prefs.put(mURL.toString(), name);
        }

        callbackSucceed();
    }

    private void contCheckBail() {
        mURL = null;
        mLocalUI = null;

        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    contFromTop();
                }
            });
    }

    private void callbackFail() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (mCallback != null)
            mCallback.fail();
    }

    private void callbackSucceed() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        assert(mURL != null && mLocalUI != null);

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELAST);
        prefs.put(mResURI.toString(), mURL.toString());

        if (mCallback != null)
            mCallback.succeed(mURL, mLocalUI);
    }

}
