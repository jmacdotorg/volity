package org.volity.client;

import java.net.URI;
import java.util.Iterator;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.volity.client.TokenFailure;
import org.volity.client.TokenRequester;
import org.volity.jabber.RPCException;

/** A Jabber-RPC connection to a Volity game server. */
public class GameServer extends TokenRequester 
{
    protected GameInfo mGameInfo = null;

    /**
     * @param connection an authenticated connection to an XMPP server
     * @param JID the JID of the game server
     * @throws IllegalStateException if the connection has not been 
     *         authenticated
     */
    public GameServer(XMPPConnection connection, String JID) {
        super(connection, JID);
    }

    /**
     * Returns the block of information which is found in the parlor's disco
     * info. The information is cached, so repeated queries are fast.
     */
    public GameInfo getGameInfo() {
        if (mGameInfo != null)
            return mGameInfo;

        ServiceDiscoveryManager discoMan =
            ServiceDiscoveryManager.getInstanceFor(getConnection());

        try {
            DiscoverInfo info = discoMan.discoverInfo(getResponderJID());
            mGameInfo = new GameInfo(info);
        }
        catch (XMPPException ex) {
            // can't disco? I guess all the info fields are null.
            mGameInfo = new GameInfo();
        }

        return mGameInfo;
    }

    /**
     * Ask what ruleset the game server implements.
     * @return the ruleset URI, or null if it could not be found.
     */
    public URI getRuleset() {
        return getGameInfo().getRulesetURI();
    }

    /**
     * Create a new instance (table) of the game (a Multi-User Chat room).
     * @return the new MUC, which should immediately be joined
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if an RPC fault occurs
     */
    public GameTable newTable() 
        throws XMPPException, RPCException, TokenFailure {
        Object res = invokeTimeout("volity.new_table", 120);
        /* We allow an extra-long timeout for the new_table call, because
         * the server may have to do a lot of work. (I've seen it take over
         * a minute, if the server is on a different Jabber server from
         * the conference host.) */
        return new GameTable(getConnection(), (String) res);
    }

}
