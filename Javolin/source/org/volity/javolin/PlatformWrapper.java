package org.volity.javolin;

import java.io.File;
import java.lang.reflect.*;

//### http://browserlauncher.sourceforge.net/
//### http://support.microsoft.com/default.aspx?scid=kb;en-us;283225
//### http://www.centerkey.com/java/browser/myapp/BareBonesBrowserLaunch.java

//### jvm parameter "http.agent" for UserAgent

//### Java6 desktop stuff:
//### http://java.sun.com/developer/technicalArticles/J2SE/Desktop/mustang/systemtray/
//### http://java.sun.com/developer/technicalArticles/J2SE/Desktop/mustang/desktop_api/

/**
 * A collection of static functions which bundle up platform-specific
 * operations. This is broken out as a separate class because it's a lot of
 * ugly reflection code.
 */
public class PlatformWrapper 
{
    private static boolean isMac;
    private static boolean isWin;

    static {
        // Cache this.
        isMac = isRunningOnMac();

        isWin = false;
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Windows"))
            isWin = true;

        assert (!(isMac && isWin));
    }

    /**
     * Do any platform-specific initialization. Call this at the top of
     * main().
     */
    public static void mainInitialize() {
        if (isMac) {
            // Make .app and .pkg files non-traversable as directories in AWT
            // file choosers
            System.setProperty("com.apple.macos.use-file-dialog-packages",
                "true");

            // Put window menu bars at the top of the screen
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }
    }

    /** 
     * Test whether we have the capability to send a URL to the user's default
     * browser.
     *
     * Currently returns true on Mac OS X and Windows.
     */
    public static boolean launchURLAvailable() {
        return (isMac || isWin);
    }

    /**
     * Send a URL to the user's default browser.
     * Currently implemented only on Mac OS X.
     *
     * @return did the action succeed?
     */
    public static boolean launchURL(String url) {
        if (isMac) {
            try {
                Class cla = Class.forName("com.apple.eio.FileManager");

                Class[] classls = new Class[] { String.class };
                Method meth = cla.getMethod("openURL", classls);

                Object[] argls = new Object[] { url };
                meth.invoke(null, argls);

                return true;
            }
            catch (Exception ex) {
                new ErrorWrapper(ex);
                return false;
            }
        }

        if (isWin) {
            try {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
                return true;
            }
            catch (Exception ex) {
                new ErrorWrapper(ex);
                return false;
            }
        }

        // Not available.
        return false;
    }

    /**
     * Test whether the application framework comes with an application menu,
     * containing About, Preferences, and Quit menu options.
     *
     * Currently returns true only on Mac OS X.
     */
    public static boolean applicationMenuHandlersAvailable() {
        return isMac;
    }

    /** 
     * Set up the application menu. Either or both of the parameters may be
     * null; this tells the system to do the default menu action.
     *
     * @param doAbout a Runnable which performs the About menu option. (Null
     *        does the default action, which is to display a boring About 
     *        box that just says "Java".)
     * @param doPreferences a Runnable which performs the Preferences menu 
     *        option. (Null does the default action, which is nothing.)
     * @param doQuit a Runnable which performs the Quit menu option. (Null
     *        does the default action, which is to shut down the app
     *        immediately.)
     * @param doFile a RunnableFile which is called when the user opens
     *        a Volity file from the OS UI. (Null means do nothing.)
     */
    public static boolean setApplicationMenuHandlers(
        Runnable doAbout,
        Runnable doPreferences,
        Runnable doQuit,
        JavolinApp.RunnableFile doFile) {

        if (isMac) {
            try {
                Class classApplication = Class.forName(
                    "com.apple.eawt.Application");
                Class intApplicationListener = Class.forName(
                    "com.apple.eawt.ApplicationListener");

                // app = Application.getApplication();
                Method methGet = classApplication.getMethod(
                    "getApplication", null);
                Object app = methGet.invoke(null, null);

                // app.setEnabledPreferencesMenu(true);
                Method methEnabledPreferencesMenu = classApplication.getMethod(
                    "setEnabledPreferencesMenu", new Class[] { Boolean.TYPE });
                methEnabledPreferencesMenu.invoke(app, new Object[] { new Boolean(true)});

                Class[] classls = new Class[] { intApplicationListener };
                Method methAdd = classApplication.getMethod(
                    "addApplicationListener", classls);

                // listener = new ApplicationListener() { ... };
                Class[] interfaces = new Class[] { intApplicationListener };
                InvocationHandler handler = new AppListenerInvocationHandler(
                    doAbout, doPreferences, doQuit, doFile);
                Class proxyClass = Proxy.getProxyClass(
                    intApplicationListener.getClassLoader(), 
                    interfaces);
                Object listener = proxyClass.getConstructor(
                    new Class[] { InvocationHandler.class }).
                    newInstance(new Object[] { handler });

                // app.addApplicationListener(listener);
                Object[] argls = new Object[] { listener };
                methAdd.invoke(app, argls);

                return true;
            }
            catch (Exception ex) {
                new ErrorWrapper(ex);
                return false;
            }
        }

        // Not available.
        return false;
    }

    /**
     * Invocation handler for com.apple.eawt.ApplicationListener.
     * (See: http://developer.apple.com/documentation/Java/Reference/1.5.0/appledoc/api/)
     *
     * All methods in this interface return void, and take one argument of type
     * com.apple.eawt.ApplicationEvent.
     */
    private static class AppListenerInvocationHandler 
        implements InvocationHandler 
    {
        Runnable mDoAbout;
        Runnable mDoPreferences;
        Runnable mDoQuit;
        JavolinApp.RunnableFile mDoFile;
        Class classApplicationEvent;
        Method methEventSetHandled;
        Method methEventGetFilename;

        AppListenerInvocationHandler(Runnable doAbout, Runnable doPreferences,
            Runnable doQuit, JavolinApp.RunnableFile doFile)
            throws ClassNotFoundException, NoSuchMethodException {
            mDoAbout = doAbout;
            mDoPreferences = doPreferences;
            mDoQuit = doQuit;
            mDoFile = doFile;

            classApplicationEvent = Class.forName(
                "com.apple.eawt.ApplicationEvent");
            methEventSetHandled = classApplicationEvent.getMethod(
                "setHandled", new Class[] { Boolean.TYPE });
            methEventGetFilename = classApplicationEvent.getMethod(
                "getFilename", null);
        }

        public Object invoke(Object proxy, Method method, Object[] args)
            throws IllegalAccessException, InvocationTargetException {
            String methName = method.getName();
            Object ev = args[0];

            if (methName.equals("handleAbout")) {
                if (mDoAbout != null) {
                    setEventHandled(ev, true);
                    mDoAbout.run();
                }
            }
            if (methName.equals("handlePreferences")) {
                if (mDoPreferences != null) {
                    setEventHandled(ev, true);
                    mDoPreferences.run();
                }
            }
            else if (methName.equals("handleQuit")) {
                /* The Quit event is backwards; you do setHandled(false) if
                 * you're doing the work, setHandled(true) if you want the
                 * default "quit immediately". */
                if (mDoQuit != null) {
                    setEventHandled(ev, false);
                    mDoQuit.run();
                }
                else {
                    setEventHandled(ev, true);
                }
            }
            else if (methName.equals("handleOpenFile")) {
                String filename = getEventFilename(ev);
                if (mDoFile != null) {
                    mDoFile.run(new File(filename));
                }
            }
            else {
                // handleOpenApplication
                // handlePrintFile
                // handleReOpenApplication
            }

            return null;
        }

        private void setEventHandled(Object ev, boolean flag) 
            throws IllegalAccessException, InvocationTargetException {
            methEventSetHandled.invoke(ev, new Object[] { new Boolean(flag)});
        }

        private String getEventFilename(Object ev)
            throws IllegalAccessException, InvocationTargetException {
            Object val = methEventGetFilename.invoke(ev, null);
            return (String)val;
        }
    }

    /**
     * Tells whether the app is running on a Mac platform.
     *
     * @return true if the app is currently running on a Mac, false if running
     * on another platform.
     */
    public static boolean isRunningOnMac() {
        // Apple recommended test for Mac
        return (System.getProperty("mrj.version") != null);
    }


}
