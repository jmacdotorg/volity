package org.volity.client;

import java.net.*;
import java.util.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;

/** A connection to a Volity bookkeeper. */
public class Bookkeeper {
  /**
   * @param connection an authenticated connection to an XMPP server
   * @param jid the JID of the bookkeeper
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public Bookkeeper(XMPPConnection connection, String jid) {
    this.connection = connection;
    this.jid = jid;
  }

  /**
   * Connect to the standard Volity bookkeeper,
   * <code>bookkeeper@volity.net/volity<code>.
   * @param connection an authenticated connection to an XMPP server
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public Bookkeeper(XMPPConnection connection) {
    this(connection, "bookkeeper@volity.net/volity");
  }

  protected XMPPConnection connection;
  protected String jid;

  /**
   * Get the service discovery manager for this connection.
   */
  protected ServiceDiscoveryManager getDiscoManager() {
    return ServiceDiscoveryManager.getInstanceFor(connection);
  }

  /**
   * Ask the bookkeeper what rulesets are available.
   * @return a list of URIs
   * @throws XMPPException if an XMPP error occurs
   */
  public List getRulesets() throws XMPPException {
    DiscoverItems items =
      getDiscoManager().discoverItems(jid, "rulesets");
    List rulesets = new ArrayList();
    for (Iterator it = items.getItems(); it.hasNext();) {
      DiscoverItems.Item item = (DiscoverItems.Item) it.next();
      rulesets.add(URI.create((String) item.getNode()));
    }
    return rulesets;
  }

  /**
   * Ask the bookkeeper what game servers are available for a
   * particular ruleset.
   * @param ruleset the URI of a ruleset known by the bookkeeper
   * @return a list of strings, game server JIDs for the given ruleset
   * @throws XMPPException if an XMPP error occurs
   */
  public List getGameServers(URI ruleset) throws XMPPException {
    DiscoverItems items =
      getDiscoManager().discoverItems(jid, ruleset + "|servers");
    List servers = new ArrayList();
    for (Iterator it = items.getItems(); it.hasNext();) {
      DiscoverItems.Item item = (DiscoverItems.Item) it.next();
      servers.add(item.getEntityID());
    }
    return servers;
  }

  /**
   * Ask the bookkeeper what game UIs are available for a particular
   * ruleset.
   * @param ruleset the URI of a ruleset known by the bookkeeper
   * @return a list of GameUIInfo objects
   * @throws XMPPException if an XMPP error occurs
   */
  public List getGameUIs(URI ruleset) throws XMPPException {
    ServiceDiscoveryManager discoMan = getDiscoManager();
    DiscoverItems items = discoMan.discoverItems(jid, ruleset + "|uis");
    List gameUIs = new ArrayList();
    for (Iterator it = items.getItems(); it.hasNext();) {
      DiscoverItems.Item item = (DiscoverItems.Item) it.next();
      try {
	URL location = new URL(item.getNode());
	String name = item.getName();
	DiscoverInfo info = discoMan.discoverInfo(jid, location.toString());
	Form form = Form.getFormFrom(info);
	gameUIs.add(new GameUIInfo(name, location, form));
      } catch (MalformedURLException e) { }
    }
    return gameUIs;
  }

  /**
   * Ask the bookkeeper what game UIs are available for a particular
   * ruleset that are compatible with a particular client type.
   * @param ruleset the URI of a ruleset known by the bookkeeper
   * @param clientType the URI of a client type
   * @return a list of GameUIInfo objects
   * @throws XMPPException if an XMPP error occurs
   */
  public List getCompatibleGameUIs(URI ruleset, URI clientType)
    throws XMPPException
  {
    List gameUIs = getGameUIs(ruleset);
    List compatibleGameUIs = new ArrayList();
    for (Iterator it = gameUIs.iterator(); it.hasNext();) {
      GameUIInfo gameUI = (GameUIInfo) it.next();
      if (gameUI.getClientTypes().contains(clientType))
	compatibleGameUIs.add(gameUI);
    }
    return compatibleGameUIs;
  }
}
