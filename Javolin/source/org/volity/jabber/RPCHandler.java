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
   * @param responseHandler a handler for sending a return value or
   *                        fault response
   */
  public void handleRPC(String methodName, List params,
			RPCResponseHandler responseHandler);
}
