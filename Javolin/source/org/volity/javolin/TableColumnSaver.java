package org.volity.javolin;

import java.awt.*;
import java.util.prefs.Preferences;
import javax.swing.JTable;
import javax.swing.table.TableColumn;

/**
 * Handles saving and restoring the size and ordering of columns in a JTable.
 */

public class TableColumnSaver
{
    private final static String COLORDER_KEY = "ColOrder";
    private final static String COLWIDTH_KEY = "ColWidth";

    private Window mWindow;
    private JTable mTable;
    private String mNodeName;

    /** 
     * Constructor.
     * @param window The window that owns the table.
     * @param table The table to manage the columns of.
     * @param nodename A string to uniquely identify the table (or the window,
     * if there is only one table in it)
     */
    public TableColumnSaver(Window window, JTable table, String nodename) {
        mWindow = window;
        mTable = table;
        mNodeName = nodename;
    }

    /** Write out the column order and widths. */
    public void saveState() {
        Preferences prefs =
            Preferences.userNodeForPackage(mWindow.getClass()).node(mNodeName);
        
        for (int ix=0; ix<mTable.getColumnCount(); ix++) {
            TableColumn col = mTable.getColumnModel().getColumn(ix);
            String colname = (String)col.getIdentifier();
            prefs.putInt(COLWIDTH_KEY+colname, col.getWidth());
        }

        String colpref = "";
        for (int ix=0; ix<mTable.getColumnCount(); ix++) {
            TableColumn col = mTable.getColumnModel().getColumn(ix);
            String colname = (String)col.getIdentifier();
            colpref += colname + ",";
        }

        prefs.put(COLORDER_KEY, colpref);
    }

    /** Read in the column order and widths. */
    public void restoreState() {
        Preferences prefs =
            Preferences.userNodeForPackage(mWindow.getClass()).node(mNodeName);
        
        for (int ix=0; ix<mTable.getColumnCount(); ix++) {
            TableColumn col = mTable.getColumnModel().getColumn(ix);
            String colname = (String)col.getIdentifier();
            int val = prefs.getInt(COLWIDTH_KEY+colname,
                col.getPreferredWidth());
            col.setPreferredWidth(val);
        }

        String colpref = prefs.get(COLORDER_KEY, null);
        if (colpref != null) {
            String[] cols = colpref.split(",");
            for (int ix=0; ix<mTable.getColumnCount(); ) {
                TableColumn col = mTable.getColumnModel().getColumn(ix);
                String colname = (String)col.getIdentifier();

                int destix = -1;
                for (int jx=0; jx<cols.length; jx++) {
                    if (cols[jx].equals(colname)) {
                        destix = jx;
                        break;
                    }
                }

                if (destix >= 0 && destix > ix) {
                    mTable.moveColumn(ix, destix);
                }
                else {
                    ix++;
                }
            }
        }
    }
}
