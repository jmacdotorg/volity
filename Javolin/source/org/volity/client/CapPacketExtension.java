package org.volity.client;

import org.jivesoftware.smack.packet.DefaultPacketExtension;

/** 
 * The extended info for JEP-0115.
 */
public class CapPacketExtension extends DefaultPacketExtension
{
    public static final String NAME = "c";
    public static final String NAMESPACE = "http://jabber.org/protocol/caps";

    protected String mNode;
    protected String mVer;
    protected String[] mExt;

    /**
     * Create a CapPacketExtension with the given node, ver, and list of
     * extensions. The ext list may be null.
     */
    public CapPacketExtension(String node, String ver, String[] ext) {
        super(NAME, NAMESPACE);

        if (ext == null)
            ext = new String[0];

        mNode = node;
        mVer = ver;
        mExt = ext;
    }

    /**
     * Create a CapPacketExtension with the given node and ver, and no
     * extensions.
     */
    public CapPacketExtension(String node, String ver) {
        super(NAME, NAMESPACE);

        mNode = node;
        mVer = ver;
        mExt = new String[0];
    }

    /**
     * Create a CapPacketExtension with the given node, ver, and
     * space-delimited list of extensions. The exts string may not be null.
     */
    public CapPacketExtension(String node, String ver, String exts) {
        super(NAME, NAMESPACE);

        mNode = node;
        mVer = ver;
        mExt = exts.split(" ");
    }

    /** Return the node attribute. */
    public String getNode() {
        return mNode;
    }

    /** Return the ver attribute. */
    public String getVer() {
        return mVer;
    }

    /**
     * Return the extensions attribute, in the form of an array. If there are
     * no extensions, this returns an empty array.
     */
    public String[] getExt() {
        return mExt;
    }

    /**
     * Return the extensions attribute, in the form of a space-delimited list.
     * If there are no extensions, this returns null.
     */
    public String getExtAsString() {
        if (mExt == null || mExt.length == 0) {
            return null;
        }

        StringBuffer buf = new StringBuffer();

        for (int ix=0; ix<mExt.length; ix++) {
            // a space-separated list
            if (ix > 0)
                buf.append(" ");
            buf.append((String)mExt[ix]);
        }

        return buf.toString();
    }

    /**
     * Returns whether the extensions attribute contains the given string. (The
     * string may not contain a space.)
     */
    public boolean hasExtString(String val) {
        if (mExt == null || mExt.length == 0) {
            return false;
        }

        for (int ix=0; ix<mExt.length; ix++) {
            String ext = (String)mExt[ix];
            if (ext.equals(val))
                return true;
        }

        return false;
    }

    public String toXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<").append(getElementName());
        buf.append(" xmlns=\"").append(getNamespace()).append("\"");
        buf.append(" node=\"").append(mNode).append("\"");
        buf.append(" ver=\"").append(mVer).append("\"");
        if (mExt != null && mExt.length > 0) {
            buf.append(" ext=\"");
            for (int ix=0; ix<mExt.length; ix++) {
                // a space-separated list
                if (ix > 0)
                    buf.append(" ");
                buf.append((String)mExt[ix]);
            }
            buf.append("\"");
        }
        buf.append(" />");
        return buf.toString();
    }
}
