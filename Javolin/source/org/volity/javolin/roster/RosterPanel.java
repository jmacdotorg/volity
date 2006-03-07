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
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.util.StringUtils;
import org.volity.client.comm.CapPacketExtension;
import org.volity.client.data.JIDTransfer;
import org.volity.javolin.CapPresenceFactory;
import org.volity.javolin.PrefsDialog;

/**
 * JPanel subclass which contains the roster list and related controls.
 */
public class RosterPanel extends JPanel implements RosterListener, TreeSelectionListener
{
    static DragSource dragSource = DragSource.getDefaultDragSource();

    private JTree mTree;
    private DefaultTreeModel mTreeModel;
    private List mRosterPanelListeners;
    private Roster mRoster;

    private ChangeListener mPrefsChangeListener;

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

        mPrefsChangeListener = new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    repopulate();
                }
            };
        PrefsDialog.addListener(PrefsDialog.ROSTER_DISPLAY_OPTIONS,
            mPrefsChangeListener);
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

        boolean showoffline = PrefsDialog.getRosterShowOffline();
        boolean showfroms = PrefsDialog.getRosterShowReverse();

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
            List userList = new ArrayList();
            Iterator usersIter = mRoster.getEntries();

            MutableTreeNode newNode = null;

            while (usersIter.hasNext())
            {
                RosterEntry entry = (RosterEntry)usersIter.next();
                Presence packet = mRoster.getPresence(entry.getUser());

                // Is this user logged on via a Volity player client?
                String role = getVolityClientRole(entry.getUser());

                RosterTreeItem item = new RosterTreeItem(entry, packet, role);

                newNode = new DefaultMutableTreeNode(item);

                RosterPacket.ItemType subtype = entry.getType();
                boolean isavail = item.isAvailable();

                // Do we not display this one?
                if (subtype == RosterPacket.ItemType.NONE)
                    continue;
                if (subtype == RosterPacket.ItemType.FROM) {
                    if (!showfroms) 
                        continue;
                }
                else {
                    // Don't check availability for FROM users
                    if (!isavail && !showoffline)
                        continue;
                }

                // Remember node if it's the one to select after populating
                if (selUserId != null && item.getId().equals(selUserId))
                {
                    nodeToSelect = newNode;
                }

                userList.add(newNode);
            }

            Object[] userArr = userList.toArray();
            Arrays.sort(userArr, new Comparator() {
                    public int compare(Object o1, Object o2) {
                        DefaultMutableTreeNode n1 = (DefaultMutableTreeNode)o1;
                        DefaultMutableTreeNode n2 = (DefaultMutableTreeNode)o2;
                        RosterTreeItem it1 = (RosterTreeItem)n1.getUserObject();
                        RosterTreeItem it2 = (RosterTreeItem)n2.getUserObject();
                        if (it1 == it2)
                            return 0;

                        RosterPacket.ItemType type1 = it1.getSubType();
                        RosterPacket.ItemType type2 = it2.getSubType();
                        boolean isavail1 = it1.isAvailable();
                        boolean isavail2 = it2.isAvailable();
                        String role1 = it1.getVolityRole();
                        String role2 = it2.getVolityRole();

                        int grp1 = 1;
                        if (type1 == RosterPacket.ItemType.FROM)
                            grp1 = 4;
                        else if (!isavail1)
                            grp1 = 3;
                        else if (role1 == CapPresenceFactory.VOLITY_ROLE_PARLOR)
                            grp1 = 2;
                        int grp2 = 1;
                        if (type2 == RosterPacket.ItemType.FROM)
                            grp2 = 4;
                        else if (!isavail2)
                            grp2 = 3;
                        else if (role2 == CapPresenceFactory.VOLITY_ROLE_PARLOR)
                            grp2 = 2;

                        if (grp1 != grp2)
                            return grp1-grp2;

                        String id1 = it1.getId();
                        String id2 = it2.getId();
                        return id1.compareTo(id2);
                    }
                });

            for (int ix=0; ix<userArr.length; ix++)
                rootNode.add((MutableTreeNode)userArr[ix]);
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
     * Return whether the given user is on the client's roster.
     */
    public boolean isUserOnRoster(String user)
    {
        RosterEntry entry = mRoster.getEntry(user);
        if (entry != null) {
            RosterPacket.ItemType subtype = entry.getType();
            if (subtype == RosterPacket.ItemType.NONE)
                return false;
            if (subtype == RosterPacket.ItemType.FROM)
                return false;
            return true;
        }
        return false;
    }

    /**
     * Add a user to the roster. If nickname is null, the nickname is parsed
     * from the JID name.
     */
    public void addUserToRoster(String user, String nickname)
        throws XMPPException
    {
        if (nickname == null) {
            nickname = StringUtils.parseName(user);
            if (nickname.equals(""))
                nickname = user;
        }

        mRoster.createEntry(user, nickname, null);
    }

    /**
     * Return a list of the resources of the given user which are Volity player
     * clients. (A list of full JID strings.) If the user is present but not
     * with a Volity client, this returns an empty list. If the user is
     * offline, or not on your roster, this returns null.
     *
     * @return the list, or null.
     */
    public List listVolityClientResources(String user)
    {
        Iterator iter = mRoster.getPresences(user);
        if (iter == null)
            return null;

        List ls = new ArrayList();
        while (iter.hasNext())
        {
            Presence packet = (Presence)iter.next();
            PacketExtension ext = packet.getExtension(CapPacketExtension.NAME,
                CapPacketExtension.NAMESPACE);
            if (ext != null && ext instanceof CapPacketExtension) 
            {
                CapPacketExtension extp = (CapPacketExtension)ext;
                if (extp.getNode().equals(CapPresenceFactory.VOLITY_NODE_URI)
                    && extp.hasExtString(CapPresenceFactory.VOLITY_ROLE_PLAYER)) 
                {
                    // Whew. It's a Volity client.
                    ls.add(packet.getFrom());
                }
            }
        }

        return ls;
    }

    /**
     * Determine what sort of Volity entity the given user is. The result will
     * be one of the constants from CapPresenceFactory: VOLITY_ROLE_PLAYER,
     * etc.
     *
     * If the user is logged on more than once, this only finds one of his
     * roles. If the user is logged on but not with a Volity client, this
     * returns VOLITY_ROLE_NONE. If the user is offline, or not on your roster,
     * this returns null.
     */
    public String getVolityClientRole(String user) {
        Iterator iter = mRoster.getPresences(user);
        if (iter == null)
            return null;

        String[] roles = CapPresenceFactory.VOLITY_ROLES;

        while (iter.hasNext())
        {
            Presence packet = (Presence)iter.next();
            PacketExtension ext = packet.getExtension(CapPacketExtension.NAME,
                CapPacketExtension.NAMESPACE);
            if (ext != null && ext instanceof CapPacketExtension) 
            {
                CapPacketExtension extp = (CapPacketExtension)ext;
                if (extp.getNode().equals(CapPresenceFactory.VOLITY_NODE_URI)) 
                {
                    for (int ix=0; ix<roles.length; ix++) 
                    {
                        if (extp.hasExtString(roles[ix]))
                            return roles[ix];
                    }
                }
            }
        }

        return CapPresenceFactory.VOLITY_ROLE_NONE;
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

        dragSource.createDefaultDragGestureRecognizer(
            mTree, 
            DnDConstants.ACTION_COPY, 
            new DragSourceThing());
    }

    private class DragSourceThing 
        implements DragGestureListener {
        public void dragGestureRecognized(DragGestureEvent ev) {
            Point origin = ev.getDragOrigin();
            TreePath path = mTree.getPathForLocation(origin.x, origin.y);
            if (path == null)
                return;
            
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            if (!(node.getUserObject() instanceof RosterTreeItem))
                return;
            RosterTreeItem item = (RosterTreeItem)node.getUserObject();

            Transferable transfer = new JIDTransfer(item.getId());
            dragSource.startDrag(ev, DragSource.DefaultMoveDrop, transfer,
                new DragSourceAdapter() {
                });
        }
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
