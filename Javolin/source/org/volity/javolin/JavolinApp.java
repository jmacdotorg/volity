/*
 * JavolinApp.java
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
package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smackx.muc.*;
import org.volity.client.*;
import org.volity.jabber.*;
import org.volity.javolin.chat.*;
import org.volity.javolin.game.*;
import org.volity.javolin.roster.*;

/**
 * The main application class of Javolin.
 */
public class JavolinApp extends JFrame implements ActionListener, ConnectionListener
{
    private final static String APPNAME = "Javolin";
    private final static String NODENAME = "MainAppWin";

    private final static String MENUCMD_CONNECT = "Connect...";
    private final static String MENUCMD_DISCONNECT = "Disconnect";
    private final static String MENUCMD_QUIT = "Exit";
    private final static String MENUCMD_NEW_TABLE_AT = "New Table At...";
    private final static String MENUCMD_JOIN_MUC = "Join Multi-user Chat...";

    private static UIFileCache sUIFileCache = new UIFileCache();

    private ImageIcon mConnectedIcon;
    private ImageIcon mDisconnectedIcon;

    private WindowMenu mWindowMenu;
    private JMenuItem mConnectMenuItem;
    private JMenuItem mQuitMenuItem;
    private JMenuItem mNewTableAtMenuItem;
    private JMenuItem mJoinMucMenuItem;

    private RosterPanel mRosterPanel;
    private JLabel mConnectedLabel;

    private SizeAndPositionSaver mSizePosSaver;
    private XMPPConnection mConnection;
    private java.util.List mMucWindows;
    private java.util.List mTableWindows;

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

        mMucWindows = new Vector();
        mTableWindows = new Vector();

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
     * Gets the UIFileCache belonging to the application.
     *
     * @return   The UIFileCache belonging to the application.
     */
    public static UIFileCache getUIFileCache()
    {
        return sUIFileCache;
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
        }

        // Assign the roster to the RosterPanel
        Roster connRost = null;

        if (mConnection != null)
        {
            connRost = mConnection.getRoster();
        }

        mRosterPanel.setRoster(connRost);

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
                message = "There is a game table open. " + action + " anyway?";
            }
            else
            {
                message = "There are " + tableWinCount + " game tables open. " + action +
                    " anyway?";
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

        if (tableWin != null)
        {
            tableWin.show();
            mTableWindows.add(tableWin);
            mWindowMenu.add(tableWin);

            // Remove the table window from the list when it closes
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

            // Remove the MUC window from the list when it closes
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
     * Helper method for setUpMenus. Assigns a keyboard mnemonic to a menu or menu item,
     * but only if not running on the Mac platform.
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
            mConnectedLabel.setIcon(mConnectedIcon);
            mConnectedLabel.setToolTipText("Connected");
        }
        else
        {
            mConnectedLabel.setIcon(mDisconnectedIcon);
            mConnectedLabel.setToolTipText("Disconnected");
        }

        // Do menu items
        updateMenuItems();
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
     *  Creates and sets up the menus for the application.
     */
    private void setUpMenus()
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
     * Populates the frame with UI controls.
     */
    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());

        // Create roster panel
        mRosterPanel = new RosterPanel();
        cPane.add(mRosterPanel, BorderLayout.CENTER);

        // Create bottom panel
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        mConnectedIcon =
            new ImageIcon(JavolinApp.class.getResource("Connected_Icon.png"));
        mDisconnectedIcon =
            new ImageIcon(JavolinApp.class.getResource("Disconnected_Icon.png"));

        mConnectedLabel = new JLabel(mDisconnectedIcon);
        mConnectedLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        mConnectedLabel.setToolTipText("Disconnected");
        panel.add(mConnectedLabel, BorderLayout.WEST);

        cPane.add(panel, BorderLayout.SOUTH);

        // Create menu bar
        setUpMenus();
    }
}
