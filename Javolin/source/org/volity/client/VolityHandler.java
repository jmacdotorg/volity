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
		k.respondValue(gameUI.callUIMethod((Function) method, params));
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
		k.respondValue(gameUI.callUIMethod((Function) method, params));
	    } catch (JavaScriptException e) {
		gameUI.errorHandler.error(e);
		k.respondFault(901, "UI script exception: " + e);
	    } catch (EvaluatorException e) {
		gameUI.errorHandler.error(e);
		k.respondFault(902, "UI script error: " + e);
	    }		    
	} else {
	    k.respondFault(999, "I don't know what to do about the volity RPC request " + methodName);
	}
	
    }

}
