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
import org.apache.batik.script.InterpreterFactory;
import org.apache.batik.script.InterpreterPool;
import org.apache.batik.script.rhino.RhinoInterpreter;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.gvt.GVTTreeRendererAdapter;
import org.apache.batik.swing.gvt.GVTTreeRendererEvent;
import org.apache.batik.util.RunnableQueue;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.volity.client.TranslateToken;

/**
 * This class is analogous to SVGCanvas. It handles the SVG pane in Testbench.
 */
public class SVGTestCanvas extends JSVGCanvas 
    implements InterpreterFactory
{
    URL uiDocument;
    DebugInfo debugInfo;
    TranslateToken translator;
    TestUI.MessageHandler messageHandler;
    TestUI.ErrorHandler errorHandler;

    public SVGTestCanvas(URL uiDocument,
        DebugInfo debugInfo,
        TranslateToken translator,
        TestUI.MessageHandler messageHandler,
        TestUI.ErrorHandler errorHandler) {

        super();
        //### if we passed a SVGUserAgent here, we could override the standard
        //### exception-alert-box behavior.

        this.uiDocument = uiDocument;
        this.debugInfo = debugInfo;
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.translator = translator;

        setDocumentState(ALWAYS_DYNAMIC);
        setURI(uiDocument.toString());

        addGVTTreeRendererListener(new GVTTreeRendererAdapter() {
                public void gvtRenderingCompleted(GVTTreeRendererEvent evt) {
                    forceRedraw();
                }
            });
    }

    /**
     * Expose the UserAgent so that the app can throw up exception dialog
     * boxes. This may not be the clean way to do this, but it works. 
     */
    public UserAgent getUserAgent() {
        return userAgent;
    }

    /**
     * Kludge (or maybe it's just a clever way) to force the canvas to reload
     * the SVG file from disk.
     */
    public void reloadUI(DebugInfo debugInfo) {
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
        /* To accept SVG12 documents, we'd need to create an
         * SVG12RhinoInterpreter subclass instead of a RhinoInterpreter
         * subclass. */
        assert (!isSVG12);

        // We need to add our game objects to the interpreter's global
        // object, but RhinoInterpreter.getGlobalObject is protected, so
        // we have to make a subclass.  And it can't be anonymous because
        // we need to override the constructor.
        class TestUIInterpreter extends RhinoInterpreter {
            TestUIInterpreter() {
                super(documentURL);
                ui = new SVGUI();
                // Will need the security domain to execute debug scripts.
                ui.setSecurityDomain(rhinoClassLoader);
                ui.initGameObjects(getGlobalObject());
                for (Iterator it = listeners.iterator(); it.hasNext(); ) {
                    ((UIListener) it.next()).newUI(ui);
                }
            }
        }
        return interpreter = new TestUIInterpreter();
    }
  
    // Inherited from InterpreterFactory.
    public String getMimeType() {
        return "image/svg+xml";
    } 
    
    SVGUI ui;
    RhinoInterpreter interpreter;
    
    public TestUI getUI() { return ui; }

    class SVGUI extends TestUI {
        SVGUI() {
            super(SVGTestCanvas.this.translator, 
                SVGTestCanvas.this.messageHandler,
                SVGTestCanvas.this.errorHandler);
        }

        public DebugInfo getDebugInfo() {
            return SVGTestCanvas.this.debugInfo;
        }

        public void loadString(final String uiScript, final String scriptLabel) {
            // Handle the request in the UpdateManager's thread, so the
            // display will be repainted correctly.

            RunnableQueue rq = getUpdateManager().getUpdateRunnableQueue();

            rq.invokeLater(new Runnable() {
                    // Make sure we use the interpreter's context rather than
                    // one returned by Context.enter() in a new thread, because
                    // it needs to be a special subclass of Context.
                    public void run() {
                        try {
                            interpreter.enterContext();
                            SVGUI.super.loadString(uiScript, scriptLabel);
                        }
                        finally {
                            Context.exit();
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
                    // Make sure we use the interpreter's context rather than
                    // one returned by Context.enter() in a new thread, because
                    // it needs to be a special subclass of Context.
                    public void run() {
                        try {
                            interpreter.enterContext();
                            SVGUI.super.callUIMethod(method, params, callback);
                        }
                        finally {
                            Context.exit();
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
