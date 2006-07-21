package org.volity.javolin.chat;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.MissingResourceException;
import javax.swing.*;
import org.jivesoftware.smack.util.StringUtils;
import org.volity.jabber.JIDUtils;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.PlatformWrapper;
import org.volity.javolin.game.TableWindow;

/**
 * A contextual menu which can be used anywhere we have a JID. (Lines in chat
 * panes, etc.)
 */
public class UserContextMenu extends JPopupMenu
    implements ActionListener
{
    static final String TABLEWIN_PROP = "TableWin";

    String mJID = null;
    boolean mIsSelf;

    JMenuItem mChatMenuItem;
    JMenuItem mStatsMenuItem;
    JMenuItem mRosterAddMenuItem;
    JMenu mInvitesMenu;

    /** Constructor. */
    public UserContextMenu() {
        super();
        buildUI();
    }

    /** Clean up component. */
    public void dispose() {
        removeAll();
        mJID = null;
    }

    /**
     * Localization helper.
     */
    protected static String localize(String key) {
        try {
            return JavolinApp.resources.getString("PopupIt_"+key);
        }
        catch (MissingResourceException ex) {
            return "???PopupIt_"+key;
        }
    }

    /**
     * This is the entry point; call this when you want a menu. The JID should
     * be a full JID if possible.
     */
    public void adjustShow(String jid, JComponent parent, int xpos, int ypos) {
        if (isVisible()) {
            // Menu is already in use.
            return;
        }

        JavolinApp app = JavolinApp.getSoleJavolinApp();

        mJID = jid;
        mIsSelf = false;

        String selfjid = app.getSelfJID();
        if (selfjid != null && JIDUtils.bareMatch(selfjid, jid))
            mIsSelf = true;

        mChatMenuItem.setEnabled(!mIsSelf);

        String barejid = StringUtils.parseBareAddress(jid);
        boolean onroster = (app.getRosterPanel() != null
            && app.getRosterPanel().isUserOnRoster(barejid));

        mRosterAddMenuItem.setEnabled(!(onroster || mIsSelf));

        int count = 0;
        mInvitesMenu.removeAll();
        if (!mIsSelf) {
            for (Iterator it = app.getTableWindows(); it.hasNext(); ) {
                TableWindow win = (TableWindow)it.next();
                String key = win.getRoom();
                JMenuItem item = new JMenuItem(win.getWindowName());
                item.addActionListener(this);
                item.putClientProperty(TABLEWIN_PROP, key);
                mInvitesMenu.add(item);
                count++;
            }
        }
        mInvitesMenu.setEnabled(count > 0);

        show(parent, xpos, ypos);
    }

    // Implements ActionListener interface
    public void actionPerformed(ActionEvent ev) {
        if (mJID == null)
            return;

        Object source = ev.getSource();
        if (source == null)
            return;

        if (source == mChatMenuItem) {
            JavolinApp.getSoleJavolinApp().chatWithUser(mJID);
        }
        if (source == mStatsMenuItem) {
            JavolinApp.getSoleJavolinApp().showUserGameStats(mJID);
        }
        if (source == mRosterAddMenuItem) {
            JavolinApp.getSoleJavolinApp().doAddUser(mJID);
        }

        if (source instanceof JComponent) {
            JComponent jsource = (JComponent)source;
            String key = (String)jsource.getClientProperty(TABLEWIN_PROP);
            if (key != null) {
                TableWindow win = JavolinApp.getSoleJavolinApp().getTableWindowByRoom(key);
                if (win != null) {
                    win.doInviteDialog(mJID, false);
                }
            }
        }
    }

    /** Create the interface. */
    protected void buildUI() {
        mChatMenuItem = new JMenuItem(localize("Chat"));
        mChatMenuItem.addActionListener(this);
        add(mChatMenuItem);

        mRosterAddMenuItem = new JMenuItem(localize("RosterAdd"));
        mRosterAddMenuItem.addActionListener(this);
        add(mRosterAddMenuItem);

        mInvitesMenu = new JMenu(localize("InviteMenu"));
        add(mInvitesMenu);

        mStatsMenuItem = new JMenuItem(localize("Stats"));
        mStatsMenuItem.addActionListener(this);
        add(mStatsMenuItem);
        if (!PlatformWrapper.launchURLAvailable())
            mStatsMenuItem.setEnabled(false);
    }
}
