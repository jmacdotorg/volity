package org.volity.client;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.mozilla.javascript.*;
import org.jivesoftware.smack.XMPPConnection;
import org.volity.jabber.*;

/**
 * A Friv-compatible game user interface.
 */
public class FrivUI extends GameUI {
  /**
   * @param connection an authenticated connection to an XMPP server
   * @throws IllegalStateException if the connection has not been authenticated
   */
  public FrivUI(XMPPConnection connection,
		ErrorHandler errorHandler,
		GameUI.MessageHandler writelnHandler)
  {
    super(connection, new TranslateToken(null), writelnHandler, errorHandler);
    this.writelnHandler = writelnHandler;
  }

  MessageHandler writelnHandler;

  // Inherited from GameUI.
  public ScriptableObject initGameObjects(ScriptableObject scope) {
    scope = super.initGameObjects(scope);
    Context context = Context.enter();
    scope.put("writeln", scope, new Callback() {
	public Object run(Object[] args) {
	  writelnHandler.print(String.valueOf(args[0]));
	  return null;
	}
      });
    client.put("registerCommand", client, new Callback() {
	public Object run(Object[] args) {
	  commands.put(args[0],
		       new Command((String) args[0], (String) args[1],
				   (String) args[2]));
	  return null;
	}
      });
    Context.exit();
    return scope;
  }

  /**
   * An unmodifiable map of the registered command names and Command
   * objects.
   */
  public Map getCommands() { return Collections.unmodifiableMap(commands); }
  Map commands = new HashMap();

  /** Get a registered command by name, or null if there is no such command. */
  public Command getCommand(String name) {
    return (Command) commands.get(name);
  }

  /**
   * Invoke a command.
   * @param name the name of the command
   * @param args the arguments to the command
   * @return the return value from the command
   * @throws NoSuchMethodException if the command is undefined
   * @throws JavaScriptException if the command causes an exception
   */
  public Object invokeCommand(String name, String args)
    throws NoSuchMethodException, JavaScriptException
  {
    Command cmd = getCommand(name);
    if (cmd == null) throw new NoSuchMethodException(name);
    return cmd.invoke(args);
  }

  /**
   * Interpret a command line of the form "cmd arg ...".
   * @return the return value from the command
   * @throws NoSuchMethodException if the command is undefined
   * @throws JavaScriptException if the command causes an exception
   */
  public Object interpret(String commandLine)
    throws NoSuchMethodException, JavaScriptException
  {
    commandLine = commandLine.trim();
    int i = commandLine.indexOf(" ");
    return (i < 0
	    ? invokeCommand(commandLine, "")
	    : invokeCommand(commandLine.substring(0, i),
			    commandLine.substring(i+1)));
  }

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
	Object f = client.get(name, client);
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
	return getFunction().call(context, scope, client,
				  new Object[] { args });
      } finally {
	Context.exit();
      }
    }
  }

}
