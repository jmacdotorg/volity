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
public class RPCResponder implements PacketListener {
  static {
    // Register the provider so that request packets get parsed correctly.
    ProviderManager.addIQProvider("query", "jabber:iq:rpc", new RPCProvider());
  }

  /**
   * @param connection an authenticated connection to an XMPP server
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public RPCResponder(XMPPConnection connection, RPCHandler handler) {
    if (!connection.isAuthenticated())
      throw new IllegalStateException("Not logged in.");
    this.connection = connection;
    this.handler = handler;
  }

  protected XMPPConnection connection;
  public XMPPConnection getConnection() { return connection; }

  protected RPCHandler handler;
  public RPCHandler getHandler() { return handler; }

  /**
   * Start listening for requests.
   */
  public void start() {
    connection.addPacketListener(this, new PacketTypeFilter(RPCRequest.class));
  }

  /**
   * Stop listening for requests.
   */
  public void stop() {
    connection.removePacketListener(this);
  }

  // Inherited from PacketListener.
  public void processPacket(Packet packet) {
    RPCRequest req = (RPCRequest) packet;
    RPCResponse resp;
    try {
      Object value = handler.handleRPC(req.getMethodName(), req.getParams());
      resp = new RPCResult(value);
    } catch (RPCException e) {
      resp = e.getFault();
    }
    resp.setTo(req.getFrom());
    resp.setPacketID(req.getPacketID());
    connection.sendPacket(resp);
  }
}
