package org.volity.testbench;

import java.io.*;
import java.net.URL;
import java.util.*;
import org.apache.batik.script.rhino.RhinoInterpreter;
import org.mozilla.javascript.*;
import org.volity.client.Audio;
import org.volity.client.GameUI;
import org.volity.client.TokenFailure;
import org.volity.client.TranslateToken;

/**
 * This class is analogous to org.volity.client.GameUI. It represents the game
 * interface, only for the testbench environment rather than a true Volity
 * client.
 */
public abstract class TestUI 
{
    public static abstract class ErrorHandler implements GameUI.ErrorHandler {
        /**
         * Report a command error. This is an abstract class because it
         * "extends" the GameUI ErrorHandler interface.
         */
        public abstract void error(Throwable e);
        public abstract void error(Throwable e, String prefix);
        public void error(Exception e) { error((Throwable)e); }
    }

    public interface Completion {
        public abstract void result(Object obj);
        public abstract void error(Throwable ex);
    }

    /**
     * Call a UI method. Since this is a slow operation, probably involving
     * work in another thread, it does not return a value or throw exceptions.
     * Instead, you may supply a callback which will be called when the method
     * completes. (The callback may not be called in your thread!) If the
     * callback is null, the result (or exception) of the method is silently
     * dropped.
     *
     * A word on concurrency: it is a bad idea for the UI to execute two
     * methods in different threads at the same time. ECMAScript isn't built
     * for multithreading, and if it were, UI authors still wouldn't want to do
     * it. However, it is not useful to put serialization guards (say, mutexes)
     * in this method. The right way to solve the problem is to queue
     * everything in one thread -- but the UI package (Batik) will have its own
     * methods for doing this.
     *
     * Therefore, you must subclass TestUI, and wrap callUIMethod in code that
     * does the appropriate serialization. (For Batik, that means calling
     * getUpdateRunnableQueue.invokeLater(). See SVGUI in SVGTestCanvas.)
     *
     * If I were being more consistent, I'd make this an abstract method. In
     * lieu of that, and to ensure that mistakes are obvious, I am putting
     * asserts in this method. The callInProgress field is used as a guard
     * against concurrent method calling.
     *
     * @param method the UI method to be called
     * @param params the list of method arguments (RPC data objects)
     * @param callback completion function, or null
     */
    public void callUIMethod(Function method, List params, Completion callback)
    {
        try {
            Context context = Context.enter();
            synchronized (callInProgressLock) {
                if (callInProgress == Boolean.TRUE)
                    throw new AssertionError("Tried to run two ECMAScript calls at the same time");
                callInProgress = Boolean.TRUE;
            }

            /* If we haven't already, put an RPCWrapFactory around whatever
             * WrapFactory exists on the context. (We don't want to give up the
             * BatikWrapFactory which exists on Batik contexts.) */
            WrapFactory wrapper = context.getWrapFactory();
            if (!(wrapper instanceof GameUI.RPCWrapFactory)) {
                wrapper = new GameUI.RPCWrapFactory(wrapper);
                context.setWrapFactory(wrapper);
            }

            try {
                Object ret = method.call(context, scope, game, params.toArray());
                if (ret == null || ret instanceof Undefined) {
                    // function returned void, but RPC result has to be non-void
                    ret = Boolean.TRUE;
                }
                if (callback != null) 
                    callback.result(ret);
            }
            catch (JavaScriptException ex) {
                errorHandler.error(ex);
                if (callback != null) 
                    callback.error(ex);
            }
        } finally {
            synchronized (callInProgressLock) {
                callInProgress = Boolean.FALSE;
            }
            Context.exit();
        }
    }

    URL baseURL;
    TranslateToken translator;
    ErrorHandler errorHandler;
    ErrorHandler parentErrorHandler;
    GameUI.MessageHandler messageHandler;
    Object securityDomain; 

    String currentSeat;
    Scriptable scope, game, info, volity;
    private Boolean callInProgress = Boolean.FALSE;
    private Object callInProgressLock = new Object();

    Map seatObjects = new HashMap();

    public TestUI(URL baseURL,
        TranslateToken translator,
        GameUI.MessageHandler messageHandler,
        ErrorHandler parentErrorHandler) {

        this.baseURL = baseURL;
        this.translator = translator;
        this.messageHandler = messageHandler;
        this.parentErrorHandler = parentErrorHandler;
        // This is not super-necessary, since nothing in Testbench can
        // *raise* a TokenFailure.
        this.errorHandler = new ErrorHandler() {
                public void error(Throwable e) {
                    error(e, null);
                }
                public void error(Throwable e, String st) {
                    if (e instanceof TokenFailure) {
                        String msg = TestUI.this.translator.translate((TokenFailure)e);
                        TestUI.this.messageHandler.print(msg);
                    }
                    else {
                        TestUI.this.parentErrorHandler.error(e, st);
                    }
                }
            };

        currentSeat = null;
    }

    public abstract DebugInfo getDebugInfo();
    
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
            Context context = Context.enter();
            synchronized (callInProgressLock) {
                if (callInProgress == Boolean.TRUE)
                    throw new AssertionError("Tried to run two ECMAScript calls at the same time");
                callInProgress = Boolean.TRUE;
            }

            context.evaluateReader(scope, new FileReader(uiScript),
                uiScript.getName(), 1, null);
        } finally {
            synchronized (callInProgressLock) {
                callInProgress = Boolean.FALSE;
            }
            Context.exit();
        }
    }

    /**
     * Execute a string as a UI script.
     * @param uiScript a string containing ECMAScript code
     */
    public void loadString(String uiScript, String scriptLabel) {
        try {
            if (scope == null) initGameObjects();
            Context context = Context.enter();
            synchronized (callInProgressLock) {
                if (callInProgress == Boolean.TRUE)
                    throw new AssertionError("Tried to run two ECMAScript calls at the same time");
                callInProgress = Boolean.TRUE;
            }

            context.evaluateString(scope, uiScript,
                "<debug command>", 1, securityDomain);
        }
        catch (Exception ex) {
            errorHandler.error(ex, scriptLabel + " failed");
        }
        finally {
            synchronized (callInProgressLock) {
                callInProgress = Boolean.FALSE;
            }
            Context.exit();
        }
    }

    /**
     * To compile and run debug commands, we need a valid security domain. That
     * has to be nipped out of the RhinoInterpreter. (See SVGTestCanvas.)
     */
    public void setSecurityDomain(Object securityDomain) {
        this.securityDomain = securityDomain;
    }

    /**
     * Initialize game-handling objects: "game", "info", "volity", and "rpc".
     * @return the initialized scope
     */
    public ScriptableObject initGameObjects() {
        return initGameObjects(null);
    }

    /**
     * Initialize game-handling objects: "game", "info", "volity", and "rpc".
     * @param scope the scope to initialize, or null, in which case a
     *              new object will be created to serve as the scope.
     * @return the initialized scope, which is the same as the scope
     *         argument if not null.
     */
    public ScriptableObject initGameObjects(ScriptableObject scope) {
        try {
            Context context = Context.enter();
            if (scope == null) {
                scope = context.initStandardObjects();
            }
            this.scope = scope;
            scope.put("game", scope, game = context.newObject(scope));
            scope.put("volity", scope, volity = context.newObject(scope));
            scope.put("info", scope, info = new Info());
            scope.put("rpc", scope, new GameUI.Callback() {
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
            scope.put("literalmessage", scope, new GameUI.Callback() {
                    public Object run(Object[] args) {
                        try {
                            if (args.length != 1) {
                                throw new Exception("message() requires one argument");
                            }
                            if (args[0] == null)
                                messageHandler.print("null");
                            else
                                messageHandler.print(args[0].toString());
                            return null;
                        } catch (Exception e) {
                            errorHandler.error(e);
                            return null;
                        }
                    }
                });
            scope.put("localize", scope, new GameUI.Callback() {
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
            scope.put("message", scope, new GameUI.Callback() {
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
            scope.put("seatmark", scope, new GameUI.Callback() {
                    public Object run(Object[] args) {
                        return null;
                    }
                });
            scope.put("audio", scope, 
                GameUI.UIAudio.makeCallableProperty(this, baseURL, 
                    messageHandler, errorHandler));
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
     * Cut off any playing sounds which have this UI for an owner.
     */
    public void stopAllSound() {
        Audio.stopGroup(this);
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
     * Convert a list of arguments for rpc() into a string.
     */
    protected String prettifyParams(Object[] args) {
        StringBuffer buf = new StringBuffer();
        buf.append("game." + args[0]);

        buf.append("(");
        for (int ix=1; ix<args.length; ix++) {
            if (ix > 1)
                buf.append(", ");
            Object obj = args[ix];
            prettifyParam(buf, obj);
        }
        buf.append(")");

        return buf.toString();
    }

    /**
     * Convert a single RPC argument into a string, and append it to the given
     * StringBuffer.
     */
    protected void prettifyParam(StringBuffer buf, Object obj) {
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
        else if (obj instanceof NativeArray) {
            NativeArray arr = (NativeArray)obj;
            Object[] ids = arr.getIds();

            boolean simple = true;
            for (int ix=0; ix<ids.length; ix++) {
                Object id = ids[ix];
                if (id instanceof Number && ((Number)id).doubleValue() == ix)
                    continue;
                simple = false;
                break;
            }

            if (simple) {
                buf.append("[");
                for (int ix=0; ix<ids.length; ix++) {
                    if (ix > 0)
                        buf.append(", ");
                    Object id = ids[ix];
                    Object val = arr.get(ix, arr);
                    prettifyParam(buf, val);
                }
                buf.append("]");
            }
            else {
                boolean printedany = false;
                buf.append("{");
                for (int ix=0; ix<ids.length; ix++) {
                    Object id = ids[ix];
                    Object val;
                    if (id instanceof Integer)
                        val = arr.get(((Integer)id).intValue(), arr);
                    else
                        val = arr.get(id.toString(), arr);
                    if (val == null || val instanceof Undefined)
                        continue;
                    if (printedany)
                        buf.append(", ");
                    printedany = true;
                    prettifyParam(buf, id);
                    buf.append(": ");
                    prettifyParam(buf, val);
                }
                buf.append("}");
            }
        }
        else {
            buf.append(obj.toString());
        }
    }

    /**
     * Return the UISeat object for a given seat ID.
     *
     * We keep a cache of these, in a hash table. Any call to getSeatById() for
     * a given ID returns the same UISeat.
     *
     * Why? If there were two UISeat objects for the same ID, they'd compare as
     * "not equal" in UI code -- even though they'd appear identical. This
     * would be confusing and weird.
     */
    UISeat getSeatById(String id) {
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

    class Info extends ScriptableObject {
        {
            try {
                defineProperty("state", Info.class, PERMANENT);
                defineProperty("recovery", Info.class, PERMANENT);
                defineProperty("nickname", Info.class, PERMANENT);
                defineProperty("seat", Info.class, PERMANENT);
                defineProperty("allseats", Info.class, PERMANENT);
                defineProperty("gameseats", Info.class, PERMANENT);
            } catch (PropertyException e) {
                throw new RuntimeException(e.toString());
            }
        }

        public String getClassName() { return "Info"; }
        public Object getDefaultValue(Class typeHint) { return toString(); }

        public String getState() {
            return null; //### track "ref" state
        }
        public Boolean getRecovery() {
            return Boolean.FALSE; //### track?
        }
        public UISeat getSeat() { 
            if (currentSeat == null)
                return null;
            return getSeatById(currentSeat);
        }
        public String getNickname() { return "XXX-nickname"; }
        public void setNickname(String nickname) {
            throw new RuntimeException("Cannot change nickname in testbench");
        }
        public Object getAllseats() throws JavaScriptException {
            List seatlist = getDebugInfo().getSeatList();

            Context context = Context.getCurrentContext();
            Scriptable ls = context.newArray(scope, 0);
            int count = 0;
            for (Iterator it = seatlist.iterator(); it.hasNext(); ) {
                String val = (String)it.next();
                ls.put(count++, ls, getSeatById(val));
            }
            return ls;
        }
        public Object getGameseats() throws JavaScriptException {
            return getAllseats();
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
            ls.put(0, ls, id+"@testbench/app");
            if (currentSeat != null && id.equals(currentSeat)) {
                ls.put(1, ls, "you@testbench/testbench");
            }
            return ls;
        }
    }

}
