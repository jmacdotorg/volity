package org.volity.client;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.ServiceDiscoveryManager;

/**
 * This class spawns a background thread, performs a disco query in that
 * thread, and then passes the result to a callback. The callback is guaranteed
 * to execute in the Swing event-handler thread.
 *
 * The callback must have the interface DiscoBackground.Callback. Its method
 * looks like:
 *
 *    public void run(IQ result, XMPPException err, Object rock) { ... }
 *
 * ...where result is the disco result (either DiscoverInfo or DiscoverItems),
 * err is null (on success), and rock is a reference you passed to the
 * DiscoBackground when you created it. If the disco query fails, then result
 * will be null and err will be set.
 */
public class DiscoBackground extends SwingWorker
{
    public static int QUERY_INFO  = 1;
    public static int QUERY_ITEMS = 2;

    /**
     * Callback interface. When the disco query finishes, the run() method will
     * be called. Exactly one of result and err will be null.
     */
    public interface Callback {
        public void run(IQ result, XMPPException err, Object rock);
    }

    protected ServiceDiscoveryManager mDiscoMan;
    protected int mQueryType;
    protected String mJID;
    protected String mNode;
    protected Callback mCallback;
    protected Object mRock;

    /**
     * Create a background query object.
     *
     * @param connection the Jabber connection on which to query.
     * @param callback the callback to invoke when the query finished.
     * @param qtype QUERY_INFO or QUERY_ITEMS.
     * @param jid the JID to query.
     * @param rock a reference which will be passed to the callback.
     */
    public DiscoBackground(XMPPConnection connection, Callback callback,
        int qtype, String jid, Object rock) {
        this(connection, callback, qtype, jid, null, rock);
    }

    /**
     * Create a background query object.
     *
     * @param connection the Jabber connection on which to query.
     * @param callback the callback to invoke when the query finished.
     * @param qtype QUERY_INFO or QUERY_ITEMS.
     * @param jid the JID to query.
     * @param node the node to query about.
     * @param rock a reference which will be passed to the callback.
     */
    public DiscoBackground(XMPPConnection connection, Callback callback,
        int qtype, String jid, String node, Object rock) {
        super();

        mDiscoMan = ServiceDiscoveryManager.getInstanceFor(connection);
        mJID = jid;
        mQueryType = qtype;
        mNode = node;
        mCallback = callback;
        mRock = rock;

        start();
    }

    /**
     * Internal thread handler. Do not call.
     */
    public Object construct() {
        try {
            Object res;

            if (mQueryType == QUERY_INFO) {
                if (mNode == null)
                    res = mDiscoMan.discoverInfo(mJID);
                else
                    res = mDiscoMan.discoverInfo(mJID, mNode);
            }
            else {
                if (mNode == null)
                    res = mDiscoMan.discoverItems(mJID);
                else
                    res = mDiscoMan.discoverItems(mJID, mNode);
            }
            return res;
        }
        catch (XMPPException ex) {
            return ex;
        }
    }

    /**
     * Internal thread handler. Do not call.
     */
    public void finished() {
        Object obj = get();
        if (obj instanceof XMPPException)
            mCallback.run(null, (XMPPException)obj, mRock);
        else
            mCallback.run((IQ)obj, null, mRock);
    }

}
