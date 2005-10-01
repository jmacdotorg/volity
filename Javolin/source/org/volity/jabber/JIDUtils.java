package org.volity.jabber;

import org.jivesoftware.smack.util.StringUtils;


/**
 * Utility functions that I wish were in StringUtils but aren't.
 */
public class JIDUtils
{
    private JIDUtils() {
        // Not instantiable.
    }

    /**
     * Returns whether jid has a resource portion.
     */
    public static boolean hasResource(String jid) {
        return !StringUtils.parseResource(jid).equals("");
    }

    /** 
     * Return a JID comprised of the bare address of jid, with the given
     * resource appended. If resource is null or empty, this just returns the
     * bare address.
     */
    public static String setResource(String jid, String resource) {
        if (jid == null)
            return null;

        jid = StringUtils.parseBareAddress(jid);
        if (resource == null || resource.equals(""))
            return jid;
        return jid + "/" + resource;
    }
}
