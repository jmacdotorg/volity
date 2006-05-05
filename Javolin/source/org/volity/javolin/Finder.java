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

public class Finder extends JFrame
    implements ActionListener, CloseableWindow, Runnable
{
    public final static String FINDER_URL = "http://volity.net/gamefinder/";
    public final static String BUGREPORT_URL = "http://volity.net/bugs/beta_bugform.html";

    // Interval to recheck Finder web pages for changes (in seconds)
    public final static long CHECK_INTERVAL = 30;

    private final static String NODENAME = "FinderWin";
    private final static String OPENFINDER_KEY = "WantedOpen";

    private final static String fallbackErrorText =
        "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n"
        +"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n"
        +"\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
        +"<html><head>\n"
        +"<title>Unable to load Game Finder</title>\n"
        +"</head><body>\n"
        +"<p>The Game Finder service does not appear to be\n"
        +"working correctly.\n"
        +"We apologize for the problem.</p>\n"
        +"<p>Try <a href=\"<$FINDER$>\">reloading the Finder page</a></p>\n"
        +"<p>Please <a href=\"<$BUGREPORT$>\">report problems</a>\n"
        +"using our bug report service\n"
        +"(<$BUGREPORT$>)</p>\n"
        +"</body></html>\n";
    
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
    private String mCurrent;
    private URL mCurrentURL;

    private XHTMLFinder mDisplay;
    private SizeAndPositionSaver mSizePosSaver;
    private JButton mHomeButton;
    private JButton mReloadButton;

    private Object mThreadLock = new Object(); // covers the following:
    private boolean mWindowClosed = false;
    private Thread mThread = null;
    private long mLastDocCheck = 0;
    private boolean mDocChanged = false;
    private long mLastModified = 0;

    private Finder(JavolinApp owner) 
    {
        mOwner = owner;

        setTitle(JavolinApp.getAppName() + " Game Finder");
        buildUI();

        /* We need a thread to periodically wake up and check the web site for
         * changes. This will be crude, but it will work.
         */
        mLastDocCheck = System.currentTimeMillis();
        mThread = new Thread(this);
        mThread.start();

        /* Push the setDocument into its own event. This doesn't speed up the
         * startup procedure, but it does prevent errors from short-circuiting
         * the creation of the window. */
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    trySetDocument(FINDER_URL);
                }
            });

        setSize(650, 400);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        mSizePosSaver.restoreSizeAndPosition();

        // Handle closing the window to quit the app
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    synchronized (mThreadLock) {
                        mWindowClosed = true;
                        mThreadLock.notify();
                    }
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
                    /* Due to bugs in Flying Saucer (R5), we have to manually
                     * refresh the screen after any window resize. Maybe FS
                     * will get better someday... */
                    if (PlatformWrapper.isRunningOnWindows()) {
                        trySetDocument(mCurrent);
                    }
                }
            });

        setVisible(true);
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        prefs.putBoolean(OPENFINDER_KEY, true);
    }

    /**
     * A customized XHTML component, which knows about the
     * "application/x-volity-command-stub" MIME type, and triggers the
     * appropriate commands when the user clicks on links of that type.
     *
     * This is a copy of the standard XHTMLPanel, except that it doesn't have
     * the font-scaling stuff, and it uses our custom LinkListener.
     */
    private class XHTMLFinder extends XHTMLPane {

        public XHTMLFinder() {
            super(Finder.this);
        }

        public void setDocument(Document doc, String url) {
            resetTimer();
            super.setDocument(doc, url);
            checkModifiedTime();
        }
        public void setDocument(InputStream stream, String url)
            throws Exception {
            resetTimer();
            super.setDocument(stream, url);
            checkModifiedTime();
        }

        public int linkDisposition(URL url, String urlstr) {
            // If it's an internal URL, fall through.
            if (url.getProtocol().equals("http")
                && (url.getHost().equals("www.volity.net") 
                    || url.getHost().equals("test.volity.net") 
                    || url.getHost().equals("volity.net"))) {

                if (url.getPath().startsWith("/gamefinder")) {
                    setCurrentURL(urlstr, url);
                    return HANDLE_INTERNAL;
                }

                if (url.getPath().startsWith("/games/gamut/help")) {
                    // Kick this over to the help window.
                    JavolinApp.getSoleJavolinApp().doGetHelp();
                    return ALREADY_HANDLED;
                }
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
            errortext = errortext.replaceAll("<\\$FINDER\\$>", FINDER_URL);
            errortext = errortext.replaceAll("<\\$BUGREPORT\\$>", BUGREPORT_URL);

            try {
                byte[] bytes = errortext.getBytes("UTF-8");
                InputStream instr = new ByteArrayInputStream(bytes);
                mDisplay.setDocument(instr, FINDER_URL);
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

        synchronized (mThreadLock) {
            mCurrent = urlstr;
            mCurrentURL = url;
        }
    }

    /**
     * Kick the timer we keep for how recently we've checked the document
     * last-mod-time. We call this just before loading a new document, as a
     * precaution against having the thread wake up while the document is in
     * the middle of being loaded.
     */
    protected void resetTimer() {
        synchronized (mThreadLock) {
            mLastDocCheck = System.currentTimeMillis();
        }
    }

    /**
     * Set a flag for the checker thread to check the last-mod-time of the
     * document. This is called after a new document is loaded (or reloaded).
     */
    protected void checkModifiedTime() {
        synchronized (mThreadLock) {
            mDocChanged = true;
            mLastModified = 0;
            mThreadLock.notify();
        }
    }

    /**
     * Implements Runnable interface, for the last-modified checker thread.
     *
     * The loop body of this function wakes up periodically to see if there is
     * last-mod-time checking work to do. This work can take two forms: (1)
     * Storing the last-mod-time of a document we have just loaded, or (2)
     * checking the last-mod-time to see if it has changed since (1).
     *
     * Note that when the window loads a new document, it calls notify() to
     * wake this thread up for task (1). Task (2) is purely based on a timer --
     * CHECK_INTERVAL seconds after the last check.
     *
     * If mCurrentURL is null, this thread does no work at all.
     */
    public void run() {
        synchronized (mThreadLock) {
            while (true) {
                if (mWindowClosed)
                    break;
                
                if (mDocChanged) {
                    // Check its last-modified stamp

                    mDocChanged = false;

                    if (mCurrentURL != null) {

                        try {
                            URLConnection connection = mCurrentURL.openConnection();
                            connection.setUseCaches(false);
                            if (connection instanceof HttpURLConnection) {
                                HttpURLConnection hconn = (HttpURLConnection)connection;
                                hconn.setRequestMethod("HEAD");
                            }

                            long srcmodtime = connection.getLastModified();
                            mLastModified = srcmodtime;
                            mLastDocCheck = System.currentTimeMillis();
                        }
                        catch (IOException ex) {
                            // never mind
                        }
                    }
                }
                else {
                    // We have a last-modified stamp for the displayed version
                    // of the document. See if it's changed.
                    long curtime = System.currentTimeMillis();
                    if (curtime > mLastDocCheck + 1000 * CHECK_INTERVAL
                        && mCurrentURL != null) {
                        mLastDocCheck = curtime;

                        try {
                            URLConnection connection = mCurrentURL.openConnection();
                            connection.setUseCaches(false);
                            if (connection instanceof HttpURLConnection) {
                                HttpURLConnection hconn = (HttpURLConnection)connection;
                                hconn.setRequestMethod("HEAD");
                            }

                            long srcmodtime = connection.getLastModified();
                            if (srcmodtime != mLastModified) {
                                // prevent rechecks until the reload happens
                                mCurrentURL = null;
                                SwingUtilities.invokeLater(new Runnable() {
                                        public void run() {
                                            trySetDocument(mCurrent);
                                        }
                                    });
                            }
                        }
                        catch (IOException ex) {
                            // never mind
                        }
                    }
                }

                // Back to sleep for a while
                try {
                    /* To prevent lots of clients from smashing the web server
                     * all at once, we wait a random interval from 4 to 7
                     * seconds. */
                    long delay = (long)(Math.random() * 3000) + 4000;
                    mThreadLock.wait(delay); // msec
                }
                catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    /** ActionListener interface method implementation. */
    public void actionPerformed(ActionEvent ev)
    {
        Object source = ev.getSource();
        if (source == null)
            return;
 
        String url = null;

        if (source == mHomeButton) {
            url = FINDER_URL;
        }
        if (source == mReloadButton) {
            url = mCurrent;
        }

        /* Several of the above actions could result in launching a new URL. We
         * collect that work here.
         */
        if (url != null) {
            final String urlref = url;
            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        trySetDocument(urlref);
                    }
                });
        }
    }

    /** Construct the UI. */
    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());
        
        mDisplay = new XHTMLFinder();
        JScrollPane scroller = new JScrollPane(mDisplay);
        scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        cPane.add(scroller, BorderLayout.CENTER);

        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        cPane.add(toolbar, BorderLayout.NORTH);

        mHomeButton = new JButton("Top");
        mHomeButton.setToolTipText("Return to index of games");
        mHomeButton.addActionListener(this);
        toolbar.add(mHomeButton);

        mReloadButton = new JButton("Reload");
        mReloadButton.setToolTipText("Manually reload this page");
        mReloadButton.addActionListener(this);
        toolbar.add(mReloadButton);


        // Necessary for all windows, for Mac support
        AppMenuBar.applyPlatformMenuBar(this);
    }
}
