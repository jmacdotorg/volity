package org.volity.jabber;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.ProviderManager;
import org.volity.jabber.packet.*;
import org.volity.jabber.provider.*;
import java.util.*;

/**
 * A class for responding to Jabber-RPC requests.
 *
 * @author Doug Orleans (dougo@place.org)
 */
public abstract class RPCResponder implements PacketListener {
  static {
    // Register the provider so that request packets get parsed correctly.
    ProviderManager.addIQProvider("query", "jabber:iq:rpc", new RPCProvider());
  }

  /**
   * @param connection an authenticated connection to an XMPP server
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public RPCResponder(XMPPConnection connection) {
    if (!connection.isAuthenticated())
      throw new IllegalStateException("Not logged in.");
    this.connection = connection;
    connection.addPacketListener(this, new PacketTypeFilter(RPCRequest.class));
  }

  protected XMPPConnection connection;
  public XMPPConnection getConnection() { return connection; }

  // Inherited from PacketListener.
  public void processPacket(Packet packet) {
    RPCRequest req = (RPCRequest) packet;
    RPCResponse resp;
    try {
      Object value = handleRPC(req.getMethodName(), req.getParams());
      resp = new RPCResult(value);
    } catch (RPCException e) {
      resp = e.getFault();
    }
    resp.setTo(req.getFrom());
    resp.setPacketID(req.getPacketID());
    connection.sendPacket(resp);
  }

  /**
   * Handle a remote procedure call.
   * @param methodName the name of the called procedure
   * @param params the list of parameter values
   * @return the resulting value of the call
   * @throws RPCException if a fault occurs
   */
  public abstract Object handleRPC(String methodName, List params)
    throws RPCException;
}
