package org.volity.client;

/**
 * A default listener for player status change events.
 *
 * This class's callbacks do nothing. The class is provided for you to subclass
 * if you want to override only a few of the StatusListener methods.
 */
public class DefaultStatusListener
    implements StatusListener
{
    public void stateChanged(int newstate) { }

    public void seatListKnown() { }
    public void requiredSeatsChanged() { }

    public void playerJoined(Player player) { }
    public void playerLeft(Player player) { }
    public void playerNickChanged(Player player, String oldNick) { }
    public void playerIsReferee(Player player) { }

    public void playerSeatChanged(Player player, Seat oldseat, Seat newseat) { }
    public void playerReady(Player player, boolean flag) { }
}
