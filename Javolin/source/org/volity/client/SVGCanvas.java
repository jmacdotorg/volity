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
  implements InterpreterFactory, GameUI.ErrorHandler
{
  public SVGCanvas(GameTable table, URL uiDocument) {
    this(table.getConnection(), uiDocument);
    this.table = table;
  }
  public SVGCanvas(XMPPConnection connection, URL uiDocument) {
    super();
    this.connection = connection;
    setDocumentState(ALWAYS_DYNAMIC);
    setURI(uiDocument.toString());
    addGVTTreeRendererListener(new GVTTreeRendererAdapter() {
	public void gvtRenderingCompleted(GVTTreeRendererEvent evt) {
	  Dimension2D size = getSVGDocumentSize();
	  setSize(new Dimension((int)(size.getWidth() + 10),
	                        (int)(size.getHeight() + 10)));
	  revalidate();
	}
      });
  }

  XMPPConnection connection;
  GameTable table;

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
	ui.initGameObjects(getGlobalObject());
	if (table != null) ui.setTable(table);
      }
    }
    return interpreter = new GameUIInterpreter();
  }

  SVGUI ui;
  RhinoInterpreter interpreter;

  class SVGUI extends GameUI {
    SVGUI() {
      super(connection, SVGCanvas.this);
    }
    public void handleRPC(final String methodName,
			  final List params,
			  final RPCResponseHandler k)
    {
      // Handle the request in the UpdateManager's thread, so the
      // display will be repainted correctly.
      getUpdateManager().getUpdateRunnableQueue().invokeLater(new Runnable() {
	  public void run() {
	    SVGUI.super.handleRPC(methodName, params, k);
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

  // Inherited from GameUI.ErrorHandler.
  public void error(Exception e) {
    userAgent.displayError(e);
  }

  /**
   * Set the table where this game is being played.
   */
  public void setTable(GameTable table) {
    this.table = table;
    if (ui != null) ui.setTable(table);
  }
}
