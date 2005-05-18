package org.volity.client;

import org.jivesoftware.smackx.packet.MUCUser;

/**
 * A listener for player status change events.
 * by Jason McIntosh <jmac@jmac.org>
 */
public interface StatusListener {
  /**
   * Called when one of the players at the table has changed its status.
   * @param user The MUCUser object whose status changed.
   * @param status An integer representing the new status of this user.
   *               0 - Standing
   *               1 - Unready
   *               2 - Ready
   */
    //  public void playerStatusChange(MUCUser user, int status);
  public void playerStatusChange(String jid, int status);
}
