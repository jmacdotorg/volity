package org.volity.javolin.game;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import org.volity.client.Player;
import org.volity.javolin.JavolinApp;

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
    SeatChart mChart;
    Player mPlayer = null;

    JMenuItem mChatMenuItem;
    JMenuItem mRemoveBotMenuItem;
    JMenuItem mSitMenuItem;
    JMenuItem mStandMenuItem;

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

        removeAll();
        mChatMenuItem = null;
        mRemoveBotMenuItem = null;
        mSitMenuItem = null;
        mStandMenuItem = null;

        mChatMenuItem = new JMenuItem("Chat With User");
        mChatMenuItem.addActionListener(this);
        if (mPlayer.isSelf() || mPlayer.isBot())
            mChatMenuItem.setEnabled(false);
        add(mChatMenuItem);

        addSeparator();

        mSitMenuItem = new JMenuItem("Sit Down");
        mSitMenuItem.addActionListener(this);
        add(mSitMenuItem);

        mStandMenuItem = new JMenuItem("Stand Up");
        mStandMenuItem.addActionListener(this);
        add(mStandMenuItem);

        if (mPlayer.getSeat() == null) {
            mStandMenuItem.setEnabled(false);
        }
        else {
            mSitMenuItem.setEnabled(false);
        }

        if (mPlayer.isBot()) {
            mRemoveBotMenuItem = new JMenuItem("Remove Bot");
            mRemoveBotMenuItem.addActionListener(this);
            add(mRemoveBotMenuItem);
        }

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

        if (source == mRemoveBotMenuItem) {
            mChart.requestRemoveBot(mPlayer.getJID());
        }

        if (source == mSitMenuItem) {
            mChart.requestSeatChange(mPlayer.getJID(), mChart.ANY_SEAT);
        }

        if (source == mStandMenuItem) {
            mChart.requestSeatChange(mPlayer.getJID(), null);
        }
    }
        
}
