package org.volity.javolin;

import java.util.Date;

/**
 * Encapsulates an exception that was thrown somewhere in the application.
 *
 * This implements the (notional) interface needed for the
 * "sun.awt.exception.handler" system property.
 */
public class ErrorWrapper
{
    private static ErrorWrapper lastError = null;

    /** 
     * Get the ErrorWrapper that was most recently created.
     */
    public static ErrorWrapper getLastError() {
        return lastError;
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

        System.out.println("Detected exception: " + ex.toString());
        lastError = this;
    }

    /** Get the timestamp of the error. */
    public Date getTime() { return mDate; }

    /** Get the Throwable of the error. */
    public Throwable getException() { return mError; }
}
