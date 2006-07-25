package org.volity.javolin;

import java.text.MessageFormat;
import java.util.MissingResourceException;

/**
 * Simple class for getting localized text from a resource file. This contains
 * only static methods.
 */
public class Localize 
{
    private Localize() {
        // Do not instantiate.
    }

    /**
     * Get a key from a resource file.
     */
    public static String localize(String node, String key) {
        try {
            return JavolinApp.resources.getString(node+"_"+key);
        }
        catch (MissingResourceException ex) {
            return "???"+node+"_"+key;
        }
    }

    /**
     * Get a key from a resource file, interpolating one string. (The argument
     * string must already be localized.)
     */
    public static String localize(String node, String key, Object arg1) {
        try {
            String pattern = JavolinApp.resources.getString(node+"_"+key);
            return MessageFormat.format(pattern, new Object[] { arg1 });
        }
        catch (MissingResourceException ex) {
            return "???"+node+"_"+key;
        }
    }

    /**
     * Get a key from a resource file, interpolating two strings. (The argument
     * strings must already be localized.)
     */
    public static String localize(String node, String key, Object arg1, Object arg2) {
        try {
            String pattern = JavolinApp.resources.getString(node+"_"+key);
            return MessageFormat.format(pattern, new Object[] { arg1, arg2 });
        }
        catch (MissingResourceException ex) {
            return "???"+node+"_"+key;
        }
    }

    /**
     * Get a key from a resource file, interpolating a list of strings. (The
     * argument strings must already be localized.)
     */
    public static String localize(String node, String key, Object[] argls) {
        try {
            String pattern = JavolinApp.resources.getString(node+"_"+key);
            return MessageFormat.format(pattern, argls);
        }
        catch (MissingResourceException ex) {
            return "???"+node+"_"+key;
        }
    }

}
