package org.volity.javolin.roster;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.MissingResourceException;
import javax.swing.*;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.util.StringUtils;
import org.volity.jabber.JIDUtils;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.PlatformWrapper;
import org.volity.javolin.game.TableWindow;

/**
 * A contextual menu which appears in a RosterPanel.
 */
public class RosterContextMenu extends JPopupMenu
    implements ActionListener
{
    static final String TABLEWIN_PROP = "TableWin";

    RosterPanel mParent;
    String mJID = null;
    RosterPacket.ItemType mSubType;
    boolean mIsAvailable;
    boolean mIsSelf;

    JMenuItem mChatMenuItem;
    JMenuItem mChatUnavailMenuItem;
    JMenuItem mRosterAddMenuItem;
    JMenuItem mRosterDeleteMenuItem;
    JMenu mInvitesMenu;
    JMenuItem mStatsMenuItem;

    /** Constructor. */
    public RosterContextMenu(RosterPanel parent) {
        super();
        mParent = parent;

        buildUI();
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
    public void adjustShow(String jid, RosterPacket.ItemType subtype, 
        boolean isavail, int xpos, int ypos) {
        if (isVisible()) {
            // Menu is already in use.
            return;
        }

        JavolinApp app = JavolinApp.getSoleJavolinApp();

        mJID = jid;
        mSubType = subtype;
        mIsAvailable = isavail;
        mIsSelf = false;

        String selfjid = app.getSelfJID();
        if (selfjid != null && JIDUtils.bareMatch(selfjid, jid))
            mIsSelf = true;

        mChatMenuItem.setVisible(mIsAvailable);
        mChatMenuItem.setEnabled(!mIsSelf);
        mChatUnavailMenuItem.setVisible(!mIsAvailable);
        mChatUnavailMenuItem.setEnabled(!mIsSelf);

        mRosterAddMenuItem.setVisible(subtype == RosterPacket.ItemType.FROM);

        int count = 0;
        mInvitesMenu.removeAll();
        if (mIsAvailable && !mIsSelf) {
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

        show(mParent, xpos, ypos);
    }

    // Implements ActionListener interface
    public void actionPerformed(ActionEvent ev) {
        if (mJID == null)
            return;

        Object source = ev.getSource();
        if (source == null)
            return;

        if (source == mChatMenuItem || source == mChatUnavailMenuItem) {
            JavolinApp.getSoleJavolinApp().chatWithUser(mJID);
        }
        if (source == mStatsMenuItem) {
            JavolinApp.getSoleJavolinApp().showUserGameStats(mJID);
        }
        if (source == mRosterAddMenuItem) {
            JavolinApp.getSoleJavolinApp().doAddUser(mJID);
        }
        if (source == mRosterDeleteMenuItem) {
            JavolinApp.getSoleJavolinApp().doDeleteUser(mJID);
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

        mChatUnavailMenuItem = new JMenuItem(localize("ChatUnavail"));
        mChatUnavailMenuItem.addActionListener(this);
        add(mChatUnavailMenuItem);

        mRosterAddMenuItem = new JMenuItem(localize("RosterAdd"));
        mRosterAddMenuItem.addActionListener(this);
        add(mRosterAddMenuItem);

        mRosterDeleteMenuItem = new JMenuItem(localize("RosterDelete"));
        mRosterDeleteMenuItem.addActionListener(this);
        add(mRosterDeleteMenuItem);

        mInvitesMenu = new JMenu(localize("InviteMenu"));
        add(mInvitesMenu);

        mStatsMenuItem = new JMenuItem(localize("Stats"));
        mStatsMenuItem.addActionListener(this);
        add(mStatsMenuItem);
        if (!PlatformWrapper.launchURLAvailable())
            mStatsMenuItem.setEnabled(false);
    }
}
