package org.volity.client;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.volity.jabber.RPCRequester;
import org.volity.jabber.RPCException;

/** A Jabber-RPC connection to a Volity game referee. */
public class Referee extends RPCRequester {
  /**
   * @param table the game table where the game will be played
   * @throws IllegalStateException if the MUC has not been joined
   */
  public Referee(GameTable table) {
    super(table.getConnection(), table.getRefereeJID());
    if (!table.isJoined())
      throw new IllegalStateException("Not sitting at the table.");
  }

  /**
   * Ask the referee to add a bot to this game.
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void addBot() throws XMPPException, RPCException {
    invoke("add_bot");
  }

  /**
   * Ask the referee to start the game.
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void startGame() throws XMPPException, RPCException {
    invoke("start_game");
  }

  /**
   * Send a text message to the referee.
   * @throws XMPPException if an XMPP error occurs
   */
  public void sendMessage(String message) throws XMPPException {
    getConnection().createChat(getResponderJID()).sendMessage(message);
  }
}
