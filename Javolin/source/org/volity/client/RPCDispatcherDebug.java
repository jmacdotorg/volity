package org.volity.client;

import java.util.*;
import org.volity.client.GameUI;
import org.volity.jabber.RPCDispatcher;
import org.volity.jabber.RPCResponseHandler;

/**
 * Wrapper for RPCDispatcher, which is able to display debugging info. When an
 * RPC arrives, this prints it to a messageHandler (if desired).
 *
 * Also contains the buildCallString method, which is used by Referee to print
 * the *outgoing* RPCs.
 */
public class RPCDispatcherDebug extends RPCDispatcher {
    protected static boolean debugFlag = false;

    /**
     * Set whether debugging output is active.
     */
    public static void setDebugOutput(boolean flag) {
        debugFlag = flag;
    }

    protected GameUI.MessageHandler messageHandler;

    public RPCDispatcherDebug(GameUI.MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * Customization of the RPCDispatcher.handleRPC method.
     */
    public void handleRPC(String methodName, List params,
        RPCResponseHandler resp) {
        if (debugFlag) {
            messageHandler.print(buildCallString("recv", methodName, params));
        }
        super.handleRPC(methodName, params, resp);
    }

    /**
     * Generate a string which displays an RPC call (method and parameters).
     */
    public static String buildCallString(String prefix, String method, 
        List params) {
        StringBuffer buf = new StringBuffer();

        buf.append(prefix);
        buf.append(": ");
        buf.append(method);

        buf.append("(");
        if (params != null) {
            for (int ix=0; ix<params.size(); ix++) {
                if (ix != 0)
                    buf.append(", ");
                buildParamString(buf, params.get(ix));
            }
        }
        buf.append(")");

        return buf.toString();
    }

    /**
     * Add one RPC parameter to a buffer.
     */        
    protected static void buildParamString(StringBuffer buf, Object obj) {
        if (obj instanceof String) {
            buf.append("\"");
            buf.append((String)obj);
            buf.append("\"");
            return;
        }

        if (obj instanceof Number) {
            buf.append(obj.toString());
            return;
        }

        if (obj instanceof Boolean) {
            buf.append(obj.toString());
            return;
        }

        if (obj instanceof List) {
            List ls = (List)obj;
            for (int ix=0; ix<ls.size(); ix++) {
                if (ix != 0)
                    buf.append(", ");
                buildParamString(buf, ls.get(ix));
            }
            return;
        }

        if (obj instanceof Map) {
            buf.append("{");
            boolean first = true;
            Set entries = ((Map)obj).entrySet();
            for (Iterator it = entries.iterator(); it.hasNext(); ) {
                Map.Entry ent = (Map.Entry)it.next();
                if (first) 
                    first = false;
                else
                    buf.append(", ");
                buf.append(ent.getKey().toString());
                buf.append(": ");
                buildParamString(buf, ent.getValue());
            }
            buf.append("}");
            return;
        }

        buf.append(obj.toString());
        return;
    }
}

