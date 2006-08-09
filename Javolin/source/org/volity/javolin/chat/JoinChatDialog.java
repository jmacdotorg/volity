package org.volity.javolin.chat;

import java.awt.*;
import java.awt.event.*;
import java.util.Iterator;
import java.util.prefs.*;
import javax.swing.*;
import org.jivesoftware.smack.*;
import org.volity.javolin.*;

/**
 * The dialog for chatting with a specified user.
 */
public class JoinChatDialog extends BaseDialog implements ActionListener
{
    private final static String NODENAME = "JoinChatDialog";
    private final static String JID_KEY = "JID";

    private JTextField mJIDField;
    private JButton mCancelButton;
    private JButton mJoinButton;

    private JavolinApp mOwner;
    private XMPPConnection mConnection;
    private boolean mInProgress;

    public JoinChatDialog(JavolinApp owner, XMPPConnection connection)
    {
        super(owner, "Chat With User", true, NODENAME);
        setTitle(JavolinApp.getAppName() + ": " + localize("WindowTitle"));

        mOwner = owner;
        mConnection = connection;
        mInProgress = false;

        // Set up dialog
        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();

        // Restore default field values
        restoreFieldValues();
    }

    /**
     * ActionListener interface method implementation.
     */
    public void actionPerformed(ActionEvent ev)
    {
        Object source = ev.getSource();
        if (source == null)
            return;

        if (source == mJoinButton)
        {
            doJoin();
        }
        else if (source == mCancelButton)
        {
            dispose();
        }
    }

    /**
     * Handles the Join button.
     */
    private void doJoin()
    {
        if (mInProgress)
            return;

        String jid = expandJIDField(mJIDField);
        if (jid == null) {
            complainMustEnter(mJIDField, localize("MustEnterJID"));
            return;
        }

        mInProgress = true;

        // Store field values in preferences
        saveFieldValues();
        dispose();

        mOwner.chatWithUser(jid);
    }

    /**
     * Saves the current text of the JID field to the preferences storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        prefs.put(JID_KEY, mJIDField.getText());
    }

    /**
     * Reads the default JID field from the preferences storage and fills in
     * the text field.
     */
    private void restoreFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mJIDField.setText(prefs.get(JID_KEY, ""));
    }

    /**
     * Populates the dialog with controls. This method is called once, from the
     * constructor.
     */
    private void buildUI()
    {
        getContentPane().setLayout(new GridBagLayout());
        GridBagConstraints c;

        int row = 0;

        // Add JID label
        JLabel someLabel = new JLabel(localize("LabelJID"));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.insets = new Insets(MARGIN, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(someLabel, c);

        // Add JID field
        mJIDField = new JTextField(20);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        getContentPane().add(mJIDField, c);
        row++;

        // Add panel with Cancel and Join buttons
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.5;
        getContentPane().add(buttonPanel, c);
        row++;

        // Add Cancel button
        mCancelButton = new JButton(localize("ButtonCancel"));
        mCancelButton.addActionListener(this);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.5;
        buttonPanel.add(mCancelButton, c);

        // Add Join button
        mJoinButton = new JButton(localize("ButtonJoin"));
        mJoinButton.addActionListener(this);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mJoinButton, c);
        // Make Join button default
        getRootPane().setDefaultButton(mJoinButton);

        // Make the buttons the same width
        Dimension dim = mJoinButton.getPreferredSize();

        if (mCancelButton.getPreferredSize().width > dim.width)
        {
            dim = mCancelButton.getPreferredSize();
        }

        mCancelButton.setPreferredSize(dim);
        mJoinButton.setPreferredSize(dim);
    }
}
