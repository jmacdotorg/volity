package org.volity.client.comm;

import java.io.IOException;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** 
 * The provider which parses the extended info for JEP-0115.
 */
public class CapExtensionProvider
    implements PacketExtensionProvider
{
    public PacketExtension parseExtension(XmlPullParser xpp)
        throws XmlPullParserException, IOException {

        if (xpp.getEventType() != xpp.START_TAG
            || !xpp.getName().equals(CapPacketExtension.NAME))
            throw new IOException("capability tag does not start with <c>");

        String nodeattr = xpp.getAttributeValue(null, "node");
        String verattr = xpp.getAttributeValue(null, "ver");
        String extattr = xpp.getAttributeValue(null, "ext");

        xpp.nextTag();
        xpp.require(xpp.END_TAG, null, CapPacketExtension.NAME);

        return new CapPacketExtension(nodeattr, verattr, extattr);
    }
}
