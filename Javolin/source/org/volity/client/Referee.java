package org.volity.client;

import java.util.Arrays;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.volity.jabber.RPCRequester;
import org.volity.jabber.RPCException;

/** A Jabber-RPC connection to a Volity game referee. */
public class Referee extends RPCRequester {
  /**
   * @param table the game table where the game will be played
   * @param jid the full JID of the referee
   */
  Referee(GameTable table, String jid) {
    super(table.getConnection(), jid);
  }  

  /**
   * Ask the referee to invite a player to this game.
   * @param jid the JID of the player to invite
   * @param message a text message to include with the invitation
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void invitePlayer(String jid, String message)
    throws XMPPException, RPCException
  {
    invoke("volity.invite_player",
	   Arrays.asList(new String[] { jid, message }));
  }

  /**
   * Ask the referee to invite a player to this game.
   * @param jid the JID of the player to invite
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void invitePlayer(String jid)
    throws XMPPException, RPCException
  {
    invoke("volity.invite_player",
	   Arrays.asList(new String[] { jid }));
  }

  /**
   * Ask the referee to add a bot to this game.
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void addBot() throws XMPPException, RPCException {
    invoke("volity.add_bot");
  }

  /**
   * Ask the referee to start the game.
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void startGame() throws XMPPException, RPCException {
    invoke("volity.start_game");
  }

  /**
   * Send a text message to the referee.
   * @throws XMPPException if an XMPP error occurs
   */
  public void sendMessage(String message) throws XMPPException {
    getConnection().createChat(getResponderJID()).sendMessage(message);
  }
}
