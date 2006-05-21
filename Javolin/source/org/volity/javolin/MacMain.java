package org.volity.javolin;

import java.lang.reflect.Method;

/**
 * The Mac GURL event-handler must be installed before we load any Swing
 * classes. Unfortunately, JavolinApp is a subclass of a Swing class. This
 * means that we need to tread very carefully. On the Mac, this class becomes
 * the "main" which is invoked. It sets up the event-handler, and then calls
 * JavolinApp.main() by reflection.
 */
public class MacMain
{
    /**
     * The main program for the MacMain class.
     *
     * @param args  The command line arguments.
     */
    public static void main(String[] args) {
        try {
            // GURLHandler han = GURLHandler.getInstance();
            Class cla = Class.forName("org.volity.javolin.GURLHandler");
            Method getInstance = cla.getMethod("getInstance", new Class[0]);
            Object gurl = getInstance.invoke(null, new Object[0]);
        }
        catch (Throwable ex) {
            System.out.println("Unable to load AppleEvent library: " + ex);
        }

        try {
            // JavolinApp.main(args);
            Class cla = Class.forName("org.volity.javolin.JavolinApp");
            Class arraycla = Class.forName("[Ljava.lang.String;");
            Method mainFunc = cla.getMethod("main", new Class[] { arraycla });
            Object[] argls = new Object[] { args };
            mainFunc.invoke(null, argls);
        }
        catch (Throwable ex) {
            System.out.println("Unable to launch application: " + ex);
        }
    }
}
