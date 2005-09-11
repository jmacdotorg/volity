package org.volity.javolin.game;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import org.volity.client.*;
import org.volity.javolin.chat.UserColorMap;

/**
 * The SeatChart is a UI component which displays the table, the seats, and the
 * players. In other words, it is a view of the GameTable.
 *
 * (It is not actually a JComponent -- you call getComponent to get that.)
 *
 * The component, in Swing terms, is a JPanel containing one or more SeatPanel
 * components. Each SeatPanel represents one seat; and there's one additional
 * one for the list of unseated players.
 */
public class SeatChart 
    implements StatusListener
{
    GameTable mTable;
    JPanel mPanel;
    UserColorMap mUserColorMap;
    TranslateToken mTranslator;

    Map mSeatPanels;        // maps String IDs to SeatPanel objects
    SeatPanel mUnseatPanel; // for unseated players

    /**
     * @param table the GameTable to watch. (The SeatChart sets itself up as a
     *     listener to the table.)
     * @param colormap a map of user names to colors.
     * @param translator a translator object (used for seat IDs).
     */
    public SeatChart(GameTable table, UserColorMap colormap,
        TranslateToken translator) {
        mTable = table;
        mUserColorMap = colormap;
        mTranslator = translator;
        mSeatPanels = new HashMap();

        mPanel = new JPanel(new GridBagLayout());

        mUnseatPanel = new SeatPanel(this, null, true);
        /* Conceivably the GameTable already has a list of Seats, in which case
         * we should do createPanels now. If not, this will be a no-op. */
        createPanels();
        adjustPanelLayout();

        mTable.addStatusListener(this);
    }

    /** Return the UI component that displays the chart. */
    public JComponent getComponent() {
        return mPanel;
    }

    /** Return the translator. */
    public TranslateToken getTranslator() {
        return mTranslator;
    }

    /**
     * Create the SeatPanel objects for all the seats. (Not the "unseated"
     * panel -- that's created at constructor time.)
     *
     * The SeatChart expects this to be called exactly once; or, if called
     * again, to contain the same list as before.
     */
    protected void createPanels() {
        for (Iterator it = mTable.getSeats(); it.hasNext(); ) {
            Seat seat = (Seat)it.next();
            String id = seat.getID();
            boolean flag = (seat.isRequired() || seat.isOccupied());
            SeatPanel panel = new SeatPanel(this, id, flag);
            mSeatPanels.put(id, panel);
        }
    }

    /**
     * Go through the panels and see if any of them need to be made visible or
     * hidden. If so, call adjustPanelLayout.
     *
     * Call this after any event which might have changed panel visibility.
     */
    protected void checkPanelVisibility() {
        boolean changes = false;

        for (Iterator it = mTable.getSeats(); it.hasNext(); ) {
            Seat seat = (Seat)it.next();
            SeatPanel panel = (SeatPanel)mSeatPanels.get(seat.getID());
            boolean flag = (seat.isRequired() || seat.isOccupied());

            if (flag != panel.mVisible) {
                panel.mVisible = flag;
                changes = true;
            }
        }

        if (changes) {
            /* A seat became visible or invisible, so we have to redo the panel
             * layout. */
            adjustPanelLayout();
        }
    }

    /**
     * Rebuild the chart: empty it, and then add in all the SeatPanels which
     * need to be visible.
     *
     * This does not itself decide whether a panel needs to be visible. It
     * relies on checkPanelVisibility having been called.
     */
    protected void adjustPanelLayout() {
        mPanel.removeAll();

        GridBagConstraints c;
        int row = 0;

        for (Iterator it = mTable.getSeats(); it.hasNext(); ) {
            Seat seat = (Seat)it.next();
            SeatPanel panel = (SeatPanel)mSeatPanels.get(seat.getID());

            if (panel.mVisible) {
                c = new GridBagConstraints();
                c.gridx = 0;
                c.gridy = row++;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.anchor = GridBagConstraints.NORTH;
                c.weightx = 1;
                c.weighty = 0;
                mPanel.add(panel, c);
            }
        }        

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        c.weightx = 1;
        c.weighty = 1;
        mPanel.add(mUnseatPanel, c);

        mPanel.revalidate();
    }

    /**
     * Rebuild one panel: empty it, and recreate its list of names and
     * icons.
     *
     * @param player rebuild the panel this player is sitting (or standing) in.
     */
    protected void adjustOnePanel(Player player) {
        adjustOnePanel(player.getSeat());
    }

    /**
     * Rebuild one panel: empty it, and recreate its list of names and
     * icons. 
     *
     * @param seat rebuild the panel for this seat. (If null, rebuild the
     *     "unseated" panel.)
     */
    protected void adjustOnePanel(Seat seat) {
        SeatPanel panel;
        if (seat == null) {
            panel = mUnseatPanel;
            panel.adjustNames(mTable.getUnseatedPlayers());
        }
        else {
            panel = (SeatPanel)mSeatPanels.get(seat.getID());
            panel.adjustNames(seat.getPlayers());
        }
    }

    /***** Methods which implement StatusListener. *****/

    public void seatListKnown() {
        System.out.println("Known seats changed.");
        createPanels();
        adjustPanelLayout();
    }
    public void requiredSeatsChanged() {
        System.out.println("Required seats changed.");
        checkPanelVisibility();
    }

    public void playerJoined(Player player) {
        System.out.println("Player joined: " + player.getJID() + " (" + player.getNick() + ")");
        adjustOnePanel(player);
        checkPanelVisibility();
    }
    public void playerLeft(Player player) {
        System.out.println("Player left: " + player.getJID() + " (" + player.getNick() + ")");
        adjustOnePanel(player);
        checkPanelVisibility();
    }
    public void playerNickChanged(Player player, String oldNick) {
        System.out.println("Player nickname changed: " + player.getJID() + " (" + player.getNick() + "), was (" + oldNick + ")");
        mUserColorMap.changeUserName(oldNick, player.getNick());
        adjustOnePanel(player);
    }

    public void playerSeatChanged(Player player, Seat oldseat, Seat newseat) {
        String oldid = "<unseated>";
        String newid = "<unseated>";
        if (oldseat != null)
            oldid = oldseat.getID();
        if (newseat != null)
            newid = newseat.getID();
        System.out.println("Player seat: " + player.getJID() + " from " + oldid + " to " + newid);
        adjustOnePanel(oldseat);
        adjustOnePanel(newseat);
        checkPanelVisibility();
    }
    public void playerReady(Player player, boolean flag) {
        System.out.println("Player ready: " + player.getJID() + ": " + flag);
        adjustOnePanel(player);
    }

}

