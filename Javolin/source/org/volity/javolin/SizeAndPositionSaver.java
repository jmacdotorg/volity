/*
 * SizeAndPositionSaver.java
 *
 * Copyright 2004 Karl von Laudermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.*;
import javax.swing.*;

/**
 * Handles saving and restoring the size and position of a Dialog or Frame in the
 * preferences storage.
 */
public class SizeAndPositionSaver
{
    private final static String POSX_KEY = "PosX";
    private final static String POSY_KEY = "PosY";
    private final static String WIDTH_KEY = "Width";
    private final static String HEIGHT_KEY = "Height";

    private Window mWindow;
    private String mPrefsNodeName;

    /**
     * Constructor.
     *
     * @param window    The window to save and restore the size of.
     * @param nodeName  A string to uniquely identify the window, to use as the
     * node name in the preferences storage.
     */
    public SizeAndPositionSaver(Window window, String nodeName)
    {
        mWindow = window;
        mPrefsNodeName = nodeName;
    }

    /**
     * Moves the window to the center of the screen.
     */
    private void centerWindow()
    {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension frameSize = mWindow.getSize();

        mWindow.setLocation((screenSize.width - frameSize.width) / 2,
            (screenSize.height - frameSize.height) / 2);
    }

    /**
     * Tells whether the window is resizable, and thus whether the window size should
     * be saved and restored.
     *
     * @return   true if the window is resizable, false if not.
     */
    private boolean isWindowResizable()
    {
        boolean retVal = false;

        if (mWindow instanceof Dialog)
        {
            retVal = ((Dialog)mWindow).isResizable();
        }
        else if (mWindow instanceof Frame)
        {
            retVal = ((Frame)mWindow).isResizable();
        }

        return retVal;
    }

    /**
     * Saves the window size and position values to the preferences storage.
     * The size is not saved if the window is not resizable.
     */
    public void saveSizeAndPosition()
    {
        Preferences prefs =
            Preferences.userNodeForPackage(mWindow.getClass()).node(mPrefsNodeName);

        // Only save the size if the window is resizable
        if (isWindowResizable())
        {
            Dimension size = mWindow.getSize();

            prefs.putInt(WIDTH_KEY, size.width);
            prefs.putInt(HEIGHT_KEY, size.height);
        }

        Point loc = mWindow.getLocation();

        prefs.putInt(POSX_KEY, loc.x);
        prefs.putInt(POSY_KEY, loc.y);
    }

    /**
     * Sizes and positions the window from the values stored in the preferences
     * storage. The size is not restored if the window is not resizable. If the size
     * was never stored, the size remains unchanged. If the position was never stored,
     * the window is centered on the screen.
     */
    public void restoreSizeAndPosition()
    {
        Preferences prefs =
            Preferences.userNodeForPackage(mWindow.getClass()).node(mPrefsNodeName);

        // Only restore the saved size if the window is resizable
        if (isWindowResizable())
        {
            Dimension size = new Dimension();

            size.width = prefs.getInt(WIDTH_KEY, -1);
            size.height = prefs.getInt(HEIGHT_KEY, -1);

            if ((size.width != -1) && (size.height != -1))
            {
                mWindow.setSize(size);
            }
        }

        Point loc = new Point();

        loc.x = prefs.getInt(POSX_KEY, -1);
        loc.y = prefs.getInt(POSY_KEY, -1);

        // If position was not found, center the window.
        if ((loc.x == -1) || (loc.y == -1))
        {
            centerWindow();
        }
        else
        {
            mWindow.setLocation(loc);
        }
    }
}
