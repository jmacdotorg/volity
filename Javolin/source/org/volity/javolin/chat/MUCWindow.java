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
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.*;
import org.volity.jabber.*;
import org.volity.javolin.*;

/**
 * A window for participating in a MUC.
 */
public class MUCWindow extends JFrame implements PacketListener
{
    private final static String NODENAME = "MUCWindow";
    private final static String CHAT_SPLIT_POS = "ChatSplitPos";
    private final static String USERLIST_SPLIT_POS = "UserListSplitPos";

    protected JSplitPane mChatSplitter;
    protected JSplitPane mUserListSplitter;
    protected JTextArea mMessageText;
    protected JScrollPane mMessageScroller;
    protected JTextArea mInputText;
    protected JTextArea mUserListText;
    protected AbstractAction mSendMessageAction;

    protected SizeAndPositionSaver mSizePosSaver;
    protected MUC mMucObject;

    /**
     * Constructor.
     *
     * @param aMUC  The MUC object to communicate with.
     */
    public MUCWindow(MUC aMUC)
    {
        super(JavolinApp.getAppName() + ": " + aMUC.getRoom());

        buildUI();

        setSize(500, 400);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        restoreWindowState();

        mMucObject = aMUC;

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
                }

                public void windowOpened(WindowEvent we)
                {
                    // Give focus to input text area when the window is created
                    mInputText.requestFocusInWindow();
                }
            });

        // Retrieve all pending messages, then register as message listener.
        Message msg;

        do
        {
            msg = mMucObject.nextMessage(50);

            if (msg != null)
            {
                doMessageReceived(msg);
            }
        } while (msg != null);

        mMucObject.addMessageListener(this);

        // Update user list and register as participant listener.
        updateUserList();
        mMucObject.addParticipantListener(this);
    }

    /**
     * Saves window state to the preferences storage, including window size and position,
     * and splitter bar positions.
     */
    protected void saveWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.saveSizeAndPosition();

        prefs.putInt(CHAT_SPLIT_POS, mChatSplitter.getDividerLocation());
        prefs.putInt(USERLIST_SPLIT_POS, mUserListSplitter.getDividerLocation());
    }

    /**
     * Saves window state to the preferences storage, including window size and position,
     * and splitter bar positions.
     */
    protected void restoreWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.restoreSizeAndPosition();

        mChatSplitter.setDividerLocation(prefs.getInt(CHAT_SPLIT_POS, getHeight() - 100));
        mUserListSplitter.setDividerLocation(prefs.getInt(USERLIST_SPLIT_POS,
            getWidth() - 100));
    }

    /**
     * Updates the list of users in the MUC.
     */
    protected void updateUserList()
    {
        mUserListText.setText("");
        Iterator iter = mMucObject.getParticipants();

        while (iter.hasNext())
        {
            mUserListText.append(StringUtils.parseResource(iter.next().toString()) +
                "\n");
        }
    }

    /**
     * PacketListener interface method implementation.
     *
     * @param packet  The packet received.
     */
    public void processPacket(Packet packet)
    {
        if (packet instanceof Message)
        {
            doMessageReceived((Message)packet);
        }
        else if (packet instanceof Presence)
        {
            updateUserList();
        }
    }

    /**
     * Sends the message that the user typed in the input text area.
     */
    protected void doSendMessage()
    {
        try
        {
            mMucObject.sendMessage(mInputText.getText());
            mInputText.setText("");
        }
        catch (XMPPException ex)
        {
            JOptionPane.showMessageDialog(this, ex.toString(),
                JavolinApp.getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handles a message received from the MUC.
     *
     * @param msg  The Message object that was received.
     */
    protected void doMessageReceived(Message msg)
    {
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

            if (addr != null)
            {
                nick = StringUtils.parseResource(addr);
            }

            writeMessageText(nick, msg.getBody());
        }
    }

    /**
     * Appends the given message text to the message text area.
     *
     * @param nickname  The nickname of the user who sent the message. If null or empty,
     * it is assumed to have come from the MUC itself.
     * @param message   The text of the message.
     */
    protected void writeMessageText(String nickname, String message)
    {
        boolean hasNick = ((nickname != null) && (!nickname.equals("")));
        String nickText = hasNick ? nickname + ":" : "***";

        mMessageText.append(nickText + " " + message + "\n");

        // Scroll to the bottom of the message area unless the user is dragging the
        // scroll thumb
        if (!mMessageScroller.getVerticalScrollBar().getValueIsAdjusting())
        {
            mMessageText.setCaretPosition(mMessageText.getDocument().getLength());
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

        mMessageText = new JTextArea();
        mMessageText.setEditable(false);
        mMessageText.setLineWrap(true);
        mMessageText.setWrapStyleWord(true);

        mMessageScroller = new JScrollPane(mMessageText);
        mChatSplitter.setTopComponent(mMessageScroller);

        mInputText = new JTextArea();
        mInputText.setLineWrap(true);
        mInputText.setWrapStyleWord(true);
        mChatSplitter.setBottomComponent(new JScrollPane(mInputText));

        // Split pane separating user list from everything else
        mUserListSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mUserListSplitter.setResizeWeight(1);

        mUserListSplitter.setLeftComponent(mChatSplitter);

        mUserListText = new JTextArea();
        mUserListText.setEditable(false);
        mUserListSplitter.setRightComponent(new JScrollPane(mUserListText));

        cPane.add(mUserListSplitter, BorderLayout.CENTER);
    }
}
