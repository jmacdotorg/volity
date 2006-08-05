package org.volity.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.volity.jabber.RPCHandler;
import org.volity.jabber.RPCResponder;
import org.volity.jabber.RPCResponseHandler;
import org.volity.jabber.packet.RPCRequest;

/**
 * A class for receiving game authorization and verification messages
 * (verify_game(), notify_game_record(), game_player_reauthorized()) from the
 * bookkeeper.
 */
public class VerificationManager implements PacketFilter, RPCHandler {
    RPCResponder mResponder;
    Bookkeeper mBookkeeper;

    List mListeners = new ArrayList();

    public VerificationManager(XMPPConnection connection,
        Bookkeeper bookkeeper) {
        mResponder = new RPCResponder(connection, this, this);
        mBookkeeper = bookkeeper;
    }

    /** Start listening for invitations. */
    public void start() {
        mResponder.start();
    }

    /** Stop listening for invitations. */
    public void stop() {
        mResponder.stop();
    }

    /** Interface to be notified of verification calls. */
    public interface Listener {
        public void verifyGame(String refereeJID, boolean hasFee, int authFee);
        public void notifyGameRecord(String recID);
        public void gamePlayerReauthorized(String player, Map values);
    }

    /**
     * Add an RPC listener.
     *
     * Note: the listener is notified on a Smack listener thread! Do not do UI
     * work in your Listener methods.
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /** Remove a Listener. */
    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    // Implements PacketFilter interface.
    public boolean accept(Packet packet) {
        // Only the bookkeeper can send these RPCs.
        if (mBookkeeper == null
            || (!mBookkeeper.getJID().equals(packet.getFrom())))
            return false;

        String name = ((RPCRequest) packet).getMethodName();

        if ("volity.notify_game_record".equals(name)
            || "volity.verify_game".equals(name)
            || "volity.game_player_reauthorized".equals(name))
            return true;

        return false;
    }

    // Implements RPCHandler interface.
    public void handleRPC(String methodName, List params,
        RPCResponseHandler callback) {

        if ("volity.notify_game_record".equals(methodName))
            notifyGameRecord(params, callback);
        if ("volity.verify_game".equals(methodName))
            verifyGame(params, callback);
        if ("volity.game_player_reauthorized".equals(methodName))
            gamePlayerReauthorized(params, callback);
    }

    public void notifyGameRecord(List params, RPCResponseHandler callback) {
        if (params.size() < 1) {
            callback.respondFault(604,
                "notify_game_record: missing argument 1");
            return;
        }
        Object arg = params.get(0);
        if (!(arg instanceof String)) {
            callback.respondFault(605,
                "notify_game_record: argument 1 must be string");
            return;
        }
        String recID = (String)arg;

        callback.respondValue(Boolean.TRUE);

        for (Iterator it = mListeners.iterator(); it.hasNext(); ) {
            Listener listener = (Listener)it.next();
            listener.notifyGameRecord(recID);
        }
    }

    public void verifyGame(List params, RPCResponseHandler callback) {
        if (params.size() < 1) {
            callback.respondFault(604,
                "verify_game: missing argument 1");
            return;
        }
        Object arg = params.get(0);
        if (!(arg instanceof String)) {
            callback.respondFault(605, 
                "verify_game: argument 1 must be string");
            return;
        }
        String refID = (String)arg;

        boolean hasFee = false;
        int authFee = 0;

        if (params.size() >= 2) {
            arg = params.get(1);
            if (!(arg instanceof Integer)) {
                callback.respondFault(605,
                    "verify_game: argument 2 must be integer");
                return;
            }
            
            hasFee = true;
            authFee = ((Integer)arg).intValue();
        }

        callback.respondValue(Boolean.TRUE);

        for (Iterator it = mListeners.iterator(); it.hasNext(); ) {
            Listener listener = (Listener)it.next();
            listener.verifyGame(refID, hasFee, authFee);
        }
    }

    public void gamePlayerReauthorized(List params,
        RPCResponseHandler callback) {

        if (params.size() < 1) {
            callback.respondFault(604,
                "game_player_reauthorized: missing argument 1");
            return;
        }
        Object arg = params.get(0);
        if (!(arg instanceof String)) {
            callback.respondFault(605,
                "game_player_reauthorized: argument 1 must be string");
            return;
        }
        String playerID = (String)arg;

        if (params.size() < 2) {
            callback.respondFault(604, 
                "game_player_reauthorized: missing argument 2");
            return;
        }
        arg = params.get(1);
        if (!(arg instanceof Map)) {
            callback.respondFault(605,
                "game_player_reauthorized: argument 2 must be struct");
            return;
        }
        Map authMap = (Map)arg;

        callback.respondValue(Boolean.TRUE);

        for (Iterator it = mListeners.iterator(); it.hasNext(); ) {
            Listener listener = (Listener)it.next();
            listener.gamePlayerReauthorized(playerID, authMap);
        }
    }
}
