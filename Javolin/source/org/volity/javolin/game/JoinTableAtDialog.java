/*
 * JoinTableAtDialog.java
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
import java.io.*;
import java.util.prefs.*;
import javax.swing.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.muc.DefaultParticipantStatusListener;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.volity.client.GameServer;
import org.volity.client.GameTable;
import org.volity.client.TokenFailure;
import org.volity.client.TranslateToken;
import org.volity.javolin.*;

/**
 * The dialog for joining an existing game table.
 */
public class JoinTableAtDialog extends BaseDialog implements ActionListener
{
    private final static String NODENAME = "JoinTableAtDialog";
    private final static String TABLEID_KEY = "GameTableID";
    private final static String NICKNAME_KEY = "Nickname";
    // XXX Should the nickname pref be unified with NewTableAtDialog?

    private JTextField mTableIdField;
    private JTextField mNicknameField;
    private JButton mCancelButton;
    private JButton mJoinButton;

    private XMPPConnection mConnection;
    private TableWindow mTableWindow;

    /**
     * Constructor.
     *
     * @param owner       The Frame from which the dialog is displayed.
     * @param connection  The current active XMPPConnection.
     */
    public JoinTableAtDialog(Frame owner, XMPPConnection connection)
    {
        super(owner, JavolinApp.getAppName() + ": Join Table At", true, NODENAME);

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
     * @return   The TableWindow for the game table that was joined when the
     * user pressed the Join button, or null if the user pressed Cancel.
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
        String tableID = null;

        // Store field values in preferences
        saveFieldValues();

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Create the TableWindow. Note that we don't have a GameServer.
        try
        {
            tableID = mTableIdField.getText();

            final GameTable gameTable = new GameTable(mConnection, tableID);
            final String nickname = mNicknameField.getText();

            /* To get the GameServer, we need to join the MUC early. */

            GameTable.ReadyListener listener = new GameTable.ReadyListener() {
                    public void ready() {
                        // Called outside Swing thread!
                        // Invoke into the Swing thread.
                        SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    doJoinCont(gameTable, nickname);
                                }
                            });
                    }
                };
            gameTable.addReadyListener(listener);

            gameTable.join(nickname);

            /**
             * One possible error case: the user typed the name of a
             * nonexistent MUC on a real MUC host. If that's happened, we just
             * accidentally created a new MUC! To check for this, we call
             * getConfigurationForm -- if that *succeeds*, then we're in the
             * bad case and have to abort.
             */

            Form form = null;
            try {
                form = gameTable.getConfigurationForm();
            }
            catch (Exception ex)
            {
                // do nothing -- form stays null
            }

            if (form != null) {
                gameTable.removeReadyListener(listener);
                gameTable.leave();
                // Use a 404 here so that it's caught below.
                throw new XMPPException(new XMPPError(404));
            }

            /*
             * Now we wait for the ReadyListener to fire, which will invoke
             * doJoinCont(), below. 
             */
        }
        catch (XMPPException ex) 
        {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            String msg = "The table could not be joined.";

            // Any or all of these may be null.
            String submsg = ex.getMessage();
            XMPPError error = ex.getXMPPError();
            Throwable subex = ex.getWrappedThrowable();

            if (error != null && error.getCode() == 404) 
            {
                /* A common case: the JID was not found. */
                msg = "No table exists at this address.";
                if (error.getMessage() != null)
                    msg = msg + " (" + error.getMessage() + ")";
                msg = msg + "\n(" + tableID + ")";
            }
            else {
                msg = "The table could not be joined";
                if (submsg != null && subex == null && error == null)
                    msg = msg + ": " + submsg;
                else
                    msg = msg + ".";
                if (subex != null)
                    msg = msg + "\n" + subex.toString();
                if (error != null)
                    msg = msg + "\nJabber error " + error.toString();
            }

            JOptionPane.showMessageDialog(this, 
                msg,
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
        }
        catch (Exception ex)
        {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            JOptionPane.showMessageDialog(this, 
                "Cannot join table:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * More handling of the Join button, after the MUC join succeeds.
     */
    private void doJoinCont(GameTable gameTable, String nickname)
    {
        try {
            String refJID = gameTable.getReferee().getResponderJID();
            String serverID = null;

            ServiceDiscoveryManager discoMan = 
                ServiceDiscoveryManager.getInstanceFor(mConnection);
            DiscoverInfo info = discoMan.discoverInfo(refJID);

            Form form = Form.getFormFrom(info);
            if (form != null) {
                FormField field = form.getField("parlor");
                if (field != null)
                    serverID = (String) field.getValues().next();
            }
            
            if (serverID == null || serverID.equals("")) {
                throw new IOException("Unable to fetch parlor ID from referee");
            }

            GameServer server = new GameServer(mConnection, serverID);

            mTableWindow = TableWindow.makeTableWindow(mConnection, 
              server, gameTable, nickname);

            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            dispose();
        }
        catch (Exception ex)
        {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            JOptionPane.showMessageDialog(this,
                "Cannot join table:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);

            // Destroy TableWindow object
            mTableWindow = null;
            gameTable.leave();
        }
    }

    /**
     * Saves the current text of the table ID and nickname fields to the preferences
     * storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        prefs.put(TABLEID_KEY, mTableIdField.getText());
        prefs.put(NICKNAME_KEY, mNicknameField.getText());
    }

    /**
     * Reads the default table ID and nickname values from the preferences storage and
     * fills in the text fields.
     */
    private void restoreFieldValues()
    {
        // Make a default nickname based on the user ID
        String defNick = mConnection.getUser();
        defNick = defNick.substring(0, defNick.indexOf('@'));

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mTableIdField.setText(prefs.get(TABLEID_KEY, ""));
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

        // Add game table ID label
        JLabel someLabel = new JLabel("Game Table ID:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(MARGIN, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(someLabel, c);

        // Add game table ID field
        mTableIdField = new JTextField(28);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        getContentPane().add(mTableIdField, c);
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
        mNicknameField = new JTextField(28);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, SPACING, 0, MARGIN);
        c.anchor = GridBagConstraints.WEST;
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
