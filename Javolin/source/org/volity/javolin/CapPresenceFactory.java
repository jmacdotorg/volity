package org.volity.javolin;

import org.jivesoftware.smack.packet.DefaultPresenceFactory;
import org.jivesoftware.smack.packet.DefaultPacketExtension;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;

/**
 * A PresenceFactory which generates presence stanzas that include (fixed)
 * JEP-0115 tags.
 */
public class CapPresenceFactory extends DefaultPresenceFactory
{
    public Presence create(Presence.Type type, String status, int priority, Presence.Mode mode) {
        return new CapPresence(type, status, priority, mode);
    }

    /**
     * A Presence stanza which includes a JEP-0115 capabilities tag.
     */
    public class CapPresence extends Presence
    {
        public CapPresence(Type type) {
            super(type);
            if (type != Type.UNAVAILABLE)
                addExtension(new CapPacketExtension());
        }

        public CapPresence(Type type, String status, int priority, Mode mode) {
            super(type, status, priority, mode);
            if (type != Type.UNAVAILABLE)
                addExtension(new CapPacketExtension());
        }

    }

    /** 
     * The extended info for JEP-0115.
     */
    public class CapPacketExtension extends DefaultPacketExtension {
        static final String VOLITY_NODE_URI = "http://volity.org/protocol/caps";
        static final String VOLITY_EXT = "player";
        static final String VOLITY_VERSION = "1.0";

        public CapPacketExtension() {
            super("c", "http://jabber.org/protocol/caps");
        }

        public String toXML() {
            StringBuffer buf = new StringBuffer();
            buf.append("<").append(getElementName());
            buf.append(" xmlns=\"").append(getNamespace()).append("\"");
            buf.append(" node=\"").append(VOLITY_NODE_URI).append("\"");
            buf.append(" ext=\"").append(VOLITY_EXT).append("\"");
            buf.append(" ver=\"").append(VOLITY_VERSION).append("\"");
            buf.append(" />");
            return buf.toString();
        }
    }
}
