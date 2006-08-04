package org.volity.jabber;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.provider.ProviderManager;
import org.volity.jabber.packet.*;
import org.volity.jabber.provider.RPCProvider;
import java.util.*;

/**
 * A class for responding to Jabber-RPC requests.
 *
 * To use this, create an RPCService and attach it to an XMPPConnection. Then
 * create RPCResponders and put them onto the RPCService.
 *
 * @author Andrew Plotkin (erkyrath@eblong.com)
 */
public class RPCService implements PacketListener {
    protected static Map services = new HashMap();
    protected static RPCProvider globalProvider;

    /**
     * Find the RPCService attached to a given XMPPConnection. There can be at
     * most one. (If there is none, returns null.)
     */
    public static RPCService getServiceForConnection(XMPPConnection connection) {
        synchronized (services) {
            return (RPCService)(services.get(connection));
        }
    }

    protected XMPPConnection mConnection;
    protected List mResponders = new ArrayList();

    public RPCService(XMPPConnection connection) {

        if (globalProvider == null) {
            // Register the provider so that request packets get parsed
            // correctly.
            globalProvider = new RPCProvider();
            ProviderManager.addIQProvider("query", "jabber:iq:rpc",
                globalProvider);
        }

        if (getServiceForConnection(connection) != null)
            throw new RuntimeException("Connection already has RPCService");

        mConnection = connection;

        // Add to the global table of RPCServices.
        synchronized (services) {
            services.put(mConnection, this);
        }

        // Add a packet listener, using a filter to grab all RPC packets.
        PacketFilter filter = new PacketTypeFilter(RPCRequest.class);
        mConnection.addPacketListener(this, filter);
    }

    /**
     * Stop listening for requests. This permanently decommissions the
     * RPCService.
     */
    public void stop() {
        synchronized (mResponders) {
            mResponders.clear();
        }

        mConnection.removePacketListener(this);

        synchronized (services) {
            services.remove(mConnection);
        }

        mConnection = null;
    }

    /**
     * Add a RPCResponder to the service. 
     */
    void addResponder(RPCResponder responder) {
        synchronized (mResponders) {
            mResponders.add(responder);
        }
    }

    /**
     * Remove a RPCResponder from the service. 
     */
    void removeResponder(RPCResponder responder) {
        synchronized (mResponders) {
            mResponders.remove(responder);
        }
    }

    /**
     * Take appropriate action when no RPCResponder accepts a packet. By
     * default, this generates an RPC fault 609. This is a code used by the
     * Volity spec, which doesn't really belong in a org.volity.jabber class --
     * if you want something else, feel free to override it.
     */
    protected void rejectPacket(Packet packet, RPCResponseHandler callback) {
        callback.respondFault(609, "RPC not handled.");
    }

    // Implements PacketListener interface.
    public void processPacket(Packet packet) {
        final RPCRequest req = (RPCRequest) packet;

        RPCResponseHandler callback = new RPCResponseHandler() {
                public void respondValue(Object value) {
                    respond(new RPCResult(value));
                }
                public void respondFault(int faultCode, String faultString) {
                    respond(new RPCFault(faultCode, faultString));
                }
                private void respond(RPCResponse resp) {
                    resp.setTo(req.getFrom());
                    String id = req.getPacketID();
                    if (id != null)
                        resp.setPacketID(id);
                    mConnection.sendPacket(resp);
                }
            };

        /* See which responder wants this packet. Note that, unlike Smack's
         * packet-response system, we run our filter in the listening
         * thread.
         */

        RPCResponder responder = null;

        synchronized (mResponders) {
            for (int ix=0; ix<mResponders.size(); ix++) {
                RPCResponder resp = (RPCResponder)mResponders.get(ix);
                PacketFilter filter = resp.getFilter();
                if (filter == null || filter.accept(packet)) {
                    responder = resp;
                    break;
                }
            }
        }

        if (responder != null) {
            responder.getHandler().handleRPC(req.getMethodName(), req.getParams(), callback);
            return;
        }

        rejectPacket(packet, callback);
    }
    
}
