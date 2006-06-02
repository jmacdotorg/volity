package org.volity.client;

import java.util.Arrays;
import java.util.List;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.volity.client.comm.RPCBackground;
import org.volity.client.comm.RPCDispatcherDebug;
import org.volity.client.translate.TokenFailure;
import org.volity.client.translate.TokenRequester;
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
     * Customize the invoke methods in TokenRequester. (They all funnel into
     * this version.) The customization is to print debug output (if desired).
     * Also, if the game is dead, return a token error instead of risking a
     * Jabber 404.
     */
    public Object invokeTimeout(String methodName, List params, int timeout)
        throws XMPPException, RPCException, TokenFailure {
        if (mCrashed)
            throw new TokenFailure("volity.referee_not_ready");
        if (debugFlag && messageHandler != null) {
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
     *
     * We also call this when the TableWindow closes, because it's
     * approximately the same situation: we want to stop sending messages out.
     * As an added bonus, it drops the Table reference.
     */
    public void setCrashed() {
        mCrashed = true;
        mTable = null;
    }

    /**
     * This is a grody hack to get a messageHandler into the referee. It's only
     * needed for debug RPC output.
     */
    public void setMessageHandler(GameUI.MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * Send a text message to the referee.
     * @throws XMPPException if an XMPP error occurs
     */
    public void sendMessage(String message) throws XMPPException {
        getConnection().createChat(getResponderJID()).sendMessage(message);
    }

    /**
     * Ask the referee to invite a player to this game.
     * @param jid the JID of the player to invite
     */
    public void invitePlayer(String jid,
        RPCBackground.Callback callback, Object rock)
    {
        invitePlayer(jid, null, callback, rock);
    }

    /**
     * Ask the referee to invite a player to this game.
     * @param jid the JID of the player to invite
     * @param message a text message to include with the invitation
     *        (If null or empty, no message is sent.)
     */
    public void invitePlayer(String jid, String message,
        RPCBackground.Callback callback, Object rock)
    {
        List args;
        if (message == null || message.equals(""))
            args = Arrays.asList(new String[] { jid });
        else
            args = Arrays.asList(new String[] { jid, message });

        // Allow a longer timeout than usual, since the message has to be
        // relayed through the referee.
        new RPCBackground(this, callback, 
            "volity.invite_player", args, 90, rock);
    }

    /**
     * Ask the referee to add a bot to this game.
     */
    public void addBot(RPCBackground.Callback callback, Object rock) 
    {
        new RPCBackground(this, callback, 
            "volity.add_bot", rock);
    }

    /**
     * Ask the referee to add a bot to this game, with a particular URI and
     * JID.
     */
    public void addBot(String uri, String jid,
        RPCBackground.Callback callback, Object rock) 
    {
        List args = Arrays.asList(new String[] { uri, jid });

        new RPCBackground(this, callback, 
            "volity.add_bot", args, rock);
    }

    /**
     * Ask the referee to remove a bot from this game.
     * @param jid the (real) JID of the bot to remove.
     */
    public void removeBot(String jid,
        RPCBackground.Callback callback, Object rock)
    {
        List args = Arrays.asList(new String[] { jid });

        new RPCBackground(this, callback, 
            "volity.remove_bot", args, rock);
    }

    /**
     * Tell the ref to send us all the seating and configuration information. 
     * (After the first invocation, we will receive seating/config updates
     * as they occur.)
     */
    public void send_state(RPCBackground.Callback callback, Object rock)
    {
        new RPCBackground(this, callback, 
            "volity.send_state", rock);
    }

    /**
     * Tell the ref that we're playing but unready.
     */
    public void unready(RPCBackground.Callback callback, Object rock)
    {
        new RPCBackground(this, callback, 
            "volity.unready", rock);
    }

    /**
     * Tell the ref that we're ready to play
     */
    public void ready(RPCBackground.Callback callback, Object rock)
    {
        new RPCBackground(this, callback, 
            "volity.ready", rock);
    }

    /**
     * Tell the ref that we're standing.
     */
    public void stand(RPCBackground.Callback callback, Object rock)
    {
        if (mTable == null) {
            callback.run(null, new TokenFailure("volity.referee_not_ready"), rock);
            return;
        }
        this.stand(mTable.getSelfPlayer(), callback, rock);
    }

    /**
     * Ask the ref to cause someone to stand up.
     * @param player The player to yank from his seat.
     */
    public void stand(Player player,
        RPCBackground.Callback callback, Object rock)
    {
        String jid = player.getJID();
        List args = Arrays.asList(new String[] { jid });

        new RPCBackground(this, callback, 
            "volity.stand", args, rock);
    }

    /**
     * Tell the ref that we want to sit in any seat.
     */
    public void sit(RPCBackground.Callback callback, Object rock)
    {
        if (mTable == null) {
            callback.run(null, new TokenFailure("volity.referee_not_ready"), rock);
            return;
        }
        sit(mTable.getSelfPlayer(), null, callback, rock);
    }

    /**
     * Tell the ref that we want to sit in a specific seat.
     * @param seat the seat to sit in (or null for "any seat")
     */
    public void sit(Seat seat, RPCBackground.Callback callback, Object rock)
    {
        if (mTable == null) {
            callback.run(null, new TokenFailure("volity.referee_not_ready"), rock);
            return;
        }
        sit(mTable.getSelfPlayer(), seat, callback, rock);
    }

    /**
     * Tell the ref that we want a player to sit in any seat.
     * @param player the player to move
     */
    public void sit(Player player,
        RPCBackground.Callback callback, Object rock)
    {
        sit(player, null, callback, rock);
    }

    /**
     * Tell the ref that we want a player to sit in a specific seat.
     * @param player the player to move
     * @param seat the seat to sit in (or null for "any seat")
     */
    public void sit(Player player, Seat seat,
        RPCBackground.Callback callback, Object rock)
    {
        String jid = player.getJID();
        List args;

        if (seat == null) {
            args = Arrays.asList(new String[] { jid });
        }
        else {
            String seatid = seat.getID();
            args = Arrays.asList(new String[] { jid, seatid });
        }
        
        new RPCBackground(this, callback, 
            "volity.sit", args, rock);
    }
     
    /**
     * Ask the referee to suspend this game.
     */
    public void suspendGame(RPCBackground.Callback callback, Object rock) 
    {
        new RPCBackground(this, callback, 
            "volity.suspend_game", rock);
    }
    

}
