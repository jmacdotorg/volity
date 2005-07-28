package org.volity.client;

import java.util.List;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.volity.jabber.RPCRequester;
import org.volity.jabber.RPCException;
import org.volity.client.TokenFailure;

/**
 * A class for making Jabber-RPC requests to a service which responds
 * with token-style lists.
 *
 * This matches the API of RPCRequester, except that the invoke methods
 * can throw TokenFailure in addition to the other exceptions.
 *
 * @author Andrew Plotkin (erkyrath@eblong.com)
 */
public class TokenRequester {

    public String VOLITY_OK_TOKEN = "volity.ok";
    
    /**
     * @param connection an authenticated connection to an XMPP server
     * @param responderJID the JID of the responder to these requests
     * @throws IllegalStateException if the connection has not been 
     *         authenticated
     */
    public TokenRequester(XMPPConnection connection, String responderJID) {
        this.requester = new RPCRequester(connection, responderJID);
    }

    protected RPCRequester requester;

    public XMPPConnection getConnection() 
    { 
        return requester.getConnection(); 
    }

    public String getResponderJID() 
    { 
        return requester.getResponderJID(); 
    }

    /**
     * Invoke a remote method with no parameters and wait (up to 30 seconds)
     * for the response.
     * @param methodName the name of the remote method
     * @return the result of the remote method invocation
     * @throws XMPPException if there was an XMPP error
     * @throws RPCException if the remote method resulted in a fault
     *                      or timed out
     * @throws TokenFailure if the remote method returned a non-success token
     */
    public Object invoke(String methodName)
        throws XMPPException, RPCException, TokenFailure
    {
        return invokeTimeout(methodName, null, 30);
    }

    /**
     * Invoke a remote method and wait (up to 30 seconds) for the response.
     * @param methodName the name of the remote method
     * @param params the list of method parameters
     * @return the result of the remote method invocation
     * @throws XMPPException if there was an XMPP error
     * @throws RPCException if the remote method resulted in a fault
     *                      or timed out
     * @throws TokenFailure if the remote method returned a non-success token
     */
    public Object invoke(String methodName, List params)
        throws XMPPException, RPCException, TokenFailure
    {
        return invokeTimeout(methodName, params, 30);
    }

    /**
     * Invoke a remote method, with no parameters, and wait (up to a limit) 
     * for the response.
     * @param methodName the name of the remote method
     * @param timeout the time (in seconds) to wait for a response
     * @return the result of the remote method invocation
     * @throws XMPPException if there was an XMPP error
     * @throws RPCException if the remote method resulted in a fault
     *                      or timed out
     * @throws TokenFailure if the remote method returned a non-success token
     */
    public Object invokeTimeout(String methodName, int timeout)
        throws XMPPException, RPCException, TokenFailure
    {
        return invokeTimeout(methodName, null, timeout);
    }

    /**
     * Invoke a remote method and wait (up to a limit) for the response.
     * @param methodName the name of the remote method
     * @param params the list of method parameters
     * @param timeout the time (in seconds) to wait for a response
     * @return the result of the remote method invocation
     * @throws XMPPException if there was an XMPP error
     * @throws RPCException if the remote method resulted in a fault
     *                      or timed out
     * @throws TokenFailure if the remote method returned a non-success token
     */
    public Object invokeTimeout(String methodName, List params, int timeout)
        throws XMPPException, RPCException, TokenFailure
    {
        Object val = this.requester.invokeTimeout(methodName, params, timeout);

        if (!(val instanceof List)) {
            throw new RPCException(606, "Response did not begin with a token");
        }

        List lval = (List) val;
        if (lval.isEmpty()) {
            throw new RPCException(606, "Response did not begin with a token");
        }
        if (!VOLITY_OK_TOKEN.equals(lval.get(0))) {
            /* A failure response is a list of tokens. */
            throw new TokenFailure(lval);
        }
        /* A success response must contain zero or one values after
         * the "volity.ok". 
         */
        if (lval.size() == 1)
            return null;
        if (lval.size() > 2) {
            throw new RPCException(606, 
                "Response of volity.ok contained more than one value");
        }
        return lval.get(1);
    }
}
