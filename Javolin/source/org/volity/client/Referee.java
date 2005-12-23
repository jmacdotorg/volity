package org.volity.client;

import java.util.Arrays;
import java.util.List;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.volity.client.TokenFailure;
import org.volity.client.TokenRequester;
import org.volity.jabber.RPCException;

/**
 * A Jabber-RPC connection to a Volity game referee.
 */
public class Referee extends TokenRequester {
  protected static boolean debugFlag = false;

  /**
   * Set whether debugging output is active.
   */
  public static void setDebugOutput(boolean flag) {
    debugFlag = flag;
  }


  GameTable mTable;
  GameUI.MessageHandler messageHandler;
  boolean mCrashed;

  /**
   * @param table the game table where the game will be played
   * @param jid the full JID of the referee
   */
  Referee(GameTable table, String jid) {
    super(table.getConnection(), jid);
    mTable = table;
    mCrashed = false;
  }  

  /**
   * Customize the invoke methods in TokenRequester. (They all funnel into this
   * version.) The customization is to print debug output (if desired). Also,
   * if the game is dead, return a token error instead of risking a Jabber 404.
   */
  public Object invokeTimeout(String methodName, List params, int timeout)
    throws XMPPException, RPCException, TokenFailure {
    if (mCrashed)
      throw new TokenFailure("volity.referee_not_ready");
    if (debugFlag) {
      String msg = RPCDispatcherDebug.buildCallString("send", methodName, params);
      messageHandler.print(msg);
    }
    return super.invokeTimeout(methodName, params, timeout);
  }

  /**
   * The client can notice in several ways that the game has crashed. (For
   * example, it might see the referee withdraw from the MUC, or it might see
   * the MUC close down entirely.) When this happens, we want to set a flag
   * which prevents further messages from going out to the ref.
   */
  public void setCrashed() {
    mCrashed = true;
  }

  /**
   * This is a grody hack to get a messageHandler into the referee. It's only
   * needed for debug RPC output.
   */
  public void setMessageHandler(GameUI.MessageHandler messageHandler) {
    this.messageHandler = messageHandler;
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
    invitePlayer(jid, null);
  }

  /**
   * Ask the referee to invite a player to this game.
   * @param jid the JID of the player to invite
   * @param message a text message to include with the invitation
   *        (If null or empty, no message is sent.)
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if a RPC fault occurs
   */
  public void invitePlayer(String jid, String message)
    throws XMPPException, RPCException, TokenFailure
  {
    List args;
    if (message == null || message.equals(""))
      args = Arrays.asList(new String[] { jid });
    else
      args = Arrays.asList(new String[] { jid, message });

    // Allow a longer timeout than usual, since the message has to be
    // relayed through the referee.
    invokeTimeout("volity.invite_player", args, 90);
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
   * Send a text message to the referee.
   * @throws XMPPException if an XMPP error occurs
   */
  public void sendMessage(String message) throws XMPPException {
    getConnection().createChat(getResponderJID()).sendMessage(message);
  }

    /**
     * Tell the ref to send us all the seating and configuration information. 
     * (After the first invocation, we will receive seating/config updates
     * as they occur.)
     *
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void send_state() throws XMPPException, RPCException, TokenFailure 
    {
        invoke("volity.send_state");
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
     * Tell the ref that we're standing.
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void stand() throws XMPPException, RPCException, TokenFailure
    {
        this.stand(mTable.getSelfPlayer());
    }

    /**
     * Ask the ref to cause someone to stand up.
     * @param player The player to yank from his seat.
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void stand(Player player) throws XMPPException, RPCException, TokenFailure
    {
        String jid = player.getJID();
        invoke("volity.stand",
            Arrays.asList(new String[] { jid }));
    }

    /**
     * Tell the ref that we want to sit in any seat.
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void sit() throws XMPPException, RPCException, TokenFailure
    {
        sit(mTable.getSelfPlayer(), null);
    }

    /**
     * Tell the ref that we want to sit in a specific seat.
     * @param seat the seat to sit in (or null for "any seat")
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void sit(Seat seat) throws XMPPException, RPCException, TokenFailure
    {
        sit(mTable.getSelfPlayer(), seat);
    }

    /**
     * Tell the ref that we want a player to sit in any seat.
     * @param player the player to move
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void sit(Player player) throws XMPPException, RPCException, TokenFailure
    {
        sit(player, null);
    }

    /**
     * Tell the ref that we want a player to sit in a specific seat.
     * @param player the player to move
     * @param seat the seat to sit in (or null for "any seat")
     * @throws XMPPException if an XMPP error occurs
     * @throws RPCException if a RPC fault occurs
     */
    public void sit(Player player, Seat seat) throws XMPPException, RPCException, TokenFailure
    {
        String jid = player.getJID();

        if (seat == null) {
            invoke("volity.sit",
                Arrays.asList(new String[] { jid }));
        }
        else {
            String seatid = seat.getID();
            invoke("volity.sit",
                Arrays.asList(new String[] { jid, seatid }));
        }
    }
     

}
