package org.volity.client;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.mozilla.javascript.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.packet.MUCUser;
import org.volity.jabber.*;

/**
 * A game user interface.
 */
public class GameUI extends RPCResponder implements RPCHandler {
  /**
   * @param connection an authenticated connection to an XMPP server
   * @param errorHandler a handler for UI script and RPC errors
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public GameUI(XMPPConnection connection, ErrorHandler errorHandler) {
    super(connection, new RPCDispatcher());
    this.errorHandler = errorHandler;
    initStandardObjects();
    start();
  }

  ErrorHandler errorHandler;
  Scriptable scope, game, info, client;
  GameTable table;
  RPCWrapFactory rpcWrapFactory = new RPCWrapFactory();

  public interface ErrorHandler {
    /**
     * Report a command error.
     */
    public abstract void error(Exception e);
  }

  protected void initStandardObjects() {
    try {
      Context context = Context.enter();
      scope = context.initStandardObjects();
      scope.put("game", scope, game = context.newObject(scope));
      scope.put("info", scope, info = new Info());
      scope.put("client", scope, client = context.newObject(scope));
      scope.put("rpc", scope, new Callback() {
	  public Object run(Object[] args) {
	    try {
	      List params = Arrays.asList(args).subList(1, args.length);
	      return table.getReferee().invoke("game." + args[0], params);
	    } catch (Exception e) {
	      errorHandler.error(e);
	      return null;
	    }
	  }
	});
      RPCDispatcher dispatcher = (RPCDispatcher) getHandler();
      dispatcher.setHandler("game", this);
    } catch (JavaScriptException e) {
      errorHandler.error(e);
    } finally {
      Context.exit();
    }
  }

  class Info extends ScriptableObject {
    public String getClassName() { return "Info"; }
    {
      try {
	defineProperty("nickname", Info.class, PERMANENT);
	defineProperty("opponents", Info.class, PERMANENT);
      } catch (PropertyException e) {
	throw new RuntimeException(e.toString());
      }
    }
    public String getNickname() { return table.getNickname(); }
    public void setNickname(String nickname) throws XMPPException {
      table.changeNickname(nickname);
    }
    Scriptable opponents;
    public Object getOpponents() throws JavaScriptException {
      if (opponents == null) {
	Context context = Context.getCurrentContext();
	opponents = context.newObject(scope);
	for (Iterator it = table.getOpponents().iterator(); it.hasNext();) {
	  String nickname = (String) it.next();
	  opponents.put(nickname, opponents, context.newObject(scope));
	}
      }
      return opponents;
    }
  }

  /**
   * @param table the table where the game will be played
   */
  public void setTable(GameTable table) {
    this.table = table;
  }
  
  /**
   * Execute a UI script.
   * @param uiScript an ECMAScript file implementing the game UI
   * @throws IOException if there is an I/O problem reading the UI script
   * @throws JavaScriptException if the UI script throws an exception
   * @throws EvaluationException if an error occurs while evaluating
   *                             the UI script
   */
  public void load(File uiScript) throws IOException, JavaScriptException {
    try {
      Context.enter().evaluateReader(scope, new FileReader(uiScript),
				     uiScript.getName(), 1, null);
    } finally {
      Context.exit();
    }
  }

  // Inherited from RPCHandler.
  public Object handleRPC(String methodName, List params)
    throws RPCException
  {
    Object method = game.get(methodName, scope);
    if (method instanceof Function)
      try {
	Context context = Context.enter();
	context.setWrapFactory(rpcWrapFactory);
	Object ret = ((Function) method).call(context, scope, game,
					      params.toArray());
	if (ret instanceof Undefined) {
	  // function returned void, but RPC result has to be non-void
	  ret = Boolean.TRUE;
	}
	return ret;
      } catch (JavaScriptException e) {
	errorHandler.error(e);
	// FIXME: Volity protocol should probably define these
	// error codes.
	throw new RPCException(901, "UI script exception: " + e);
      } catch (EvaluatorException e) {
	errorHandler.error(e);
	throw new RPCException(902, "UI script error: " + e);
      } finally {
	Context.exit();
      }
    else
      throw new RPCException(903, "No such UI function.");
  }

  /**
   * A factory for wrapping RPC data types into JavaScript objects.
   * In particular, RPC arrays and structs are List objects and Map
   * objects, respectively, and this turns them both into Scriptables.
   */
  class RPCWrapFactory extends WrapFactory {
    public Object wrap(Context cs, Scriptable scope,
		       Object obj, Class staticType)
    {
      if (obj != null && obj instanceof List) {
	final List list = (List) obj;
	return new Scriptable() {
	    public boolean has(int index, Scriptable start) {
	      return index >= 0 && index < list.size();
	    }
	    public Object get(int index, Scriptable start) {
	      return list.get(index);
	    }
	    public void put(int index, Scriptable start, Object value) {
	      list.set(index, value);
	    }
	    public void delete(int index) {
	      list.remove(index);
	    }
	    public Object[] getIds() {
	      Object[] ids = new Object[list.size()];
	      for (int i = 0; i < ids.length; i++)
		ids[i] = new Integer(i);
	      return ids;
	    }

	    // Not really sure what to do with all these...
	    public String getClassName() {
	      return list.getClass().getName();
	    }
	    public Object getDefaultValue(Class hint) {
	      if (hint == null || hint == String.class)
		return list.toString();
	      if (hint == Boolean.class)
		return Boolean.TRUE;
	      if (hint == Number.class)
		return new Double(Double.NaN);
	      return this;
	    }
	    public boolean hasInstance(Scriptable instance) {
	      return false;
	    }
	    public boolean has(String name, Scriptable start) {
	      return false;
	    }
	    public Object get(String name, Scriptable start) {
	      return NOT_FOUND;
	    }
	    public void put(String name, Scriptable start, Object value) {
	      // ignore
	    }
	    public void delete(String name) {
	      // ignore
	    }
	    public Scriptable getParentScope() {
	      return null;
	    }
	    public void setParentScope(Scriptable parent) {
	      // ignore
	    }
	    public Scriptable getPrototype() {
	      return null;
	    }
	    public void setPrototype(Scriptable prototype) {
	      // ignore;
	    }
	  };
      } else if (obj != null && obj instanceof Map) {
	final Map map = (Map) obj;
	return new Scriptable() {
	    public boolean has(String name, Scriptable start) {
	      return map.containsKey(name);
	    }
	    public Object get(String name, Scriptable start) {
	      return map.get(name);
	    }
	    public void put(String name, Scriptable start, Object value) {
	      map.put(name, value);
	    }
	    public void delete(String name) {
	      map.remove(name);
	    }

	    public Object[] getIds() {
	      return map.keySet().toArray();
	    }

	    // Not really sure what to do with all these...
	    public String getClassName() {
	      return map.getClass().getName();
	    }
	    public Object getDefaultValue(Class hint) {
	      if (hint == null || hint == String.class)
		return map.toString();
	      if (hint == Boolean.class)
		return Boolean.TRUE;
	      if (hint == Number.class)
		return new Double(Double.NaN);
	      return this;
	    }
	    public boolean hasInstance(Scriptable instance) {
	      return false;
	    }
	    public boolean has(int index, Scriptable start) {
	      return false;
	    }
	    public Object get(int index, Scriptable start) {
	      return NOT_FOUND;
	    }
	    public void put(int index, Scriptable start, Object value) {
	      // ignore
	    }
	    public void delete(int index) {
	      // ignore
	    }
	    public Scriptable getParentScope() {
	      return null;
	    }
	    public void setParentScope(Scriptable parent) {
	      // ignore
	    }
	    public Scriptable getPrototype() {
	      return null;
	    }
	    public void setPrototype(Scriptable prototype) {
	      // ignore;
	    }
	  };
      } else {
	return super.wrap(cs, scope, obj, staticType);
      }
    }
  }

  /** A simple way to define a function object without using reflection. */
  abstract class Callback extends ScriptableObject implements Function {
    // Inherited from ScriptableObject.
    public String getClassName() { return "Function"; }
    // Inherited from Function.
    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
		       Object[] args) {
      return run(args);
    }
    // Inherited from Function.
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
      throw new RuntimeException("Not a constructor.");
    }
    /** Run the callback. */
    public abstract Object run(Object[] args);
  }
}
