package org.volity.jabber;

import java.util.*;

/**
 * A class for dispatching Jabber-RPC requests to named handlers.
 *
 * @author Doug Orleans (dougo@place.org)
 */
public class RPCDispatcher implements RPCHandler {
  public RPCDispatcher() {
  }

  /**
   * @param globalHandler the handler for RPC requests whose method
   *                      names have no prefix
   */
  public RPCDispatcher(RPCHandler globalHandler) {
    setGlobalHandler(globalHandler);
  }

  Map handlers = new HashMap();

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

  RPCHandler globalHandler;

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

  // Inherited from RPCHandler.
  public Object handleRPC(String methodName, List params)
    throws RPCException
  {
    int i = methodName.indexOf(".");
    if (i < 0)
      if (globalHandler == null)
	throw noSuchMethodException(methodName);
      else
	return globalHandler.handleRPC(methodName, params);
    String handlerName = methodName.substring(0, i);
    methodName = methodName.substring(i+1);
    RPCHandler handler = getHandler(handlerName);
    if (handler == null)
      throw noSuchHandlerException(handlerName, methodName);
    return handler.handleRPC(methodName, params);
  }

  /**
   * An exception to be thrown when there is no global handler.
   */
  public RPCException noSuchMethodException(String methodName) {
    return new RPCException(404, "No such method: " + methodName);
  }

  /**
   * An exception to be thrown when there is no named handler.
   */
  public RPCException noSuchHandlerException(String handlerName,
					     String methodName)
  {
    return new RPCException(404, "No such method: " +
			    handlerName + "." + methodName);
  }
}
