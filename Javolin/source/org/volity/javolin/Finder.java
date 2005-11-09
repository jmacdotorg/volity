package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.*;
import org.w3c.dom.Document;
import org.volity.client.CommandStub;

public class Finder extends JFrame
{
    private final static String NODENAME = "FinderWin";

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

    private JavolinApp mOwner;
    private SizeAndPositionSaver mSizePosSaver;

    private Finder(JavolinApp owner) 
    {
        mOwner = owner;

        setTitle(JavolinApp.getAppName() + " Game Finder");
        buildUI();

        setSize(400, 400);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        mSizePosSaver.restoreSizeAndPosition();

        // Handle closing the window to quit the app
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    mSizePosSaver.saveSizeAndPosition();
                    soleFinder = null;
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
    }

    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());
        

        // Necessary for all windows, for Mac support
        JavolinMenuBar.applyPlatformMenuBar(this);
    }
}
