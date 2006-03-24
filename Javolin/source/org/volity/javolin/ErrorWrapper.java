package org.volity.javolin;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates an exception that was thrown somewhere in the application.
 *
 * This implements the (notional) interface needed for the
 * "sun.awt.exception.handler" system property.
 */
public class ErrorWrapper
{
    private static Object lastErrorLock = new Object();
    private static ErrorWrapper lastError = null;

    /** 
     * Get the ErrorWrapper that was most recently created.
     */
    public static ErrorWrapper getLastError() {
        ErrorWrapper err;
        synchronized (lastErrorLock) {
            err = lastError;
        }
        return err;
    }

    private Throwable mError;
    private Date mDate;

    /**
     * Create an ErrorWrapper. You should call handle(ex) immediately after
     * calling this constructor.
     */
    public ErrorWrapper() {
    }

    /**
     * Create an ErrorWrapper. Do not call handle(ex) after using this
     * constructor.
     */
    public ErrorWrapper(Throwable ex) {
        handle(ex);
    }

    /**
     * Create an ErrorWrapper containing a generic Exception. Do not call
     * handle(ex) after using this constructor.
     */
    public ErrorWrapper(String str) {
        Exception ex = new Exception(str);
        handle(ex);
    }

    /**
     * Place the given Throwable into an ErrorWrapper. You should only call
     * this after creating an ErrorWrapper with the argumentless constructor.
     */
    public void handle(Throwable ex) {
        mError = ex;
        mDate = new Date();

        if (ex instanceof OutOfMemoryError) {
            // This is fatal.
            System.out.println("Detected fatal exception: " + ex.toString());
            System.exit(-1);
        }

        System.out.println("Detected exception: " + ex.toString());
        synchronized (lastErrorLock) {
            lastError = this;
        }
        Audio.playError();
    }

    /** Get the timestamp of the error. */
    public Date getTime() { return mDate; }

    /** Get the Throwable of the error. */
    public Throwable getException() { return mError; }


    private static Pattern sFixLines = Pattern.compile(
        "\\.svg:([0-9]+); line ([0-9]+)\\)",
        Pattern.CASE_INSENSITIVE);
    
    /**
     * Fix the annoying Batik line numbers in an error dump.
     *     (Inline <script> file:/x/y/z.svg:23; line 60)
     * should become
     *     (Inline <script> file:/x/y/z.svg:23; line 60) [UI line 82]
     */
    public static String fixLineNumbers(String msg) {
        Matcher matcher = sFixLines.matcher(msg);

        int count = 0;
        StringBuffer buffer = null;
 
        while (matcher.find()) {
            if (buffer == null)
                buffer = new StringBuffer();
            String orig = matcher.group(0);
            String num1 = matcher.group(1);
            String num2 = matcher.group(2);
            int val1 = Integer.parseInt(num1);
            int val2 = Integer.parseInt(num2);
            orig += " [UI line " + String.valueOf(val1+val2-1) + "]";
            matcher.appendReplacement(buffer, orig);
            count++;
        }

        if (count == 0)
            return msg;

        matcher.appendTail(buffer); 
        return buffer.toString();
    }
}
