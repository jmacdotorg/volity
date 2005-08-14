package org.volity.client;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.packet.MUCUser;
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
    addParticipantListener(new PacketListener() {
	public void processPacket(Packet packet) {
	  Presence presence = (Presence) packet;
	  MUCUser user = getUser(presence);
	  if (isReferee(user))
	    if (presence.getType() == Presence.Type.AVAILABLE) {
	      String jid = user.getItem().getJid();
	      if (jid == null) jid = presence.getFrom();
	      referee = new Referee(GameTable.this, jid);
	    } else if (presence.getType() == Presence.Type.UNAVAILABLE)
	      referee = null;
	}
      });
  }

  /**
   * Get the user extension from a Presence packet.
   * @return null if the packet has no user extension.
   */
  public static MUCUser getUser(Presence presence) {
    PacketExtension ext = presence.getExtension("x", userNamespace);
    return ext == null ? null : (MUCUser) ext;
  }

  static final String userNamespace = "http://jabber.org/protocol/muc#user";

  /**
   * Get the user information for a participant.
   * @param participant a fully-qualified MUC JID, e.g. an element of
   *                    getParticipants()
   */
  public MUCUser getUser(String participant) {
    return getUser(getParticipantPresence(participant));
  }


  XMPPConnection connection;
  public XMPPConnection getConnection() { return connection; }

  Referee referee;

  /**
   * The referee for this table, or null if no referee is connected.
   */
  public Referee getReferee() {
    return referee;
  }

  /**
   * Is a user the referee?
   */
  public static boolean isReferee(MUCUser user) {
    return user != null && user.getItem().getAffiliation().equals("owner");
  }

  /**
   * Is a participant the referee?
   * @param participant a fully-qualified MUC JID, e.g. an element of
   *                    getParticipants()
   */
  public boolean isReferee(String participant) {
    return isReferee(getUser(participant));
  }

  /**
   * Get a list of opponent nicknames. I.e. participants not including
   * the referee or myself.
   */
  public List getOpponents() {
    List opponents = new ArrayList();
    for (Iterator it = getParticipants(); it.hasNext();) {
      String participant = (String) it.next();
      if (!isReferee(participant)) {
	String nickname = StringUtils.parseResource(participant);
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
