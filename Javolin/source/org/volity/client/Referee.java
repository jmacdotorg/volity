package org.volity.client;

import java.util.Arrays;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.volity.jabber.RPCException;
import org.volity.client.TokenRequester;
import org.volity.client.TokenFailure;

/** A Jabber-RPC connection to a Volity game referee. */
public class Referee extends TokenRequester {

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
    throws XMPPException, RPCException, TokenFailure
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
    throws XMPPException, RPCException, TokenFailure
  {
    invoke("volity.invite_player",
           Arrays.asList(new String[] { jid }));
  }

  /**
   * Ask the referee to add a bot to this game.
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void addBot() 
    throws XMPPException, RPCException, TokenFailure
  {
    invoke("volity.add_bot");
  }

  /**
   * DEPRECATED. This method should be deleted by the release of Javolin 0.1
   * --jmac
   * Ask the ref to start the game, by sitting and then declaring readiness.
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void startGame() throws XMPPException, RPCException, TokenFailure
  {
    invoke("volity.unready");
    invoke("volity.ready");
  }

  /**
   * Send a text message to the referee.
   * @throws XMPPException if an XMPP error occurs
   */
  public void sendMessage(String message) throws XMPPException {
    getConnection().createChat(getResponderJID()).sendMessage(message);
  }

    /**
     * Tell the ref that we're playing but unready.
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void unready() throws XMPPException, RPCException, TokenFailure 
    {
        invoke("volity.unready");
    }

    /**
     * Tell the ref that we're ready to play
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void ready() throws XMPPException, RPCException, TokenFailure
    {
        invoke("volity.ready");
    }

    /**
     * Tell the ref that we're not playing at all.
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void stand() throws XMPPException, RPCException, TokenFailure
    {
        invoke("volity.stand");
    }

}
