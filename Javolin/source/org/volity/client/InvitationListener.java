package org.volity.client;

/**
 * A listener for game table invitation events.
 */
public interface InvitationListener {
  /**
   * Called when someone invites the user to join a game table.
   */
  public void invitationReceived(Invitation invitation);
}
