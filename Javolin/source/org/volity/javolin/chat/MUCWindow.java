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
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.text.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.*;
import org.jivesoftware.smackx.*;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.volity.javolin.*;

/**
 * A window for participating in a MUC.
 */
public class MUCWindow extends JFrame implements PacketListener
{
    private final static String NODENAME = "MUCWindow";
    private final static String CHAT_SPLIT_POS = "ChatSplitPos";
    private final static String USERLIST_SPLIT_POS = "UserListSplitPos";

    private final static Color colorCurrentTimestamp = Color.BLACK;
    private final static Color colorDelayedTimestamp = new Color(0.3f, 0.3f, 0.3f);

    private JSplitPane mChatSplitter;
    private JSplitPane mUserListSplitter;
    private LogTextPanel mMessageText;
    private JTextArea mInputText;
    private JTextPane mUserListText;
    private AbstractAction mSendMessageAction;

    private UserColorMap mUserColorMap;
    private SimpleDateFormat mTimeStampFormat;
    private SimpleAttributeSet mBaseUserListStyle;

    private SizeAndPositionSaver mSizePosSaver;
    private XMPPConnection mConnection;
    private MultiUserChat mMucObject;

    private Runnable mColorChangeListener;

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

        mConnection = connection;
        mMucObject = new MultiUserChat(connection, mucId);

        mUserColorMap = new UserColorMap();
        mUserColorMap.getUserNameColor(nickname); // Give user first color

        mTimeStampFormat = new SimpleDateFormat("HH:mm:ss");

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
                public void windowClosing(WindowEvent we)
                {
                    // Leave the chat room when the window is closed
                    saveWindowState();
                    mMucObject.leave();
                    mUserColorMap.dispose();
                    PrefsDialog.removeListener(PrefsDialog.CHAT_COLOR_OPTIONS,
                        mColorChangeListener);
                }

                public void windowOpened(WindowEvent we)
                {
                    // Give focus to input text area when the window is created
                    mInputText.requestFocusInWindow();
                }
            });

        mColorChangeListener = new Runnable() {
                public void run() {
                    updateUserList();
                }
            };
        PrefsDialog.addListener(PrefsDialog.CHAT_COLOR_OPTIONS,
            mColorChangeListener);

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

    /**
     * Updates the list of users in the MUC.
     */
    private void updateUserList()
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        mUserListText.setText("");
        Iterator iter = mMucObject.getOccupants();

        while (iter.hasNext())
        {
            String userName = StringUtils.parseResource(iter.next().toString());

            SimpleAttributeSet style = new SimpleAttributeSet(mBaseUserListStyle);
            StyleConstants.setForeground(style,
                mUserColorMap.getUserNameColor(userName));

            Document doc = mUserListText.getDocument();

            try
            {
                doc.insertString(doc.getLength(), userName + "\n", style);
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
            String nick = null;
            String addr = msg.getFrom();
            Date date = null;

            if (addr != null)
            {
                nick = StringUtils.parseResource(addr);
            }

            PacketExtension ext = msg.getExtension("x", "jabber:x:delay");
            if (ext != null && ext instanceof DelayInformation) 
            {
                date = ((DelayInformation)ext).getStamp();
            }

            writeMessageText(nick, msg.getBody(), date);
        }
    }

    /**
     * Appends the given message text to the message text area.
     *
     * @param nickname  The nickname of the user who sent the message.
     *                  If null or empty, it is assumed to have come from the
     *                  client or from the MultiUserChat itself.
     * @param message   The text of the message.
     * @param date      The timestamp of the message. If null, it is assumed
     *                  to be current.
     */
    private void writeMessageText(String nickname, String message, Date date)
    {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Append time stamp
        Color dateColor;
        if (date == null) {
            date = new Date();
            dateColor = colorCurrentTimestamp;
        }
        else {
            dateColor = colorDelayedTimestamp;
        }
        mMessageText.append("[" + mTimeStampFormat.format(date) + "]  ",
            dateColor);

        // Append received message
        boolean hasNick = ((nickname != null) && (!nickname.equals("")));

        String nickText = hasNick ? nickname + ":" : "***";

        Color nameColor =
            hasNick ? mUserColorMap.getUserNameColor(nickname) : Color.BLACK;
        Color textColor =
            hasNick ? mUserColorMap.getUserTextColor(nickname) : Color.BLACK;

        mMessageText.append(nickText + " ", nameColor);
        mMessageText.append(message + "\n", textColor);
    }

    /**
     * Appends the given message text to the message text area. The message is
     * assumed to be current.
     *
     * @param nickname  The nickname of the user who sent the message.
     *                  If null or empty, it is assumed to have come from the
     *                  client or from the MultiUserChat itself.
     * @param message   The text of the message.
     */
    private void writeMessageText(String nickname, String message)
    {
        writeMessageText(nickname, message, null);
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

        mMessageText = new LogTextPanel();
        mChatSplitter.setTopComponent(mMessageText);

        mInputText = new JTextArea();
        mInputText.setLineWrap(true);
        mInputText.setWrapStyleWord(true);
        mChatSplitter.setBottomComponent(new JScrollPane(mInputText));

        // Split pane separating user list from everything else
        mUserListSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mUserListSplitter.setResizeWeight(1);

        mUserListSplitter.setLeftComponent(mChatSplitter);

        mUserListText = new JTextPane();
        mUserListText.setEditable(false);
        mUserListSplitter.setRightComponent(new JScrollPane(mUserListText));

        cPane.add(mUserListSplitter, BorderLayout.CENTER);

        // Necessary for all windows, for Mac support
        JavolinMenuBar.applyPlatformMenuBar(this);
    }
}
