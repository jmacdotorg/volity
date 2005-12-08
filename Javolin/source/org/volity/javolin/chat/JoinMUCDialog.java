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

    /**
     * Constructor.
     *
     * @param owner       The Frame from which the dialog is displayed.
     * @param connection  The current active XMPPConnection.
     */
    public JoinMUCDialog(JavolinApp owner, XMPPConnection connection)
    {
        super(owner, JavolinApp.getAppName() + ": Join Multi-user Chat", true, NODENAME);

        mOwner = owner;
        mConnection = connection;

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
     * Gets the MUCWindow that was created.
     *
     * @return   The MUCWindow for the MUC that was created and joined when the user
     *  pressed the Join button, or null if the user pressed Cancel.
     */
    public MUCWindow getMUCWindow()
    {
        return mMucWindow;
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
        if (mMucIdField.getText().equals("")) {
            mMucIdField.requestFocusInWindow();
            return;
        }

        if (mNicknameField.getText().equals("")) {
            mNicknameField.requestFocusInWindow();
            return;
        }

        // Store field values in preferences
        saveFieldValues();

        String mucID = mMucIdField.getText();

        // Make sure we're not already in this MUC.
        for (Iterator it = mOwner.getMucWindows(); it.hasNext(); ) {
            MUCWindow win = (MUCWindow)it.next();
            if (mucID.equals(win.getRoom())) {
                // We are. Bring up the existing window, and exit.
                dispose();
                win.show();
                return;
            }
        }

        // Create the MUCWindow
        try
        {
            mMucWindow = new MUCWindow(mConnection, mucID,
                mNicknameField.getText());

            dispose();
        }
        catch (XMPPException ex)
        {
            new ErrorWrapper(ex);
            JOptionPane.showMessageDialog(this, ex.toString(),
                JavolinApp.getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);

            // Destroy MUCWindow object
            mMucWindow = null;
        }
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
        JLabel someLabel = new JLabel("MUC ID:");
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
        someLabel = new JLabel("Nickname:");
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
        mCancelButton = new JButton("Cancel");
        mCancelButton.addActionListener(this);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.5;
        buttonPanel.add(mCancelButton, c);

        // Add Join button
        mJoinButton = new JButton("Join");
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
