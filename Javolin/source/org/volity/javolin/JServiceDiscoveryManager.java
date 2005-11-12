package org.volity.javolin;

import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.IQ;
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

}
