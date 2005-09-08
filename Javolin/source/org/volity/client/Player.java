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
     * @param isref is the MUC member the referee?
     */
    public Player(String jid, String nick, boolean isself, boolean isref) {
        mIsSelf = isself;
        mIsReferee = isref;
        mJID = jid;
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

    public boolean isSelf() {
        return mIsSelf;
    }

    public boolean isReferee() {
        return mIsReferee;
    }

    public boolean isReady() {
        return mIsReady;
    }

    protected void setReady(boolean flag) {
        mIsReady = flag;
    }

    public Seat getSeat() {
        return mSeat;
    }

    protected void setSeat(Seat seat) {
        mSeat = seat;
    }
}
