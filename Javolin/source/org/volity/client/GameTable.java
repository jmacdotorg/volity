package org.volity.client;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.packet.MUCUser;
import java.util.*;

/** A game table (a Multi-User Chat room for playing a Volity game). */
public class GameTable extends MultiUserChat {
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
	  MUCUser user = getUser(presence);
	  if (isReferee(user))
	    if (presence.getType() == Presence.Type.AVAILABLE)
	      referee = new Referee(GameTable.this, user.getItem().getJid());
	    else if (presence.getType() == Presence.Type.UNAVAILABLE)
	      referee = null;
	}
      });
  }

  /**
   * Get the user extension from a Presence packet.
   * @return null if the packet has no user extension.
   */
  public static MUCUser getUser(Presence presence) {
    PacketExtension ext = presence.getExtension("x", userNamespace);
    return ext == null ? null : (MUCUser) ext;
  }

  static final String userNamespace = "http://jabber.org/protocol/muc#user";

  /**
   * Get the user information for a participant.
   * @param participant a fully-qualified MUC JID, e.g. an element of
   *                    getParticipants()
   */
  public MUCUser getUser(String participant) {
    return getUser(getParticipantPresence(participant));
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

  /**
   * Is a user the referee?
   */
  public static boolean isReferee(MUCUser user) {
    return user != null && user.getItem().getAffiliation().equals("owner");
  }

  /**
   * Is a participant the referee?
   * @param participant a fully-qualified MUC JID, e.g. an element of
   *                    getParticipants()
   */
  public boolean isReferee(String participant) {
    return isReferee(getUser(participant));
  }

  /**
   * Get a list of opponent nicknames, i.e. participants not including
   * the referee or myself.
   */
  public List getOpponents() {
    List opponents = new ArrayList();
    for (Iterator it = getParticipants(); it.hasNext();) {
      String participant = (String) it.next();
      if (!isReferee(participant)) {
	String nickname = StringUtils.parseResource(participant);
	if (!getNickname().equals(nickname))
	  opponents.add(nickname);
      }
    }
    return opponents;
  }
}
