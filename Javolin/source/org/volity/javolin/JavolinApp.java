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
import javax.swing.*;
import javax.swing.border.*;
import org.jivesoftware.smack.*;
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

    private ImageIcon mConnectedIcon;
    private ImageIcon mDisconnectedIcon;

    private RosterPanel mRosterPanel;
    private JLabel mConnectedLabel;
    private JMenuItem mConnectMenuItem;

    private SizeAndPositionSaver mSizePosSaver;
    private XMPPConnection mConnection;

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
        if (e.getActionCommand() == MENUCMD_CONNECT)
        {
            doConnect();
        }
        else if (e.getActionCommand() == MENUCMD_DISCONNECT)
        {
            doDisconnect();
        }
        else if (e.getActionCommand() == MENUCMD_QUIT)
        {
            doQuit();
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
     * Closes the current connection.
     */
    private void doDisconnect()
    {
        // Sanity check
        if (!isConnected())
        {
            return;
        }

        mConnection.close();
        mConnection = null;

        mRosterPanel.setRoster(null);

        updateUI();
    }

    /**
     * Handler for the Quit menu item.
     */
    private void doQuit()
    {
        doDisconnect();
        System.exit(0);
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

        mConnection = null;
        mRosterPanel.setRoster(null);
        updateUI();

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

        JMenuItem menuItem = new JMenuItem(MENUCMD_QUIT);
        menuItem.addActionListener(this);
        setPlatformMnemonic(menuItem, KeyEvent.VK_X);
        fileMenu.add(menuItem);

        // Create menu bar
        JMenuBar theMenuBar = new JMenuBar();
        theMenuBar.add(fileMenu);
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
