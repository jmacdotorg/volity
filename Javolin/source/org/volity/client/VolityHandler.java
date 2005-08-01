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
  }

  RPCResponder responder;
  GameUI gameUI;

    // Inherited from RPCHandler.
    public void handleRPC(String methodName, List params, RPCResponseHandler k) {
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
	    //	    String foo = (String)params.get(0);
	    //	    setPlayerStatus(foo, 2);
	    setPlayerStatus((String)params.get(0), 2);
	    System.out.println((String)params.get(0) + " is ready.");
	} else if (methodName.equals("player_unready")) {
	    setPlayerStatus((String)params.get(0), 1);
	    System.out.println((String)params.get(0) + " is unready.");
	} else if (methodName.equals("player_stood")) {
	    setPlayerStatus((String)params.get(0), 0);
	    System.out.println((String)params.get(0) + " just stood up.");
	} else {
	    k.respondFault(999, "I don't know what to do about the volity RPC request " + methodName);
	}

    }

    /**
     * A player's status has changed!
     * First, we tell the table about it.
     * Then we tell any registered listeners about it. (This is probably
     * just going to be the appropriate TableUI.)
     * @param user A MUCUser object.
     * @param status An integer value representing the new status.
     *              0 - standing; 1 - unready; 2 - ready
     */
    private void setPlayerStatus(String jid, int status) {
	try 
	    {
		gameUI.table.setPlayerStatus(jid, status);
	    } 
	catch (Exception e)
	    {
		System.err.println("Got an exception: " + e.toString());
	    }
	for (Iterator it = gameUI.table.statusListeners.iterator(); it.hasNext(); ) {
	    ((StatusListener) it.next()).playerStatusChange(jid, status);
	}
    }

}
