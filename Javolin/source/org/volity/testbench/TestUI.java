package org.volity.testbench;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrapFactory;
import org.volity.client.Audio;
import org.volity.client.GameUI;
import org.volity.client.data.Metadata;
import org.volity.client.data.VersionNumber;
import org.volity.client.data.VersionSpec;
import org.volity.client.translate.TokenFailure;
import org.volity.client.translate.TranslateToken;

/**
 * This class is analogous to org.volity.client.GameUI. It represents the game
 * interface, only for the testbench environment rather than a true Volity
 * client.
 */
public abstract class TestUI 
{
    /**
     * This describes the API version implemented in this file.
     */
    public static VersionNumber sUIVersion = GameUI.sUIVersion;

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
     * Abstract method. This should read metadata from the gamefile (baseURL).
     * If there is no metadata, create a blank Metadata() and return that.
     */
    public abstract Metadata loadMetadata();

    URL baseURL;
    TranslateToken translator;
    ErrorHandler errorHandler;
    ErrorHandler parentErrorHandler;
    GameUI.MessageHandler messageHandler;
    Metadata metadata;

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

        this.metadata = loadMetadata();

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

    /**
     * Get the Testbench-specific debug information (projected list of seats,
     * debug buttons). This is an abstract method, which should be overridden
     * by the SVGTestCanvas class.
     */
    public abstract DebugInfo getDebugInfo();

    /**
     * Execute a string as a UI script. This is an abstract method, which
     * should be overridden by the SVGTestCanvas class.
     *
     * @param uiScript a string containing ECMAScript code
     * @param scriptLabel a label to attach to error messages
     */
    public abstract void loadString(String uiScript, String scriptLabel);

    /**
     * Execute an ECMAScript function object. This is an abstract method, which
     * should be overridden by the SVGTestCanvas class.
     *
     * Since this is a slow operation, probably involving work in another
     * thread, it does not return a value or throw exceptions. Instead, you may
     * supply a callback which will be called when the method completes. (The
     * callback may not be called in your thread!) If the callback is null, the
     * result (or exception) of the method is silently dropped.
     *
     * A word on concurrency: it is a bad idea for the UI to execute two
     * methods in different threads at the same time. ECMAScript isn't built
     * for multithreading, and if it were, UI authors still wouldn't want to do
     * it. However, it is not useful to put serialization guards (say, mutexes)
     * in the execution methods. The right way to solve the problem is to queue
     * everything in one thread -- but the UI package (Batik) will have its own
     * methods for doing this.
     *
     * Therefore, you must subclass TestUI, and implement callUIMethod and
     * loadString with code that does the appropriate serialization. (For
     * Batik, that means calling getUpdateRunnableQueue.invokeLater(). See
     * SVGUI in SVGTestCanvas.)
     *
     * @param method the UI method to be called
     * @param params the list of method arguments (RPC data objects)
     * @param callback completion function, or null
     */
    public abstract void callUIMethod(Function method, List params,
        Completion callback);

    /**
     * Create a ContextAction which does the work of calling a method,
     * translating the result, and invoking the callback. This is a utility
     * method which should be used by the callUIMethod() implementation.
     */
    public ContextAction uiMethodAction(final ScriptableObject global,
        final Function method, final List params, final Completion callback) {

        return new ContextAction() {
                public Object run(Context context) {
                    /* If we haven't already, put an RPCWrapFactory around
                     * whatever WrapFactory exists on the context. (We don't
                     * want to give up the BatikWrapFactory which exists on
                     * Batik contexts.) */
                    WrapFactory wrapper = context.getWrapFactory();
                    if (!(wrapper instanceof GameUI.RPCWrapFactory)) {
                        wrapper = new GameUI.RPCWrapFactory(wrapper);
                        context.setWrapFactory(wrapper);
                    }

                    Object ret = method.call(context,
                        global, global, params.toArray());
                    if (ret == null || ret instanceof Undefined) {
                        // function returned void, but RPC result has to be non-void
                        ret = Boolean.TRUE;
                    }
                    if (callback != null) 
                        callback.result(ret);
                                    
                    return null;
                }
            };    
    }

    /**
     * This exists only to support assertions and paranoia checks. We like
     * those, especially in Testbench. Call this before executing any script
     * code, in the thread where it will be executed.
     */
    public void beginScriptCode() {
        synchronized (callInProgressLock) {
            if (callInProgress == Boolean.TRUE)
                throw new AssertionError("Tried to run two ECMAScript calls at the same time");
            callInProgress = Boolean.TRUE;
        }
    }

    /**
     * Call this after executing any script code.
     */
    public void endScriptCode() {
        synchronized (callInProgressLock) {
            callInProgress = Boolean.FALSE;
        }
    }

    /**
     * Initialize game-handling objects. Call this inside a ContextAction
     * when setting up the Interpreter.
     *
     * @param context a context to run in.
     * @param scope the scope to initialize. May not be null.
     */
    public void initGameObjects(Context context, ScriptableObject scope) {
        try {
            this.scope = scope;
            scope.put("game", scope, game = context.newObject(scope));
            scope.put("volity", scope, volity = context.newObject(scope));
            scope.put("info", scope, info = new Info());
            scope.put("metadata", scope, new MetadataObj());
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
                                throw new Exception("literalmessage() requires one argument");
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
                        try {
                            Map map = GameUI.parseSeatMarkArgs(args);
                            String msg = "[Seat mark change: ";
                            int count = 0;
                            for (Iterator it = map.entrySet().iterator();
                                 it.hasNext(); ) {
                                Map.Entry ent = (Map.Entry)it.next();
                                if (count > 0)
                                    msg = msg + ", ";
                                msg = msg + ent.getKey().toString();
                                msg = msg + ": ";
                                msg = msg + ent.getValue().toString();
                                count++;
                            }
                            if (count == 0)
                                msg = msg + "(none)";
                            msg = msg + "]";
                            messageHandler.print(msg);
                            return null;
                        } catch (Exception ex) {
                            errorHandler.error(ex);
                            return null;
                        }
                    }
                });
            scope.put("audio", scope, 
                GameUI.UIAudio.makeCallableProperty(this, baseURL, 
                    messageHandler, errorHandler));

            /* If you add more identifiers to scope, be sure to delete them in
             * the stop() method. */

        } catch (Exception ex) {
            errorHandler.error(ex);
        }
    }

    /**
     * Cease work.
     */
    public void stop() {
        // Cut off any playing sounds which have this UI for an owner.
        Audio.stopGroup(this);

        seatObjects.clear();

        if (scope != null) {
            scope.delete("game");
            scope.delete("volity");
            scope.delete("info");
            scope.delete("metadata");
            scope.delete("rpc");
            scope.delete("literalmessage");
            scope.delete("localize");
            scope.delete("message");
            scope.delete("seatmark");
            scope.delete("audio");
            scope = null;
        }
        game = null;
        volity = null;
        info = null;
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

    /**
     * Convert a list of arguments for rpc() into a string.
     */
    protected String prettifyParams(Object[] args) {
        StringBuffer buf = new StringBuffer();

        String methname = args[0].toString();
        if (methname.indexOf('.') < 0)
            methname = "game."+methname;
        buf.append(methname);

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
        private Callable funcVersionMatch;

        {
            defineProperty("version", Info.class, PERMANENT);
            defineProperty("state", Info.class, PERMANENT);
            defineProperty("recovery", Info.class, PERMANENT);
            defineProperty("nickname", Info.class, PERMANENT);
            defineProperty("seat", Info.class, PERMANENT);
            defineProperty("allseats", Info.class, PERMANENT);
            defineProperty("gameseats", Info.class, PERMANENT);
            defineProperty("ruleset", Info.class, PERMANENT);
            defineProperty("versionmatch", Info.class, PERMANENT);

            funcVersionMatch = new GameUI.Callback() {
                    public Object run(Object[] args) {
                        if (args.length != 2)
                            throw new RuntimeException("versionmatch() requires two arguments");
                        VersionNumber vnum;
                        VersionSpec vspec;
                        try {
                            vnum = new VersionNumber(args[0].toString());
                        }
                        catch (Exception ex) {
                            throw new RuntimeException(ex.getMessage());
                        }
                        try {
                            vspec = new VersionSpec(args[1].toString());
                        }
                        catch (Exception ex) {
                            throw new RuntimeException(ex.getMessage());
                        }
                        boolean result = vspec.matches(vnum);
                        return new Boolean(result);
                    }
                };
        }

        public String getClassName() { return "Info"; }
        public Object getDefaultValue(Class typeHint) { return toString(); }

        public String getVersion() {
            return sUIVersion.toString();
        }
        public String getState() {
            return null; //### track "ref" state
        }
        public Boolean getRecovery() {
            return Boolean.FALSE; //### track?
        }
        public String getRuleset() {
            return metadata.get(Metadata.VOLITY_RULESET);
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
        public Object getAllseats() {
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
        public Object getGameseats() {
            return getAllseats();
        }
        public Callable getVersionmatch() {
            return funcVersionMatch;
        }
    }

    class MetadataObj extends ScriptableObject {
        private Callable funcGet;
        private Callable funcGetall;

        {
            defineProperty("get", MetadataObj.class, PERMANENT);
            defineProperty("getall", MetadataObj.class, PERMANENT);

            funcGet = new GameUI.Callback() {
                    public Object run(Object[] args) {
                        if (args.length != 2 && args.length != 3)
                            throw new RuntimeException("get() requires two or three arguments");
                        Object uristr = args[0];
                        String label = args[1].toString();
                        Object defaultval = null;
                        if (args.length == 3)
                            defaultval = args[2];

                        Metadata data;
                        URI uri = null;
                        if (!(uristr == null || uristr instanceof Undefined)) {
                            try {
                                uri = new URI(uristr.toString());
                            }
                            catch (Exception ex) { }
                        }
                        if (uri == null)
                            data = metadata;
                        else
                            data = metadata.getResource(uri);
                        if (data == null)
                            return null;
                        Object res = data.get(GameUI.expandKey(label), TranslateToken.getLanguage());
                        if (res == null)
                            res = defaultval;
                        return res;
                    }
                };

            funcGetall = new GameUI.Callback() {
                    public Object run(Object[] args) {
                        if (args.length != 2)
                            throw new RuntimeException("getall() requires two arguments");
                        Object uristr = args[0];
                        String label = args[1].toString();

                        Metadata data;
                        URI uri = null;
                        if (!(uristr == null || uristr instanceof Undefined)) {
                            try {
                                uri = new URI(uristr.toString());
                            }
                            catch (Exception ex) { }
                        }
                        if (uri == null)
                            data = metadata;
                        else
                            data = metadata.getResource(uri);
                        List res = data.getAll(GameUI.expandKey(label));

                        Context context = Context.getCurrentContext();
                        Scriptable ls = context.newArray(scope, 0);
                        for (int ix=0; ix<res.size(); ix++) {
                            ls.put(ix, ls, res.get(ix));
                        }
                        return ls;
                    }
                };
        }

        public String getClassName() { return "Metadata"; }
        public Object getDefaultValue(Class typeHint) { return toString(); }

        public Callable getGet() {
            return funcGet;
        }
        public Callable getGetall() {
            return funcGetall;
        }
    }

    class UISeat extends ScriptableObject {
        {
            defineProperty("players", UISeat.class, PERMANENT);
            defineProperty("nicknames", UISeat.class, PERMANENT);
        }

        protected String id;

        public UISeat(String id) {
            this.id = id;
        }

        public String getClassName() { return "Seat"; }
        public Object getDefaultValue(Class typeHint) { return id; }

        public Object getPlayers() {
            Context context = Context.getCurrentContext();
            Scriptable ls = context.newArray(scope, 0);
            ls.put(0, ls, id+"@testbench/app");
            if (currentSeat != null && id.equals(currentSeat)) {
                ls.put(1, ls, "you@testbench/testbench");
            }
            return ls;
        }

        public Object getNicknames() {
            Context context = Context.getCurrentContext();
            Scriptable ls = context.newArray(scope, 0);
            ls.put(0, ls, id);
            if (currentSeat != null && id.equals(currentSeat)) {
                ls.put(1, ls, "you");
            }
            return ls;
        }
    }

}
