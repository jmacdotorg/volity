package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.volity.client.data.ResourceInfo;
import org.volity.javolin.BaseDialog;
import org.volity.javolin.JavolinMenuBar;
import org.volity.javolin.TableColumnSaver;
import org.volity.javolin.URLChooser;

/**
 * The dialog which lets you select a game resource from the list available.
 *
 * This is a modal dialog; you call show(), let it do its thing, and get
 * control back when the dialog closes. You then call getSuccess() to see
 * whether the user selected something or cancelled; then getResult() to
 * retrieve the URL that was selected.
 */
public class ChooseResourceDialog extends BaseDialog
{
    private final static String NODENAME = "ChooseResourceDialog";

    private final static String COL_INTERFACE = "Resource";
    private final static String COL_URL = "URL";
    private final static String DEFAULT_URLSTR = "-";

    private boolean mSuccess = false;
    private URL mResult = null;

    private DefaultTableModel mModel;
    private int mLastChoiceIndex = 0;
    private JTable mTable;

    private TableColumnSaver mTableSaver;
    private JButton mCancelButton;
    private JButton mSelectButton;
    private JButton mFileButton;
    private JButton mURLButton;

    /**
     * Construct a dialog.
     * @param resList a list of ResourceInfo objects, representing choices
     *    that have come from the bookkeeper.
     * @param lastChoice the last URL to be chosen by the player. This may
     *    or may not be on the resList.
     * @param lastName the name of the resource that lastChoice refers to. 
     *    This is needed for display purposes.
     */
    public ChooseResourceDialog(List resList, URL lastChoice, String lastName) {
        super(null, "Select a Resource", true, NODENAME);

        // Construct the model.

        mModel = new UITableModel();
        mModel.addColumn(COL_INTERFACE);
        mModel.addColumn(COL_URL);

        boolean redundant = false;

        Object[] row = new Object[2];
        for (int ix=0; ix<resList.size(); ix++) {
            ResourceInfo info = (ResourceInfo)resList.get(ix);
            URL url = info.getLocation();
            row[0] = info.getName();
            row[1] = url;
            mModel.addRow(row);

            if (lastChoice != null && lastChoice.equals(url)) {
                redundant = true;
                mLastChoiceIndex = ix;
            }
        }

        if (lastChoice != null && !redundant) {
            row[0] = lastName;
            row[1] = lastChoice;
            mModel.insertRow(0, row);
            mLastChoiceIndex = 0;
        }

        // Throw in the default choice
        row[0] = "(default resource)";
        row[1] = DEFAULT_URLSTR;
        mModel.insertRow(0, row);
        if (lastChoice == null) 
            mLastChoiceIndex = 0;
        else 
            mLastChoiceIndex += 1;

        // Now that the model is done, build the UI.
        buildUI();

        mTableSaver = new TableColumnSaver(this, mTable, NODENAME);

        setSize(550, 270);
        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();
        restoreWindowState();

        mTable.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent ev) {
                    if (ev.getClickCount() >= 2)
                        doSelect();
                }
            });

        mCancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    mSuccess = false;
                    dispose();
                }
            });

        mFileButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    // Put up a standard file-choosing dialog.

                    JFileChooser filer = new JFileChooser();
                    filer.setDialogTitle("Select Local Resource");
                    filer.setApproveButtonText("Select");
                    filer.addChoosableFileFilter(new FileFilter() {
                            public boolean accept(File file) {
                                if (file.isDirectory())
                                    return true;
                                String name = file.getName().toLowerCase();
                                if (name.endsWith(".zip"))
                                    return true;
                                if (name.endsWith(".svg"))
                                    return true;
                                return false;
                            }
                            public String getDescription() {
                                return "Resource Packages (*.zip, *.svg)";
                            }
                        });
                    int val = filer.showOpenDialog(null);
                    
                    if (val == JFileChooser.APPROVE_OPTION) {
                        try {
                            File file = filer.getSelectedFile();
                            if (file.isFile()) {
                                mSuccess = true;
                                mResult = file.toURI().toURL();
                                dispose();
                            }
                        }
                        catch (MalformedURLException ex) {
                            // forget it
                        }
                    }
                }
            });

        mURLButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    URLChooser urler = new URLChooser(null);
                    urler.show();
                    URL url = urler.getResult();
                    if (url != null) {
                        mSuccess = true;
                        mResult = url;
                        dispose();
                    }
                }
            });

        mSelectButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    doSelect();
                }
            });

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    saveWindowState();
                }
            });

    }

    /** Store the window state preferences. */
    private void saveWindowState() {
        mTableSaver.saveState();
    }

    /** Load the window state preferences. */
    private void restoreWindowState() {
        mTableSaver.restoreState();
    }

    /**
     * The Select action. This is triggered by the OK button, and also by
     * double-clicking a row in the table.
     */
    private void doSelect() {
        // Make sure exactly one row is selected.
        int[] rows = mTable.getSelectedRows();
        if (rows.length != 1)
            return;

        Object urlo = mModel.getValueAt(rows[0], 1);
        if (urlo != null && DEFAULT_URLSTR.equals(urlo)) {
            urlo = null;
        }
        else {
            if (urlo == null || !(urlo instanceof URL))
                return;
        }

        mSuccess = true;
        mResult = (URL)urlo;
        dispose();
    }

    /**
     * Check whether the user selected a resource, or hit Cancel.
     */
    public boolean getSuccess() {
        return mSuccess;
    }

    /**
     * Retrieve the URL that was selected. Only call this if getSuccess()
     * returns true. If the user selected "default", this returns null.
     */
    public URL getResult() {
        return mResult;
    }

    /** 
     * Customization of the default TableModel. This model is entirely
     * non-editable.
     */
    private static class UITableModel extends DefaultTableModel {
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    /**
     * Customization of the default renderer. This renderer forces the hasFocus
     * flag off, so that no single cell is highlighted. (We only want to see
     * the whole-row selection highlight.)
     */
    private static class UITableCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable table,
            Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
            return super.getTableCellRendererComponent(table,
                value, isSelected, false, row, column);
        }
    }

    /** Build the dialog UI. */
    private void buildUI() {
        Container cPane = getContentPane();
        cPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        JLabel label;

        int row = 0;

        mTable = new JTable(mModel);
        mTable.setDefaultRenderer(Object.class, new UITableCellRenderer());
        mTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(mTable);
        scroll.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        cPane.add(scroll, c);
        
        // Add panel with Cancel and Create buttons (etc)
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        c.anchor = GridBagConstraints.EAST;
        cPane.add(buttonPanel, c);

        mFileButton = new JButton("Select File...");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        buttonPanel.add(mFileButton, c);

        mURLButton = new JButton("Enter URL...");
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        buttonPanel.add(mURLButton, c);

        // Blank stretchy spacer
        label = new JLabel(" ");
        c = new GridBagConstraints();
        c.gridx = 2;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 1;
        buttonPanel.add(label, c);

        mCancelButton = new JButton("Cancel");
        c = new GridBagConstraints();
        c.gridx = 3;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mCancelButton, c);

        mSelectButton = new JButton("Select");
        c = new GridBagConstraints();
        c.gridx = 4;
        c.gridy = 0;
        c.insets = new Insets(0, SPACING, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        buttonPanel.add(mSelectButton, c);
        // Make Select button default
        getRootPane().setDefaultButton(mSelectButton);

        // Make the buttons the same width
        Dimension dim = mSelectButton.getPreferredSize();

        if (mCancelButton.getPreferredSize().width > dim.width) {
            dim = mCancelButton.getPreferredSize();
        }

        mCancelButton.setPreferredSize(dim);
        mSelectButton.setPreferredSize(dim);

        // Can't be empty, because "default" is always an option
        mTable.setRowSelectionInterval(mLastChoiceIndex, mLastChoiceIndex);
    }
}
