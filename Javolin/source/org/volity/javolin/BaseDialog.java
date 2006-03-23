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
import org.jivesoftware.smack.util.StringUtils;

/**
 * Base class for dialogs. Defines some layout metric constants, and adds
 * support for saving and restoring the dialog size and position in the
 * preferences storage.
 *
 * See also BaseWindow.
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
     * @param owner     The parent frame. (May be null.)
     * @param title     The dialog title.
     * @param modal     True for a modal dialog, false for non-modal.
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

    /** 
     * A utility function for the case where someone has failed to fill in an
     * important field. Display an alert box, and then highlight the field for
     * input.
     */
    public void complainMustEnter(JComponent comp, String desc) {
        JOptionPane.showMessageDialog(this,
            "You must enter " + desc + ".",
            JavolinApp.getAppName() + ": Error",
            JOptionPane.ERROR_MESSAGE);

        if (comp != null)
            comp.requestFocusInWindow();
    }

    private final static String DEFAULT_HOST = "volity.net";

    /**
     * A rather baroque utility function which happens to be useful in a lot of
     * dialog boxes. This looks at a text field and pulls a JID out of it. If
     * the JID lacks an "@host" part, this inserts "@volity.net". (In the
     * field, not just in the returned value.)
     *
     * If the JID is entirely blank (or starts with a slash -- e.g., only has a
     * resource string) then this returns null. The caller should not proceed,
     * but should instead put the focus on the field and wait for better input.
     */
    public static String expandJIDField(JTextField field) {
        return expandJIDField(field, DEFAULT_HOST);
    }

    /**
     * Same as the simpler form of expandJIDField, but you can specify what
     * default host to insert.
     */
    public static String expandJIDField(JTextField field, String defaultHost) {
        String jid = field.getText().trim();

        if (jid.equals("")
            || jid.startsWith("/")) {
            return null;
        }

        String jidresource = StringUtils.parseResource(jid);
        String jidhost = StringUtils.parseServer(jid);
        String jidname = StringUtils.parseName(jid);

        /* Due to the JID structure, if the user typed no "@" then we wind up
         * with a jidhost and no jidname. */

        if (jidhost.equals("")) {
            return "";
        }

        if (jidname.equals("")) {
            jidname = jidhost;
            jidhost = defaultHost;
            jid = jidname + "@" + jidhost;
            if (!jidresource.equals(""))
                jid += ("/" + jidresource);
            field.setText(jid);
        }

        return jid;
    }

}
