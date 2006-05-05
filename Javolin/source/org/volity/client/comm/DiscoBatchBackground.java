package org.volity.client.comm;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;

/**
 * This class spawns a bunch of background threads, performs a batch of disco
 * queries, and then passes all the results to a callback. The callback is
 * guaranteed to execute in the Swing event-handler thread.
 *
 * The callback must have the interface DiscoBatchBackground.Callback. Its
 * method looks like:
 *
 *    public void run(Query[] queries, Object rock) { ... }
 *
 * The queries array will be the same one you passed in. Each query object in
 * it will be filled out (either with a result or an error).
 */
public class DiscoBatchBackground extends SwingWorker
{
    public static int QUERY_INFO  = DiscoBackground.QUERY_INFO;
    public static int QUERY_ITEMS = DiscoBackground.QUERY_ITEMS;

    /**
     * Callback interface. When the disco query finishes, the run() method will
     * be called.
     */
    public interface Callback {
        public void run(Query queries[], Object rock);
    }

    /**
     * Query object. Create this with the jid (and, optionally, the node) that
     * you want to query. After the DiscoBatchBackground completes, you can
     * call the get methods to see what the result was.
     */
    public static class Query {
        protected boolean mSuccess;
        protected String mJID;
        protected String mNode;
        protected IQ mIQ;
        protected XMPPException mError;
        protected DiscoWorker mWorker;

        public Query(String jid) {
            this(jid, null);
        }

        public Query(String jid, String node) {
            mJID = jid;
            mNode = node;

            mSuccess = false;
            mIQ = null;
            mError = null;
        }

        /**
         * Ask whether the query succeeded. If it did, you can call getInfo()
         * or getItems(). If not, you can call getError().
         */
        public boolean isSuccess() {
            return mSuccess;
        }

        /** Extract the error that resulted from the query. */
        public XMPPException getError() {
            return mError;
        }

        /**
         * Extract the DiscoverInfo that resulted from the query. (QUERY_INFO
         * queries only.)
         */
        public DiscoverInfo getInfo() {
            return (DiscoverInfo)mIQ;
        }

        /**
         * Extract the DiscoverItems that resulted from the query. (QUERY_ITEMS
         * queries only.)
         */
        public DiscoverItems getItems() {
            return (DiscoverItems)mIQ;
        }

        /**
         * Check out the worker's result object, and set the query's member
         * variables appropriately.
         */
        protected void resolve() {
            Object obj = mWorker.get();
            if (obj == null) {
                obj = new XMPPException("Disco operation interrupted");
            }

            if (obj instanceof XMPPException) {
                mSuccess = false;
                mError = (XMPPException)obj;
            }
            else {
                mSuccess = true;
                mIQ = (IQ)obj;
            }
        }
    }

    protected ServiceDiscoveryManager mDiscoMan;
    protected int mQueryType;
    protected Query[] mQueries;
    protected int mCount;
    protected Callback mCallback;
    protected Object mRock;

    /**
     * Create a background query object.
     *
     * @param connection the Jabber connection on which to query.
     * @param callback the callback to invoke when the query finished.
     * @param qtype QUERY_INFO or QUERY_ITEMS.
     * @param queries the list of queries.
     * @param rock a reference which will be passed to the callback.
     */
    public DiscoBatchBackground(XMPPConnection connection, Callback callback,
        int qtype, Query[] queries, Object rock) {
        super();

        mDiscoMan = ServiceDiscoveryManager.getInstanceFor(connection);
        mQueryType = qtype;
        mQueries = queries;
        mCount = mQueries.length;
        mCallback = callback;
        mRock = rock;

        start();
    }

    /**
     * Internal thread handler. Do not call.
     */
    public Object construct() {
        for (int ix=0; ix<mCount; ix++) {
            Query query = mQueries[ix];
            query.mWorker = new DiscoWorker(mQueryType, query);
        }

        for (int ix=0; ix<mCount; ix++) {
            Query query = mQueries[ix];
            query.mWorker.start();
        }

        for (int ix=0; ix<mCount; ix++) {
            Query query = mQueries[ix];
            Object result = query.mWorker.get();
        }

        return mQueries;
    }

    /**
     * Internal thread handler. Do not call.
     */
    public void finished() {
        /* Resolve all the query results. We do this after the worker threads
         * have finished, because it's less confusing this way.
         */
        for (int ix=0; ix<mCount; ix++) {
            Query query = mQueries[ix];
            query.resolve();
        }

        mCallback.run(mQueries, mRock);
    }

    /**
     * A simple SwingWorker class that performs a disco query. We aren't using
     * DiscoBackground here because we don't want to fool around with callbacks
     * for the individual queries. Instead, the caller (the construct() method
     * above) does a get() on each worker, which means it will block until all
     * the workers are done.
     */
    protected class DiscoWorker extends SwingWorker {
        int mQueryType;
        Query mQuery;

        public DiscoWorker(int qtype, Query query) {
            mQueryType = qtype;
            mQuery = query;            
        }

        public Object construct() {
            try {
                Object res;

                if (mQueryType == QUERY_INFO) {
                    if (mQuery.mNode == null)
                        res = mDiscoMan.discoverInfo(mQuery.mJID);
                    else
                        res = mDiscoMan.discoverInfo(mQuery.mJID, mQuery.mNode);
                }
                else {
                    if (mQuery.mNode == null)
                        res = mDiscoMan.discoverItems(mQuery.mJID);
                    else
                        res = mDiscoMan.discoverItems(mQuery.mJID, mQuery.mNode);
                }
                return res;
            }
            catch (XMPPException ex) {
                return ex;
            }

        }
    }
}
