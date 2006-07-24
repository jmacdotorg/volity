/*
 * JoinMUCDialog.java
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
import java.util.Iterator;
import java.util.prefs.*;
import javax.swing.*;
import org.jivesoftware.smack.*;
import org.volity.javolin.*;

/**
 * The dialog for joining a MUC.
 */
public class JoinMUCDialog extends BaseDialog implements ActionListener
{
    private final static String NODENAME = "JoinMUCDialog";
    private final static String MUCID_KEY = "MUCID";
    private final static String NICKNAME_KEY = "Nickname";

    private JTextField mMucIdField;
    private JTextField mNicknameField;
    private JButton mCancelButton;
    private JButton mJoinButton;

    private JavolinApp mOwner;
    private XMPPConnection mConnection;
    private MUCWindow mMucWindow;
    private boolean mInProgress;

    /**
     * Constructor.
     *
     * @param owner       The Frame from which the dialog is displayed.
     * @param connection  The current active XMPPConnection.
     */
    public JoinMUCDialog(JavolinApp owner, XMPPConnection connection)
    {
        super(owner, "Join Multi-User Chat", true, NODENAME);
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
     *
     * @param e  The action event to handle.
     */
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == mJoinButton)
        {
            doJoin();
        }
        else if (e.getSource() == mCancelButton)
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

        String mucID = expandJIDField(mMucIdField, "conference.volity.net");
        if (mucID == null) {
            complainMustEnter(mMucIdField, localize("MustEnterMUC"));
            return;
        }

        if (mNicknameField.getText().equals("")) {
            complainMustEnter(mNicknameField, localize("MustEnterNickname"));
            return;
        }

        mInProgress = true;

        // Store field values in preferences
        saveFieldValues();

        MakeMUCWindow maker = new MakeMUCWindow(mOwner, mConnection, this);
        maker.joinMUC(mucID, mNicknameField.getText(),
            new MakeMUCWindow.MUCWindowCallback() {
                public void fail() {
                    mInProgress = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
                public void succeed(MUCWindow win) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    dispose();
                }
            });
    }

    /**
     * Saves the current text of the MUC ID and nickname fields to the preferences
     * storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        prefs.put(MUCID_KEY, mMucIdField.getText());
        prefs.put(NICKNAME_KEY, mNicknameField.getText());
    }

    /**
     * Reads the default MUC ID and nickname values from the preferences storage and
     * fills in the text fields.
     */
    private void restoreFieldValues()
    {
        // Make a default nickname based on the user ID
        String defNick = mConnection.getUser();
        defNick = defNick.substring(0, defNick.indexOf('@'));

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mMucIdField.setText(prefs.get(MUCID_KEY, ""));
        mNicknameField.setText(prefs.get(NICKNAME_KEY, defNick));
    }

    /**
     * Populates the dialog with controls. This method is called once, from the
     * constructor.
     */
    private void buildUI()
    {
        getContentPane().setLayout(new GridBagLayout());
        GridBagConstraints c;

        int gridY = 0;

        // Add MUC ID label
        JLabel someLabel = new JLabel(localize("LabelMucId"));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(MARGIN, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(someLabel, c);

        // Add MUC ID field
        mMucIdField = new JTextField(20);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        getContentPane().add(mMucIdField, c);
        gridY++;

        // Add nickname label
        someLabel = new JLabel(localize("LabelNickname"));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(someLabel, c);

        // Add nickname field
        mNicknameField = new JTextField(20);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, SPACING, 0, MARGIN);
        getContentPane().add(mNicknameField, c);
        gridY++;

        // Add panel with Cancel and Join buttons
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.5;
        getContentPane().add(buttonPanel, c);
        gridY++;

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
