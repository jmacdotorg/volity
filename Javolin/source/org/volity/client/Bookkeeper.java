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
import org.volity.client.data.GameUIInfo;
import org.volity.jabber.JIDUtils;

/** A connection to a Volity bookkeeper. */
public class Bookkeeper {

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
        this.connection = connection;
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

    protected XMPPConnection connection;
    protected String jid;

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

        DiscoBackground query = new DiscoBackground(connection,
            subback,
            DiscoBackground.QUERY_ITEMS, jid, "rulesets", rock);
    }

    /**
     * Ask the bookkeeper what game servers are available for a
     * particular ruleset.
     * Result is a list of JIDs (strings).
     * @param ruleset the URI of a ruleset known by the bookkeeper
     */
    public void getGameServers(final Callback callback, URI ruleset,
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

        DiscoBackground query = new DiscoBackground(connection,
            subback,
            DiscoBackground.QUERY_ITEMS, jid, (ruleset + "|servers"), rock);
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
                        connection, subback2, DiscoBatchBackground.QUERY_INFO,
                        queries, ls);
                }
            };

        DiscoBackground query = new DiscoBackground(connection,
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
}
