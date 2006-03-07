package org.volity.client;

import java.util.List;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.volity.client.translate.TokenFailure;
import org.volity.client.translate.TokenRequester;
import org.volity.jabber.RPCException;

/**
 * This class spawns a background thread, performs a Jabber-RPC call in that
 * thread, and then passes the result to a callback. The callback is guaranteed
 * to execute in the Swing event-handler thread.
 *
 * The callback must have the interface RPCBackground.Callback. Its method
 * looks like:
 *
 *   public void run(Object result, Exception err, Object rock) { ... }
 *
 * ...where result is the object returned from the call, err is null (on
 * success), and rock is a reference you passed to the DiscoBackground when you
 * created it. If the RPC fails, then result will be null and err will be set:
 *
 *   XMPPException if there was an XMPP error
 *   RPCException if the remote method resulted in a fault or timed out
 *   TokenFailure if the remote method returned a non-success token
 */
public class RPCBackground extends SwingWorker
{
    public static int DEFAULT_RPC_TIMEOUT = TokenRequester.DEFAULT_RPC_TIMEOUT;

    /**
     * Callback interface. When the disco query finishes, the run() method will
     * be called. Exactly one of result and err will be null.
     */
    public interface Callback {
        public void run(Object result, Exception err, Object rock);
    }

    protected Callback mCallback;
    protected Object mRock;
    protected TokenRequester mTarget;
    protected int mTimeout;
    protected String mMethodName;
    protected List mParams;

    /**
     * Create a background RPC object with a default 30-second timeout.
     *
     * @param target the TokenRequester to send the RPC to.
     * @param callback the callback to invoke when the RPC finshes.
     * @param methodName the method name.
     * @param params the method arguments.
     * @param rock a reference which will be passed to the callback.
     */
    public RPCBackground(TokenRequester target, Callback callback,
        String methodName, List params,
        Object rock) {
        this(target, callback,
            methodName, params, DEFAULT_RPC_TIMEOUT,
            rock);
    }

    /**
     * Create a background RPC object.
     *
     * @param target the TokenRequester to send the RPC to.
     * @param callback the callback to invoke when the RPC finshes.
     * @param methodName the method name.
     * @param timeout the time (in seconds) to wait for a response.
     * @param rock a reference which will be passed to the callback.
     */
    public RPCBackground(TokenRequester target, Callback callback,
        String methodName, int timeout,
        Object rock) {
        this(target, callback,
            methodName, null, timeout,
            rock);
    }

    /**
     * Create a background RPC object with a default 30-second timeout.
     *
     * @param target the TokenRequester to send the RPC to.
     * @param callback the callback to invoke when the RPC finshes.
     * @param methodName the method name.
     * @param rock a reference which will be passed to the callback.
     */
    public RPCBackground(TokenRequester target, Callback callback,
        String methodName,
        Object rock) {
        this(target, callback,
            methodName, null, DEFAULT_RPC_TIMEOUT,
            rock);
    }

    /**
     * Create a background RPC object.
     *
     * @param target the TokenRequester to send the RPC to.
     * @param callback the callback to invoke when the RPC finshes.
     * @param methodName the method name.
     * @param params the method arguments.
     * @param timeout the time (in seconds) to wait for a response.
     * @param rock a reference which will be passed to the callback.
     */
    public RPCBackground(TokenRequester target, Callback callback,
        String methodName, List params, int timeout,
        Object rock) {

        super();

        mTarget = target;
        mMethodName = methodName;
        mParams = params;
        mTimeout = timeout;
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

            res = mTarget.invokeTimeout(mMethodName, mParams, mTimeout);
            return res;
        }
        catch (TokenFailure ex) {
            return ex;
        }
        catch (RPCException ex) {
            return ex;
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
        if (obj instanceof Exception)
            mCallback.run(null, (Exception)obj, mRock);
        else
            mCallback.run(obj, null, mRock);
    }

}
