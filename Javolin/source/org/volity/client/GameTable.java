package org.volity.client;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.DefaultParticipantStatusListener;
import org.jivesoftware.smackx.muc.Occupant;
import java.util.*;

/** A game table (a Multi-User Chat room for playing a Volity game). */
public class GameTable extends MultiUserChat {

    public ArrayList mReadyPlayers = new ArrayList(); //###
    public Map mSeatsById = new HashMap();
    public List mSeats = new ArrayList();
    protected boolean mInitialJoined = false;
    protected List statusListeners = new ArrayList();
    protected List readyListeners = new ArrayList();
    public int mGameStatus = 0; //###

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
            if (!mInitialJoined) {
              mInitialJoined = true;
              fireReadyListeners();
            }
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

    /***** Table readiness change methods *****/

    /**
     * Listener interface for addReadyListener / removeReadyListener. This
     * allows a listener to be notified when the GameTable has successfully
     * joined the MUC and located the referee.
     */
    public interface ReadyListener {
        /**
         * Report that the GameTable has successfully joined the MUC
         * and located the referee.
         */
        public abstract void ready();
    }

    /** Add a table-joined listener. */
    public void addReadyListener(ReadyListener listener) {
	readyListeners.add(listener);
    }
    
    /** Remove a table-joined listener. */
    public void removeReadyListener(ReadyListener listener) {
	readyListeners.remove(listener);
    }

    /**
     * Notify all listeners that have registered for notification of
     * MUC-joinedness.
     */
    private void fireReadyListeners()
    {
        Iterator iter = readyListeners.iterator();
        while (iter.hasNext())
        {
            ((ReadyListener)iter.next()).ready();
        }
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
    }
    
    /** Remove a player readiness change listener. */
    public void removeStatusListener(StatusListener listener) {
	statusListeners.remove(listener);
    }

    /***** Dealing with seats *****/

    public void setSeats(List ids) {
	System.out.println("Setting the seat list: " + ids.toString()); //###

        if (!mSeats.isEmpty()) {
            /* If we already have a list of seats, this should be an identical
             * list. Let's check that, though. */

            if (ids.size() != mSeats.size()) {
                System.err.println("New set_seats list has different length from the original set_seats list.");
                return;
            }

            for (Iterator it = ids.iterator(); it.hasNext(); ) {
                String seatId = (String)it.next();
                if (!mSeatsById.containsKey(seatId)) {
                    System.err.println("New set_seats list does not match the original set_seats list.");
                    return;
                }
            }

            // All okay.
            return;
        }

        /* Create our seats list. */

	for (Iterator it = ids.iterator(); it.hasNext(); ) {
	    String seatId = (String)it.next();
	    Seat seat = new Seat(seatId);
	    mSeats.add(seat);
            mSeatsById.put(seatId, seat);
	}
    }


    /** Set the list of required seat IDs. */
    public void setRequiredSeats(List ids) {
	System.out.println("Setting the required seats: " + ids.toString()); //###

        /* The cleanest way to do this is to iterate through the seats, marking
         * each one "required" or "optional". To do this, we'll need a Set of
         * required seats. */

        Set requiredSet = new HashSet(ids);
        boolean changes = false;

        for (Iterator it = mSeats.iterator(); it.hasNext(); ) {
            Seat seat = (Seat)it.next();
            boolean required = requiredSet.contains(seat.id());
            if (required != seat.required()) {
                seat.setRequired(false);
                changes = true;
            }
        }

        if (changes) {
	    System.out.println("Some seats changed requiredness status");
            fireStatusListeners_requiredSeatsChanged();
        }
    }

    /** Get a seat by ID. (Or null, if there is no such seat.) */
    public Seat getSeat(String id) {
        return (Seat)mSeatsById.get(id);
    }

    /** Return an array of seat objects which have players in them. */
    public List occupiedSeats() {
	List occupiedSeats = new ArrayList();
	for (Iterator it = mSeats.iterator(); it.hasNext();) {
	    Seat seat = (Seat)it.next();
	    if (seat.isOccupied()) {
		occupiedSeats.add(seat);
	    }
	}
	return occupiedSeats;
    }

    private void fireStatusListeners_requiredSeatsChanged()
    {
        Iterator iter = statusListeners.iterator();
        while (iter.hasNext())
        {
            ((StatusListener)iter.next()).requiredSeatsChanged();
        }
    }
}
