package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.volity.client.GameTable;
import org.volity.client.data.GameInfo;
import org.volity.client.data.Metadata;
import org.volity.client.translate.TranslateToken;
import org.volity.javolin.BaseDialog;

/**
 * A window which displays all sorts of pedantic information about an active
 * game.
 */
public class InfoDialog extends BaseDialog
{
    private final static String NODENAME = "GameInfoDialog";
    private final static String TABPANESELECTION_KEY = "TabSelection";

    private TableWindow mOwner;
    private GameTable mGameTable;
    private GameInfo mGameInfo;
    private Metadata mMetadata;

    private JTabbedPane mTabPane;
    private JButton mButton;

    public InfoDialog(TableWindow owner, GameTable gameTable,
        GameInfo gameInfo, Metadata metadata) {
        super(owner, "Game Information", false, NODENAME);

        mOwner = owner;
        mGameTable = gameTable;
        mGameInfo = gameInfo;
        mMetadata = metadata;

        buildUI();
        setResizable(false);
        pack();

        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();
        restoreWindowState();

        mButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    dispose();
                }
            });

        addWindowListener(
            new WindowAdapter() {
                public void windowOpened(WindowEvent ev) {
                    // Ensure that Enter triggers the "OK" button.
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

        // If the user swaps tabs, save that fact.
        mTabPane.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    saveWindowState();
                }
            });
    }

    /** Save the state of the tab pane. */
    protected void saveWindowState() {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        int pos = mTabPane.getSelectedIndex();
        if (pos >= 0) {
            prefs.putInt(TABPANESELECTION_KEY, pos);
        }
    }

    /** Restore the state of the tab pane. */
    protected void restoreWindowState() {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        int pos = prefs.getInt(TABPANESELECTION_KEY, 0);
        try {
            mTabPane.setSelectedIndex(pos);
        }
        catch (IndexOutOfBoundsException ex) {
            // forget it 
        }
    }

    private interface AddLine {
        void add(Container pane, int row, String key, String value);
    }

    /**
     * Create the window UI.
     */
    private void buildUI() {
        mTabPane = new JTabbedPane();
        Container cPane = getContentPane();
        cPane.setLayout(new GridBagLayout());

        AddLine addblank = new AddLine() {
                public void add(Container pane, int row, String key, String value) {
                    JLabel label;
                    JTextField field;
                    GridBagConstraints c;

                    // Blank stretchy spacer
                    label = new JLabel(" ");
                    c = new GridBagConstraints();
                    c.gridx = 0;
                    c.gridy = row++;
                    c.weightx = 1;
                    c.weighty = 1;
                    c.gridwidth = GridBagConstraints.REMAINDER;
                    c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
                    pane.add(label, c);
                }
            };

        AddLine adder = new AddLine() {
                public void add(Container pane, int row, String key, String value) {
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
                    c.insets = new Insets(SPACING, SPACING, 0, 0);
                    pane.add(label, c);

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
                    c.insets = new Insets(SPACING, SPACING, 0, SPACING);
                    pane.add(field, c);
                }
            };

        AddLine addarea = new AddLine() {
                public void add(Container pane, int row, String key, String value) {
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
                    c.insets = new Insets(SPACING, SPACING, 0, 0);
                    pane.add(label, c);

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
                    c.insets = new Insets(SPACING, SPACING, 0, SPACING);
                    JScrollPane scroller = new JScrollPane(field);
                    scroller.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    pane.add(scroller, c);
                }
            };


        {
            JPanel pane = new JPanel(new GridBagLayout());
            String msg;
            
            int row = 0;

            adder.add(pane, row++, "Table ID:", mGameTable.getRoom());
            adder.add(pane, row++, "Referee ID:", mGameTable.getRefereeJID());
            adder.add(pane, row++, "Parlor ID:", mGameInfo.getParlorJID());
            msg = null;
            if (mGameInfo.getGameWebsiteURL() != null)
                msg = mGameInfo.getGameWebsiteURL().toString();
            adder.add(pane, row++, "Game site:", msg);
            msg = null;
            if (mGameInfo.getRulesetURI() != null)
                msg = mGameInfo.getRulesetURI().toString();
            adder.add(pane, row++, "Ruleset:", msg);
            adder.add(pane, row++, "Ruleset version:", mGameInfo.getRulesetVersion());
            adder.add(pane, row++, "Admin Email:", mGameInfo.getParlorContactEmail());
            adder.add(pane, row++, "Admin JID:", mGameInfo.getParlorContactJID());

            addblank.add(pane, row++, null, null);

            mTabPane.addTab("Game", pane);
        }


        {
            JPanel pane = new JPanel(new GridBagLayout());
            String msg;
            List ls;
            
            // Slightly cheesy way to get the client language setting
            String currentLanguage = TranslateToken.getLanguage();

            int row = 0;

            msg = mOwner.getUIUrl().toString();
            adder.add(pane, row++, "UI loaded:", msg);

            msg = mMetadata.get(Metadata.VOLITY_VERSION);
            adder.add(pane, row++, "Version:", msg);

            msg = mMetadata.get(Metadata.DC_TITLE, currentLanguage);
            if (msg != null)
                adder.add(pane, row++, "Title:", msg);
            msg = mMetadata.get(Metadata.DC_DESCRIPTION, currentLanguage);
            if (msg != null)
                addarea.add(pane, row++, "Description:", msg);

            ls = mMetadata.getAll(Metadata.DC_CREATOR);
            for (int ix=0; ix<ls.size(); ix++)
                adder.add(pane, row++, "Creator:", (String)ls.get(ix));

            msg = mMetadata.get(Metadata.DC_CREATED);
            if (msg != null)
                adder.add(pane, row++, "Created:", msg);
            msg = mMetadata.get(Metadata.DC_MODIFIED);
            if (msg != null)
                adder.add(pane, row++, "Modified:", msg);

            msg = mMetadata.get(Metadata.VOLITY_DESCRIPTION_URL);
            if (msg != null)
                adder.add(pane, row++, "Description URL:", msg);

            ls = mMetadata.getAll(Metadata.VOLITY_RULESET);
            for (int ix=0; ix<ls.size(); ix++)
                adder.add(pane, row++, "Ruleset:", (String)ls.get(ix));

            ls = mMetadata.getAll(Metadata.DC_LANGUAGE);
            msg = "";
            for (int ix=0; ix<ls.size(); ix++) {
                if (msg.length() != 0)
                    msg += ", ";
                msg += (String)ls.get(ix);
            }
            if (msg.length() != 0)
                adder.add(pane, row++, "Languages:", msg);

            msg = mMetadata.get(Metadata.VOLITY_REQUIRES_ECMASCRIPT_API);
            if (msg != null)
                adder.add(pane, row++, "Requires ECMA API:", msg);

            ls = mMetadata.getAll(Metadata.VOLITY_REQUIRES_RESOURCE);
            for (int ix=0; ix<ls.size(); ix++) {
                String resource = (String)ls.get(ix);
                adder.add(pane, row++, "Requires resource:", resource);
            }

            addblank.add(pane, row++, null, null);

            mTabPane.addTab("UI", pane);
        }

        List resList = mMetadata.getAllResources();
        for (int jx=0; jx<resList.size(); jx++) {
            URI key = (URI)resList.get(jx);
            Metadata subdata = mMetadata.getResource(key);
            if (subdata == null)
                continue;

            JPanel pane = new JPanel(new GridBagLayout());
            String msg;
            List ls;
            
            // Slightly cheesy way to get the client language setting
            String currentLanguage = TranslateToken.getLanguage();

            int row = 0;

            msg = key.toString();
            adder.add(pane, row++, "Resource URI:", msg);

            URL loc = mMetadata.getResourceLocation(key);
            if (loc != null)
                msg = loc.toString();
            else
                msg = "(default)";
            adder.add(pane, row++, "Resource loaded:", msg);

            msg = subdata.get(Metadata.VOLITY_VERSION);
            adder.add(pane, row++, "Version:", msg);

            msg = subdata.get(Metadata.DC_TITLE, currentLanguage);
            if (msg != null)
                adder.add(pane, row++, "Title:", msg);
            msg = subdata.get(Metadata.DC_DESCRIPTION, currentLanguage);
            if (msg != null)
                addarea.add(pane, row++, "Description:", msg);

            ls = subdata.getAll(Metadata.DC_CREATOR);
            for (int ix=0; ix<ls.size(); ix++)
                adder.add(pane, row++, "Creator:", (String)ls.get(ix));

            msg = subdata.get(Metadata.DC_CREATED);
            if (msg != null)
                adder.add(pane, row++, "Created:", msg);
            msg = subdata.get(Metadata.DC_MODIFIED);
            if (msg != null)
                adder.add(pane, row++, "Modified:", msg);

            msg = subdata.get(Metadata.VOLITY_DESCRIPTION_URL);
            if (msg != null)
                adder.add(pane, row++, "Description URL:", msg);

            ls = subdata.getAll(Metadata.DC_LANGUAGE);
            msg = "";
            for (int ix=0; ix<ls.size(); ix++) {
                if (msg.length() != 0)
                    msg += ", ";
                msg += (String)ls.get(ix);
            }
            if (msg.length() != 0)
                adder.add(pane, row++, "Languages:", msg);

            msg = subdata.get(Metadata.VOLITY_REQUIRES_ECMASCRIPT_API);
            if (msg != null)
                adder.add(pane, row++, "Requires ECMA API:", msg);

            ls = subdata.getAll(Metadata.VOLITY_PROVIDES_RESOURCE);
            for (int ix=0; ix<ls.size(); ix++) {
                String resource = (String)ls.get(ix);
                adder.add(pane, row++, "Provides resource:", resource);
            }

            addblank.add(pane, row++, null, null);

            msg = "Resource";
            if (resList.size() > 1)
                msg += " " + String.valueOf(jx+1);
            mTabPane.addTab(msg, pane);
        }

        GridBagConstraints c;
        int row = 0;

        JLabel labelName = new JLabel(mOwner.getWindowName());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
        cPane.add(labelName, c);

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(SPACING, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        cPane.add(mTabPane, c);

        mButton = new JButton("OK");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(SPACING, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        cPane.add(mButton, c);
        getRootPane().setDefaultButton(mButton);

    }    
}
