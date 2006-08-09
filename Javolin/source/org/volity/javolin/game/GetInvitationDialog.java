package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.util.StringUtils;
import org.volity.client.data.Invitation;
import org.volity.javolin.*;

/**
 * Dialog box that appears when a game invitation is received.
 */
public class GetInvitationDialog extends BaseWindow
    implements CloseableWindow
{
    private final static String NODENAME = "GetInvitationDialog";
    private final static String NICKNAME_KEY = "Nickname";

    private static SimpleDateFormat sTimeStampFormat = 
        new SimpleDateFormat("hh:mm aa");

    boolean mInProgress;
    XMPPConnection mConnection;
    JavolinApp mOwner;
    private Invitation mInvite;

    private JButton mAcceptButton;
    private JButton mDeclineButton;
    private JButton mChatButton;
    private JTextField mNicknameField;

    public GetInvitationDialog(JavolinApp owner, XMPPConnection connection, 
        Invitation inv) {
        super(owner, "Invitation", NODENAME);

        mInvite = inv;
        mOwner = owner;
        mConnection = connection;
        mInProgress = false;

        String title = JavolinApp.getAppName() + ": " + localize("WindowTitle");
        String fromjid = mInvite.getPlayerJID();
        if (fromjid != null) {
            fromjid = StringUtils.parseName(fromjid);
            if (fromjid != null && fromjid.length() > 0)
                title = title + " " + localize("WindowSuffix", fromjid);
        }
        setTitle(title);

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
        if (mInProgress)
            return;

        mInProgress = true;

        // Store field values in preferences
        saveFieldValues();

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        MakeTableWindow maker = new MakeTableWindow(mOwner, mConnection, this);
        maker.joinTable(mInvite.getTableJID(), mNicknameField.getText(),
            new MakeTableWindow.TableWindowCallback() {
                public void fail() {
                    mInProgress = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
                public void succeed(TableWindow win) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    dispose();
                }
            });
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

        label = new JLabel(sTimeStampFormat.format(mInvite.getTimestamp()));
        label.setFont(new Font("SansSerif", Font.PLAIN, 9));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        msg = mInvite.getPlayerJID();
        msg = StringUtils.parseBareAddress(msg);
        field = new JTextField(msg);
        field.setEditable(false);
        field.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        cPane.add(field, c);

        String gamename = mInvite.getGameName();
        if (gamename != null)
            gamename = gamename.trim();

        if (gamename != null && !gamename.equals(""))
            msg = "  " + localize("MessageInvitedOf");
        else
            msg = "  " + localize("MessageInvited");
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

        label = new JLabel(localize("LabelNickname"));
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

        mAcceptButton = new JButton(localize("ButtonAccept"));
        c = new GridBagConstraints();
        c.gridx = 2;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mAcceptButton, c);

        mChatButton = new JButton(localize("ButtonDeclineChat"));
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mChatButton, c);

        mDeclineButton = new JButton(localize("ButtonDecline"));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mDeclineButton, c);
    }
}
