/*
 * MUCWindow.java
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
package org.volity.javolin.chat;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.util.Date;
import java.util.Iterator;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.MUCUser;
import org.volity.client.data.JIDTransfer;
import org.volity.javolin.*;

/**
 * A window for participating in a MUC.
 */
public class MUCWindow extends JFrame 
    implements PacketListener, WindowMenu.GetWindowName, CloseableWindow
{
    private final static String NODENAME = "MUCWindow";
    private final static String CHAT_SPLIT_POS = "ChatSplitPos";
    private final static String USERLIST_SPLIT_POS = "UserListSplitPos";

    private JSplitPane mChatSplitter;
    private JSplitPane mUserListSplitter;
    private ChatLogPanel mLog;
    private JTextArea mInputText;
    private UserPanel mUserList;
    private AbstractAction mSendMessageAction;

    private UserColorMap mColorMap;

    private String mWindowName;
    private SizeAndPositionSaver mSizePosSaver;
    private XMPPConnection mConnection;
    private MultiUserChat mMucObject;

    private ChangeListener mColorChangeListener;

    /**
     * Constructor.
     *
     * @param connection         The current active XMPPConnection.
     * @param mucId              The ID of the MUC to create and join.
     * @param nickname           The nickname to use to join the MUC.
     * @exception XMPPException  If an error occurs joining the room.
     */
    public MUCWindow(XMPPConnection connection, String mucId, String nickname)
         throws XMPPException
    {
        super(JavolinApp.getAppName() + ": " + mucId);
        mWindowName = "Chat Room: " + StringUtils.parseName(mucId);

        mConnection = connection;
        mMucObject = new MultiUserChat(connection, mucId);

        mColorMap = new UserColorMap();
        // Give user first color
        mColorMap.getUserNameColor(mConnection.getUser());

        buildUI();

        setSize(500, 400);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        restoreWindowState();

        // Send message when user presses Enter while editing input text
        mSendMessageAction =
            new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    doSendMessage();
                }
            };

        mInputText.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            mSendMessageAction);

        // Handle window events
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter()
            {
                public void windowClosed(WindowEvent we) {
                    // Leave the chat room when the window is closed
                    saveWindowState();
                    leave();
                }

                public void windowOpened(WindowEvent we) {
                    // Give focus to input text area when the window is created
                    mInputText.requestFocusInWindow();
                }
            });

        mColorChangeListener = new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    updateUserList();
                }
            };
        mColorMap.addListener(mColorChangeListener);

        // Register as message listener.
        mMucObject.addMessageListener(this);

        // Register as participant listener.
        mMucObject.addParticipantListener(this);

        // Last but not least, join the MUC
        if (mucExists())
        {
            mMucObject.join(nickname);
        }
        else
        {
            mMucObject.create(nickname);
            configureMuc();
        }
    }

    /**
     * Clean up everything we tried to create. This is called when the window
     * closes, or if there's a creation error and it never gets opened.
     */
    protected void leave()
    {
        if (mLog != null) {
            mLog.dispose();
        }

        if (mUserList != null) {
            mUserList.dispose();
            mUserList = null;
        }

        mColorMap.removeListener(mColorChangeListener);
        mColorMap.dispose();

        if (mMucObject != null) {
            // Deregister listeners
            mMucObject.removeMessageListener(this);
            mMucObject.removeParticipantListener(this);

            mMucObject.leave();

            /* It is usually a mistake to call any object's finalize() method,
             * but I have no choice. MultiUserChat is written so that all its
             * internal packet filters are cleaned up in the finalize() method.
             * I need to clean those up immediately -- otherwise they hang on
             * the XMPPConnection forever, and the MUC never gets garbage-
             * collected.
             *
             * When the object *is* garbage-collected, Java will call
             * finalize() a second time. Fortunately, MultiUserChat doesn't
             * choke on that. */
            try {
                mMucObject.finalize();
            }
            catch (Throwable ex) { }
            mMucObject = null;
        }
    }

    /**
     * Tells whether the MUC exists on the chat service.
     *
     * @return   true if the MUC exists on the chat service, false otherwise.
     */
    protected boolean mucExists()
    {
        ServiceDiscoveryManager discoMan =
            ServiceDiscoveryManager.getInstanceFor(mConnection);
        try
        {
            // If the room exists, it must answer to service discovery.
            // We don't actually care what the answer is.
            // ### ought to be async.
            discoMan.discoverInfo(mMucObject.getRoom());
            return true;
        }
        catch (XMPPException ex)
        {
            return false;
        }
    }

    /**
     * Configure the MUC by accepting the server default settings.
     *
     * @throws XMPPException  if an error occurs while configuring.
     */
    protected void configureMuc() throws XMPPException
    {
        // A blank form indicates that we accept the server default settings.
        Form blankForm = new Form(Form.TYPE_SUBMIT);
        mMucObject.sendConfigurationForm(blankForm);
    }

    /**
     * Saves window state to the preferences storage, including window size and position,
     * and splitter bar positions.
     */
    private void saveWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.saveSizeAndPosition();

        prefs.putInt(CHAT_SPLIT_POS, mChatSplitter.getDividerLocation());
        prefs.putInt(USERLIST_SPLIT_POS, mUserListSplitter.getDividerLocation());
    }

    /**
     * Restores window state from the preferences storage, including window size and 
     * position, and splitter bar positions.
     */
    private void restoreWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.restoreSizeAndPosition();

        mChatSplitter.setDividerLocation(prefs.getInt(CHAT_SPLIT_POS, getHeight() - 100));
        mUserListSplitter.setDividerLocation(prefs.getInt(USERLIST_SPLIT_POS,
            getWidth() - 100));
    }

    /*
     * Returns the name of the MUC room.
     */
    public String getRoom() 
    {
        return mMucObject.getRoom();
    }

    // Implements GetWindowName.
    public String getWindowName() 
    {
        return mWindowName;
    }

    /**
     * Updates the list of users in the MUC.
     */
    private void updateUserList()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        if (mUserList == null)
            return;

        mUserList.removeAll();
        Iterator iter = mMucObject.getOccupants();

        GridBagConstraints c;
        int row = 0;

        while (iter.hasNext()) {
            String jid = (String)iter.next();
            Occupant occ = mMucObject.getOccupant(jid);
            String nick = occ.getNick();
            String realAddr = occ.getJid();

            // For an anonymous MUC, color by full MUC JID. (but no popup)
            if (realAddr == null)
                realAddr = jid;

            Color col = mColorMap.getUserNameColor(realAddr);

            JLabelPop label = new JLabelPop(realAddr, 
                nick, SwingConstants.LEFT);
            label.setFont(UserPanel.sLabelFont);
            label.setForeground(col);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.ipady = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            mUserList.add(label, c);
        }

        // Stretchy blank label
        JLabel label = new JLabel(" ", SwingConstants.LEFT);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.weighty = 1;
        c.ipady = 1;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.NORTHWEST;
        mUserList.add(label, c);

        mUserList.revalidate();
    }

    /**
     * PacketListener interface method implementation.
     * (Used by both addMessageListener and addParticipantListener.)
     *
     * Called outside Swing thread!
     *
     * @param packet  The packet received.
     */
    public void processPacket(final Packet packet)
    {
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (packet instanceof Message)
                    {
                        doMessageReceived((Message)packet);
                    }
                    else if (packet instanceof Presence)
                    {
                        Presence pres = (Presence)packet;

                        String from = pres.getFrom();

                        String nick = null;
                        String realAddr = null;
                        boolean realjid = false;

                        PacketExtension ext = pres.getExtension("x",
                            "http://jabber.org/protocol/muc#user");
                        if (ext != null && ext instanceof MUCUser) {
                            MUCUser userext = (MUCUser)ext;
                            MUCUser.Item item = userext.getItem();
                            if (item != null) {
                                realAddr = item.getJid();
                                realjid = true;
                                nick = item.getNick();
                            }
                        }

                        if (realAddr == null && from != null) {
                            realAddr = from;
                            realjid = false;
                        }

                        if (nick == null && from != null) {
                            nick = StringUtils.parseResource(from);
                        }

                        if (realAddr != null && nick != null) {
                            Presence.Type typ = pres.getType();
                            if (typ == Presence.Type.AVAILABLE) {
                                boolean already = mUserList.isJIDPresent(realAddr);
                                if (!already) {
                                    mLog.message(realAddr, realjid, null,
                                        nick+" has joined the chat.");
                                    Audio.playPresenceIn();
                                }
                            }
                            if (typ == Presence.Type.UNAVAILABLE) {
                                mLog.message(realAddr, realjid, null,
                                    nick+" has left the chat.");
                                Audio.playPresenceOut();
                            }
                        }

                        /* Do this last, because otherwise it would mess up the
                         * "is user already present?" computation above. */
                        updateUserList();
                    }
                }
            });
    }

    /**
     * Sends the message that the user typed in the input text area.
     */
    private void doSendMessage()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        try
        {
            mMucObject.sendMessage(mInputText.getText());
            mInputText.setText("");
        }
        catch (XMPPException ex)
        {
            new ErrorWrapper(ex);
            JOptionPane.showMessageDialog(this, ex.toString(),
                JavolinApp.getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handles a message received from the MUC.
     *
     * @param msg  The Message object that was received.
     */
    private void doMessageReceived(Message msg)
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (msg.getType() == Message.Type.ERROR)
        {
            JOptionPane.showMessageDialog(this, msg.getError().getMessage(),
                JavolinApp.getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);
        }
        else if (msg.getType() == Message.Type.HEADLINE)
        {
            // Do nothing
        }
        else
        {
            String from = msg.getFrom();

            Occupant occ = mMucObject.getOccupant(from);

            String nick;
            String realAddr;
            boolean realjid;

            if (occ != null) {
                nick = occ.getNick();
                realAddr = occ.getJid();
                realjid = true;
            }
            else {
                nick = StringUtils.parseResource(from);
                realAddr = from;
                realjid = false;
            }

            if (realAddr == null) {
                realAddr = from;
                realjid = false;
            }

            Date date = null;
            PacketExtension ext = msg.getExtension("x", "jabber:x:delay");
            if (ext != null && ext instanceof DelayInformation) {
                date = ((DelayInformation)ext).getStamp();
            }

            mLog.message(realAddr, realjid, nick, msg.getBody(), date);
            if (ext == null)
                Audio.playMessage();
        }
    }

    /**
     * Populates the frame with UI controls.
     */
    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());

        // Split pane for message text area and input text area
        mChatSplitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mChatSplitter.setResizeWeight(1);
        mChatSplitter.setBorder(BorderFactory.createEmptyBorder());

        mLog = new ChatLogPanel(mColorMap);
        mChatSplitter.setTopComponent(mLog);

        mInputText = new JTextArea();
        mInputText.setLineWrap(true);
        mInputText.setWrapStyleWord(true);
        mInputText.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        mChatSplitter.setBottomComponent(new JScrollPane(mInputText));

        // Split pane separating user list from everything else
        mUserListSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mUserListSplitter.setResizeWeight(1);

        mUserListSplitter.setLeftComponent(mChatSplitter);

        mUserList = new UserPanel();
        mUserList.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        mUserListSplitter.setRightComponent(new JScrollPane(mUserList));

        cPane.add(mUserListSplitter, BorderLayout.CENTER);

        // Necessary for all windows, for Mac support
        AppMenuBar.applyPlatformMenuBar(this);
    }

    protected static class UserPanel extends JPanel {
        static public DragSource dragSource = DragSource.getDefaultDragSource();
        static public Font sLabelFont = new Font("SansSerif", Font.PLAIN, 12);

        protected UserContextMenu mPopupMenu = null;

        public UserPanel() {
            super(new GridBagLayout());

            setOpaque(true);
            setBackground(Color.WHITE);

            mPopupMenu = new UserContextMenu();
        }        

        /** Clean up component. */
        public void dispose() {
            if (mPopupMenu != null) {
                mPopupMenu.dispose();
                mPopupMenu = null;
            }
        }

        /**
         * Pop up a contextual menu for the given JID.
         */
        protected void displayPopupMenu(String jid, int xp, int yp) {
            if (mPopupMenu != null) {
                Point pt = getLocationOnScreen();
                mPopupMenu.adjustShow(jid, this, xp-pt.x, yp-pt.y);
            }
        }

        /**
         * Check whether this JID is in the current list.
         */
        public boolean isJIDPresent(String jid) {
            Object[] ls = getComponents();
            for (int ix=0; ix<ls.length; ix++) {
                if (ls[ix] instanceof JLabelPop) {
                    JLabelPop label = (JLabelPop)ls[ix];
                    if (label.mJID.equals(jid))
                        return true;
                }
            }
            return false;
        }
    }

    /** Extension of JLabel which can pop up a contextual menu. */
    protected class JLabelPop extends JLabel {

        String mJID;

        public JLabelPop(String jid,
            String text, int horizontalAlignment) {
            super(text, horizontalAlignment);
            mJID = jid;

            DragGestureRecognizer recognizer = 
                UserPanel.dragSource.createDefaultDragGestureRecognizer(
                    this, 
                    DnDConstants.ACTION_MOVE, 
                    new DragSourceThing(this, jid));
        }

        protected void processMouseEvent(MouseEvent ev) {
            if (ev.isPopupTrigger()) {
                if (mUserList != null) {
                    Point pt = getLocationOnScreen();
                    mUserList.displayPopupMenu(mJID, pt.x+ev.getX(), pt.y+ev.getY());
                }
                return;
            }
            
            super.processMouseEvent(ev);
        }
        
    }

    protected class DragSourceThing 
        implements DragGestureListener {
        JComponent mComponent;
        String mJID;
        boolean mWasOpaque;

        Color mDragColor = new Color(0.866f, 0.866f, 0.92f);

        public DragSourceThing(JComponent obj, String jid) {
            mComponent = obj;
            mJID = jid;
        }

        public void dragGestureRecognized(DragGestureEvent ev) {
            // Highlight the source label.
            mWasOpaque = mComponent.isOpaque();
            mComponent.setBackground(mDragColor);
            mComponent.setOpaque(true);

            Transferable transfer = new JIDTransfer(mJID);
            UserPanel.dragSource.startDrag(ev, DragSource.DefaultMoveDrop, transfer,
                new DragSourceAdapter() {
                    public void dragDropEnd(DragSourceDropEvent ev) {
                        // Unhighlight drag target.
                        mComponent.setOpaque(mWasOpaque);
                        mComponent.setBackground(null);
                    }
                });
        }
    }

}
