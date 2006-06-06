package org.volity.javolin;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.volity.javolin.BaseDialog;

/**
 * Extremely simple dialog which accepts a typed-in JID.
 *
 * To use this, create a JIDChooser, setVisible(true), and then call
 * getResult(). If the result is null, then the user hit "cancel" instead of
 * "ok".
 *
 * Subclasses handle choosing JIDs of particular types. Currently,
 * JIDChooser.Factory is the only subclass.
 */
public class JIDChooser extends BaseDialog
{
    public final static String NODENAME = "JIDChooser";
    private final static String JID_KEY = "JID";

    private JTextField mField;
    private JButton mOkayButton;
    private JButton mCancelButton;

    private String mResult = null;

    /** Constructor. */
    public JIDChooser(Frame owner) {
        this(owner, null);
    }

    /** Constructor. */
    public JIDChooser(Frame owner, String target) {
        super(owner, "Enter JID", true, NODENAME);
        if (target != null) 
            setTitle("Enter JID of " + target);

        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();
        restoreFieldValues();

        mCancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    dispose();
                }
            });

        mOkayButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    String str = expandJIDField(mField);
                    if (str == null || str.equals("")) {
                        mField.requestFocusInWindow();
                        return;
                    }

                    saveFieldValues();
                    mResult = str;
                    dispose();
                }
            });
    }

    /** String to distinguish subclasses. */
    private String getSubnode() { return ""; }

    /**
     * Retrieve the JID entered by the user. If he cancelled the dialog, this
     * will be null.
     */
    public String getResult() {
        return mResult;
    }

    /**
     * Saves the current text of the JID field to the preferences storage.
     */
    private void saveFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(JIDChooser.class).node(NODENAME+getSubnode());

        prefs.put(JID_KEY, mField.getText());
    }

    /**
     * Reads the default JID from the preferences storage and fills in the text
     * field.
     */
    private void restoreFieldValues()
    {
        Preferences prefs = Preferences.userNodeForPackage(JIDChooser.class).node(NODENAME+getSubnode());

        mField.setText(prefs.get(JID_KEY, ""));
    }

    /** Create the interface. */
    private void buildUI() {
        Container cPane = getContentPane();
        cPane.setLayout(new GridBagLayout());
        GridBagConstraints c;

        int row = 0;

        mField = new JTextField(20);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
        cPane.add(mField, c);

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

        mCancelButton = new JButton("Cancel");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.5;
        buttonPanel.add(mCancelButton, c);

        mOkayButton = new JButton("OK");
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mOkayButton, c);
        // Make Okay button default
        getRootPane().setDefaultButton(mOkayButton);

        // Make the buttons the same width
        Dimension dim = mOkayButton.getPreferredSize();

        if (mCancelButton.getPreferredSize().width > dim.width)
        {
            dim = mCancelButton.getPreferredSize();
        }

        mCancelButton.setPreferredSize(dim);
        mOkayButton.setPreferredSize(dim);
    }

    public static class Factory extends JIDChooser {
        public Factory(Frame owner) {
            super(owner, "Bot Factory");
        }
        private String getSubnode() { return "Factory"; }
    }
}
