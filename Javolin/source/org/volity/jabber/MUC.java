package org.volity.jabber;

import java.util.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.ProviderManager;
import org.volity.jabber.packet.*;
import org.volity.jabber.provider.MUCUserProvider;

/**
 * A Multi-User Chat room using the JEP-0045 protocol.
 *
 * @author Doug Orleans (dougo@place.org)
 */
public class MUC extends GroupChat {
  static {
    // Register the parser for muc#user packet extensions.
    ProviderManager.addExtensionProvider(MUCUser.elementName,
					 MUCUser.namespace,
					 new MUCUserProvider());
  }

  /**
   * Creates a new MUC.
   * @param connection an authenticated XMPP connection
   * @param jid the JID of the MUC
   */
  public MUC(XMPPConnection connection, String jid) {
    super(connection, jid);
    addParticipantListener(new PacketListener() {
	public void processPacket(Packet packet) {
	  Presence presence = (Presence) packet;
	  MUCUser user = MUCUser.getUserInfo(presence);
	  if (user != null) {
	    if (presence.getType() == Presence.Type.AVAILABLE) {
	      userMap.put(presence.getFrom(), user);
	    } else if (presence.getType() == Presence.Type.UNAVAILABLE) {
	      userMap.remove(presence.getFrom());
	    }
	  }
	}
      });
  }

  Map userMap = new HashMap();

  /**
   * MUC-specific user info for a participant, such as role and affiliation.
   * @param jid the room JID of the participant (i.e. a
   *            string contained in {@link #getParticipants()})
   */
  public MUCUser getUserInfo(String jid) {
    return (MUCUser) userMap.get(jid);
  }

  /**
   * The room JID of the owner of the MUC, or null if the owner is not
   * connected.
   */
  public String getOwner() {
    for (Iterator it = userMap.entrySet().iterator(); it.hasNext();) {
      Map.Entry entry = (Map.Entry) it.next();
      MUCUser user = (MUCUser) entry.getValue();
      if (user.getAffiliation() == MUCUser.Affiliation.OWNER)
	return (String) entry.getKey();
    }
    return null;
  }
}
