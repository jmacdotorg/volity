package org.volity.client;

import java.util.Iterator;
import java.net.URI;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.volity.jabber.RPCRequester;
import org.volity.jabber.RPCException;

// WORKAROUND -- REMOVE THESE WHEN SMACK SUPPORTS JEP-0128
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.xmlpull.v1.XmlPullParser;

/** A Jabber-RPC connection to a Volity game server. */
public class GameServer extends RPCRequester {
  /**
   * @param connection an authenticated connection to an XMPP server
   * @param JID the JID of the game server
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public GameServer(XMPPConnection connection, String JID) {
    super(connection, JID);
  }

  /**
   * Ask what ruleset the game server implements.
   * @throws XMPPException if an XMPP error occurs or the game server
   *                       doesn't reply properly.
   */
  public URI getRuleset() throws XMPPException {
    ServiceDiscoveryManager discoMan = 
      ServiceDiscoveryManager.getInstanceFor(connection);
    DiscoverInfo info = discoMan.discoverInfo(responderJID);
    Form form = Form.getFormFrom(info);
    if (form != null) {
      FormField field = form.getField("ruleset");
      if (field != null)
	return URI.create((String) field.getValues().next());
    }
    // FIXME: should be a Volity exception, with a better message
    throw new XMPPException("No ruleset field found in disco form.");
  }

  /**
   * Create a new instance (table) of the game (a Multi-User Chat room).
   * @return the new MUC, which should immediately be joined
   * @throws XMPPException if an XMPP error occurs
   * @throws RPCException if an RPC fault occurs
   */
  public GameTable newTable() throws XMPPException, RPCException {
    return new GameTable(connection, (String) invoke("volity.new_table"));
  }

  // WORKAROUND -- REMOVE EVERYTHING BELOW WHEN SMACK SUPPORTS JEP-0128

  static {
    ProviderManager.
      addIQProvider("query",
		    "http://jabber.org/protocol/disco#info",
		    new DiscoverInfoProvider());
  }

  private static class DiscoverInfoProvider implements IQProvider {
    public IQ parseIQ(XmlPullParser parser) throws Exception {
      DiscoverInfo discoverInfo = new DiscoverInfo();
      boolean done = false;
      DiscoverInfo.Feature feature = null;
      DiscoverInfo.Identity identity = null;
      String category = "";
      String name = "";
      String type = "";
      String variable = "";
      discoverInfo.setNode(parser.getAttributeValue("", "node"));
      while (!done) {
	int eventType = parser.next();
	if (eventType == XmlPullParser.START_TAG) {
	  if (parser.getName().equals("identity")) {
	    // Initialize the variables from the parsed XML
	    category = parser.getAttributeValue("", "category");
	    name = parser.getAttributeValue("", "name");
	    type = parser.getAttributeValue("", "type");
	  } else if (parser.getName().equals("feature")) {
	    // Initialize the variables from the parsed XML
	    variable = parser.getAttributeValue("", "var");
	  } else {
	    // Otherwise, it must be a packet extension.
	    discoverInfo.
	      addExtension(PacketParserUtils.
			   parsePacketExtension(parser.getName(),
						parser.getNamespace(),
						parser));
	  }
	} else if (eventType == XmlPullParser.END_TAG) {
	  if (parser.getName().equals("identity")) {
	    // Create a new identity and add it to the discovered info.
	    identity = new DiscoverInfo.Identity(category, name);
	    identity.setType(type);
	    discoverInfo.addIdentity(identity);
	  }
	  if (parser.getName().equals("feature")) {
	    // Create a new feature and add it to the discovered info.
	    discoverInfo.addFeature(variable);
	  }
	  if (parser.getName().equals("query")) {
	    done = true;
	  }
	}
      }

      return discoverInfo;
    }
  }
}
