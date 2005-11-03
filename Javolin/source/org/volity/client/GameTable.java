package org.volity.client;

import java.util.*;
import javax.swing.SwingUtilities;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.muc.DefaultParticipantStatusListener;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;
import org.jivesoftware.smackx.packet.DiscoverInfo;

/**
 * A game table (a Multi-User Chat room for playing a Volity game).
 *
 * The GameTable acts as the client's model for the arrangement of the table --
 * the seats, the players, who is sitting where.
 *
 * Note on threading: 
 *
 * Everything in this class is triggered by Smack packet listeners. The
 * PacketListener interface notes that all listening is done in the same
 * thread. That makes this class simpler (no worries about synchonization), but
 * it also means that if this code takes a long time, packet reception will
 * lag.
 *
 * Since all the listeners to this code are UI elements, which invoke the Swing
 * thread, I think we're okay.
 */
public class GameTable extends MultiUserChat 
{
    /**
     * Constants for referee states. UNKNOWN means we haven't contacted the
     * referee yet. SETUP, ACTIVE, SUSPENDED track the referee. (Clients do not
     * know about referee states "disrupted" or "abandoned"; those fall under
     * ACTIVE.)
     */
    public final static int STATE_UNKNOWN   = 0;
    public final static int STATE_SETUP     = 1;
    public final static int STATE_ACTIVE    = 2;
    public final static int STATE_SUSPENDED = 3;

    protected List mPlayers = new ArrayList();
    protected Player mSelfPlayer = null;
    protected Map mSeatsById = new HashMap();
    protected List mSeats = new ArrayList();
    protected boolean mInitialJoined = false;
    protected List statusListeners = new ArrayList();
    protected List readyListeners = new ArrayList();

    protected XMPPConnection mConnection;
    protected Referee mReferee;
    protected int mRefereeState = STATE_UNKNOWN;

    protected List mQueuedMessages = new ArrayList();
    protected PacketListener mParticipantListener;
    protected PacketListener mInternalListener;
    protected PacketListener mExternalListener;

    /**
     * @param connection an authenticated connection to an XMPP server.
     * @param room the JID of the game table.
     */
    public GameTable(XMPPConnection connection, String room) {
        super(connection, room);
        mConnection = connection;

        mParticipantListener = new PacketListener() {
                public void processPacket(Packet packet) {
                    // Called outside Swing thread!
                    rescanOccupantList();
                }
            };
        addParticipantListener(mParticipantListener);

        /*
         * Rather than letting the application set a MessageListener on this
         * MUC, we gather all messages ourself -- that's mInternalListener --
         * and queue them up. When the app is ready, it will call
         * setQueuedMessageListener, which returns the backlog as well as
         * subsequent messages.
         *
         * This scheme is simple, but it has the drawback that only one caller
         * can be attached (via setQueuedMessageListener) at a time. If more
         * are needed, we have modify this system.
         *
         * (I tried a scheme where the mInternalListener shuts down and hands
         * off to a new MessageListener. Failed miserably due to weird Smack
         * queuing -- messages were getting lost. Stick to this scheme.)
         */
        mExternalListener = null;
        mInternalListener = new PacketListener() {
                public void processPacket(Packet packet) {
                    // Called outside Swing thread!
                    if (packet instanceof Message) {
                        if (mExternalListener == null) {
                            mQueuedMessages.add(packet);
                        }
                        else {
                            mExternalListener.processPacket(packet);
                        }
                    }
                }
            };
        addMessageListener(mInternalListener);

    }

    /** Customization: Leave the MUC */
    public void leave() {
        // Turn off all our listeners.
        if (mParticipantListener != null) {
            removeParticipantListener(mParticipantListener);
            mParticipantListener = null;
        }
        if (mInternalListener != null) {
            removeMessageListener(mInternalListener);
            mInternalListener = null;
        }
        mExternalListener = null;
        super.leave();
    }

    /** Get the XMPP connection associated with this table. */
    public XMPPConnection getConnection() { 
        return mConnection; 
    }

    /**
     * The referee for this table, or null if no referee is connected.
     */
    public Referee getReferee() {
        return mReferee;
    }

    /**
     * Return the JID of the table's referee. This will not be set until the
     * GameTable has signalled ready. Before that, this returns null.
     */
    public String getRefereeJID() {
        if (mReferee == null)
            return null;
        return mReferee.getResponderJID();
    }
    
    /**
     * It's possible that the creator of the GameTable will ask it to join()
     * before setting up listeners for MUC messages. If that happens, the first
     * burst of messages could be lost.
     *
     * To prevent that, the GameTable itself saves those messages. When the
     * creator is ready, it calls setQueuedMessageListener(l) instead of
     * setting a listener directly on the MUC. All the saved messages will be
     * passed to l immediately, and incoming messages will go to l thereafter.
     *
     * It is safe to call this before join(); in that case it is functionally
     * equivalent to setting a direct listener.
     */
    public void setQueuedMessageListener(PacketListener listener) {
        if (mExternalListener != null) {
            throw new AssertionError("Cannot set two QueuedMessageListener on a Gametable");
        }

        mExternalListener = listener;
        while (mQueuedMessages.size() > 0) {
            Message msg = (Message)mQueuedMessages.remove(0);
            mExternalListener.processPacket(msg);
        }
    }

    public void clearQueuedMessageListener() {
        mExternalListener = null;
    }

    /***** Table readiness change methods *****/

    /**
     * Listener interface for addReadyListener / removeReadyListener. This
     * allows a listener to be notified when the GameTable has successfully
     * joined the MUC and located the referee.
     *
     * Note: the listener is notified on a Smack listener thread! Do not
     * do UI work in your invitationReceived() method.
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
        synchronized (readyListeners) {
            readyListeners.add(listener);
        }
    }
    
    /** Remove a table-joined listener. */
    public void removeReadyListener(ReadyListener listener) {
        synchronized (readyListeners) {
            readyListeners.remove(listener);
        }
    }

    /**
     * Notify all listeners that have registered for notification of
     * MUC-joinedness.
     *
     * May be called outside Swing thread! May call listeners outside Swing
     * thread!
     *
     * (Actually, clever people will note that this is called *in* the Swing
     * thread, thanks to the DiscoBackground class. But let's not rely on
     * that implementation detail.)
     */
    private void fireReadyListeners()
    {
        Iterator iter;
        synchronized (readyListeners) {
            // Clone listener list for unsynched use
            iter = new ArrayList(readyListeners).iterator();
        }
        while (iter.hasNext())
        {
            ((ReadyListener)iter.next()).ready();
        }
    }

    /***** Player status-change methods & callbacks *****/

    /** Internal data class used to store a (Player, nickname) pair. */
    protected class PlayerNick {
        Player player;
        String oldNick;
        PlayerNick(Player player, String oldNick) {
            this.player = player;
            this.oldNick = oldNick;
        }
    }

    /**
     * Internal data class which stores an (Occupant, Presence) pair.
     */
    protected class OccupantPresence {
        Occupant occupant;
        Presence presence;
        OccupantPresence(Occupant occ, Presence pres) {
            occupant = occ;
            presence = pres;
        }
    }

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
     * (This code *does* detect nickname changes, but only because it can track
     * the players' real JIDs. In an anonymous MUC, this code falls over and
     * dies.)
     *
     * Called outside Swing thread!
     */
    protected void rescanOccupantList() {
        Map occupantMap = new HashMap(); // maps JID to (Occupant, Presence)
        for (Iterator it = getOccupants(); it.hasNext(); ) {
            String jid = (String)it.next();
            Occupant occ = getOccupant(jid);
            Presence pres = getOccupantPresence(jid);
            if (occ != null) {
                occupantMap.put(occ.getJid(), new OccupantPresence(occ, pres));
            }
        }

        List gonePlayers = null;
        List newPlayers = null;
        List nickPlayers = null;

        for (Iterator it = mPlayers.iterator(); it.hasNext(); ) {
            Player player = (Player)it.next();
            String jid = player.getJID();
            if (occupantMap.containsKey(jid)) {
                OccupantPresence opair = (OccupantPresence)occupantMap.get(jid);
                Occupant occ = opair.occupant;
                String oldnick = player.getNick();
                if (!occ.getNick().equals(oldnick)) {
                    player.setNick(occ.getNick());
                    if (nickPlayers == null)
                        nickPlayers = new ArrayList();
                    PlayerNick pair = new PlayerNick(player, oldnick);
                    nickPlayers.add(pair);
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
                if (player.isReferee()) {
                    // Oh dear. We've lost our referee.
                    mReferee = null;
                    // XXX notify somebody?
                }
                // XXX if player.isSelf(), then the whole MUC is gone. Should
                // react the same way to that as to a referee lossage.
            }
        }
        for (Iterator it = occupantMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry ent = (Map.Entry)it.next();
            String jid = (String)ent.getKey();
            OccupantPresence opair = (OccupantPresence)ent.getValue();
            Occupant occ = opair.occupant;
            // this is a new player
            Player player = new Player(jid, occ.getNick(), 
                jid.equals(mConnection.getUser()));
            if (newPlayers == null)
                newPlayers = new ArrayList();
            newPlayers.add(player);
            if (player.isSelf())
                mSelfPlayer = player;

            boolean knownRef = false;

            PacketExtension ext = opair.presence.getExtension(
                CapPacketExtension.NAME,
                CapPacketExtension.NAMESPACE);
            if (ext != null && ext instanceof CapPacketExtension) {
                CapPacketExtension extp = (CapPacketExtension)ext;
                if (extp.getNode().equals("http://volity.org/protocol/caps")
                    && extp.hasExtString("referee")) {
                    knownRef = true;
                    player.setReferee(true);
                    // Invoke into the Swing thread.
                    final Player refPlayer = player;
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                foundReferee(refPlayer);
                            }
                        });
                }
            }
            
            if ((!knownRef) && occ.getAffiliation().equals("owner")) {
                /*
                 * We couldn't tell from the presence packet whether this was
                 * the ref. But it's an owner, and the owner is usually the
                 * ref. So, fire off a test. It probably is, but we want to be
                 * sure.
                 */
                DiscoBackground query = new DiscoBackground(mConnection,
                    new DiscoBackground.Callback() {
                        public void run(IQ result, XMPPException ex, Object rock) {
                            if (result != null) {
                                checkOwnerDisco((DiscoverInfo)result, (Player)rock);
                            }
                        }
                    },
                    DiscoBackground.QUERY_INFO, jid, player);
            }
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
                Seat seat = player.getSeat();
                if (seat != null) {
                    /* Remove the player from the seat, but leave player.seat
                     * set. This makes the redraw happen correctly, and it
                     * shouldn't hurt anything. */
                    seat.removePlayer(player);
                }
                fireStatusListeners_playerLeft(player);
            }
        }
        if (nickPlayers != null) {
            for (Iterator it = nickPlayers.iterator(); it.hasNext(); ) {
                PlayerNick pair = (PlayerNick)it.next();
                fireStatusListeners_playerNickChanged(pair.player, 
                    pair.oldNick);
            }
        }
    }

    /**
     * This is a DiscoBackground callback, so it's running in the Swing thread.
     */
    private void checkOwnerDisco(DiscoverInfo info, Player player) {
        if (player != getPlayerByJID(player.getJID())) {
            // This player object is obsolete; forget it.
            return;
        }

        Form form = Form.getFormFrom(info);
        if (form != null) {
            FormField field = form.getField("volity-role");
            if (field != null) {
                String role = (String) field.getValues().next();
                if (role.equals("referee") && mReferee == null) {
                    player.setReferee(true);
                    foundReferee(player);
                }
            }
        }
        
    }

    /**
     * By one means or another, we have decided that the given Player is the
     * referee.
     */
    private void foundReferee(Player player) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (mReferee != null)
            return;

        /* It appears we have found our referee! Or re-found it, perhaps (if
         * the first one disconnected) */
        mReferee = new Referee(this, player.getJID());
        if (!mInitialJoined) {
            mInitialJoined = true;
            fireReadyListeners();
        }

        fireStatusListeners_playerIsReferee(player);
    }

    /** Return an iterator of all the Player objects. */
    public Iterator getPlayers() {
        return mPlayers.iterator();
    }

    /** Get the Player with a given (real) JID. */
    public Player getPlayerByJID(String jid) {
        for (Iterator it = mPlayers.iterator(); it.hasNext(); ) {
            Player player = (Player)it.next();
            if (player.getJID().equals(jid))
                return player;
        }

        return null;
    }

    /** Return an iterator of all the players who are not seated. */
    public Iterator getUnseatedPlayers() {
        return new IteratorFilter(mPlayers) {
                public boolean matches(Object obj) {
                    Player player = (Player)obj;
                    return (player.getSeat() == null);
                }
            };
    }

    /** Add a player readiness change listener. */
    public void addStatusListener(StatusListener listener) {
        statusListeners.add(listener);
    }
    
    /** Remove a player readiness change listener. */
    public void removeStatusListener(StatusListener listener) {
        statusListeners.remove(listener);
    }

    /***** Dealing with the referee state *****/

    /**
     * React to a change in referee state. 
     */
    public void setRefereeState(int newstate) {
        if (newstate != mRefereeState) {
            mRefereeState = newstate;
            fireStatusListeners_stateChanged(mRefereeState);
        }
    }

    /**
     * React to a change in referee state (expressed as a string).
     */
    public void setRefereeState(String val) {
        int newstate = STATE_UNKNOWN;

        if (val.equals("setup"))
            newstate = STATE_SETUP;
        else if (val.equals("suspended"))
            newstate = STATE_SUSPENDED;
        else if (val.equals("active"))
            newstate = STATE_ACTIVE;
        else if (val.equals("disrupted"))
            newstate = STATE_ACTIVE;
        else if (val.equals("abandoned"))
            newstate = STATE_ACTIVE;

        setRefereeState(newstate);
    }

    /**
     * Get the current known referee state.
     *
     *   STATE_UNKNOWN:   The referee has not yet been contacted.
     *   STATE_SETUP:     New game configuration.
     *   STATE_ACTIVE:    Game in progress.
     *   STATE_SUSPENDED: Game suspended for reconfiguration.
     */
    public int getRefereeState() {
        return mRefereeState;
    }

    /***** Dealing with seats *****/

    /**
     * React to discovering the referee's list of seats. Create Seat objects to
     * model them.
     *
     * This is the complete seat list -- it should not change over the lifetime
     * of the game table.
     */
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
            boolean required = requiredSet.contains(seat.getID());
            if (required != seat.isRequired()) {
                seat.setRequired(required);
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
        Player player = getPlayerByJID(jid);
        if (player == null)
            return;

        Seat seat;
        if (seatid == null) {
            seat = null;
        }
        else {
            seat = getSeat(seatid);
            if (seat == null)
                return;
        }

        Seat oldseat = player.getSeat(); // may be null
        if (oldseat == seat)
            return;

        if (oldseat != null)
            oldseat.removePlayer(player);
        if (seat != null)
            seat.addPlayer(player);
        player.setSeat(seat);

        fireStatusListeners_playerSeatChanged(player, oldseat, seat);
    }

    /**
     * React to a player becoming ready or unready.
     * @param jid the player's JID.
     * @param flag is the player now ready?
     */
    public void setPlayerReadiness(String jid, boolean flag) {
        Player player = getPlayerByJID(jid);
        if (player == null)
            return;
        if (player.isReady() == flag)
            return;

        player.setReady(flag);
        fireStatusListeners_playerReady(player, flag);
    }

    /**
     * React to all players becoming unready. (This is a side effect of many
     * volity.* RPCs.)
     */
    public void setAllPlayersUnready() {
        for (Iterator it = mPlayers.iterator(); it.hasNext(); ) {
            Player player = (Player)it.next();
            if (player.isReady()) {
                player.setReady(false);
                fireStatusListeners_playerReady(player, false);
            }
        }
    }

    /** Return an iterator of all the seat objects. */
    public Iterator getSeats() {
        return mSeats.iterator();
    }

    /** Get a seat by ID. (Or null, if there is no such seat.) */
    public Seat getSeat(String id) {
        return (Seat)mSeatsById.get(id);
    }

    /** Return an iterator of seats which have players in them. */
    public Iterator getOccupiedSeats() {
        return new IteratorFilter(mSeats) {
                public boolean matches(Object obj) {
                    Seat seat = (Seat)obj;
                    return (seat.isOccupied());
                }
            };
    }

    /**
     * Return an iterator of seats which are visible (i.e., either required or
     * occupied).
     */
    public Iterator getVisibleSeats() {
        return new IteratorFilter(mSeats) {
                public boolean matches(Object obj) {
                    Seat seat = (Seat)obj;
                    return (seat.isOccupied() || seat.isRequired());
                }
            };
    }

    /** Return the Player object which represents this client. */
    public Player getSelfPlayer() {
        return mSelfPlayer;
    }

    /** Is this client marked ready? */
    public boolean isSelfReady() {
        if (mSelfPlayer == null)
            return false;
        return mSelfPlayer.isReady();
    }

    /** Is this client seated? */
    public boolean isSelfSeated() {
        if (mSelfPlayer == null)
            return false;
        return (mSelfPlayer.getSeat() != null);
    }

    /***** One-liners to notify StatusListeners of table change. *****/

    /*
     * All these methods are called from Smack, and therefore call their
     * listeners outside the Swing thread.
     */

    private void fireStatusListeners_stateChanged(int newstate)
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).stateChanged(newstate);
        }
    }
 
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
 
    private void fireStatusListeners_playerNickChanged(Player player,
        String oldNick)
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).playerNickChanged(player, oldNick);
        }
    }
 
    private void fireStatusListeners_playerIsReferee(Player player)
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).playerIsReferee(player);
        }
    }
 
    private void fireStatusListeners_playerReady(Player player,
        boolean flag)
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).playerReady(player, flag);
        }
    }
 
    private void fireStatusListeners_playerSeatChanged(Player player,
        Seat oldseat, Seat newseat)
    {
        for (Iterator iter = statusListeners.iterator(); iter.hasNext(); ) {
            ((StatusListener)iter.next()).playerSeatChanged(player, 
                oldseat, newseat);
        }
    }

    /***** IteratorFilter and friends *****/

    /**
     * IteratorFilter is a handy way to iterate over *some* items in a list or
     * other iterable.
     *
     * You create a subclass and define the matches() predicate. The result is
     * an Iterator that wraps an existing Iterator, and only lets the matching
     * objects show through.
     */
    public abstract class IteratorFilter implements Iterator {
        Iterator mBaseIterator;
        Object nextObject;

        public IteratorFilter(Iterator iter) {
            mBaseIterator = iter;
            nextObject = null;
            nextMatching();
        }

        public IteratorFilter(List ls) {
            mBaseIterator = ls.iterator();
            nextObject = null;
            nextMatching();
        }

        private void nextMatching() {
            while (mBaseIterator.hasNext()) {
                Object obj = mBaseIterator.next();
                if (matches(obj)) {
                    nextObject = obj;
                    return;
                }
            }
            nextObject = null;
        }

        protected abstract boolean matches(Object obj);

        public boolean hasNext() {
            return (nextObject != null);
        }

        public Object next() {
            Object val = nextObject;
            nextMatching();
            return val;
        }

        public void remove() {
            throw new UnsupportedOperationException("IteratorFilter does not support remove");
        }
    }

}
