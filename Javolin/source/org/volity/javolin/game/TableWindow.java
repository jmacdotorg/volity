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
import java.util.prefs.*;
import java.util.zip.ZipException;
import javax.swing.*;
import javax.swing.text.*;

import org.apache.batik.bridge.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.*;
import org.jivesoftware.smackx.packet.MUCUser;

import org.volity.client.*;
import org.volity.jabber.*;
import org.volity.javolin.*;
import org.volity.javolin.chat.*;

/**
 * A window for playing a game.
 */
public class TableWindow extends JFrame implements PacketListener, StatusListener
{
    private final static String NODENAME = "TableWindow";
    private final static String CHAT_SPLIT_POS = "ChatSplitPos";
    private final static String USERLIST_SPLIT_POS = "UserListSplitPos";
    private final static String BOARD_SPLIT_POS = "BoardSplitPos";

    private final static String VIEW_GAME = "GameViewport";
    private final static String VIEW_LOADING = "LoadingMessage";

    private static Map sLocalUiFileMap = new HashMap();

    private JSplitPane mChatSplitter;
    private JSplitPane mUserListSplitter;
    private JSplitPane mBoardSplitter;
    private LogTextPanel mMessageText;
    private JTextArea mInputText;
    private JTextPane mUserListText;
    private SVGCanvas mGameViewport;
    private JPanel mGameViewWrapper;
    private JComponent mLoadingComponent;
    private AbstractAction mSendMessageAction;

    private UserColorMap mUserColorMap;
    private SimpleDateFormat mTimeStampFormat;
    private SimpleAttributeSet mBaseUserListStyle;

    private SizeAndPositionSaver mSizePosSaver;
    private GameTable mGameTable;
    private String mNickname;
    private TranslateToken mTranslator;

    private boolean mGameTableStarted = false;
    private boolean mGameViewportStarted = false;
    private boolean mGameStartFinished = false;


    /**
     * Factory method for a TableWindow. This form will create a new table.
     *
     * @param connection                 The current active XMPPConnection
     * @param serverId                   The ID of the game table to create and join.
     * @param nickname                   The nickname to use to join the table.
     * @return                           A new TableWindow.
     * @exception XMPPException          If the table could not be joined.
     * @exception RPCException           If a new table could not be created.
     * @exception IOException            If a UI file could not be downloaded.
     * @exception MalformedURLException  If an invalid UI file URL was used.
     * @exception TokenFailure           If a new_table RPC failed.
     */
    public static TableWindow makeTableWindow(XMPPConnection connection, String serverId,
        String nickname) throws XMPPException, RPCException, IOException,
        TokenFailure, MalformedURLException
    {
        GameServer server = new GameServer(connection, serverId);
        GameTable table = server.newTable();

        return makeTableWindow(connection, server, table, nickname);
    }

    /**
     * Factory method for a TableWindow. This form will join an existing table.
     *
     * @param connection                 The current active XMPPConnection
     * @param server                     The GameServer.
     * @param table                      The GameTable to join.
     * @param nickname                   The nickname to use to join the table.
     * @return                           A new TableWindow, or null if one could not be
     *  created.
     * @exception XMPPException          If the table could not be joined.
     * @exception RPCException           If a new table could not be created.
     * @exception IOException            If a UI file could not be downloaded.
     * @exception ZipException           If a UI file could not be unpacked.
     * @exception MalformedURLException  If an invalid UI file URL was used.
     * @exception TokenFailure           If a new_table RPC failed.
     */
    public static TableWindow makeTableWindow(XMPPConnection connection, 
        GameServer server,
        GameTable table, 
        String nickname) throws XMPPException, RPCException,
        IOException, TokenFailure, MalformedURLException, ZipException
    {
        TableWindow retVal = null;

        URL uiUrl = getUIURL(server);

        if (uiUrl == null)
        {
            return null;
        }

        File dir = JavolinApp.getUIFileCache().getUIDir(uiUrl);
        retVal = new TableWindow(server, table, nickname, dir);

        return retVal;
    }

    /**
     * Constructor.
     *
     * @param server                     The GameServer
     * @param table                      The GameTable to join. If null,
     *        a new table will be created.
     * @param nickname                   The nickname to use to join the table.
     * @param uiDir                      The UI directory.
     * @exception XMPPException          If the table could not be joined.
     * @exception RPCException           If a new table could not be created.
     * @exception TokenFailure           If a new_table RPC failed.
     * @exception IOException            If a UI file could not be downloaded.
     * @exception MalformedURLException  If an invalid UI file URL was used.
     */
    protected TableWindow(GameServer server, GameTable table, String nickname,
        File uiDir) throws XMPPException, RPCException, IOException, TokenFailure,
        MalformedURLException
    {
        mGameTable = table;
        mNickname = nickname;

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

        if (mGameTable == null)
        {
            mGameTable = server.newTable();
        }

        setTitle(JavolinApp.getAppName() + ": " + mGameTable.getRoom());

        // Create the SVG object. The third argument is an anonymous
        // TokenTranslationHandler subclass which knows how to print
        // failure messages to the message pane.
        mGameViewport = new SVGCanvas(mGameTable, uiMainUrl, mTranslator,
            new GameUI.MessageHandler()
            {
                public void print(String msg)
                {
                    writeMessageText(msg);
                }
            });

        mGameViewport.addUpdateManagerListener(
            new UpdateManagerAdapter()
            {
                public void managerStarted(UpdateManagerEvent evt)
                {
                    mGameViewportStarted = true;
                    tryFinishInit();
                }
            });

        mUserColorMap = new UserColorMap();
        mUserColorMap.getUserNameColor(nickname); // Give user first color

        mTimeStampFormat = new SimpleDateFormat("HH:mm:ss");

        mBaseUserListStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(mBaseUserListStyle, "SansSerif");
        StyleConstants.setFontSize(mBaseUserListStyle, 12);

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
                    // Leave the chat room when the window is closed
                    saveWindowState();
                    mGameTable.leave();
                }

                public void windowOpened(WindowEvent we)
                {
                    // Give focus to input text area when the window is created
                    mInputText.requestFocusInWindow();
                }
            });

        // Register as message listener.
        mGameTable.addMessageListener(this);

        // Register as participant listener.
        mGameTable.addParticipantListener(this);

        // Register as a player-status listener.
        mGameTable.addStatusListener(this);

        // Join the table, if we haven't already
        try
        {
            if (!mGameTable.isJoined()) {
                mGameTable.addReadyListener(new GameTable.ReadyListener() {
                        public void ready() {
                            mGameTableStarted = true;
                            tryFinishInit();
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
            JOptionPane.showMessageDialog(this,
                "Cannot join table:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
            //XXX We should really kill the table window in this case.
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

        // Begin the flood of seating/config info.
        try
        {
            mGameTable.getReferee().send_state();
        }
        catch (TokenFailure ex) {
            writeMessageText(mTranslator.translate(ex));
        }
        catch (Exception ex) {
            writeMessageText(ex.toString());
        }
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
     * Helper method for makeTableWindow. Returns the URL for the given game server's
     * UI.
     *
     * @param server                     The game server for which to retrieve the UI URL.
     * @return                           The URL for the game UI, or null if none was
     *  available.
     * @exception XMPPException          If an XMPP error occurs.
     * @exception MalformedURLException  If an error ocurred creating a URL for a local
     *  file.
     */
    private static URL getUIURL(GameServer server) throws XMPPException,
        MalformedURLException
    {
        URL retVal = null;

        Bookkeeper keeper = new Bookkeeper(server.getConnection());
        java.util.List uiList = keeper.getCompatibleGameUIs(server.getRuleset(),
            JavolinApp.getClientTypeURI());

        if (uiList.size() != 0)
        {
            // Use first UI info object
            GameUIInfo info = (GameUIInfo)uiList.get(0);
            retVal = info.getLocation();
        }
        else
        {
            if (sLocalUiFileMap.containsKey(server.getRuleset()))
            {
                retVal = (URL)sLocalUiFileMap.get(server.getRuleset());
            }
            else if (JOptionPane.showConfirmDialog(null,
                "No UI file is known for this game.\nChoose a local file?",
                JavolinApp.getAppName(), JOptionPane.YES_NO_OPTION) ==
                JOptionPane.YES_OPTION)
            {
                JFileChooser fDlg = new JFileChooser();
                int val = fDlg.showOpenDialog(null);

                if (val == JFileChooser.APPROVE_OPTION)
                {
                    retVal = fDlg.getSelectedFile().toURI().toURL();
                    sLocalUiFileMap.put(server.getRuleset(), retVal);
                }
            }
        }

        return retVal;
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
     * Updates the list of users in the MUC.
     */
    private void updateUserList()
    {
        mUserListText.setText("");
        Iterator iter = mGameTable.getOccupants();

        while (iter.hasNext())
        {
            String userName = StringUtils.parseResource(iter.next().toString());

            SimpleAttributeSet style = new SimpleAttributeSet(mBaseUserListStyle);
            StyleConstants.setForeground(style,
                mUserColorMap.getUserNameColor(userName));

            Document doc = mUserListText.getDocument();

            try
            {
                doc.insertString(doc.getLength(), userName + "\n", style);
            }
            catch (BadLocationException ex)
            {
            }
        }
    }

    /**
     * PacketListener interface method implementation.
     *
     * @param packet  The packet received.
     */
    public void processPacket(Packet packet)
    {
        if (packet instanceof Message)
        {
            doMessageReceived((Message)packet);
        }
        else if (packet instanceof Presence)
        {
            updateUserList();
        }
    }

    /**
     * Sends the message that the user typed in the input text area.
     */
    private void doSendMessage()
    {
        try
        {
            mGameTable.sendMessage(mInputText.getText());
            mInputText.setText("");
        }
        catch (XMPPException ex)
        {
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

            if (addr != null)
            {
                nick = StringUtils.parseResource(addr);
            }

            writeMessageText(nick, msg.getBody());
        }
    }

    /**
     * Appends the given message text to the message text area.
     *
     * @param nickname  The nickname of the user who sent the message.
     *                  If null or empty, it is assumed to have come from the
     *                  client or from the MultiUserChat itself.
     * @param message   The text of the message.
     */
    private void writeMessageText(String nickname, String message)
    {
        // Append time stamp
        Date now = new Date();
        mMessageText.append("[" + mTimeStampFormat.format(now) + "] ", Color.BLACK);

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
     * assumed to have come from the client or from the MultiUserChat itself.
     *
     * @param message  The text of the message.
     */
    private void writeMessageText(String message)
    {
        writeMessageText(null, message);
    }

    /**
     * StatusListener interface method implementation.
     *
     * @param jid  ID of the user whose status changed.
     */

    public void playerBecameReady(String jid)
    {
        System.out.println("I am the table window, and a player became ready!!");
    }

    /**
     * StatusListener interface method implementation.
     *
     * @param jid  ID of the user whose status changed.
     */
    public void playerBecameUnready(String jid)
    {
        System.out.println("I am the table window, and a player became unready!!");
    }

    /**
     * StatusListener interface method implementation.
     *
     * @param jid  ID of the user whose status changed.
     */
    public void playerStood(String jid)
    {
        System.out.println("I am the table window, and a player stood up!!");
    }

    /**
     * StatusListener interface method implementation.
     *
     * @param jid     ID of the user whose status changed.
     * @param seatId  Seat ID indicating where the user sat.
     */
    public void playerSat(String jid, String seatId)
    {
        System.out.println("I am the table window, and a player sat down!!");
    }

    /**
     * Switches the mGameViewWrapper to show either the loading message or the game view.
     *
     * @param viewStr  The selector for the view, either VIEW_LOADING or VIEW_GAME.
     */
    private void switchView(String viewStr)
    {
        ((CardLayout)mGameViewWrapper.getLayout()).show(mGameViewWrapper, viewStr);
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

        mUserListText = new JTextPane();
        mUserListText.setEditable(false);
        mUserListSplitter.setRightComponent(new JScrollPane(mUserListText));

        cPane.add(mUserListSplitter, BorderLayout.CENTER);
    }
}
