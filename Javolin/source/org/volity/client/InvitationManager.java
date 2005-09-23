package org.volity.client;

import java.util.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.volity.jabber.packet.RPCRequest;
import org.volity.jabber.RPCHandler;
import org.volity.jabber.RPCResponder;
import org.volity.jabber.RPCResponseHandler;

/**
 * A class for receiving invitations to a game table.
 */
public class InvitationManager implements PacketFilter, RPCHandler {
  /**
   * Create a new invitation manager.
   * @param connection an authenticated XMPP connection
   */
  public InvitationManager(XMPPConnection connection) {
    responder = new RPCResponder(connection, this, this);
  }

  RPCResponder responder;
  List listeners = new ArrayList();

  /**
   * Add a game table invitation listener.
   * Note: the listener is notified on a Smack listener thread! Do not
   * do UI work in your invitationReceived() method.
   */
  public void addInvitationListener(InvitationListener listener) {
    listeners.add(listener);
  }

  /** Remove a game table invitation listener. */
  public void removeInvitationListener(InvitationListener listener) {
    listeners.remove(listener);
  }

  /** Start listening for invitations. */
  public void start() {
    responder.start();
  }

  /** Stop listening for invitations. */
  public void stop() {
    responder.stop();
  }

  // Implements PacketFilter interface.
  public boolean accept(Packet packet) {
    return "volity.receive_invitation".
      equals(((RPCRequest) packet).getMethodName());
  }

  // Implements PacketFilter interface.
  public void handleRPC(String methodName, List params,
			RPCResponseHandler responseHandler)
  {
    if (params.size() != 1) {
      responseHandler.respondFault(604, "missing argument in invitation");
      return;
    }
    Object map = params.get(0);
    if (!(map instanceof Map)) {
      responseHandler.respondFault(605, "invitation argument is not struct");
      return;
    }
    Invitation invitation = new Invitation((Map)map);
    responseHandler.respondValue(Boolean.TRUE);
    for (Iterator it = listeners.iterator(); it.hasNext(); )
      ((InvitationListener) it.next()).invitationReceived(invitation);
  }
}
