package org.volity.jabber.packet;

import org.jivesoftware.smack.packet.IQ;

/**
 * A remote procedure call response packet conforming to JEP-0009
 * (Jabber-RPC).  The response must be either a result or a fault.
 */
public abstract class RPCResponse extends RPC {
  public RPCResponse() {
    setType(IQ.Type.RESULT);
  }

  // Inherited from RPC.
  public String getPayloadXML() {
    return ("<methodResponse>" +
	    getResponseXML() +
	    "</methodResponse>");
  }

  /** XML string representing the response value (result or fault). */
  public abstract String getResponseXML();
}
