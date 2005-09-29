package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.volity.client.GameInfo;
import org.volity.client.GameTable;
import org.volity.javolin.BaseDialog;

public class InfoDialog extends BaseDialog
{
    private final static String NODENAME = "GameInfoDialog";

    private TableWindow mOwner;
    private GameTable mGameTable;
    private GameInfo mGameInfo;

    private JButton mButton;

    public InfoDialog(TableWindow owner, GameTable gameTable,
        GameInfo gameInfo) {
        super(owner, "Game Information", false, NODENAME);

        mOwner = owner;
        mGameTable = gameTable;
        mGameInfo = gameInfo;

        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();

        mButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    dispose();
                }
            });

        addWindowListener(
            new WindowAdapter() {
                public void windowOpened(WindowEvent ev) {
                    // Ensure that Enter triggers the "Ok" button.
                    mButton.requestFocusInWindow();
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

    private interface AddLine {
        void add(int row, String key, String value);
    }

    /**
     * Create the window UI.
     */
    private void buildUI() {
        final Container cPane = getContentPane();
        cPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        String msg;

        int row = 0;

        JLabel labelName = new JLabel(mOwner.getWindowName());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
        cPane.add(labelName, c);

        AddLine adder = new AddLine() {
                public void add(int row, String key, String value) {
                    JLabel label;
                    JTextField field;
                    GridBagConstraints c;

                    boolean isBlank = (value == null);
                    if (isBlank)
                        value = "not available";

                    label = new JLabel(key);
                    c = new GridBagConstraints();
                    c.gridx = 0;
                    c.gridy = row;
                    c.anchor = GridBagConstraints.SOUTHWEST;
                    c.insets = new Insets(SPACING, MARGIN, 0, 0);
                    cPane.add(label, c);

                    field = new JTextField(value);
                    field.setEditable(false);
                    if (!isBlank)
                        field.setFont(new Font("SansSerif", Font.PLAIN, 12));
                    else
                        field.setFont(new Font("SansSerif", Font.ITALIC, 12));
                    field.setBorder(BorderFactory.createEmptyBorder(3, 4, 1, 4));
                    c = new GridBagConstraints();
                    c.gridx = 1;
                    c.gridy = row++;
                    c.weightx = 1;
                    c.anchor = GridBagConstraints.WEST;
                    c.insets = new Insets(SPACING, SPACING, 0, MARGIN);
                    cPane.add(field, c);
                }
            };

        adder.add(row++, "Table ID:", mGameTable.getRoom());
        adder.add(row++, "Referee ID:", mGameTable.getRefereeJID());
        adder.add(row++, "Parlor ID:", mGameInfo.getParlorJID());
        msg = null;
        if (mGameInfo.getGameWebsiteURL() != null)
            msg = mGameInfo.getGameWebsiteURL().toString();
        adder.add(row++, "Game site:", msg);
        msg = null;
        if (mGameInfo.getRulesetURI() != null)
            msg = mGameInfo.getRulesetURI().toString();
        adder.add(row++, "Ruleset:", msg);
        adder.add(row++, "Ruleset version:", mGameInfo.getRulesetVersion());
        msg = mOwner.getUIUrl().toString();
        adder.add(row++, "UI loaded:", msg);
        adder.add(row++, "Admin Email:", mGameInfo.getParlorContactEmail());
        adder.add(row++, "Admin JID:", mGameInfo.getParlorContactJID());

        mButton = new JButton("Ok");
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        cPane.add(mButton, c);
        getRootPane().setDefaultButton(mButton);
    }    
}
