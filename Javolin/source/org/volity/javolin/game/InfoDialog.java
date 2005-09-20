package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.volity.client.GameTable;
import org.volity.javolin.BaseDialog;

public class InfoDialog extends BaseDialog
{
    private final static String NODENAME = "GameInfoDialog";

    private TableWindow mOwner;
    private GameTable mGameTable;

    private JButton mButton;

    public InfoDialog(TableWindow owner, GameTable gameTable) {
        super(owner, "Game Information", false, NODENAME);

        mOwner = owner;
        mGameTable = gameTable;

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

        // If the table window closes, this should close.
        mOwner.addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    dispose();
                }
            });
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

        // ### Improve window label, please
        label = new JLabel(mOwner.getTitle());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        label = new JLabel("Table ID:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.SOUTHWEST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        msg = mGameTable.getRoom();
        field = new JTextField(msg);
        field.setEditable(false);
        field.setFont(new Font("SansSerif", Font.PLAIN, 12));
        field.setBorder(BorderFactory.createEmptyBorder(3, 4, 1, 4));
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        cPane.add(field, c);

        label = new JLabel("Referee ID:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.SOUTHWEST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        msg = mGameTable.getRefereeJID();
        field = new JTextField(msg);
        field.setEditable(false);
        field.setFont(new Font("SansSerif", Font.PLAIN, 12));
        field.setBorder(BorderFactory.createEmptyBorder(3, 4, 1, 4));
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        cPane.add(field, c);

        label = new JLabel("Parlor ID:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.SOUTHWEST;
        c.insets = new Insets(SPACING, MARGIN, 0, MARGIN);
        cPane.add(label, c);

        msg = mGameTable.getParlorJID();
        field = new JTextField(msg);
        field.setEditable(false);
        field.setFont(new Font("SansSerif", Font.PLAIN, 12));
        field.setBorder(BorderFactory.createEmptyBorder(3, 4, 1, 4));
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, SPACING, 0, MARGIN);
        cPane.add(field, c);

        mButton = new JButton("Ok");
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        cPane.add(mButton, c);
    }    
}
