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

        String parlorJID = getResponderJID();
        ServiceDiscoveryManager discoMan =
            ServiceDiscoveryManager.getInstanceFor(getConnection());

        try {
            /* ### this really ought to be async, although since it's cached,
             * ### it's not a serious problem. */
            DiscoverInfo info = discoMan.discoverInfo(parlorJID);
            mGameInfo = new GameInfo(parlorJID, info);
        }
        catch (XMPPException ex) {
            // can't disco? I guess all the info fields are null.
            mGameInfo = new GameInfo(parlorJID);
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
     * Tell the parlor to create a new game table MUC, asyncly.
     *
     * The callback argument is invoked (in the Swing UI thread) when the RPC
     * either succeeds or fails. If it succeeds, the callback's first argument
     * will be a GameTable. The caller should join it. (The second argument
     * will be null.)
     *
     * If the RPC fails, the first argument will be null, and the second
     * argument will be an Exception:
     *
     *   XMPPException if there was an XMPP error
     *   RPCException if the remote method resulted in a fault or timed out
     *   TokenFailure if the remote method returned a non-success token
     *
     * The third callback argument will be the rock you passed to this method.
     */
    public void newTable(final RPCBackground.Callback callback, Object rock) {

        /* We allow an extra-long timeout for the new_table call, because
         * the server may have to do a lot of work. (I've seen it take over
         * a minute, if the server is on a different Jabber server from
         * the conference host.) */
        RPCBackground background = new RPCBackground(this, 
            new RPCBackground.Callback() {
                public void run(Object result, Exception err, Object rock) {
                    GameTable table = null;
                    if (result != null && !(result instanceof String)) {
                        result = null;
                        err = new RPCException(605, "new_table reply was not a string");
                    }
                    if (result != null) {
                        table = new GameTable(getConnection(), (String) result);
                    }
                    callback.run(table, err, rock);
                }
            },
            "volity.new_table", 120, rock);
    }

}
