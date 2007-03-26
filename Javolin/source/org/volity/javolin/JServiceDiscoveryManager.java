package org.volity.javolin;

import java.util.Iterator;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;

/**
 * Javolin customization of Smack's ServiceDiscoveryManager.
 *
 * This exists in order to increase the default timeout on disco queries.
 * Smack's default timeout for everything is five seconds. Maybe we should
 * crank that up in general, but we'd specifically like to crank it up for
 * disco queries.
 */
public class JServiceDiscoveryManager extends ServiceDiscoveryManager
{
    protected static final int QUERY_TIMEOUT = 30;   // seconds

    protected static final String CAPSVERSION_NODE = 
        CapPresenceFactory.VOLITY_NODE_URI+"#"+CapPresenceFactory.VOLITY_VERSION;
    protected static final String CAPSVERSION_ROLE = 
        CapPresenceFactory.VOLITY_NODE_URI+"#"+CapPresenceFactory.VOLITY_ROLE_PLAYER;

    public JServiceDiscoveryManager(XMPPConnection connection) {
        super(connection);
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID and
     * note attribute. Use this message only when trying to query information which is not 
     * directly addressable.
     *
     * This is an exact copy of the method in ServiceDiscoveryManager, except
     * the timeout is longer.
     * 
     * @param entityID the address of the XMPP entity.
     * @param node the attribute that supplements the 'jid' attribute.
     * @return the discovered information.
     * @throws XMPPException if the operation failed for some reason.
     */
    public DiscoverInfo discoverInfo(String entityID, String node) throws XMPPException {
        // Discover the entity's info
        DiscoverInfo disco = new DiscoverInfo();
        disco.setType(IQ.Type.GET);
        disco.setTo(entityID);
        disco.setNode(node);

        // Create a packet collector to listen for a response.
        PacketCollector collector =
            connection.createPacketCollector(new PacketIDFilter(disco.getPacketID()));

        connection.sendPacket(disco);

        // Wait up to 30 seconds for a result.
        IQ result = (IQ) collector.nextResult(QUERY_TIMEOUT*1000);
        // Stop queuing results
        collector.cancel();
        if (result == null) {
            throw new XMPPException("No response from the server.");
        }
        if (result.getType() == IQ.Type.ERROR) {
            throw new XMPPException(result.getError());
        }
        return (DiscoverInfo) result;
    }

    /**
     * Returns the discovered items of a given XMPP entity addressed by its JID and
     * note attribute. Use this message only when trying to query information which is not 
     * directly addressable.
     * 
     * This is an exact copy of the method in ServiceDiscoveryManager, except
     * the timeout is longer.
     * 
     * @param entityID the address of the XMPP entity.
     * @param node the attribute that supplements the 'jid' attribute.
     * @return the discovered items.
     * @throws XMPPException if the operation failed for some reason.
     */
    public DiscoverItems discoverItems(String entityID, String node) throws XMPPException {
        // Discover the entity's items
        DiscoverItems disco = new DiscoverItems();
        disco.setType(IQ.Type.GET);
        disco.setTo(entityID);
        disco.setNode(node);

        // Create a packet collector to listen for a response.
        PacketCollector collector =
            connection.createPacketCollector(new PacketIDFilter(disco.getPacketID()));

        connection.sendPacket(disco);

        // Wait up to 30 seconds for a result.
        IQ result = (IQ) collector.nextResult(QUERY_TIMEOUT*1000);
        // Stop queuing results
        collector.cancel();
        if (result == null) {
            throw new XMPPException("No response from the server.");
        }
        if (result.getType() == IQ.Type.ERROR) {
            throw new XMPPException(result.getError());
        }
        return (DiscoverItems) result;
    }

    /**
     * Initializes the packet listeners of the connection that will answer to
     * any service discovery request.
     *
     * (Overrides ServiceDiscoveryManager method to do the same thing, but
     * better. We need to be able to respond to disco#info queries with nodes.)
     */
    protected void initPacketListener() {
        // Listen for disco#items requests and answer with an empty result
        PacketFilter packetFilter = new PacketTypeFilter(DiscoverItems.class);
        PacketListener packetListener = new PacketListener() {
            public void processPacket(Packet packet) {
                DiscoverItems discoverItems = (DiscoverItems) packet;
                // Send back the items defined in the client if the request is of type GET
                if (discoverItems != null && discoverItems.getType() == IQ.Type.GET) {
                    DiscoverItems response = new DiscoverItems();
                    response.setType(IQ.Type.RESULT);
                    response.setTo(discoverItems.getFrom());
                    response.setPacketID(discoverItems.getPacketID());

                    // Add the defined items related to the requested node. Look for 
                    // the NodeInformationProvider associated with the requested node.  
                    if (getNodeInformationProvider(discoverItems.getNode()) != null) {
                        Iterator items =
                            getNodeInformationProvider(discoverItems.getNode()).getNodeItems();
                        while (items.hasNext()) {
                            response.addItem((DiscoverItems.Item) items.next());
                        }
                    }
                    connection.sendPacket(response);
                }
            }
        };
        connection.addPacketListener(packetListener, packetFilter);

        // Listen for disco#info requests and answer the client's supported features 
        // To add a new feature as supported use the #addFeature message        
        packetFilter = new PacketTypeFilter(DiscoverInfo.class);
        packetListener = new PacketListener() {
            public void processPacket(Packet packet) {
                DiscoverInfo discoverInfo = (DiscoverInfo) packet;
                // Answer the client's supported features if the request is of the GET type
                if (discoverInfo != null && discoverInfo.getType() == IQ.Type.GET) {
                    DiscoverInfo response = new DiscoverInfo();
                    response.setType(IQ.Type.RESULT);
                    response.setTo(discoverInfo.getFrom());
                    response.setPacketID(discoverInfo.getPacketID());

                    /* Add the client's identity and features if "node" is
                     * null, or if it's the caps#1.0 node.
                     */
                    String discoNode = discoverInfo.getNode();

                    if (discoNode == null || discoNode.equals(CAPSVERSION_NODE)) {
                        // Set this client identity
                        DiscoverInfo.Identity identity = new DiscoverInfo.Identity("client",
                                getIdentityName());
                        identity.setType(getIdentityType());
                        response.addIdentity(identity);
                        // Add the registered features to the response
                        synchronized (features) {
                            for (Iterator it = getFeatures(); it.hasNext();) {
                                response.addFeature((String) it.next());
                            }
                        }
                        // Add the form, if any
                        if (identityExtForm != null) {
                            response.addExtension(identityExtForm.getDataFormToSend());
                        }
                    }
                    else if (discoNode.equals(CAPSVERSION_ROLE)) {
                        response.addFeature(CAPSVERSION_ROLE);
                    }
                    else {
                        // Return an <item-not-found/> error since a client doesn't have nodes
                        response.setNode(discoverInfo.getNode());
                        System.out.println("### disco node not found " + discoverInfo.getNode() + " (from " + discoverInfo.getFrom() + ")");
                        response.setType(IQ.Type.ERROR);
                        response.setError(new XMPPError(404, "item-not-found"));
                        System.out.println("### query: " + discoverInfo.toXML() + "\n### reply: " + response.toXML());
                    }
                    connection.sendPacket(response);
                }
            }
        };
        connection.addPacketListener(packetListener, packetFilter);
    }
}
