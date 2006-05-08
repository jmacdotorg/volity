package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.volity.client.data.CommandStub;
import org.w3c.dom.Document;
import org.xhtmlrenderer.extend.NamespaceHandler;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.swing.BasicPanel;
import org.xhtmlrenderer.swing.LinkListener;
import org.xhtmlrenderer.util.XRRuntimeException;

public class HelpWindow extends JFrame
    implements CloseableWindow
{
    public final static String HELPWIN_URL = "http://volity.net/games/gamut/help/welcome.html";
    public final static String BUGREPORT_URL = Finder.BUGREPORT_URL;

    private final static String NODENAME = "HelpWin";

    private final static String fallbackErrorText =
        "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n"
        +"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n"
        +"\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
        +"<html><head>\n"
        +"<title>Unable to load Gamut Help</title>\n"
        +"</head><body>\n"
        +"<p>The Gamut Help service does not appear to be\n"
        +"working correctly.\n"
        +"We apologize for the problem.</p>\n"
        +"<p>Try <a href=\"<$HELPWIN$>\">reloading the Help page</a></p>\n"
        +"<p>Please <a href=\"<$BUGREPORT$>\">report problems</a>\n"
        +"using our bug report service\n"
        +"(<$BUGREPORT$>)</p>\n"
        +"</body></html>\n";
    
    private static HelpWindow soleHelpWindow = null;

    /**
     * There should only be one HelpWindow at a time. This returns it if there
     * is one, or else creates it.
     */
    public static HelpWindow getSoleHelpWindow(JavolinApp owner) {
        if (soleHelpWindow == null) {
            soleHelpWindow = new HelpWindow(owner);
        }
        return soleHelpWindow;
    }

    private JavolinApp mOwner;
    private String mCurrent;
    private URL mCurrentURL;

    private XHTMLHelper mDisplay;
    private SizeAndPositionSaver mSizePosSaver;

    private HelpWindow(JavolinApp owner) 
    {
        mOwner = owner;

        setTitle(JavolinApp.getAppName() + " Help");
        buildUI();

        /* Push the setDocument into its own event. This doesn't speed up the
         * startup procedure, but it does prevent errors from short-circuiting
         * the creation of the window. */
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    trySetDocument(HELPWIN_URL);
                }
            });

        setSize(725, 450);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        mSizePosSaver.restoreSizeAndPosition();

        // Handle closing the window to quit the app
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    mSizePosSaver.saveSizeAndPosition();
                    soleHelpWindow = null;
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
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
                    /* Due to bugs in Flying Saucer (R5), we have to manually
                     * refresh the screen after any window resize. Maybe FS
                     * will get better someday... */
                    if (PlatformWrapper.isRunningOnWindows()) {
                        trySetDocument(mCurrent);
                    }
                }
            });

        setVisible(true);
    }

    /**
     * A customized XHTML component, which knows about the
     * "application/x-volity-command-stub" MIME type, and triggers the
     * appropriate commands when the user clicks on links of that type.
     *
     * This is a copy of the standard XHTMLPanel, except that it doesn't have
     * the font-scaling stuff, and it uses our custom LinkListener.
     */
    private class XHTMLHelper extends XHTMLPane {

        public XHTMLHelper() {
            super(HelpWindow.this);
        }

        public int linkDisposition(URL url, String urlstr) {
            // If it's an internal URL, fall through.
            if (url.getProtocol().equals("http")
                && (url.getHost().equals("www.volity.net") 
                    || url.getHost().equals("test.volity.net") 
                    || url.getHost().equals("volity.net"))
                && (url.getPath().startsWith("/gamefinder")
                    || url.getPath().startsWith("/games/gamut/help"))) {
                setCurrentURL(urlstr, url);
                return HANDLE_INTERNAL;
            }

            return HANDLE_EXTERNAL;
        }
    }

    /**
     * Attempt to go to the given URL. If there is a problem, display a
     * built-in error page.
     */
    private void trySetDocument(String urlstr) {
        if (mDisplay == null)
            return;

        try {
            setCurrentURL(urlstr, null);
            mDisplay.setDocument(urlstr);
        }
        catch (XRRuntimeException ex) {
            new ErrorWrapper(ex);

            String errortext = fallbackErrorText;
            errortext = errortext.replaceAll("<\\$HELPWIN\\$>", HELPWIN_URL);
            errortext = errortext.replaceAll("<\\$BUGREPORT\\$>", BUGREPORT_URL);

            try {
                byte[] bytes = errortext.getBytes("UTF-8");
                InputStream instr = new ByteArrayInputStream(bytes);
                mDisplay.setDocument(instr, HELPWIN_URL);
            }
            catch (Exception ex2) {
                // give up
            }
        }
    }

    /** Set the URL we are looking at. We keep both String and URL forms of it,
     * for convenience. (But sometimes the URL is null. That suppresses the
     * last-mod-time checker.)
     */
    protected void setCurrentURL(String urlstr, URL url) {
        if (url == null) {
            try {
                url = new URL(urlstr);
            }
            catch (MalformedURLException ex) {
                // never mind
            }
        }

        mCurrent = urlstr;
        mCurrentURL = url;
    }

    /** Construct the UI. */
    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());
        
        mDisplay = new XHTMLHelper();
        JScrollPane scroller = new JScrollPane(mDisplay);
        scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        cPane.add(scroller, BorderLayout.CENTER);

        // Necessary for all windows, for Mac support
        AppMenuBar.applyPlatformMenuBar(this);
    }
}
