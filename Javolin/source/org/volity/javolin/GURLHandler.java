package org.volity.javolin;

import java.util.ArrayList;
import java.util.List;

/**
 * JNI based GetURL AppleEvent handler for Mac OS X.
 * 
 * This only makes sense on MacOS; do not call getInstance() on other OSs!
 *
 * Due to the way Swing works on MacOS, you must call getInstance() before the
 * Swing classes are loaded. If you fail to do this, the first URL event -- the
 * one which launches the application -- will be lost.
 * 
 * getInstance() will fail unless libMacGURL (the JNI library) is available in
 * java.library.path.
 */
public final class GURLHandler {

    public interface GURLListener {
        public void handle(String url);
    }

    private static GURLHandler soleInstance = null;
    private static Object lock = new Object(); // covers the following:
    private static List listeners = new ArrayList();
    private static List cachedURLs = new ArrayList();

    /**
     * Fetch the singleton GURLHandler object. 
     *
     * This only makes sense on MacOS; do not call it on other OSs!
     */
    public static GURLHandler getInstance() {
        if (soleInstance == null) {
            System.loadLibrary("MacGURL");
            soleInstance = new GURLHandler();
        }
        return soleInstance;
    }

    /**
     * Return whether a handler has been registered. On non-Mac systems, this
     * will safely return false.
     */
    public static boolean isRegistered() {
        if (soleInstance != null && soleInstance.registered)
            return true;
        else
            return false;
    }

    /**
     * Add a GURL listener.
     *
     * The listener will not necessarily be called in any particular thread. It
     * should invoke to another thread if it wants to do Swing work, or if it
     * expects to do any long-running work at all.
     *
     * Since the event handler must be registered at the very beginning of
     * application startup, some URLs may have come in before you call
     * addListener(). You should therefore call getCachedURLs() immediately
     * after addListener(), to see what those URLs were.
     */
    public static void addListener(GURLListener listener) {
        synchronized (lock) {
            listeners.add(listener);
        }
    }

    /** Remove a GURL listener. */
    public static void removeListener(GURLListener listener) {
        synchronized (lock) {
            listeners.remove(listener);
        }
    }

    /** Fetch all the GURLs that have been received so far. */
    public static List getCachedURLs() {
        List res;
        synchronized (lock) {
            res = new ArrayList(cachedURLs);
            cachedURLs.clear();
        }
        return res;
    }

    private boolean registered = false;
    
    /** Constructor. Registers the GetURL AppleEvent handler. */
    private GURLHandler() {
        if (InstallEventHandler() == 0) {
            registered = true;
        }
    }
    
    /** Called by the native code. */
    private void callback(String url) {
        synchronized (lock) {
            if (listeners.size() == 0) {
                cachedURLs.add(url);
            }
            else {
                for (int ix=0; ix<listeners.size(); ix++) {
                    GURLListener listener = (GURLListener)listeners.get(ix);
                    listener.handle(url);
                }
            }
        }
    }
    
    /** Deregister the GetURL AppleEvent handler. */
    protected void finalize() throws Throwable {
        if (registered) {
            RemoveEventHandler();
            registered = false;
        }
    }
    
    private synchronized final native int InstallEventHandler();
    private synchronized final native int RemoveEventHandler();
}
