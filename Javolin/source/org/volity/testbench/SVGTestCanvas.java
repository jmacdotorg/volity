package org.volity.testbench;

import java.awt.Dimension;
import java.awt.geom.Dimension2D;
import java.net.URL;
import java.util.*;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.UpdateManager;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.script.Interpreter;
import org.apache.batik.script.InterpreterFactory;
import org.apache.batik.script.InterpreterPool;
import org.apache.batik.script.rhino.RhinoInterpreter;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.gvt.GVTTreeRendererAdapter;
import org.apache.batik.swing.gvt.GVTTreeRendererEvent;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.volity.client.TranslateToken;

public class SVGTestCanvas extends JSVGCanvas 
    implements InterpreterFactory
{
    URL uiDocument;
    TranslateToken translator;
    TestUI.MessageHandler messageHandler;

    public SVGTestCanvas(URL uiDocument,
        TranslateToken translator,
        TestUI.MessageHandler messageHandler) {
        super();

        this.uiDocument = uiDocument;
        this.messageHandler = messageHandler;
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
    public void reloadUI() {
        setURI(uiDocument.toString());
    }

    /**
     * Kludge to force the component to redraw itself.
     */
    public void forceRedraw() {
	Dimension2D size = getSVGDocumentSize();
	setSize(new Dimension((int)(size.getWidth() + 10), (int)(size.getHeight() + 10)));
	revalidate();
    }  

    // Inherited from JSVGComponent.
    protected BridgeContext createBridgeContext() {
        InterpreterPool pool = new InterpreterPool();
        pool.putInterpreterFactory("text/ecmascript", this);
        BridgeContext context = super.createBridgeContext();
        // This doesn't work, because setInterpreterPool is protected:
        //
        // context.setInterpreterPool(pool);
        // return context;
        //
        // Instead, make a new context.
        BridgeContext newContext =
            new BridgeContext(context.getUserAgent(),
                pool,
                context.getDocumentLoader());
        newContext.setDynamic(true);
        return newContext;
    }

    // Inherited from InterpreterFactory.
    public Interpreter createInterpreter(final URL documentURL) {
        // We need to add our game objects to the interpreter's global
        // object, but RhinoInterpreter.getGlobalObject is protected, so
        // we have to make a subclass.  And it can't be anonymous because
        // we need to override the constructor.
        class TestUIInterpreter extends RhinoInterpreter {
            TestUIInterpreter() {
                super(documentURL);
                ui = new SVGUI();
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
                SVGTestCanvas.this.messageHandler);
        }

        public Object callUIMethod(Function method, List params)
            throws JavaScriptException {
            try {
                // Make sure we use the interpreter's context rather than one
                // returned by Context.enter() in a new thread, because it
                // needs to be a special subclass of Context.
                interpreter.enterContext();
                return super.callUIMethod(method, params);
            } 
            finally {
                Context.exit();
            }
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
