package org.volity.client;

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.packet.MUCUser;
import org.mozilla.javascript.*;
import org.volity.jabber.*;

/**
 * A handler for volity-level RPC calls.
 * by Jason McIntosh <jmac@jmac.org>
 */
public class VolityHandler implements RPCHandler {
  public VolityHandler() {
      System.out.println("Created volityhandler object.");
  }

  RPCResponder responder;
  GameUI gameUI;

    // Inherited from RPCHandler.
    public void handleRPC(String methodName, List params, RPCResponseHandler k) {
	System.out.println("handleRPC called on " + methodName);

        // Any argument mismatches will show up as runtime exceptions. 
        // Should we catch those? (What would we do with them?)

	// Figure out which method to call now. There are only a handful
	// of possibilities so we'll just use an if/else chain.
	if (methodName.equals("start_game")) {
	    // The ref is telling us that it's time to start this game.
	    //	    Object method = gameUI.game.get("START", gameUI.scope);
	    Object method = gameUI.game.get("START", gameUI.scope);
	    try {
                if (method != Scriptable.NOT_FOUND) {
                    k.respondValue(gameUI.callUIMethod((Function) method, params));
                }
	    } catch (JavaScriptException e) {
		gameUI.errorHandler.error(e);
		k.respondFault(901, "UI script exception: " + e);
	    } catch (EvaluatorException e) {
		gameUI.errorHandler.error(e);
		k.respondFault(902, "UI script error: " + e);
	    }		    
	} else if (methodName.equals("end_game")) {
	    // The ref is telling us that this game is over.
	    Object method = gameUI.game.get("END", gameUI.scope);
	    try {
                if (method != Scriptable.NOT_FOUND) {
                    k.respondValue(gameUI.callUIMethod((Function) method, params));
                }
	    } catch (JavaScriptException e) {
		gameUI.errorHandler.error(e);
		k.respondFault(901, "UI script exception: " + e);
	    } catch (EvaluatorException e) {
		gameUI.errorHandler.error(e);
		k.respondFault(902, "UI script error: " + e);
	    }		    
	} else if (methodName.equals("player_ready")) {
	    setPlayerReadiness((String)params.get(0), 1);
	    System.out.println((String)params.get(0) + " is ready.");
	} else if (methodName.equals("player_unready")) {
	    setPlayerReadiness((String)params.get(0), 0);
	    System.out.println((String)params.get(0) + " is unready.");
	} else if (methodName.equals("player_stood")) {
	    standPlayer((String)params.get(0));
	    System.out.println((String)params.get(0) + " just stood up.");
	} else if (methodName.equals("player_sat")) {
	    sitPlayer((String)params.get(0), (String)params.get(1));
	    System.out.println((String)params.get(0) + " just sat in seat " + (String)params.get(1));
	} else if (methodName.equals("seat_list")) {
	    gameUI.table.setSeats((List)params.get(0));
	} else if (methodName.equals("required_seat_list")) {
	    gameUI.table.setRequiredSeats((List)params.get(0));
	} else if (methodName.equals("receive_state")) {
	    System.out.println("Receiving game state.");
	} else if (methodName.equals("state_sent")) {
	    System.out.println("Game state received.");
	} else if (methodName.equals("suspend_game")) {
	    System.out.println("Game suspended.");
	} else if (methodName.equals("resume_game")) {
	    System.out.println("Game resumed.");
	} else if (methodName.equals("kill_game")) {
	    System.out.println("Kill-game call received..");
	} else {
	    k.respondFault(999, "I don't know what to do about the volity RPC request " + methodName);
	}

    }

    /**
     * A player's readiness has changed!
     * First, we tell the table about it.
     * Then we tell any registered listeners about it. (This is probably
     * just going to be the appropriate TableUI.)
     * @param jid A JabberID string.
     * @param status An integer value representing the new readiness.
     *              0 - unready; 1 - ready;
     */
    private void setPlayerReadiness(String jid, int readiness) {
	try 
	    {
		if (readiness == 1) {
		    gameUI.table.playerIsReady(jid);
		} else {
		    gameUI.table.playerIsUnready(jid);
		}
	    } 
	catch (Exception e)
	    {
		System.err.println("Got an exception: " + e.toString());
	    }
	for (Iterator it = gameUI.table.statusListeners.iterator(); it.hasNext(); ) {
	    if (readiness == 1) {
		((StatusListener) it.next()).playerBecameReady(jid);
	    } else {
		((StatusListener) it.next()).playerBecameUnready(jid);
	    }
	}
    }


    /**
     * A player has stood!
     * Tell any registered listeners about it. (This is probably
     * just going to be the appropriate TableUI.)
     * @param jid A JabberID string.
     */
    private void standPlayer(String jid) {
	for (Iterator it = gameUI.table.statusListeners.iterator(); it.hasNext(); ) {
	    ((StatusListener) it.next()).playerStood(jid);
	}
    }

    /**
     * A player has sat!
     * Tell any registered listeners about it. (This is probably
     * just going to be the appropriate TableUI.)
     * @param jid A JabberID string.
     * @param seatID A string representing a seat's ID.
     */
    private void sitPlayer(String jid, String seatId) {
	for (Iterator it = gameUI.table.statusListeners.iterator(); it.hasNext(); ) {
	    ((StatusListener) it.next()).playerSat(jid, seatId);
	}
    }

}
