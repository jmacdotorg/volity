package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.volity.client.GameTable;
import org.volity.client.TokenFailure;
import org.volity.client.TranslateToken;
import org.volity.jabber.JIDUtils;
import org.volity.javolin.BaseDialog;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.roster.RosterPanel;

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
     *
     * If it's a full JID, the invitation goes straight out -- we assume the
     * caller knows what resource he wants. If it's a bare JID, we have to do
     * more work.
     *
     * First, we check the roster. If there's no subscription, the only choice
     * is to add one. We can't do the invite until the subscription is
     * confirmed; so, display a message and exit.
     *
     * If we're subscribed to the JID's presence, then we know what resource to
     * use. (Or we know there is no logged-on resource which is a Volity
     * client.) There might be multiple resources, though, which is a weird
     * case. We resolve it by sending invitations to all of them. But only the
     * first one gets to display an error, or close the dialog box. The rest
     * are "quiet".
     */
    protected void doInvitePlayer(String jid, String msg) {
        JavolinApp app = JavolinApp.getSoleJavolinApp();
        if (app == null)
            return;

        if (!JIDUtils.hasResource(jid)) {
            // We must try to figure out a resource.

            RosterPanel rpanel = app.getRosterPanel();
            if (rpanel == null)
                return;

            if (!rpanel.isUserOnRoster(jid)) {
                int res = JOptionPane.showConfirmDialog(this,
                    "You may only invite players on your roster.\n"
                    +"Do you want to add the user " + jid + "?\n",
                    app.getAppName() + ": Add To Roster",
                    JOptionPane.OK_CANCEL_OPTION);
                if (res == JOptionPane.CANCEL_OPTION)
                    return;

                try {
                    rpanel.addUserToRoster(jid, null);
                }
                catch (Exception ex) {
                    new ErrorWrapper(ex);
                    JOptionPane.showMessageDialog(this, 
                        "Unable to add to roster:\n" + ex.toString(),
                        app.getAppName() + ": Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                JOptionPane.showMessageDialog(this, 
                    "When " + jid + " accepts the subscription,\n"
                    +"you will be able to invite him or her.",
                    app.getAppName() + ": Error",
                    JOptionPane.INFORMATION_MESSAGE);
                dispose();
                return;                
            }

            List resources = rpanel.listVolityClientResources(jid);
            
            if (resources == null) {
                // Logged off
                JOptionPane.showMessageDialog(this, 
                    "The user " + jid + "\n"
                    +"is not presently available on Jabber.",
                    app.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (resources.size() == 0) {
                // Logged on, but not with a Volity client.
                String[] options = { "Begin Chat", "Cancel" };
                int res = JOptionPane.showOptionDialog(this,
                    "The user " + jid + "\n"
                    +"is available, but is not using a Volity client.",
                    app.getAppName() + ": Error",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null,
                    options, options[0]);

                if (res == 0) {
                    dispose();
                    app.chatWithUser(jid);
                }

                return;
            }

            // We've got a JID.
            jid = (String)resources.remove(0);

            if (resources.size() > 0) {
                /*
                 * Whoops, we've got more than one JID. What to do with the
                 * remaining ones? We want to send out extra invitations, but
                 * we don't want them to throw up error dialogs or affect the
                 * SendInvite box. So we hand them off to a TableWindow API.
                 */
                mOwner.sendQuietInvites(resources, msg);
            }
        }

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
                app.getAppName() + ": Error",
                JOptionPane.ERROR_MESSAGE);
        }
        catch (Exception ex) {
            new ErrorWrapper(ex);
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            JOptionPane.showMessageDialog(this, 
                "Unable to send invitation:\n" + ex.toString(),
                app.getAppName() + ": Error",
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
