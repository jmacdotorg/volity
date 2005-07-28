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
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.border.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.*;

import org.volity.client.*;

import org.volity.javolin.chat.*;
import org.volity.javolin.game.*;
import org.volity.javolin.roster.*;

/**
 * The main application class of Javolin.
 */
public class JavolinApp extends JFrame implements ActionListener, ConnectionListener,
    RosterPanelListener, PacketListener, InvitationListener
{
    private final static String APPNAME = "Javolin";
    private final static String NODENAME = "MainAppWin";
    private final static String SHOW_OFFLINE_USERS_KEY = "ShowOfflineUsers";

    private final static String MENUCMD_CONNECT = "Connect...";
    private final static String MENUCMD_DISCONNECT = "Disconnect";
    private final static String MENUCMD_QUIT = "Exit";
    private final static String MENUCMD_NEW_TABLE_AT = "New Table At...";
    private final static String MENUCMD_JOIN_MUC = "Join Multi-user Chat...";

    private final static ImageIcon CONNECTED_ICON;
    private final static ImageIcon DISCONNECTED_ICON;
    private final static ImageIcon SHOW_UNAVAIL_ICON;
    private final static ImageIcon HIDE_UNAVAIL_ICON;

    private static URI sClientTypeUri = URI.create("http://volity.org/protocol/ui/svg");
    private static UIFileCache sUIFileCache = new UIFileCache(isRunningOnMac());

    private static TranslateToken sTranslator = new TranslateToken(null);

    private JButton mShowHideUnavailBut;
    private JButton mAddUserBut;
    private JButton mDelUserBut;
    private JButton mChatBut;

    private WindowMenu mWindowMenu;
    private JMenuItem mConnectMenuItem;
    private JMenuItem mQuitMenuItem;
    private JMenuItem mNewTableAtMenuItem;
    private JMenuItem mJoinMucMenuItem;

    private JPopupMenu mRosterContextMenu;
    private JMenuItem mChatContextItem;
    private JMenuItem mAddUserContextItem;
    private JMenuItem mDelUserContextItem;

    private RosterPanel mRosterPanel;
    private JLabel mConnectedLabel;

    private SizeAndPositionSaver mSizePosSaver;
    private XMPPConnection mConnection;
    private java.util.List mMucWindows;
    private java.util.List mTableWindows;
    private java.util.List mChatWindows;
    private Map mUserChatWinMap;
    private boolean mShowUnavailUsers;

    static
    {
        SHOW_UNAVAIL_ICON =
            new ImageIcon(JavolinApp.class.getResource("ShowUnavail_ButIcon.png"));
        HIDE_UNAVAIL_ICON =
            new ImageIcon(JavolinApp.class.getResource("HideUnavail_ButIcon.png"));
        CONNECTED_ICON =
            new ImageIcon(JavolinApp.class.getResource("Connected_Icon.png"));
        DISCONNECTED_ICON =
            new ImageIcon(JavolinApp.class.getResource("Disconnected_Icon.png"));
    }

    /**
     * Constructor.
     */
    public JavolinApp()
    {
        setTitle(APPNAME);
        buildUI();

        setSize(200, 400);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        mSizePosSaver.restoreSizeAndPosition();

        mMucWindows = new ArrayList();
        mTableWindows = new ArrayList();
        mChatWindows = new ArrayList();
        mUserChatWinMap = new Hashtable();

        // Set roster to show/hide unavailable users based on prefs
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        mShowUnavailUsers = prefs.getBoolean(SHOW_OFFLINE_USERS_KEY, true);
        mRosterPanel.setShowUnavailableUsers(mShowUnavailUsers);
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

        show();
    }

    /**
     * Performs tasks that should occur immediately after launch, but which don't seem
     * appropriate to put in the constructor.
     */
    private void start()
    {
        doConnect();
    }

    /**
     * The main program for the JavolinApp class.
     *
     * @param args  The command line arguments.
     */
    public static void main(String[] args)
    {
        // Set the look and feel
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e)
        {
        }

        // Set appropriate properties when running on Mac
        if (isRunningOnMac())
        {
            // Make .app and .pkg files non-traversable as directories in AWT file
            // choosers
            System.setProperty("com.apple.macos.use-file-dialog-packages", "true");
        }

        JavolinApp mainApp = new JavolinApp();
        mainApp.start();
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

    /**
     * Tells whether Javolin is running on a Mac platform.
     *
     * @return   true if Javolin is currently running on a Mac, false if running on
     * another platform.
     */
    private static boolean isRunningOnMac()
    {
        return (System.getProperty("mrj.version") != null); // Apple recommended test for Mac
    }

    /**
     * Tells whether Javolin is currently connected to a Volity server.
     *
     * @return   true if Javolin is currently connected to a Volity server, false
     * otherwise.
     */
    private boolean isConnected()
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
        if (e.getSource() == mConnectMenuItem)
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
        else if (e.getSource() == mQuitMenuItem)
        {
            doQuit();
        }
        else if (e.getSource() == mNewTableAtMenuItem)
        {
            doNewTableAt();
        }
        else if (e.getSource() == mJoinMucMenuItem)
        {
            doJoinMuc();
        }
        else if (e.getSource() == mShowHideUnavailBut)
        {
            doShowHideUnavailBut();
        }
        else if ((e.getSource() == mAddUserBut) || (e.getSource() == mAddUserContextItem))
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
     * Brings up a ConnectDialog to establish a connection.
     */
    private void doConnect()
    {
        ConnectDialog connDlg = new ConnectDialog(this);
        connDlg.show();
        mConnection = connDlg.getConnection();

        if (mConnection != null)
        {
            mConnection.addConnectionListener(this);

            // Listen for incoming chat messages
            PacketFilter filter = new MessageTypeFilter(Message.Type.CHAT);
            mConnection.addPacketListener(this, filter);
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
    private void doDisconnect()
    {
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

        // Clear the Window menu
        mWindowMenu.clear();

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
    private void doQuit()
    {
        if (confirmCloseTableWindows("Exit"))
        {
            doDisconnect();
            System.exit(0);
        }
    }

    /**
     * Handler for the New Table At... menu item.
     */
    private void doNewTableAt()
    {
        NewTableAtDialog newTableDlg = new NewTableAtDialog(this, mConnection);
        newTableDlg.show();
        TableWindow tableWin = newTableDlg.getTableWindow();

        handleNewTableWindow(tableWin);
    }

    /**
     * Incorporates a newly created TableWindow properly into the system. This method
     * should be called any time a new TableWindow is created.
     *
     * @param tableWin  The newly created TableWindow.
     */
    private void handleNewTableWindow(TableWindow tableWin)
    {
        if (tableWin != null)
        {
            tableWin.show();
            mTableWindows.add(tableWin);
            mWindowMenu.add(tableWin);

            // Remove the table window from the list and menu when it closes
            tableWin.addWindowListener(
                new WindowAdapter()
                {
                    public void windowClosed(WindowEvent we)
                    {
                        mTableWindows.remove(we.getWindow());
                        mWindowMenu.remove((JFrame)we.getWindow());
                    }
                });
        }
    }


    /**
     * Handler for the Join Multi-user Chat... menu item.
     */
    private void doJoinMuc()
    {
        JoinMUCDialog joinMucDlg = new JoinMUCDialog(this, mConnection);
        joinMucDlg.show();
        MUCWindow mucWin = joinMucDlg.getMUCWindow();

        if (mucWin != null)
        {
            mucWin.show();
            mMucWindows.add(mucWin);
            mWindowMenu.add(mucWin);

            // Remove the MUC window from the list and menu when it closes
            mucWin.addWindowListener(
                new WindowAdapter()
                {
                    public void windowClosed(WindowEvent we)
                    {
                        mMucWindows.remove(we.getWindow());
                        mWindowMenu.remove((JFrame)we.getWindow());
                    }
                });
        }
    }

    /**
     * Handler for the Chat button.
     */
    private void doChatBut()
    {
        UserTreeItem selItem = mRosterPanel.getSelectedUserItem();

        if (selItem == null)
        {
            return;
        }

        chatWithUser(selItem.getId());
    }

    /**
     * Activates a chat session with the specified user. If a ChatWindow exists for the
     * user, it brings that window to the front. Otherwise, it creates a new chat window
     * for communicating with the specified user.
     *
     * @param userId  The Jabber ID of the user to chat with.
     */
    private void chatWithUser(String userId)
    {
        ChatWindow chatWin = getChatWindowForUser(userId);

        if (chatWin != null)
        {
            chatWin.toFront();
        }
        else
        {

            try
            {
                chatWin = new ChatWindow(mConnection, userId);

                chatWin.show();
                mChatWindows.add(chatWin);
                mWindowMenu.add(chatWin);
                mUserChatWinMap.put(userId, chatWin);

                // Remove the chat window from the list, menu, and map when it closes
                chatWin.addWindowListener(
                    new WindowAdapter()
                    {
                        public void windowClosed(WindowEvent we)
                        {
                            ChatWindow win = (ChatWindow)we.getWindow();

                            mChatWindows.remove(win);
                            mWindowMenu.remove(win);
                            mUserChatWinMap.remove(win.getRemoteUserId());
                        }
                    });

            }
            catch (XMPPException ex)
            {
                JOptionPane.showMessageDialog(this, ex.toString(),
                    getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Handler for Show/Hide Unavailable Users button.
     */
    private void doShowHideUnavailBut()
    {
        mShowUnavailUsers = !mShowUnavailUsers;

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        prefs.putBoolean(SHOW_OFFLINE_USERS_KEY, mShowUnavailUsers);

        mRosterPanel.setShowUnavailableUsers(mShowUnavailUsers);
        updateToolBarButtons();
    }

    /**
     * Handler for the Add User button.
     */
    private void doAddUserBut()
    {
        AddUserDialog addUserDlg = new AddUserDialog(this, mConnection.getRoster());
        addUserDlg.show();
    }

    /**
     * Handler for the Delete User button.
     */
    private void doDeleteUserBut()
    {
        UserTreeItem selUser = mRosterPanel.getSelectedUserItem();

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
                theRoster.removeEntry(entry);
            }
        }
    }

    /**
     * Helper method for setUpAppMenus. Assigns a keyboard mnemonic to a menu or menu
     * item, but only if not running on the Mac platform.
     *
     * @param item  The menu or menu item to assign the mnemonic to
     * @param key   The keyboard mnemonic.
     */
    private void setPlatformMnemonic(JMenuItem item, int key)
    {
        if (!isRunningOnMac())
        {
            item.setMnemonic(key);
        }
    }

    /**
     * Updates the state and appearance of all UI items that depend on program state.
     */
    private void updateUI()
    {
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
        updateMenuItems();
    }

    /**
     * Updates the state and appearance of the toolbar buttons depending on the program
     * state.
     */
    private void updateToolBarButtons()
    {
        // Toggle icon and tool tip text of show/hide unavailable users button
        if (mShowUnavailUsers)
        {
            mShowHideUnavailBut.setIcon(HIDE_UNAVAIL_ICON);
            mShowHideUnavailBut.setToolTipText("Hide offline users");
        }
        else
        {
            mShowHideUnavailBut.setIcon(SHOW_UNAVAIL_ICON);
            mShowHideUnavailBut.setToolTipText("Show offline users");
        }

        // Enable/disable appropriate buttons
        UserTreeItem selectedUser = mRosterPanel.getSelectedUserItem();

        mAddUserBut.setEnabled(isConnected());
        mDelUserBut.setEnabled(selectedUser != null);
        mChatBut.setEnabled((selectedUser != null) && selectedUser.isAvailable());
    }

    /**
     * Updates the text or state of all dynamic menu items.
     */
    private void updateMenuItems()
    {
        int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        if (isConnected())
        {
            mConnectMenuItem.setText(MENUCMD_DISCONNECT);
            mConnectMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                keyMask));
            setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_D);
        }
        else
        {
            mConnectMenuItem.setText(MENUCMD_CONNECT);
            mConnectMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
                keyMask));
            setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_N);
        }

        mNewTableAtMenuItem.setEnabled(isConnected());
        mJoinMucMenuItem.setEnabled(isConnected());
    }

    /**
     * ConnectionListener interface method implementation. Does nothing.
     */
    public void connectionClosed()
    {
    }

    /**
     * ConnectionListener interface method implementation. Alerts the user that the
     * connection was lost.
     *
     * @param ex  The exception.
     */
    public void connectionClosedOnError(Exception ex)
    {
        JOptionPane.showMessageDialog(this, "Connection closed due to exception:\n" +
            ex.toString(), getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);

        doDisconnect();
    }

    /**
     * InvitationListener interface method implementation.
     *
     * @param invitation  The invitation that was received.
     */
    public void invitationReceived(Invitation invitation)
    {
        String text = invitation.getPlayerJID() + " has invited you to join a game.";
        String message = invitation.getMessage();
        if (message != null)
        {
            text = text + "\n\"" + message + "\"";
        }

        //      JOptionPane.showMessageDialog(this, text);
        Object[] options = {"Accept", "Decline", "Decline and chat"};
        int choice = JOptionPane.showOptionDialog(this, text, "Invitation",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options,
            options[0]);

        if (choice == 0)
        {
            // Join the table.
            String tableJID = invitation.getTableJID();
            String serverJID = invitation.getGameServerJID();
            GameServer server = new GameServer(mConnection, serverJID);
            GameTable table = new GameTable(mConnection, tableJID);
            try
            {
                TableWindow tableWindow =
                    TableWindow.makeTableWindow(server, table, mConnection.getUser());
                handleNewTableWindow(tableWindow);
            }
            catch (Exception e)
            {
                System.out.println("Invitation: something went wrong.");
            }
            // Show the resulting table window.
        }
        else if (choice == 1)
        {
            System.out.println("Declining an invite.");
        }
        else if (choice == 2)
        {
            System.out.println("Declining and invite and opening a chat.");
            chatWithUser(invitation.getPlayerJID());
        }
        else
        {
            System.out.println("Got bizarre dialog choice: " + choice);
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
        UserTreeItem item = e.getUserTreeItem();

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

        UserTreeItem item = e.getUserTreeItem();

        // Enable/disable appropriate items
        mChatContextItem.setEnabled((item != null) && item.isAvailable());
        mDelUserContextItem.setEnabled(item != null);

        mRosterContextMenu.show(mRosterPanel, e.getX(), e.getY());
    }

    /**
     * Gets the ChatWindow handling chatting with the specified user, if any.
     *
     * @param userId  The ID of the remote user.
     * @return        The ChatWindow being used to handle chat with the specified user,
     * or null if there isn't currently one.
     */
    private ChatWindow getChatWindowForUser(String userId)
    {
        return (ChatWindow)mUserChatWinMap.get(userId);
    }

    /**
     * PacketListener interface method implementation. Handles incoming chat messages.
     *
     * @param packet  The packet received.
     */
    public void processPacket(Packet packet)
    {
        if (packet instanceof Message)
        {
            Message message = (Message)packet;
            String remoteId = StringUtils.parseBareAddress(message.getFrom());

            // If there is not already a chat window for the current user, create one
            // and give it the message.
            ChatWindow chatWin = getChatWindowForUser(remoteId);

            if (chatWin == null)
            {
                chatWithUser(remoteId);
                chatWin = getChatWindowForUser(remoteId);
                chatWin.processPacket(message);
            }
        }
    }

    /**
     *  Creates and sets up the menus for the application.
     */
    private void setUpAppMenus()
    {
        // Platform independent accelerator key
        int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        // File menu
        JMenu fileMenu = new JMenu("File");
        setPlatformMnemonic(fileMenu, KeyEvent.VK_F);

        mConnectMenuItem = new JMenuItem(MENUCMD_CONNECT);
        mConnectMenuItem.addActionListener(this);
        mConnectMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, keyMask));
        setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_N);
        fileMenu.add(mConnectMenuItem);

        fileMenu.addSeparator();

        mQuitMenuItem = new JMenuItem(MENUCMD_QUIT);
        mQuitMenuItem.addActionListener(this);
        setPlatformMnemonic(mQuitMenuItem, KeyEvent.VK_X);
        fileMenu.add(mQuitMenuItem);

        // Chat menu
        JMenu chatMenu = new JMenu("Chat");
        setPlatformMnemonic(chatMenu, KeyEvent.VK_C);

        mJoinMucMenuItem = new JMenuItem(MENUCMD_JOIN_MUC);
        mJoinMucMenuItem.addActionListener(this);
        mJoinMucMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, keyMask));
        setPlatformMnemonic(mJoinMucMenuItem, KeyEvent.VK_J);
        chatMenu.add(mJoinMucMenuItem);

        // Game menu
        JMenu gameMenu = new JMenu("Game");
        setPlatformMnemonic(gameMenu, KeyEvent.VK_G);

        mNewTableAtMenuItem = new JMenuItem(MENUCMD_NEW_TABLE_AT);
        mNewTableAtMenuItem.addActionListener(this);
        mNewTableAtMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,
            keyMask));
        setPlatformMnemonic(mNewTableAtMenuItem, KeyEvent.VK_E);
        gameMenu.add(mNewTableAtMenuItem);

        // Window menu
        mWindowMenu = new WindowMenu();

        // Create menu bar
        JMenuBar theMenuBar = new JMenuBar();
        theMenuBar.add(fileMenu);
        theMenuBar.add(chatMenu);
        theMenuBar.add(gameMenu);
        theMenuBar.add(mWindowMenu);
        setJMenuBar(theMenuBar);
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

        mShowHideUnavailBut = new JButton(HIDE_UNAVAIL_ICON);
        mShowHideUnavailBut.addActionListener(this);
        toolbar.add(mShowHideUnavailBut);

        ImageIcon image =
            new ImageIcon(getClass().getResource("AddUser_ButIcon.png"));
        mAddUserBut = new JButton(image);
        mAddUserBut.setToolTipText("Add user");
        mAddUserBut.addActionListener(this);
        toolbar.add(mAddUserBut);

        image = new ImageIcon(getClass().getResource("DeleteUser_ButIcon.png"));
        mDelUserBut = new JButton(image);
        mDelUserBut.setToolTipText("Delete user");
        mDelUserBut.addActionListener(this);
        toolbar.add(mDelUserBut);

        image = new ImageIcon(getClass().getResource("Chat_ButIcon.png"));
        mChatBut = new JButton(image);
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
        setUpAppMenus();

        // Create contextual menu for roster
        mRosterContextMenu = createRosterContextMenu();
    }
}
