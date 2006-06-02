package org.volity.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;
import org.volity.client.comm.DiscoBackground;
import org.volity.client.comm.RPCBackground;
import org.volity.client.data.GameInfo;
import org.volity.client.translate.TokenFailure;
import org.volity.client.translate.TokenRequester;
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

    /**
     * Asynchronously fetch the list of bot (algorithm) URIs that this parlor
     * supports. The callback will be run with a list of AvailableBot objects
     * (which may be empty). If the parlor query fails, the callback will be
     * run with a null argument.
     *
     * The callback will be executed in the Swing thread.
     */
    public void getAvailableBots(final AvailableBotCallback callback) {
        final String parlorJID = getResponderJID();

        new DiscoBackground(getConnection(),
            new DiscoBackground.Callback() {
                public void run(IQ result, XMPPException err, Object rock) {
                    if (err != null) {
                        // Not implemented or not available.
                        callback.run(null);
                    }
                    else {
                        assert (result != null && result instanceof DiscoverItems);

                        List bots = new ArrayList();
                        
                        DiscoverItems items = (DiscoverItems)result;
                        for (Iterator it = items.getItems(); it.hasNext(); ) {
                            DiscoverItems.Item el = (DiscoverItems.Item)it.next();
                            if (el.getNode() != null) {
                                AvailableBot bot = new AvailableBot(el.getEntityID(), el.getNode(), el.getName());
                                bots.add(bot);
                            }
                            else {
                                /* Don't include the parlor's JID as a factory
                                 * JID! That would cause a disco storm. */
                                if (!parlorJID.equals(el.getEntityID())) {
                                    AvailableFactory factory = new AvailableFactory(el.getEntityID(), el.getName());
                                    bots.add(factory);
                                }
                            }
                        }

                        callback.run(bots);
                    }
                }
            },
            DiscoBackground.QUERY_ITEMS, parlorJID, "bots", null);
    }

    /**
     * Asynchronously fetch the list of bot (algorithm) URIs from a factory.
     * (This doesn't really have anything to do with the GameServer object, but
     * this was a reasonable place to put this function.)
     *
     * The resulting list will be placed in factory.list, and then the callback
     * will be called. If the query fails, neither occurs.
     *
     * The callback will be executed in the Swing thread.
     */
    public void getFactoryAvailableBots(final AvailableFactory factory, 
        final Runnable callback) {
        new DiscoBackground(getConnection(),
            new DiscoBackground.Callback() {
                public void run(IQ result, XMPPException err, Object rock) {
                    if (err != null) {
                        // Not implemented or not available.
                    }
                    else {
                        assert (result != null && result instanceof DiscoverItems);

                        List bots = new ArrayList();
                        
                        DiscoverItems items = (DiscoverItems)result;
                        for (Iterator it = items.getItems(); it.hasNext(); ) {
                            DiscoverItems.Item el = (DiscoverItems.Item)it.next();
                            if (el.getNode() != null) {
                                AvailableBot bot = new AvailableBot(el.getEntityID(), el.getNode(), el.getName());
                                bots.add(bot);
                            }
                            // no factories from a factory.
                        }

                        factory.list = bots;
                        callback.run();
                    }
                }
            },
            DiscoBackground.QUERY_ITEMS, factory.jid, "bots", null);
    }


    /** Callback for getAvailableBots() */
    public interface AvailableBotCallback {
        public void run(List bots);
    }

    /** Data class for the list of available bots. */
    static public class AvailableBot {
        public String jid;
        public String uri;
        public String name;
        public AvailableBot(String jid, String uri, String name) {
            this.jid = jid;
            this.uri = uri;
            this.name = name;
        }
    }

    /** Data class for the list of available bots. */
    static public class AvailableFactory {
        public String jid;
        public String name;
        public List list;
        public AvailableFactory(String jid, String name) {
            this.jid = jid;
            this.name = name;
            this.list = null;
        }
        public List getList() { return list; }
    }
}
