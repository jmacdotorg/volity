/*
 * RosterPanel.java
 *
 * Copyright 2004-2005 Karl von Laudermann
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
import java.awt.event.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import org.jivesoftware.smack.*;
import org.volity.javolin.*;

/**
 * JPanel subclass which contains the roster list and related controls.
 */
public class RosterPanel extends JPanel implements RosterListener, TreeSelectionListener
{
    private JTree mTree;
    private DefaultTreeModel mTreeModel;
    private java.util.List mSelectionListeners;
    private boolean mShowUnavailUsers;
    private Roster mRoster;

    /**
     * Constructor.
     */
    public RosterPanel()
    {
        mSelectionListeners = new ArrayList();
        buildUI();

        // Set up tree
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Root");
        mTreeModel = new DefaultTreeModel(rootNode);

        mTree.setModel(mTreeModel);
        mTree.setCellRenderer(new UserTreeCellRenderer());
        mTree.getSelectionModel().setSelectionMode
            (TreeSelectionModel.SINGLE_TREE_SELECTION);
        mTree.setRootVisible(false);
        mTree.addTreeSelectionListener(this);
    }

    /**
     * Adds a listener for RosterPanel selection events.
     *
     * @param listener  A RosterPanelSelectionListener that will be notified when a node
     * is selected or deselected.
     */
    public void addRosterPanelSelectionListener(RosterPanelSelectionListener listener)
    {
        mSelectionListeners.add(listener);
    }

    /**
     * Removes a RosterPanel selection event listener.
     *
     * @param listener  The RosterPanelSelectionListener to remove.
     */
    public void removeRosterPanelSelectionListener(
        RosterPanelSelectionListener listener)
    {
        mSelectionListeners.remove(listener);
    }

    /**
     * Notifies all listeners that have registered interest for notification on this
     * event type.
     *
     * @param e  The RosterPanelSelectionEvent to be fired; generated when a node is
     * selected or deselected.
     */
    private void fireRosterPanelSelection(RosterPanelSelectionEvent e)
    {
        Iterator iter = mSelectionListeners.iterator();

        while (iter.hasNext())
        {
            ((RosterPanelSelectionListener)iter.next()).valueChanged(e);
        }
    }

    /**
     * Sets the roster object for the panel.
     *
     * @param roster  The roster object. This value can be null, which generally means
     * that the Javolin client is not currently connected to an XMPP server. In this
     * case, the roster list will be cleared.
     */
    public void setRoster(Roster roster)
    {
        // Remove this as RosterListener from old roster
        if (mRoster != null)
        {
            mRoster.removeRosterListener(this);
        }

        // Accept new roster
        mRoster = roster;

        if (mRoster != null)
        {
            mRoster.addRosterListener(this);
        }

        repopulate();
    }

    /**
     * Populates the tree with user items from the roster.
     */
    private void repopulate()
    {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)(mTreeModel.getRoot());

        // Save selected user ID so we can reselect the node for that user after
        // repopulating
        String selUserId = null;
        if (getSelectedUserItem() != null)
        {
            selUserId = getSelectedUserItem().getId();
        }

        TreeNode nodeToSelect = null;

        // Remove all items
        rootNode.removeAllChildren();

        // Populate the tree if the roster is not null
        if (mRoster != null)
        {
            java.util.List unavailUsers = new Vector();
            Iterator usersIter = mRoster.getEntries();

            MutableTreeNode newNode = null;

            while (usersIter.hasNext())
            {
                RosterEntry entry = (RosterEntry)usersIter.next();
                UserTreeItem item =
                    new UserTreeItem(entry, mRoster.getPresence(entry.getUser()));

                newNode = new DefaultMutableTreeNode(item);

                // Remember node if it's the one to select after populating, and the item
                // will be visible in the tree
                if (((selUserId != null) && (item.getId().equals(selUserId))) && 
                    (item.isAvailable() || mShowUnavailUsers))
                {
                    nodeToSelect = newNode;
                }

                if (item.isAvailable())
                {
                    // Add available users to tree
                    rootNode.add(newNode);
                }
                else if (mShowUnavailUsers)
                {
                    // Save unavailable users to List to add later
                    unavailUsers.add(newNode);
                }
            }

            // Add unavailable users to tree
            for (int n = 0; n < unavailUsers.size(); n++)
            {
                rootNode.add((MutableTreeNode)unavailUsers.get(n));
            }
        }

        // Ensure the tree redraws
        mTreeModel.nodeStructureChanged(rootNode);

        // Reselect previously selected user
        if (nodeToSelect != null)
        {
            mTree.setSelectionPath(new TreePath(mTreeModel.getPathToRoot(nodeToSelect)));
        }
    }

    /**
     * Gets the UserTreeItem for the selected tree node.
     *
     * @return   The selected UserTreeItem, or null if no item is selected.
     */
    public UserTreeItem getSelectedUserItem()
    {
        UserTreeItem retVal = null;

        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode)(mTree.getLastSelectedPathComponent());

        if ((node != null) &&
            (node.getUserObject() instanceof UserTreeItem))
        {
            retVal = (UserTreeItem)node.getUserObject();
        }

        return retVal;
    }

    /**
     * Sets whether to display users that are unavailable.
     *
     * @param show  true to show unavailable users, false to hide them.
     */
    public void setShowUnavailableUsers(boolean show)
    {
        mShowUnavailUsers = show;
        repopulate();
    }

    /**
     * RosterListener interface method implementation.
     */
    public void rosterModified()
    {
        repopulate();
    }

    /**
     * RosterListener interface method implementation.
     *
     * @param XMPPAddress  The XMPP address of the user whose presence has changed.
     */
    public void presenceChanged(String XMPPAddress)
    {
        repopulate();
    }

    /**
     * TreeSelectionListener interface method implementation.
     *
     * @param e  The event that occured.
     */
    public void valueChanged(TreeSelectionEvent e)
    {
        fireRosterPanelSelection(new RosterPanelSelectionEvent(this,
            getSelectedUserItem()));
    }

    /**
     * Creates and sets up the UI objects in the panel.
     */
    private void buildUI()
    {
        setLayout(new BorderLayout());

        // Create roster item tree
        mTree = new JTree();
        JScrollPane scrollPane = new JScrollPane(mTree);
        add(scrollPane, BorderLayout.CENTER);
    }
}
