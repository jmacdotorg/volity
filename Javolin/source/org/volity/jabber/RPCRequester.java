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
   * Invoke a remote method with no parameters and wait 5 seconds for
   * the response.
   * @param methodName the name of the remote method
   * @return the result of the remote method invocation
   * @throws XMPPException if there was an XMPP error
   * @throws RPCException if the remote method resulted in a fault
   *                      or timed out
   */
  public Object invoke(String methodName)
    throws XMPPException, RPCException
  {
    return invoke(methodName, null);
  }

  /**
   * Invoke a remote method and wait 5 seconds for the response.
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
    RPCRequest request = new RPCRequest(methodName, params);
    request.setTo(responderJID);
    final String requestID = request.getPacketID(); // auto-generated
    PacketCollector collector =
      connection.createPacketCollector(new PacketFilter() {
	  public boolean accept(Packet packet) {
	    return packet instanceof RPCResponse &&
	      packet.getPacketID().equals(requestID);
	  }
	});
    connection.sendPacket(request);
    RPCResponse response = (RPCResponse) collector.nextResult(5000);
    collector.cancel();
    if (response == null)
      throw new RPCException(1, "Timed out waiting for response.");
    XMPPError error = response.getError();
    if (error != null)
      throw new XMPPException(error);
    if (response instanceof RPCFault)
      throw new RPCException((RPCFault) response);
    return ((RPCResult) response).getValue();
  }
}
