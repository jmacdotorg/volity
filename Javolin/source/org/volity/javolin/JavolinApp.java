/*
 * JavolinApp.java
 * Source code Copyright 2004 by Karl von Laudermann
 */
package org.volity.javolin;

import javax.swing.*;

/**
 * The main application class of Javolin.
 *
 * @author   karlvonl
 */
public class JavolinApp extends JFrame
{
    private final static String APPNAME = "Javolin";

    /**
     * The main program for the JavolinApp class
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args)
    {
        ConnectDialog connDlg = new ConnectDialog(null);
        connDlg.show();
    }

    /**
     * Gets the name of the application, suitable for display to the user in dialog
     * box titles and other appropriate places
     *
     * @return   The name of the application
     */
    public static String getAppName()
    {
        return APPNAME;
    }
}
