package org.volity.client;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.mozilla.javascript.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.util.StringUtils;
import org.volity.jabber.*;
import org.volity.jabber.packet.MUCUser;

/**
 * A game user interface.  Subclasses must implement writeln, endGame,
 * and error.
 */
public abstract class GameUI extends RPCResponder {
  /**
   * @param table the table where the game will be played
   * @param uiScript an ECMAScript file implementing the game UI
   */
  public GameUI(GameTable table, File uiScript) {
    super(table.getConnection());
    this.table = table;
    this.uiScript = uiScript;
  }

  GameTable table;
  File uiScript;
  Scriptable scope, game, commandFunctions;
  RPCWrapFactory rpcWrapFactory = new RPCWrapFactory();

  /**
   * Execute the UI script.
   * @throws IOException if there is an I/O problem reading the UI script
   * @throws JavaScriptException if the UI script causes an exception
   */
  public void start() throws IOException, JavaScriptException {
    try {
      Context context = Context.enter();
      scope = context.initStandardObjects();

      scope.put("game", scope, game = context.newObject(scope));
      game.put("myNickname", game, table.getNickname());
      Scriptable opponents = context.newObject(scope);
      for (Iterator it = table.getParticipants(); it.hasNext();) {
	String roomJID = (String) it.next();
	MUCUser userInfo = table.getUserInfo(roomJID);
	if (userInfo.getAffiliation() == MUCUser.Affiliation.OWNER)
	  // Skip the ref.
	  continue;
	String nickname = StringUtils.parseResource(roomJID);
	if (nickname.equals(table.getNickname()))
	  // Skip myself.
	  continue;
	opponents.put(nickname, opponents, context.newObject(scope));
      }
      game.put("opponents", game, opponents);

      // FIXME: referee is a misleading name for this object
      scope.put("referee", scope, commandFunctions = context.newObject(scope));

      scope.put("writeln", scope, new Callback() {
	  public Object run(Object[] args) {
	    writeln(String.valueOf(args[0]));
	    return null;
	  }
	});
      scope.put("rpc", scope, new Callback() {
	  public Object run(Object[] args) {
	    try {
	      List params = Arrays.asList(args).subList(1, args.length);
	      return table.getReferee().invoke("game." + args[0], params);
	    } catch (Exception e) {
	      error(e);
	      return null;
	    }
	  }
	});
      commandFunctions.put("registerCommand", commandFunctions, new Callback() {
	  public Object run(Object[] args) {
	    commands.add(new Command((String) args[0], (String) args[1],
				     (String) args[2]));
	    return null;
	  }
	});

      context.evaluateReader(scope, new FileReader(uiScript),
			     uiScript.getName(), 1, null);
    } finally {
      Context.exit();
    }
  }

  // Inherited from RPCResponder
  public Object handleRPC(String methodName, List params) throws RPCException {
    if (methodName.startsWith("game.")) {
      Object method = game.get(methodName.substring(5), scope);
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
	  error(e);
	  // FIXME: Volity protocol should probably define these error codes.
	  throw new RPCException(901, "UI script error: " + e);
	} finally {
	  Context.exit();
	}
      else
	throw new RPCException(902, "No such UI function.");
    } else if (methodName.equals("end_game")) {
      endGame();
      return Boolean.TRUE;
    } else
      throw new RPCException(903, "No such client method.");
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

  /** An unmodifiable list of the registered commands (Command objects). */
  public List getCommands() { return Collections.unmodifiableList(commands); }
  List commands = new ArrayList();

  /** A user command, registered by the UI script and invoked by the client. */
  public class Command {
    /**
     * @param name the name of the command
     * @param shortDescription a short description of the command
     * @param longDescription a multi-line description of the command,
     *                        for help text
     */
    public Command(String name, String shortDescription,
		   String longDescription)
    {
      this.name = name;
      this.shortDescription = shortDescription;
      this.longDescription = longDescription;
    }

    String name;
    public String getName() { return name; }

    String shortDescription;
    public String getShortDescription() { return shortDescription; }

    String longDescription;
    public String getLongDescription() { return longDescription; }

    Function function;
    // We can't set this at construction time (when registerCommand is
    // invoked) because the script is still being evaluated and the
    // command might not have been defined yet!
    Function getFunction() throws NoSuchMethodException {
      if (function == null) {
	Object f = commandFunctions.get(name, commandFunctions);
	if (!(f instanceof Function))
	  throw new NoSuchMethodException(name);
	function = (Function) f;
      }
      return function;
    }

    /**
     * Invoke this command, which is passed to the UI script.
     * @param args the arguments to the command
     * @return the return value from the command
     * @throws NoSuchMethodException if the command is undefined
     * @throws JavaScriptException if the command causes an exception
     */
    public Object invoke(String args)
      throws NoSuchMethodException, JavaScriptException
    {
      try {
	Context context = Context.enter();
	return getFunction().call(context, scope, commandFunctions,
				  new Object[] { args });
      } finally {
	Context.exit();
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

  /**
   * Display a line of text to the user.  Called by the UI script.
   */
  public abstract void writeln(String line);

  /**
   * End the current game.  Called by the referee when the game ends.
   */
  public abstract void endGame();

  /**
   * Report a command error.
   */
  public abstract void error(Exception e);

}
