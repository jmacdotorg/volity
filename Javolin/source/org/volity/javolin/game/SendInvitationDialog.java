package org.volity.javolin.game;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.Reader;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.jivesoftware.smack.util.StringUtils;
import org.volity.client.GameTable;
import org.volity.client.comm.RPCBackground;
import org.volity.client.data.JIDTransfer;
import org.volity.client.translate.TokenFailure;
import org.volity.client.translate.TranslateToken;
import org.volity.jabber.JIDUtils;
import org.volity.javolin.BaseDialog;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.roster.RosterPanel;

public class SendInvitationDialog extends BaseDialog
{
    private final static String NODENAME = "SendInvitationDialog";
    private final static String USERID_KEY = "UserID";
    private final static String FULLID_KEY = "FullID";

    private TableWindow mOwner;
    private GameTable mGameTable;

    private String mFullJID = null;
    private JTextField mUserIdField;
    private JTextArea mMessageField;
    private JButton mCancelButton;
    private JButton mInviteButton;

    /**
     * The recipient may be the (bare or full) JID of someone to invite. The
     * dialog will appear with that JID in the "to" field.
     *
     * The handling is stranger than you might imagine, because we want to
     * route invitations to full JIDs if at all possible, but we don't want to
     * *display* the full JID. So we keep the resource string hidden.
     * Furthermore, if the user changes the (bare) JID, we don't want to use
     * the same hidden resource string. So we also keep a hidden note of what
     * the JID was that the resource was attached to. Clear?
     *
     * If recipient is null, the dialog will appear showing the last person you
     * invited (on the theory that you play with that person a lot).
     */
    public SendInvitationDialog(TableWindow owner, GameTable gameTable,
        String recipient) {
        super(owner, "Invite A Player", false, NODENAME);
        setTitle(JavolinApp.getAppName() + ": " + localize("WindowTitle"));

        mOwner = owner;
        mGameTable = gameTable;

        buildUI();
        setResizable(false);
        pack();

        JIDFieldTransferHandler.Acceptor acceptor = new JIDFieldTransferHandler.Acceptor() {
                public void accept(String jid) {
                    if (JIDUtils.hasResource(jid)) {
                        mFullJID = jid;
                        jid = StringUtils.parseBareAddress(jid);
                    }
                    else {
                        mFullJID = null;
                    }
                    mUserIdField.setText(jid);
                }
            };
        mUserIdField.setTransferHandler(new JIDFieldTransferHandler(acceptor));

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();

        // Restore default field values
        restoreFieldValues();

        if (recipient != null) {
            acceptor.accept(recipient);
        }

        mCancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    dispose();
                }
            });

        mInviteButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    String jid = expandJIDField(mUserIdField);
                    if (jid == null) {
                        complainMustEnter(mUserIdField, localize("MustEnterJID"));
                        return;
                    }

                    // Is this a bare version of our original hidden JID?
                    if (!JIDUtils.hasResource(jid)
                        && mFullJID != null
                        && JIDUtils.bareMatch(jid, mFullJID)) {
                        jid = mFullJID;
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
     * First, we check the roster. If there's no subscription, we send out the
     * invitation with the bare JID. The referee will have to do its best.
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

            String selfbarejid = StringUtils.parseBareAddress(mGameTable.getConnection().getUser());
            if (jid.equals(selfbarejid)) {
                JOptionPane.showMessageDialog(this, 
                    localize("ErrorAlreadyAtTable"),
                    app.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (rpanel.isUserOnRoster(jid)) {
                List resources = rpanel.listVolityClientResources(jid);
            
                if (resources == null) {
                    // Logged off
                    JOptionPane.showMessageDialog(this, 
                        localize("ErrorJIDNotAvailable", jid),
                        app.getAppName() + ": Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (resources.size() == 0) {
                    // Logged on, but not with a Volity client.
                    String[] options = { 
                        localize("ButtonBeginChat"),
                        localize("ButtonCancel") 
                    };
                    int res = JOptionPane.showOptionDialog(this,
                        localize("ErrorNoVolityClient", jid),
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
                     * remaining ones? We want to send out extra invitations,
                     * but we don't want them to throw up error dialogs or
                     * affect the SendInvite box. So we hand them off to a
                     * TableWindow API.
                     */
                    mOwner.sendQuietInvites(resources, msg);
                }
            }
        }

        // We now have a JID, which should be full but may not be.

        RPCBackground.Callback callback = new Callback(jid, msg, true);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        mGameTable.getReferee().invitePlayer(jid, msg, callback, null);
    }

    private class Callback implements RPCBackground.Callback {
        String jid;
        String msg;
        boolean firsttime;

        public Callback(String jid, String msg, boolean firsttime) {
            this.jid = jid;
            this.msg = msg;
            this.firsttime = firsttime;
        }

        public void run(Object result, Exception err, Object rock) {
            JavolinApp app = JavolinApp.getSoleJavolinApp();

            /*
             * We might want to retry with the resource string trimmed off.
             * Sorry about the slant code.
             */
            if (err != null && err instanceof TokenFailure) {
                TokenFailure tok = (TokenFailure)err;
                String watcherr = "volity.relay_failed";
                List ls = tok.getTokens();
                if (firsttime 
                    && ls.size() > 0 && watcherr.equals(ls.get(0))) {
                    String barejid = StringUtils.parseBareAddress(jid);
                    if (!jid.equals(barejid)) {
                        RPCBackground.Callback callback = new Callback(jid, msg, false);
                        mGameTable.getReferee().invitePlayer(barejid,
                            msg, callback, null);
                        return;
                    }
                }
            }

            // No retry. Just report the problem or close the dialog.

            if (err == null) {
                // success; close dialog
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                dispose();
            }
            else if (err instanceof TokenFailure) {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                TokenFailure ex = (TokenFailure)err;
                String errtext = mOwner.getTranslator().translate(ex);
                JOptionPane.showMessageDialog(
                    SendInvitationDialog.this,
                    localize("ErrorCannotSend")+"\n" + errtext,
                    app.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
            else {
                new ErrorWrapper(err);
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        
                JOptionPane.showMessageDialog(
                    SendInvitationDialog.this, 
                    localize("ErrorCannotSend")+"\n" + err.toString(),
                    app.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Saves the current text of the fields to the preferences storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        String jid = mUserIdField.getText().trim();

        if (JIDUtils.hasResource(jid))
            mFullJID = jid;
        else if (!JIDUtils.bareMatch(jid, mFullJID))
            mFullJID = null;

        prefs.put(USERID_KEY, jid);
        if (mFullJID == null)
            prefs.remove(FULLID_KEY);
        else
            prefs.put(FULLID_KEY, mFullJID);
    }

    /**
     * Reads the default values from the preferences storage and fills in the
     * text fields.
     */
    private void restoreFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mUserIdField.setText(prefs.get(USERID_KEY, ""));
        mFullJID = prefs.get(FULLID_KEY, null);
    }

    /**
     * This class handles dropping JIDs (and regular text) into a JTextField.
     * It may wind up being useful for other dialogs, in which case I'll move
     * it to a separate class.
     *
     * When you construct the JIDFieldTransferHandler, you may (optionally)
     * pass in an Acceptor. This is called when a JID is dragged into the
     * field. If you don't provide an Acceptor, the default behavior is to set
     * the field to the bare form of the JID.
     */
    private static class JIDFieldTransferHandler extends TransferHandler {
        public interface Acceptor {
            public void accept(String jid);
        }
        private Acceptor mAcceptor;
        public JIDFieldTransferHandler() {
            mAcceptor = null;
        }
        public JIDFieldTransferHandler(Acceptor acceptor) {
            mAcceptor = acceptor;
        }
        public boolean canImport(JComponent comp, DataFlavor[] flavors) {
            for (int ix=0; ix<flavors.length; ix++) {
                DataFlavor flavor = flavors[ix];
                if (flavor == JIDTransfer.JIDFlavor)
                    return true;
                if (flavor.isFlavorTextType())
                    return true;
            }
            return false;
        }
        public boolean importData(JComponent comp, Transferable transfer) {
            try {
                JTextField field = (JTextField)comp;

                if (transfer.isDataFlavorSupported(JIDTransfer.JIDFlavor)) {
                    JIDTransfer obj = (JIDTransfer)transfer.getTransferData(JIDTransfer.JIDFlavor);
                    String jid = obj.getJID();
                    if (mAcceptor == null) {
                        jid = StringUtils.parseBareAddress(jid);
                        field.setText(jid);
                    }
                    else {
                        mAcceptor.accept(jid);
                    }
                    return true;
                }

                DataFlavor flavor = DataFlavor.selectBestTextFlavor(transfer.getTransferDataFlavors());
                if (flavor != null) {
                    String val = "";

                    Reader reader = flavor.getReaderForText(transfer);
                    BufferedReader bufreader = new BufferedReader(reader);
                    String line = bufreader.readLine();
                    while (line != null) {
                        val = val+line;
                        line = bufreader.readLine();
                    }
                    bufreader.close();
                    reader.close();
                    field.getDocument().insertString(field.getCaretPosition(), val, null);
                    return true;
                }

                return false;
            }
            catch (Exception ex) {
                new ErrorWrapper(ex);
                return false;
            }
        }
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

        label = new JLabel(localize("LabelUser"));
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

        label = new JLabel(localize("LabelMessage"));
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
        mCancelButton = new JButton(localize("ButtonCancel"));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.5;
        buttonPanel.add(mCancelButton, c);

        // Add Invite button
        mInviteButton = new JButton(localize("ButtonInvite"));
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
