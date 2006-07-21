package org.volity.javolin.game;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.MissingResourceException;
import javax.swing.*;
import org.jivesoftware.smack.util.StringUtils;
import org.volity.client.Player;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.PlatformWrapper;

/**
 * The popup (contextual) menu that appears for players in the seating chart.
 *
 * This is invoked by a JLabel in a SeatPanel, but the menu winds up attached
 * to the SeatChart itself. This makes life easier -- labels come and go, but
 * the SeatChart is a permanent part of the window. On the other hand, if a
 * player's status changes, you may wind up invoking a meaningless menu option.
 * We live with that.
 */
public class SeatContextMenu extends JPopupMenu
    implements ActionListener
{
    static final String TABLEWIN_PROP = "TableWin";

    SeatChart mChart;
    Player mPlayer = null;

    JMenuItem mChatMenuItem;
    JMenuItem mRemoveBotMenuItem;
    JMenuItem mSitMenuItem;
    JMenuItem mStandMenuItem;
    JMenuItem mStatsMenuItem;
    JMenuItem mRosterAddMenuItem;
    JMenu mInvitesMenu;

    public SeatContextMenu(SeatChart chart) {
        super();
        mChart = chart;
    }

    /** Clean up the object. */
    public void dispose() {
        removeAll();
        mPlayer = null;
        mChart = null;
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
     * Bring up the menu, attached to the given parent. The location (xpos,
     * ypos) should be relative to the parent.
     *
     * This ensures that the menu is filled with the appropriate items.
     *
     * The Player is retained for the lifetime of the menu. This is okay,
     * because a Player object is data-only and doesn't hold any interesting
     * references. Well, not too many.
     */
    public void adjustShow(Player player,
        JComponent parent, int xpos, int ypos) {

        if (isVisible()) {
            // Menu is already in use.
            return;
        }

        mPlayer = player;
        boolean isGameActive = mChart.mTable.isRefereeStateActive();
        boolean isSelf = mPlayer.isSelf();

        JavolinApp app = JavolinApp.getSoleJavolinApp();

        removeAll();
        mChatMenuItem = null;
        mRosterAddMenuItem = null;
        mRemoveBotMenuItem = null;
        mSitMenuItem = null;
        mStandMenuItem = null;
        mStatsMenuItem = null;
        mInvitesMenu = null;

        mChatMenuItem = new JMenuItem(localize("Chat"));
        mChatMenuItem.addActionListener(this);
        if (isSelf || mPlayer.isBot())
            mChatMenuItem.setEnabled(false);
        add(mChatMenuItem);

        mRosterAddMenuItem = new JMenuItem(localize("RosterAdd"));
        mRosterAddMenuItem.addActionListener(this);
        if (isSelf || mPlayer.isBot()) {
            mRosterAddMenuItem.setEnabled(false);
        }
        else {
            String barejid = StringUtils.parseBareAddress(mPlayer.getJID());
            boolean onroster = (app.getRosterPanel() != null
                && app.getRosterPanel().isUserOnRoster(barejid));
            if (onroster)
                mRosterAddMenuItem.setEnabled(false);
        }
        add(mRosterAddMenuItem);

        addSeparator();

        mSitMenuItem = new JMenuItem(localize("Sit"));
        mSitMenuItem.addActionListener(this);
        add(mSitMenuItem);

        mStandMenuItem = new JMenuItem(localize("Stand"));
        mStandMenuItem.addActionListener(this);
        add(mStandMenuItem);

        if (!isGameActive) {
            if (mPlayer.getSeat() == null) {
                mStandMenuItem.setEnabled(false);
            }
            else {
                mSitMenuItem.setEnabled(false);
            }
        }
        else {
            mStandMenuItem.setEnabled(false);
            mSitMenuItem.setEnabled(false);
        }

        if (mPlayer.isBot()) {
            mRemoveBotMenuItem = new JMenuItem(localize("RemoveBot"));
            mRemoveBotMenuItem.addActionListener(this);
            add(mRemoveBotMenuItem);
            if (mPlayer.getSeat() != null)
                mRemoveBotMenuItem.setEnabled(false);
        }

        addSeparator();

        mInvitesMenu = new JMenu(localize("InviteMenu"));
        add(mInvitesMenu);
        int count = 0;
        mInvitesMenu.removeAll();
        if (!isSelf) {
            String thistablekey = mChart.mTable.getRoom();
            for (Iterator it = app.getTableWindows(); it.hasNext(); ) {
                TableWindow win = (TableWindow)it.next();
                String key = win.getRoom();
                if (key.equals(thistablekey))
                    continue;
                JMenuItem item = new JMenuItem(win.getWindowName());
                item.addActionListener(this);
                item.putClientProperty(TABLEWIN_PROP, key);
                mInvitesMenu.add(item);
                count++;
            }
        }
        mInvitesMenu.setEnabled(count > 0);

        mStatsMenuItem = new JMenuItem(localize("Stats"));
        mStatsMenuItem.addActionListener(this);
        add(mStatsMenuItem);
        if (!PlatformWrapper.launchURLAvailable())
            mStatsMenuItem.setEnabled(false);

        show(parent, xpos, ypos);
    }

    // Implements ActionListener interface
    public void actionPerformed(ActionEvent ev) {
        if (mPlayer == null || mChart == null)
            return;

        Object source = ev.getSource();
        if (source == null)
            return;

        if (source == mChatMenuItem) {
            JavolinApp.getSoleJavolinApp().chatWithUser(mPlayer.getJID());
        }

        if (source == mRosterAddMenuItem) {
            JavolinApp.getSoleJavolinApp().doAddUser(mPlayer.getJID());
        }

        if (source == mRemoveBotMenuItem) {
            mChart.requestRemoveBot(mPlayer.getJID());
        }

        if (source == mSitMenuItem) {
            mChart.requestSeatChange(mPlayer.getJID(), mChart.ANY_SEAT);
        }

        if (source == mStandMenuItem) {
            mChart.requestSeatChange(mPlayer.getJID(), null);
        }

        if (source == mStatsMenuItem) {
            JavolinApp.getSoleJavolinApp().showUserGameStats(mPlayer.getJID());
        }

        if (source instanceof JComponent) {
            JComponent jsource = (JComponent)source;
            String key = (String)jsource.getClientProperty(TABLEWIN_PROP);
            if (key != null) {
                TableWindow win = JavolinApp.getSoleJavolinApp().getTableWindowByRoom(key);
                if (win != null) {
                    win.doInviteDialog(mPlayer.getJID(), false);
                }
            }
        }
    }
        
}
