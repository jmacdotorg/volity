/*
 * BaseDialog.java
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
import javax.swing.*;

/**
 * Base class for dialogs. Defines some layout metric constants, and adds support for
 * saving and restoring the dialog size and position in the preferences storage.
 */
public class BaseDialog extends JDialog
{
    protected final static int MARGIN = 12; // Space to window edge
    protected final static int SPACING = 6; // Space between related controls
    protected final static int GAP = 12; // Space between unrelated controls

    protected SizeAndPositionSaver mSizePosSaver;

    /**
     * Constructor.
     *
     * @param owner     The parent frame.
     * @param title     The dialog title.
     * @param modal     true for a modal dialog, false for non-modal.
     * @param nodeName  A string to uniquely identify the dialog, to use as the
     * node name in the preferences storage.
     */
    public BaseDialog(Frame owner, String title, boolean modal, String nodeName)
    {
        super(owner, title, modal);
        mSizePosSaver = new SizeAndPositionSaver(this, nodeName);

        // Save window size and position when closing
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter()
            {
                public void windowClosed(WindowEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }
            });
    }
}
