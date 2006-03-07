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
import java.awt.event.*;
import java.text.*;
import java.util.Date;
import java.util.Iterator;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.*;
import org.jivesoftware.smackx.*;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.MUCUser;
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
    private JTextPanePopup mUserListText;
    private AbstractAction mSendMessageAction;

    private UserColorMap mColorMap;
    private SimpleAttributeSet mBaseUserListStyle;

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

        mBaseUserListStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(mBaseUserListStyle, "SansSerif");
        StyleConstants.setFontSize(mBaseUserListStyle, 12);

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

        if (mUserListText != null) {
            mUserListText.dispose();
            mUserListText = null;
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
        if (mUserListText == null)
            return;

        mUserListText.setText("");
        Iterator iter = mMucObject.getOccupants();

        while (iter.hasNext()) {
            String jid = (String)iter.next();
            Occupant occ = mMucObject.getOccupant(jid);
            String nick = occ.getNick();
            String realAddr = occ.getJid();

            // For an anonymous MUC, color by full MUC JID. (but no popup)
            if (realAddr == null)
                realAddr = jid;

            SimpleAttributeSet style = new SimpleAttributeSet(mBaseUserListStyle);
            StyleConstants.setForeground(style,
                mColorMap.getUserNameColor(realAddr));
            if (occ.getJid() != null)
                style.addAttribute(ChatLogPanel.ATTR_JID, occ.getJid());

            Document doc = mUserListText.getDocument();

            try
            {
                doc.insertString(doc.getLength(), nick + "\n", style);
            }
            catch (BadLocationException ex)
            {
            }
        }
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
                        updateUserList();

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
                                mLog.message(realAddr, realjid, null,
                                    nick+" has joined the chat.");
                                Audio.playPresenceIn();
                            }
                            if (typ == Presence.Type.UNAVAILABLE) {
                                mLog.message(realAddr, realjid, null,
                                    nick+" has left the chat.");
                                Audio.playPresenceOut();
                            }
                        }
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

        mUserListText = new JTextPanePopup();
        mUserListText.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        mUserListText.setEditable(false);
        mUserListSplitter.setRightComponent(new JScrollPane(mUserListText));

        cPane.add(mUserListSplitter, BorderLayout.CENTER);

        // Necessary for all windows, for Mac support
        JavolinMenuBar.applyPlatformMenuBar(this);
    }


    /**
     * Subclass of JTextPane that has a pop-up menu.
     */
    protected static class JTextPanePopup extends JTextPane {
        protected UserContextMenu mPopupMenu = null;

        public JTextPanePopup() {
            mPopupMenu = new UserContextMenu();
        }

        /** Clean up component. */
        public void dispose() {
            if (mPopupMenu != null) {
                mPopupMenu.dispose();
                mPopupMenu = null;
            }
        }

        /** Customized mouse handler that knows about popup clicks. */
        protected void processMouseEvent(MouseEvent ev) {
            if (ev.isPopupTrigger()) {
                if (mPopupMenu != null) {
                    displayPopupMenu(ev.getX(), ev.getY());
                }
                return;
            }
            
            super.processMouseEvent(ev);
        }

        /**
         * Given a popup-inducing click at a particular location, see if
         * there's a JID associated with that location. If so, pop up a
         * contextual menu.
         */
        protected void displayPopupMenu(int xp, int yp) {
            int pos = viewToModel(new Point(xp, yp));
            StyledDocument doc = getStyledDocument();
            Element el = doc.getCharacterElement(pos);
            if (el == null)
                return;
            AttributeSet attrs = el.getAttributes();
            if (attrs == null)
                return;
            String jid = (String)attrs.getAttribute(ChatLogPanel.ATTR_JID);
            if (jid == null)
                return;

            mPopupMenu.adjustShow(jid, this, xp, yp);
        }

    }
}
