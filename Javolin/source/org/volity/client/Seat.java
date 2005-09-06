package org.volity.client;

import java.util.*;

/**
 * A seat at a table.
 */
public class Seat {

    List mPlayers = new ArrayList();
    String mId;
    boolean mRequired;
    
    public Seat(String seatId) {
	mId = seatId;
        mRequired = false;
    }

    public String id() {
	return mId;
    }

    public boolean required() {
        return mRequired;
    }

    public void setRequired(boolean val) {
        mRequired = val;
    }

    public void addPlayer(String jid) {
	if (mPlayers.indexOf(jid) > -1) {
	    // ###Throw an exception here?
	} else {
	    mPlayers.add(jid);
	}
    }

    public void removePlayer(String jid) {
	int index = mPlayers.indexOf(jid);
	if (index > -1) {
	    mPlayers.remove(index);
	} else {
	    // ###Throw an exception?
	}
    }

    public List players() {
	return mPlayers;
    }

    public boolean isOccupied() {
	if (mPlayers.isEmpty()) {
	    return false;
	} else {
	    return true;
	}
    }


}
