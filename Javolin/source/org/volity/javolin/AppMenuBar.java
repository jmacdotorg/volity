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
import org.volity.client.GameServer;
import org.volity.client.data.GameInfo;
import org.volity.client.data.Metadata;
import org.volity.javolin.game.TableWindow;

/**
 * This class is a factory for Gamut menu bars. (They're mostly identical.)
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
public class AppMenuBar extends JMenuBar
    implements ActionListener
{
    private static List menuBarList = new ArrayList();

    private final static String MENUTYPE_PROP = "MenuType";
    private final static String MENUTYPE_PROP_RESOURCE = "MenuType_Resource";
    private final static String MENUTYPE_PROP_INVITEBOT = "MenuType_InviteBot";
    private final static String SELECTRESOURCE_PROP = "SelectResource";
    private final static String INVITEBOT_JID_PROP = "InviteBot_JID";
    private final static String INVITEBOT_URI_PROP = "InviteBot_URI";

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
    private JMenuItem mChatWithMenuItem;
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
    private JMenu mInviteBotMenu;
    private JMenuItem mInviteBotSelectMenuItem;
    private JMenuItem mGameFinderMenuItem;
    private JMenuItem mShowGameFinderMenuItem;
    private JMenuItem mBugReportMenuItem;
    private JCheckBoxMenuItem mDebugShowRPCsMenuItem;
    private JMenuItem mGamutHelpMenuItem;
    private JMenuItem mGameHelpMenuItem;

    /**
     * Construct a menu bar and attach it to the given window.
     *
     * Do not call this directly. Instead, call applyPlatformMenuBar(win).
     */
    protected AppMenuBar(JFrame win) {
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
                    menuBarList.remove(AppMenuBar.this);
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
        JMenu fileMenu = new JMenu(localizeTitle("File"));
        setPlatformMnemonic(fileMenu, KeyEvent.VK_F);

        mConnectMenuItem = new JMenuItem(localize("Connect"));
        mConnectMenuItem.addActionListener(this);
        setAccelerator(mConnectMenuItem, KeyEvent.VK_L);
        setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_L);
        fileMenu.add(mConnectMenuItem);

        fileMenu.addSeparator();

        mCloseWindowMenuItem = new JMenuItem(localize("CloseWindow"));
        mCloseWindowMenuItem.addActionListener(this);
        setPlatformMnemonic(mCloseWindowMenuItem, KeyEvent.VK_W);
        setAccelerator(mCloseWindowMenuItem, KeyEvent.VK_W);
        if (!mCloseableWindow) 
            mCloseWindowMenuItem.setEnabled(false);
        fileMenu.add(mCloseWindowMenuItem);

        if (!PlatformWrapper.applicationMenuHandlersAvailable()) {
            // Only needed if there isn't a built-in Preferences menu
            mPreferencesMenuItem = new JMenuItem(localize("Preferences"));
            mPreferencesMenuItem.addActionListener(this);
            setPlatformMnemonic(mPreferencesMenuItem, KeyEvent.VK_P);
            fileMenu.add(mPreferencesMenuItem);
        }

        if (!PlatformWrapper.applicationMenuHandlersAvailable()) {
            // Only needed if there isn't a built-in Quit menu

            fileMenu.addSeparator();

            mQuitMenuItem = new JMenuItem(localize("Quit"));
            mQuitMenuItem.addActionListener(this);
            setPlatformMnemonic(mQuitMenuItem, KeyEvent.VK_X);
            fileMenu.add(mQuitMenuItem);
        }

        // Chat menu
        JMenu chatMenu = new JMenu(localizeTitle("Chat"));
        setPlatformMnemonic(chatMenu, KeyEvent.VK_C);

        mChatWithMenuItem = new JMenuItem(localize("ChatWith"));
        mChatWithMenuItem.addActionListener(this);
        setPlatformMnemonic(mChatWithMenuItem, KeyEvent.VK_C);
        chatMenu.add(mChatWithMenuItem);

        mJoinMucMenuItem = new JMenuItem(localize("JoinMUC"));
        mJoinMucMenuItem.addActionListener(this);
        setAccelerator(mJoinMucMenuItem, KeyEvent.VK_G);
        setPlatformMnemonic(mJoinMucMenuItem, KeyEvent.VK_G);
        chatMenu.add(mJoinMucMenuItem);

        // Game menu
        JMenu gameMenu = new JMenu(localizeTitle("Game"));
        setPlatformMnemonic(gameMenu, KeyEvent.VK_G);

        mNewTableAtMenuItem = new JMenuItem(localize("NewTableAt"));
        mNewTableAtMenuItem.addActionListener(this);
        setAccelerator(mNewTableAtMenuItem, KeyEvent.VK_N);
        setPlatformMnemonic(mNewTableAtMenuItem, KeyEvent.VK_N);
        gameMenu.add(mNewTableAtMenuItem);

        mJoinTableAtMenuItem = new JMenuItem(localize("JoinTableAt"));
        mJoinTableAtMenuItem.addActionListener(this);
        setAccelerator(mJoinTableAtMenuItem, KeyEvent.VK_J);
        setPlatformMnemonic(mJoinTableAtMenuItem, KeyEvent.VK_J);
        gameMenu.add(mJoinTableAtMenuItem);

        mShowGameFinderMenuItem = new JMenuItem(localize("ShowGameFinder"));
        mShowGameFinderMenuItem.addActionListener(this);
        setAccelerator(mShowGameFinderMenuItem, KeyEvent.VK_F);
        setPlatformMnemonic(mShowGameFinderMenuItem, KeyEvent.VK_F);
        gameMenu.add(mShowGameFinderMenuItem);

        gameMenu.addSeparator();
        
        mSelectUIMenuItem = new JMenuItem(localize("SelectUI"));
        mSelectUIMenuItem.addActionListener(this);
        setPlatformMnemonic(mSelectUIMenuItem, KeyEvent.VK_U);
        if (mTableWindow == null) 
            mSelectUIMenuItem.setEnabled(false);
        gameMenu.add(mSelectUIMenuItem);

        if (mTableWindow != null) {
            mSelectResourceMenuItem = new JMenuItem(localize("SelectResource"));
            mSelectResourceMenuItem.addActionListener(this);
            gameMenu.add(mSelectResourceMenuItem);

            mSelectResourceMenu = new JMenu(localize("SelectResourceMenu"));
            gameMenu.add(mSelectResourceMenu);
        }

        gameMenu.addSeparator();
        
        mGameInfoMenuItem = new JMenuItem(localize("GameInfo"));
        mGameInfoMenuItem.addActionListener(this);
        setAccelerator(mGameInfoMenuItem, KeyEvent.VK_I);
        setPlatformMnemonic(mGameInfoMenuItem, KeyEvent.VK_I);
        if (mTableWindow == null) 
            mGameInfoMenuItem.setEnabled(false);
        gameMenu.add(mGameInfoMenuItem);

        mSuspendTableMenuItem = new JMenuItem(localize("SuspendTable"));
        mSuspendTableMenuItem.addActionListener(this);
        setPlatformMnemonic(mSuspendTableMenuItem, KeyEvent.VK_S);
        if (mTableWindow == null) 
            mSuspendTableMenuItem.setEnabled(false);
        gameMenu.add(mSuspendTableMenuItem);

        mInvitePlayerMenuItem = new JMenuItem(localize("InvitePlayer"));
        mInvitePlayerMenuItem.addActionListener(this);
        setPlatformMnemonic(mInvitePlayerMenuItem, KeyEvent.VK_P);
        if (mTableWindow == null) 
            mInvitePlayerMenuItem.setEnabled(false);
        gameMenu.add(mInvitePlayerMenuItem);

        mInviteBotMenuItem = new JMenuItem(localize("InviteBot"));
        mInviteBotMenuItem.addActionListener(this);
        setAccelerator(mInviteBotMenuItem, KeyEvent.VK_B);
        setPlatformMnemonic(mInviteBotMenuItem, KeyEvent.VK_B);
        if (mTableWindow == null) 
            mInviteBotMenuItem.setEnabled(false);
        gameMenu.add(mInviteBotMenuItem);

        if (mTableWindow != null) {
            mInviteBotMenu = new JMenu(localize("InviteBotMenu"));
            gameMenu.add(mInviteBotMenu);

            mInviteBotSelectMenuItem = new JMenuItem(localize("InviteBotSelect"));
            mInviteBotSelectMenuItem.addActionListener(this);
            setPlatformMnemonic(mInviteBotSelectMenuItem, KeyEvent.VK_V);
            gameMenu.add(mInviteBotSelectMenuItem);
        }

        // Window menu
        mWindowMenu = new WindowMenu();

        // Debug menu
        JMenu debugMenu = new JMenu(localizeTitle("Debug"));

        mBugReportMenuItem = new JMenuItem(localize("BugReport"));
        mBugReportMenuItem.addActionListener(this);
        setPlatformMnemonic(mBugReportMenuItem, KeyEvent.VK_B);
        if (!PlatformWrapper.launchURLAvailable())
            mBugReportMenuItem.setEnabled(false);
        debugMenu.add(mBugReportMenuItem);

        debugMenu.addSeparator();

        mRestartUIMenuItem = new JMenuItem(localize("RestartUI"));
        mRestartUIMenuItem.addActionListener(this);
        setPlatformMnemonic(mRestartUIMenuItem, KeyEvent.VK_E);
        if (mTableWindow == null) 
            mRestartUIMenuItem.setEnabled(false);
        debugMenu.add(mRestartUIMenuItem);

        mReloadUIMenuItem = new JMenuItem(localize("ReloadUI"));
        mReloadUIMenuItem.addActionListener(this);
        setPlatformMnemonic(mReloadUIMenuItem, KeyEvent.VK_R);
        if (mTableWindow == null) 
            mReloadUIMenuItem.setEnabled(false);
        debugMenu.add(mReloadUIMenuItem);

        mShowLastErrorMenuItem = new JMenuItem(localize("ShowLastError"));
        mShowLastErrorMenuItem.addActionListener(this);
        setPlatformMnemonic(mShowLastErrorMenuItem, KeyEvent.VK_S);
        debugMenu.add(mShowLastErrorMenuItem);

        mDebugShowRPCsMenuItem = new JCheckBoxMenuItem(localize("DebugShowRPCs"));
        mDebugShowRPCsMenuItem.addActionListener(this);
        setPlatformMnemonic(mDebugShowRPCsMenuItem, KeyEvent.VK_P);
        debugMenu.add(mDebugShowRPCsMenuItem);

        mClearCacheMenuItem = new JMenuItem(localize("ClearCache"));
        mClearCacheMenuItem.addActionListener(this);
        setPlatformMnemonic(mClearCacheMenuItem, KeyEvent.VK_C);
        debugMenu.add(mClearCacheMenuItem);

        mMemUsageMenuItem = new JMenuItem(localize("MemUsage"));
        mMemUsageMenuItem.addActionListener(this);
        //setAccelerator(mMemUsageMenuItem, KeyEvent.VK_M);
        setPlatformMnemonic(mMemUsageMenuItem, KeyEvent.VK_M);
        debugMenu.add(mMemUsageMenuItem);

        // Help menu
        helpMenu = new JMenu(localizeTitle("Help"));

        mGamutHelpMenuItem = new JMenuItem(localize("AppHelp"));
        mGamutHelpMenuItem.addActionListener(this);
        setPlatformMnemonic(mGamutHelpMenuItem, KeyEvent.VK_G);
        helpMenu.add(mGamutHelpMenuItem);

        mGameHelpMenuItem = new JMenuItem(localize("GameHelp"));
        mGameHelpMenuItem.addActionListener(this);
        setPlatformMnemonic(mGameHelpMenuItem, KeyEvent.VK_P);
        helpMenu.add(mGameHelpMenuItem);
        if (mTableWindow == null) 
            mGameHelpMenuItem.setEnabled(false);

        if (!PlatformWrapper.applicationMenuHandlersAvailable()) {
            // Only needed if there isn't a built-in About menu
            helpMenu.addSeparator();
            mAboutMenuItem = new JMenuItem(localize("About"));
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
        updateInviteBotMenu();
    }

    /**
     * Updates the text or state of all dynamic menu items. (Except WindowMenu
     * -- that's handled in updateWindowMenu.)
     */
    private void updateMenuItems() {
        boolean isConnected = mApplication.isConnected();
        int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        if (isConnected) {
            mConnectMenuItem.setText(localize("Disconnect"));
            setAccelerator(mConnectMenuItem, KeyEvent.VK_D);
            setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_D);
        }
        else {
            mConnectMenuItem.setText(localize("Connect"));
            setAccelerator(mConnectMenuItem, KeyEvent.VK_L);
            setPlatformMnemonic(mConnectMenuItem, KeyEvent.VK_L);
        }

        mNewTableAtMenuItem.setEnabled(isConnected);
        mJoinTableAtMenuItem.setEnabled(isConnected);
        mChatWithMenuItem.setEnabled(isConnected);
        mJoinMucMenuItem.setEnabled(isConnected);

        mDebugShowRPCsMenuItem.setState(PrefsDialog.getDebugShowRPCs());
    }

    /**
     * Update the "Invite Bot" item and submenu.
     */
    private void updateInviteBotMenu() {
        if (mTableWindow == null)
            return;

        List resList = mTableWindow.getAvailableBots();

        if (resList == null) {
            // Offer an invite option, for the default bot.
            mInviteBotMenuItem.setEnabled(true);
            mInviteBotMenuItem.setVisible(true);
            mInviteBotMenuItem.putClientProperty(MENUTYPE_PROP,
                MENUTYPE_PROP_INVITEBOT);
            mInviteBotMenuItem.putClientProperty(INVITEBOT_URI_PROP,
                null);
            mInviteBotMenuItem.putClientProperty(INVITEBOT_JID_PROP,
                null);

            mInviteBotMenu.removeAll();
            mInviteBotMenu.setEnabled(false);
            mInviteBotMenu.setVisible(false);
            return;
        }

        if (resList.size() == 0) {
            // Display a greyed-out invite option.
            mInviteBotMenuItem.setEnabled(false);
            mInviteBotMenuItem.setVisible(true);

            mInviteBotMenu.removeAll();
            mInviteBotMenu.setEnabled(false);
            mInviteBotMenu.setVisible(false);
            return;
        }

        mInviteBotMenuItem.setEnabled(false);
        mInviteBotMenuItem.setVisible(false);
        mInviteBotMenu.removeAll();
        mInviteBotMenu.setEnabled(true);
        mInviteBotMenu.setVisible(true);

        /* This ordering algorithm assumes that factory entries are grouped at
         * the end of the list. */

        for (int ix=0; ix<resList.size(); ix++) {
            Object obj = resList.get(ix);
            if (obj instanceof GameServer.AvailableBot) {
                GameServer.AvailableBot bot = (GameServer.AvailableBot)obj;
                String title = bot.name;
                if (title == null || title.equals(""))
                    title = bot.uri;

                JMenuItem item = new JMenuItem(title);
                item.putClientProperty(MENUTYPE_PROP, MENUTYPE_PROP_INVITEBOT);
                item.putClientProperty(INVITEBOT_URI_PROP, bot.uri);
                item.putClientProperty(INVITEBOT_JID_PROP, bot.jid);
                item.addActionListener(this);
                mInviteBotMenu.add(item);
            }
            else {
                GameServer.AvailableFactory factory = (GameServer.AvailableFactory)obj;
                
                if (mInviteBotMenu.getMenuComponentCount() != 0)
                    mInviteBotMenu.addSeparator();

                List subList = factory.getList();
                if (subList == null) {
                    String title = factory.name;
                    if (title == null || title.equals(""))
                        title = "Factory " + factory.jid;
                    JMenuItem item = new JMenuItem(title);
                    item.setEnabled(false);
                    mInviteBotMenu.add(item);
                }
                else {
                    for (int jx=0; jx<subList.size(); jx++) {
                        Object obj2 = subList.get(jx);
                        GameServer.AvailableBot bot = (GameServer.AvailableBot)obj2;
                        String title = bot.name;
                        if (title == null || title.equals(""))
                            title = bot.uri;

                        JMenuItem item = new JMenuItem(title);
                        item.putClientProperty(MENUTYPE_PROP, MENUTYPE_PROP_INVITEBOT);
                        item.putClientProperty(INVITEBOT_URI_PROP, bot.uri);
                        item.putClientProperty(INVITEBOT_JID_PROP, bot.jid);
                        item.addActionListener(this);
                        mInviteBotMenu.add(item);
                    }
                }
            }
        }

        /* Now we do some lame retrofitting. If we've generated an empty
         * submenu, go back to a lone greyed-out menu option. If we've
         * generated a submenu of exactly one item, go back to a lone active
         * menu option. */
        int count = mInviteBotMenu.getMenuComponentCount();

        if (count == 0) {
            // Display a greyed-out invite option.
            mInviteBotMenuItem.setEnabled(false);
            mInviteBotMenuItem.setVisible(true);

            mInviteBotMenu.removeAll();
            mInviteBotMenu.setEnabled(false);
            mInviteBotMenu.setVisible(false);
            return;
        }

        if (count == 1) {
            JMenuItem item = (JMenuItem)mInviteBotMenu.getMenuComponent(0);
            
            mInviteBotMenuItem.setEnabled(item.isEnabled());
            mInviteBotMenuItem.setVisible(true);
            mInviteBotMenuItem.putClientProperty(MENUTYPE_PROP,
                item.getClientProperty(MENUTYPE_PROP));
            mInviteBotMenuItem.putClientProperty(INVITEBOT_URI_PROP,
                item.getClientProperty(INVITEBOT_URI_PROP));
            mInviteBotMenuItem.putClientProperty(INVITEBOT_JID_PROP,
                item.getClientProperty(INVITEBOT_JID_PROP));

            mInviteBotMenu.removeAll();
            mInviteBotMenu.setEnabled(false);
            mInviteBotMenu.setVisible(false);
            return;
        }

        /* And if there's still a menu, set the first active item to be
         * cmd-B. */
        for (int ix=0; ix<count; ix++) {
            JMenuItem item = (JMenuItem)mInviteBotMenu.getMenuComponent(ix);
            if (item.isEnabled()) {
                setAccelerator(item, KeyEvent.VK_B);
                break;
            }
        }
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
            mSelectResourceMenuItem.putClientProperty(MENUTYPE_PROP, MENUTYPE_PROP_RESOURCE);
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
            item.putClientProperty(MENUTYPE_PROP, MENUTYPE_PROP_RESOURCE);
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
        mGameFinderMenuItem = new JMenuItem(localize("GameFinderTitle"));
        mGameFinderMenuItem.addActionListener(this);
        mWindowMenu.add(mGameFinderMenuItem);

        /* Include the main (roster) window in the menu. */
        mWindowMenu.add(mApplication);

        boolean divided = false;

        // All the game table windows.
        for (it = mApplication.getTableWindows(); it.hasNext(); ) {
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
     * Helper method for setUpAppMenus. Find the localized string for a menu
     * item.
     */
    private String localize(String key) {
        return Localize.localize("MenuIt", key);
    }

    /**
     * Helper method for setUpAppMenus. Find the localized string for a menu
     * title.
     */
    private String localizeTitle(String key) {
        return Localize.localize("Menu", key);
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
        else if (source == mChatWithMenuItem) {
            mApplication.doChatWith();
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
            long max = runtime.maxMemory();
            System.out.println("Memory used: " + String.valueOf(total-free)
                + " of " + String.valueOf(total)
                + " (limit " + String.valueOf(max) + ")");
        }
        else if (source == mGameFinderMenuItem
            || source == mShowGameFinderMenuItem) {
            mApplication.doGetFinder();
        }
        else if (source == mBugReportMenuItem) {
            sendBugReportURL();
        }
        else if (source == mGamutHelpMenuItem) {
            mApplication.doGetHelp();
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
                mTableWindow.doInviteDialog();
            }
            else if (source == mInviteBotSelectMenuItem) {
                mTableWindow.doInviteBotSelect();
            }
            else if (source == mGameHelpMenuItem) {
                mTableWindow.doGetGameHelp();
            }
        }

        if (source instanceof JComponent) {
            JComponent jsource = (JComponent)source;

            String proptype = (String)jsource.getClientProperty(MENUTYPE_PROP);
            if (proptype == MENUTYPE_PROP_RESOURCE) {
                URI propuri = (URI)jsource.getClientProperty(SELECTRESOURCE_PROP);
                if (propuri != null && mTableWindow != null) {
                    mTableWindow.doSelectNewResource(propuri);
                }
            }
            if (proptype == MENUTYPE_PROP_INVITEBOT) {
                String propuri = (String)jsource.getClientProperty(INVITEBOT_URI_PROP);
                String propjid = (String)jsource.getClientProperty(INVITEBOT_JID_PROP);
                // Either or both may be null.
                mTableWindow.doInviteBot(propuri, propjid);
            }
        }
    }

    protected void sendBugReportURL() {
        String url = Finder.BUGREPORT_URL;

        try {
            String query = "";

            query += "?javolin_version=";
            query += URLEncoder.encode(JavolinApp.getAppVersion(), "UTF-8");

            String jvmversion = System.getProperty("java.version");
            if (jvmversion != null) {
                query += "&jvm_version=";
                query += URLEncoder.encode(jvmversion, "UTF-8");
            }

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

                Metadata metadata = mTableWindow.getMetadata();
                if (metadata != null) {
                    String uivers = metadata.get(Metadata.VOLITY_VERSION);
                    if (uivers != null) {
                        query += "&ui_version=";
                        query += URLEncoder.encode(uivers, "UTF-8");
                    }
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
        new AppMenuBar(win);
    }

    /**
     * All table windows call this.
     */
    static public void applyPlatformMenuBar(TableWindow win) {
        new AppMenuBar(win);
    }

    /**
     * All generic windows call this. On the Mac, this creates a clone of the
     * app menu bar, and applies it to the window. On other platforms, generic
     * windows don't get menu bars, so this does nothing.
     */
    static public void applyPlatformMenuBar(JFrame win) {
        if (PlatformWrapper.isRunningOnMac()) {
            new AppMenuBar(win);
        }
    }

    /**
     * Notify all menu bars that the main application state has changed.
     */
    static void notifyUpdateItems() {
        for (Iterator it = menuBarList.iterator(); it.hasNext(); ) {
            AppMenuBar bar = (AppMenuBar)it.next();
            bar.updateMenuItems();
        }
    }

    /**
     * Notify all menu bars that the window list has changed.
     */
    static void notifyUpdateWindowMenu() {
        for (Iterator it = menuBarList.iterator(); it.hasNext(); ) {
            AppMenuBar bar = (AppMenuBar)it.next();
            bar.updateWindowMenu();            
        }
    }

    /**
     * Notify one menu bar that the metadata of its table window has changed.
     */
    public static void notifyUpdateResourceMenu(TableWindow win) {
        for (Iterator it = menuBarList.iterator(); it.hasNext(); ) {
            AppMenuBar bar = (AppMenuBar)it.next();
            if (bar.mTableWindow == win)
                bar.updateResourceMenu();            
        }
    }

    /**
     * Notify one menu bar that the bot list of its table window has changed.
     */
    public static void notifyUpdateInviteBotMenu(TableWindow win) {
        for (Iterator it = menuBarList.iterator(); it.hasNext(); ) {
            AppMenuBar bar = (AppMenuBar)it.next();
            if (bar.mTableWindow == win)
                bar.updateInviteBotMenu();            
        }
    }

    /**
     * Fetch from one menu bar the default (cmd-B) invite-bot parameters. The
     * supplied String[2] is optional; if present, it will be filled in with
     * [URI, JID]. Either or both value may be null.
     *
     * @return whether there is a valid default invite-bot command.
     */
    public static boolean getDefaultInviteBot(TableWindow win, 
        String[] urijid) {
        for (Iterator it = menuBarList.iterator(); it.hasNext(); ) {
            AppMenuBar bar = (AppMenuBar)it.next();
            if (bar.mTableWindow == win) {
                JMenuItem item = bar.mInviteBotMenuItem;

                if (item == null || !item.isEnabled()) {
                    item = null;
                    int count = bar.mInviteBotMenu.getMenuComponentCount();
                    for (int ix=0; ix<count; ix++) {
                        JMenuItem subitem = (JMenuItem)bar.mInviteBotMenu.getMenuComponent(ix);
                        if (subitem.isEnabled()) {
                            item = subitem;
                            break;
                        }
                    }
                }

                if (item == null || !item.isEnabled())
                    return false;

                if (urijid != null) {
                    String uri = (String)item.getClientProperty(INVITEBOT_URI_PROP);
                    String jid = (String)item.getClientProperty(INVITEBOT_JID_PROP);
                    urijid[0] = uri;
                    urijid[1] = jid;
                }
                return true;
            }
        }

        return false;
    }
}
