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
 * A handler for volity-namespace RPC calls.
 *
 * All of this code is called in Smack threads. All of what it does is call
 * GameTable methods, which are safe to call in Smack threads.
 *
 * @author Jason McIntosh <jmac@jmac.org>
 */
public class VolityHandler implements RPCHandler {
    GameUI gameUI;

    public VolityHandler(GameUI ui) {
        gameUI = ui;
    }

    // Inherited from RPCHandler.
    public void handleRPC(String methodName, List params, 
        RPCResponseHandler k) {
        System.out.println("handleRPC: " + methodName + " " + params.toString());

        GameTable table = gameUI.table;

        // Any argument mismatches will show up as runtime exceptions. 
        // Should we catch those? (What would we do with them?)

        // Figure out which method to call now. There are only a handful
        // of possibilities so we'll just use an if/else chain.

        if (methodName.equals("start_game")) {
            // The ref is telling us that it's time to start this game.
            table.setRefereeState(GameTable.STATE_ACTIVE);
            table.setAllPlayersUnready();
            callUI(gameUI.game, "START", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("end_game")) {
            // The ref is telling us that this game is over.
            table.setRefereeState(GameTable.STATE_SETUP);
            table.setAllPlayersUnready();
            callUI(gameUI.game, "END", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("player_ready")) {
            table.setPlayerReadiness((String)params.get(0), true);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("player_unready")) {
            table.setPlayerReadiness((String)params.get(0), false);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("player_stood")) {
            table.setAllPlayersUnready();
            table.setPlayerSeat((String)params.get(0), null);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("player_sat")) {
            table.setAllPlayersUnready();
            table.setPlayerSeat((String)params.get(0), (String)params.get(1));
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("seat_list")) {
            table.setSeats((List)params.get(0));
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("required_seat_list")) {
            table.setRequiredSeats((List)params.get(0));
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("receive_state")) {
            System.out.println("Receiving game state.");
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("state_sent")) {
            System.out.println("Game state received.");
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("suspend_game")) {
            table.setRefereeState(GameTable.STATE_SUSPENDED);
            table.setAllPlayersUnready();
            System.out.println("Game suspended.");
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("resume_game")) {
            table.setRefereeState(GameTable.STATE_ACTIVE);
            table.setAllPlayersUnready();
            System.out.println("Game resumed.");
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("language")) {
            table.setAllPlayersUnready();
            System.out.println("Configuration set language.");
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("show_table")) {
            table.setAllPlayersUnready();
            System.out.println("Configuration set show_table.");
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("record_games")) {
            table.setAllPlayersUnready();
            System.out.println("Configuration set record_games.");
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("kill_game")) {
            table.setAllPlayersUnready();
            System.out.println("Configuration set kill_game.");
            k.respondValue(Boolean.TRUE);
        } else {
            k.respondFault(999, "I don't know what to do about the volity RPC request " + methodName);
        }

    }

    /**
     * Utility function to call a named method of an object. If the method
     * doesn't exist, that's okay. The method's return value is ignored, but if
     * an exception occurs, it is passed back to the GameUI's error handler.
     *
     * @return Whether the method existed.
     */
    protected boolean callUI(Scriptable obj, String name, List params) {
        Object method = obj.get(name, gameUI.scope);
        if (!(method instanceof Function)) {
            // No UI method is the same as a no-op
            return false;
        }

        gameUI.callUIMethod((Function) method, params, 
            new GameUI.Completion() {
                public void result(Object obj) { 
                    // Drop the return value on the floor.
                }
                public void error(Exception ex) {
                    gameUI.errorHandler.error(ex);
                }
            });
        return true;
    }
}
