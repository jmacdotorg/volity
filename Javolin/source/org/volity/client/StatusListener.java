package org.volity.client;

/**
 * A listener for player status change events.
 *
 * @author Jason McIntosh <jmac@jmac.org>
 */
public interface StatusListener 
{
    public void stateChanged(int newstate);

    public void seatListKnown();
    public void requiredSeatsChanged();

    public void playerJoined(Player player);
    public void playerLeft(Player player);
    public void playerNickChanged(Player player, String oldNick);
    public void playerIsReferee(Player player);

    public void playerSeatChanged(Player player, Seat oldseat, Seat newseat);
    public void playerReady(Player player, boolean flag);
}
