package org.volity.client;

import java.util.*;
import org.jivesoftware.smackx.muc.Occupant; //###

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
    protected boolean isReferee;
    protected boolean isReady;

    /**
     * @param jid the (real) JID of the MUC member
     * @param nick the nickname of the player in the MUC
     * @param isref is the MUC member the referee?
     */
    public Player(String jid, String nick, boolean isref) {
        isReferee = isref;
        mJID = jid;
        mNick = nick;
        mSeat = null;
        isReady = false;
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
    public void setNick(String nick) {
        mNick = nick;
    }
}
