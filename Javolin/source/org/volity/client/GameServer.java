package org.volity.client;

import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.volity.jabber.RPCRequester;
import org.volity.jabber.RPCException;

/** A Jabber-RPC connection to a Volity game server. */
public class GameServer extends RPCRequester {
  /**
   * @param connection an authenticated connection to an XMPP server
   * @param JID the JID of the game server
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public GameServer(XMPPConnection connection, String JID) {
    super(connection, JID);
  }

  /**
   * Create a new instance (table) of the game (a Multi-User Chat room).
   * @return the new MUC, which should immediately be joined
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if an RPC fault occurs
   */
  public GameTable newTable() throws XMPPException, RPCException {
    return new GameTable(connection, (String) invoke("new_table"));
  }
}
