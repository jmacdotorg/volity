package org.volity.client;

import java.net.*;
import java.util.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;
import org.volity.client.comm.DiscoBackground;
import org.volity.client.comm.DiscoBatchBackground;
import org.volity.client.comm.RPCBackground;
import org.volity.client.data.GameUIInfo;
import org.volity.client.data.ResourceInfo;
import org.volity.client.translate.TokenRequester;
import org.volity.jabber.JIDUtils;

/** A connection to a Volity bookkeeper. */
public class Bookkeeper extends TokenRequester {

    protected static String defaultJid = "bookkeeper@volity.net/volity";

    /**
     * Set the class's default bookkeeper JID address. (You should call
     * this before the Jabber connection is opened.)
     *
     * If you don't specify a JID resource, "volity" is assumed.
     *
     * @param jid the JID of the bookkeeper. 
     */
    public static void setDefaultJid(String jid) {
        if (!JIDUtils.hasResource(jid))
            jid = JIDUtils.setResource(jid, "volity");

        defaultJid = jid;
    }

    /**
     * Return the address used for the bookkeeper.
     */
    public static String getDefaultJid() {
        return defaultJid;
    }

    /**
     * Callback interface. When the disco query finishes, the run() method will
     * be called. Exactly one of result and err will be null. The result
     * argument is generic; its contents will depend on the request you made.
     */
    public interface Callback {
        public void run(Object result, XMPPException err, Object rock);
    }

    /**
     * Connect to a Volity bookkeeper. (This does not do any Jabber work; it
     * just returns an object you can use to make bookkeeper requests.)
     *
     * @param connection an authenticated connection to an XMPP server
     * @param jid the JID of the bookkeeper
     */
    public Bookkeeper(XMPPConnection connection, String jid) {
        super(connection, jid);
        this.jid = jid;
    }

    /**
     * Connect to the standard Volity bookkeeper. (This does not do any Jabber
     * work; it just returns an object you can use to make bookkeeper
     * requests.)
     *
     * <code>bookkeeper@volity.net/volity</code>.
     * @param connection an authenticated connection to an XMPP server
     */
    public Bookkeeper(XMPPConnection connection) {
        this(connection, defaultJid);
    }

    protected String jid;

    /** Clean up resources. */
    public void close() {
        // nothing to do
    }

    /** Get the bookkeeper JID. */
    public String getJID() {
        return jid;
    }

    /**
     * Ask the bookkeeper whether a player is authorized to play a particular
     * game.
     */
    public void gamePlayerAuthorized(String parlor, String player,
        RPCBackground.Callback callback, Object rock) 
    {
        List args = Arrays.asList(new String[] { parlor, player });

        new RPCBackground(this, callback, 
            "volity.game_player_authorized", args, rock);
    }

    /**
     * Ask the bookkeeper what rulesets are available.
     * The result will be a list of URIs.
     */
    public void getRulesets(final Callback callback, Object rock) {
        DiscoBackground.Callback subback = new DiscoBackground.Callback() {
                public void run(IQ result, XMPPException err, Object rock) {
                    if (err != null) {
                        callback.run(null, err, rock);
                        return;
                    }
                    assert (result != null && result instanceof DiscoverItems);
                    DiscoverItems items = (DiscoverItems)result;

                    List rulesets = new ArrayList();
                    for (Iterator it = items.getItems(); it.hasNext(); ) {
                        DiscoverItems.Item item = (DiscoverItems.Item)it.next();
                        rulesets.add(URI.create((String) item.getNode()));
                    }
                    callback.run(rulesets, null, rock);
                }
            };

        DiscoBackground query = new DiscoBackground(getConnection(),
            subback,
            DiscoBackground.QUERY_ITEMS, jid, "rulesets", rock);
    }

    /**
     * Ask the bookkeeper what game parlors are available for a
     * particular ruleset.
     * Result is a list of JIDs (strings).
     * @param ruleset the URI of a ruleset known by the bookkeeper
     */
    public void getGameParlors(final Callback callback, URI ruleset,
        Object rock) {
        DiscoBackground.Callback subback = new DiscoBackground.Callback() {
                public void run(IQ result, XMPPException err, Object rock) {
                    if (err != null) {
                        callback.run(null, err, rock);
                        return;
                    }
                    assert (result != null && result instanceof DiscoverItems);
                    DiscoverItems items = (DiscoverItems)result;

                    List servers = new ArrayList();
                    for (Iterator it = items.getItems(); it.hasNext(); ) {
                        DiscoverItems.Item item = (DiscoverItems.Item)it.next();
                        servers.add(item.getEntityID());
                    }

                    callback.run(servers, null, rock);
                }
            };

        DiscoBackground query = new DiscoBackground(getConnection(),
            subback,
            DiscoBackground.QUERY_ITEMS, jid, (ruleset + "|parlors"), rock);
    }

    /**
     * Ask the bookkeeper what game UIs are available for a particular
     * ruleset.
     * The result is a list of {@link GameUIInfo} objects.
     * @param ruleset the URI of a ruleset known by the bookkeeper
     */
    public void getGameUIs(final Callback callback, URI ruleset,
        final Object rock) {
        final DiscoBatchBackground.Callback subback2 = new DiscoBatchBackground.Callback() {
                public void run(DiscoBatchBackground.Query queries[],
                    Object subrock) {
                    List ls = (List)subrock;
                    List infolist = new ArrayList();

                    assert (ls.size() == queries.length);

                    for (int ix=0; ix<queries.length; ix++) {
                        UIPair pair = (UIPair)ls.get(ix);
                        DiscoBatchBackground.Query query = queries[ix];
                        if (query.isSuccess()) {
                            DiscoverInfo info = query.getInfo();
                            Form form = Form.getFormFrom(info);
                            if (form.getField("ruleset") == null) {
                                // This URL wasn't really queryable -- skip it.
                                continue;
                            }
                            infolist.add(new GameUIInfo(pair.name,
                                             pair.location, form));
                        }
                    }

                    callback.run(infolist, null, rock);
                }
            };

        DiscoBackground.Callback subback = new DiscoBackground.Callback() {
                public void run(IQ result, XMPPException err, Object rock) {
                    if (err != null) {
                        callback.run(null, err, rock);
                        return;
                    }
                    assert (result != null && result instanceof DiscoverItems);
                    DiscoverItems items = (DiscoverItems)result;

                    List ls = new ArrayList();
                    for (Iterator it = items.getItems(); it.hasNext();) {
                        DiscoverItems.Item item = (DiscoverItems.Item) it.next();
                        try {
                            URL location = new URL(item.getNode());
                            String name = item.getName();
                            ls.add(new UIPair(location, name));
                        } catch (MalformedURLException e) { }
                    }

                    int count = ls.size();
                    DiscoBatchBackground.Query[] queries 
                        = new DiscoBatchBackground.Query[count];
                    for (int ix=0; ix<count; ix++) {
                        UIPair pair = (UIPair)ls.get(ix);
                        queries[ix] = pair.query;
                    }

                    DiscoBatchBackground batch = new DiscoBatchBackground(
                        getConnection(), subback2, 
                        DiscoBatchBackground.QUERY_INFO,
                        queries, ls);
                }
            };

        DiscoBackground query = new DiscoBackground(getConnection(),
            subback,
            DiscoBackground.QUERY_ITEMS, jid, (ruleset + "|uis"), rock);
    }

    /** Data class used by getGameUIs(). */
    protected class UIPair 
    {
        URL location;
        String name;
        DiscoBatchBackground.Query query;

        public UIPair(URL location, String name) {
            this.location = location;
            this.name = name;
            this.query = new DiscoBatchBackground.Query(jid, location.toString());
        }
    }

    /**
     * Ask the bookkeeper what resources are available for a particular
     * resource URI.
     *
     * The result is a list of ResourceInfo objects.
     */
    public void getGameResources(final Callback callback, URI resuri,
        final Object rock) {

        final RPCBackground.Callback subback2 = new RPCBackground.Callback() {
                public void run(Object result, Exception err, Object lsrock) {
                    if (err != null) {
                        // may have to wrap generic exception as XMPPException
                        XMPPException ex; 
                        if (err instanceof XMPPException)
                            ex = (XMPPException)err;
                        else
                            ex = new XMPPException(err);
                        callback.run(null, ex, rock);
                        return;
                    }

                    assert (result != null);
                    assert (lsrock != null && lsrock instanceof List);
                    List urlls = (List)lsrock;

                    if (!(result instanceof List)) {
                        XMPPException ex = new XMPPException("volity.get_resource_info returned non-list");
                        callback.run(null, ex, rock);
                        return;
                    }

                    List ls = (List)result;
                    if (ls.size() != urlls.size()) {
                        XMPPException ex = new XMPPException("volity.get_resource_info returned wrong length list");
                        callback.run(null, ex, rock);
                        return;
                    }
                    for (int ix=0; ix<ls.size(); ix++) {
                        Object obj = ls.get(ix);
                        if (!(obj instanceof Map)) {
                            XMPPException ex = new XMPPException("volity.get_resource_info returned non-struct in list");
                            callback.run(null, ex, rock);
                            return;
                        }
                    }

                    List finalls = new ArrayList();

                    for (int ix=0; ix<ls.size(); ix++) {
                        URL url = (URL)urlls.get(ix);
                        Map map = (Map)ls.get(ix);
                        ResourceInfo info = new ResourceInfo(url, map);
                        finalls.add(info);
                    }

                    callback.run(finalls, null, rock);
                    return;
                }
            };

        final RPCBackground.Callback subback = new RPCBackground.Callback() {
                public void run(Object result, Exception err, Object rock2) {
                    if (err != null) {
                        // may have to wrap generic exception as XMPPException
                        XMPPException ex; 
                        if (err instanceof XMPPException)
                            ex = (XMPPException)err;
                        else
                            ex = new XMPPException(err);
                        callback.run(null, ex, rock);
                        return;
                    }
                    
                    if (result == null) {
                        /* The bookkeeper shouldn't return an empty result, but
                         * we check for that case anyway. Treat it as an empty
                         * list. */
                        result = new ArrayList();
                    }

                    assert (result != null);
                    assert (rock == rock2);

                    if (!(result instanceof List)) {
                        XMPPException ex = new XMPPException("volity.get_resources returned non-list");
                        callback.run(null, ex, rock);
                        return;
                    }

                    List ls = (List)result;
                    if (ls.size() == 0) {
                        /* No point making a second call. */
                        callback.run(new ArrayList(), null, rock);
                        return;
                    }

                    List urlls = new ArrayList();

                    for (int ix=0; ix<ls.size(); ix++) {
                        Object obj = ls.get(ix);
                        if (!(obj instanceof String)) {
                            XMPPException ex = new XMPPException("volity.get_resources returned non-string in list");
                            callback.run(null, ex, rock);
                            return;
                        }
                        URL url;
                        try {
                            url = new URL((String)obj);
                        }
                        catch (MalformedURLException ex) {
                            XMPPException ex2 = new XMPPException("volity.get_resources returned malformed url");
                            callback.run(null, ex2, rock);
                            return;
                        }
                        urlls.add(url);
                    }

                    List args = Arrays.asList(new Object[] { ls } );
                    new RPCBackground(Bookkeeper.this, subback2,
                        "volity.get_resource_info", args, urlls);
                }
            };

        List args = Arrays.asList(new String[] { resuri.toString() });

        new RPCBackground(this, subback, 
            "volity.get_resources", args, rock);
    }
}
