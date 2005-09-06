package org.volity.client;

import java.util.*;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.DefaultParticipantStatusListener;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;

/** A game table (a Multi-User Chat room for playing a Volity game). */
public class GameTable extends MultiUserChat {

    protected List mPlayers = new ArrayList();
    protected Map mSeatsById = new HashMap();
    protected List mSeats = new ArrayList();
    protected boolean mInitialJoined = false;
    protected List statusListeners = new ArrayList();
    protected List readyListeners = new ArrayList();

    protected XMPPConnection mConnection;
    protected String refereeRoomJID;
    protected Referee referee;


    /**
     * @param connection an authenticated connection to an XMPP server.
     * @param room the JID of the game table.
     */
    public GameTable(XMPPConnection connection, String room) {
        super(connection, room);
        mConnection = connection;

        addParticipantStatusListener(new DefaultParticipantStatusListener() {
                public void joined(String roomJID) {
                    Occupant occupant = getOccupant(roomJID);
                    if (occupant == null)
                        return;
                    if (isReferee(occupant)) {
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

        addParticipantListener(new PacketListener() {
                public void processPacket(Packet packet) {
                    rescanOccupantList();
                }
            });

    }

    public XMPPConnection getConnection() { return mConnection; }

    /**
     * The referee for this table, or null if no referee is connected.
     */
    public Referee getReferee() {
        return referee;
    }
    
    /**
     * Is an occupant the referee?
     */
    private boolean isReferee(Occupant occupant) {
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
     * We want to maintain an accurate, ongoing list of players (represented as
     * Player objects, which contain Occupant objects). However, Smack doesn't
     * provide great tools to do this. Not only is the nicknameChanged()
     * listener broken, but the joined() listener is not reliably called for
     * the player's own JID. On top of that, the Smack API represents
     * everything as JIDs, when we really want to use Player references.
     *
     * Therefore, we do it the dumb way. Every time we get a presence packet,
     * we re-scan the MUC object's list of Occupants -- check for newcomers and
     * missing, er, oldgoers. We then adjust our own list and send (smarter)
     * notifications to our own listeners.
     *
     * The PacketListener interface notes that all listening is done in the
     * same thread. That makes this method simpler (no worries about
     * synchonization), but it also means that if this takes a long time,
     * packet reception will lag. I think we're okay for the moment.
     */
    protected void rescanOccupantList() {
        Map occupantMap = new HashMap(); // maps JID to Occupant
        for (Iterator it = getOccupants(); it.hasNext(); ) {
            String jid = (String)it.next();
            Occupant occ = getOccupant(jid);
            if (occ != null) {
                occupantMap.put(occ.getJid(), occ);
            }
        }

        List gonePlayers = null;
        List newPlayers = null;
        List nickPlayers = null;

        for (Iterator it = mPlayers.iterator(); it.hasNext(); ) {
            Player player = (Player)it.next();
            String jid = player.getJID();
            if (occupantMap.containsKey(jid)) {
                Occupant occ = (Occupant)occupantMap.get(jid);
                if (!occ.getNick().equals(player.getNick())) {
                    player.setNick(occ.getNick());
                    if (nickPlayers == null)
                        nickPlayers = new ArrayList();
                    nickPlayers.add(player);
                }
                // the player remains present (although the nick might have 
                // changed)
                occupantMap.remove(jid);
            }
            else {
                // player has vanished
                if (gonePlayers == null)
                    gonePlayers = new ArrayList();
                gonePlayers.add(player);
            }
        }
        for (Iterator it = occupantMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry ent = (Map.Entry)it.next();
            String jid = (String)ent.getKey();
            Occupant occ = (Occupant)ent.getValue();
            // this is a new player
            Player player = new Player(jid, occ.getNick(), isReferee(occ));
            if (newPlayers == null)
                newPlayers = new ArrayList();
            newPlayers.add(player);
        }
        occupantMap.clear();

        /* Now we have lists of newly-arrived and newly-vanished players.
         * Update mPlayers accordingly, and notify listeners. */

        if (newPlayers != null) {
            for (Iterator it = newPlayers.iterator(); it.hasNext(); ) {
                Player player = (Player)it.next();
                mPlayers.add(player);
                fireStatusListeners_playerJoined(player);
            }
        }
        if (gonePlayers != null) {
            for (Iterator it = gonePlayers.iterator(); it.hasNext(); ) {
                Player player = (Player)it.next();
                mPlayers.remove(player);
                fireStatusListeners_playerLeft(player);
            }
        }
        if (nickPlayers != null) {
            for (Iterator it = nickPlayers.iterator(); it.hasNext(); ) {
                Player player = (Player)it.next();
                fireStatusListeners_playerNickChanged(player);
            }
        }
    }

    public Iterator getPlayers() {
        return mPlayers.iterator();
    }

    public Player getPlayerByJID(String jid) {
        for (Iterator it = mPlayers.iterator(); it.hasNext(); ) {
            Player player = (Player)it.next();
            if (player.getJID() == jid)
                return player;
        }

        return null;
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

        fireStatusListeners_seatListKnown();
    }


    /** Set the list of required seat IDs. */
    public void setRequiredSeats(List ids) {
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
            fireStatusListeners_requiredSeatsChanged();
        }
    }

    /**
     * React to a player changing seats.
     * @param jid the player's JID.
     * @param seatid the ID of the seat (or null if the player stood).
     */
    public void setPlayerSeat(String jid, String seatid) {
        System.out.println("### Player " + jid + " is now in seat " + seatid);
        //### notify
    }

    /**
     * React to a player becoming ready or unready.
     * @param jid the player's JID.
     * @param flag is the player now ready?
     */
    public void setPlayerReadiness(String jid, boolean flag) {
        System.out.println("### Player " + jid + " is ready: " + flag);
        //### notify
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

    /***** One-liners to notify StatusListeners of table change. *****/

    private void fireStatusListeners_requiredSeatsChanged()
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).requiredSeatsChanged();
        }
    }
 
    private void fireStatusListeners_seatListKnown()
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).seatListKnown();
        }
    }
 
    private void fireStatusListeners_playerJoined(Player player)
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).playerJoined(player);
        }
    }
 
    private void fireStatusListeners_playerLeft(Player player)
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).playerLeft(player);
        }
    }
 
    private void fireStatusListeners_playerNickChanged(Player player)
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).playerNickChanged(player);
        }
    }
}
