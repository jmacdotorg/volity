package org.volity.client;

import java.util.*;

/**
 * A player at a table. One of these represents every member of the table MUC.
 * (Including you and the referee.)
 *
 * In an ideal world, this would be a subclass of Occupant. But there's no
 * factory to generate Occupants. Also, Occupant objects aren't stable; Smack
 * constantly tears them down and builds new ones, even for a player with a
 * persistent connection.
 */
public class Player {

    protected Seat mSeat;
    protected String mJID;
    protected String mNick;
    protected boolean mIsSelf;
    protected boolean mIsReferee;
    protected boolean mIsReady;

    /**
     * @param jid the (real) JID of the MUC member
     * @param nick the nickname of the player in the MUC
     * @param isself is this you?
     */
    public Player(String jid, String nick, boolean isself) {
        mIsSelf = isself;
        mJID = jid;
        mIsReferee = false;
        mNick = nick;
        mSeat = null;
        mIsReady = false;
    }

    /**
     * @return the (real) JID of the player.
     */
    public String getJID() {
        return mJID;
    }

    /**
     * @return the MUC nickname of the player.
     */
    public String getNick() {
        return mNick;
    }

    /**
     * Record a change in the MUC nickname of the player.
     */
    protected void setNick(String nick) {
        mNick = nick;
    }

    /** Does this Player represent the client? */
    public boolean isSelf() {
        return mIsSelf;
    }

    /** Does this Player represent the referee? */
    public boolean isReferee() {
        return mIsReferee;
    }

    /** Is this a bot? */
    public boolean isBot() {
        // Not yet implemented.
        return false;
    }

    /** Record whether the player is the referee. */
    protected void setReferee(boolean val) {
        mIsReferee = val;
    }

    /** Is this player marked "ready"? */
    public boolean isReady() {
        return mIsReady;
    }

    /** Set the "ready" flag. */
    protected void setReady(boolean flag) {
        mIsReady = flag;
    }

    /** Return the Player's seat (or null, if unseated). */
    public Seat getSeat() {
        return mSeat;
    }

    /** Set the Player's seat (null means unseated). */
    protected void setSeat(Seat seat) {
        mSeat = seat;
    }
}
