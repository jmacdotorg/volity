/*
 * ChatWindow.java
 *
 * Copyright 2005 Karl von Laudermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.volity.javolin.chat;

import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.text.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.StringUtils;
import org.volity.javolin.*;

/**
 * A window for participating in a one-on-one chat.
 */
public class ChatWindow extends JFrame
    implements PacketListener, WindowMenu.GetWindowName, CloseableWindow
{
    private final static String NODENAME = "ChatWindow";
    private final static String CHAT_SPLIT_POS = "ChatSplitPos";

    /* It is desirable to track the last known resource of each JID that
     * chatted with us. (This helps us send outgoing messages to an appropriate
     * resource.)
     *
     * We do this with a table that maps bare JIDs to full JIDs.
     *
     * We're not worrying about thread-safety here, so please only call these
     * functions from the Swing thread.
     */
    static private Map sResourceTracker = new HashMap();

    /** Put this full JID into the mapping. */
    static public void setLastKnownResource(String jid) {
        String barejid = StringUtils.parseBareAddress(jid);
        sResourceTracker.put(barejid, jid);
    }

    /**
     * Remove this JID from the mapping. If the JID has a resource, only remove
     * it if the resource matches.
     */
    static public void clearLastKnownResource(String jid) {
        String barejid = StringUtils.parseBareAddress(jid);

        if (!jid.equals(barejid)) {
            String fulljid = (String)sResourceTracker.get(barejid);
            if (fulljid == null)
                return;
            if (!fulljid.equals(jid))
                return;
        }

        sResourceTracker.remove(barejid);
    }

    /**
     * Given a bare JID, if it is in the mapping, return the associated full
     * JID. If not, return the bare JID.
     */
    static public String applyLastKnownResource(String barejid) {
        String jid = (String)sResourceTracker.get(barejid);
        if (jid != null)
            return jid;
        return barejid;
    }


    private JSplitPane mChatSplitter;
    private ChatLogPanel mLog;
    private JTextArea mInputText;
    private AbstractAction mSendMessageAction;

    private UserColorMap mColorMap;

    private SizeAndPositionSaver mSizePosSaver;
    private XMPPConnection mConnection;
    private BetterChat mChatObject;
    private String mLocalId;
    private String mRemoteIdBare;
    private String mRemoteIdFull;
    private String mRemoteNick;

    /*
     * We no longer need to call setFilteredOnThreadID. The current version of
     * Smack handles unthreaded messages automatically. See
     * http://www.jivesoftware.org/community/thread.jspa?messageID=104051
     */

    /**
     * Constructor.
     *
     * @param connection         The current active XMPPConnection.
     * @param remoteId           The ID of the user to chat with. (May have
     *     a resource, but this will be used only as a hint for sending
     *     replies.)
     * @exception XMPPException  If an error occurs joining the room.
     */
    public ChatWindow(XMPPConnection connection, String remoteId)
        throws XMPPException
    {
        mConnection = connection;
        mRemoteIdFull = remoteId;
        mRemoteIdBare = StringUtils.parseBareAddress(remoteId);

        if (!mRemoteIdFull.equals(mRemoteIdBare))
            setLastKnownResource(mRemoteIdFull);

        mChatObject = new BetterChat(mConnection, mRemoteIdFull);
        
        // Get nickname for remote user and use for window title
        RosterEntry entry = mConnection.getRoster().getEntry(mRemoteIdBare);
        
        if (entry != null)
        {
            mRemoteNick = entry.getName();
        }
        
        if (mRemoteNick == null || mRemoteNick.equals(""))
        {
            mRemoteNick = mRemoteIdBare;
        }
        
        String val = JavolinApp.resources.getString("ChatWindow_WindowTitle");
        setTitle(JavolinApp.getAppName() + ": " + val + " " + mRemoteNick);

        // Get local user ID and chat color
        mLocalId = StringUtils.parseBareAddress(mConnection.getUser());
        mColorMap = new UserColorMap();
        mColorMap.getUserNameColor(mLocalId); // Give user first color

        // Set up UI
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
                public void windowClosed(WindowEvent we)
                {
                    saveWindowState();
                    if (mLog != null) {
                        mLog.dispose();
                    }
                    mColorMap.dispose();
                }

                public void windowOpened(WindowEvent we)
                {
                    // Give focus to input text area when the window is created
                    mInputText.requestFocusInWindow();
                }
            });

        /* We do *not* register as a message listener. The Smack Chat object is
         * kind of a crock; there's no documented way to turn off its packet
         * interception when we close this window. And the main JavolinApp
         * listener is already grabbing all CHAT and NORMAL message packets, so
         * there's no need for us to listen -- in fact, it leads to double
         * printing in some cases. */
    }

    /**
     * Saves window state to the preferences storage, including window size and position,
     * and splitter bar position.
     */
    private void saveWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.saveSizeAndPosition();

        prefs.putInt(CHAT_SPLIT_POS, mChatSplitter.getDividerLocation());
    }

    /**
     * Restores window state from the preferences storage, including window size and
     * position, and splitter bar position.
     */
    private void restoreWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.restoreSizeAndPosition();

        mChatSplitter.setDividerLocation(prefs.getInt(CHAT_SPLIT_POS, getHeight() - 100));
    }

    /**
     * PacketListener interface method implementation.
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
                }
            });
    }

    // Implements GetWindowName.
    public String getWindowName() 
    {
        return "Chat: " + mRemoteNick;
    }

    /**
     * Gets the bare ID of the remote user involved in the chat.
     *
     * @return   The bare ID of the remote user involved in the chat.
     */
    public String getRemoteUserId()
    {
        return mRemoteIdBare;
    }

    /**
     * Sends the message that the user typed in the input text area.
     */
    private void doSendMessage()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        try
        {
            String message = mInputText.getText();

            /* Make sure we've got the right resource. */
            mRemoteIdFull = applyLastKnownResource(mRemoteIdBare);
            mChatObject.setParticipant(mRemoteIdFull);

            mChatObject.sendMessage(message);
            mInputText.setText("");
            mLog.message(mLocalId, mLocalId, message);
            // Make the noise, since we won't get an incoming copy of this
            Audio.playMessage();
        }
        catch (XMPPException ex)
        {
            new ErrorWrapper(ex);
            JOptionPane.showMessageDialog(this, ex.toString(), JavolinApp.getAppName()
                 + ": Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handles a message received from the remote party.
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
            String jid = msg.getFrom();
            if (jid != null) 
                setUserResource(jid);
            mLog.message(mRemoteIdFull, mRemoteNick, msg.getBody());
            Audio.playMessage();
        }
    }

    /**
     * If the given JID is a full JID, take its resource to be the resource we
     * are currently chatting with.
     */
    public void setUserResource(String jid) {
        String bareJid = StringUtils.parseBareAddress(jid);
        if (!jid.equals(bareJid)) {
            setLastKnownResource(jid);
            mRemoteIdFull = jid;
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

        cPane.add(mChatSplitter, BorderLayout.CENTER);

        // Necessary for all windows, for Mac support
        AppMenuBar.applyPlatformMenuBar(this);
    }


    /**
     * Sometimes we want to change the destination of a chat object in mid-chat
     * -- not the whole JID, just the resource string. (The XMPP RFC says we
     * "SHOULD" do this when we're replying to a message which had a resource
     * string.) We need to extend Chat to support this.
     */
    static class BetterChat extends Chat {
        public BetterChat(XMPPConnection connection, String participant) {
            super(connection, participant);
        }

        /**
         * Set a new JID for this chat. It may be a bare or full JID. If full,
         * it should bare-match the existing JID, although the code does not
         * currently check that.
         */
        public void setParticipant(String jid) {
            participant = jid;
        }
    }
}
