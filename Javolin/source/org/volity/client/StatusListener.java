package org.volity.client;

import org.jivesoftware.smackx.packet.MUCUser;

/**
 * A listener for player status change events.
 * by Jason McIntosh <jmac@jmac.org>
 */
public interface StatusListener {
    public void playerBecameReady(String jid);
    public void playerBecameUnready(String jid);
    public void playerStood(String jid);
    public void playerSat(String jid, String seatId);
    public void requiredSeatsChanged();
}
