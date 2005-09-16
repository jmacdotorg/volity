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
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import org.jivesoftware.smack.*;

/**
 * JPanel subclass which contains the roster list and related controls.
 */
public class RosterPanel extends JPanel implements RosterListener, TreeSelectionListener
{
    private JTree mTree;
    private DefaultTreeModel mTreeModel;
    private java.util.List mRosterPanelListeners;
    private boolean mShowUnavailUsers;
    private Roster mRoster;

    /**
     * Constructor.
     */
    public RosterPanel()
    {
        mRosterPanelListeners = new ArrayList();
        buildUI();

        // Set up tree
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Root");
        mTreeModel = new DefaultTreeModel(rootNode);

        mTree.setModel(mTreeModel);
        mTree.setCellRenderer(new RosterTreeCellRenderer());
        mTree.getSelectionModel().setSelectionMode
            (TreeSelectionModel.SINGLE_TREE_SELECTION);
        mTree.setRootVisible(false);
        mTree.addTreeSelectionListener(this);

        // Set up handling of double-clicks on tree items
        mTree.addMouseListener(
            new MouseAdapter()
            {
                public void mousePressed(MouseEvent e)
                {
                    TreePath clickPath = mTree.getPathForLocation(e.getX(), e.getY());
                    if ((clickPath != null) && (e.getClickCount() == 2))
                    {
                        doItemDoubleClick(clickPath);
                    }
                }
            });
    }

    /**
     * Adds a listener for RosterPanel events.
     *
     * @param listener  A RosterPanelListener that will be notified about roster actions.
     */
    public void addRosterPanelListener(RosterPanelListener listener)
    {
        mRosterPanelListeners.add(listener);
    }

    /**
     * Removes a RosterPanel event listener.
     *
     * @param listener  The RosterPanelListener to remove.
     */
    public void removeRosterPanelListener(RosterPanelListener listener)
    {
        mRosterPanelListeners.remove(listener);
    }

    /**
     * Notifies all listeners that have registered interest for notification on this
     * event type.
     *
     * @param e  The RosterPanelEvent to be fired; generated when a node is selected or
     * deselected.
     */
    private void fireRosterPanelSelection(RosterPanelEvent e)
    {
        Iterator iter = mRosterPanelListeners.iterator();

        while (iter.hasNext())
        {
            ((RosterPanelListener)iter.next()).selectionChanged(e);
        }
    }

    /**
     * Notifies all listeners that have registered interest for notification on this
     * event type.
     *
     * @param e  The RosterPanelEvent to be fired; generated when a node is double-
     * clicked.
     */
    private void fireRosterPanelDoubleClick(RosterPanelEvent e)
    {
        Iterator iter = mRosterPanelListeners.iterator();

        while (iter.hasNext())
        {
            ((RosterPanelListener)iter.next()).itemDoubleClicked(e);
        }
    }

    /**
     * Notifies all listeners that have registered interest for notification on this
     * event type.
     *
     * @param e  The RosterPanelEvent to be fired; generated when the roster is right-
     * clicked.
     */
    private void fireRosterPanelContextMenuInvoke(RosterPanelEvent e)
    {
        Iterator iter = mRosterPanelListeners.iterator();

        while (iter.hasNext())
        {
            ((RosterPanelListener)iter.next()).contextMenuInvoked(e);
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
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)(mTreeModel.getRoot());

        // Save selected user ID so we can reselect the node for that user after
        // repopulating
        String selUserId = null;
        if (getSelectedRosterItem() != null)
        {
            selUserId = getSelectedRosterItem().getId();
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
                RosterTreeItem item =
                    new RosterTreeItem(entry, mRoster.getPresence(entry.getUser()));

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
     * Gets the RosterTreeItem for the selected tree node.
     *
     * @return   The selected RosterTreeItem, or null if no item is selected.
     */
    public RosterTreeItem getSelectedRosterItem()
    {
        RosterTreeItem retVal = null;

        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode)(mTree.getLastSelectedPathComponent());

        if ((node != null) && (node.getUserObject() instanceof RosterTreeItem))
        {
            retVal = (RosterTreeItem)node.getUserObject();
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
     * Handler for an item in the tree getting double-clicked.
     *
     * @param path  The TreePath indicating the double-clicked item.
     */
    private void doItemDoubleClick(TreePath path)
    {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();

        if (node.getUserObject() instanceof RosterTreeItem)
        {
            fireRosterPanelDoubleClick(
                new RosterPanelEvent(this, (RosterTreeItem)node.getUserObject()));
        }
    }

    /**
     * RosterListener interface method implementation.
     *
     * Called outside Swing thread!
     */
    public void rosterModified()
    {
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    repopulate();
                }
            });
    }

    /**
     * RosterListener interface method implementation.
     *
     * Called outside Swing thread!
     *
     * @param XMPPAddress  The XMPP address of the user whose presence has changed.
     */
    public void presenceChanged(String XMPPAddress)
    {
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    repopulate();
                }
            });
    }

    /**
     * TreeSelectionListener interface method implementation.
     *
     * @param e  The event that occured.
     */
    public void valueChanged(TreeSelectionEvent e)
    {
        fireRosterPanelSelection(new RosterPanelEvent(this, getSelectedRosterItem()));
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

        mTree.addMouseListener(
            new MouseAdapter()
            {
                public void mousePressed(MouseEvent e)
                {
                    maybeShowPopup(e);
                }

                public void mouseReleased(MouseEvent e)
                {
                    maybeShowPopup(e);
                }
            });
    }

    /**
     * Checks whether the received mouse event was a popup trigger, and if so, selects
     * the clicked item (if any) and fires a context menu event to the RosterPanel
     * listeners.
     *
     * @param e  The event that was triggered.
     */
    private void maybeShowPopup(MouseEvent e)
    {
        if (!e.isPopupTrigger())
        {
            return;
        }

        RosterTreeItem selectedItem = null;

        // Select item under cursor
        TreePath path = mTree.getPathForLocation(e.getX(), e.getY());

        if (path != null)
        {
            mTree.setSelectionPath(path);
            selectedItem = getSelectedRosterItem();
        }

        fireRosterPanelContextMenuInvoke(
            new RosterPanelEvent(this, selectedItem, e.getX(), e.getY()));
    }
}
