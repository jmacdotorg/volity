package org.volity.client;

import java.util.*;

/**
 * A seat at a table. This is a dumb, do-what-you're-told class.
 */
public class Seat {

    List mPlayers = new ArrayList();
    String mId;
    boolean mRequired;
    boolean mActive;

    /** Constructor. */    
    public Seat(String seatId) {
        mId = seatId;
        mRequired = false;
        mActive = false;
    }

    /** Get the seat ID. */
    public String getID() {
        return mId;
    }

    /** Is this a required seat? */
    public boolean isRequired() {
        return mRequired;
    }

    /**
     * Set whether this is a required seat. This is done from the
     * volity.required_seat_list RPC.
     */
    protected void setRequired(boolean val) {
        mRequired = val;
    }

    /** Add a player to this seat. */
    protected void addPlayer(Player player) {
        if (mPlayers.indexOf(player) > -1) {
            // already there
        }
        else {
            mPlayers.add(player);
        }
    }

    /** Remove a player from this seat. */
    protected void removePlayer(Player player) {
        int index = mPlayers.indexOf(player);
        if (index > -1) {
            mPlayers.remove(index);
        }
        else {
            // already gone
        }
    }

    /** Get the list of players sitting in this seat. */
    public Iterator getPlayers() {
        return mPlayers.iterator();
    }

    /** Is this seat occupied? */
    public boolean isOccupied() {
        if (mPlayers.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Set whether this seat is active -- that is, involved in the current
     * game. This is done when the game leaves the SETUP state. It is cleared
     * again when the game returns to SETUP.
     */
    protected void setActive(boolean val) {
        mActive = val;
    }

    /** Is this seat active in the current game? */
    public boolean isActive() {
        return mActive;
    }
}
