package org.volity.javolin.chat;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.MissingResourceException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.XMPPError;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.JavolinApp;

/**
 * The all-singing, all-dancing, fully-asynchronous MUCWindow factory.
 *
 * This is a separate class because it's analogous to MakeTableWindow. The
 * MakeMUCWindow object is transient; it contains the state needed to create
 * the MUCWindow. (That is, the state needed to clean up if the creation
 * process dies halfway through.)
 */
public class MakeMUCWindow
{
    JavolinApp mApp;
    XMPPConnection mConnection;
    MUCWindowCallback mCallback;
    Window mParentDialog;

    String mMucID;
    String mNickname;
    MUCWindow mMUCWindow;

    /**
     * Create a MakeMUCWindow. This does no work; it just sets up the context
     * for you to use later.
     *
     * @param app The JavolinApp.
     * @param connection The Jabber connection.
     * @param parent The window to hang error dialog boxes off of. It's okay
     *    if this is null; it's okay if the window closes while (or before)
     *    the MakeMUCWindow is working.
     */
    public MakeMUCWindow(JavolinApp app, XMPPConnection connection, 
        Window parent) {
        mApp = app;
        mConnection = connection;
        mParentDialog = parent;

        if (parent != null) {
            parent.addWindowListener(
                new WindowAdapter() {
                    public void windowClosed(WindowEvent ev) {
                        mParentDialog = null;
                    }
                });
        }
    }

    /**
     * The callback interface for MUC joining. Exactly one of the two methods
     * will be called:
     *
     *   void succeed(MUCWindow win);
     *   void fail();
     *
     * In the case of succeed, the window may be a newly-created MUCWindow or
     * one that previously existed.
     *
     * Whichever method is called will be called in the Swing UI thread.
     * Implementations of these methods must be fast -- if they block, the app
     * will lag.
     */
    public interface MUCWindowCallback {
        public void succeed(MUCWindow win);
        public void fail();
    }

    /** 
     * Begin work, joining a MUC.
     *
     * @param mucID The JID of the MUC.
     * @param nickname The nickname which the player desires.
     * @param callback Callback to invoke when the process succeeds or fails.
     *   This may be null; if it is non-null, the methods must be fast (non-
     *   blocking).
     */
    public void joinMUC(
        String mucID,
        String nickname,
        MUCWindowCallback callback) {

        mCallback = callback;
        mNickname = nickname;
        mMucID = mucID;

        if (mNickname == null)
            throw new RuntimeException("MakeMUCWindow requires a nickname.");

        // Make sure we're not already in this MUC.
        for (Iterator it = mApp.getMucWindows(); it.hasNext(); ) {
            MUCWindow win = (MUCWindow)it.next();
            if (mucID.equals(win.getRoom())) {
                // We are. Bring up the existing window, and exit.
                callbackAlreadyConnected(win);
                win.setVisible(true);
                return;
            }
        }

        // Create the MUCWindow

        try {
            mMUCWindow = new MUCWindow(mConnection, mucID, mNickname);

            // Guess it worked.
            callbackSucceed();
            mApp.handleNewMucWindow(mMUCWindow);
        }
        catch (XMPPException ex) {
            new ErrorWrapper(ex);
            callbackFail();

            String msg = localize("ErrorCouldNotJoin");

            // Any or all of these may be null.
            String submsg = ex.getMessage();
            XMPPError error = ex.getXMPPError();
            Throwable subex = ex.getWrappedThrowable();

            if (error != null && error.getCode() == 404) {
                /* A common case: the JID was not found. */
                msg = localize("ErrorNoSuchMUC");
                if (error.getMessage() != null)
                    msg = msg + " (" + error.getMessage() + ")";
                msg = msg + "\n(" + mMucID + ")";
            }
            else if (error != null && error.getCode() == 409) {
                /* A common case: your nickname conflicts. */
                msg = localize("ErrorNicknameConflict", mNickname);
            }
            else {
                if (submsg != null && subex == null && error == null)
                    msg = localize("ErrorCouldNotJoinColon") + " " + submsg;
                else
                    msg = localize("ErrorCouldNotJoin");
                if (subex != null)
                    msg = msg + "\n" + subex.toString();
                if (error != null)
                    msg = msg + "\nJabber error " + error.toString();
            }

            JOptionPane.showMessageDialog(mParentDialog, 
                msg,
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Localization helper.
     */
    protected static String localize(String key) {
        try {
            return JavolinApp.resources.getString("MakeMUCWindow_"+key);
        }
        catch (MissingResourceException ex) {
            return "???MakeMUCWindow_"+key;
        }
    }

    protected String localize(String key, Object arg1) {
        try {
            String pattern = JavolinApp.resources.getString("MakeMUCWindow_"+key);
            return MessageFormat.format(pattern, new Object[] { arg1 });
        }
        catch (MissingResourceException ex) {
            return "???"+"MakeMUCWindow_"+key;
        }
    }

    private void callbackFail() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        if (mCallback != null)
            mCallback.fail();
    }

    private void callbackAlreadyConnected(MUCWindow win) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        assert (mMUCWindow == null);
        assert (win != null);
        if (mCallback != null)
            mCallback.succeed(win);
    }

    private void callbackSucceed() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        assert (mMUCWindow != null);
        if (mCallback != null)
            mCallback.succeed(mMUCWindow);
    }

}
