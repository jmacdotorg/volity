/*
 * JavolinApp.java
 *
 * Copyright 2004-2005 Karl von Laudermann
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
package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.*;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.ServiceDiscoveryFactory;
import org.jivesoftware.smackx.ServiceDiscoveryManager;

import org.volity.client.*;
import org.volity.javolin.chat.*;
import org.volity.javolin.game.*;
import org.volity.javolin.roster.*;

/**
 * The main application class of Javolin.
 */
public class JavolinApp extends JFrame 
    implements ActionListener, ConnectionListener,
               RosterPanelListener, InvitationListener
{
    private final static String APPNAME = "Javolin";
    private final static String APPVERSION = "0.2.8";

    private final static String NODENAME = "MainAppWin";

    private final static String ADDUSER_LABEL = "Add";
    private final static String DELETEUSER_LABEL = "Delete";
    private final static String CHAT_LABEL = "Chat";
    private final static ImageIcon CONNECTED_ICON;
    private final static ImageIcon DISCONNECTED_ICON;
    private final static ImageIcon ADDUSER_ICON;
    private final static ImageIcon DELETEUSER_ICON;
    private final static ImageIcon CHAT_ICON;

    private static URI sClientTypeUri = URI.create("http://volity.org/protocol/ui/svg");
    private static UIFileCache sUIFileCache = new UIFileCache(PlatformWrapper.isRunningOnMac());

    private static JavolinApp soleJavolinApp = null;
    private static TranslateToken sTranslator = new TranslateToken(null);

    protected static GameResourcePrefs sGameResourcePrefs;
    {
        sGameResourcePrefs = new GameResourcePrefs(sUIFileCache);
        org.volity.client.protocols.volresp.Handler.setResourcePrefs(sGameResourcePrefs);
    }

    private JButton mAddUserBut;
    private JButton mDelUserBut;
    private JButton mChatBut;

    private JPopupMenu mRosterContextMenu;
    private JMenuItem mChatContextItem;
    private JMenuItem mAddUserContextItem;
    private JMenuItem mDelUserContextItem;

    private RosterPanel mRosterPanel;
    private JLabel mConnectedLabel;

    private SizeAndPositionSaver mSizePosSaver;
    private XMPPConnection mConnection;
    List mMucWindows;
    List mTableWindows;
    List mChatWindows;
    List mDialogWindows;
    private Map mUserChatWinMap;

    private boolean mTryingConnection = false;
    private List mQueuedCommandStubs = new ArrayList();

    static
    {
        CONNECTED_ICON =
            new ImageIcon(JavolinApp.class.getResource("Connected_Icon.png"));
        DISCONNECTED_ICON =
            new ImageIcon(JavolinApp.class.getResource("Disconnected_Icon.png"));
        ADDUSER_ICON =
            new ImageIcon(JavolinApp.class.getResource("AddUser_ButIcon.png"));
        DELETEUSER_ICON = 
            new ImageIcon(JavolinApp.class.getResource("DeleteUser_ButIcon.png"));
        CHAT_ICON = new ImageIcon(JavolinApp.class.getResource("Chat_ButIcon.png"));
    }

    /**
     * Constructor.
     */
    public JavolinApp()
    {
        if (soleJavolinApp != null)
            throw new AssertionError("Cannot handle more than one JavolinApp.");
        soleJavolinApp = this;

        mMucWindows = new ArrayList();
        mTableWindows = new ArrayList();
        mChatWindows = new ArrayList();
        mDialogWindows = new ArrayList();
        mUserChatWinMap = new HashMap();

        PrefsDialog.loadPreferences();

        setTitle(APPNAME + " Roster");
        buildUI();

        setSize(200, 400);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        mSizePosSaver.restoreSizeAndPosition();

        if (PlatformWrapper.applicationMenuHandlersAvailable()) {
            PlatformWrapper.setApplicationMenuHandlers(
                new Runnable() {
                    public void run() { doAbout(); }
                },
                new Runnable() {
                    public void run() { doPreferences(); }
                },
                new Runnable() {
                    public void run() { doQuit(); }
                },
                new RunnableFile() {
                    public void run(File file) { doOpenFile(file); }
                });
        }

        updateToolBarButtons();

        // Add self as listener for RosterPanel events
        mRosterPanel.addRosterPanelListener(this);

        // Handle closing the window to quit the app
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        addWindowListener(
            new WindowAdapter()
            {
                public void windowClosing(WindowEvent we)
                {
                    doQuit();
                }
            });

        // Save window size and position whenever it is moved or resized
        addComponentListener(
            new ComponentAdapter()
            {
                public void componentMoved(ComponentEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }

                public void componentResized(ComponentEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }
            });

        PrefsDialog.addListener(PrefsDialog.ROSTER_DISPLAY_OPTIONS,
            new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    if (ev.getSource() == PrefsDialog.ROSTERNOTIFYSUBSCRIPTIONS_KEY) {
                        updateSubscriptionPolicy();
                    }
                }
            });

        show();
    }

    /**
     * Performs tasks that should occur immediately after launch, but which don't seem
     * appropriate to put in the constructor.
     */
    private void start()
    {
        /* 
         * Set up the static information in ServiceDiscoveryManager. This will
         * be returned to disco queries. To work around a weird Smack
         * class-loading race, we ping the SmackConfiguration class first.
         */
        String ref = SmackConfiguration.getVersion();

        ServiceDiscoveryManager.setServiceDiscoveryFactory(
            new ServiceDiscoveryFactory() {
                public ServiceDiscoveryManager create(XMPPConnection connection) {
                    return new JServiceDiscoveryManager(connection);
                }
            }
            );

        ServiceDiscoveryManager.setIdentityName(APPNAME);
        ServiceDiscoveryManager.setIdentityType("player");
        Form discoForm = new Form("result");
        FormField fld = new FormField("volity-role");
        fld.addValue(CapPresenceFactory.VOLITY_ROLE_PLAYER);
        discoForm.addField(fld);
        ServiceDiscoveryManager.setIdentityExtension(discoForm);

        /* 
         * Set up a packet extension provider for capability (JEP-0115) tags.
         */
        ProviderManager.addExtensionProvider(
            CapPacketExtension.NAME, CapPacketExtension.NAMESPACE,
            new CapExtensionProvider());

        // Open the Game Finder window
        if (Finder.getFinderWanted())
            doGetFinder();

        // Bring up the initial "connect" dialog box.
        doConnect();
    }

    /**
     * The main program for the JavolinApp class.
     *
     * @param args  The command line arguments.
     */
    public static void main(String[] args)
    {
        /*
         * Make sure we can reach the handlers for our special Volity URLs.
         * (Content and protocol handlers are both in the same package -- 
         * there's no conflict.)
         */
        String val = System.getProperty("java.protocol.handler.pkgs");
        if (val == null)
            val = "org.volity.client.protocols";
        else
            val = val + "|org.volity.client.protocols";
        System.setProperty("java.protocol.handler.pkgs", val);

        val = System.getProperty("java.content.handler.pkgs");
        if (val == null)
            val = "org.volity.client.protocols";
        else
            val = val + "|org.volity.client.protocols";
        System.setProperty("java.content.handler.pkgs", val);

        // Set the look and feel
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e)
        {
        }

        /*
         * Set up something to catch all exceptions in the UI thread.
         *
         * This is a kludge. In Java 1.5, we should use
         * Thread.setDefaultUncaughtExceptionHandler.
         */
        System.setProperty("sun.awt.exception.handler",
            "org.volity.javolin.ErrorWrapper");

        /*
         * Set the HTTP User-Agent.
         */
        val = getAppName() + "/" + getAppVersion();
        System.setProperty("http.agent", val);

        // Set up system properties and the like.
        PlatformWrapper.mainInitialize();

        // See if someone has set an alternate bookkeeper
        String bookkeeperJid = System.getProperty("org.volity.bookkeeper");
        if (bookkeeperJid != null)
            Bookkeeper.setDefaultJid(bookkeeperJid);

        /* Invoke into the Swing thread to create the JavolinApp.
         *
         * We do this because all our UI code is sprinkled with asserts that
         * we're *in* the Swing thread. It's legal to *create* Swing components
         * from other threads, but we are conservative and do the creation in
         * the Swing thread. This lets us use tighter asserts.
         */
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
                    JavolinApp mainApp = new JavolinApp();
                    mainApp.start();
                }
            });
    }

    /**
     * Return the one and only JavolinApp object.
     */
    public static JavolinApp getSoleJavolinApp()
    {
        return soleJavolinApp;
    }

    /**
     * Gets the name of the application, suitable for display to the user in dialog box
     * titles and other appropriate places.
     *
     * @return   The name of the application.
     */
    public static String getAppName()
    {
        return APPNAME;
    }

    /**
     * Get the version of the application, suitable for display to the user.
     */
    public static String getAppVersion()
    {
        return APPVERSION;
    }

    /**
     * Gets the generic token translator (volity.* and literal.*
     * tokens only) belonging to the application.
     *
     * This really only exists to handle failures in parlor RPCs
     * (i.e., volity.new_table). Any failures that happen in the
     * context of a given table/referee should go to
     * TableWindow.getTranslator() instead.
     *
     * @return   The TranslateToken belonging to the application.
     */
    public static TranslateToken getTranslator()
    {
        return sTranslator;
    }
    
     /**
     * Gets the UIFileCache belonging to the application.
     *
     * @return   The UIFileCache belonging to the application.
     */
    public static UIFileCache getUIFileCache()
    {
        return sUIFileCache;
    }

    /**
     * Gets the URI indicating the client type.
     *
     * @return   The URI indicating the client type.
     */
    public static URI getClientTypeURI()
    {
        return sClientTypeUri;
    }

    public static GameResourcePrefs getGameResourcePrefs()
    {
        return sGameResourcePrefs;
    }

    /**
     * Tells whether Javolin is currently connected to a Volity server.
     *
     * @return   true if Javolin is currently connected to a Volity server, false
     * otherwise.
     */
    boolean isConnected()
    {
        return (mConnection != null) && mConnection.isConnected();
    }

    /**
     * ActionListener interface method implementation.
     *
     * @param e  The ActionEvent received.
     */
    public void actionPerformed(ActionEvent e)
    {
        if ((e.getSource() == mAddUserBut) || (e.getSource() == mAddUserContextItem))
        {
            doAddUserBut();
        }
        else if ((e.getSource() == mDelUserBut) || (e.getSource() == mDelUserContextItem))
        {
            doDeleteUserBut();
        }
        else if ((e.getSource() == mChatBut) || (e.getSource() == mChatContextItem))
        {
            doChatBut();
        }
    }

    /**
     * Handle the About menu item
     */
    void doAbout()
    {
        AboutBox box = AboutBox.getSoleAboutBox();
        box.show();        
    }

    /**
     * Handle the Preferences menu item
     */
    void doPreferences()
    {
        PrefsDialog box = PrefsDialog.getSolePrefsDialog(this);
        box.show();        
    }

    /**
     * Handle the Game Finder item (in the Windows menu). Create a Finder
     * window if there is none; display the one we've got if there is.
     */
    void doGetFinder()
    {
        Finder win = Finder.getSoleFinder(this);
        win.show();
    }

    /**
     * Handle the Connect/Disconnect menu item
     */
    void doConnectDisconnect()
    {
        if (isConnected())
        {
            if (confirmCloseTableWindows("Disconnect"))
            {

                doDisconnect();
            }
        }
        else
        {
            doConnect();
        }
    }

    /**
     * Brings up a ConnectDialog to establish a connection.
     */
    void doConnect()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Guard against more than one of these at a time.
        if (mTryingConnection)
            return;

        try {
            mTryingConnection = true;

            ConnectDialog connDlg = new ConnectDialog(this);
            connDlg.show();
            mConnection = connDlg.getConnection();
        }
        finally {
            mTryingConnection = false;
        }

        if (mConnection != null)
        {
            mConnection.addConnectionListener(this);
            updateSubscriptionPolicy();

            PacketFilter filter;
            PacketListener listener;

            // Listen for incoming chat messages
            listener = new PacketListener() {
                    public void processPacket(final Packet packet) {
                        // Invoke into the Swing thread.
                        SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    if (packet instanceof Message)
                                        receiveMessage((Message)packet);
                                }
                            });
                    }
                };
            filter = new MessageTypeFilter(Message.Type.CHAT);
            mConnection.addPacketListener(listener, filter);
            filter = new MessageTypeFilter(Message.Type.NORMAL);
            mConnection.addPacketListener(listener, filter);

            // Listen for incoming presence-subscription messages
            filter = new PacketFilter() {
                    public boolean accept(Packet packet) {
                        return (packet instanceof Presence);
                    }
                };
            listener = new PacketListener() {
                    public void processPacket(final Packet packet) {
                        // Invoke into the Swing thread.
                        SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    // Ignore MUC presence packets
                                    if (packet instanceof Presence
                                        && packet.getExtension("x", "http://jabber.org/protocol/muc#user") == null) {
                                        Presence pres = (Presence)packet;
                                        if (pres.getType() == Presence.Type.SUBSCRIBE) {
                                            receiveSubscribeRequest(pres);
                                        }
                                        else if (pres.getType() == Presence.Type.AVAILABLE) {
                                            Audio.playBuddyIn();
                                        }
                                        else if (pres.getType() == Presence.Type.UNAVAILABLE) {
                                            Audio.playBuddyOut();
                                        }
                                    }
                                }
                            });
                    }
                };
            mConnection.addPacketListener(listener, filter);
        }

        // Assign the roster to the RosterPanel
        Roster connRost = null;

        if (mConnection != null)
        {
            connRost = mConnection.getRoster();
        }

        mRosterPanel.setRoster(connRost);

        if (mConnection != null)
        {
            InvitationManager im = new InvitationManager(mConnection);
            im.addInvitationListener(this);
            im.start();
        }

        // Update the UI
        updateUI();

        if (mConnection != null)
        {
            /*
             * If we have any queued stubs, launch them now. But we pull a copy
             * of the queue, and then clear it, so that this can't get into an
             * infinite loop.
             *
             * As a safety measure, we chop the queue at eight stubs. We don't
             * want to flood the user with a million connections, if he forgot
             * to log in for hours at a time. 
             */
            List ls = new ArrayList(mQueuedCommandStubs);
            mQueuedCommandStubs.clear();

            for (int ix=0; ix<ls.size() && ix<8; ix++) {
                CommandStub stub = (CommandStub)ls.get(ix);
                doOpenFile(stub);
            }
        }
    }

    /**
     * If any game table windows are open, this method asks the user for confirmation of
     *  an action that would cause all table windows to be closed.
     *
     * @param action  The name of the action to take. It will appear in the message.
     * @return        true if the user has confirmed the action (or if the user was
     *  never asked since no table windows were open), false if the action should be
     *  cancled.
     */
    private boolean confirmCloseTableWindows(String action)
    {
        boolean retVal = true;
        int tableWinCount = mTableWindows.size();

        if (tableWinCount > 0)
        {
            String message;

            if (tableWinCount == 1)
            {
                message = "You still have a game window open. " + action + " anyway?";
            }
            else
            {
                message = "You still have " + tableWinCount + " game windows open. " +
                    action + " anyway?";
            }

            int result = JOptionPane.showConfirmDialog(this, message,
                getAppName() + ": Confirm " + action, JOptionPane.YES_NO_OPTION);

            retVal = (result == JOptionPane.YES_OPTION);
        }

        return retVal;
    }

    /**
     * Closes the current connection. This method can also be called to clean up the
     * application state after the connection has been closed or lost via some other
     * means.
     */
    void doDisconnect()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Close all Chat windows
        while (mChatWindows.size() > 0)
        {
            ChatWindow chatWin = (ChatWindow)mChatWindows.get(0);
            chatWin.dispose();
            mChatWindows.remove(chatWin);
        }

        // Close all MUC windows
        while (mMucWindows.size() > 0)
        {
            MUCWindow mucWin = (MUCWindow)mMucWindows.get(0);
            mucWin.dispose();
            mMucWindows.remove(mucWin);
        }

        // Close all table windows
        while (mTableWindows.size() > 0)
        {
            TableWindow tableWin = (TableWindow)mTableWindows.get(0);
            tableWin.dispose();
            mTableWindows.remove(tableWin);
        }

        // Close all BaseWindow windows
        while (mDialogWindows.size() > 0)
        {
            BaseWindow dialogWin = (BaseWindow)mDialogWindows.get(0);
            dialogWin.dispose();
            mDialogWindows.remove(dialogWin);
        }

        // Clear the Window menu
        JavolinMenuBar.notifyUpdateWindowMenu();

        // Close connection if open
        if (mConnection != null)
        {
            mConnection.close();
            mConnection = null;
        }

        // Clear the roster panel
        mRosterPanel.setRoster(null);

        // Update UI component states
        updateUI();
    }

    /**
     * Handler for the Quit menu item.
     */
    void doQuit()
    {
        if (confirmCloseTableWindows("Exit"))
        {
            doDisconnect();
            System.exit(0);
        }
    }

    /**
     * We sometimes need a nickname in a context where the player has not
     * entered one. This is a hack to pick something halfway reasonable.
     */
    String getDefaultNickname()
    {
        // Make a default nickname based on the user ID
        String defNick = mConnection.getUser();
        defNick = defNick.substring(0, defNick.indexOf('@'));

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NewTableAtDialog.NODENAME);

        return prefs.get(NewTableAtDialog.NICKNAME_KEY, defNick);
    }

    /**
     * Handler for OpenFile events, which occur when the user double-clicks on
     * a file in the Finder (or whatever). Currently, this only handles Volity
     * command stub files.
     */
    void doOpenFile(File file)
    {
        try {
            FileInputStream instr = new FileInputStream(file);
            Charset utf8 = Charset.forName("UTF-8");
            Reader reader = new BufferedReader(new InputStreamReader(instr, utf8));
            CommandStub stub = CommandStub.parse(reader);

            doOpenFile(stub);
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);
            JOptionPane.showMessageDialog(this,
                ex.toString(),
                getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handler for OpenFile events, which occur when the user double-clicks on
     * a file in the Finder (or whatever). Currently, this only handles Volity
     * command stub files.
     */
    void doOpenFile(CommandStub stub)
    {
        try {
            /* Make sure we're connected to Jabber. If we're not, try to
             * connect (if we're not in the middle of connecting already). We
             * also queue up the stub, so that it will be opened when we next
             * succeed in connecting. */
            if (!isConnected()) {
                mQueuedCommandStubs.add(stub);

                if (!mTryingConnection) {
                    // invoke a connection dialog.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                doConnect();
                            }
                        });
                }
                return;
            }

            if (stub.getCommand() == CommandStub.COMMAND_CREATE_TABLE) {
                MakeTableWindow maker = new MakeTableWindow(this,
                    mConnection, this);
                maker.newTable(stub.getJID(), getDefaultNickname(), null);
            }
            else if (stub.getCommand() == CommandStub.COMMAND_JOIN_TABLE) {
                MakeTableWindow maker = new MakeTableWindow(this,
                    mConnection, this);
                maker.joinTable(stub.getJID(), getDefaultNickname(), null);
            }
            else if (stub.getCommand() == CommandStub.COMMAND_JOIN_LOBBY) {
                MakeMUCWindow maker = new MakeMUCWindow(this,
                    mConnection, this);
                maker.joinMUC(stub.getJID(), getDefaultNickname(), null);
            }
            else {
                throw new Exception("Unknown Volity command stub: " + stub.toString());
            }
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);
            JOptionPane.showMessageDialog(this,
                ex.toString(),
                getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handler for the New Table At... menu item.
     */
    void doNewTableAt()
    {
        NewTableAtDialog newTableDlg = new NewTableAtDialog(this, mConnection);
        newTableDlg.show();
    }

    /**
     * Handler for the Join Table At... menu item.
     */
    void doJoinTableAt()
    {
        JoinTableAtDialog joinTableDlg = new JoinTableAtDialog(this, mConnection);
        joinTableDlg.show();
    }

    /**
     * Incorporates a newly created TableWindow properly into the system. This
     * method should be called any time a new TableWindow is created.
     *
     * This is called by MakeTableWindow, so it does not need to be called by
     * anyone else.
     *
     * @param tableWin  The newly created TableWindow.
     */
    public void handleNewTableWindow(TableWindow tableWin)
    {
        tableWin.show();
        mTableWindows.add(tableWin);
        JavolinMenuBar.notifyUpdateWindowMenu();
        
        // Remove the table window from the list and menu when it closes
        tableWin.addWindowListener(
            new WindowAdapter()
            {
                public void windowClosed(WindowEvent we)
                {
                    mTableWindows.remove(we.getWindow());
                    JavolinMenuBar.notifyUpdateWindowMenu();
                }
            });
    }

    /**
     * Handler for the Join Multi-user Chat... menu item.
     */
    void doJoinMuc()
    {
        JoinMUCDialog joinMucDlg = new JoinMUCDialog(this, mConnection);
        joinMucDlg.show();
    }

    public void handleNewMucWindow(MUCWindow mucWin)
    {
        if (mucWin == null)
            return;

        mucWin.show();
        mMucWindows.add(mucWin);
        JavolinMenuBar.notifyUpdateWindowMenu();

        // Remove the MUC window from the list and menu when it closes
        mucWin.addWindowListener(
            new WindowAdapter()
            {
                public void windowClosed(WindowEvent we)
                {
                    mMucWindows.remove(we.getWindow());
                    JavolinMenuBar.notifyUpdateWindowMenu();
                }
            });
    }

    /**
     * Handler for the Chat button.
     */
    private void doChatBut()
    {
        RosterTreeItem selItem = mRosterPanel.getSelectedRosterItem();

        if (selItem == null)
        {
            return;
        }

        chatWithUser(selItem.getId());
    }

    /**
     * Activates a chat session with the specified user. If a ChatWindow exists
     * for the user, it brings that window to the front. Otherwise, it creates
     * a new chat window for communicating with the specified user.
     *
     * @param userId  The Jabber ID of the user to chat with. (The resource
     * string is ignored.)
     */
    public ChatWindow chatWithUser(String userId)
    {
        return chatWithUser(userId, true);
    }

    /**
     * Activates a chat session with the specified user. If a ChatWindow exists
     * for the user, it brings that window to the front (if requested).
     * Otherwise, it creates a new chat window for communicating with the
     * specified user.
     *
     * @param userId  The Jabber ID of the user to chat with. (The resource
     * string is ignored.)
     * @param toFront If a window exists, move it to the front?
     */
    public ChatWindow chatWithUser(String userId, boolean toFront)
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        userId = StringUtils.parseBareAddress(userId);

        ChatWindow chatWin = (ChatWindow)mUserChatWinMap.get(userId);

        if (chatWin != null)
        {
            if (toFront)
                chatWin.toFront();
            return chatWin;
        }

        try
        {
            chatWin = new ChatWindow(mConnection, userId);

            chatWin.show();
            mChatWindows.add(chatWin);
            JavolinMenuBar.notifyUpdateWindowMenu();
            mUserChatWinMap.put(userId, chatWin);

            Audio.playThread();

            // Remove the chat window from the list, menu, and map when it closes
            chatWin.addWindowListener(
                new WindowAdapter()
                {
                    public void windowClosed(WindowEvent we)
                    {
                        ChatWindow win = (ChatWindow)we.getWindow();

                        mChatWindows.remove(win);
                        JavolinMenuBar.notifyUpdateWindowMenu();
                        mUserChatWinMap.remove(win.getRemoteUserId());
                    }
                });

            return chatWin;
        }
        catch (XMPPException ex)
        {
            new ErrorWrapper(ex);
            JOptionPane.showMessageDialog(this, 
                ex.toString(),
                getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /**
     * Handler for the Add User button.
     */
    private void doAddUserBut()
    {
        //### if we have a "from" user selected, prefill it into the dialog box
        AddUserDialog addUserDlg = new AddUserDialog(this, mConnection.getRoster());
        addUserDlg.show();
    }

    /**
     * Handler for the Delete User button.
     */
    private void doDeleteUserBut()
    {
        RosterTreeItem selUser = mRosterPanel.getSelectedRosterItem();

        if (selUser == null)
        {
            return;
        }

        // The user name string in the message box will be "Joe Blow (joe@volity.net)" or
        // "joe@volity.net" depending on whether the user has a nickname
        String userStr = selUser.getId();

        if (!selUser.getNickname().equals(""))
        {
            userStr = selUser.getNickname() + " (" + userStr + ")";
        }

        // Show confrimation dialog
        String message = "Delete " + userStr + " from your roster?";

        int result = JOptionPane.showConfirmDialog(this, message,
            JavolinApp.getAppName() + ": Confirm Delete User", JOptionPane.YES_NO_OPTION);

        // Delete user from roster
        if (result == JOptionPane.YES_OPTION)
        {
            Roster theRoster = mConnection.getRoster();
            RosterEntry entry = theRoster.getEntry(selUser.getId());

            if (entry != null)
            {
                try
                {
                    theRoster.removeEntry(entry);
                }
                catch (XMPPException ex)
                {
                    new ErrorWrapper(ex);
                    JOptionPane.showMessageDialog(this, ex.toString(),
                        getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    /**
     * Updates the state and appearance of all UI items that depend on program state.
     */
    private void updateUI()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Update connected/disconnected icon
        if (isConnected())
        {
            mConnectedLabel.setIcon(CONNECTED_ICON);
            mConnectedLabel.setToolTipText("Connected");
        }
        else
        {
            mConnectedLabel.setIcon(DISCONNECTED_ICON);
            mConnectedLabel.setToolTipText("Disconnected");
        }

        // Do toolbar buttons
        updateToolBarButtons();

        // Do menu items
        JavolinMenuBar.notifyUpdateItems();
    }

    /**
     * Updates the state and appearance of the toolbar buttons depending on the program
     * state.
     */
    private void updateToolBarButtons()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Enable/disable appropriate buttons
        RosterTreeItem selectedUser = mRosterPanel.getSelectedRosterItem();

        mAddUserBut.setEnabled(isConnected());
        mDelUserBut.setEnabled(selectedUser != null);
        mChatBut.setEnabled((selectedUser != null) && selectedUser.isAvailable());
    }

    /**
     * ConnectionListener interface method implementation. Does nothing.
     *
     * Called outside Swing thread!
     */
    public void connectionClosed()
    {
        // No need to invoke the Swing thread, because we don't do anything.
    }

    /**
     * ConnectionListener interface method implementation. Alerts the user that the
     * connection was lost.
     *
     * Called outside Swing thread!
     *
     * @param ex  The exception.
     */
    public void connectionClosedOnError(final Exception ex)
    {
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    JOptionPane.showMessageDialog(JavolinApp.this,
                        "Connection closed due to exception:\n" + ex.toString(),
                        getAppName() + ": Error",
                        JOptionPane.ERROR_MESSAGE);

                    doDisconnect();
                }
            });
    }

    /**
     * Return the list of MucWindow objects we have open.
     */ 
    public Iterator getMucWindows() {
        return mMucWindows.iterator();
    }

    /**
     * Return the list of TableWindow objects we have open.
     */ 
    public Iterator getTableWindows() {
        return mTableWindows.iterator();
    }

    /**
     * Return the RosterPanel. (Which is useful for the invitation mechanism.)
     */
    public RosterPanel getRosterPanel() 
    {
        return mRosterPanel;
    }

    /**
     * InvitationListener interface method implementation.
     *
     * Called outside Swing thread!
     *
     * @param invitation  The invitation that was received.
     */
    public void invitationReceived(final Invitation invitation)
    {
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    doInvitationReceived(invitation);
                }
            });
    }

    /**
     * Handle an invitation.
     *
     * @param invitation  The invitation that was received.
     */
    private void doInvitationReceived(Invitation invitation)
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Make sure the required fields are present.
        if (invitation.getPlayerJID() == null 
            || invitation.getTableJID() == null 
            || invitation.getRefereeJID() == null) {
            JOptionPane.showMessageDialog(this, 
                "Incomplete invitation received.",
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        GetInvitationDialog box = new GetInvitationDialog(this,
            mConnection, invitation);
        Audio.playInvited();
        box.show();
    }

    public void doShowLastError() {
        ErrorWrapper err = ErrorWrapper.getLastError();
        JFrame box = new ErrorDialog(err, true);
        box.show();
    }

    public void doClearCache() {
        if (mTableWindows.size() == 0) {
            sUIFileCache.clearCache(true);
        }
        else {
            sUIFileCache.clearCache(false);
        }
    }

    /**
     * RosterPanelListener interface method implementation. Updates the toolbar
     * buttons when the roster selection has changed.
     *
     * @param e  The RosterPanelEvent.
     */
    public void selectionChanged(RosterPanelEvent e)
    {
        updateToolBarButtons();
    }

    /**
     * RosterPanelListener interface method implementation. Starts chat if the double-
     * clicked user is available.
     *
     * @param e  The event received.
     */
    public void itemDoubleClicked(RosterPanelEvent e)
    {
        RosterTreeItem item = e.getRosterTreeItem();

        if (item.isAvailable())
        {
            chatWithUser(item.getId());
        }
    }

    /**
     * RosterPanelListener interface method implementation. Shows the appropriate context
     * menu.
     *
     * @param e  The event received.
     */
    public void contextMenuInvoked(RosterPanelEvent e)
    {
        // Don't show menu if menu is already visible
        if (mRosterContextMenu.isVisible())
        {
            return;
        }

        RosterTreeItem item = e.getRosterTreeItem();

        // Enable/disable appropriate items
        mChatContextItem.setEnabled((item != null) && item.isAvailable());
        mDelUserContextItem.setEnabled(item != null);

        mRosterContextMenu.show(mRosterPanel, e.getX(), e.getY());
    }

    /**
     * Handles incoming chat messages.
     */
    public void receiveMessage(Message message)
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        String remoteId = message.getFrom();

        // If there is not already a chat window for the current user,
        // create one and give it the message.
        ChatWindow chatWin = chatWithUser(remoteId, false);

        if (chatWin != null)
        {
            chatWin.processPacket(message);
        }
    }

    /**
     * Handles incoming requests-to-put-on-roster.
     */
    public void receiveSubscribeRequest(Presence presence) {
        if (!PrefsDialog.getRosterNotifySubscriptions()) {
            // Roster will autoaccept
            Audio.playBuddyIn();
            return;
        }

        Audio.playInvited();

        String jid = presence.getFrom();

        String[] options = { "Permit", "Permit and Add", "Refuse" };
        //### untether!
        int res = JOptionPane.showOptionDialog(this,
            "The user " + jid + "\n"
            +"wishes to add you to his or her buddy list.\n"
            +"Do you want to permit this? If so, do you also\n"
            +"want to add this user to yours?",
            getAppName() + ": Request",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null,
            options, options[0]);

        if (res == 0) {
            Presence response = mConnection.createPresence(Presence.Type.SUBSCRIBED);
            response.setTo(jid);
            mConnection.sendPacket(response);
        }
        else if (res == 2) {
            Presence response = mConnection.createPresence(Presence.Type.UNSUBSCRIBED);
            response.setTo(jid);
            mConnection.sendPacket(response);
        }
        else {
            Presence response = mConnection.createPresence(Presence.Type.SUBSCRIBED);
            response.setTo(jid);
            mConnection.sendPacket(response);
            try {
                mConnection.getRoster().createEntry(jid, 
                    StringUtils.parseResource(jid), null);
            }
            catch (XMPPException ex) {
                new ErrorWrapper(ex);
                JOptionPane.showMessageDialog(this,
                    ex.toString(),
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    protected void updateSubscriptionPolicy() {
        if (mConnection != null) {
            Roster roster = mConnection.getRoster();
            if (PrefsDialog.getRosterNotifySubscriptions())
                roster.setSubscriptionMode(Roster.SUBSCRIPTION_MANUAL);
            else
                roster.setSubscriptionMode(Roster.SUBSCRIPTION_ACCEPT_ALL);
        }
    }

    /**
     * Interface to call a function with a File argument.
     */
    public interface RunnableFile
    {
        public void run(File file);
    }


    /**
     * Creates the contextual menu for the roster.
     *
     * @return   The menu to display when the user right-clicks the roster.
     */
    private JPopupMenu createRosterContextMenu()
    {
        JPopupMenu retVal = new JPopupMenu();

        mChatContextItem = new JMenuItem("Chat");
        mChatContextItem.addActionListener(this);
        retVal.add(mChatContextItem);

        retVal.addSeparator();

        mAddUserContextItem = new JMenuItem("Add User...");
        mAddUserContextItem.addActionListener(this);
        retVal.add(mAddUserContextItem);

        mDelUserContextItem = new JMenuItem("Delete User");
        mDelUserContextItem.addActionListener(this);
        retVal.add(mDelUserContextItem);

        return retVal;
    }

    /**
     * Populates the frame with UI controls.
     */
    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());

        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        cPane.add(toolbar, BorderLayout.NORTH);

        mAddUserBut = new JButton(ADDUSER_LABEL, ADDUSER_ICON);
        mAddUserBut.setToolTipText("Add new user");
        mAddUserBut.addActionListener(this);
        toolbar.add(mAddUserBut);

        mDelUserBut = new JButton(DELETEUSER_LABEL, DELETEUSER_ICON);
        mDelUserBut.setToolTipText("Delete user");
        mDelUserBut.addActionListener(this);
        toolbar.add(mDelUserBut);

        mChatBut = new JButton(CHAT_LABEL, CHAT_ICON);
        mChatBut.setToolTipText("Chat with user");
        mChatBut.addActionListener(this);
        toolbar.add(mChatBut);

        // Create roster panel
        mRosterPanel = new RosterPanel();
        cPane.add(mRosterPanel, BorderLayout.CENTER);

        // Create bottom panel
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        mConnectedLabel = new JLabel(DISCONNECTED_ICON);
        mConnectedLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        mConnectedLabel.setToolTipText("Disconnected");
        panel.add(mConnectedLabel, BorderLayout.WEST);

        cPane.add(panel, BorderLayout.SOUTH);

        // Create menu bar
        JavolinMenuBar.applyPlatformMenuBar(this);

        // Create contextual menu for roster
        mRosterContextMenu = createRosterContextMenu();
    }
}
