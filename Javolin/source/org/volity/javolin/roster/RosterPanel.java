/*
 * RosterPanel.java
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
import java.awt.event.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import org.jivesoftware.smack.*;

/**
 * JPanel subclass which contains the roster list and related controls.
 */
public class RosterPanel extends JPanel implements ActionListener, RosterListener,
    TreeSelectionListener
{
    private final static String NODENAME = "Roster";
    private final static String SHOW_OFFLINE_USERS_KEY = "ShowOfflineUsers";
    private final static ImageIcon SHOW_UNAVAIL_ICON;
    private final static ImageIcon HIDE_UNAVAIL_ICON;

    private JButton mShowHideUnavailBut;
    private JButton mAddUserBut;
    private JButton mDelUserBut;
    private JButton mChatBut;

    private JTree mTree;
    private DefaultTreeModel mTreeModel;

    private boolean mShowUnavailUsers;
    private Roster mRoster;

    static
    {
        SHOW_UNAVAIL_ICON =
            new ImageIcon(RosterPanel.class.getResource("ShowUnavail_ButIcon.png"));
        HIDE_UNAVAIL_ICON =
            new ImageIcon(RosterPanel.class.getResource("HideUnavail_ButIcon.png"));
    }

    /**
     * Constructor.
     */
    public RosterPanel()
    {
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

        // Finish setting up UI
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        mShowUnavailUsers = prefs.getBoolean(SHOW_OFFLINE_USERS_KEY, true);
        updateToolBarButtons();
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
        updateToolBarButtons();
    }

    /**
     * Populates the tree with user items from the roster.
     */
    private void repopulate()
    {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)(mTreeModel.getRoot());

        // Remove all items
        rootNode.removeAllChildren();

        // Populate the tree if the roster is not null
        if (mRoster != null)
        {
            java.util.List unavailUsers = new Vector();
            Iterator usersIter = mRoster.getEntries();

            while (usersIter.hasNext())
            {
                RosterEntry entry = (RosterEntry)usersIter.next();
                UserTreeItem item =
                    new UserTreeItem(entry, mRoster.getPresence(entry.getUser()));

                if (item.isAvailable())
                {
                    // Add available users to tree
                    rootNode.add(new DefaultMutableTreeNode(item));
                }
                else if (mShowUnavailUsers)
                {
                    // Save unavailable users to Vector to add later
                    unavailUsers.add(item);
                }
            }

            // Add unavailable users to tree
            for (int n = 0; n < unavailUsers.size(); n++)
            {
                rootNode.add(new DefaultMutableTreeNode(unavailUsers.get(n)));
            }
        }

        // Ensure the tree redraws
        mTreeModel.nodeStructureChanged(rootNode);
    }

    /**
     * Updates the state and appearance of the toolbar buttons depending on the program
     * state.
     */
    private void updateToolBarButtons()
    {
        // Toggle icon and tool tip text of show/hide unavailable users button
        if (mShowUnavailUsers)
        {
            mShowHideUnavailBut.setIcon(HIDE_UNAVAIL_ICON);
            mShowHideUnavailBut.setToolTipText("Hide offline users");
        }
        else
        {
            mShowHideUnavailBut.setIcon(SHOW_UNAVAIL_ICON);
            mShowHideUnavailBut.setToolTipText("Show offline users");
        }

        // Enable/disable appropriate buttons
        UserTreeItem selectedUser = getSelectedUser();

        mAddUserBut.setEnabled(mRoster != null);
        mDelUserBut.setEnabled(selectedUser != null);
        mChatBut.setEnabled((selectedUser != null) && selectedUser.isAvailable());
    }

    /**
     * Gets the user whose tree node is selected.
     *
     * @return   The selected user item, or null if no item is selected.
     */
    public UserTreeItem getSelectedUser()
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
     * ActionListener interface method implementation.
     *
     * @param e  The action event that occurred.
     */
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == mShowHideUnavailBut)
        {
            doShowHideUnavailBut();
        }
        else if (e.getSource() == mAddUserBut)
        {
        }
        else if (e.getSource() == mDelUserBut)
        {
        }
        else if (e.getSource() == mChatBut)
        {
        }
    }

    /**
     * Handler for show/hide unavailable users button.
     */
    private void doShowHideUnavailBut()
    {
        mShowUnavailUsers = !mShowUnavailUsers;

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);
        prefs.putBoolean(SHOW_OFFLINE_USERS_KEY, mShowUnavailUsers);

        repopulate();
        updateToolBarButtons();
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
        updateToolBarButtons();
    }

    /**
     * Creates and sets up the UI objects in the panel.
     */
    private void buildUI()
    {
        setLayout(new BorderLayout());

        // Create toolbar at the top of the panel
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        add(toolbar, BorderLayout.NORTH);

        // Create toolbar buttons
        mShowHideUnavailBut = new JButton(HIDE_UNAVAIL_ICON);
        mShowHideUnavailBut.addActionListener(this);
        toolbar.add(mShowHideUnavailBut);

        ImageIcon image =
            new ImageIcon(RosterPanel.class.getResource("AddUser_ButIcon.png"));
        mAddUserBut = new JButton(image);
        mAddUserBut.setToolTipText("Add user");
        mAddUserBut.addActionListener(this);
        toolbar.add(mAddUserBut);

        image = new ImageIcon(RosterPanel.class.getResource("DeleteUser_ButIcon.png"));
        mDelUserBut = new JButton(image);
        mDelUserBut.setToolTipText("Delete user");
        mDelUserBut.addActionListener(this);
        toolbar.add(mDelUserBut);

        image = new ImageIcon(RosterPanel.class.getResource("Chat_ButIcon.png"));
        mChatBut = new JButton(image);
        mChatBut.setToolTipText("Chat with user");
        mChatBut.addActionListener(this);
        toolbar.add(mChatBut);

        // Create roster item tree
        mTree = new JTree();
        JScrollPane scrollPane = new JScrollPane(mTree);
        add(scrollPane, BorderLayout.CENTER);
    }
}
