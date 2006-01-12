package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.volity.client.CommandStub;
import org.w3c.dom.Document;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.swing.BasicPanel;
import org.xhtmlrenderer.swing.LinkListener;

public class Finder extends JFrame
{
    private final static String FINDER_URL = "http://www.volity.net/gamefinder/";

    private final static String NODENAME = "FinderWin";
    private final static String OPENFINDER_KEY = "WantedOpen";

    private static Finder soleFinder = null;

    /**
     * There should only be one Finder at a time. This returns it if there is
     * one, or else creates it.
     */
    public static Finder getSoleFinder(JavolinApp owner) {
        if (soleFinder == null) {
            soleFinder = new Finder(owner);
        }
        return soleFinder;
    }

    /**
     * Return whether the user wants the Finder window open or not. (This is
     * based on whether the user had it open at last check -- i.e., when the
     * app was last running.)
     *
     * The default is true.
     */
    public static boolean getFinderWanted() {
        Preferences prefs = Preferences.userNodeForPackage(Finder.class).node(NODENAME);
        return prefs.getBoolean(OPENFINDER_KEY, true);
    }

    private JavolinApp mOwner;
    private BasicPanel mDisplay;
    private SizeAndPositionSaver mSizePosSaver;

    private Finder(JavolinApp owner) 
    {
        mOwner = owner;

        setTitle(JavolinApp.getAppName() + " Game Finder");
        buildUI();

        setSize(650, 400);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        mSizePosSaver.restoreSizeAndPosition();

        // Handle closing the window to quit the app
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    mSizePosSaver.saveSizeAndPosition();
                    soleFinder = null;
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
                    prefs.putBoolean(OPENFINDER_KEY, false);
                }
            });
        
        // Save window size and position whenever it is moved or resized
        addComponentListener(
            new ComponentAdapter()
            {
                public void componentMoved(ComponentEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }

                public void componentResized(ComponentEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }
            });

        show();
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        prefs.putBoolean(OPENFINDER_KEY, true);
    }

    {
        /* The current version of xhtmlrenderer has a lot of logging turned on
         * by default. We do not like it, so we turn it all off. This must be
         * done before the xhtmlrenderer packages load. */
        String[] logprops = {
            "show-config",
            "xr.util-logging..level",
            "xr.util-logging.plumbing.level",
            "xr.util-logging.plumbing.config.level",
            "xr.util-logging.plumbing.exception.level",
            "xr.util-logging.plumbing.general.level",
            "xr.util-logging.plumbing.init.level",
            "xr.util-logging.plumbing.load.level",
            "xr.util-logging.plumbing.load.xml-entities.level",
            "xr.util-logging.plumbing.match.level",
            "xr.util-logging.plumbing.cascade.level",
            "xr.util-logging.plumbing.css-parse.level",
            "xr.util-logging.plumbing.layout.level",
            "xr.util-logging.plumbing.render.level"
        };
        for (int ix=0; ix<logprops.length; ix++) {
            System.setProperty(logprops[ix], "OFF");
        }
    }

    /**
     * A customized LinkListener. When the user clicks on a URL which returns a
     * CommandStub, this executes the command. Any other URL does the default
     * thing -- changes the document display.
     */
    private class FinderLinkListener extends LinkListener {
        final Class[] arrayCommandStub = new Class[] { CommandStub.class };

        BasicPanel mPanel;
        public FinderLinkListener(BasicPanel panel) {
            super(panel);
            mPanel = panel;
        }
        public void linkClicked(String uri) {
            String urlstr = mPanel.getRenderingContext().getUac().resolveURI(uri);
            URL url = null;
            try {
                url = new URL(urlstr);
                CommandStub stub = (CommandStub)url.getContent(arrayCommandStub);
                if (stub != null) {
                    // If it's a CommandStub, execute it.
                    mOwner.doOpenFile(stub);
                    return;
                }

                // If it's an internal URL, fall through.
                if (url.getProtocol().equals("http")
                    && (url.getHost().equals("www.volity.net") 
                        || url.getHost().equals("volity.net"))
                    && url.getPath().startsWith("/gamefinder")) {
                    super.linkClicked(uri);
                    return;
                }

                // Otherwise, kick it to the external browser.
                boolean res = PlatformWrapper.launchURL(urlstr);
                if (!res) {
                    JOptionPane.showMessageDialog(Finder.this,
                        "Unable to launch URL:\n" + urlstr,
                        JavolinApp.getAppName() + ": Error",
                        JOptionPane.ERROR_MESSAGE);
                }
                return;
            }
            catch (MalformedURLException ex) {
                new ErrorWrapper(ex);
                JOptionPane.showMessageDialog(Finder.this,
                    ex.toString(),
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
            catch (IOException ex) {
                new ErrorWrapper(ex);
                JOptionPane.showMessageDialog(Finder.this,
                    ex.toString(),
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * A customized XHTML component, which knows about the
     * "application/x-volity-command-stub" MIME type, and triggers the
     * appropriate commands when the user clicks on links of that type.
     *
     * This is a copy of the standard XHTMLPanel, except that it doesn't have
     * the font-scaling stuff, and it uses our custom LinkListener.
     */
    private class XHTMLFinder extends BasicPanel {

        public XHTMLFinder() {
            super();
            setupListeners();
        }
        public XHTMLFinder(UserAgentCallback uac) {
            super(uac);
            setupListeners();
        }

        public void setDocument(String uri) {
            setDocument(loadDocument(uri), uri);
        }
        public void setDocument(Document doc) {
            setDocument(doc, "");
        }
        public void setDocument(Document doc, String url) {
            super.setDocument(doc, url, new XhtmlNamespaceHandler());
        }
        public void setDocument(InputStream stream, String url)
            throws Exception {
            super.setDocument(stream, url, new XhtmlNamespaceHandler());
        }

        private void setupListeners() {
            LinkListener linkListener = new FinderLinkListener(this);
            addMouseListener(linkListener);
            addMouseMotionListener(linkListener);
        }
    }

    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());
        
        mDisplay = new XHTMLFinder();
        mDisplay.setDocument(FINDER_URL);
        cPane.add(new JScrollPane(mDisplay), BorderLayout.CENTER);

        // Necessary for all windows, for Mac support
        JavolinMenuBar.applyPlatformMenuBar(this);
    }
}
