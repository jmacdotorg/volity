package org.volity.jabber;

import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.provider.ProviderManager;
import org.volity.jabber.packet.*;
import org.volity.jabber.provider.*;
import java.util.*;

/**
 * A class for making Jabber-RPC requests.
 *
 * @author Doug Orleans (dougo@place.org)
 */
public class RPCRequester {

  public static int DEFAULT_RPC_TIMEOUT = 30;

  static {
    // Register the provider so that response packets get parsed correctly.
    ProviderManager.addIQProvider(RPC.elementName,
                                  RPC.namespace,
                                  new RPCProvider());
  }

  /**
   * @param connection an authenticated connection to an XMPP server
   * @param responderJID the JID of the responder to these requests
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public RPCRequester(XMPPConnection connection, String responderJID) {
    if (connection == null || !connection.isAuthenticated())
      throw new IllegalStateException("Not logged in.");
    this.connection = connection;
    this.responderJID = responderJID;
  }

  protected XMPPConnection connection;
  public XMPPConnection getConnection() { return connection; }

  protected String responderJID;
  public String getResponderJID() { return responderJID; }

  /**
   * Invoke a remote method with no parameters and wait (up to 30 seconds)
   * for the response.
   * @param methodName the name of the remote method
   * @return the result of the remote method invocation
   * @throws XMPPException if there was an XMPP error
   * @throws RPCException if the remote method resulted in a fault
   *                      or timed out
   */
  public Object invoke(String methodName)
    throws XMPPException, RPCException
  {
    return invokeTimeout(methodName, null, DEFAULT_RPC_TIMEOUT);
  }

  /**
   * Invoke a remote method and wait (up to 30 seconds) for the response.
   * @param methodName the name of the remote method
   * @param params the list of method parameters
   * @return the result of the remote method invocation
   * @throws XMPPException if there was an XMPP error
   * @throws RPCException if the remote method resulted in a fault
   *                      or timed out
   */
  public Object invoke(String methodName, List params)
    throws XMPPException, RPCException
  {
    return invokeTimeout(methodName, params, DEFAULT_RPC_TIMEOUT);
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
   */
  public Object invokeTimeout(String methodName, int timeout)
    throws XMPPException, RPCException
  {
    return invokeTimeout(methodName, null, timeout);
  }

  /**
   * Invoke a remote method and wait (up to a limit) for the response.
   * @param methodName the name of the remote method
   * @param params the list of method parameters
   * @param timeout the time (in seconds) to wait for a response
   * @return the result of the remote method invocation
   * @throws XMPPException if there was an XMPP error, or the method timed out
   * @throws RPCException if the remote method resulted in a fault
   */
  public Object invokeTimeout(String methodName, List params, int timeout)
    throws XMPPException, RPCException
  {
    RPCRequest request = new RPCRequest(methodName, params);
    request.setTo(responderJID);
    final String requestID = request.getPacketID(); // auto-generated
    PacketCollector collector =
      connection.createPacketCollector(new PacketFilter() {
          public boolean accept(Packet packet) {
            String id = packet.getPacketID();
            if (id != null && id.equals(requestID)) {
                if (packet instanceof RPCResponse)
                    return true;
                if (packet instanceof RPCRequest 
                    && packet.getError() != null)
                    return true;
            }
            return false;
          }
        });
    connection.sendPacket(request);

    Packet response = collector.nextResult(1000*timeout);
    collector.cancel();
    if (response == null)
      throw new XMPPException("Timed out waiting for response.");
    XMPPError error = response.getError();
    if (error != null)
      throw new XMPPException(error);
    if (response instanceof RPCFault)
      throw new RPCException((RPCFault) response);
    if (!(response instanceof RPCResult))
      throw new AssertionError("RPC response was of a strange class");
    return ((RPCResult) response).getValue();
  }
}
