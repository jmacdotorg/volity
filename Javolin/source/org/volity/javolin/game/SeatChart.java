package org.volity.javolin.game;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.SimpleAttributeSet;
import org.volity.client.*;
import org.volity.client.comm.RPCBackground;
import org.volity.client.data.Metadata;
import org.volity.client.translate.TokenFailure;
import org.volity.client.translate.TranslateToken;
import org.volity.javolin.ErrorWrapper;
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
    public final static String ANY_SEAT = "";

    GameTable mTable;
    JPanel mPanel;
    UserColorMap mColorMap;
    Map mSeatColors;
    TranslateToken mTranslator;
    GameUI.MessageHandler mMessageHandler;
    Metadata.Provider mMetadataProvider;

    Map mSeatPanels;        // maps String IDs to SeatPanel objects
    SeatPanel mUnseatPanel; // for unseated players
    SeatPanel mNextSeatPanel; // for sitting in a previously unoccupied seat

    /* These values are tracked for audio alerts. They are updated from inside
     * SeatPanel.
     */
    String mCurrentSeat = null;
    String mCurrentSelfMark = GameTable.MARK_NONE;

    SeatContextMenu mPopupMenu = null;

    ChangeListener mColorChangeListener;
    RPCBackground.Callback mDefaultCallback;
    List mSeatMarkListeners = new ArrayList();

    /**
     * @param table the GameTable to watch. (The SeatChart sets itself up as a
     *     listener to the table.)
     * @param colormap a map of user names to colors.
     * @param translator a translator object (used for seat IDs).
     */
    public SeatChart(GameTable table, UserColorMap colormap,
        Metadata.Provider metadataProvider,
        TranslateToken translator, GameUI.MessageHandler messageHandler) {
        mTable = table;
        mColorMap = colormap;
        mSeatColors = null;
        mTranslator = translator;
        mMessageHandler = messageHandler;
        mMetadataProvider = metadataProvider;
        mSeatPanels = new HashMap();

        mPanel = new JPanel(new GridBagLayout());
        mPanel.setOpaque(true);
        mPanel.setBackground(Color.WHITE);

        mUnseatPanel = new SeatPanel(this, null, true);
        mNextSeatPanel = new SeatPanel(this, ANY_SEAT, false);
        /* Conceivably the GameTable already has a list of Seats, in which case
         * we should do createPanels now. If not, this will be a no-op. */
        createPanels();
        checkPanelVisibility(true);
        /* Adjust the seats. We iterate over the SeatPanels we've got, not the
         * Seats in the GameTable. This is to guard against new seats arriving
         * in the middle of the operation. */
        adjustOnePanel((Seat)null);
        for (Iterator it = mSeatPanels.keySet().iterator(); it.hasNext(); ) {
            String id = (String)it.next();
            adjustOnePanel(mTable.getSeat(id));
        }

        mPopupMenu = new SeatContextMenu(this);

        mDefaultCallback = new RPCBackground.Callback() {
                public void run(Object result, Exception err, Object rock) {
                    if (err != null) {
                        if (err instanceof TokenFailure) {
                            mMessageHandler.print(mTranslator.translate((TokenFailure)err));
                        }
                        else {
                            new ErrorWrapper(err);
                            mMessageHandler.print(err.toString());
                        }
                    }
                }
            };
        
        mTable.addStatusListener(this);

        mColorChangeListener = new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    // redraw seat panels -- italicization may have changed
                    for (Iterator it = mTable.getSeats(); it.hasNext(); ) {
                        Seat seat = (Seat)it.next();
                        adjustOnePanel(seat);
                    }
                    adjustOnePanel((Seat)null);
                }
            };
        mColorMap.addListener(mColorChangeListener);
    }

    /** Clean up this component. */
    public void dispose() {
        mTable.removeStatusListener(this);
        mColorMap.removeListener(mColorChangeListener);
        if (mPopupMenu != null) {
            mPopupMenu.dispose();
            mPopupMenu = null;
        }
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
     * We are now in the given seat, with the given mark. This can be caused by
     * changing seats or by a seat's mark changing. Or just by a redraw of the
     * seat panel -- we have to filter out non-changes.
     */
    protected void setSeatAndMark(String seatid, String mark) {
        // Yes, we use == on strings here. It's okay.
        if (mCurrentSeat == seatid && mCurrentSelfMark == mark)
            return;

        String oldseat = mCurrentSeat;
        String oldmark = mCurrentSelfMark;

        mCurrentSeat = seatid;
        mCurrentSelfMark = mark;

        if (oldmark != mCurrentSelfMark) {
            fireSeatMarkListeners(mCurrentSelfMark, oldseat == mCurrentSeat);
        }
    }

    /**
     * Get the canonical color for a seat.
     *
     * This parses the metadata (when it's available), and caches the result.
     */
    public Color getSeatColor(String id) {
        if (mSeatColors != null)
            return (Color)mSeatColors.get(id);

        Metadata metadata = mMetadataProvider.getMetadata();
        if (metadata == null)
            return null;

        mSeatColors = new HashMap();

        List ls = metadata.getAll(Metadata.VOLITY_SEAT_COLOR);

        for (int ix=0; ix<ls.size(); ix++) {
            String val = (String)ls.get(ix);
            int pos = val.lastIndexOf('#');
            if (pos < 0)
                continue;
            String seatid = val.substring(0, pos).trim();
            Color col = GameUI.parseColor(val.substring(pos));
            if (col != null) {
                mSeatColors.put(seatid, col);
            }
        }

        return (Color)mSeatColors.get(id);
    }

    /**
     * Adjust the player's ColorMap color to the seat color, if necessary. (The
     * UserColorMap decides if it's necessary.)
     */
    public void adjustPlayerColor(Player player, Seat seat) {
        Color col = null;
        if (seat != null)
            col = getSeatColor(seat.getID());
        mColorMap.changeOverrideColor(player.getJID(), col);
    }

    /**
     * Create the SeatPanel objects for all the seats. (Not the "unseated"
     * panel -- that's created directly by the constructor.)
     *
     * The SeatChart expects this to be called exactly once; or, if called
     * again, to contain the same list as before.
     */
    protected void createPanels() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        for (Iterator it = mTable.getSeats(); it.hasNext(); ) {
            Seat seat = (Seat)it.next();
            String id = seat.getID();
            boolean flag = (seat.isRequired() || seat.isOccupied() || seat.isActive());
            SeatPanel panel = new SeatPanel(this, id, flag);
            mSeatPanels.put(id, panel);
        }
    }

    /**
     * Go through the panels and see if any of them need to be made visible or
     * hidden. If so, call adjustPanelLayout.
     *
     * Call this after any event which might have changed panel visibility.
     *
     * If the argument is true, call adjustPanelLayout whether or not any
     * visibility changed. (This is used after createPanels to do the initial
     * chart layout.)
     */
    protected void checkPanelVisibility(boolean forceAdjust) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        boolean anyhidden = false;
        boolean changes = false;

        for (Iterator it = mTable.getSeats(); it.hasNext(); ) {
            Seat seat = (Seat)it.next();
            SeatPanel panel = (SeatPanel)mSeatPanels.get(seat.getID());
            if (panel == null)
                continue;

            boolean flag = (seat.isRequired() || seat.isOccupied() || seat.isActive());

            if (!flag)
                anyhidden = true;

            if (flag != panel.mVisible) {
                panel.mVisible = flag;
                changes = true;
            }
        }

        boolean shownext = (anyhidden 
            && mTable.getRefereeState() == GameTable.STATE_SETUP);

        if (shownext != mNextSeatPanel.mVisible) {
            mNextSeatPanel.mVisible = shownext;
            changes = true;
        }

        if (changes || forceAdjust) {
            /* A seat became visible or invisible, so we have to redo the panel
             * layout. (Or the adjustment was requested by the caller.) */
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
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

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

        if (mNextSeatPanel.mVisible) {
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTH;
            c.weightx = 1;
            c.weighty = 0;
            mPanel.add(mNextSeatPanel, c);
        }

        // The stretchy "observer" panel. (It's stretchy so that the whole
        // bottom part of the seating UI is a valid drag target.)
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.fill = GridBagConstraints.BOTH;
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
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

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
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        SeatPanel panel;
        if (seat == null) {
            panel = mUnseatPanel;
            panel.adjustNames(mTable.getUnseatedPlayers());
        }
        else {
            panel = (SeatPanel)mSeatPanels.get(seat.getID());
            if (panel != null)
                panel.adjustNames(seat.getPlayers());
        }
    }

    /**
     * Send a sit or stand request to the referee. This is used by the
     * drag-drop and popup menu mechanisms; that's why the arguments are
     * Strings (which must be converted to Player/Seat objects).
     *
     * @param jid the player who is to sit/stand.
     * @param seatid the seat to sit in (or null to stand, or ANY_SEAT to sit
     *    anywhere).
     */
    protected void requestSeatChange(String jid, String seatid) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        Seat seat;
        Player player;

        player = mTable.getPlayerByJID(jid);
        if (player == null)
            return;

        if (seatid == null) {
            mTable.getReferee().stand(player, mDefaultCallback, null);
        }
        else if (seatid == ANY_SEAT) {
            mTable.getReferee().sit(player, mDefaultCallback, null);
        }
        else {
            seat = mTable.getSeat(seatid);
            mTable.getReferee().sit(player, seat, mDefaultCallback, null);
        }
    }

    /**
     * Send a remove-bot request to the referee. This is used by the popup menu
     * mechanism; that's why the argument is a String.
     */
    protected void requestRemoveBot(String jid) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        mTable.getReferee().removeBot(jid, mDefaultCallback, null);
    }
    
    /** The pop-up menu (shared among all the items in the chart) */
    public void displayPopupMenu(Player player, int xpos, int ypos) {
        Point pt = mPanel.getLocationOnScreen();
        mPopupMenu.adjustShow(player, mPanel, xpos-pt.x, ypos-pt.y);
    }

    /**
     * Interface for those interested in changes to the player's seat mark. 
     *
     * Listeners will only be called when the mark actually changes. The mark
     * will be one of the GameTable.MARK_* constants. If bygame is true, the
     * event is caused by the game actually changing the seat's mark. If false,
     * the event is caused by the player switching to a different seat (with a
     * different mark).
     */
    public interface SeatMarkListener {
        public void markChanged(String mark, boolean bygame);
    }

    /** Add a listener for changes of the player's seat mark. */
    public void addSeatMarkListener(SeatMarkListener listener) {
        mSeatMarkListeners.add(listener);
    }

    /** Remove a listener for changes of the player's seat mark. */
    public void removeSeatMarkListener(SeatMarkListener listener) {
        mSeatMarkListeners.remove(listener);
    }

    /** Notify listeners of a seat mark change. */
    protected void fireSeatMarkListeners(String mark, boolean bygame) {
        for (Iterator iter = mSeatMarkListeners.iterator(); iter.hasNext(); ) {
            ((SeatMarkListener)iter.next()).markChanged(mark, bygame);
        }
    }

    /***** Methods which implement StatusListener. *****/

    /* All these methods are called from outside the Swing thread. */

    public void stateChanged(int newstate) {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    // redraw seat panels -- italicization may have changed
                    for (Iterator it = mTable.getSeats(); it.hasNext(); ) {
                        Seat seat = (Seat)it.next();
                        adjustOnePanel(seat);
                    }
                    // check visibility, because the ANY_SEAT panel may vanish
                    checkPanelVisibility(false);
                }
            });
    }

    public void seatListKnown() {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    createPanels();
                    checkPanelVisibility(true);
                }
            });
    }

    public void requiredSeatsChanged() {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    checkPanelVisibility(false);
                }
            });
    }

    public void seatMarksChanged(final List seats) {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    for (int ix=0; ix<seats.size(); ix++) {
                        Seat seat = (Seat)(seats.get(ix));
                        adjustOnePanel(seat);
                    }
                }
            });
    }

    public void playerJoined(final Player player) {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    adjustOnePanel(player);
                    checkPanelVisibility(false);
                }
            });
    }

    public void playerLeft(final Player player) {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    adjustOnePanel(player);
                    checkPanelVisibility(false);
                }
            });
    }

    public void playerIsReferee(final Player player) {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    mColorMap.setUserColor(player.getJID(),
                        Color.GRAY);
                    adjustOnePanel(player);
                }
            });
    }

    public void playerNickChanged(final Player player, final String oldNick) {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    adjustOnePanel(player);
                }
            });
    }

    public void playerSeatChanged(final Player player, 
        final Seat oldseat, final Seat newseat) {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    adjustOnePanel(oldseat);
                    adjustOnePanel(newseat);
                    checkPanelVisibility(false);
                    adjustPlayerColor(player, newseat);
                }
            });
    }

    public void playerReady(final Player player, boolean flag) {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    adjustOnePanel(player);
                }
            });
    }

}

