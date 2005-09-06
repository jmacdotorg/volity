package org.volity.client;

/**
 * A listener for player status change events.
 *
 * @author Jason McIntosh <jmac@jmac.org>
 */
public interface StatusListener {
    public void seatListKnown();
    public void requiredSeatsChanged();

    public void playerJoined(Player player);
    public void playerLeft(Player player);
    public void playerNickChanged(Player player);

    public void playerStood(Player player);
    public void playerSat(Player player, Seat seat);
    public void playerReady(Player player);
    public void playerUnready(Player player);
}
