package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

import org.volity.javolin.game.TableWindow;

/**
 * This class is a factory for Javolin menu bars. (They're mostly identical.)
 * Every nonmodal window needs to call applyPlatformMenuBar().
 *
 * The applyPlatformMenuBar() entry point is polymorphic on the window type; it
 * generates a menu bar with the appropriate customizations. (For example, a
 * table window has more items enabled in the "game" menu.)
 * 
 * When appropriate, applyPlatformMenuBar() also checks to see if we're on a
 * Mac. On Macs, *every* window needs a menu bar. (This is because of the way
 * Swing handles the Mac "menus at the top of the screen" rule.) So for windows
 * like "About", which normally wouldn't need menu bars, applyPlatformMenuBar()
 * checks and generates one if-and-only-if we're on a Mac.
 *
 * When JavolinApp changes state, it notifies this class, which updates all the
 * menu bar instances in parallel.
 */
public class JavolinMenuBar extends JMenuBar
    implements ActionListener
{
    private static List menuBarList = new ArrayList();

    private final static String MENUCMD_ABOUT = "About Javolin...";
    private final static String MENUCMD_CONNECT = "Connect...";
    private final static String MENUCMD_DISCONNECT = "Disconnect";
    private final static String MENUCMD_QUIT = "Exit";
    private final static String MENUCMD_NEW_TABLE_AT = "New Table At...";
    private final static String MENUCMD_JOIN_TABLE_AT = "Join Table At...";
    private final static String MENUCMD_GAME_INFO = "Game Info...";
    private final static String MENUCMD_INVITE_PLAYER = "Invite Player...";
    private final static String MENUCMD_INVITE_BOT = "Request Bot";
    private final static String MENUCMD_JOIN_MUC = "Join Multi-user Chat...";
    private final static String MENUCMD_SHOW_LAST_ERROR = "Display Last Error...";

    private JavolinApp mApplication = null;
    private TableWindow mTableWindow = null;

    private WindowMenu mWindowMenu;
    private JMenuItem mAboutMenuItem;
    private JMenuItem mConnectMenuItem;
    private JMenuItem mQuitMenuItem;
    private JMenuItem mNewTableAtMenuItem;
    private JMenuItem mJoinTableAtMenuItem;
    private JMenuItem mJoinMucMenuItem;
    private JMenuItem mShowLastErrorMenuItem;
    private JMenuItem mGameInfoMenuItem;
    private JMenuItem mInvitePlayerMenuItem;
    private JMenuItem mInviteBotMenuItem;

    /**
     * Construct a menu bar and attach it to the given window.
     *
     * Do not call this directly. Instead, call applyPlatformMenuBar(win).
     */
    protected JavolinMenuBar(JFrame win) {
        mApplication = JavolinApp.getSoleJavolinApp();
        if (mApplication == null)
            throw new AssertionError("No JavolinApp has been set yet.");

        /* Certain windows have extra items. Our flag for whether we're
         * attached to, say, a TableWindow is whether mTableWindow is non-null.
         * (Easier to read than a constant stream of instanceof tests!)
         */
        if (win instanceof TableWindow)
            mTableWindow = (TableWindow)win;

        // Add to the notification list.
        menuBarList.add(this);

        setUpAppMenus();

        win.setJMenuBar(this);

        win.addWindowListener(
            new WindowAdapter() {
                public void windowClosing(WindowEvent ev) {
                    // When the window closes, remove its menu bar from the
                    // notification list.
                    menuBarList.remove(JavolinMenuBar.this);
                }
            });
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

        if (!PlatformWrapper.applicationMenuHandlersAvailable()) {
            // Only needed if there isn't a built-in About menu

            mAboutMenuItem = new JMenuItem(MENUCMD_ABOUT);
            mAboutMenuItem.addActionListener(this);
            setPlatformMnemonic(mAboutMenuItem, KeyEvent.VK_A);
            fileMenu.add(mAboutMenuItem);

            fileMenu.addSeparator();
        }

        mConnectMenuItem = new JMenuItem(MENUCMD_CONNECT);
        mConnectMenuItem.addActionListener(this);
        mConnectMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, keyMask));
        setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_N);
        fileMenu.add(mConnectMenuItem);

        fileMenu.addSeparator();

        // This one is for debugging, and should be removed for a final
        // release. Well, hidden, anyway.
        mShowLastErrorMenuItem = new JMenuItem(MENUCMD_SHOW_LAST_ERROR);
        mShowLastErrorMenuItem.addActionListener(this);
        setPlatformMnemonic(mShowLastErrorMenuItem, KeyEvent.VK_S);
        fileMenu.add(mShowLastErrorMenuItem);

        if (!PlatformWrapper.applicationMenuHandlersAvailable()) {
            // Only needed if there isn't a built-in Quit menu

            fileMenu.addSeparator();

            mQuitMenuItem = new JMenuItem(MENUCMD_QUIT);
            mQuitMenuItem.addActionListener(this);
            setPlatformMnemonic(mQuitMenuItem, KeyEvent.VK_X);
            fileMenu.add(mQuitMenuItem);
        }

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

        mJoinTableAtMenuItem = new JMenuItem(MENUCMD_JOIN_TABLE_AT);
        mJoinTableAtMenuItem.addActionListener(this);
        mJoinTableAtMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
            keyMask));
        setPlatformMnemonic(mJoinTableAtMenuItem, KeyEvent.VK_T);
        gameMenu.add(mJoinTableAtMenuItem);

        gameMenu.addSeparator();
        
        mGameInfoMenuItem = new JMenuItem(MENUCMD_GAME_INFO);
        mGameInfoMenuItem.addActionListener(this);
        setPlatformMnemonic(mGameInfoMenuItem, KeyEvent.VK_I);
        if (mTableWindow == null) 
            mGameInfoMenuItem.setEnabled(false);
        gameMenu.add(mGameInfoMenuItem);

        mInvitePlayerMenuItem = new JMenuItem(MENUCMD_INVITE_PLAYER);
        mInvitePlayerMenuItem.addActionListener(this);
        setPlatformMnemonic(mInvitePlayerMenuItem, KeyEvent.VK_P);
        if (mTableWindow == null) 
            mInvitePlayerMenuItem.setEnabled(false);
        gameMenu.add(mInvitePlayerMenuItem);

        mInviteBotMenuItem = new JMenuItem(MENUCMD_INVITE_BOT);
        mInviteBotMenuItem.addActionListener(this);
        setPlatformMnemonic(mInviteBotMenuItem, KeyEvent.VK_B);
        if (mTableWindow == null) 
            mInviteBotMenuItem.setEnabled(false);
        gameMenu.add(mInviteBotMenuItem);

        // Window menu
        mWindowMenu = new WindowMenu();

        add(fileMenu);
        add(chatMenu);
        add(gameMenu);
        add(mWindowMenu);

        // Update everything to the current app state
        updateMenuItems();
        updateWindowMenu();
    }

    /**
     * Updates the text or state of all dynamic menu items. (Except WindowMenu
     * -- that's handled in updateWindowMenu.)
     */
    private void updateMenuItems() {
        boolean isConnected = mApplication.isConnected();
        int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        if (isConnected) {
            mConnectMenuItem.setText(MENUCMD_DISCONNECT);
            mConnectMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                keyMask));
            setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_D);
        }
        else {
            mConnectMenuItem.setText(MENUCMD_CONNECT);
            mConnectMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
                keyMask));
            setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_N);
        }

        mNewTableAtMenuItem.setEnabled(isConnected);
        mJoinTableAtMenuItem.setEnabled(isConnected);
        mJoinMucMenuItem.setEnabled(isConnected);
    }

    /**
     * Update the contents of the Window menu.
     */
    private void updateWindowMenu() {
        Iterator it;

        mWindowMenu.clear();

        /* Include the main (roster) window in the menu. */
        mWindowMenu.add(mApplication);

        // All the game table windows.
        for (it = mApplication.mTableWindows.iterator(); it.hasNext(); ) {
            JFrame win = (JFrame)it.next();
            mWindowMenu.add(win);
        }

        // All the MUC windows.
        for (it = mApplication.mMucWindows.iterator(); it.hasNext(); ) {
            JFrame win = (JFrame)it.next();
            mWindowMenu.add(win);
        }

        // All the one-to-one chat windows.
        for (it = mApplication.mChatWindows.iterator(); it.hasNext(); ) {
            JFrame win = (JFrame)it.next();
            mWindowMenu.add(win);
        }
    }

    /**
     * Helper method for setUpAppMenus. Assigns a keyboard mnemonic to a menu
     * or menu item, but only if not running on the Mac platform.
     *
     * @param item  The menu or menu item to assign the mnemonic to
     * @param key   The keyboard mnemonic.
     */
    private void setPlatformMnemonic(JMenuItem item, int key) {
        if (!PlatformWrapper.isRunningOnMac()) {
            item.setMnemonic(key);
        }
    }

    /**
     * ActionListener interface method implementation.
     * All the actual work is done by mApplication.
     *
     * @param e  The ActionEvent received.
     */
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == null) {
            // We don't want surprises; some of the menu items may be null.
            return; 
        }

        if (e.getSource() == mAboutMenuItem) {
            mApplication.doAbout();
        }
        else if (e.getSource() == mConnectMenuItem) {
            mApplication.doConnectDisconnect();
        }
        else if (e.getSource() == mQuitMenuItem) {
            mApplication.doQuit();
        }
        else if (e.getSource() == mNewTableAtMenuItem) {
            mApplication.doNewTableAt();
        }
        else if (e.getSource() == mJoinTableAtMenuItem) {
            mApplication.doJoinTableAt();
        }
        else if (e.getSource() == mGameInfoMenuItem) {
            if (mTableWindow != null)
                mTableWindow.doInfoDialog();
        }
        else if (e.getSource() == mInvitePlayerMenuItem) {
            if (mTableWindow != null)
                mTableWindow.doInviteDialog();
        }
        else if (e.getSource() == mInviteBotMenuItem) {
            if (mTableWindow != null)
                mTableWindow.doInviteBot();
        }
        else if (e.getSource() == mJoinMucMenuItem) {
            mApplication.doJoinMuc();
        }
        else if (e.getSource() == mShowLastErrorMenuItem) {
            mApplication.doShowLastError();
        }
    }

    /**
     * The main (roster) window calls this.
     */
    static public void applyPlatformMenuBar(JavolinApp win) {
        new JavolinMenuBar(win);
    }

    /**
     * All table windows call this.
     */
    static public void applyPlatformMenuBar(TableWindow win) {
        new JavolinMenuBar(win);
    }

    /**
     * All generic windows call this. On the Mac, this creates a clone of the
     * app menu bar, and applies it to the window. On other platforms, generic
     * windows don't get menu bars, so this does nothing.
     */
    static public void applyPlatformMenuBar(JFrame win) {
        if (PlatformWrapper.isRunningOnMac()) {
            new JavolinMenuBar(win);
        }
    }

    /**
     * Notify all menu bars that the main application state has changed.
     */
    static void notifyUpdateItems() {
        for (Iterator it = menuBarList.iterator(); it.hasNext(); ) {
            JavolinMenuBar bar = (JavolinMenuBar)it.next();
            bar.updateMenuItems();
        }
    }

    /**
     * Notify all menu bars that the window list has changed.
     */
    static void notifyUpdateWindowMenu() {
        for (Iterator it = menuBarList.iterator(); it.hasNext(); ) {
            JavolinMenuBar bar = (JavolinMenuBar)it.next();
            bar.updateWindowMenu();            
        }
    }

}
