/*
 * NewTableAtDialog.java
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
package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.*;
import javax.swing.*;
import org.jivesoftware.smack.*;
import org.volity.client.TokenFailure;
import org.volity.client.TranslateToken;
import org.volity.javolin.*;

/**
 * The dialog for creating a new game table.
 */
public class NewTableAtDialog extends BaseDialog implements ActionListener
{
    private final static String NODENAME = "NewTableAtDialog";
    private final static String SERVERID_KEY = "GameServerID";
    private final static String NICKNAME_KEY = "Nickname";

    private JTextField mServerIdField;
    private JTextField mNicknameField;
    private JButton mCancelButton;
    private JButton mCreateButton;

    private XMPPConnection mConnection;
    private TableWindow mTableWindow;

    /**
     * Constructor.
     *
     * @param owner       The Frame from which the dialog is displayed.
     * @param connection  The current active XMPPConnection.
     */
    public NewTableAtDialog(Frame owner, XMPPConnection connection)
    {
        super(owner, JavolinApp.getAppName() + ": New Table At", true, NODENAME);

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
     * Gets the TableWindow that was created.
     *
     * @return   The TableWindow for the game table that was created and joined when the
     * user pressed the Create button, or null if the user pressed Cancel.
     */
    public TableWindow getTableWindow()
    {
        return mTableWindow;
    }

    /**
     * ActionListener interface method implementation.
     *
     * @param e  The action event to handle.
     */
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == mCreateButton)
        {
            doCreate();
        }
        else if (e.getSource() == mCancelButton)
        {
            dispose();
        }
    }

    /**
     * Handles the Create button.
     */
    private void doCreate()
    {
        // Store field values in preferences
        saveFieldValues();

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Create the TableWindow
        try
        {
            String serverID = mServerIdField.getText();

            if (serverID.indexOf('/') == -1)
            {
                serverID = serverID + "/volity";
            }

            mTableWindow = TableWindow.makeTableWindow(mConnection, serverID,
                mNicknameField.getText());

            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            dispose();
        }
        catch (TokenFailure ex)
        {
            // We don't have a log window, so shove the failure message
            // up in a MessageDialog.
            String msg = JavolinApp.getTranslator().translate(ex);

            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            JOptionPane.showMessageDialog(this,
                "Cannot create table due to error:\n" + msg,
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);

            // Destroy TableWindow object
            mTableWindow = null;
        }
        catch (Exception ex)
        {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            JOptionPane.showMessageDialog(this, ex.toString(),
                JavolinApp.getAppName() + ": Error", JOptionPane.ERROR_MESSAGE);

            // Destroy TableWindow object
            mTableWindow = null;
        }
    }

    /**
     * Saves the current text of the server ID and nickname fields to the preferences
     * storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        prefs.put(SERVERID_KEY, mServerIdField.getText());
        prefs.put(NICKNAME_KEY, mNicknameField.getText());
    }

    /**
     * Reads the default server ID and nickname values from the preferences storage and
     * fills in the text fields.
     */
    private void restoreFieldValues()
    {
        // Make a default nickname based on the user ID
        String defNick = mConnection.getUser();
        defNick = defNick.substring(0, defNick.indexOf('@'));

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mServerIdField.setText(prefs.get(SERVERID_KEY, ""));
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

        // Add game server ID label
        JLabel someLabel = new JLabel("Game Server ID:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(MARGIN, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(someLabel, c);

        // Add game server ID field
        mServerIdField = new JTextField(20);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        getContentPane().add(mServerIdField, c);
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

        // Add panel with Cancel and Create buttons
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

        // Add Create button
        mCreateButton = new JButton("Create");
        mCreateButton.addActionListener(this);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mCreateButton, c);
        // Make Create button default
        getRootPane().setDefaultButton(mCreateButton);

        // Make the buttons the same width
        Dimension dim = mCreateButton.getPreferredSize();

        if (mCancelButton.getPreferredSize().width > dim.width)
        {
            dim = mCancelButton.getPreferredSize();
        }

        mCancelButton.setPreferredSize(dim);
        mCreateButton.setPreferredSize(dim);
    }
}
