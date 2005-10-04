package org.volity.client;

import java.awt.Dimension;
import java.awt.geom.Dimension2D;
import java.net.URL;
import java.util.List;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.UpdateManager;
import org.apache.batik.script.Interpreter;
import org.apache.batik.script.InterpreterFactory;
import org.apache.batik.script.InterpreterPool;
import org.apache.batik.script.rhino.RhinoInterpreter;
import org.apache.batik.swing.gvt.GVTTreeRendererAdapter;
import org.apache.batik.swing.gvt.GVTTreeRendererEvent;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.svg.GVTTreeBuilderAdapter;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;
import org.jivesoftware.smack.XMPPConnection;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.volity.jabber.RPCResponseHandler;

public class SVGCanvas extends JSVGCanvas 
    implements InterpreterFactory
{
    XMPPConnection connection;
    GameTable table;
    TranslateToken translator;
    GameUI.MessageHandler messageHandler;
    GameUI.ErrorHandler errorHandler;

    SVGUI ui;
    RhinoInterpreter interpreter;
    boolean stopped = false;

    /**
     * @param table represents a game table (MUC)
     * @param uiDocument the top-level SVG document
     * @param translator service to translate tokens into a string
     * @param messageHandler service to display a string to the user
     * @param errorHandler service to display an exception to the user
     */
    public SVGCanvas(GameTable table, URL uiDocument,
        TranslateToken translator,
        GameUI.MessageHandler messageHandler, 
        GameUI.ErrorHandler errorHandler) {
        this(table.getConnection(), uiDocument, translator,
            messageHandler, errorHandler);
        this.table = table;
    }
    
    /**
     * @param connection Jabber connection
     * @param uiDocument the top-level SVG document
     * @param translator service to translate tokens into a string
     * @param messageHandler service to display a string to the user
     */
    public SVGCanvas(XMPPConnection connection, URL uiDocument,
        TranslateToken translator,
        GameUI.MessageHandler messageHandler,
        GameUI.ErrorHandler errorHandler) {

        super();
        this.connection = connection;
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
    class GameUIInterpreter extends RhinoInterpreter {
      GameUIInterpreter() {
        super(documentURL);
        ui = new SVGUI();
        /* This is kind of pathetic, but it's possible for an SVGCanvas to
         * reach the createInterpreter stage after the table window has already
         * closed. We have to continue on, but we don't want to keep a live
         * responder in the UI. Therefore, we create a UI and immediately stop
         * it.
         * (Test case: create a new table, requesting nickname "referee".) */
        if (stopped)
          ui.stop();
        ui.initGameObjects(getGlobalObject());
        if (table != null) ui.setTable(table);
      }
    }
    return interpreter = new GameUIInterpreter();
  }

  // Inherited from InterpreterFactory.
  public String getMimeType() {
    return "image/svg+xml";
  } 

  // The window containing this has closed. We need to do some cleanup.
  public void stop() {
    stopped = true;
    if (ui != null)
      ui.stop();
  }
  
  public GameUI getUI() { return ui; }

  class SVGUI extends GameUI {
    SVGUI() {
      super(connection, SVGCanvas.this.translator, 
        SVGCanvas.this.messageHandler, SVGCanvas.this.errorHandler);
    }

    public void handleRPC(final String methodName,
                          final List params,
                          final RPCResponseHandler k)
    {
      // Handle the request in the UpdateManager's thread, so the
      // display will be repainted correctly.
      getUpdateManager().getUpdateRunnableQueue().invokeLater(new Runnable() {
          public void run() {
            // We don't need to call interpreter.enterContext() here, because
            // super.handleRPC calls around to callUIMethod, which does it.
            // That's confusing, but it suffices.
            // Since this is inside an invokeLater, we can't let exceptions
            // escape -- they'll just slam into a Batik thread and fall on
            // the floor (i.e., on stdout).
            try {
              SVGUI.super.handleRPC(methodName, params, k);
            }
            catch (Exception ex) {
              errorHandler.error(ex);                
            }
          }
        });
    }

    public Object callUIMethod(Function method, List params)
      throws JavaScriptException
    {
      try {
	// Make sure we use the interpreter's context rather than one
	// returned by Context.enter() in a new thread, because it
	// needs to be a special subclass of Context.
	interpreter.enterContext();
	return super.callUIMethod(method, params);
      } finally {
	Context.exit();
      }
    }
  }

  /**
   * Set the table where this game is being played.
   */
  public void setTable(GameTable table) {
    this.table = table;
    if (ui != null) ui.setTable(table);
  }
}
