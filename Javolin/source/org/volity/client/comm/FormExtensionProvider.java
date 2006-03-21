package org.volity.client.comm;

import java.io.IOException;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smackx.packet.DataForm;
import org.jivesoftware.smackx.provider.DataFormProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * The provider which parses <volity> message attachments.
 *
 * This is simple, because most of the content is a standard data form, so we
 * use the standard FormExtensionProvider.
 */
public class FormExtensionProvider
    implements PacketExtensionProvider
{
    DataFormProvider mFormProvider;

    /** Constructor. */
    public FormExtensionProvider() {
        mFormProvider = new DataFormProvider();
    }

    /** Implements PacketExtensionProvider interface. */
    public PacketExtension parseExtension(XmlPullParser xpp)
        throws Exception {

        if (xpp.getEventType() != xpp.START_TAG
            || !xpp.getName().equals(FormPacketExtension.NAME))
            throw new IOException("volity tag does not start with <volity>");

        DataForm form = (DataForm)mFormProvider.parseExtension(xpp);

        xpp.nextTag();
        xpp.require(xpp.END_TAG, null, FormPacketExtension.NAME);
        
        return new FormPacketExtension(form);
    }
}
