package org.volity.client;

import java.awt.Dimension;
import java.awt.geom.Dimension2D;
import java.net.URI;
import java.net.URL;
import java.util.List;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.UpdateManager;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.svg12.SVG12BridgeContext;
import org.apache.batik.dom.svg.SVGOMDocument;
import org.apache.batik.script.Interpreter;
import org.apache.batik.script.InterpreterException;
import org.apache.batik.script.InterpreterFactory;
import org.apache.batik.script.InterpreterPool;
import org.apache.batik.script.rhino.RhinoInterpreter;
import org.apache.batik.script.rhino.svg12.SVG12RhinoInterpreter;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.gvt.GVTTreeRendererAdapter;
import org.apache.batik.swing.gvt.GVTTreeRendererEvent;
import org.apache.batik.swing.svg.SVGUserAgentGUIAdapter;
import org.apache.batik.util.RunnableQueue;
import org.jivesoftware.smack.XMPPConnection;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.volity.client.data.Metadata;
import org.volity.client.translate.TranslateToken;

public class SVGCanvas extends JSVGCanvas 
    implements InterpreterFactory
{
    URI ruleset;
    URL uiDocument;
    XMPPConnection connection;
    GameTable table;
    TranslateToken translator;
    GameUI.MessageHandler messageHandler;
    GameUI.ErrorHandler errorHandler;
    LinkHandler linkHandler;

    SVGUI ui;
    ScriptableObject uiGlobalObject;
    RhinoInterpreter interpreter;
    boolean stopped = false;

    /**
     * @param table represents a game table (MUC)
     * @param uiDocument the top-level SVG document
     * @param translator service to translate tokens into a string
     * @param messageHandler service to display a string to the user
     * @param errorHandler service to display an exception to the user
     */
    public SVGCanvas(GameTable table, URI ruleset, URL uiDocument,
        TranslateToken translator,
        GameUI.MessageHandler messageHandler, 
        GameUI.ErrorHandler errorHandler,
        LinkHandler linkHandler) {
        this(table.getConnection(), ruleset, uiDocument, translator,
            messageHandler, errorHandler, linkHandler);
        this.table = table;
    }
    
    /**
     * @param connection Jabber connection
     * @param uiDocument the top-level SVG document
     * @param translator service to translate tokens into a string
     * @param messageHandler service to display a string to the user
     */
    public SVGCanvas(XMPPConnection connection, URI ruleset, URL uiDocument,
        TranslateToken translator,
        GameUI.MessageHandler messageHandler,
        GameUI.ErrorHandler errorHandler,
        LinkHandler linkHandler) {

        super(new SVGUserAgentJavolin(linkHandler), true, true);

        this.ruleset = ruleset;
        this.uiDocument = uiDocument;
        this.connection = connection;
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.linkHandler = linkHandler;
        this.translator = translator;
        setDocumentState(ALWAYS_DYNAMIC);

        // Used to be important, when forceRedraw did something.
        /*
        addGVTTreeRendererListener(new GVTTreeRendererAdapter() {
                public void gvtRenderingCompleted(GVTTreeRendererEvent evt) {
                    forceRedraw();
                }
            });
        */

        // Load the UI. (We do this after the listener is set up.)
        setURI(uiDocument.toString());
    }

    /**
     * Interface for a service which handles the activation of a hyperlink. 
     */
    public interface LinkHandler {
        public abstract void link(String uri);
    }
  
    protected static class SVGUserAgentJavolin extends SVGUserAgentGUIAdapter {
        protected LinkHandler linkHandler;
        public SVGUserAgentJavolin(LinkHandler linkHandler) {
            super(null);
            this.linkHandler = linkHandler;
        }
        public String getDefaultFontFamily() {
            // Copied from AbstractJSVGComponent
            return "Arial, Helvetica, sans-serif";
        }
        public void openLink(String uri, boolean newc) {
            if (linkHandler != null)
                linkHandler.link(uri);
        }
    }

    /**
     * Override the creation of a UserAgent. This lets us customize the
     * handling of SVG errors. (Possibly this could be subsumed into the
     * SVGUserAgent class, but I haven't done it.)
     */
    protected UserAgent createUserAgent() {
        UserAgent agent = new UserAgentJavolin();
        return agent;
    }

    /**
     * Define a class which customizes the handling of SVG errors. Instead of
     * popping up Batik's standard ugly dialog box, we pass the buck to our own
     * handlers.
     */
    protected class UserAgentJavolin extends JSVGCanvas.CanvasUserAgent {
        public UserAgentJavolin() {
        }
        public void displayError(String msg) {
            messageHandler.print(msg);
        }
        public void displayError(Exception ex) {
            errorHandler.error(ex);
        }
    }

    /**
     * Overrides JSVGCanvas.installKeyboardActions, which wants to install a
     * bunch of key actions like panning the canvas on arrow keys, or zooming
     * on ctrl-I/O. We don't want any of that.
     */
    protected void installKeyboardActions() {
    }

    /**
     * Kludge (or maybe it's just a clever way) to force the canvas to reload
     * the SVG file from disk. If newDocument is non-null, reload that instead.
     */
    public void reloadUI(URL newDocument) {
        if (newDocument != null)
            uiDocument = newDocument;

        if (ui != null) {
            ui.stop();
            ui = null;
        }
        setURI(uiDocument.toString());
    }

    /**
     * Kludge to force the component to redraw itself.
     *
     * This was necessary due to bugs in the original Batik 1.6 release. These
     * bugs are fixed in the Batik release we are now using. I am leaving this
     * routine in place, as a stub, in case I'm wrong.
     */
    public void forceRedraw() {
        // Do nothing.
    }  

    // Inherited from AbstractJSVGComponent.
    protected BridgeContext createBridgeContext(SVGOMDocument doc) {
        InterpreterPool pool = new InterpreterPool();
        pool.putInterpreterFactory("text/ecmascript", this);
        BridgeContext context = super.createBridgeContext(doc);
        // This doesn't work, because setInterpreterPool is protected:
        //
        // context.setInterpreterPool(pool);
        // return context;
        //
        // Instead, make a new context.
        BridgeContext newContext;
        if (doc.isSVG12()) {
            newContext = new SVG12BridgeContext(context.getUserAgent(),
                pool, context.getDocumentLoader());
        }
        else {
            newContext = new BridgeContext(context.getUserAgent(),
                pool, context.getDocumentLoader());
        }
        newContext.setDynamic(true);
        return newContext;
    }

    // Inherited from InterpreterFactory.
    public Interpreter createInterpreter(final URL documentURL, boolean isSVG12) {
        // We need to add our game objects to the interpreter's global
        // object, but RhinoInterpreter.getGlobalObject is protected, so
        // we have to make a subclass.  And it can't be anonymous because
        // we need to override the constructor.

        if (!isSVG12) {
            class GameUIInterpreter extends RhinoInterpreter {
                GameUIInterpreter() {
                    super(documentURL);
                    ui = new SVGUI();
                    /* This is kind of pathetic, but it's possible for an
                     * SVGCanvas to reach the createInterpreter stage after the
                     * table window has already closed. We have to continue on,
                     * but we don't want to keep a live responder in the UI.
                     * Therefore, we create a UI and immediately stop it. (Test
                     * case: create a new table, requesting nickname
                     * "referee".) */
                    uiGlobalObject = getGlobalObject();
                    contextFactory.call(new ContextAction() {
                            public Object run(Context cx) {
                                ui.initGameObjects(cx, uiGlobalObject);
                                return null;
                            }
                        });
                    if (stopped)
                        ui.stop();
                    if (table != null)
                        ui.setTable(table);
                }
            }
            interpreter = new GameUIInterpreter();
        }
        else {
            class GameUI12Interpreter extends SVG12RhinoInterpreter {
                GameUI12Interpreter() {
                    super(documentURL);
                    ui = new SVGUI();
                    /* This is kind of pathetic, but it's possible for an
                     * SVGCanvas to reach the createInterpreter stage after the
                     * table window has already closed. We have to continue on,
                     * but we don't want to keep a live responder in the UI.
                     * Therefore, we create a UI and immediately stop it. (Test
                     * case: create a new table, requesting nickname
                     * "referee".) */
                    uiGlobalObject = getGlobalObject();
                    contextFactory.call(new ContextAction() {
                            public Object run(Context cx) {
                                ui.initGameObjects(cx, uiGlobalObject);
                                return null;
                            }
                        });
                    if (stopped)
                        ui.stop();
                    if (table != null)
                        ui.setTable(table);
                }
            }
            interpreter = new GameUI12Interpreter();
        }

        return interpreter;
    }

    // Inherited from InterpreterFactory.
    public String getMimeType() {
        return "image/svg+xml";
    }

    // Inherited from InterpreterFactory.
    public String[] getMimeTypes() {
        String[] mimeTypes = {"image/svg+xml"};
        return mimeTypes;
    }

    // The window containing this has closed. We need to do some cleanup.
    public void stop() {
        stopped = true;
        if (ui != null) {
            ui.stop();
            ui = null;
        }
        table = null;
        interpreter = null;

        // The dispose() call should (eventually) shut down all Batik activity.
        stopProcessing();
        dispose();
    }
  
    public GameUI getUI() { return ui; }

    class SVGUI extends GameUI {
        SVGUI() {
            super(SVGCanvas.this.ruleset, uiDocument, 
                connection, SVGCanvas.this.translator, 
                SVGCanvas.this.messageHandler, SVGCanvas.this.errorHandler);
        }

        /**
         * Implementation of GameUI.loadMetadata. This performs the appropriate
         * parsing for an SVG file.
         */
        public Metadata loadMetadata() {
            try {
                return Metadata.parseSVGMetadata(baseURL);
            }
            catch (Exception ex) {
                errorHandler.error(ex);
                return new Metadata();
            }
        }

        /**
         * Customization of GameUI.callUIMethod. The Batik library requires
         * that we invoke ECMAScript from a Batik thread. Therefore, all
         * callUIMethod calls have to invoke the Batik thread before they can
         * do work.
         *
         * Note that handleRPC calls callUIMethod, so it's covered by this
         * wrapper too.
         * 
         * Also note that by queueing everything in the Batik RunnableQueue, we
         * ensure that all ECMAScript calls are serialized. This is a very good
         * thing -- otherwise the UI code would have to deal with concurrency
         * issues. (And then UI authors would come after us with pitchforks.)
         */
        public void callUIMethod(final Function method, final List params, 
            final Completion callback)
        {
            RunnableQueue rq = getUpdateManager().getUpdateRunnableQueue();

            rq.invokeLater(new Runnable() {
                    public void run() {        
                        if (interpreter == null) {
                            callback.error(new NullPointerException("interpreter is stopped."));
                            return;
                        }
                        ContextAction action = uiMethodAction(uiGlobalObject,
                            method, params, callback);
                        try {
                            interpreter.getContextFactory().call(action);
                        }
                        catch (InterpreterException ex) {
                            Exception wrapex = ex.getException();
                            if (wrapex == null)
                                wrapex = ex;
                            errorHandler.error(wrapex);
                        }
                    }
                });
        }
    }
    
    /**
     * Set the table where this game is being played.
     */
    public void setTable(GameTable table) {
        this.table = table;
        if (ui != null)
            ui.setTable(table);
    }
}
