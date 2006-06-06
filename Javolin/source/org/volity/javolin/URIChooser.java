package org.volity.javolin;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.*;
import org.volity.javolin.BaseDialog;

/**
 * Extremely simple dialog which accepts a typed-in URI.
 *
 * To use this, create a URIChooser, call setVisible(true), and then call
 * getResult(). If the result is null, then the user hit "cancel" instead of
 * "ok".
 */
public class URIChooser extends BaseDialog
{
    private final static String NODENAME = "URIChooser";

    private JTextField mField;
    private JButton mOkayButton;
    private JButton mCancelButton;

    private URI mResult = null;

    /** Constructor. */
    public URIChooser(Frame owner) {
        super(owner, "Enter URI", true, NODENAME);

        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();

        mCancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    dispose();
                }
            });

        mOkayButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    String str = mField.getText().trim();
                    if (str.equals("")) {
                        mField.requestFocusInWindow();
                        return;
                    }

                    try {
                        mResult = new URI(str);
                    }
                    catch (URISyntaxException ex) {
                        JOptionPane.showMessageDialog(URIChooser.this, 
                            "Invalid URI:\n" + ex.getMessage(),
                            JavolinApp.getAppName() + ": Error",
                            JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    dispose();
                }
            });
    }

    /**
     * Retrieve the URI entered by the user. If he cancelled the dialog, this
     * will be null.
     */
    public URI getResult() {
        return mResult;
    }

    /** Create the interface. */
    private void buildUI() {
        Container cPane = getContentPane();
        cPane.setLayout(new GridBagLayout());
        GridBagConstraints c;

        int row = 0;

        mField = new JTextField(30);
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
}
