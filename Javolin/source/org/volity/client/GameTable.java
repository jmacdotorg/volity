package org.volity.client;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.DefaultParticipantStatusListener;
import org.jivesoftware.smackx.muc.Occupant;
import java.util.*;

/** A game table (a Multi-User Chat room for playing a Volity game). */
public class GameTable extends MultiUserChat {

    public ArrayList mReadyPlayers = new ArrayList();
    public Hashtable mSeatsById = new Hashtable();
    public ArrayList mSeats = new ArrayList();
    public ArrayList statusListeners = new ArrayList();
    public int mGameStatus = 0;
    public ArrayList mRequiredSeatIds = new ArrayList();

  /**
   * @param connection an authenticated connection to an XMPP server.
   * @param room the JID of the game table.
   */
  public GameTable(XMPPConnection connection, String room) {
    super(connection, room);
    this.connection = connection;
    addParticipantStatusListener(new DefaultParticipantStatusListener() {
	public void joined(String roomJID) {
	  Occupant occupant = getOccupant(roomJID);
	  if (occupant != null && isReferee(occupant)) {
	    refereeRoomJID = roomJID;
	    referee = new Referee(GameTable.this, occupant.getJid());
	  }
	}
	public void left(String roomJID) { unjoined(roomJID); }
	public void kicked(String roomJID) { unjoined(roomJID); }
	public void banned(String roomJID) { unjoined(roomJID); }
	/**
	 * Called when an occupant is no longer in the room, either
	 * because it left or was kicked or banned.
	 */
	void unjoined(String roomJID) {
	  // FIXME: This assumes the referee's nickname doesn't
	  // change!  But keeping track of changed nicknames is
	  // difficult currently.  See
	  // http://www.jivesoftware.org/issues/browse/SMACK-55.
	  if (roomJID.equals(refereeRoomJID)) {
	    refereeRoomJID = null;
	    referee = null;
	  }
	}
      });
  }

  XMPPConnection connection;
  public XMPPConnection getConnection() { return connection; }

  String refereeRoomJID;
  Referee referee;

  /**
   * The referee for this table, or null if no referee is connected.
   */
  public Referee getReferee() {
    return referee;
  }

  /**
   * Is an occupant the referee?
   */
  public boolean isReferee(Occupant occupant) {
    return occupant.getAffiliation().equals("owner");
  }

  /**
   * Get a list of opponent nicknames, i.e., occupants not including
   * the referee or myself.
   */
  public List getOpponents() {
    List opponents = new ArrayList();
    for (Iterator it = getOccupants(); it.hasNext();) {
      String roomJID = (String) it.next();
      Occupant occupant = getOccupant(roomJID);
      if (occupant != null && !isReferee(occupant)) {
	String nickname = occupant.getNick();
	if (!getNickname().equals(nickname))
	  opponents.add(nickname);
      }
    }
    return opponents;
  }

    /***** Player status-change methods & callbacks *****/

    /**
     * Return truth if the player is ready, falsehood otherwise.
     */
    public boolean isPlayerReady(String jid) {
	boolean readiness;
	if (mReadyPlayers.indexOf(jid) > -1) {
	    return true;
	} else {
	    return false;
	}
    }

    /**
     * Declare that player is ready.
     * @param jid The JID of a player.
     */ 
    public void playerIsReady(String jid) {
	if (mReadyPlayers.indexOf(jid) == -1) {
	    mReadyPlayers.add(jid);
	}
    }

    /**
     * Declare that player is unready.
     * @param jid The JID of a player.
     */ 
    public void playerIsUnready(String jid) {
	int index = mReadyPlayers.indexOf(jid);
	if (index > -1) {
	    mReadyPlayers.remove(index);
	}
    }

    /** Add a player readiness change listener. */
    public void addStatusListener(StatusListener listener) {
	statusListeners.add(listener);
	StatusListener foo = (StatusListener)statusListeners.get(0);
    }
    
    /** Remove a player readiness change listener. */
    public void removeStatusListener(StatusListener listener) {
	statusListeners.remove(listener);
    }


    /** Return the array of required seat IDs. */
    public ArrayList requiredSeatIds() {
	return mRequiredSeatIds;
    }

    /** Set the array of required seat IDs. */
    public void setRequiredSeatIds(ArrayList requiredSeatIds) {
	mRequiredSeatIds = requiredSeatIds;
    }

    /** Return an array of seat objects which have players in them. */
    public ArrayList occupiedSeats() {
	ArrayList occupiedSeats = new ArrayList();
	for (Iterator it = mSeats.iterator(); it.hasNext();) {
	    Seat seat = (Seat)it.next();
	    if (seat.isOccupied()) {
		occupiedSeats.add(seat);
	    }
	}
	return occupiedSeats;
    }

    /** 
     * Return an array of seat objects which are worth displaying
     * in the seat UI.
     */
    public ArrayList seatsToDisplay() {
	ArrayList seatsToDisplay = new ArrayList();
	for (Iterator it = mSeats.iterator(); it.hasNext();) {
	    Seat seat = (Seat)it.next();
	    if (seat.isOccupied()) {
		seatsToDisplay.add(seat);
	    } else if (mRequiredSeatIds.indexOf(seat.id()) > -1) {
		seatsToDisplay.add(seat);
	    }
	}
	return seatsToDisplay;
    }

}
