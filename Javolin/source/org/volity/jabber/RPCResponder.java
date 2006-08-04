package org.volity.jabber;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.ProviderManager;
import org.volity.jabber.packet.*;
import org.volity.jabber.provider.*;
import java.util.*;

/**
 * A class for responding to Jabber-RPC requests. This must be added to an
 * RPCService, which is attached to an XMPPConnection.
 *
 * @author Doug Orleans (dougo@place.org)
 */
public class RPCResponder {
    /**
     * @param connection a connection to an XMPP server
     * @param filter a filter specifying which RPC requests to handle
     * @param handler a handler for RPC requests
     *
     * If the filter is null, the responder will apply to all RPC requests.
     */
    public RPCResponder(XMPPConnection connection, 
        PacketFilter filter,
        RPCHandler handler) {

        this.service = RPCService.getServiceForConnection(connection);
        if (this.service == null)
            throw new RuntimeException("Connection has no RPCService");

        this.filter = filter;
        this.handler = handler;
    }

    /**
     * @param service an RPC-receiving service
     * @param filter a filter specifying which RPC requests to handle
     * @param handler a handler for RPC requests
     *
     * If the filter is null, the responder will apply to all RPC requests.
     */
    public RPCResponder(RPCService service, 
        PacketFilter filter,
        RPCHandler handler) {
        this.service = service;
        this.filter = filter;
        this.handler = handler;
    }

    protected RPCService service;

    protected PacketFilter filter;
    public PacketFilter getFilter() { return filter; }

    protected RPCHandler handler;
    public RPCHandler getHandler() { return handler; }

    /**
     * Start listening for requests.
     */
    public void start() {
        service.addResponder(this);
    }

    /**
     * Stop listening for requests.
     */
    public void stop() {
        service.removeResponder(this);
    }
}
