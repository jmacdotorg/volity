package org.volity.jabber.packet;

import org.jivesoftware.smack.packet.IQ;
import java.util.List;

/**
 * A remote procedure call request packet conforming to JEP-0009 (Jabber-RPC).
 */
public class RPCRequest extends RPC {
  public RPCRequest(String methodName, List params) {
    this.methodName = methodName;
    this.params = params;
    setType(IQ.Type.SET);
  }

  protected String methodName;
  public String getMethodName() { return methodName; }
  public String getMethodNameXML() {
    return "<methodName>" + methodName + "</methodName>";
  }

  protected List params;
  public List getParams() { return params; }
  public String getParamsXML() { return getParamsXML(params); }

  // Inherited from RPC.
  public String getPayloadXML() {
    return ("<methodCall>" +
	    getMethodNameXML() +
	    getParamsXML() +
	    "</methodCall>");
  }
}
