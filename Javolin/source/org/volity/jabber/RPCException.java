package org.volity.jabber;

import org.volity.jabber.packet.RPCFault;

/**
 * An exception thrown when a remote procedure call causes a fault.
 */
public class RPCException extends Exception {
  public RPCException(RPCFault fault) {
    super(fault.toString());
    this.fault = fault;
  }
  public RPCException(int faultCode, String faultString) {
    this(new RPCFault(faultCode, faultString));
  }

  protected RPCFault fault;
  public RPCFault getFault() { return fault; }
  public int getFaultCode() { return fault.getCode(); }
  public String getFaultString() { return fault.getString(); }
}
