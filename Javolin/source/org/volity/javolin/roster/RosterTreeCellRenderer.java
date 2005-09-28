/*
 * RosterTreeCellRenderer.java
 *
 * Copyright 2004 Karl von Laudermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.volity.javolin.roster;

import java.awt.*;
import javax.swing.*;
import javax.swing.tree.*;

/**
 * Custom tree item renderer for displaying a Jabber user in the roster tree.
 */
public class RosterTreeCellRenderer extends DefaultTreeCellRenderer
{
    private final static ImageIcon AVAILABLE_ICON;
    private final static ImageIcon VOL_AVAILABLE_ICON;
    private final static ImageIcon UNAVAILABLE_ICON;
    private final static ImageIcon BUSY_ICON;
    private final static ImageIcon VOL_BUSY_ICON;

    static
    {
        AVAILABLE_ICON = new ImageIcon(RosterTreeCellRenderer.class.getResource(
            "Avail_TreeIcon.png"));
        VOL_AVAILABLE_ICON = new ImageIcon(RosterTreeCellRenderer.class.getResource(
            "VolAvail_TreeIcon.png"));
        UNAVAILABLE_ICON = new ImageIcon(RosterTreeCellRenderer.class.getResource(
            "Unavail_TreeIcon.png"));
        BUSY_ICON = new ImageIcon(RosterTreeCellRenderer.class.getResource(
            "Busy_TreeIcon.png"));
        VOL_BUSY_ICON = new ImageIcon(RosterTreeCellRenderer.class.getResource(
            "VolBusy_TreeIcon.png"));
    }

    /**
     * Overridden to use the appropriate icon based on the user's availability.
     *
     * @param tree      The JTree that contains the item.
     * @param value     The tree node to render.
     * @param sel       boolean indicating whether the item is selected.
     * @param expanded  boolean indicating whether the item is expanded.
     * @param leaf      boolean indicating whether the item is a leaf node.
     * @param row       The row number of the item.
     * @param hasFocus  boolean indicating whether the item has focus.
     * @return          The <code>Component</code> that the renderer uses to draw the
     *                  value.
     */
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
        boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,
            hasFocus);

        DefaultMutableTreeNode theNode = (DefaultMutableTreeNode)value;

        if (theNode.getUserObject() instanceof RosterTreeItem)
        {
            RosterTreeItem userItem = (RosterTreeItem)(theNode.getUserObject());

            // Set the user icon
            if (userItem.isAvailable())
            {
                if (userItem.isVolityClient())
                {
                    if (userItem.isBusy())
                    {
                        setIcon(VOL_BUSY_ICON);
                    }
                    else
                    {
                        setIcon(VOL_AVAILABLE_ICON);
                    }
                }
                else 
                {
                    if (userItem.isBusy())
                    {
                        setIcon(BUSY_ICON);
                    }
                    else
                    {
                        setIcon(AVAILABLE_ICON);
                    }
                }
            }
            else
            {
                setIcon(UNAVAILABLE_ICON);
            }
        }
        return this;
    }
}
