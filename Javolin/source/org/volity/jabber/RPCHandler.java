package org.volity.jabber;

import java.util.List;

/**
 * An interface for handling Jabber-RPC requests.
 *
 * @author Doug Orleans (dougo@place.org)
 */
public interface RPCHandler {
  /**
   * Handle a remote procedure call.
   * @param methodName the name of the called procedure
   * @param params the list of parameter values
   * @return the resulting value of the call
   * @throws RPCException if a fault occurs
   */
  public Object handleRPC(String methodName, List params)
    throws RPCException;
}
