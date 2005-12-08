/*
 * ConnectDialog.java
 *
 * Copyright 2004 Karl von Laudermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.volity.client.CapPacketExtension;

/**
 * The dialog for establishing a connection to a Jabber server.
 */
public class ConnectDialog extends BaseDialog implements ActionListener
{
    private final static String NODENAME = "ConnectDialog";
    private final static String HOSTNAME_KEY = "HostName";
    private final static String USERNAME_KEY = "UserName";
    private final static String EMAIL_KEY = "Email";
    private final static String FULLNAME_KEY = "FullName";

    private final static String HELP_TEXT_CONNECT = 
        "If you are new to Volity, select this option."       +
        " If you already have a Jabber account that you"      +
        " wish to use, you may fill it in and press Connect.";
    private final static String HELP_TEXT_REGISTER = 
        "If you wish to log into an existing Jabber account," +
        " turn off this option.\n\nYou may register with any" +
        " Jabber host that offers open registration. You are" +
        " welcome to use volity.net.";

    private static String staticResourceString = null;

    private JTextField mHostNameField;
    private JTextField mUserNameField;
    private JPasswordField mPasswordField;
    private JButton mCancelButton;
    private JButton mConnectButton;
    private JCheckBox mRegisterCheck;
    private JTextArea mHelpArea;

    private JPasswordField mPasswordAgainField;
    private JLabel mPasswordAgainLabel;
    private JTextField mEmailField;
    private JLabel mEmailLabel;
    private JTextField mFullNameField;
    private JLabel mFullNameLabel;

    private XMPPConnection mConnection = null;
    private boolean mShowExtraHelp = false;
    private boolean mShowRegistration = false;

    /**
     * Constructor for the ConnectDialog object.
     *
     * @param owner The Frame from which the dialog is displayed.
     */
    public ConnectDialog(Frame owner)
    {
        super(owner, JavolinApp.getAppName() + ": Connect", true, NODENAME);

        // Decide this now -- we don't want to change our minds until the
        // dialog box closes.
        mShowExtraHelp = isEmptyUserField();
        mShowRegistration = false; // initially

        // Set up dialog
        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();

        // Restore default field values
        restoreFieldValues();

        // Set focus to first blank field
        if (mHostNameField.getText().equals(""))
        {
            mHostNameField.requestFocusInWindow();
        }
        else if (mUserNameField.getText().equals(""))
        {
            mUserNameField.requestFocusInWindow();
        }
        else
        {
            mPasswordField.requestFocusInWindow();
        }
    }

    /**
     * Gets the XMPPConnection holding the connection that was established.
     *
     * @return   The XMPPConnection that was created when the user pressed the Connect
     * button, or null if the user pressed Cancel or if there was an error connecting.
     */
    public XMPPConnection getConnection()
    {
        return mConnection;
    }

    /**
     * ActionListener interface method implementation.
     *
     * @param e  The action event to handle.
     */
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == mConnectButton)
        {
            if (mShowRegistration)
                doRegister();
            else
                doConnect();
        }
        else if (e.getSource() == mCancelButton)
        {
            dispose();
        }
        else if (e.getSource() == mRegisterCheck)
        {
            mShowRegistration = mRegisterCheck.isSelected();
            adjustUI();
        }
    }

    /**
     * Handles the Connect button.
     */
    private void doConnect()
    {
        // Make sure at least the first two fields are nonempty.
        // (The password *could* be the empty string.)
        if (mHostNameField.getText().equals("")) {
            mHostNameField.requestFocusInWindow();
            return;
        }
        if (mUserNameField.getText().equals("")) {
            mUserNameField.requestFocusInWindow();
            return;
        }

        // Store field values in preferences
        saveFieldValues();

        // Connect
        try
        {
            mConnection = new XMPPConnection(mHostNameField.getText());
            mConnection.setPresenceFactory(new CapPresenceFactory());
            ServiceDiscoveryManager.getInstanceFor(mConnection).addFeature(CapPacketExtension.NAMESPACE);

            mConnection.login(mUserNameField.getText(),
                new String(mPasswordField.getPassword()), 
                getJIDResource());

            dispose();
        }
        catch (XMPPException ex)
        {
            new ErrorWrapper(ex);
            String message = ex.toString();
            XMPPError error = ex.getXMPPError();

            if (error != null)
            {
                switch (error.getCode())
                {
                case 502:
                case 504:
                    message = "Could not connect to Jabber host " +
                        mHostNameField.getText() + ".";
                    break;
                case 401:
                    message = "Unable to log into Jabber host " +
                        mHostNameField.getText() +
                        ".\nMake sure your user name and password are correct.";
                    break;
                }
            }

            JOptionPane.showMessageDialog(this,
                message,
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);

            // Destroy connection object
            if (mConnection != null) 
            {
                mConnection.close();
                mConnection = null;
            }
        }
    }

    /**
     * Handles the Register button.
     */
    private void doRegister()
    {
        // Make sure the first four fields are nonempty. We frown upon
        // people who set up empty passwords.
        if (mHostNameField.getText().equals("")) {
            mHostNameField.requestFocusInWindow();
            return;
        }
        if (mUserNameField.getText().equals("")) {
            mUserNameField.requestFocusInWindow();
            return;
        }
        if (mPasswordField.getPassword().length == 0) {
            mPasswordField.requestFocusInWindow();
            return;
        }
        if (mPasswordAgainField.getPassword().length == 0) {
            mPasswordAgainField.requestFocusInWindow();
            return;
        }

        /* Theoretically you're supposed to call getPassword(), keep the result
         * in an array, and zero out the array after you use it. But Smack
         * takes passwords as strings, so we can't. This is not anybody's
         * biggest problem. */
        String password  = new String(mPasswordField.getPassword());
        String password2 = new String(mPasswordAgainField.getPassword());
        if (!password.equals(password2)) {
            JOptionPane.showMessageDialog(this, 
                "You did not retype your password correctly.",
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
            mPasswordAgainField.requestFocusInWindow();
            return;
        }

        // Store field values in preferences
        saveFieldValues();

        try {
            mConnection = new XMPPConnection(mHostNameField.getText());
            mConnection.setPresenceFactory(new CapPresenceFactory());
            ServiceDiscoveryManager.getInstanceFor(mConnection).addFeature(CapPacketExtension.NAMESPACE);

            AccountManager manager = mConnection.getAccountManager();

            if (!manager.supportsAccountCreation()) {
                JOptionPane.showMessageDialog(this, 
                    "This Jabber host does not permit you to\n" +
                    "register an account through this client.",
                    JavolinApp.getAppName() + ": Error", 
                    JOptionPane.ERROR_MESSAGE);
                mHostNameField.requestFocusInWindow();
                mConnection.close();
                mConnection = null;
                return;                
            }

            // What fields does this server require?
            boolean emailRequired = false;
            boolean nameRequired = false;
            boolean otherRequired = false;
            String otherFields = "";
            Iterator iter = manager.getAccountAttributes();
            while (iter.hasNext()) {
                String field = (String)iter.next();
                if (field.equals("password") || field.equals("username")) {
                    // No sweat
                }
                else if (field.equals("email")) {
                    emailRequired = true;
                }
                else if (field.equals("name")) {
                    nameRequired = true;
                }
                else {
                    otherRequired = true;
                    if (!otherFields.equals(""))
                        otherFields = otherFields + ", ";
                    otherFields = otherFields + field;
                }
            }

            if (otherRequired) {
                JOptionPane.showMessageDialog(this, 
                    "Javolin is not smart enough to register\n" +
                    "at this host. (Additional fields needed:\n" +
                    otherFields + ")",
                    JavolinApp.getAppName() + ": Error", 
                    JOptionPane.ERROR_MESSAGE);
                mHostNameField.requestFocusInWindow();
                mConnection.close();
                mConnection = null;
                return;                
            }

            if (nameRequired && mFullNameField.getText().equals("")) {
                JOptionPane.showMessageDialog(this, 
                    "You must enter your full name to\n" +
                    "register at this host.",
                    JavolinApp.getAppName() + ": Error", 
                    JOptionPane.ERROR_MESSAGE);
                mFullNameField.requestFocusInWindow();
                mConnection.close();
                mConnection = null;
                return;                
            }

            if (emailRequired && mEmailField.getText().equals("")) {
                JOptionPane.showMessageDialog(this, 
                    "You must enter an email address to\n" +
                    "register at this host.",
                    JavolinApp.getAppName() + ": Error", 
                    JOptionPane.ERROR_MESSAGE);
                mEmailField.requestFocusInWindow();
                mConnection.close();
                mConnection = null;
                return;                
            }

            // Try creating the account!

            Map attr = new HashMap();
            attr.put("username", mUserNameField.getText());
            attr.put("password", password);
            if (!mEmailField.getText().equals(""))
                attr.put("email", mEmailField.getText());
            if (!mFullNameField.getText().equals(""))
                attr.put("name", mFullNameField.getText());

            manager.createAccount(mUserNameField.getText(), password, attr);

            mConnection.login(mUserNameField.getText(), password, 
                getJIDResource());

            dispose();
        }
        catch (XMPPException ex)
        {
            new ErrorWrapper(ex);
            String message = ex.toString();
            XMPPError error = ex.getXMPPError();

            if (error != null)
            {
                switch (error.getCode())
                {
                case 502:
                case 504:
                    message = "Could not connect to Jabber host " +
                        mHostNameField.getText() + ".";
                    break;
                case 409:
                    message = "An account with that name already " +
                        "exists at this host.";
                    break;
                }
            }

            JOptionPane.showMessageDialog(this, 
                message,
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);

            // Destroy connection object
            if (mConnection != null) 
            {
                mConnection.close();
                mConnection = null;
            }
        }

    }

    /**
     * Saves the current text of the host name and user name fields to the preferences
     * storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        prefs.put(HOSTNAME_KEY, mHostNameField.getText());
        prefs.put(USERNAME_KEY, mUserNameField.getText());
        prefs.put(EMAIL_KEY, mEmailField.getText());
        prefs.put(FULLNAME_KEY, mFullNameField.getText());
    }

    /**
     * Reads the default host name and user name values from the preferences storage and
     * fills in the text fields.
     */
    private void restoreFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mHostNameField.setText(prefs.get(HOSTNAME_KEY, "volity.net"));
        mUserNameField.setText(prefs.get(USERNAME_KEY, ""));
        mEmailField.setText(prefs.get(EMAIL_KEY, ""));
        mFullNameField.setText(prefs.get(FULLNAME_KEY, ""));
    }

    /**
     * Check whether the username field is empty. This is used to decide
     * whether to show the extra help text.
     */
    private boolean isEmptyUserField()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        String val = prefs.get(USERNAME_KEY, "");
        return (val.equals(""));
    }

    /**
     * Create a resource for the JID we are going to use to log in.
     *
     * It is desirable to randomize this string, so that the user can run
     * Javolin in two places (or twice on the same machine) without disaster.
     * On the other hand, we can keep the same value for the duration of the
     * Javolin process. (If you disconnect and reconnect, using the same
     * resource string, there's no disaster.) So this is stored in a static
     * field.
     */
    public String getJIDResource() 
    {
        if (staticResourceString == null) {
            StringBuffer buf = new StringBuffer("javolin");
            for (int ix=0; ix<6; ix++) {
                int val = (int)(Math.random() * 25.9999);
                buf.append((char)('A'+val));
            }
            staticResourceString = buf.toString();
        }

        return staticResourceString;
    }

    /**
     * Make the "register new account" fields visible, or invisible, depending
     * on mShowRegistration.
     */
    private void adjustUI() 
    {
        mPasswordAgainLabel.setVisible(mShowRegistration);
        mPasswordAgainField.setVisible(mShowRegistration);
        mEmailLabel.setVisible(mShowRegistration);
        mEmailField.setVisible(mShowRegistration);
        mFullNameLabel.setVisible(mShowRegistration);
        mFullNameField.setVisible(mShowRegistration);

        if (mShowRegistration)
            mConnectButton.setText("Register");
        else
            mConnectButton.setText("Connect");

        if (mHelpArea != null) {
            if (mShowRegistration)
                mHelpArea.setText(HELP_TEXT_REGISTER);
            else
                mHelpArea.setText(HELP_TEXT_CONNECT);
        }

        pack();
    }

    /**
     * Populates the dialog with controls. This method is called once, from the
     * constructor.
     *
     * We create several components -- the "register new account" fields --
     * which are initially hidden.
     */
    private void buildUI()
    {
        getContentPane().setLayout(new GridBagLayout());
        GridBagConstraints c;

        int gridY = 0;

        // Add host label
        JLabel someLabel = new JLabel("Host:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(MARGIN, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(someLabel, c);

        // Add host field
        mHostNameField = new JTextField(15);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        getContentPane().add(mHostNameField, c);
        gridY++;

        // Add user name label
        someLabel = new JLabel("User name:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(someLabel, c);

        // Add user name field
        mUserNameField = new JTextField(15);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, SPACING, 0, MARGIN);
        getContentPane().add(mUserNameField, c);
        gridY++;

        // Add password label
        someLabel = new JLabel("Password:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(someLabel, c);

        // Add password field
        mPasswordField = new JPasswordField(15);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, SPACING, 0, MARGIN);
        getContentPane().add(mPasswordField, c);
        gridY++;

        // Add repeat-password label
        mPasswordAgainLabel = new JLabel("Retype password:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(mPasswordAgainLabel, c);

        // Add repeat-password field
        mPasswordAgainField = new JPasswordField(15);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(SPACING, SPACING, 0, MARGIN);
        getContentPane().add(mPasswordAgainField, c);
        gridY++;

        // Add Register checkbox
        mRegisterCheck = new JCheckBox("Register a new Jabber account",
            mShowRegistration);
        mRegisterCheck.addActionListener(this);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(mRegisterCheck, c);
        gridY++;

        if (mShowExtraHelp) 
        {
            mHelpArea = new JTextArea(HELP_TEXT_CONNECT);
            mHelpArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
            mHelpArea.setOpaque(false);
            mHelpArea.setEditable(false);
            mHelpArea.setLineWrap(true);
            mHelpArea.setWrapStyleWord(true);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = gridY;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(SPACING, MARGIN+6, 0, MARGIN);
            c.anchor = GridBagConstraints.WEST;
            getContentPane().add(mHelpArea, c);
            gridY++;
        }

        // Add fullname address label
        mFullNameLabel = new JLabel("Your name:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(GAP, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(mFullNameLabel, c);

        // Add fullname address field
        mFullNameField = new JTextField(15);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(GAP, SPACING, 0, MARGIN);
        getContentPane().add(mFullNameField, c);
        gridY++;

        // Add email address label
        mEmailLabel = new JLabel("Email address:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.insets = new Insets(GAP, MARGIN, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        getContentPane().add(mEmailLabel, c);

        // Add email address field
        mEmailField = new JTextField(15);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = gridY;
        c.insets = new Insets(GAP, SPACING, 0, MARGIN);
        getContentPane().add(mEmailField, c);
        gridY++;

        // Add panel with Cancel and Connect buttons
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

        // Add Connect button
        mConnectButton = new JButton("Connect");
        mConnectButton.addActionListener(this);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mConnectButton, c);
        // Make Connect button default
        getRootPane().setDefaultButton(mConnectButton);

        // Make the buttons the same width
        Dimension dim = mConnectButton.getPreferredSize();
        
        if (mCancelButton.getPreferredSize().width > dim.width)
        {
            dim = mCancelButton.getPreferredSize();
        }
        
        mCancelButton.setPreferredSize(dim);
        mConnectButton.setPreferredSize(dim);

        adjustUI();
    }
}
