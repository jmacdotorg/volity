package org.volity.client;

import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPConnection;

/** A game table (a Multi-User Chat room for playing a Volity game). */
public class GameTable extends GroupChat {
  /**
   * @param connection an authenticated connection to an XMPP server.
   * @param room the JID of the game table.
   */
  public GameTable(XMPPConnection connection, String room) {
    super(connection, room);
    this.connection = connection;
  }

  XMPPConnection connection;
  public XMPPConnection getConnection() { return connection; }

  Referee referee;

  /** The referee sitting at this table. */
  public Referee getReferee() {
    // FIXME: should wait for ref to actually connect
    if (referee == null) referee = new Referee(this);
    return referee;
  }

  /** The nickname of the game referee sitting at this table. */
  public String getRefereeName() {
    // FIXME: should look for MUC owner or moderator role
    return "volity";
  }

  /** The JID of the game referee sitting at this table. */
  public String getRefereeJID() {
    return getRoom() + "/" + getRefereeName();
  }
}
