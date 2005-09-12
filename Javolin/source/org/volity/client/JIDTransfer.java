package org.volity.client;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.Serializable;

/**
 * This class wraps a JID string for drag-and-drop. The DataFlavor
 * JIDTransfer.JIDFlavor is its MIME type. (It is not drag-and-drop compatible
 * with any other flavor, not even other strings. I'm keeping things simple for
 * now.)
 */
public class JIDTransfer implements Transferable, Serializable {
    static public DataFlavor JIDFlavor = null;
    static public DataFlavor[] JIDFlavorArray = null;
    static {
        JIDFlavor = new DataFlavor(JIDTransfer.class, "JID");
        JIDFlavorArray = new DataFlavor[1];
        JIDFlavorArray[0] = JIDFlavor;
    }

    protected String mJID;

    /**
     * Argumentless constructor -- for serializability. 
     */
    protected JIDTransfer() { }

    /**
     * Construct a JIDTransfer for the given JID.
     */ 
    public JIDTransfer(String jid) {
        mJID = jid;
    }

    public String getJID() {
        return mJID;
    }

    /* Methods which implement Transferable: */

    public Object getTransferData(DataFlavor flavor) {
        return this;
    }
    public DataFlavor[] getTransferDataFlavors() {
        return JIDFlavorArray;
    }
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(JIDFlavor);
    }
}

