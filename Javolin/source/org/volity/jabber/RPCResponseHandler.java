package org.volity.jabber;

/**
 * An interface for sending Jabber-RPC responses.
 *
 * @author Doug Orleans (dougo@place.org)
 */
public interface RPCResponseHandler {
  /**
   * Respond to a remote procedure call with a return value.
   */
  public void respondValue(Object value);

  /**
   * Respond to a remote procedure call with a fault.
   */
  public void respondFault(int faultCode, String faultString);
}
