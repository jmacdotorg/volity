package org.volity.jabber.packet;

import java.util.Collections;

/**
 * A remote procedure call result packet conforming to JEP-0009
 * (Jabber-RPC).
 */
public class RPCResult extends RPCResponse {
  public RPCResult(Object value) {
    this.value = value;
  }

  protected Object value;
  public Object getValue() { return value; }

  // Inherited from RPCResponse.
  public String getResponseXML() {
    return getParamsXML(Collections.singletonList(value));
  }
}
