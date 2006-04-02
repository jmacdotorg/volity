package org.volity.client.comm;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.util.StringUtils;

/**
 * When you register a new account, you should send a VCard containing your
 * name. The Smack VCard object is, however, annoying and sad -- it wants first
 * names and last names and so on. We have simple needs which do not fit its
 * way of life.
 */
public class VCard extends IQ {
    String mName;
    String mEmail;

    public VCard(XMPPConnection connection, String name, String email) {
        mName = name;
        mEmail = email;

        setFrom(connection.getUser());
        setType(Type.SET);
    }

    public String getChildElementXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<vCard xmlns=\"vcard-temp\">");
        if (mName != null) {
            buf.append("<FN>");
            buf.append(StringUtils.escapeForXML(mName));
            buf.append("</FN>");
        }
        if (mEmail != null) {
            buf.append("<EMAIL><INTERNET/><PREF/><USERID>");
            buf.append(StringUtils.escapeForXML(mEmail));
            buf.append("</USERID></EMAIL>");
        }
        buf.append("</vCard>");
        return buf.toString();
    }
}
