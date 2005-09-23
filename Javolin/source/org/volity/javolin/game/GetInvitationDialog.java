package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.prefs.Preferences;
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
import org.volity.client.Invitation;
import org.volity.javolin.*;

/**
 * Dialog box that appears when a game invitation is received.
 */
public class GetInvitationDialog extends BaseDialog
{
    private final static String NODENAME = "GetInvitationDialog";
    private final static String NICKNAME_KEY = "Nickname";

    XMPPConnection mConnection;
    TableWindow mTableWindow;
    JavolinApp mOwner;
    private Invitation mInvite;

    private JButton mAcceptButton;
    private JButton mDeclineButton;
    private JButton mChatButton;
    private JTextField mNicknameField;

    public GetInvitationDialog(JavolinApp owner, XMPPConnection connection, 
        Invitation inv) {
        super(owner, "Invitation", false, NODENAME);

        mInvite = inv;
        mOwner = owner;
        mConnection = connection;
        mTableWindow = null;

        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();

        // Restore default field values
        restoreFieldValues();

        mDeclineButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    dispose();
                }
            });

        mChatButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    mOwner.chatWithUser(mInvite.getPlayerJID());
                    dispose();
                }
            });

        mAcceptButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    doJoin();
                }
            });

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

        /* Create the TableWindow.
         *
         * The Invitation may or may not contain a parlor ID. Since we can't
         * rely on it, I've written this code to not look for it at all. We
         * always join the MUC and query for the server ID -- just as we do in
         * JoinTableAtDialog.
         *
         * In fact, the code below is directly stolen from JoinTableAtDialog.
         * If you change anything here, make the same change there.
         *
         * A more correct solution would be to have a single, asynchronous
         * entry point in TableWindow. This would accept various combinations
         * of arguments (including a parlor ID, or not), do all the work --
         * *not* blocking the calling thread -- and then call back to the
         * caller to indicate the success or failure of the operation.
         */
        try
        {
            tableID = mInvite.getTableJID();

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
            new ErrorWrapper(ex);
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
            new ErrorWrapper(ex);
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
            String refJID = gameTable.getRefereeJID();
            String serverID = null;

            //### This should be async, so it doesn't bog the Smack thread
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
            mOwner.handleNewTableWindow(mTableWindow);

            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            dispose();
        }
        catch (Exception ex)
        {
            new ErrorWrapper(ex);
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
     * Gets the TableWindow that was created.
     *
     * @return The TableWindow for the game table that was joined, or
     *         null if the join failed.
     */
    public TableWindow getTableWindow()
    {
        return mTableWindow;
    }

    /**
     * Saves the current text of the fields to the preferences storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        prefs.put(NICKNAME_KEY, mNicknameField.getText());
    }

    /**
     * Reads the default values from the preferences storage and fills in the
     * text fields.
     */
    private void restoreFieldValues()
    {
        // Make a default nickname based on the user ID
        String defNick = mConnection.getUser();
        defNick = defNick.substring(0, defNick.indexOf('@'));

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mNicknameField.setText(prefs.get(NICKNAME_KEY, defNick));
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

        field = new JTextField(mInvite.getPlayerJID());
        field.setEditable(false);
        field.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
        cPane.add(field, c);

        String gamename = mInvite.getGameName();
        if (gamename != null)
            gamename = gamename.trim();

        msg = "  has invited you to join a game";
        if (gamename != null && !gamename.equals(""))
            msg = msg + " of";
        else
            msg = msg + ".";
        label = new JLabel(msg);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        if (gamename != null && !gamename.equals("")) {
            field = new JTextField(gamename);
            field.setEditable(false);
            field.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
            cPane.add(field, c);
        }

        String message = mInvite.getMessage();
        if (message != null)
            message = message.trim();

        if (message != null && !message.equals("")) {
            JTextArea textarea = new JTextArea();
            textarea.setEditable(false);
            textarea.setRows(4);
            textarea.setLineWrap(true);
            textarea.setWrapStyleWord(true);
            textarea.setText(message);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
            JScrollPane scroller = new JScrollPane(textarea);
            scroller.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            cPane.add(scroller, c);
        }

        label = new JLabel("Nickname:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        mNicknameField = new JTextField(20);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(GAP, SPACING, 0, MARGIN);
        cPane.add(mNicknameField, c);

        // Add panel with Cancel and Create buttons
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 1;
        cPane.add(buttonPanel, c);

        mAcceptButton = new JButton("Accept");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mAcceptButton, c);

        mChatButton = new JButton("Decline and Chat");
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mChatButton, c);

        mDeclineButton = new JButton("Decline");
        c = new GridBagConstraints();
        c.gridx = 2;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mDeclineButton, c);

    }
}
