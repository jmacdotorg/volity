package org.volity.testbench;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.volity.client.Metadata;
import org.volity.client.VersionNumber;
import org.volity.client.VersionSpec;
import org.volity.client.translate.TranslateToken;
import org.volity.javolin.BaseDialog;

/**
 * Simple window which displays metadata from a file.
 */
public class MetadataDialog extends BaseDialog
{
    private final static String NODENAME = "TestbenchMetadataDialog";

    private static MetadataDialog soleMetadataDialog = null;

    /**
     * There should only be one dialog at a time. This returns it if there is
     * one, or else creates it.
     */
    public static MetadataDialog getSoleMetadataDialog(File file,
        TestUI.ErrorHandler errorHandler) {
        if (soleMetadataDialog == null) {
            soleMetadataDialog = new MetadataDialog(file, errorHandler);
        }
        return soleMetadataDialog;
    }

    /**
     * If there's a dialog open, reload its contents.
     */
    public static void reloadSoleMetadataDialog() {
        if (soleMetadataDialog != null) {
            soleMetadataDialog.reloadUI();
        }
    }

    private File mFile;
    private JButton mButton;
    private TestUI.ErrorHandler errorHandler;

    public MetadataDialog(File file, TestUI.ErrorHandler errorHandler) {
        super(null, "Metadata", false, NODENAME);

        mFile = file;
        this.errorHandler = errorHandler;

        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();

        addWindowListener(
            new WindowAdapter() {
                public void windowOpened(WindowEvent ev) {
                    // Ensure that Enter triggers the "OK" button.
                    mButton.requestFocusInWindow();
                }
                public void windowClosed(WindowEvent ev) {
                    soleMetadataDialog = null;
                }
            });
    }

    /**
     * Rebuild the contents of the window (because the metadata has changed).
     */
    protected void reloadUI() {
        buildUI();
        pack();
        repaint(0, 0, 999, 999);
    }

    private interface AddLine {
        void add(int row, String key, String value);
    }

    /**
     * Create the dialog UI. This is also called at reload time, to remove and
     * recreate the UI, because new metadata might have been loaded in.
     */
    private void buildUI() {
        final Container cPane = getContentPane();
        cPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        String msg;
        List ls;

        cPane.removeAll();

        int row = 0;

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
                    c.gridy = row;
                    c.weightx = 1;
                    c.anchor = GridBagConstraints.WEST;
                    c.insets = new Insets(SPACING, SPACING, 0, MARGIN);
                    cPane.add(field, c);
                }
            };

        AddLine addarea = new AddLine() {
                public void add(int row, String key, String value) {
                    JLabel label;
                    JTextArea field;
                    GridBagConstraints c;

                    boolean isBlank = (value == null);
                    if (isBlank)
                        value = "not available";

                    label = new JLabel(key);
                    c = new GridBagConstraints();
                    c.gridx = 0;
                    c.gridy = row;
                    c.anchor = GridBagConstraints.NORTHWEST;
                    c.insets = new Insets(SPACING, MARGIN, 0, 0);
                    cPane.add(label, c);

                    field = new JTextArea(value);
                    field.setEditable(false);
                    if (!isBlank)
                        field.setRows(4);
                    else
                        field.setRows(2);
                    field.setLineWrap(true);
                    field.setWrapStyleWord(true);
                    if (!isBlank)
                        field.setFont(new Font("SansSerif", Font.PLAIN, 12));
                    else
                        field.setFont(new Font("SansSerif", Font.ITALIC, 12));
                    c = new GridBagConstraints();
                    c.gridx = 1;
                    c.gridy = row;
                    c.weightx = 1;
                    c.fill = GridBagConstraints.HORIZONTAL;
                    c.anchor = GridBagConstraints.WEST;
                    c.insets = new Insets(SPACING, SPACING, 0, MARGIN);
                    JScrollPane scroller = new JScrollPane(field);
                    scroller.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    cPane.add(scroller, c);
                }
            };

        Metadata data;

        try {
            data = Metadata.parseSVGMetadata(mFile);
        }
        catch (Exception ex) {
            errorHandler.error(ex);
            msg = ex.toString();
            addarea.add(row++, "Error:", msg);

            // Create an empty set, so that we can finish the routine
            data = new Metadata();
        }

        String currentLanguage = TranslateToken.getLanguage();

        msg = data.get(Metadata.DC_TITLE, currentLanguage);
        adder.add(row++, "Title:", msg);
        msg = data.get(Metadata.DC_DESCRIPTION, currentLanguage);
        addarea.add(row++, "Description:", msg);

        ls = data.getAll(Metadata.DC_CREATOR);
        for (int ix=0; ix<ls.size(); ix++)
            adder.add(row++, "Creator:", (String)ls.get(ix));

        msg = data.get(Metadata.DC_CREATED);
        if (msg != null)
            adder.add(row++, "Created:", msg);
        msg = data.get(Metadata.DC_MODIFIED);
        if (msg != null)
            adder.add(row++, "Modified:", msg);

        ls = data.getAll(Metadata.DC_LANGUAGE);
        msg = "";
        for (int ix=0; ix<ls.size(); ix++) {
            if (msg.length() != 0)
                msg += ", ";
            msg += (String)ls.get(ix);
        }
        if (msg.length() != 0)
            adder.add(row++, "Languages:", msg);

        msg = data.get(Metadata.VOLITY_VERSION);
        adder.add(row++, "Version:", msg);

        ls = data.getAll(Metadata.VOLITY_RULESET);
        for (int ix=0; ix<ls.size(); ix++) {
            String ruleset = (String)ls.get(ix);
            try {
                VersionSpec.fromURI(ruleset);
            }
            catch (URISyntaxException ex) {
                ruleset += "   [invalid URI]";
            }
            catch (VersionNumber.VersionFormatException ex) {
                ruleset += "   [invalid spec]";
            }
            adder.add(row++, "Ruleset:", ruleset);
        }

        msg = data.get(Metadata.VOLITY_DESCRIPTION_URL);
        if (msg != null) {
            try {
                new URL(msg);
            }
            catch (MalformedURLException ex) {
                msg += "   [invalid URL]";
            }
            adder.add(row++, "Description URL:", msg);
        }
        msg = data.get(Metadata.VOLITY_REQUIRES_ECMASCRIPT_API);
        if (msg != null) {
            try {
                new VersionSpec(msg);
            }
            catch (VersionNumber.VersionFormatException ex) {
                msg += "   [invalid spec]";
            }
            adder.add(row++, "Requires ECMA API:", msg);
        }

        msg = data.get(Metadata.VOLITY_PROVIDES_RESOURCE);
        if (msg != null) {
            try {
                VersionNumber.fromURI(msg);
            }
            catch (URISyntaxException ex) {
                msg += "   [invalid URI]";
            }
            catch (VersionNumber.VersionFormatException ex) {
                msg += "   [invalid number]";
            }
            adder.add(row++, "Provides Resource:", msg);
        }

        ls = data.getAll(Metadata.VOLITY_REQUIRES_RESOURCE);
        for (int ix=0; ix<ls.size(); ix++) {
            String resource = (String)ls.get(ix);
            try {
                VersionSpec.fromURI(resource);
            }
            catch (URISyntaxException ex) {
                resource += "   [invalid URI]";
            }
            catch (VersionNumber.VersionFormatException ex) {
                resource += "   [invalid spec]";
            }
            adder.add(row++, "Requires Resource:", resource);
        }


        mButton = new JButton("OK");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        cPane.add(mButton, c);
        getRootPane().setDefaultButton(mButton);

        mButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    dispose();
                }
            });

    }
}
