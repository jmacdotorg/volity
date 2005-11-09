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
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.DiscoverInfo;

import org.volity.client.*;
import org.volity.jabber.*;
import org.volity.javolin.*;
import org.volity.javolin.chat.*;

/**
 * A window for playing a game.
 */
public class TableWindow extends JFrame implements PacketListener
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
    private LogTextPanel mMessageText;
    private JTextArea mInputText;
    private SVGCanvas mGameViewport;
    private JPanel mGameViewWrapper;
    private SeatChart mSeatChart;
    private JComponent mLoadingComponent;
    private AbstractAction mSendMessageAction;

    private JButton mInviteButton;
    private JButton mReadyButton;
    private JButton mSeatButton;
    private JLabel mRefereeStatusLabel;

    private UserColorMap mUserColorMap;
    private SimpleDateFormat mTimeStampFormat;

    private SizeAndPositionSaver mSizePosSaver;
    private GameServer mServer;
    private GameTable mGameTable;
    private String mNickname;
    private URL mUIUrl;
    private TranslateToken mTranslator;

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
        uiDir = locateTopDirectory(uiDir);

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
            uiMainFile = findFileCaseless(uiDir, "main.svg");
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
        mTranslator = new TranslateToken(findFileCaseless(uiDir, "locale"));

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
         * thread. So, we'll make a thread-safe wrapper for
         * writeMessageText. */
        final GameUI.MessageHandler messageHandler = new GameUI.MessageHandler() {
                public void print(final String msg)
                {
                    if (SwingUtilities.isEventDispatchThread()) {
                        writeMessageText(msg);
                        return;
                    }
                    // Otherwise, invoke into the Swing thread.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                writeMessageText(msg);
                            }
                        });
                }
            };

        /* Some components also want a callback in which they can dump an
         * arbitrary exception. TokenFailures simply get translated and printed
         * (with messageHandler). Anything else is printed as an ugly exception
         * string, but the user can hit "Show Last Error" to see the whole
         * thing.
         */
        GameUI.ErrorHandler errorHandler = new GameUI.ErrorHandler() {
                public void error(Exception ex) {
                    if (ex instanceof TokenFailure) {
                        // No need to display the stack trace of a token failure.
                        String msg = mTranslator.translate((TokenFailure)ex);
                        messageHandler.print(msg);
                        return;
                    }
                    // Display a one-line version of the error, and stash the
                    // whole thing away in ErrorWrapper for debugging
                    // commands.
                    new ErrorWrapper(ex);
                    messageHandler.print(ex.toString());
                }
            };

        // Create the SVG object.
        mGameViewport = new SVGCanvas(mGameTable, uiMainUrl, mTranslator,
            messageHandler, errorHandler);

        mGameViewport.addUpdateManagerListener(
            new UpdateManagerAdapter()
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
            });

        mUserColorMap = new UserColorMap();
        mUserColorMap.getUserNameColor(nickname); // Give user first color

        mTimeStampFormat = new SimpleDateFormat("HH:mm:ss");

        mSeatChart = new SeatChart(mGameTable, mUserColorMap, mTranslator,
            messageHandler);

        buildUI();

        setSize(500, 600);
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
                public void windowClosing(WindowEvent we)
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

        // We need a StatusListener to adjust button states when this player
        // stands, sits, etc.
        mGameTable.addStatusListener(new DefaultStatusListener() {
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
            });


        // Set up button actions.

        mInviteButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    doInviteDialog();
                }
            });

        mReadyButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        if (!mGameTable.isSelfReady()) {
                            mGameTable.getReferee().ready();
                        }
                        else {
                            mGameTable.getReferee().unready();
                        }
                    }
                    catch (TokenFailure ex) {
                        writeMessageText(mTranslator.translate(ex));
                    }
                    catch (Exception ex) {
                        new ErrorWrapper(ex);
                        writeMessageText(ex.toString());
                    }
                }
            });

        mSeatButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        if (!mGameTable.isSelfSeated()) {
                            mGameTable.getReferee().sit();
                        }
                        else {
                            mGameTable.getReferee().stand();
                        }
                    }
                    catch (TokenFailure ex) {
                        writeMessageText(mTranslator.translate(ex));
                    }
                    catch (Exception ex) {
                        new ErrorWrapper(ex);
                        writeMessageText(ex.toString());
                    }
                }
            });

        // Join the table, if we haven't already
        try
        {
            if (!mGameTable.isJoined()) {
                mGameTable.addReadyListener(new GameTable.ReadyListener() {
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
                    });
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
     * work (and it also checks to make sure it's never run before).
     */
    private void tryFinishInit()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (!mGameTableStarted || !mGameViewportStarted) 
        {
            // Need both operations finished.
            return;
        }
        if (mGameStartFinished) 
        {
            // Should only do this once.
            return;
        }
        mGameStartFinished = true;

        switchView(VIEW_GAME);
        mGameViewport.forceRedraw();

        // Remove loading component, since it's no longer needed
        ((CardLayout)mGameViewWrapper.getLayout()).removeLayoutComponent(
            mLoadingComponent);

        mLoadingComponent = null;

        Referee referee = mGameTable.getReferee();

        // Begin the flood of seating/config info.
        try
        {
            referee.send_state();
        }
        catch (TokenFailure ex) {
            writeMessageText(mTranslator.translate(ex));
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);
            writeMessageText(ex.toString());
        }

        /**
         * When we begin receiving RPCs from the referee, we don't necessarily
         * know what state the referee is in. (There's no status RPC for that.)
         * So we have to do a disco query.
         * 
         * ### This ought to be asynchronous. Blocking the Swing thread is bad
         * form.
         */
        try {
            ServiceDiscoveryManager discoMan = 
                ServiceDiscoveryManager.getInstanceFor(referee.getConnection());
            DiscoverInfo info = discoMan.discoverInfo(referee.getResponderJID());
            Form form = Form.getFormFrom(info);
            if (form != null) {
                FormField field = form.getField("state");
                if (field != null) {
                    String refState = (String) field.getValues().next();
                    mGameTable.setRefereeState(refState);
                }
            }
        }
        catch (XMPPException ex) {
            new ErrorWrapper(ex);
            writeMessageText(ex.toString());
        }
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
            mGameViewport.stop();
        }

        // Leave the chat room.
        if (mGameTable != null) {
            mGameTable.removeMessageListener(TableWindow.this);
            mGameTable.leave();
            mGameTable = null;
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
     * Gets the game token translator belonging to the table.
     *
     * @return   The TranslateToken belonging to the table.
     */
    public TranslateToken getTranslator()
    {
        return mTranslator;
    }

    /**
     * Look in a directory to see if it contains anything interesting, or if
     * it's just a wrapper around a subdirectory (and nothing else). In the
     * latter case, look into the subdirectory, and so on recursively.
     *
     * The first directory which is "interesting" (contains any files, or
     * contains more than one subdirectory) is the final result. This may be
     * the same as the directory that was passed in to begin with.
     *
     * This function is useful to search a directory created by unpacking a ZIP
     * file (or other archive). Some people create archives with the important
     * files at the top level; others create archives with everything important
     * wrapped in a folder. This function handles both -- or, indeed, any
     * number of wrappers -- and gives you back the directory in which to find
     * things.
     *
     * @param dir  the directory in which to search
     * @return     the directory which contains significant files
     */
    public static File locateTopDirectory(File dir)
    {
        while (true)
        {
            File[] entries = dir.listFiles();
            if (entries.length != 1)
            {
                break;
            }
            if (!(entries[0].isDirectory()))
            {
                break;
            }
            dir = entries[0];
        }

        return dir;
    }

    /**
     * Given a directory and a string, locate a directory entry which matches
     * the string, case-insensitively. More precisely: this looks for an entry
     * which matches name, name.toLowerCase(), or name.toUpperCase(). It will
     * not find arbitrary mixed-case entries.
     *
     * @param dir   the directory to search.
     * @param name  the file/dir name to search for.
     * @return      a File representing an existing file/dir; or null, if no entry
     *         was found.
     */
    public static File findFileCaseless(File dir, String name)
    {
        File res;
        String newname;

        res = new File(dir, name);
        if (res.exists())
        {
            return res;
        }

        newname = name.toUpperCase();
        if (!newname.equals(name))
        {
            res = new File(dir, newname);
            if (res.exists())
            {
                return res;
            }
        }

        newname = name.toLowerCase();
        if (!newname.equals(name))
        {
            res = new File(dir, newname);
            if (res.exists())
            {
                return res;
            }
        }

        return null;
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
            getWidth() - 100));
    }

    /**
     * PacketListener interface method implementation.
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
                    if (packet instanceof Message)
                    {
                        doMessageReceived((Message)packet);
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
            String nick = null;
            String addr = msg.getFrom();
            Date date = null;

            if (addr != null)
            {
                nick = StringUtils.parseResource(addr);
            }

            PacketExtension ext = msg.getExtension("x", "jabber:x:delay");
            if (ext != null && ext instanceof DelayInformation) 
            {
                date = ((DelayInformation)ext).getStamp();
            }

            writeMessageText(nick, msg.getBody(), date);
        }
    }

    /**
     * Appends the given message text to the message text area.
     *
     * @param nickname  The nickname of the user who sent the message.
     *                  If null or empty, it is assumed to have come from the
     *                  client or from the MultiUserChat itself.
     * @param message   The text of the message.
     * @param date      The timestamp of the message. If null, it is assumed
     *                  to be current.
     */
    private void writeMessageText(String nickname, String message, Date date)
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Append time stamp
        Color dateColor;
        if (date == null) {
            date = new Date();
            dateColor = colorCurrentTimestamp;
        }
        else {
            dateColor = colorDelayedTimestamp;
        }
        mMessageText.append("[" + mTimeStampFormat.format(date) + "]  ",
            dateColor);

        // Append received message
        boolean hasNick = ((nickname != null) && (!nickname.equals("")));

        String nickText = hasNick ? nickname + ":" : "***";

        Color nameColor =
            hasNick ? mUserColorMap.getUserNameColor(nickname) : Color.BLACK;
        Color textColor =
            hasNick ? mUserColorMap.getUserTextColor(nickname) : Color.BLACK;

        mMessageText.append(nickText + " ", nameColor);
        mMessageText.append(message + "\n", textColor);
    }

    /**
     * Appends the given message text to the message text area. The message is
     * assumed to be current.
     *
     * @param nickname  The nickname of the user who sent the message.
     *                  If null or empty, it is assumed to have come from the
     *                  client or from the MultiUserChat itself.
     * @param message   The text of the message.
     */
    private void writeMessageText(String nickname, String message)
    {
        writeMessageText(nickname, message, null);
    }

    /**
     * Appends the given message text to the message text area. The message is
     * assumed to be current, and to have come from the client or from the
     * MultiUserChat itself.
     *
     * @param message  The text of the message.
     */
    private void writeMessageText(String message)
    {
        writeMessageText(null, message);
    }

    /**
     * Bring up the game's info dialog box.
     */
    public void doInfoDialog() {
        if (mInfoDialog == null) {
            mInfoDialog = new InfoDialog(this, mGameTable,
                mServer.getGameInfo());

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
     * Bring up an invite dialog. There can be multiple of these at a time,
     * even for the same game.
     */
    public void doInviteDialog() {
        SendInvitationDialog box =
            new SendInvitationDialog(TableWindow.this, mGameTable);
        box.show();
    }

    /**
     * Send a request for a retainer bot.
     */
    public void doInviteBot() {
        try {
            mGameTable.getReferee().addBot();
        }
        catch (TokenFailure ex) {
            writeMessageText(mTranslator.translate(ex));
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);
            writeMessageText(ex.toString());
        }
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
        for (Iterator iter = jids.iterator(); iter.hasNext(); ) {
            final String jid = (String)iter.next();
            SwingWorker worker = new SwingWorker() {
                    public Object construct() {
                        try {
                            mGameTable.getReferee().invitePlayer(jid, msg);
                        }
                        catch (Exception ex) { };
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

        isGameActive = 
            (mGameTable.getRefereeState() == GameTable.STATE_ACTIVE);
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

        mMessageText = new LogTextPanel();
        mChatSplitter.setTopComponent(mMessageText);

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

        JComponent chart = mSeatChart.getComponent();
        mUserListSplitter.setRightComponent(new JScrollPane(chart));

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
