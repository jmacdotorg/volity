package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.volity.client.data.GameInfo;
import org.volity.client.data.Metadata;
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

    private final static String SELECTRESOURCE_PROP = "SelectResource";

    private final static String MENUCMD_ABOUT = "About Javolin...";
    private final static String MENUCMD_PREFERENCES = "Preferences...";
    private final static String MENUCMD_CONNECT = "Connect...";
    private final static String MENUCMD_DISCONNECT = "Disconnect";
    private final static String MENUCMD_CLOSE_WINDOW = "Close Window";
    private final static String MENUCMD_QUIT = "Exit";
    private final static String MENUCMD_NEW_TABLE_AT = "New Table At...";
    private final static String MENUCMD_JOIN_TABLE_AT = "Join Table At...";
    private final static String MENUCMD_GAME_INFO = "Game Info...";
    private final static String MENUCMD_SUSPEND_TABLE = "Suspend Table";
    private final static String MENUCMD_RESTART_UI = "Restart Interface";
    private final static String MENUCMD_RELOAD_UI = "Reload Interface";
    private final static String MENUCMD_SELECT_UI = "Select New Interface...";
    private final static String MENUCMD_SELECT_RESOURCE = "Select New Resource...";
    private final static String MENUCMD_SELECT_RESOURCE_MENU = "Select New Resources";
    private final static String MENUCMD_INVITE_PLAYER = "Invite Player...";
    private final static String MENUCMD_INVITE_BOT = "Request Bot";
    private final static String MENUCMD_JOIN_MUC = "Join Multi-user Chat...";
    private final static String MENUCMD_SHOW_LAST_ERROR = "Display Last Error...";
    private final static String MENUCMD_CLEAR_CACHE = "Clear Interface Cache";
    private final static String MENUCMD_MEMUSAGE = "Show Memory Usage";
    private final static String MENUCMD_GAME_FINDER = "Game Finder";
    private final static String MENUCMD_SHOW_GAME_FINDER = "Show Game Finder";
    private final static String MENUCMD_BUG_REPORT = "File Bug Report";
    private final static String MENUCMD_DEBUG_SHOW_RPCS = "Print All RPCs";

    private JavolinApp mApplication = null;
    private JFrame mWindow = null;
    private TableWindow mTableWindow = null;
    private boolean mCloseableWindow = false;
    private CloseableWindow.Custom mCustomCloseableWindow = null;

    private WindowMenu mWindowMenu;
    private JMenuItem mAboutMenuItem;
    private JMenuItem mPreferencesMenuItem;
    private JMenuItem mConnectMenuItem;
    private JMenuItem mCloseWindowMenuItem;
    private JMenuItem mQuitMenuItem;
    private JMenuItem mNewTableAtMenuItem;
    private JMenuItem mJoinTableAtMenuItem;
    private JMenuItem mJoinMucMenuItem;
    private JMenuItem mShowLastErrorMenuItem;
    private JMenuItem mClearCacheMenuItem;
    private JMenuItem mMemUsageMenuItem;
    private JMenuItem mGameInfoMenuItem;
    private JMenuItem mSuspendTableMenuItem;
    private JMenuItem mRestartUIMenuItem;
    private JMenuItem mReloadUIMenuItem;
    private JMenuItem mSelectUIMenuItem;
    private JMenuItem mSelectResourceMenuItem;
    private JMenu mSelectResourceMenu;
    private JMenuItem mInvitePlayerMenuItem;
    private JMenuItem mInviteBotMenuItem;
    private JMenuItem mGameFinderMenuItem;
    private JMenuItem mShowGameFinderMenuItem;
    private JMenuItem mBugReportMenuItem;
    private JCheckBoxMenuItem mDebugShowRPCsMenuItem;

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
        mWindow = win;
        if (win instanceof TableWindow)
            mTableWindow = (TableWindow)win;
        if (win instanceof CloseableWindow)
            mCloseableWindow = true;
        if (win instanceof CloseableWindow.Custom)
            mCustomCloseableWindow = (CloseableWindow.Custom)win;

        // Add to the notification list.
        menuBarList.add(this);

        setUpAppMenus();

        mWindow.setJMenuBar(this);

        mWindow.addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    // When the window closes, remove its menu bar from the
                    // notification list.
                    menuBarList.remove(JavolinMenuBar.this);
                    mWindow.removeWindowListener(this);
                    mWindow = null;
                    mTableWindow = null;
                    if (mWindowMenu != null) {
                        mWindowMenu.clear();
                    }
                }
            });
    }

    /**
     *  Creates and sets up the menus for the application.
     */
    private void setUpAppMenus()
    {
        JMenu helpMenu = null;

        // File menu
        JMenu fileMenu = new JMenu("File");
        setPlatformMnemonic(fileMenu, KeyEvent.VK_F);

        mConnectMenuItem = new JMenuItem(MENUCMD_CONNECT);
        mConnectMenuItem.addActionListener(this);
        setAccelerator(mConnectMenuItem, KeyEvent.VK_L);
        setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_L);
        fileMenu.add(mConnectMenuItem);

        fileMenu.addSeparator();

        mCloseWindowMenuItem = new JMenuItem(MENUCMD_CLOSE_WINDOW);
        mCloseWindowMenuItem.addActionListener(this);
        setPlatformMnemonic(mCloseWindowMenuItem, KeyEvent.VK_W);
        setAccelerator(mCloseWindowMenuItem, KeyEvent.VK_W);
        if (!mCloseableWindow) 
            mCloseWindowMenuItem.setEnabled(false);
        fileMenu.add(mCloseWindowMenuItem);

        if (!PlatformWrapper.applicationMenuHandlersAvailable()) {
            // Only needed if there isn't a built-in Preferences menu
            mPreferencesMenuItem = new JMenuItem(MENUCMD_PREFERENCES);
            mPreferencesMenuItem.addActionListener(this);
            setPlatformMnemonic(mPreferencesMenuItem, KeyEvent.VK_P);
            fileMenu.add(mPreferencesMenuItem);
        }

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
        setAccelerator(mJoinMucMenuItem, KeyEvent.VK_G);
        setPlatformMnemonic(mJoinMucMenuItem, KeyEvent.VK_G);
        chatMenu.add(mJoinMucMenuItem);

        // Game menu
        JMenu gameMenu = new JMenu("Game");
        setPlatformMnemonic(gameMenu, KeyEvent.VK_G);

        mNewTableAtMenuItem = new JMenuItem(MENUCMD_NEW_TABLE_AT);
        mNewTableAtMenuItem.addActionListener(this);
        setAccelerator(mNewTableAtMenuItem, KeyEvent.VK_N);
        setPlatformMnemonic(mNewTableAtMenuItem, KeyEvent.VK_N);
        gameMenu.add(mNewTableAtMenuItem);

        mJoinTableAtMenuItem = new JMenuItem(MENUCMD_JOIN_TABLE_AT);
        mJoinTableAtMenuItem.addActionListener(this);
        setAccelerator(mJoinTableAtMenuItem, KeyEvent.VK_J);
        setPlatformMnemonic(mJoinTableAtMenuItem, KeyEvent.VK_J);
        gameMenu.add(mJoinTableAtMenuItem);

        mShowGameFinderMenuItem = new JMenuItem(MENUCMD_SHOW_GAME_FINDER);
        mShowGameFinderMenuItem.addActionListener(this);
        setAccelerator(mShowGameFinderMenuItem, KeyEvent.VK_F);
        setPlatformMnemonic(mShowGameFinderMenuItem, KeyEvent.VK_F);
        gameMenu.add(mShowGameFinderMenuItem);

        gameMenu.addSeparator();
        
        mSelectUIMenuItem = new JMenuItem(MENUCMD_SELECT_UI);
        mSelectUIMenuItem.addActionListener(this);
        setPlatformMnemonic(mSelectUIMenuItem, KeyEvent.VK_U);
        if (mTableWindow == null) 
            mSelectUIMenuItem.setEnabled(false);
        gameMenu.add(mSelectUIMenuItem);

        if (mTableWindow != null) {
            mSelectResourceMenuItem = new JMenuItem(MENUCMD_SELECT_RESOURCE);
            mSelectResourceMenuItem.addActionListener(this);
            gameMenu.add(mSelectResourceMenuItem);

            mSelectResourceMenu = new JMenu(MENUCMD_SELECT_RESOURCE_MENU);
            gameMenu.add(mSelectResourceMenu);
        }

        gameMenu.addSeparator();
        
        mGameInfoMenuItem = new JMenuItem(MENUCMD_GAME_INFO);
        mGameInfoMenuItem.addActionListener(this);
        setPlatformMnemonic(mGameInfoMenuItem, KeyEvent.VK_I);
        if (mTableWindow == null) 
            mGameInfoMenuItem.setEnabled(false);
        gameMenu.add(mGameInfoMenuItem);

        mSuspendTableMenuItem = new JMenuItem(MENUCMD_SUSPEND_TABLE);
        mSuspendTableMenuItem.addActionListener(this);
        setPlatformMnemonic(mSuspendTableMenuItem, KeyEvent.VK_S);
        if (mTableWindow == null) 
            mSuspendTableMenuItem.setEnabled(false);
        gameMenu.add(mSuspendTableMenuItem);

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

        // Debug menu
        JMenu debugMenu = new JMenu("Debug");

        mBugReportMenuItem = new JMenuItem(MENUCMD_BUG_REPORT);
        mBugReportMenuItem.addActionListener(this);
        setPlatformMnemonic(mBugReportMenuItem, KeyEvent.VK_B);
        if (!PlatformWrapper.launchURLAvailable())
            mBugReportMenuItem.setEnabled(false);
        debugMenu.add(mBugReportMenuItem);

        debugMenu.addSeparator();

        mRestartUIMenuItem = new JMenuItem(MENUCMD_RESTART_UI);
        mRestartUIMenuItem.addActionListener(this);
        setPlatformMnemonic(mRestartUIMenuItem, KeyEvent.VK_E);
        if (mTableWindow == null) 
            mRestartUIMenuItem.setEnabled(false);
        debugMenu.add(mRestartUIMenuItem);

        mReloadUIMenuItem = new JMenuItem(MENUCMD_RELOAD_UI);
        mReloadUIMenuItem.addActionListener(this);
        setPlatformMnemonic(mReloadUIMenuItem, KeyEvent.VK_R);
        if (mTableWindow == null) 
            mReloadUIMenuItem.setEnabled(false);
        debugMenu.add(mReloadUIMenuItem);

        mShowLastErrorMenuItem = new JMenuItem(MENUCMD_SHOW_LAST_ERROR);
        mShowLastErrorMenuItem.addActionListener(this);
        setPlatformMnemonic(mShowLastErrorMenuItem, KeyEvent.VK_S);
        debugMenu.add(mShowLastErrorMenuItem);

        mDebugShowRPCsMenuItem = new JCheckBoxMenuItem(MENUCMD_DEBUG_SHOW_RPCS);
        mDebugShowRPCsMenuItem.addActionListener(this);
        setPlatformMnemonic(mDebugShowRPCsMenuItem, KeyEvent.VK_P);
        debugMenu.add(mDebugShowRPCsMenuItem);

        mClearCacheMenuItem = new JMenuItem(MENUCMD_CLEAR_CACHE);
        mClearCacheMenuItem.addActionListener(this);
        setPlatformMnemonic(mClearCacheMenuItem, KeyEvent.VK_C);
        debugMenu.add(mClearCacheMenuItem);

        mMemUsageMenuItem = new JMenuItem(MENUCMD_MEMUSAGE);
        mMemUsageMenuItem.addActionListener(this);
        setAccelerator(mMemUsageMenuItem, KeyEvent.VK_M);
        setPlatformMnemonic(mMemUsageMenuItem, KeyEvent.VK_M);
        debugMenu.add(mMemUsageMenuItem);

        // Help menu
        if (!PlatformWrapper.applicationMenuHandlersAvailable()) {
            // Only needed if there isn't a built-in About menu

            helpMenu = new JMenu("Help");
            
            mAboutMenuItem = new JMenuItem(MENUCMD_ABOUT);
            mAboutMenuItem.addActionListener(this);
            setPlatformMnemonic(mAboutMenuItem, KeyEvent.VK_A);
            helpMenu.add(mAboutMenuItem);
        }


        // Put all the menus in place.
        add(fileMenu);
        add(chatMenu);
        add(gameMenu);
        add(mWindowMenu);
        add(debugMenu);
        if (helpMenu != null)
            add(helpMenu);


        // Update everything to the current app state
        updateMenuItems();
        updateWindowMenu();
        updateResourceMenu();
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
            setAccelerator(mConnectMenuItem, KeyEvent.VK_D);
            setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_D);
        }
        else {
            mConnectMenuItem.setText(MENUCMD_CONNECT);
            setAccelerator(mConnectMenuItem, KeyEvent.VK_L);
            setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_L);
        }

        mNewTableAtMenuItem.setEnabled(isConnected);
        mJoinTableAtMenuItem.setEnabled(isConnected);
        mJoinMucMenuItem.setEnabled(isConnected);

        mDebugShowRPCsMenuItem.setState(PrefsDialog.getDebugShowRPCs());
    }

    /**
     * Update the "Select Resource" item and submenu.
     */
    private void updateResourceMenu() {
        if (mTableWindow == null)
            return;

        List resList = null;
        Metadata metadata = mTableWindow.getMetadata();
        if (metadata != null)
            resList = metadata.getAllResources();

        if (resList == null || resList.size() == 0) {
            mSelectResourceMenuItem.setEnabled(false);
            mSelectResourceMenuItem.setVisible(true);

            mSelectResourceMenu.removeAll();
            mSelectResourceMenu.setEnabled(false);
            mSelectResourceMenu.setVisible(false);
            return;
        }

        if (resList.size() == 1) {
            URI uri = (URI)resList.get(0);

            mSelectResourceMenuItem.setEnabled(true);
            mSelectResourceMenuItem.setVisible(true);
            mSelectResourceMenuItem.putClientProperty(SELECTRESOURCE_PROP,
                uri);

            mSelectResourceMenu.removeAll();
            mSelectResourceMenu.setEnabled(false);
            mSelectResourceMenu.setVisible(false);
            return;
        }

        mSelectResourceMenuItem.setEnabled(false);
        mSelectResourceMenuItem.setVisible(false);
        mSelectResourceMenu.removeAll();
        mSelectResourceMenu.setEnabled(true);
        mSelectResourceMenu.setVisible(true);

        for (int ix=0; ix<resList.size(); ix++) {
            URI uri = (URI)resList.get(ix);
            String title = "Resource";
            Metadata submeta = metadata.getResource(uri);
            if (submeta != null) {
                String val = submeta.get(Metadata.DC_TITLE);
                if (val != null && !val.equals(""))
                    title = val;
            }

            JMenuItem item = new JMenuItem(title);
            item.putClientProperty(SELECTRESOURCE_PROP, uri);
            item.addActionListener(this);
            mSelectResourceMenu.add(item);
        }
    }

    /**
     * Update the contents of the Window menu.
     */
    private void updateWindowMenu() {
        Iterator it;

        mWindowMenu.clear();

        /* Include the game-finder window. This isn't added as a JFrame menu
         * item; it's a routine that calls JavolinApp.doGetFinder. */
        mGameFinderMenuItem = new JMenuItem(MENUCMD_GAME_FINDER);
        mGameFinderMenuItem.addActionListener(this);
        mWindowMenu.add(mGameFinderMenuItem);

        /* Include the main (roster) window in the menu. */
        mWindowMenu.add(mApplication);

        boolean divided = false;

        // All the game table windows.
        for (it = mApplication.mTableWindows.iterator(); it.hasNext(); ) {
            JFrame win = (JFrame)it.next();
            if (!divided) {
                mWindowMenu.addSeparator();
                divided = true;
            }
            mWindowMenu.add(win);
        }

        divided = false;

        // All the MUC windows.
        for (it = mApplication.mMucWindows.iterator(); it.hasNext(); ) {
            JFrame win = (JFrame)it.next();
            if (!divided) {
                mWindowMenu.addSeparator();
                divided = true;
            }
            mWindowMenu.add(win);
        }

        divided = false;

        // All the one-to-one chat windows.
        for (it = mApplication.mChatWindows.iterator(); it.hasNext(); ) {
            JFrame win = (JFrame)it.next();
            if (!divided) {
                mWindowMenu.addSeparator();
                divided = true;
            }
            mWindowMenu.add(win);
        }

        divided = false;

        // All the dialog windows (BaseWindow objects).
        for (it = mApplication.mDialogWindows.iterator(); it.hasNext(); ) {
            JFrame win = (JFrame)it.next();
            if (!divided) {
                mWindowMenu.addSeparator();
                divided = true;
            }
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
     * Helper method for setUpAppMenus. Assigns a keyboard shortcut to a menu
     * item.
     *
     * @param item  The menu or menu item to assign the mnemonic to
     * @param key   The keyboard mnemonic.
     */
    private void setAccelerator(JMenuItem item, int key) {
        // Platform independent accelerator key
        int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        item.setAccelerator(KeyStroke.getKeyStroke(key, keyMask));
    }

    /**
     * ActionListener interface method implementation.
     * All the actual work is done by mApplication.
     *
     * @param e  The ActionEvent received.
     */
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (source == null) {
            // We don't want surprises; some of the menu items may be null.
            return; 
        }

        if (mWindow == null) {
            // This menu bar is history.
            return;
        }

        if (source == mAboutMenuItem) {
            mApplication.doAbout();
        }
        else if (source == mPreferencesMenuItem) {
            mApplication.doPreferences();
        }
        else if (source == mConnectMenuItem) {
            mApplication.doConnectDisconnect();
        }
        else if (source == mQuitMenuItem) {
            mApplication.doQuit();
        }
        else if (source == mCloseWindowMenuItem) {
            if (mCloseableWindow) {
                if (mCustomCloseableWindow == null)
                    mWindow.dispose();
                else
                    mCustomCloseableWindow.closeWindow();
            }
        }
        else if (source == mNewTableAtMenuItem) {
            mApplication.doNewTableAt();
        }
        else if (source == mJoinTableAtMenuItem) {
            mApplication.doJoinTableAt();
        }
        else if (source == mJoinMucMenuItem) {
            mApplication.doJoinMuc();
        }
        else if (source == mShowLastErrorMenuItem) {
            mApplication.doShowLastError();
        }
        else if (source == mDebugShowRPCsMenuItem) {
            boolean val = mDebugShowRPCsMenuItem.getState();
            PrefsDialog.setDebugShowRPCs(val);
        }
        else if (source == mClearCacheMenuItem) {
            mApplication.doClearCache();
        }
        else if (source == mMemUsageMenuItem) {
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long total = runtime.totalMemory();
            long free = runtime.freeMemory();
            System.out.println("Memory used: " + String.valueOf(total-free)
                               + " of " + String.valueOf(total));
        }
        else if (source == mGameFinderMenuItem
            || source == mShowGameFinderMenuItem) {
            mApplication.doGetFinder();
        }
        else if (source == mBugReportMenuItem) {
            sendBugReportURL();
        }

        if (mTableWindow != null) {
            if (source == mGameInfoMenuItem) {
                mTableWindow.doInfoDialog();
            }
            else if (source == mSuspendTableMenuItem) {
                mTableWindow.doSuspendTable();
            }
            else if (source == mRestartUIMenuItem) {
                mTableWindow.doReloadUI(false);
            }
            else if (source == mReloadUIMenuItem) {
                mTableWindow.doReloadUI(true);
            }
            else if (source == mSelectUIMenuItem) {
                mTableWindow.doSelectNewUI();
            }
            else if (source == mInvitePlayerMenuItem) {
                mTableWindow.doInviteDialog(null);
            }
            else if (source == mInviteBotMenuItem) {
                mTableWindow.doInviteBot();
            }
        }

        if (source instanceof JComponent) {
            JComponent jsource = (JComponent)source;
            URI propuri = (URI)jsource.getClientProperty(SELECTRESOURCE_PROP);
            if (propuri != null && mTableWindow != null) {
                mTableWindow.doSelectNewResource(propuri);
            }
        }
    }

    protected void sendBugReportURL() {
        String url = Finder.BUGREPORT_URL;

        try {
            String query = "";

            query += "?javolin_version=";
            query += URLEncoder.encode(JavolinApp.getAppVersion(), "UTF-8");

            String jid = JavolinApp.getSoleJavolinApp().getSelfJID();
            if (jid != null) {
                query += "&username=";
                query += URLEncoder.encode(jid, "UTF-8");
            }

            if (mTableWindow != null) {
                GameInfo info = mTableWindow.getGameInfo();
                if (info != null) {
                    URI uri = info.getRulesetURI();
                    if (uri != null) {
                        query += "&ruleset_uri=";
                        query += URLEncoder.encode(uri.toString(), "UTF-8");
                    }
                    String version = info.getRulesetVersion();
                    if (version != null) {
                        query += "&ruleset_version=";
                        query += URLEncoder.encode(version, "UTF-8");
                    }
                }
                URL uiurl = mTableWindow.getUIUrl();
                if (uiurl != null) {
                    query += "&ui_url=";
                    query += URLEncoder.encode(uiurl.toString(), "UTF-8");
                }
                String muc = mTableWindow.getRoom();
                if (muc != null) {
                    query += "&table_muc=";
                    query += URLEncoder.encode(muc, "UTF-8");
                }
            }

            url += query;
        }
        catch (UnsupportedEncodingException ex) {
            // never mind.
        }

        PlatformWrapper.launchURL(url);
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

    /**
     * Notify one menu bar that the metadata of its table window has changed.
     */
    public static void notifyUpdateResourceMenu(TableWindow win) {
        for (Iterator it = menuBarList.iterator(); it.hasNext(); ) {
            JavolinMenuBar bar = (JavolinMenuBar)it.next();
            if (bar.mTableWindow == win)
                bar.updateResourceMenu();            
        }
    }
}
