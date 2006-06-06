package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;
import org.volity.client.GameServer;
import org.volity.client.comm.DiscoBackground;
import org.volity.jabber.JIDUtils;
import org.volity.javolin.BaseDialog;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.JIDChooser;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.TableColumnSaver;
import org.volity.javolin.URIChooser;

/**
 * The dialog which lets you choose a factory, and then a bot from that
 * factory.
 *
 * This is a modal dialog; you call setVisible(true), let it do its thing, and
 * get control back when the dialog closes. You then call getSuccess() to see
 * whether the user selected something or cancelled; then getFactoryJID() and
 * getBotURI() for the selection.
 */
public class ChooseFactoryBotDialog extends BaseDialog
{
    private final static String NODENAME = "ChooseFactoryBotDialog";
    private final static String NODENAMELASTFACTORY = NODENAME+"/LastFactoryChosen";
    private final static String NODENAMELASTBOT = NODENAME+"/LastBotChosen";
    private final static String NODENAMEFACTORYNAMES = NODENAME+"/FactoryNames";
    private final static String NODENAMEBOTNAMES = NODENAME+"/BotNames";
    private final static String NODENAMEFACTORYCOLS = NODENAME+"/FactoryCols";
    private final static String NODENAMEBOTCOLS = NODENAME+"/BotCols";

    private final static String COL_FACTORY = "Factory";
    private final static String COL_JID = "JID";
    private final static String COL_BOT = "Bot";
    private final static String COL_URI = "URI";

    private URI mRuleset;
    private XMPPConnection mConnection;
    private boolean mSuccess = false;
    private Map mFactories = new HashMap();
    private String mFactoryOfBotTable = null;

    private String mResultFactory = null;
    private String mResultBot = null;

    private UITableModel mFactoryModel;
    private UITableModel mBotModel;
    private JTable mFactoryTable;
    private JTable mBotTable;
    private int mFactoryFakeRow = -1;
    private int mBotFakeRow = -1;
    private TableColumnSaver mFactoryTableSaver;
    private TableColumnSaver mBotTableSaver;

    private String mLastFactoryChoice = null;
    private String mLastFactoryName = null;

    private JButton mCancelButton;
    private JButton mSelectButton;
    private JButton mFactoryJIDButton;
    private JButton mURIButton;

    /**
     * Construct a dialog.
     * @param ruleset the ruleset URI for which we want a bot.
     */
    public ChooseFactoryBotDialog(XMPPConnection connection, URI ruleset) {
        super(null, "Select a Bot", true, NODENAME);

        mConnection = connection;
        mRuleset = ruleset;

        // Construct the models.

        // Check some stored defaults.
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELASTFACTORY);
        mLastFactoryChoice = prefs.get(mRuleset.toString(), null);
        mLastFactoryName = null;
        int lastFactoryIndex = -1;
        if (mLastFactoryChoice != null) {
            prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMEFACTORYNAMES);
            mLastFactoryName = prefs.get(mLastFactoryChoice, "Bot factory");
        }

        Object[] row = new Object[2];

        mFactoryModel = new UITableModel();
        mFactoryModel.addColumn(COL_FACTORY);
        mFactoryModel.addColumn(COL_JID);

        if (mLastFactoryChoice != null) {
            row[0] = mLastFactoryName;
            row[1] = mLastFactoryChoice;
            mFactoryModel.insertRow(0, row);
            lastFactoryIndex = 0;
        }

        mBotModel = new UITableModel();
        mBotModel.addColumn(COL_BOT);
        mBotModel.addColumn(COL_URI);

        // Now that the models are done, build the UI.
        buildUI();

        adjustFactoryTable();

        mFactoryTableSaver = new TableColumnSaver(this, mFactoryTable,
            NODENAMEFACTORYCOLS);
        mBotTableSaver = new TableColumnSaver(this, mBotTable,
            NODENAMEBOTCOLS);

        setSize(550, 470);
        // Restore saved window position
        mSizePosSaver.restoreSizeAndPosition();
        restoreWindowState();

        if (lastFactoryIndex >= 0) {
            mFactoryTable.setRowSelectionInterval(lastFactoryIndex, lastFactoryIndex);
        }
        adjustForTableSelection();

        // Double-click to re-query the factory.
        mFactoryTable.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent ev) {
                    if (ev.getID() == MouseEvent.MOUSE_CLICKED
                        && ev.getClickCount() >= 2) 
                        doRequeryFactory();
                }
            });

        // Double-click to select a row.
        mBotTable.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent ev) {
                    if (ev.getID() == MouseEvent.MOUSE_CLICKED
                        && ev.getClickCount() >= 2)
                        doSelect();
                }
            });


        mFactoryTable.getSelectionModel().addListSelectionListener(
            new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent ev) {
                    adjustForTableSelection();
                }
            });
        mBotTable.getSelectionModel().addListSelectionListener(
            new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent ev) {
                    adjustForTableSelection();
                }
            });

        mCancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    mSuccess = false;
                    dispose();
                }
            });

        mSelectButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    doSelect();
                }
            });

        mURIButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    doEnterURI();
                }
            });

        mFactoryJIDButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    SelectFactory selector = new SelectFactory(mConnection);
                    selector.select(new SelectFactory.Callback() {
                            public void succeed(String jid, String name) {
                                addFactory(jid, name);
                            }
                            public void fail() { 
                                // do nothing
                            }
                        });
                }
            });

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    saveWindowState();
                    mFactories = null;
                }
            });

        /*
         * ### We should here query the bookkeeper for matching bots. But
         * that's not possible yet.
         */
    }

    /** Store the window state preferences. */
    private void saveWindowState() {
        mFactoryTableSaver.saveState();
        mBotTableSaver.saveState();
    }

    /** Load the window state preferences. */
    private void restoreWindowState() {
        mFactoryTableSaver.restoreState();
        mBotTableSaver.restoreState();
    }

    /**
     * Check whether the user selected a UI, or hit Cancel.
     */
    public boolean getSuccess() {
        return mSuccess;
    }

    /** Return the selected factory JID. */
    public String getFactoryJID() {
        return mResultFactory;
    }

    /** Return the selected bot URI. */
    public String getBotURI() {
        return mResultBot;
    }

    /**
     * Return the JID of the selected factory, or null.
     * If savename is true, store the factory's name in our prefs cache.
     */
    private String getSelectedFactory(boolean savename) {
        String selfactory = null;
        String selname = null;

        if (mFactoryFakeRow < 0) {
            int[] rows = mFactoryTable.getSelectedRows();
            if (rows.length == 1) {
                Object jido = mFactoryModel.getValueAt(rows[0], 1);
                if (jido != null && jido instanceof String) {
                    selfactory = (String)jido;
                    selname = (String)mFactoryModel.getValueAt(rows[0], 0);
                }
            }
        }

        if (savename && selfactory != null && selname != null) {
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMEFACTORYNAMES);
            prefs.put(selfactory, selname);
        }

        return selfactory;
    }

    /**
     * Return the URI of the selected bot, or null.
     * If selfactory is not null, store the bot's name in our prefs cache.
     */
    private String getSelectedURI(String selfactory) {
        String seluri = null;
        String selname = null;

        if (mBotFakeRow < 0) {
            int[] rows = mBotTable.getSelectedRows();
            if (rows.length == 1) {
                Object jido = mBotModel.getValueAt(rows[0], 1);
                if (jido != null && jido instanceof String) {
                    seluri = (String)jido;
                    selname = (String)mBotModel.getValueAt(rows[0], 0);
                }
            }
        }

        if (selfactory != null && seluri != null && selname != null) {
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMEBOTNAMES);
            prefs.put(selfactory+"#"+seluri, selname);
        }

        return seluri;
    }

    /**
     * Throw a new JID into the factory panel, if it isn't there already.
     * Select it.
     */
    private void addFactory(String jid, String name) {
        if (mFactoryModel.findEntry(1, jid) < 0) {
            // Add it
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMEFACTORYNAMES);
            if (name == null || name.equals(""))
                name = prefs.get(jid, "Bot factory");
            else
                prefs.put(jid, name);

            Object[] row = new Object[] { name, jid };
            mFactoryModel.addRow(row);
        
            adjustFactoryTable();
        }

        // Now select the JID we just added (or found)
        int sel = mFactoryModel.findEntry(1, jid);
        if (sel >= 0)
            mFactoryTable.setRowSelectionInterval(sel, sel);
    }

    /**
     * Update the factory table, after a model change. (Add or delete the "no
     * factories" row.)
     */
    private void adjustFactoryTable() {
        int total = mFactoryModel.getRowCount();
        if (mFactoryFakeRow >= 0)
            total -= 1;

        if (total == 0 && mFactoryFakeRow < 0) {
            Object[] row = new Object[] { "(no factories are published for this game)" };
            mFactoryModel.addRow(row);
            mFactoryFakeRow = 0;
        }

        if (total > 0 && mFactoryFakeRow >= 0) {
            mFactoryModel.removeRow(mFactoryFakeRow);
            mFactoryFakeRow = -1;
        }

        if (mFactoryFakeRow >= 0) {
            mFactoryTable.setRowSelectionAllowed(false);
            mFactoryTable.clearSelection();
        }
        else {
            mFactoryTable.setRowSelectionAllowed(true);
        }
    }

    /**
     * Enable or disable the buttons, after a factory selection change.
     */
    private void adjustForTableSelection() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        String selfactory = getSelectedFactory(false);
        String seluri = getSelectedURI(null);

        if (selfactory != null) {
            if (mFactories != null && !mFactories.containsKey(selfactory)) {
                Factory factory = new Factory(selfactory);
                mFactories.put(selfactory, factory);
                
                new DiscoBackground(mConnection,
                    new DiscoBackground.Callback() {
                        public void run(IQ result, XMPPException err, Object rock) {
                            acceptFactoryBotList(result, err, (String)rock);
                        }
                    },
                    DiscoBackground.QUERY_ITEMS, selfactory, "bots", selfactory);
            }
        }

        if (selfactory == null) {
            if (mFactoryOfBotTable != null) {
                mFactoryOfBotTable = null;
                adjustFactoryContents();
            }

            mURIButton.setEnabled(false);
            mSelectButton.setEnabled(false);
        }
        else {
            if (mFactoryOfBotTable == null 
                || !mFactoryOfBotTable.equals(selfactory)) {
                mFactoryOfBotTable = selfactory;
                adjustFactoryContents();
            }

            mURIButton.setEnabled(true);
            mSelectButton.setEnabled(seluri != null);
        }
    }

    /**
     * Send out another bot-list query. We do this by wiping the entry from the
     * mFactories table -- dumb but easy.
     */
    private void doRequeryFactory()
    {
        String selfactory = getSelectedFactory(false);

        if (selfactory != null) {
            if (mFactories != null && mFactories.containsKey(selfactory)) {
                mFactories.remove(selfactory);
                adjustFactoryContents();
                adjustForTableSelection();
            }
        }
    }

    /**
     * Rebuild the lower panel to reflect mFactoryOfBotTable.
     */
    private void adjustFactoryContents() {
        if (mFactoryOfBotTable == null) {
            mBotModel.setRowCount(0);
            Object[] row = new Object[] { "(Select a factory)" };
            mBotModel.addRow(row);
            mBotFakeRow = 0;
            mBotTable.setRowSelectionAllowed(false);
            mBotTable.clearSelection();
            return;
        }

        Factory factory = (Factory)mFactories.get(mFactoryOfBotTable);
        if (factory == null || factory.mStatus == Factory.QUERYING) {
            mBotModel.setRowCount(0);
            Object[] row = new Object[] { "(Querying...)" };
            mBotModel.addRow(row);
            mBotFakeRow = 0;
            mBotTable.setRowSelectionAllowed(false);
            mBotTable.clearSelection();
            return;
        }

        if (factory.mStatus == Factory.ERROR || factory.mBots.size() == 0) {
            mBotModel.setRowCount(0);
            Object[] row = new Object[] { "(Unable to query factory)" };
            mBotModel.addRow(row);
            mBotFakeRow = 0;
            mBotTable.setRowSelectionAllowed(false);
            mBotTable.clearSelection();
            return;
        }

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELASTBOT);
        String lastBotChoice = prefs.get(mFactoryOfBotTable, null);
        boolean foundLastBot = false;

        mBotModel.setRowCount(0);
        mBotTable.setRowSelectionAllowed(true);
        mBotFakeRow = -1;
        Object[] row = new Object[2];
        for (int ix=0; ix<factory.mBots.size(); ix++) {
            GameServer.AvailableBot bot = (GameServer.AvailableBot)factory.mBots.get(ix);
            row[0] = bot.name;
            row[1] = bot.uri;
            mBotModel.insertRow(0, row);

            if (lastBotChoice != null && lastBotChoice.equals(bot.uri))
                foundLastBot = true;
        }

        if (lastBotChoice != null && !foundLastBot) {
            prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMEBOTNAMES);
            String lastBotName = prefs.get(mFactoryOfBotTable+"#"+lastBotChoice, "Volity bot");
            row[0] = lastBotName;
            row[1] = lastBotChoice;
            mBotModel.insertRow(0, row);
        }
    }

    /**
     * Handle the results of a disco#items query to a factory.
     */
    private void acceptFactoryBotList(IQ result, XMPPException err, String jid) {
        if (mFactories == null) {
            // Window has been closed.
            return;
        }

        Factory factory = (Factory)mFactories.get(jid);
        if (factory == null)
            return;

        if (err != null) {
            // Disco query failed.
            XMPPException ex = err;
            new ErrorWrapper(ex);

            factory.mStatus = Factory.ERROR;
            if (mFactoryOfBotTable != null && mFactoryOfBotTable.equals(jid))
                adjustFactoryContents();

            String msg = "The factory could not be contacted.";

            // Any or all of these may be null.
            String submsg = ex.getMessage();
            XMPPError error = ex.getXMPPError();
            Throwable subex = ex.getWrappedThrowable();

            if (error != null 
                && (error.getCode() == 404 || error.getCode() == 400)) {
                /* A common case: the JID was not found. */
                msg = "No factory exists at this address.";
                if (error.getMessage() != null)
                    msg = msg + " (" + error.getMessage() + ")";
                msg = msg + "\n(" + jid + ")";
            }
            else {
                msg = "The factory could not be contacted";
                if (submsg != null && subex == null && error == null)
                    msg = msg + ": " + submsg;
                else
                    msg = msg + ".";
                if (subex != null)
                    msg = msg + "\n" + subex.toString();
                if (error != null)
                    msg = msg + "\nJabber error " + error.toString();
            }

            JOptionPane.showMessageDialog(null, 
                msg,
                JavolinApp.getAppName() + ": Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        DiscoverItems items = (DiscoverItems)result;
        List bots = new ArrayList();

        for (Iterator it = items.getItems(); it.hasNext(); ) {
            DiscoverItems.Item el = (DiscoverItems.Item)it.next();
            if (el.getNode() != null) {
                GameServer.AvailableBot bot = new GameServer.AvailableBot(el.getEntityID(), el.getNode(), el.getName());
                bots.add(bot);
            }
        }

        factory.mStatus = Factory.GOT;
        factory.mBots = bots;
        if (mFactoryOfBotTable != null && mFactoryOfBotTable.equals(jid))
            adjustFactoryContents();
    }

    private void doEnterURI() {
        String selfactory = getSelectedFactory(true);

        if (selfactory == null)
            return;

        URIChooser chooser = new URIChooser(null);
        chooser.setVisible(true);
        URI uri = chooser.getResult();
        if (uri == null)
            return;

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELASTFACTORY);
        prefs.put(mRuleset.toString(), selfactory);
        prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELASTBOT);
        prefs.put(selfactory, uri.toString());

        mResultFactory = selfactory;
        mResultBot = uri.toString();
        mSuccess = true;
        dispose();
    }

    private void doSelect() {
        String selfactory = getSelectedFactory(true);
        String seluri = getSelectedURI(selfactory);

        if (selfactory == null || seluri == null) 
            return;

        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELASTFACTORY);
        prefs.put(mRuleset.toString(), selfactory);
        prefs = Preferences.userNodeForPackage(getClass()).node(NODENAMELASTBOT);
        prefs.put(selfactory, seluri);

        mResultFactory = selfactory;
        mResultBot = seluri;
        mSuccess = true;
        dispose();
    }

    /** 
     * Customization of the default TableModel. This model is entirely
     * non-editable. It also provides a search facility.
     */
    private static class UITableModel extends DefaultTableModel {
        public boolean isCellEditable(int row, int column) {
            return false;
        }
        public int findEntry(int column, String val) {
            int count = getRowCount();
            for (int ix=0; ix<count; ix++) {
                Object cell = getValueAt(ix, column);
                if (cell != null && val.equals(cell)) {
                    return ix;
                }
            }
            return -1;
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

        mFactoryTable = new JTable(mFactoryModel);
        mFactoryTable.setDefaultRenderer(Object.class, new UITableCellRenderer());
        mFactoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(mFactoryTable);
        scroll.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        cPane.add(scroll, c);
        
        mFactoryJIDButton = new JButton("Enter Factory...");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
        c.anchor = GridBagConstraints.WEST;
        cPane.add(mFactoryJIDButton, c);

        mBotTable = new JTable(mBotModel);
        mBotTable.setDefaultRenderer(Object.class, new UITableCellRenderer());
        mBotTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scroll = new JScrollPane(mBotTable);
        scroll.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(GAP, 0, 0, 0);
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

        mURIButton = new JButton("Enter URI...");
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        buttonPanel.add(mURIButton, c);

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
    }

    /** Data class, stored in mFactories. */
    protected static class Factory {
        static final int QUERYING = 1;
        static final int ERROR = 2;
        static final int GOT = 3;

        String mJID;
        int mStatus;
        List mBots;

        public Factory(String jid) {
            mJID = jid;
            mStatus = QUERYING;
            mBots = null;
        }
    }

    /**
     * Helper class to let the user enter a factory JID. This checks that it's
     * a working JID, and a bot factory.
     */
    protected static class SelectFactory {
        public interface Callback {
            public void succeed(String jid, String name);
            public void fail();
        }

        protected XMPPConnection mConnection;
        protected Callback mCallback;
        protected String mResult = null;
        protected String mResultName = null;

        public SelectFactory(XMPPConnection connection) {
            mConnection = connection;
        }

        public void select(Callback callback) {
            mCallback = callback;

            // Queue up the first function.
            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        contFromTop();
                    }
                });
        }

        private void contFromTop() {
            JIDChooser box = new JIDChooser.Factory(null);
            box.setVisible(true);

            String jid = box.getResult();
            if (jid == null) {
                callbackFail();
                return;
            }

            if (!JIDUtils.hasResource(jid)) {
                jid = JIDUtils.setResource(jid, "volity");
            }

            mResult = jid;

            new DiscoBackground(mConnection,
                new DiscoBackground.Callback() {
                    public void run(IQ result, XMPPException err, Object rock) {
                        contDidDisco(result, err);
                    }
                },
                DiscoBackground.QUERY_INFO, mResult, null);
        }

        private void contDidDisco(IQ result, XMPPException err) {
            assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
            if (err != null) {
                // Disco query failed.
                XMPPException ex = err;
                new ErrorWrapper(ex);
                callbackFail();

                String msg = "The factory could not be contacted.";

                // Any or all of these may be null.
                String submsg = ex.getMessage();
                XMPPError error = ex.getXMPPError();
                Throwable subex = ex.getWrappedThrowable();

                if (error != null 
                    && (error.getCode() == 404 || error.getCode() == 400)) {
                    /* A common case: the JID was not found. */
                    msg = "No factory exists at this address.";
                    if (error.getMessage() != null)
                        msg = msg + " (" + error.getMessage() + ")";
                    msg = msg + "\n(" + mResult + ")";
                }
                else {
                    msg = "The factory could not be contacted";
                    if (submsg != null && subex == null && error == null)
                        msg = msg + ": " + submsg;
                    else
                        msg = msg + ".";
                    if (subex != null)
                        msg = msg + "\n" + subex.toString();
                    if (error != null)
                        msg = msg + "\nJabber error " + error.toString();
                }

                JOptionPane.showMessageDialog(null, 
                    msg,
                    JavolinApp.getAppName() + ": Error", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            assert (result != null && result instanceof DiscoverInfo);

            String jidrole = null;

            DiscoverInfo info = (DiscoverInfo)result;
            Form form = Form.getFormFrom(info);
            if (form != null) {
                FormField field = form.getField("volity-role");
                if (field != null)
                    jidrole = (String) field.getValues().next();
            }

            if (jidrole == null || !jidrole.equals("factory")) {
                callbackFail();
                String msg = "This is not a Volity bot factory.";
                if (jidrole != null)
                    msg = "This is a Volity " + jidrole + ", not a bot factory.";
                msg = msg + "\n(" + mResult + ")";
                JOptionPane.showMessageDialog(null, 
                    msg,
                    JavolinApp.getAppName() + ": Error", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            DiscoverInfo.Identity ident = (DiscoverInfo.Identity)info.getIdentities().next();
            if (ident != null)
                mResultName = ident.getName();

            callbackSucceed();
        }

        private void callbackFail() {
            assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
            
            if (mCallback != null)
                mCallback.fail();
        }

        private void callbackSucceed() {
            assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
            assert (mResult != null);
            
            if (mCallback != null)
                mCallback.succeed(mResult, mResultName);
        }
        
    }
}
