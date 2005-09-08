package org.volity.client;

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import org.apache.batik.script.rhino.RhinoInterpreter;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.packet.MUCUser;
import org.mozilla.javascript.*;
import org.volity.client.TranslateToken;
import org.volity.jabber.*;

/**
 * A game user interface.
 */
public class GameUI implements RPCHandler, PacketFilter {
  /**
   * (The arguments here are slightly redundant -- we could implement
   * errorHandler in this class using translator and messageHandler. But then
   * we'd have to catch more exceptions. Maybe we should do it anyway.)
   *
   * @param connection an authenticated connection to an XMPP server
   * @param translator a token translation instance
   * @param messageHandler a handler for UI script and RPC messages
   *        (will display messages in-line -- e.g., in a message pane)
   * @param errorHandler   a handler for UI script and RPC exceptions
   *        (will display problems out-of-band -- e.g., a dialog box)
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public GameUI(XMPPConnection connection, TranslateToken translator,
    MessageHandler messageHandler, ErrorHandler errorHandler) {

    this.translator = translator;
    this.errorHandler = errorHandler;
    this.messageHandler = messageHandler;
    RPCDispatcher dispatcher = new RPCDispatcher();
    dispatcher.setHandler("game", this);
    VolityHandler volityHandler = new VolityHandler(this);
    dispatcher.setHandler("volity", volityHandler);
    responder = new RPCResponder(connection, this, dispatcher);
    responder.start();
  }

  TranslateToken translator;
  ErrorHandler errorHandler;
  MessageHandler messageHandler;
  RPCResponder responder;
  Scriptable scope, game, info, client;
  GameTable table;
  RPCWrapFactory rpcWrapFactory = new RPCWrapFactory();

  public interface ErrorHandler {
    /**
     * Report a command error.
     */
    public abstract void error(Exception e);
  }

  public interface MessageHandler {
    /**
     * Report a status or game-response message.
     */
    public abstract void print(String msg);
  }

  /**
   * Initialize game-handling objects: "game", "info", "client", and "rpc".
   * @return the initialized scope
   */
  public ScriptableObject initGameObjects() {
    return initGameObjects(null);
  }

  /**
   * Initialize game-handling objects: "game", "info", "client", and "rpc".
   * @param scope the scope to initialize, or null, in which case a
   *              new object will be created to serve as the scope.
   * @return the initialized scope, which is the same as the scope
   *         argument if not null.
   */
  public ScriptableObject initGameObjects(ScriptableObject scope) {
    try {
      Context context = Context.enter();
      if (scope == null) scope = context.initStandardObjects();
      this.scope = scope;
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
              /* This will print TokenFailures in window log, and display
               * other exceptions as dialog box */
	      return null;
	    }
	  }
	});
      scope.put("literalmessage", scope, new Callback() {
	  public Object run(Object[] args) {
	    try {
              if (args.length != 1) {
                throw new Exception("message() requires one argument");
              }
              messageHandler.print((String)args[0]);
              return null;
	    } catch (Exception e) {
	      errorHandler.error(e);
	      return null;
	    }
	  }
	});
      scope.put("localize", scope, new Callback() {
	  public Object run(Object[] args) {
	    try {
              if (args.length == 0)
                throw new Exception("localize() requires at least one argument");
              List params = massageTokenList(args);
              return translator.translate(params);
	    } catch (Exception e) {
	      errorHandler.error(e);
	      return null;
	    }
	  }
	});
      scope.put("message", scope, new Callback() {
	  public Object run(Object[] args) {
	    try {
              if (args.length == 0)
                throw new Exception("message() requires at least one argument");
              List params = massageTokenList(args);
              messageHandler.print(translator.translate(params));
              return null;
	    } catch (Exception e) {
	      errorHandler.error(e);
	      return null;
	    }
	  }
	});
    } catch (JavaScriptException e) {
      errorHandler.error(e);
    } finally {
      Context.exit();
    }
    return scope;
  }

  /**
   * A couple of Javascript-accessible functions (message and localize) take
   * translation tokens as arguments. We need to ensure that these lists
   * contains only strings.
   *
   * Also, as a convenience, we convert Java numeric objects to literal
   * strings. That way, message("game.your_score_was", 6) will work nicely.
   *
   * @param args a list of anything (preferably tokens strings)
   */
  protected List massageTokenList(Object[] args) {
    List ls = new ArrayList();
    for (int ix=0; ix<args.length; ix++) {
        Object obj = args[ix];
        if (obj instanceof String) {
            // ok as is
        }
        else if (obj instanceof Number) {
            obj = "literal." + String.valueOf(obj);
        }
        else if (obj == null) {
            obj = "literal.(null)";
        }
        else {
            obj = "literal.(unprintable object " + obj.toString() + ")";
        }
        ls.add(obj);
    }
    return ls;
  }

  class Info extends ScriptableObject {
    public String getClassName() { return "Info"; }
    {
      try {
	defineProperty("nickname", Info.class, PERMANENT);
	defineProperty("seat", Info.class, PERMANENT);
	defineProperty("opponents", Info.class, PERMANENT);
      } catch (PropertyException e) {
	throw new RuntimeException(e.toString());
      }
    }
    public String getSeat() { return "XXX-seat-id-here"; }
    public String getNickname() { return table.getNickname(); }
    public void setNickname(String nickname) throws XMPPException {
      table.changeNickname(nickname);
    }
    Scriptable opponents;
    public Object getOpponents() throws JavaScriptException {
      List nicknames = table.getOpponents();
      Context context = Context.getCurrentContext();
      opponents = context.newArray(scope, nicknames.size());
      for (Iterator it = nicknames.iterator(); it.hasNext();) {
	String nickname = (String) it.next();
	opponents.put(nickname, opponents, context.newObject(scope));
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
      if (scope == null) initGameObjects();
      Context.enter().evaluateReader(scope, new FileReader(uiScript),
				     uiScript.getName(), 1, null);
    } finally {
      Context.exit();
    }
  }

  // Inherited from PacketFilter.
  public boolean accept(Packet packet) {
    // Only accept packets from the referee at this table, because the
    // user might be playing at multiple tables (perhaps even in
    // multiple instances of the application).
    if (table == null) return false;
    Referee ref = table.getReferee();
    // If the referee is missing, we should usually assume that this
    // packet is a wayward request and ignore it.  But because filters
    // run in a different thread from listeners (!) the referee's
    // presence packet might still be waiting to be processed, so we
    // are lenient here and accept this packet, with a second check in
    // the actual listener (see RPCResponder.processPacket).  A future
    // version of Smack may fix this, in which case the check should
    // be made more restrictive again.
    /* return ref != null && */
    return ref == null ||
      ref.getResponderJID().equals(packet.getFrom());
  }

  // Inherited from RPCHandler.
  public void handleRPC(String methodName, List params, RPCResponseHandler k) {
    Object method = game.get(methodName, scope);
    if (method instanceof Function)
      try {
	k.respondValue(callUIMethod((Function) method, params));
      } catch (JavaScriptException e) {
	errorHandler.error(e);
	// FIXME: Volity protocol should probably define these
	// error codes.
	k.respondFault(901, "UI script exception: " + e);
      } catch (EvaluatorException e) {
	errorHandler.error(e);
	k.respondFault(902, "UI script error: " + e);
      }
    else
      k.respondFault(903, "No such UI function.");
  }

  /**
   * Call a UI method.
   * @param method the UI method to be called
   * @param params the list of method arguments (RPC data objects)
   * @return the method return value (RPC data object)
   * @throws JavaScriptException if the UI method throws an exception
   * @throws EvaluatorException if an error occurs while evaluating
   *                            the UI method body
   */
  public Object callUIMethod(Function method, List params)
    throws JavaScriptException
  {
    try {
      Context context = Context.enter();

      /* We can't run SVG script in any old Context; it has to be a Context
       * created by the Batik classes. (If we fail to do this, various things
       * go wrong in the script. Notably, any attempt to refer to "document"
       * throws a ClassCastException.) See the SVGCanvas.SVGUI methods for the
       * code that ensures this.
       */
      if (!(context instanceof RhinoInterpreter.ExtendedContext)) {
        throw new AssertionError("Tried to run ECMAScript for SVG in a non-Batik Context");
      }

      context.setWrapFactory(rpcWrapFactory);
      Object ret = method.call(context, scope, game, params.toArray());
      if (ret instanceof Undefined) {
	// function returned void, but RPC result has to be non-void
	ret = Boolean.TRUE;
      }
      return ret;
    } finally {
      Context.exit();
    }
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
