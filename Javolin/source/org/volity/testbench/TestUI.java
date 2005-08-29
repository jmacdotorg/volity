package org.volity.testbench;

import java.io.*;
import java.util.*;
import org.mozilla.javascript.*;
import org.volity.client.TranslateToken;
import org.volity.client.TokenFailure;

/**
 * This class is analogous to org.volity.client.GameUI. It represents the game
 * interface, only for the testbench environment rather than a true Volity
 * client.
 */
public class TestUI 
{
    public interface MessageHandler {
        /**
         * Report a status or game-response message.
         */
        public abstract void print(String msg);
    }

    public interface ErrorHandler {
        /**
         * Report a command error.
         */
        public abstract void error(Exception e);
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

    TranslateToken translator;
    ErrorHandler errorHandler;
    MessageHandler messageHandler;

    String currentSeat;
    Scriptable scope, game, info, client;
    RPCWrapFactory rpcWrapFactory = new RPCWrapFactory();

    public TestUI(TranslateToken translator,
        MessageHandler messageHandler) {

        this.translator = translator;
        this.messageHandler = messageHandler;
        this.errorHandler = new ErrorHandler() {
                public void error(Exception e) {
                    if (e instanceof TokenFailure) {
                        String msg = TestUI.this.translator.translate((TokenFailure)e);
                        TestUI.this.messageHandler.print(msg);
                    }
                    else {
                        String msg = e.toString();
                        TestUI.this.messageHandler.print(msg);
                    }
                }
            };

        currentSeat = null;
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

    /**
     * Execute a string as a UI script.
     * @param uiScript a string containing ECMAScript code
     * @throws IOException if there is an I/O problem reading the UI script
     * @throws JavaScriptException if the UI script throws an exception
     * @throws EvaluationException if an error occurs while evaluating
     *                             the UI script
     */
    //### "document" does not resolve. Huh? ###
    public void loadString(String uiScript)
        throws IOException, JavaScriptException {
        try {
            if (scope == null) initGameObjects();
            Context.enter().evaluateReader(scope, new StringReader(uiScript),
                "<debug command>", 1, null);
        } finally {
            Context.exit();
        }
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
                            messageHandler.print("sent RPC: " + prettifyParams(args));
                            return null;
                        } catch (Exception e) {
                            errorHandler.error(e);
                            /* This will print TokenFailures in window log, and
                             * display other exceptions as dialog box */
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
     * Set where the imaginary client thinks it's sitting. (In a real
     * client, this information would come from the client's seating
     * map, which is updated by volity.player_sat() / volity.player_stood()
     * RPCs.)
     *
     * Param seatId the notional player's current seat (null for unseated)
     */
    public void setCurrentSeat(String seatId) {
        currentSeat = seatId;
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

    protected String prettifyParams(Object[] args) {
        StringBuffer buf = new StringBuffer();
        buf.append("game." + args[0]);

        buf.append("(");
        for (int ix=1; ix<args.length; ix++) {
            if (ix > 1)
                buf.append(", ");
            Object obj = args[ix];
            if (obj instanceof Number) {
                Number nobj = (Number)obj;
                if (nobj.doubleValue() == nobj.intValue())
                    buf.append(String.valueOf(nobj.intValue()));
                else
                    buf.append(nobj.toString());
            }
            else if (obj instanceof String) {
                String sobj = (String)obj;
                buf.append("\"" + sobj + "\"");
            }
            //### org.mozilla.javascript.NativeArray, and whatever struct maps to
            else {
                buf.append(obj.toString());
            }
        }
        buf.append(")");

        return buf.toString();
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
        public String getSeat() { return currentSeat; }
        public String getNickname() { return "XXX-nickname"; }
        public void setNickname(String nickname) throws Exception {
            throw new Exception("Cannot change nickname in testbench");
        }
        Scriptable opponents;
        public Object getOpponents() throws JavaScriptException {
            Context context = Context.getCurrentContext();
            opponents = context.newArray(scope, 0); //XXX?
            return opponents;
        }
    }

}
