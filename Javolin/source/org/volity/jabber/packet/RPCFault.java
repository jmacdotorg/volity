package org.volity.jabber.packet;

import java.util.*;

/**
 * A remote procedure call fault response packet conforming to
 * JEP-0009 (Jabber-RPC).
 */
public class RPCFault extends RPCResponse {
  public RPCFault(int code, String string) {
    this.code = code;
    this.string = string;
  }

  protected int code;
  public int getCode() { return code; }

  protected String string;
  public String getString() { return string; }

  // Inherited from RPCResponse.
  public String getResponseXML() {
    Map struct = new LinkedHashMap(2);
    struct.put("faultCode", new Integer(code));
    struct.put("faultString", string);
    return "<fault>" + getValueXML(struct) + "</fault>";
  }

  public String toString() {
    return "RPC fault " + code + ": " + string;
  }
}
