package org.volity.javolin.game;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.*;
import java.util.*;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.volity.client.*;
import org.volity.jabber.JIDUtils;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.JavolinApp;

/**
 * The all-singing, all-dancing, fully-asynchronous TableWindow factory.
 *
 * This is a separate class because it's gotten so big and ugly. The
 * MakeTableWindow object is transient; it contains the state needed to create
 * the TableWindow. (That is, the state needed to clean up if the creation
 * process dies halfway through.)
 */
public class MakeTableWindow
{
    JavolinApp mApp;
    XMPPConnection mConnection;
    TableWindowCallback mCallback;
    Window mParentDialog;

    GameServer mGameServer;
    GameTable mGameTable;
    String mServerID;
    String mTableID;
    String mNickname;
    TableWindow mTableWindow;
    boolean mIsCreating;

    /**
     * Create a MakeTableWindow. This does no work; it just sets up the context
     * for you to use later.
     *
     * @param app The JavolinApp.
     * @param connection The Jabber connection.
     * @param parent The window to hang error dialog boxes off of. It's okay
     *    if this is null; it's okay if the window closes while (or before)
     *    the MakeTableWindow is working.
     */
    public MakeTableWindow(JavolinApp app, XMPPConnection connection, 
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
     * The callback interface for table creation and table joining. Exactly one
     * of the two methods will be called:
     *
     *   void succeed(TableWindow win);
     *   void fail();
     *
     * Whichever method is called will be called in the Swing UI thread.
     * Implementations of these methods must be fast -- if they block, the app
     * will lag.
     */
    public interface TableWindowCallback {
        public void succeed(TableWindow win);
        public void fail();
    }

    /** 
     * Begin work, creating a new table.
     *
     * @param serverID The JID of the game parlor. (If this does not have 
     *   "volity" as the JID resource, that is implicitly added.)
     * @param nickname The nickname which the player desires.
     * @param callback Callback to invoke when the process succeeds or fails.
     *   This may be null; if it is non-null, the methods must be fast (non-
     *   blocking).
     */
    public void newTable(
        String serverID,
        String nickname,
        TableWindowCallback callback) {

        build(serverID, null, nickname, callback);
    }

    /** 
     * Begin work, joining an existing table.
     *
     * @param tableID The JID of the game table MUC.
     * @param nickname The nickname which the player desires.
     * @param callback Callback to invoke when the process succeeds or fails.
     *   This may be null; if it is non-null, the methods must be fast (non-
     *   blocking).
     */
    public void joinTable(
        String tableID,
        String nickname,
        TableWindowCallback callback) {
        build(null, tableID, nickname, callback);
    }

    /**
     * Beginning of the internal sequence which handles both newTable() and
     * joinTable().
     */
    private void build(
        String serverID,
        String tableID,
        String nickname,
        TableWindowCallback callback) {

        mCallback = callback;
        mNickname = nickname;
        mGameTable = null;
        mGameServer = null;
        mTableID = tableID;
        mIsCreating = false;

        if (mNickname == null)
            throw new RuntimeException("MakeTableWindow requires a nickname.");

        if (tableID == null && serverID == null)
            throw new RuntimeException("MakeTableWindow requires either serverID or tableID.");

        if (tableID == null) {
            // Create a new table.

            mIsCreating = true;
            mServerID = serverID;

            if (!JIDUtils.hasResource(mServerID)) {
                mServerID = JIDUtils.setResource(mServerID, "volity");
            }

            //### asyncify!
            try {
                mGameServer = new GameServer(mConnection, mServerID);
                GameTable table = mGameServer.newTable();
                contCreateDidNewTable(table, null);
            }
            catch (Exception ex) {
                contCreateDidNewTable(null, ex);
            }
            return;
        }

        // Join an existing table.

        // Stage 1: check to see if the MUC exists.

        new DiscoBackground(mConnection, 
            new DiscoBackground.Callback() {
                public void run(IQ result, XMPPException err, Object rock) {
                    contJoinDidQueryTable(result, err);
                }
            },
            DiscoBackground.QUERY_INFO, mTableID, null);
    }

    private void contJoinDidQueryTable(IQ result, XMPPException err) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (err != null) {
            // Disco query failed.
            XMPPException ex = err;
            new ErrorWrapper(ex);
            callbackFail();

            String msg = "The table could not be contacted.";

            // Any or all of these may be null.
            String submsg = ex.getMessage();
            XMPPError error = ex.getXMPPError();
            Throwable subex = ex.getWrappedThrowable();

            if (error != null 
                && (error.getCode() == 404 || error.getCode() == 400)) {
                /* A common case: the JID was not found. */
                msg = "No table exists at this address.";
                if (error.getMessage() != null)
                    msg = msg + " (" + error.getMessage() + ")";
                msg = msg + "\n(" + mTableID + ")";
            }
            else {
                msg = "The table could not be contacted";
                if (submsg != null && subex == null && error == null)
                    msg = msg + ": " + submsg;
                else
                    msg = msg + ".";
                if (subex != null)
                    msg = msg + "\n" + subex.toString();
                if (error != null)
                    msg = msg + "\nJabber error " + error.toString();
            }

            JOptionPane.showMessageDialog(mParentDialog, 
                msg,
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        assert (result != null && result instanceof DiscoverInfo);

        DiscoverInfo info = (DiscoverInfo)result;
        if (!info.containsFeature("http://jabber.org/protocol/muc")) {
            callbackFail();

            String msg = "This address (" + mTableID + ")\n"
                +"does not refer to a Volity game table.";

            JOptionPane.showMessageDialog(mParentDialog, 
                msg,
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        /* Disco success. Next step: Create a GameTable, and join the MUC.
         *
         * Note that we don't have a GameServer.
         */
        GameTable.ReadyListener listener = null;

        try
        {
            mGameTable = new GameTable(mConnection, mTableID);

            /* To get the GameServer, we need to join the MUC early. */

            listener = new GameTable.ReadyListener() {
                    public void ready() {
                        // Called outside Swing thread!
                        // Remove the listener, now that it's triggered
                        mGameTable.removeReadyListener(this);
                        // Invoke into the Swing thread.
                        SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    contJoinDoQueryRef();
                                }
                            });
                    }
                };
            mGameTable.addReadyListener(listener);

            mGameTable.join(mNickname);

            /*
             * Now we wait for the ReadyListener to fire, which will invoke
             * contJoinDoQueryRef(), below. 
             */
        }
        catch (XMPPException ex) 
        {
            new ErrorWrapper(ex);
            callbackFail();

            if (mGameTable != null) {
                if (listener != null)
                    mGameTable.removeReadyListener(listener);
                mGameTable.leave();
            }

            String msg = "The table could not be joined.";

            // Any or all of these may be null.
            String submsg = ex.getMessage();
            XMPPError error = ex.getXMPPError();
            Throwable subex = ex.getWrappedThrowable();

            if (error != null && error.getCode() == 404) 
            {
                /* A common case: the JID was not found. */
                msg = "No table exists at this address.";
                if (error.getMessage() != null)
                    msg = msg + " (" + error.getMessage() + ")";
                msg = msg + "\n(" + mTableID + ")";
            }
            else if (error != null && error.getCode() == 409) 
            {
                /* A common case: your nickname conflicts. */
                msg = "The nickname \"" + mNickname + "\" is already in\n"
                    +"use at this table. Please choose another.";
            }
            else {
                msg = "The table could not be joined";
                if (submsg != null && subex == null && error == null)
                    msg = msg + ": " + submsg;
                else
                    msg = msg + ".";
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
        catch (Exception ex)
        {
            new ErrorWrapper(ex);
            callbackFail();

            if (mGameTable != null) {
                if (listener != null)
                    mGameTable.removeReadyListener(listener);
                mGameTable.leave();
            }

            JOptionPane.showMessageDialog(mParentDialog, 
                "Cannot join table:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void contJoinDoQueryRef()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Next step: disco the referee.

        String refJID = mGameTable.getRefereeJID();
        
        new DiscoBackground(mConnection, 
            new DiscoBackground.Callback() {
                public void run(IQ result, XMPPException err, Object rock) {
                    contJoinDidQueryRef(result, err);
                }
            },
            DiscoBackground.QUERY_INFO, refJID, null);
    }

    private void contJoinDidQueryRef(IQ result, XMPPException err) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (err != null) {
            // Disco query failed.
            XMPPException ex = err;
            new ErrorWrapper(ex);
            callbackFail();

            mGameTable.leave();

            JOptionPane.showMessageDialog(mParentDialog,
                "Cannot contact referee:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        assert (result != null && result instanceof DiscoverInfo);

        try {
            DiscoverInfo info = (DiscoverInfo)result;
            mServerID = null;

            Form form = Form.getFormFrom(info);
            if (form != null) {
                FormField field = form.getField("parlor");
                if (field != null)
                    mServerID = (String) field.getValues().next();
            }
            
            if (mServerID == null || mServerID.equals("")) {
                throw new Exception("Unable to fetch parlor ID from referee");
            }

            // Next step: connect to the server, and construct the TableWindow.

            mGameServer = new GameServer(mConnection, mServerID);

            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        contJoinDoConstruct();
                    }
                });
            return;
        }
        catch (Exception ex)
        {
            new ErrorWrapper(ex);
            callbackFail();

            mGameTable.leave();

            JOptionPane.showMessageDialog(mParentDialog,
                "Cannot join table:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void contCreateDidNewTable(GameTable result, Exception err) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (err != null) {
            // RPC failed.
            new ErrorWrapper(err);
            callbackFail();

            if (err instanceof TokenFailure) {
                TokenFailure ex = (TokenFailure)err;

                String msg = JavolinApp.getTranslator().translate(ex);

                JOptionPane.showMessageDialog(mParentDialog,
                    "Cannot create table:\n" + msg,
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
            else if (err instanceof XMPPException) {
                XMPPException ex = (XMPPException)err;
                String msg = "The table could not be created."; 

                // Any or all of these may be null.
                String submsg = ex.getMessage();
                XMPPError error = ex.getXMPPError();
                Throwable subex = ex.getWrappedThrowable();

                if (error != null && error.getCode() == 404) {
                    /* A common case: the JID was not found. */
                    msg = "No game parlor exists at this address.";
                    if (error.getMessage() != null)
                        msg = msg + " (" + error.getMessage() + ")";
                    msg = msg + "\n(" + mServerID + ")";
                }
                else if (error != null && error.getCode() == 409) {
                    /* A common case: your nickname conflicts. */
                    msg = "The nickname \"" + mNickname + "\" is already in\n"
                        +"use at this table. Please choose another.";
                }
                else {
                    msg = "The table could not be created";
                    if (submsg != null && subex == null && error == null)
                        msg = msg + ": " + submsg;
                    else
                        msg = msg + ".";
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
            else {
                Exception ex = err;
                JOptionPane.showMessageDialog(mParentDialog, 
                    "Cannot create table:\n" + ex.toString(),
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }

            return;
        }

        assert (result != null);
        mGameTable = result;

        contJoinDoConstruct();
    }

    /**
     * The new-table and join-table paths merge here. At this point, we have
     * both a GameServer object and a GameTable object. It is time to construct
     * the TableWindow.
     */
    private void contJoinDoConstruct() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        assert (mGameServer != null);
        assert (mGameTable != null);
        assert (mTableWindow == null);

        try {
            URL uiUrl = getUIURL(mGameServer);
            if (uiUrl == null) {
                throw new Exception("Unable to fetch UI URL from parlor.");
            }

            File dir = JavolinApp.getUIFileCache().getUIDir(uiUrl);

            /* Once we call the TableWindow constructor, it owns the GameTable.
             * The constructor will clean up the GameTable on failure. */
            mTableWindow = new TableWindow(mGameServer, mGameTable,
                mNickname, dir, uiUrl);

            // If there were a failure after this point, we'd have to call
            // mTableWindow.leave(). But we're done.

            callbackSucceed();
            mApp.handleNewTableWindow(mTableWindow);
        }
        catch (TokenFailure ex)
        {
            callbackFail();

            mGameTable.leave();

            String msg = JavolinApp.getTranslator().translate(ex);

            JOptionPane.showMessageDialog(mParentDialog,
                "Cannot " + (mIsCreating ? "create" : "join") 
                + " table:\n" + msg,
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
        catch (XMPPException ex) 
        {
            new ErrorWrapper(ex);
            callbackFail();

            mGameTable.leave();

            String msg = "The table could not be " 
                + (mIsCreating ? "created" : "joined") 
                + ".";

            // Any or all of these may be null.
            String submsg = ex.getMessage();
            XMPPError error = ex.getXMPPError();
            Throwable subex = ex.getWrappedThrowable();

            if (error != null && error.getCode() == 404) {
                /* A common case: the JID was not found. */
                msg = "No game parlor exists at this address.";
                if (error.getMessage() != null)
                    msg = msg + " (" + error.getMessage() + ")";
                msg = msg + "\n(" + mServerID + ")";
            }
            else if (error != null && error.getCode() == 409) {
                /* A common case: your nickname conflicts. */
                msg = "The nickname \"" + mNickname + "\" is already in\n"
                    +"use at this table. Please choose another.";
            }
            else {
                msg = "The table could not be " + (mIsCreating ? "created" : "joined");
                if (submsg != null && subex == null && error == null)
                    msg = msg + ": " + submsg;
                else
                    msg = msg + ".";
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
        catch (Exception ex)
        {
            new ErrorWrapper(ex);
            callbackFail();

            mGameTable.leave();

            JOptionPane.showMessageDialog(mParentDialog, 
                "Cannot " + (mIsCreating ? "create" : "join") 
                + " table:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void callbackFail() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        if (mCallback != null)
            mCallback.fail();
    }

    private void callbackSucceed() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        assert (mTableWindow != null);
        if (mCallback != null)
            mCallback.succeed(mTableWindow);
    }


    private static Map sLocalUiFileMap = new HashMap();

    /**
     * Helper method for makeTableWindow. Returns the URL for the given game
     * server's UI.
     *
     * @param server The game server for which to retrieve the UI URL.
     * @return       The URL for the game UI, or null if none was
     *   available.
     * @exception XMPPException          If an XMPP error occurs.
     * @exception MalformedURLException  If an error ocurred creating a URL for
     *   a local file.
     */
    static URL getUIURL(GameServer server) throws XMPPException,
        MalformedURLException
    {
        URL retVal = null;

        //### asyncify this call! and all of getUIURL.

        Bookkeeper keeper = new Bookkeeper(server.getConnection());
        List uiList = keeper.getCompatibleGameUIs(server.getRuleset(),
            JavolinApp.getClientTypeURI());

        if (uiList.size() != 0) {
            // Use first UI info object
            GameUIInfo info = (GameUIInfo)uiList.get(0);
            retVal = info.getLocation();
        }
        else {
            if (sLocalUiFileMap.containsKey(server.getRuleset())) {
                retVal = (URL)sLocalUiFileMap.get(server.getRuleset());
            }
            else if (JOptionPane.showConfirmDialog(null,
                "No UI file is known for this game.\nChoose a local file?",
                JavolinApp.getAppName(), JOptionPane.YES_NO_OPTION) ==
                JOptionPane.YES_OPTION) {
                JFileChooser fDlg = new JFileChooser();
                int val = fDlg.showOpenDialog(null);

                if (val == JFileChooser.APPROVE_OPTION) {
                    retVal = fDlg.getSelectedFile().toURI().toURL();
                    sLocalUiFileMap.put(server.getRuleset(), retVal);
                }
            }
        }

        return retVal;
    }

}
