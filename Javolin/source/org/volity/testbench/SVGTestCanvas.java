package org.volity.testbench;

import java.awt.Dimension;
import java.awt.geom.Dimension2D;
import java.net.URL;
import java.util.*;
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
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.volity.client.GameUI;
import org.volity.client.data.Metadata;
import org.volity.client.translate.TranslateToken;

/**
 * This class is analogous to SVGCanvas. It handles the SVG pane in Testbench.
 */
public class SVGTestCanvas extends JSVGCanvas 
    implements InterpreterFactory
{
    URL uiDocument;
    DebugInfo debugInfo;
    TranslateToken translator;
    GameUI.MessageHandler messageHandler;
    TestUI.ErrorHandler errorHandler;

    public SVGTestCanvas(URL uiDocument,
        DebugInfo debugInfo,
        TranslateToken translator,
        GameUI.MessageHandler messageHandler,
        TestUI.ErrorHandler errorHandler) {

        super(new SVGUserAgentJavolin(messageHandler), true, true);

        this.uiDocument = uiDocument;
        this.debugInfo = debugInfo;
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.translator = translator;

        setDocumentState(ALWAYS_DYNAMIC);
        setURI(uiDocument.toString());

        // Used to be important, when forceRedraw did something.
        /*
        addGVTTreeRendererListener(new GVTTreeRendererAdapter() {
                public void gvtRenderingCompleted(GVTTreeRendererEvent evt) {
                    forceRedraw();
                }
            });
        */
    }

    /**
     * A custom SVGUserAgent class.
     */
    protected static class SVGUserAgentJavolin extends SVGUserAgentGUIAdapter {
        protected GameUI.MessageHandler messageHandler;
        public SVGUserAgentJavolin(GameUI.MessageHandler messageHandler) {
            super(null);
            this.messageHandler = messageHandler;
        }
        public String getDefaultFontFamily() {
            // Copied from AbstractJSVGComponent
            return "Arial, Helvetica, sans-serif";
        }
        public void openLink(String uri, boolean newc) {
            messageHandler.print("Clicked hyperlink: " + uri);
        }
    }

    /**
     * Override the creation of a UserAgent. This lets us customize the
     * handling of SVG errors. (Possibly this could be subsumed into the
     * SVGUserAgent class, but I haven't done it.)
     */
    protected UserAgent createUserAgent() {
        /**
         * Define an anonymous class which customizes the handling of SVG
         * errors. Instead of popping up Batik's standard ugly dialog box, we
         * pass the buck to our own handlers.
         */
        UserAgent agent = new JSVGCanvas.CanvasUserAgent () {
                public void displayError(String msg) {
                    SVGTestCanvas.this.messageHandler.print(msg);
                }
                public void displayError(Exception ex) {
                    SVGTestCanvas.this.errorHandler.error(ex);
                }
            };
        return agent;
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
     * the SVG file from disk.
     */
    public void reloadUI(DebugInfo debugInfo) {
        if (ui != null)
            ui.stop();
        this.debugInfo = debugInfo;
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

    // Inherited from JSVGComponent.
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
            class TestUIInterpreter extends RhinoInterpreter {
                TestUIInterpreter() {
                    super(documentURL);
                    ui = new SVGUI(documentURL);
                    uiGlobalObject = getGlobalObject();
                    contextFactory.call(new ContextAction() {
                            public Object run(Context cx) {
                                ui.initGameObjects(cx, uiGlobalObject);
                                return null;
                            }
                        });
                    for (Iterator it = listeners.iterator(); it.hasNext(); ) {
                        ((UIListener) it.next()).newUI(ui);
                    }
                }
            }
            interpreter = new TestUIInterpreter();
        }
        else {
            class TestUI12Interpreter extends SVG12RhinoInterpreter {
                TestUI12Interpreter() {
                    super(documentURL);
                    ui = new SVGUI(documentURL);
                    uiGlobalObject = getGlobalObject();
                    contextFactory.call(new ContextAction() {
                            public Object run(Context cx) {
                                ui.initGameObjects(cx, uiGlobalObject);
                                return null;
                            }
                        });
                    for (Iterator it = listeners.iterator(); it.hasNext(); ) {
                        ((UIListener) it.next()).newUI(ui);
                    }
                }
            }
            interpreter = new TestUI12Interpreter();
        }

        return interpreter;
    }
  
    // Inherited from InterpreterFactory.
    public String getMimeType() {
        return "image/svg+xml";
    } 
    
    SVGUI ui;
    ScriptableObject uiGlobalObject;
    RhinoInterpreter interpreter;
    
    public TestUI getUI() { return ui; }

    class SVGUI extends TestUI {
        SVGUI(URL baseurl) {
            super(baseurl,
                SVGTestCanvas.this.translator, 
                SVGTestCanvas.this.messageHandler,
                SVGTestCanvas.this.errorHandler);
        }

        /**
         * Implementation of TestUI.loadMetadata. This performs the appropriate
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

        public DebugInfo getDebugInfo() {
            return SVGTestCanvas.this.debugInfo;
        }

        public void loadString(final String uiScript, final String scriptLabel) {
            // Handle the request in the UpdateManager's thread, so the
            // display will be repainted correctly.

            RunnableQueue rq = getUpdateManager().getUpdateRunnableQueue();

            rq.invokeLater(new Runnable() {
                    public void run() {
                        try {
                            beginScriptCode();
                            interpreter.evaluate(uiScript);
                        }
                        catch (InterpreterException ex) {
                            Exception wrapex = ex.getException();
                            if (wrapex == null)
                                wrapex = ex;
                            errorHandler.error(wrapex, scriptLabel + " failed");
                        }
                        finally {
                            endScriptCode();
                        }
                    }
                });
        }

        public void callUIMethod(final Function method, final List params,
            final Completion callback) {
            // Handle the request in the UpdateManager's thread, so the
            // display will be repainted correctly.

            RunnableQueue rq = getUpdateManager().getUpdateRunnableQueue();

            rq.invokeLater(new Runnable() {
                    public void run() {
                        ContextAction action = uiMethodAction(uiGlobalObject,
                            method, params, callback);
                        try {
                            beginScriptCode();
                            interpreter.getContextFactory().call(action);
                        }
                        catch (InterpreterException ex) {
                            Exception wrapex = ex.getException();
                            if (wrapex == null)
                                wrapex = ex;
                            errorHandler.error(wrapex);
                        }
                        finally {
                            endScriptCode();
                        }
                    }
                });
        }
    }

    public interface UIListener {
        public abstract void newUI(TestUI ui);
    }

    List listeners = new ArrayList();

    public void addUIListener(UIListener listener) {
        listeners.add(listener);
        if (ui != null)
            listener.newUI(ui);
    }

}
