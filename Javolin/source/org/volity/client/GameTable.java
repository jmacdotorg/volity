package org.volity.client;

import org.volity.jabber.MUC;
import org.volity.jabber.packet.MUCUser;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;

/** A game table (a Multi-User Chat room for playing a Volity game). */
public class GameTable extends MUC {
  /**
   * @param connection an authenticated connection to an XMPP server.
   * @param room the JID of the game table.
   */
  public GameTable(XMPPConnection connection, String room) {
    super(connection, room);
    this.connection = connection;
    addParticipantListener(new PacketListener() {
	public void processPacket(Packet packet) {
	  Presence presence = (Presence) packet;
	  MUCUser user = MUCUser.getUserInfo(presence);
	  if (user != null &&
	      user.getAffiliation() == MUCUser.Affiliation.OWNER) {
	    if (presence.getType() == Presence.Type.AVAILABLE) {
	      referee = new Referee(GameTable.this, user.getJID());
	    } else if (presence.getType() == Presence.Type.UNAVAILABLE) {
	      referee = null;
	    }
	  }
	}
      });
  }

  XMPPConnection connection;
  public XMPPConnection getConnection() { return connection; }

  Referee referee;

  /**
   * The referee for this table, or null if no referee is connected.
   */
  public Referee getReferee() {
    return referee;
  }
}
