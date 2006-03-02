package org.volity.jabber;

import java.util.*;

/**
 * A class for dispatching Jabber-RPC requests to named handlers.
 *
 * @author Doug Orleans (dougo@place.org)
 */
public class RPCDispatcher implements RPCHandler {
  Map handlers = new HashMap();
  RPCHandler globalHandler = null;

  public RPCDispatcher() {
  }

  /**
   * @param globalHandler the handler for RPC requests whose method
   *                      names have no prefix
   */
  public RPCDispatcher(RPCHandler globalHandler) {
    setGlobalHandler(globalHandler);
  }

  /**
   * @param name the name of a handler
   * @return the named handler, or null if there is none
   */
  public RPCHandler getHandler(String name) {
    return (RPCHandler) handlers.get(name);
  }

  /**
   * Set the handler for RPC requests whose method names start with
   * name followed by a period.  This prefix will be removed and the
   * remaining string will be passed as the method name to the
   * handler's handleRPC method.
   */
  public void setHandler(String name, RPCHandler handler) {
    handlers.put(name, handler);
  }

  /**
   * @return the handler for RPC requests whose method names have no
   *         prefix, or null if there is none
   */
  public RPCHandler getGlobalHandler() {
    return globalHandler;
  }

  /**
   * Set the handler for RPC requests whose method names have no prefix.
   */
  public void setGlobalHandler(RPCHandler handler) {
    globalHandler = handler;
  }

  // Implements RPCHandler interface.
  public void handleRPC(String methodName, List params, RPCResponseHandler k) {
    int i = methodName.indexOf('.');
    if (i < 0) {
      if (globalHandler == null)
        noSuchMethodFault(methodName, k);
      else
        globalHandler.handleRPC(methodName, params, k);
    } else {
      String handlerName = methodName.substring(0, i);
      methodName = methodName.substring(i+1);
      RPCHandler handler = getHandler(handlerName);
      if (handler == null)
        noSuchHandlerFault(handlerName, methodName, k);
      else
        handler.handleRPC(methodName, params, k);
    }
  }

  /**
   * Clear all handlers.
   */
  public void clear() {
    setGlobalHandler(null);
    handlers.clear();    
  }

  /**
   * Send a fault response because there is no global handler.
   */
  public void noSuchMethodFault(String methodName, RPCResponseHandler k) {
    k.respondFault(404, "No such method: " + methodName);
  }

  /**
   * Send a fault response because there is no named handler.
   */
  public void noSuchHandlerFault(String handlerName, String methodName,
                                 RPCResponseHandler k)
  {
    k.respondFault(404, "No such method: " + handlerName + "." + methodName);
  }
}
