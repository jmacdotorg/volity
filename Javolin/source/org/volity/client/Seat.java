package org.volity.client;

import java.util.*;

/**
 * A seat at a table. This is a dumb, do-what-you're-told class.
 */
public class Seat {

    List mPlayers = new ArrayList();
    String mId;
    boolean mRequired;
    
    public Seat(String seatId) {
	mId = seatId;
        mRequired = false;
    }

    public String getID() {
	return mId;
    }

    public boolean isRequired() {
        return mRequired;
    }

    protected void setRequired(boolean val) {
        mRequired = val;
    }

    protected void addPlayer(Player player) {
	if (mPlayers.indexOf(player) > -1) {
	    // already there
	}
        else {
	    mPlayers.add(player);
	}
    }

    protected void removePlayer(Player player) {
	int index = mPlayers.indexOf(player);
	if (index > -1) {
	    mPlayers.remove(index);
	}
        else {
	    // already gone
	}
    }

    public Iterator getPlayers() {
	return mPlayers.iterator();
    }

    public boolean isOccupied() {
	if (mPlayers.isEmpty()) {
	    return false;
	} else {
	    return true;
	}
    }


}
