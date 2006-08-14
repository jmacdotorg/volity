package org.volity.client;

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.MUCUser;
import org.mozilla.javascript.*;
import org.volity.client.comm.DiscoBackground;
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

    public void stop() {
        gameUI = null;
    }

    // Implements RPCHandler interface.
    public void handleRPC(String methodName, List params, 
        RPCResponseHandler k) {

        if (gameUI == null) {
            k.respondFault(608, "this RPC handler is dead");
            return;
        }

        GameTable table = gameUI.table;

        // Any argument mismatches will show up as runtime exceptions. 
        // Should we catch those? (What would we do with them?)

        // Figure out which method to call now. There are only a handful
        // of possibilities so we'll just use an if/else chain.

        if (methodName.equals("start_game")) {
            // The ref is telling us that it's time to start this game.
            table.setRefereeState(GameTable.STATE_ACTIVE);
            table.setAllPlayersUnready();
            if (!callUI(gameUI.volity, "start_game", params))
                callUI(gameUI.game, "START", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("end_game")) {
            // The ref is telling us that this game is over.
            table.setRefereeState(GameTable.STATE_SETUP);
            table.setAllPlayersUnready();
            if (!callUI(gameUI.volity, "end_game", params))
                callUI(gameUI.game, "END", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("game_has_started")) {
            callUI(gameUI.volity, "game_has_started", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("game_activity")) {
            table.setRefereeState((String)params.get(0));
            callUI(gameUI.volity, "game_activity", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("game_validation")) {
            table.setRefereeState((String)params.get(0));
            callUI(gameUI.volity, "game_validation", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("player_ready")) {
            table.setPlayerReadiness((String)params.get(0), true);
            callUI(gameUI.volity, "player_ready", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("player_unready")) {
            table.setPlayerReadiness((String)params.get(0), false);
            callUI(gameUI.volity, "player_unready", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("player_stood")) {
            table.setAllPlayersUnready();
            table.setPlayerSeat((String)params.get(0), null);
            callUI(gameUI.volity, "player_stood", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("player_sat")) {
            table.setAllPlayersUnready();
            table.setPlayerSeat((String)params.get(0), (String)params.get(1));
            callUI(gameUI.volity, "player_sat", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("seat_list")) {
            table.setSeats((List)params.get(0));
            callUI(gameUI.volity, "seat_list", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("required_seat_list")) {
            table.setRequiredSeats((List)params.get(0));
            callUI(gameUI.volity, "required_seat_list", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("receive_state")) {
            table.setInStateRecovery(true);
            String foundstate = null;
            if (params.size() > 0 && (params.get(0) instanceof Map)) {
                Map map = (Map)params.get(0);
                Object obj = map.get("state");
                if (obj instanceof String)
                    foundstate = (String)obj;
            }
            if (foundstate != null) {
                table.setRefereeState(foundstate);
            }
            else {
                queryRefereeState();
            }
            callUI(gameUI.volity, "receive_state", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("state_sent")) {
            callUI(gameUI.volity, "state_sent", params);
            table.setInStateRecovery(false);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("suspend_game")) {
            table.setRefereeState(GameTable.STATE_SUSPENDED);
            table.setAllPlayersUnready();
            callUI(gameUI.volity, "suspend_game", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("resume_game")) {
            table.setRefereeState(GameTable.STATE_ACTIVE);
            table.setAllPlayersUnready();
            callUI(gameUI.volity, "resume_game", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("language")) {
            table.setAllPlayersUnready();
            callUI(gameUI.volity, "language", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("show_table")) {
            table.setAllPlayersUnready();
            callUI(gameUI.volity, "show_table", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("record_games")) {
            table.setAllPlayersUnready();
            callUI(gameUI.volity, "record_games", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("kill_game")) {
            table.setAllPlayersUnready();
            callUI(gameUI.volity, "kill_game", params);
            k.respondValue(Boolean.TRUE);
        } else if (methodName.equals("message")) {
            /* The callUI() calls above throw the params into a type-checking
             * script engine. This call, however, needs to extract the
             * arguments directly. So we must be careful about argument
             * types. */
            if (params.size() != 1) {
                k.respondFault(604, "volity.message: wrong number of arguments");
                return;
            }
            Object arg = params.get(0);
            if (!(arg instanceof List)) {
                k.respondFault(605, "volity.message: argument is not array");
                return;
            }
            List tokls = (List)arg;
            if (tokls.size() == 0) {
                k.respondFault(606, "volity.message: array is empty");
                return;
            }
            for (int ix=0; ix<tokls.size(); ix++) {
                if (!(tokls.get(ix) instanceof String)) {
                    k.respondFault(605, "volity.message: array contains non-string");
                    return;
                }
            }
            gameUI.messageHandler.print(gameUI.translator.translate(tokls));
            k.respondValue(Boolean.TRUE);
        } else {
            k.respondFault(603,
                "unknown RPC request volity." + methodName);
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
        if (obj == null || gameUI == null || gameUI.scope == null) {
            return false;
        }

        Object method = obj.get(name, gameUI.scope);
        if (method == null || !(method instanceof Function)) {
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

    /**
     * If the referee doesn't provide a game state in the receive_state RPC, we
     * have to do a disco query for it. (This only exists to support old
     * referee code.)
     */
    protected void queryRefereeState() {
        if (gameUI == null) {
            return;
        }

        final GameTable table = gameUI.table;

        DiscoBackground.Callback callback = new DiscoBackground.Callback() {
                public void run(IQ result, XMPPException err, Object rock) {
                    if (err != null) {
                        gameUI.errorHandler.error(err);
                        return;
                    }
                    assert (result != null && result instanceof DiscoverInfo);
                    DiscoverInfo info = (DiscoverInfo)result;
                    Form form = Form.getFormFrom(info);
                    if (form != null) {
                        FormField field = form.getField("state");
                        if (field != null) {
                            String refState = (String) field.getValues().next();
                            table.setRefereeState(refState);
                        }
                    }
                }
            };

        new DiscoBackground(table.getConnection(), 
            callback,
            DiscoBackground.QUERY_INFO,
            table.getReferee().getResponderJID(), null);
    }
}
