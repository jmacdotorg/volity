/*
 * JavolinApp.java
 * Source code Copyright 2004 by Karl von Laudermann
 */
package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import org.jivesoftware.smack.*;

/**
 * The main application class of Javolin.
 */
public class JavolinApp extends JFrame implements ActionListener
{
    private final static String APPNAME = "Javolin";

    private final static String MENUCMD_CONNECT = "Connect...";
    private final static String MENUCMD_DISCONNECT = "Disconnect";
    private final static String MENUCMD_QUIT = "Exit";

    private JMenuItem mConnectMenuItem;

    private XMPPConnection mConnection;

    /**
     * Constructor.
     */
    public JavolinApp()
    {
        setTitle(APPNAME);
        setUpMenus();

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

        setSize(300, 300);
//        pack();
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

            // Make the menu bar appear at the top of the screen instead of in the window
            // (Let's reenable this later when we can work out a way to put the same menu
            // bar on all windows and keep them in sync, so that the same menu bar will
            // appear regardless of what window is active. We need to make sure that the
            // same menu item can be enabled/disabled on all menu bars simultaneously, as
            // well as make sure that invoking the same item on all bars activates the
            // same handler.)
            //System.setProperty("com.apple.macos.useScreenMenuBar", "true");
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
        return (mConnection != null);
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
        updateMenuItems();
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

        updateMenuItems();
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
//        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, keyMask));
        setPlatformMnemonic(menuItem, KeyEvent.VK_X);
        fileMenu.add(menuItem);

        // Create menu bar
        JMenuBar theMenuBar = new JMenuBar();
        theMenuBar.add(fileMenu);
        setJMenuBar(theMenuBar);
    }
}
