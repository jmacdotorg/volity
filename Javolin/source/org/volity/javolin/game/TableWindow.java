/*
 * TableWindow.java
 *
 * Copyright 2004 Karl von Laudermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.volity.javolin.game;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.text.*;

import org.apache.batik.bridge.UpdateManagerAdapter;
import org.apache.batik.bridge.UpdateManagerEvent;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.muc.Occupant;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.MUCUser;

import org.volity.client.DefaultStatusListener;
import org.volity.client.GameServer;
import org.volity.client.GameTable;
import org.volity.client.GameUI;
import org.volity.client.Player;
import org.volity.client.Referee;
import org.volity.client.SVGCanvas;
import org.volity.client.Seat;
import org.volity.client.comm.RPCBackground;
import org.volity.client.comm.SwingWorker;
import org.volity.client.data.JIDTransfer;
import org.volity.client.data.Metadata;
import org.volity.client.translate.TokenFailure;
import org.volity.client.translate.TranslateToken;
import org.volity.jabber.*;
import org.volity.javolin.*;
import org.volity.javolin.Audio;
import org.volity.javolin.chat.*;

/**
 * A window for playing a game.
 */
public class TableWindow extends JFrame
    implements PacketListener, CloseableWindow
{
    private final static String NODENAME = "TableWindow";
    private final static String CHAT_SPLIT_POS = "ChatSplitPos";
    private final static String USERLIST_SPLIT_POS = "UserListSplitPos";
    private final static String BOARD_SPLIT_POS = "BoardSplitPos";

    private final static String VIEW_GAME = "GameViewport";
    private final static String VIEW_LOADING = "LoadingMessage";

    private static Map sGameNameNumberMap = new HashMap();

    private final static String INVITE_LABEL = "Invite";
    private final static String READY_LABEL = "Ready";
    private final static String SEAT_LABEL = "Seat";
    private final static ImageIcon INVITE_ICON;
    private final static ImageIcon READY_ICON;
    private final static ImageIcon UNREADY_ICON;
    private final static ImageIcon SEAT_ICON;
    private final static ImageIcon UNSEAT_ICON;

    private final static Color colorCurrentTimestamp = Color.BLACK;
    private final static Color colorDelayedTimestamp = new Color(0.3f, 0.3f, 0.3f);

    static {
        INVITE_ICON = new ImageIcon(TableWindow.class.getResource("Invite_ButIcon.png"));
        READY_ICON = new ImageIcon(TableWindow.class.getResource("Ready_ButIcon.png"));
        UNREADY_ICON = new ImageIcon(TableWindow.class.getResource("Unready_ButIcon.png"));
        SEAT_ICON = new ImageIcon(TableWindow.class.getResource("Seat_ButIcon.png"));
        UNSEAT_ICON = new ImageIcon(TableWindow.class.getResource("Unseat_ButIcon.png"));
    }

    private String mGameName;
    private int mGameNameNumber;

    private JSplitPane mChatSplitter;
    private JSplitPane mUserListSplitter;
    private JSplitPane mBoardSplitter;
    private ChatLogPanel mLog;
    private JTextArea mInputText;
    private SVGCanvas mGameViewport;
    private JPanel mGameViewWrapper;
    private SeatChart mSeatChart;
    private HelpPanel mHelpPanel;
    private JComponent mLoadingComponent;
    private AbstractAction mSendMessageAction;
    private GameTable.ReadyListener mTableReadyListener;
    private GameTable.ReadyListener mTableShutdownListener;
    private DefaultStatusListener mTableStatusListener;
    private UpdateManagerAdapter mViewportUpdateListener;

    private JButton mInviteButton;
    private JButton mReadyButton;
    private JButton mSeatButton;
    private JLabel mRefereeStatusLabel;

    private UserColorMap mColorMap;
    private SimpleDateFormat mTimeStampFormat;

    private SizeAndPositionSaver mSizePosSaver;
    private GameServer mServer;
    private GameTable mGameTable;
    private String mNickname;
    private URL mUIUrl;

    private TranslateToken mTranslator;
    private TableMessageHandler mMessageHandler;
    private TableErrorHandler mErrorHandler;
    private TableLinkHandler mLinkHandler;
    private TableDefaultCallback mDefaultCallback;
    private TableMetadataProvider mMetadataProvider;

    private boolean mGameTableStarted = false;
    private boolean mGameViewportStarted = false;
    private boolean mGameStartFinished = false;

    private InfoDialog mInfoDialog = null;

    /**
     * Constructor.
     *
     * @param server                     The GameServer.
     * @param table                      The GameTable to join.
     * @param nickname                   The nickname to use to join the table.
     * @param uiDir                      The UI directory.
     * @param uiUrl                      The (original, remote) UI URL.
     * @exception XMPPException          If the table could not be joined.
     * @exception RPCException           If a new table could not be created.
     * @exception TokenFailure           If a new_table RPC failed.
     * @exception IOException            If a UI file could not be downloaded.
     * @exception MalformedURLException  If an invalid UI file URL was used.
     */
    protected TableWindow(GameServer server, GameTable table, String nickname,
        File uiDir, URL uiUrl) 
        throws XMPPException, RPCException, IOException, TokenFailure,
        MalformedURLException
    {
        assert (server != null);
        assert (table != null);
        assert (nickname != null);

        mServer = server;
        mGameTable = table;
        mNickname = nickname;
        mUIUrl = uiUrl;    // We save this only for the sake of the info dialog

        // We must now locate the "main" files in the UI directory. First, find
        // the directory which actually contains the significant files.
        uiDir = UIFileCache.locateTopDirectory(uiDir);

        //If there's exactly one file, that's it. Otherwise, look for
        // main.svg or MAIN.SVG.
        // XXX Or config.svg, main.html, config.html...
        File uiMainFile;
        File[] entries = uiDir.listFiles();

        if (entries.length == 1 && !entries[0].isDirectory())
        {
            uiMainFile = entries[0];
        }
        else
        {
            uiMainFile = UIFileCache.findFileCaseless(uiDir, "main.svg");
            if (uiMainFile == null)
            {
                throw new IOException("unable to locate UI file in cache");
            }
        }

        URL uiMainUrl = uiMainFile.toURI().toURL();

        // Set up a translator which knows about the "locale" subdirectory.
        // This will be used for all token translation at the table. (If there
        // is no "locale" or "LOCALE" subdir, then the argument to
        // TranslateToken() will be null. In this case, no game.* or seat.*
        // tokens will be translatable.)
        mTranslator = new TranslateToken(UIFileCache.findFileCaseless(uiDir, "locale"));

        /* Store the basic game name (as determined by the parlor info), and
         * the uniquifying number which we may add onto the end in the window
         * titlebar. */
        mGameName = mServer.getGameInfo().getGameName();
        if (mGameName == null)
            mGameName = "Game";
        mGameNameNumber = getGameNameNumber(mGameName);

        setTitle(JavolinApp.getAppName() + ": " + getWindowName());

        /* Several components want a callback that prints text in the message
         * window. However, we don't know that they'll call it in the Swing
         * thread. So, we'll make a thread-safe wrapper for mLog.message().
         */
        mMessageHandler = new TableMessageHandler(this);

        /* Some components also want a callback in which they can dump an
         * arbitrary exception. TokenFailures simply get translated and printed
         * (with messageHandler). Anything else is printed as an ugly exception
         * string, but the user can hit "Show Last Error" to see the whole
         * thing.
         *
         * Thread-safe.
         */
        mErrorHandler = new TableErrorHandler(this, mMessageHandler);

        /* Many RPC handlers are going to use this as a callback. */
        mDefaultCallback = new TableDefaultCallback(mErrorHandler);

        /* Finally, the SVGCanvas needs a way to handle hyperlink clicks.
         */
        mLinkHandler = new TableLinkHandler(mMessageHandler);

        // Create the SVG object.
        mGameViewport = new SVGCanvas(mGameTable, uiMainUrl, mTranslator,
            mMessageHandler, mErrorHandler, mLinkHandler);

        mViewportUpdateListener = new UpdateManagerAdapter()
            {
                public void managerStarted(UpdateManagerEvent evt)
                {
                    // Called outside Swing thread! (I think?)
                    // Invoke into the Swing thread. (In case.)
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                mGameViewportStarted = true;
                                tryFinishInit();
                            }
                        });
                }
            };
        mGameViewport.addUpdateManagerListener(mViewportUpdateListener);

        mMetadataProvider = new TableMetadataProvider(mGameViewport);

        mColorMap = new UserColorMap();
        // Give user first color
        mColorMap.getUserNameColor(mGameTable.getConnection().getUser());

        mTimeStampFormat = new SimpleDateFormat("HH:mm:ss");

        mSeatChart = new SeatChart(mGameTable, mColorMap, 
            mMetadataProvider, mTranslator, mMessageHandler);

        mHelpPanel = new HelpPanel(mGameTable);

        buildUI();

        setSize(600, 600);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        restoreWindowState();

        // Send message when user presses Enter while editing input text
        mSendMessageAction =
            new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    doSendMessage();
                }
            };

        mInputText.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            mSendMessageAction);

        // Handle window events
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter()
            {
                public void windowClosed(WindowEvent we)
                {
                    saveWindowState();
                    leave();
                }

                public void windowOpened(WindowEvent we)
                {
                    // Give focus to input text area when the window is created
                    mInputText.requestFocusInWindow();
                }
            });

        /* 
         * We attach to a high-level GameTable message service here, instead of
         * adding a listener directly to the MUC. This is because the table
         * might already be joined, and have messages waiting.
         */
        mGameTable.setQueuedMessageListener(this);

        /* 
         * Handle presence messages, although we don't care about queued
         * presence.
         */
        mGameTable.addParticipantListener(this);

        // Notify the player if the referee crashes.
        mTableShutdownListener = 
            new GameTable.ReadyListener() {
                public void ready() {
                    // Called outside Swing thread!
                    // Invoke into the Swing thread.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                JOptionPane.showMessageDialog(TableWindow.this,
                                    "The referee has shut down unexpectedly.",
                                    JavolinApp.getAppName() + ": Error",
                                    JOptionPane.ERROR_MESSAGE);
                            }
                        });
                }
            };
        mGameTable.addShutdownListener(mTableShutdownListener);        

        // We need a StatusListener to adjust button states when this player
        // stands, sits, etc.
        mTableStatusListener = new DefaultStatusListener() {
                public void stateChanged(int state) {
                    // Called outside Swing thread!
                    // (We can do the string picking first, that's thread-safe)
                    String str = "Game status unknown";
                    switch (state) {
                    case GameTable.STATE_SETUP:
                        str = "New game setup";
                        break;
                    case GameTable.STATE_ACTIVE:
                        str = "Game in progress";
                        break;
                    case GameTable.STATE_DISRUPTED:
                        str = "Game is disrupted: a seat has been left empty";
                        break;
                    case GameTable.STATE_ABANDONED:
                        str = "Game is abandoned: no humans are left";
                        break;
                    case GameTable.STATE_SUSPENDED:
                        str = "Game suspended";
                        break;
                    }
                    final String label = str;
                    // Invoke into the Swing thread.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                mRefereeStatusLabel.setText(label);
                                adjustButtons();
                            }
                        });
                }
                public void playerSeatChanged(final Player player, 
                    Seat oldseat, Seat newseat) {
                    // Called outside Swing thread!
                    // Invoke into the Swing thread.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (player == mGameTable.getSelfPlayer()) {
                                    adjustButtons();
                                }
                            }
                        });
                }
                public void playerReady(final Player player, boolean flag) {
                    // Called outside Swing thread!
                    // Invoke into the Swing thread.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (player == mGameTable.getSelfPlayer()) {
                                    adjustButtons();
                                }
                            }
                        });
                }
            };
        mGameTable.addStatusListener(mTableStatusListener);

        // Set up button actions.

        mInviteButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    doInviteDialog(null);
                }
            });

        new DropTarget(mInviteButton, DnDConstants.ACTION_MOVE,
            new DropTargetAdapter() {
                public void dragEnter(DropTargetDragEvent dtde) {
                    mInviteButton.setSelected(true);
                }
                public void dragExit(DropTargetEvent dte) {
                    mInviteButton.setSelected(false);
                }
                public void drop(DropTargetDropEvent ev) {
                    mInviteButton.setSelected(false);
                    try {
                        Transferable transfer = ev.getTransferable();
                        if (transfer.isDataFlavorSupported(JIDTransfer.JIDFlavor)) {
                            ev.acceptDrop(DnDConstants.ACTION_MOVE);
                            JIDTransfer obj = (JIDTransfer)transfer.getTransferData(JIDTransfer.JIDFlavor);
                            doInviteDialog(obj.getJID());
                            ev.dropComplete(true);
                            return;
                        }
                        ev.rejectDrop();
                    }
                    catch (Exception ex) {
                        ev.rejectDrop();
                    }
                }
            });

        mReadyButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!mGameTable.isSelfReady()) {
                        mGameTable.getReferee().ready(mDefaultCallback, null);
                    }
                    else {
                        mGameTable.getReferee().unready(mDefaultCallback, null);
                    }
                }
            });

        mSeatButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!mGameTable.isSelfSeated()) {
                        mGameTable.getReferee().sit(mDefaultCallback, null);
                    }
                    else {
                        mGameTable.getReferee().stand(mDefaultCallback, null);
                    }
                }
            });

        new DropTarget(mSeatButton, DnDConstants.ACTION_MOVE,
            new DropTargetAdapter() {
                public void dragEnter(DropTargetDragEvent dtde) {
                    mSeatButton.setSelected(true);
                }
                public void dragExit(DropTargetEvent dte) {
                    mSeatButton.setSelected(false);
                }
                public void drop(DropTargetDropEvent ev) {
                    mSeatButton.setSelected(false);
                    try {
                        Transferable transfer = ev.getTransferable();
                        if (transfer.isDataFlavorSupported(JIDTransfer.JIDFlavor)) {
                            ev.acceptDrop(DnDConstants.ACTION_MOVE);
                            JIDTransfer obj = (JIDTransfer)transfer.getTransferData(JIDTransfer.JIDFlavor);
                            mSeatChart.requestSeatChange(obj.getJID(), mSeatChart.ANY_SEAT);
                            ev.dropComplete(true);
                            return;
                        }
                        ev.rejectDrop();
                    }
                    catch (Exception ex) {
                        ev.rejectDrop();
                    }
                }
            });

        // Join the table, if we haven't already
        try
        {
            if (!mGameTable.isJoined()) {
                if (mTableReadyListener == null) {
                    mTableReadyListener = new GameTable.ReadyListener() {
                            public void ready() {
                                // Called outside Swing thread!
                                // Invoke into the Swing thread.
                                SwingUtilities.invokeLater(new Runnable() {
                                        public void run() {
                                            mGameTableStarted = true;
                                            tryFinishInit();
                                        }
                                    });
                            }
                        };
                }
                mGameTable.addReadyListener(mTableReadyListener);
                mGameTable.join(mNickname);
            }
            else {
                mGameTableStarted = true;
                tryFinishInit();
            }
        }
        catch (XMPPException ex)
        {
            // Clean up anything we may have started, like mGameTable.
            leave();
            // Re-raise exception, so that the constructor fails
            throw ex;
        }
    }

    /**
     * Performs final steps of initialization of the GameTable, after the UI
     * file has been loaded.
     *
     * Two operations must be complete before we do this: the UI must be
     * created, and the MUC must be joined. Conveniently, these happen in
     * parallel. So the listener for *each* op calls this function. The
     * function checks to make sure that *both* flags are set before it starts
     * work.
     *
     * The function also checks to make sure it only runs once -- or rather,
     * only once per UI initialization. (The "reload UI" command resets the
     * switch.)
     */
    private void tryFinishInit()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (mGameViewport == null)
        {
            // I guess the window has already been closed.
            return;
        }
        if (!mGameTableStarted || !mGameViewportStarted) 
        {
            // Need both operations finished.
            return;
        }
        if (mGameStartFinished) 
        {
            // Should only do this once per UI initialization.
            return;
        }
        mGameStartFinished = true;

        switchView(VIEW_GAME);
        //mGameViewport.forceRedraw();

        // Remove loading component, since it's no longer needed
        if (mLoadingComponent != null) {
            ((CardLayout)mGameViewWrapper.getLayout()).removeLayoutComponent(
                mLoadingComponent);
            mLoadingComponent = null;
        }

        // Put a message service in the referee (only used for debug output)
        Referee referee = mGameTable.getReferee();
        referee.setMessageHandler(mMessageHandler);

        // Begin the flood of seating/config info.
        referee.send_state(mDefaultCallback, null);
    }

    /**
     * Clean up everything we tried to create. This is called when the window
     * closes, or if there's a creation error and it never gets opened.
     */
    public void leave() 
    {
        if (mGameName != null) {
            releaseGameNameNumber(mGameName, mGameNameNumber);
            mGameName = null;
        }

        // Shut down the UI.
        if (mGameViewport != null) {

            if (mViewportUpdateListener != null) {
                mGameViewport.removeUpdateManagerListener(mViewportUpdateListener);
                mViewportUpdateListener = null;
            }

            mGameViewport.stop();
            mGameViewport = null;
        }

        // Leave the chat room.
        if (mGameTable != null) {
            mGameTable.removeParticipantListener(this);
            mGameTable.clearQueuedMessageListener();
            if (mTableReadyListener != null) {
                mGameTable.removeReadyListener(mTableReadyListener);
                mTableReadyListener = null;
            }
            if (mTableShutdownListener != null) {
                mGameTable.removeShutdownListener(mTableShutdownListener);
                mTableShutdownListener = null;
            }
            if (mTableStatusListener != null) {
                mGameTable.removeStatusListener(mTableStatusListener);
                mTableStatusListener = null;
            }

            mGameTable.leave();
            mGameTable = null;
        }

        if (mSeatChart != null) {
            mSeatChart.dispose();
            mSeatChart = null;
        }

        if (mHelpPanel != null) {
            mHelpPanel.dispose();
            mHelpPanel = null;
        }

        if (mLog != null) {
            mLog.dispose();
        }

        if (mColorMap != null) {
            mColorMap.dispose();
            mColorMap = null;
        }

        if (mMetadataProvider != null) {
            mMetadataProvider.close();
            mMetadataProvider = null;
        }
        if (mLinkHandler != null) {
            mLinkHandler.close();
            mLinkHandler = null;
        }
        if (mDefaultCallback != null) {
            mDefaultCallback.close();
            mDefaultCallback = null;
        }
        if (mErrorHandler != null) {
            mErrorHandler.close();
            mErrorHandler = null;
        }
        if (mMessageHandler != null) {
            mMessageHandler.close();
            mMessageHandler = null;
        }
    }

    /**
     * Some classes which act as services: handling text messages, errors, or
     * link clicks. 
     *
     * These are set up as static classes so that they can be severed when the
     * table shuts down. Lots of other classes hold references on these
     * handlers. We don't want them to be holding references on the
     * TableWindow.
     */

    protected static class TableMessageHandler implements GameUI.MessageHandler
    {
        TableWindow mParent;

        public TableMessageHandler(TableWindow parent) {
            mParent = parent;
        }

        public void close() {
            mParent = null;
        }

        public void print(final String msg) {
            if (SwingUtilities.isEventDispatchThread()) {
                if (mParent != null)
                    mParent.mLog.message(msg);
                return;
            }
            // Otherwise, invoke into the Swing thread.
            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (mParent != null)
                            mParent.mLog.message(msg);
                    }
                });
        }
    }

    protected static class TableErrorHandler implements GameUI.ErrorHandler
    {
        TableWindow mParent;
        GameUI.MessageHandler mMessageHandler;

        public TableErrorHandler(TableWindow parent,
            GameUI.MessageHandler messageHandler) {
            mParent = parent;
            mMessageHandler = messageHandler;
        }

        public void close() {
            mParent = null;
            mMessageHandler = null;
        }

        public void error(Exception ex) {
            if (ex instanceof TokenFailure && mParent != null) {
                // No need to display the stack trace of a token failure.
                String msg = mParent.mTranslator.translate((TokenFailure)ex);
                if (mMessageHandler != null)
                    mMessageHandler.print(msg);
                return;
            }

            // Display a one-line version of the error, and stash the
            // whole thing away in ErrorWrapper for debugging
            // commands.
            new ErrorWrapper(ex);
            if (mMessageHandler != null)
                mMessageHandler.print(ex.toString());
        }
    }

    protected static class TableDefaultCallback implements RPCBackground.Callback
    {
        GameUI.ErrorHandler mErrorHandler;

        public TableDefaultCallback(GameUI.ErrorHandler errorHandler) {
            mErrorHandler = errorHandler;
        }

        public void close() {
            mErrorHandler = null;
        }

        public void run(Object result, Exception err, Object rock) {
            if (err != null) {
                if (mErrorHandler != null) {
                    mErrorHandler.error(err);
                }
                else {
                    // Best we can do.
                    new ErrorWrapper(err);
                }
            }
        }
    };

    protected static class TableLinkHandler implements SVGCanvas.LinkHandler
    {
        GameUI.MessageHandler mMessageHandler;

        public TableLinkHandler(GameUI.MessageHandler messageHandler) {
            mMessageHandler = messageHandler;
        }

        public void close() {
            mMessageHandler = null;
        }

        public void link(String uri) {
            if (PlatformWrapper.launchURLAvailable()) {
                PlatformWrapper.launchURL(uri);
            }
            else {
                if (mMessageHandler != null)
                    mMessageHandler.print("Visit this website: " + uri);
            }
        }
    }

    protected static class TableMetadataProvider implements Metadata.Provider
    {
        SVGCanvas mCanvas;

        public TableMetadataProvider(SVGCanvas canvas) {
            mCanvas = canvas;
        }

        public void close() {
            mCanvas = null;
        }

        public Metadata getMetadata() {
            if (mCanvas == null)
                return null;
            GameUI ui = mCanvas.getUI();
            if (ui == null)
                return null;
            return ui.getMetadata();
        }
    }
    
    /**
     * Return the URL that the window's UI was loaded from. (This points at the
     * original server, not the cache directory.)
     */
    public URL getUIUrl() 
    {
        return mUIUrl;
    }

    /**
     * Return the general name for the game. This is taken from the server
     * disco info. (If we didn't get a name that way, it's just "Game".)
     */
    public String getGameName()
    {
        return mGameName;
    }

    /**
     * Return the window's name for the game. This is the server's game name,
     * possibly with a uniquifying number stuck on the end. (So "Chess", 
     * "Chess (2)", "Chess (3)"...)
     */
    public String getWindowName() 
    {
        String res = mGameName;
        if (mGameNameNumber > 1)
            res = res + " (" + mGameNameNumber + ")";
        return res;
    }

    private static int getGameNameNumber(String name) 
    {
        SortedSet set = (SortedSet)sGameNameNumberMap.get(name);

        if (set == null) {
            set = new TreeSet();
            sGameNameNumberMap.put(name, set);
            set.add(new Integer(1));
            return 1;
        }

        int num = 1;
        for (Iterator iter = set.iterator(); iter.hasNext(); ) {
            Integer nextItem = (Integer)iter.next();
            int next = nextItem.intValue();
            if (next != num)
                break;
            num++;
        }

        set.add(new Integer(num));
        return num;
    }

    private static void releaseGameNameNumber(String name, int num)
    {
        Integer item = new Integer(num);
        SortedSet set = (SortedSet)sGameNameNumberMap.get(name);

        if (set == null) {
            return;
        }

        set.remove(item);
    }

    /**
     * Return the MUC JID (as a string) of the table.
     */
    public String getRoom()
    {
        return mGameTable.getRoom();
    }

    /**
     * Gets the game token translator belonging to the table.
     *
     * @return   The TranslateToken belonging to the table.
     */
    public TranslateToken getTranslator()
    {
        return mTranslator;
    }

    /**
     * Saves window state to the preferences storage, including window size and position,
     * and splitter bar positions.
     */
    private void saveWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.saveSizeAndPosition();

        prefs.putInt(CHAT_SPLIT_POS, mChatSplitter.getDividerLocation());
        prefs.putInt(BOARD_SPLIT_POS, mBoardSplitter.getDividerLocation());
        prefs.putInt(USERLIST_SPLIT_POS, mUserListSplitter.getDividerLocation());
    }

    /**
     * Restores window state from the preferences storage, including window size and
     * position, and splitter bar positions.
     */
    private void restoreWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.restoreSizeAndPosition();

        mChatSplitter.setDividerLocation(prefs.getInt(CHAT_SPLIT_POS, 100));
        mBoardSplitter.setDividerLocation(prefs.getInt(BOARD_SPLIT_POS,
            getHeight() - 200));
        mUserListSplitter.setDividerLocation(prefs.getInt(USERLIST_SPLIT_POS,
            getWidth() - 156));
    }

    /**
     * Reload the game UI from scratch.
     */
    public void doReloadUI() 
    {
        /* ### If we switch to a different UI, we'd need a whole new
         * translator. Or we'd need to switch the translator to a different
         * localedir, really, since so many components cache pointers to it. 
         * ### This also applies to a reload that recreates the cache dir.
         */
        mTranslator.clearCache();

        /* Clear the seat marks, so that the new UI gets a blank mark slate.
         */
        mGameTable.setSeatMarks(new HashMap());
        
        /* Reset this switch, so that the UI update event triggers a new
         * send_state() RPC. */
        mGameStartFinished = false;

        mGameViewport.reloadUI();

        /* ### This may change metadata, therefore seat panel coloring.
         * However, it's a nuisance to notify all the SeatPanels and get them
         * to update their borders. I suppose this could lead to a race, where
         * the SeatPanels get created before the metadata is available, and
         * therefore they never get colored. Oh well.
         */
    }

    /**
     * PacketListener interface method implementation. Used for both Message
     * and Presence packets.
     *
     * Called outside Swing thread!
     *
     * @param packet  The packet received.
     */
    public void processPacket(final Packet packet)
    {
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (packet instanceof Message) {
                        doMessageReceived((Message)packet);
                    }
                    if (packet instanceof Presence) {
                        Presence pres = (Presence)packet;

                        String from = pres.getFrom();

                        String nick = null;
                        String realAddr = null;
                        boolean realjid = false;

                        PacketExtension ext = pres.getExtension("x",
                            "http://jabber.org/protocol/muc#user");
                        if (ext != null && ext instanceof MUCUser) {
                            MUCUser userext = (MUCUser)ext;
                            MUCUser.Item item = userext.getItem();
                            if (item != null) {
                                realAddr = item.getJid();
                                realjid = true;
                                nick = item.getNick();
                            }
                        }

                        if (realAddr == null && from != null) {
                            realAddr = from;
                            realjid = false;
                        }

                        if (nick == null && from != null) {
                            nick = StringUtils.parseResource(from);
                        }

                        if (realAddr != null && nick != null) {
                            Presence.Type typ = pres.getType();
                            if (typ == Presence.Type.AVAILABLE) {
                                mLog.message(realAddr, null,
                                    nick+" has joined the table.");
                                Audio.playPresenceIn();
                            }
                            if (typ == Presence.Type.UNAVAILABLE) {
                                mLog.message(realAddr, null,
                                    nick+" has left the table.");
                                Audio.playPresenceOut();
                            }
                        }
                    }
                }
            });
    }

    /**
     * Sends the message that the user typed in the input text area.
     */
    private void doSendMessage()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        try
        {
            mGameTable.sendMessage(mInputText.getText());
            mInputText.setText("");
        }
        catch (XMPPException ex)
        {
            new ErrorWrapper(ex);
            JOptionPane.showMessageDialog(this, ex.toString(),
                JavolinApp.getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handles a message received from the MUC.
     *
     * @param msg  The Message object that was received.
     */
    private void doMessageReceived(Message msg)
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (msg.getType() == Message.Type.ERROR)
        {
            JOptionPane.showMessageDialog(this, msg.getError().getMessage(),
                JavolinApp.getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);
        }
        else if (msg.getType() == Message.Type.HEADLINE)
        {
            // Do nothing
        }
        else
        {
            String from = msg.getFrom();

            Occupant occ = mGameTable.getOccupant(from);

            String nick;
            String realAddr;
            boolean realjid;

            if (occ != null) {
                nick = occ.getNick();
                realAddr = occ.getJid();
                realjid = true;
            }
            else {
                nick = StringUtils.parseResource(from);
                realAddr = from;
                realjid = false;
            }

            if (realAddr == null) {
                realAddr = from;
                realjid = false;
            }

            PacketExtension ext;
            Date date = null;

            // Suppress old-style MUC status messages.
            ext = msg.getExtension("x", "http://jabber.org/protocol/muc#user");
            if (ext != null && ext instanceof MUCUser) {
                MUCUser userext = (MUCUser)ext;
                if (userext != null && userext.getStatus() != null
                    && ((nick == null) || (nick.equals(""))))
                    return;
            }

            ext = msg.getExtension("x", "jabber:x:delay");
            if (ext != null && ext instanceof DelayInformation) {
                date = ((DelayInformation)ext).getStamp();
            }

            mLog.message(realAddr, realjid, nick, msg.getBody(), date);
            if (ext == null)
                Audio.playMessage();
        }
    }

    /**
     * Bring up the game's info dialog box.
     */
    public void doInfoDialog() {
        if (mInfoDialog == null) {
            if (mGameViewport == null || mGameViewport.getUI() == null)
                return;
            Metadata metadata = mGameViewport.getUI().getMetadata();
            mInfoDialog = new InfoDialog(this, mGameTable,
                mServer.getGameInfo(), metadata);

            // When the InfoDialog closes, clear mInfoDialog
            mInfoDialog.addWindowListener(
                new WindowAdapter() {
                    public void windowClosed(WindowEvent ev) {
                        mInfoDialog = null;
                    }
                });
        }

        mInfoDialog.show();
    }

    /**
     * Suspend the game. If the game is not in progress, or already suspended,
     * or if the player is not seated, this will generate a failure token
     * message.
     */
    public void doSuspendTable() {
        mGameTable.getReferee().suspendGame(mDefaultCallback, null);
    }

    /**
     * Bring up an invite dialog. There can be multiple of these at a time,
     * even for the same game.
     *
     * If recipient is null, start an empty dialog. If not null, check to see
     * if the given JID is already present in the game. If it is not, start a
     * dialog with the bare form of the JID.
     */
    public void doInviteDialog(String recipient) {
        if (recipient != null) {
            recipient = StringUtils.parseBareAddress(recipient);

            Iterator iter = mGameTable.getPlayers();
            while (iter.hasNext()) {
                Player player = (Player)iter.next();
                String jid = StringUtils.parseBareAddress(player.getJID());
                if (recipient.equals(jid))
                    return;
            }
        }

        /* The recipient is now a bare address, not present at the table. Or
         * else it's null.
         */

        SendInvitationDialog box =
            new SendInvitationDialog(TableWindow.this, mGameTable, recipient);
        box.show();
    }

    /**
     * Send a request for a retainer bot.
     */
    public void doInviteBot() {
        mGameTable.getReferee().addBot(mDefaultCallback, null);
    }

    /**
     * Given a list of full JIDs, send an invitation to each of them, in
     * parallel. This does *not* give user feedback on failures. It is intended
     * for use on a collection of users (or a collection of resources of the
     * same user), where the player doesn't want to be bothered with a string
     * of failure dialogs. 
     *
     * @param jids a list of full JID strings.
     * @param msg a message to include in the invites, or null.
     */
    public void sendQuietInvites(List jids, final String msg) {
        final RPCBackground.Callback silentCallback = 
            new RPCBackground.Callback() {
                public void run(Object result, Exception err, Object rock) { }
            };
        for (Iterator iter = jids.iterator(); iter.hasNext(); ) {
            final String jid = (String)iter.next();
            SwingWorker worker = new SwingWorker() {
                    public Object construct() {
                        mGameTable.getReferee().invitePlayer(jid, msg,
                            silentCallback, null);
                        return jid;
                    }
                };
            worker.start();
        }
    }

    /**
     * Get the toolbar buttons into the correct state.
     */
    private void adjustButtons() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        boolean isSeated, isReady, isGameActive;

        isGameActive = mGameTable.isRefereeStateActive();
        isSeated = mGameTable.isSelfSeated();
        isReady = mGameTable.isSelfReady();

        mReadyButton.setEnabled(isSeated && !isGameActive);
        mSeatButton.setEnabled(!isGameActive);

        if (isSeated) {
            mSeatButton.setIcon(SEAT_ICON);
        }
        else {
            mSeatButton.setIcon(UNSEAT_ICON);
        }

        if (isGameActive) {
            mSeatButton.setToolTipText("The game is in progress");
        }
        else {
            if (isSeated) 
                mSeatButton.setToolTipText("Stand up");
            else 
                mSeatButton.setToolTipText("Sit down");
        }

        if (isReady) 
            mReadyButton.setIcon(READY_ICON);
        else 
            mReadyButton.setIcon(UNREADY_ICON);

        if (isGameActive) {
            mReadyButton.setToolTipText("The game is in progress");
        }
        else if (!isSeated) {
            mReadyButton.setToolTipText("You must sit down before you can declare yourself ready");
        }
        else {
            if (isReady) 
                mReadyButton.setToolTipText("Declare yourself not ready");
            else
                mReadyButton.setToolTipText("Declare yourself ready to play");
        }
    }

    /**
     * Switches the mGameViewWrapper to show either the loading message or the game view.
     *
     * @param viewStr  The selector for the view, either VIEW_LOADING or VIEW_GAME.
     */
    private void switchView(String viewStr)
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        CardLayout layout = ((CardLayout)mGameViewWrapper.getLayout());
        layout.show(mGameViewWrapper, viewStr);
    }

    /**
     * Creates and returns a JComponent that indicates that the game UI is loading.
     *
     * @return   A JComponent that indicates that the game UI is loading.
     */
    private JComponent makeLoadingComponent()
    {
        JPanel retVal = new JPanel(new GridBagLayout());
        GridBagConstraints c;

        int gridY = 0;

        // Add Loading label
        JLabel someLabel = new JLabel("Loading...");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.anchor = GridBagConstraints.SOUTH;
        retVal.add(someLabel, c);
        gridY++;

        // Add progress bar
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(6, 0, 0, 0);
        c.anchor = GridBagConstraints.NORTH;
        retVal.add(progress, c);

        return retVal;
    }

    /**
     * Populates the frame with UI controls.
     */
    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());

        // JPanel with CardLayout to hold game viewport and "Loading" message
        mGameViewWrapper = new JPanel(new CardLayout());
        mGameViewWrapper.add(mGameViewport, VIEW_GAME);
        mLoadingComponent = makeLoadingComponent();
        mGameViewWrapper.add(mLoadingComponent, VIEW_LOADING);
        switchView(VIEW_LOADING);

        // Split pane for message text area and input text area
        mChatSplitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mChatSplitter.setResizeWeight(1);
        mChatSplitter.setBorder(BorderFactory.createEmptyBorder());

        mLog = new ChatLogPanel(mColorMap);
        mChatSplitter.setTopComponent(mLog);

        mInputText = new JTextArea();
        mInputText.setLineWrap(true);
        mInputText.setWrapStyleWord(true);
        mInputText.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        mChatSplitter.setBottomComponent(new JScrollPane(mInputText));

        // Split pane separating game viewport from message text and input text
        mBoardSplitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mBoardSplitter.setResizeWeight(1);
        mBoardSplitter.setBorder(BorderFactory.createEmptyBorder());

        mBoardSplitter.setTopComponent(mGameViewWrapper);

        mBoardSplitter.setBottomComponent(mChatSplitter);

        // Split pane separating user list from everything else
        mUserListSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mUserListSplitter.setResizeWeight(1);

        mUserListSplitter.setLeftComponent(mBoardSplitter);

        // Right side, including SeatChart and HelpPanel
        JPanel rightSide = new JPanel(new GridBagLayout());
        {
            JComponent chart = mSeatChart.getComponent();
            JComponent scrollChart = new JScrollPane(chart);

            GridBagConstraints c;
            int row = 0;

            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.fill = GridBagConstraints.BOTH;
            c.anchor = GridBagConstraints.NORTH;
            c.weightx = 1;
            c.weighty = 1;
            rightSide.add(scrollChart, c);
            row++;

            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.SOUTH;
            c.weightx = 1;
            c.weighty = 0;
            rightSide.add(mHelpPanel, c);
            row++;
        }
        mUserListSplitter.setRightComponent(rightSide);

        cPane.add(mUserListSplitter, BorderLayout.CENTER);

        // The toolbar.

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        cPane.add(toolbar, BorderLayout.NORTH);

        toolbar.addSeparator();

        mRefereeStatusLabel = new JLabel("Connecting...");
        mRefereeStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        toolbar.add(mRefereeStatusLabel);

        // Blank stretchy component
        JComponent label = new JLabel("");
        toolbar.add(label);
        label.setMaximumSize(new Dimension(32767, 10));

        mInviteButton = new JButton(INVITE_LABEL, INVITE_ICON);
        toolbar.add(mInviteButton);

        toolbar.addSeparator(new Dimension(16, 10));

        mSeatButton = new JButton(SEAT_LABEL, UNSEAT_ICON);
        toolbar.add(mSeatButton);

        toolbar.addSeparator();

        mReadyButton = new JButton(READY_LABEL, UNREADY_ICON);
        toolbar.add(mReadyButton);

        adjustButtons();

        // Add the window menu bar
        JavolinMenuBar.applyPlatformMenuBar(this);
    }
}
