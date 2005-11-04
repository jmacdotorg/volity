package org.volity.client;

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
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
   * (The arguments here are slightly redundant -- we could implement the
   * FailureToken-grabbing part of errorHandler in this class using translator
   * and messageHandler. But then we'd have to catch more exceptions. Maybe we
   * should do it anyway.)
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
  private Boolean callInProgress = Boolean.FALSE;
  Map seatObjects = new HashMap();

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

  public interface Completion {
    public abstract void result(Object obj);
    public abstract void error(Exception ex);
  }

  /**
   * Disconnect the RPC handler and cease work. 
   * Call this when the user interface (e.g., the window) closes.
   */
  public void stop() {
    responder.stop();
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

  /**
   * Return the UISeat object for a given Seat.
   *
   * We keep a cache of these, in a hash table. Any call to getUISeat() for a
   * given Seat returns the same UISeat. (This is true even if the seating
   * system discards a Seat and replaces it with a new one that has the same
   * ID. Not that this should happen... but if it does, it should be
   * transparent to the UI.)
   *
   * Why? If there were two UISeat objects for the same ID, they'd compare as
   * "not equal" in UI code -- even though they'd appear identical. This
   * would be confusing and weird.
   */
  UISeat getUISeat(Seat gameseat) {
    String id = gameseat.getID();
    if (!seatObjects.containsKey(id)) {
      UISeat seat = new UISeat(id);
      seatObjects.put(id, seat);
      return seat;
    }
    else {
      UISeat seat = (UISeat)seatObjects.get(id);
      return seat;
    }
  }

  public class Info extends ScriptableObject {
    {
      try {
        defineProperty("nickname", Info.class, PERMANENT);
        defineProperty("seat", Info.class, PERMANENT);
        defineProperty("allseats", Info.class, PERMANENT);
        defineProperty("gameseats", Info.class, PERMANENT);
      } catch (PropertyException e) {
        errorHandler.error(e);
      }
    }

    public String getClassName() { return "Info"; }
    public Object getDefaultValue(Class typeHint) { return toString(); }

    public UISeat getSeat() { 
      Player player = table.getSelfPlayer();
      if (player == null)
        return null;
      Seat seat = player.getSeat();
      if (seat == null)
        return null;
      return getUISeat(seat);
    }
    public String getNickname() { return table.getNickname(); }
    public void setNickname(String nickname) throws XMPPException {
      table.changeNickname(nickname);
    }
    public Object getAllseats() throws JavaScriptException {
      Context context = Context.getCurrentContext();
      Scriptable ls = context.newArray(scope, 0);
      int count = 0;
      for (Iterator it = table.getSeats(); it.hasNext(); ) {
        Seat seat = (Seat)it.next();
        ls.put(count++, ls, getUISeat(seat));
      }
      return ls;
    }
    public Object getGameseats() throws JavaScriptException {
      Context context = Context.getCurrentContext();
      Scriptable ls = context.newArray(scope, 0);
      int count = 0;
      for (Iterator it = table.getVisibleSeats(); it.hasNext(); ) {
        Seat seat = (Seat)it.next();
        ls.put(count++, ls, getUISeat(seat));
      }
      return ls;
    }
  }

  class UISeat extends ScriptableObject {
    {
      try {
        defineProperty("players", UISeat.class, PERMANENT);
      } catch (PropertyException e) {
        throw new RuntimeException(e.toString());
      }
    }

    protected String id;

    public UISeat(String id) {
      this.id = id;
    }

    public String getClassName() { return "Seat"; }
    public Object getDefaultValue(Class typeHint) { return id; }

    public Object getPlayers() throws JavaScriptException {
      Context context = Context.getCurrentContext();
      Scriptable ls = context.newArray(scope, 0);
      Seat seat = table.getSeat(id);
      if (seat != null) {
        int count = 0;
        for (Iterator it = seat.getPlayers(); it.hasNext(); ) {
          Player player = (Player)it.next();
          ls.put(count++, ls, player.getJID());
        }
      }
      return ls;
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

  // Implements PacketFilter interface.
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

  // Implements RPCHandler interface.
  public void handleRPC(String methodName, List params, 
    final RPCResponseHandler k) {
    Object method = game.get(methodName, scope);

    if (!(method instanceof Function)) {
      k.respondFault(603, "No game."+methodName+" function in UI.");
      return;        
    }

    /*
     * Call the method, passing a callback which will be certain to respond to
     * the RPC.
     */

    callUIMethod((Function) method, params, new Completion() {
      public void result(Object obj) {
        if (obj instanceof Undefined) {
          // function returned null/void, but RPC result has to be non-void
          obj = Boolean.TRUE;
        }
        k.respondValue(obj);
      }
      public void error(Exception ex) {
        errorHandler.error(ex);
        k.respondFault(608, "UI script error: " + ex.toString());
      }
    });
  }

  /**
   * Call a UI method. Since this is a slow operation, probably involving work
   * in another thread, it does not return a value or throw exceptions.
   * Instead, you may supply a callback which will be called when the method
   * completes. (The callback may not be called in your thread!) If the
   * callback is null, the result (or exception) of the method is silently
   * dropped.
   *
   * A word on concurrency: it is a bad idea for the UI to execute two methods
   * in different threads at the same time. ECMAScript isn't built for
   * multithreading, and if it were, UI authors still wouldn't want to do it.
   * However, it is not useful to put serialization guards (say, mutexes) in
   * this method. The right way to solve the problem is to queue everything in
   * one thread -- but the UI package (Batik) will have its own methods for
   * doing this.
   *
   * Therefore, you must subclass GameUI, and wrap callUIMethod in code that
   * does the appropriate serialization. (For Batik, that means calling
   * getUpdateRunnableQueue.invokeLater(). See SVGUI in SVGCanvas.)
   *
   * If I were being more consistent, I'd make this an abstract method. In lieu
   * of that, and to ensure that mistakes are obvious, I am putting asserts in
   * this method. The callInProgress field is used as a guard against
   * concurrent method calling.
   *
   * @param method the UI method to be called
   * @param params the list of method arguments (RPC data objects)
   * @param callback completion function, or null
   */
  public void callUIMethod(Function method, List params, Completion callback)
  {
    try {
      Context context = Context.enter();
      synchronized (callInProgress) {
        if (callInProgress == Boolean.TRUE)
          throw new AssertionError("Tried to run two ECMAScript calls at the same time");
        callInProgress = Boolean.TRUE;
      }

      /* We can't run SVG script in any old Context; it has to be a Context
       * created by the Batik classes. (If we fail to do this, various things
       * go wrong in the script. Notably, any attempt to refer to "document"
       * throws a ClassCastException.) See the SVGCanvas.SVGUI methods for the
       * code that ensures this.
       *
       * NOTE: Technically the GameUI class should never mention Batik -- all
       * Batik-specific behavior should be in the SGVUI subclass. However, this
       * assertion is protecting our asses. If we ever get an HTMLUI subclass,
       * we'll have to modify the assert appropriately.
       */
      if (!(context instanceof org.apache.batik.script.rhino.RhinoInterpreter.ExtendedContext)) {
        throw new AssertionError("Tried to run ECMAScript for SVG in a non-Batik Context");
      }

      context.setWrapFactory(rpcWrapFactory);
      try {
        Object ret = method.call(context, scope, game, params.toArray());
        if (callback != null)
          callback.result(ret);
      }
      catch (Exception ex) {
        if (callback != null) 
          callback.error(ex);
      }
    } finally {
      synchronized (callInProgress) {
        callInProgress = Boolean.FALSE;
      }
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
              if (name.equals("length"))
                return true;
              return false;
            }
            public Object get(String name, Scriptable start) {
              if (name.equals("length"))
                return new Integer(list.size());
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
