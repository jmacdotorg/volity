package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.volity.client.GameTable;
import org.volity.client.TokenFailure;
import org.volity.client.TranslateToken;
import org.volity.javolin.BaseDialog;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.JavolinApp;

public class SendInvitationDialog extends BaseDialog
{
    private final static String NODENAME = "SendInvitationDialog";
    private final static String USERID_KEY = "UserID";

    private TableWindow mOwner;
    private GameTable mGameTable;

    private JTextField mUserIdField;
    private JTextArea mMessageField;
    private JButton mCancelButton;
    private JButton mInviteButton;

    public SendInvitationDialog(TableWindow owner, GameTable gameTable) {
        super(owner, "Invite A Player", false, NODENAME);

        mOwner = owner;
        mGameTable = gameTable;

        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();

        // Restore default field values
        restoreFieldValues();

        mCancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    dispose();
                }
            });

        mInviteButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    String jid = mUserIdField.getText();
                    if (jid.equals("")) {
                        mUserIdField.requestFocusInWindow();
                        return;
                    }
                    String msg = mMessageField.getText().trim();

                    saveFieldValues();
                    doInvitePlayer(jid, msg);
                }
            });

        // If the table window closes, this should close.
        mOwner.addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    dispose();
                }
            });
    }

    /**
     * Ask the referee to invite a player into the game. Handle any errors that
     * may occur.
     */
    protected void doInvitePlayer(String jid, String msg) {
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            mGameTable.getReferee().invitePlayer(jid, msg);

            // success; close dialog
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            dispose();
            return;
        }
        catch (TokenFailure ex) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            String errtext = mOwner.getTranslator().translate(ex);
            JOptionPane.showMessageDialog(this,
                "Unable to send invitation:\n" + errtext,
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            JOptionPane.showMessageDialog(this, 
                "Unable to send invitation:\n" + ex.toString(),
                JavolinApp.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Saves the current text of the fields to the preferences storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        prefs.put(USERID_KEY, mUserIdField.getText());
    }

    /**
     * Reads the default values from the preferences storage and fills in the
     * text fields.
     */
    private void restoreFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mUserIdField.setText(prefs.get(USERID_KEY, ""));
    }

    /**
     * Create the window UI.
     */
    private void buildUI() {
        Container cPane = getContentPane();
        cPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        JLabel label;
        String msg;
        JTextField field;

        int row = 0;

        label = new JLabel(mOwner.getWindowName());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        label = new JLabel("User:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        mUserIdField = new JTextField(25);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        cPane.add(mUserIdField, c);

        label = new JLabel("Message to include:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        mMessageField = new JTextArea();
        mMessageField.setRows(4);
        mMessageField.setLineWrap(true);
        mMessageField.setWrapStyleWord(true);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        JScrollPane scroller = new JScrollPane(mMessageField);
        cPane.add(scroller, c);

        // Add panel with Cancel and Create buttons
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.5;
        cPane.add(buttonPanel, c);

        // Add Cancel button
        mCancelButton = new JButton("Cancel");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.5;
        buttonPanel.add(mCancelButton, c);

        // Add Invite button
        mInviteButton = new JButton("Invite");
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mInviteButton, c);
        // Make Invite button default
        getRootPane().setDefaultButton(mInviteButton);

        // Make the buttons the same width
        Dimension dim = mInviteButton.getPreferredSize();

        if (mCancelButton.getPreferredSize().width > dim.width)
        {
            dim = mCancelButton.getPreferredSize();
        }

        mCancelButton.setPreferredSize(dim);
        mInviteButton.setPreferredSize(dim);
    }
}
