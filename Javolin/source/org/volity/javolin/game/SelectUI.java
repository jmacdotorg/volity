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
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.XMPPError;
import org.volity.client.Bookkeeper;
import org.volity.client.data.GameUIInfo;
import org.volity.client.data.Metadata;
import org.volity.client.data.VersionNumber;
import org.volity.client.data.VersionSpec;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.PrefsDialog;

/**
 * This class handles the entire process of selecting a UI (both automatic and
 * manual selections). That includes downloading the UI into the cache and
 * checking its metadata. This process is used from MakeTableWindow, and also
 * from inside a TableWindow (if the player wants to reselect a UI).
 *
 * The SelectUI object is transient; it contains the state for the selection
 * process, and you throw it away when selection is over.
 *
 * The control flow is complex, which is a polite way of saying "noodly". I
 * could make it cleaner by putting the entire control flow into a new
 * thread... but that gripes me. So it's all callbacks.
 *
 * It goes like this:
 *
 * (top) Starting out.
 * - If there's a URL in the preferences, and the user wants to automate
 *   familiar games, pick it. Go to (check).
 * - Query the bookkeeper for compatible URLs. 
 * - If there are none, go to (empty).
 * - If there's exactly one, and the user wants to automate sole picks, pick
 *   it. Go to (check).
 * - If the user wants to automate always, pick the top of the list. Go to
 *   (check).
 * (multi) We have several options from the bookkeeper.
 * - Pop dialog box offering a list, file selection, or typed-in URL.
 * - Go to (check).
 *
 * (empty) We're not using the last-known URL, but the bookkeeper has supplied
 *   nothing.
 * - Pop dialog box offering a file selection or a typed-in URL.
 * - Go to (check).
 *
 * (check) We have a URL, but we don't know how valid it is. (On any errors, 
 *   go to (top) with ForceInteraction set to true.)
 * - Fetch it into cache.
 * - Check its metadata.
 * - Success!
 */
public class SelectUI
{
    public final static String NODENAME = "UI";
    public final static String NODENAMELAST = NODENAME+"/LastChosen";
    public final static String NODENAMENAMES = NODENAME+"/UINames";

    /**
     * The interface for a SelectUI callback. Exactly one of succeed() and
     * fail() will be called.
     *
     * The arguments to succeed() are the UI URL which was chosen, and main
     * file of that UI in the local cache. (SelectUI always loads the UI into
     * the cache, as part of checking it out. So you don't have to do that.)
     */
    public interface Callback {
        public void succeed(URL ui, File localui);
        public void fail();
    }

    URI mRuleset;
    VersionNumber mVersionNumber;
    boolean mForceInteractive;

    Callback mCallback;

    URL mLastChoice;
    List mGameUIInfoList;
    URL mURL;
    File mLocalUI;

    /**
     * Create a SelectUI. This does no work; it just sets up the context for
     * you to use later.
     *
     * @param ruleset A ruleset URI (possibly with version number fragment).
     * @param forcechoice Put up a selection dialog, regardless of user prefs?
     */
    public SelectUI(URI ruleset, boolean forcechoice)
        throws VersionNumber.VersionFormatException,
               URISyntaxException {
        VersionNumber versionnum = VersionNumber.fromURI(ruleset);

        // Strip off the fragment
        ruleset = VersionNumber.onlyURI(ruleset);

        mRuleset = ruleset;
        mVersionNumber = versionnum;

        constructor(forcechoice);
    }

    /**
     * Create a SelectUI. This does no work; it just sets up the context for
     * you to use later.
     *
     * @param ruleset A ruleset URI (with no version number fragment).
     * @param versionnum A version number (or null, meaning "1.0").
     * @param forcechoice Put up a selection dialog, regardless of user prefs?
     */
    public SelectUI(URI ruleset, VersionNumber versionnum,
        boolean forcechoice) {
        if (ruleset.getFragment() != null)
            throw new IllegalArgumentException("ruleset URI may not have a fragment");
        mRuleset = ruleset;
        mVersionNumber = versionnum;

        constructor(forcechoice);
    }

    /** Finish constructing. */
    private void constructor(boolean forcechoice) {
        mForceInteractive = forcechoice;
        mLastChoice = null;
        mURL = null;
        mLocalUI = null;
        mGameUIInfoList = null;

        if (mVersionNumber == null)
            mVersionNumber = new VersionNumber(); // 1.0
    }

    /**
     * Begin the work of selection. When this is complete, one of the callback
     * methods will be called.
     */
    public void select(Callback callback) {
        mCallback = callback;

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELAST);
        String urlstr = prefs.get(mRuleset.toString(), null);
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

        boolean automateFamiliar = (!mForceInteractive)
            && (PrefsDialog.getGameSelectUIAlways()
                || PrefsDialog.getGameSelectUIFamiliar());

        if (automateFamiliar && mLastChoice != null) {
            // auto-choose the familiar choice.

            mURL = mLastChoice;
            contCheckChoice();
            return;
        }

        Bookkeeper keeper = JavolinApp.getSoleJavolinApp().getBookkeeper();
        if (keeper == null) {
            // Emergency wipeout -- probably we're disconnected
            callbackFail();
            return;
        }

        keeper.getGameUIs(new Bookkeeper.Callback() {
                public void run(Object result, XMPPException ex, Object rock) {
                    contGotGameUIs(result, ex);
                }
            }, mRuleset, null);
    }

    private void contGotGameUIs(Object result, XMPPException ex) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (result == null) {
            assert (ex != null);

            new ErrorWrapper(ex);
            callbackFail();

            String msg = "The bookkeeper could not be found.";

            // Any or all of these may be null.
            String submsg = ex.getMessage();
            XMPPError error = ex.getXMPPError();
            Throwable subex = ex.getWrappedThrowable();
            
            if (error != null && error.getCode() == 404) {
                /* A common case: the JID was not found. */
                msg = "No bookkeeper exists at this address.";
                if (error.getMessage() != null)
                    msg = msg + " (" + error.getMessage() + ")";
                msg = msg + "\n(" + Bookkeeper.getDefaultJid() + ")";
            }
            else {
                msg = "The bookkeeper could not be found";
                if (submsg != null && subex == null && error == null)
                    msg = msg + ": " + submsg;
                else
                    msg = msg + ".";
                if (subex != null)
                    msg = msg + "\n" + subex.toString();
                if (error != null)
                    msg = msg + "\nJabber error " + error.toString();
            }

            JOptionPane.showMessageDialog(null, 
                msg,
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        assert (result instanceof List);
        List resultList = (List)result;

        /* First issue: not all the UIs on this list may have a compatible
         * client type. Filter those out. (Sure, the bookkeeper really should
         * have done that already. But let's be careful.) */

        URI clientType = JavolinApp.getClientTypeURI();
        List uiList = new ArrayList();
        for (Iterator it = resultList.iterator(); it.hasNext();) {
            GameUIInfo gameUI = (GameUIInfo) it.next();
            if (gameUI.getClientTypes().contains(clientType))
                uiList.add(gameUI);
        }

        mGameUIInfoList = uiList;

        boolean automateSole = (!mForceInteractive)
            && (PrefsDialog.getGameSelectUIAlways()
                || PrefsDialog.getGameSelectUISole());
        
        if (automateSole && uiList.size() == 0 && mLastChoice != null) {
            // auto-choose the sole (i.e., the user's last) choice

            mURL = mLastChoice;
            contCheckChoice();
            return;
        }

        if (uiList.size() == 0) {
            contPopDialog();
            return;
        }

        if (automateSole && uiList.size() == 1) {
            // auto-choose the sole choice.

            GameUIInfo info = (GameUIInfo)uiList.get(0);
            mURL = info.getLocation();

            contCheckChoice();
            return;
        }

        contPopDialog();
    }

    private void contPopDialog() {
        String lastname = null;

        if (mLastChoice != null) {
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMENAMES);
            lastname = prefs.get(mLastChoice.toString(), "Game interface");
        }

        ChooseUIDialog box = new ChooseUIDialog(mGameUIInfoList,
            mLastChoice, lastname);
        box.show();

        if (!box.getSuccess()) {
            // cancelled.
            callbackFail();
            return;
        }

        mURL = box.getResult();
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
                "Cannot download UI:\n" + ex.toString(),
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
                "Cannot read UI:\n" + ex.toString(),
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
                "Cannot parse UI metadata:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
            contCheckBail();
            return;
        }

        // Look at the metadata, see if it matches
        List rulesets = metadata.getAll(Metadata.VOLITY_RULESET);
        if (rulesets.size() == 0) {
            // Old UI, no metadata. Accept it at face value.
        }
        else {
            boolean match = false;

            // Look for a volity:ruleset entry which matches.
            for (int ix=0; ix<rulesets.size(); ix++) {
                String uristr = (String)rulesets.get(ix);
                try {
                    URI uri = VersionSpec.onlyURI(uristr);
                    if (mRuleset.equals(uri)) {
                        VersionSpec spec = VersionSpec.fromURI(uristr);
                        /* If there was no spec fragment, spec will be a "match
                         * anything" object. */
                        if (mVersionNumber.matches(spec)) {
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
                    "The UI you have selected does not match this game's ruleset.",
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
                contCheckBail();
                return;
            }
        }

        // Store the game name into the name mapping store, for future display
        String name = null;

        if (mGameUIInfoList != null) {
            for (int ix=0; ix<mGameUIInfoList.size(); ix++) {
                GameUIInfo info = (GameUIInfo)mGameUIInfoList.get(ix);
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
        mForceInteractive = true;

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
        prefs.put(mRuleset.toString(), mURL.toString());

        if (mCallback != null)
            mCallback.succeed(mURL, mLocalUI);
    }

}
