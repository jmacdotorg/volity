package org.volity.javolin;

import org.jivesoftware.smack.packet.DefaultPresenceFactory;
import org.jivesoftware.smack.packet.DefaultPacketExtension;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.volity.client.comm.CapPacketExtension;

/**
 * A PresenceFactory which generates presence stanzas that include
 * (Volity-specific) JEP-0115 tags.
 */
public class CapPresenceFactory extends DefaultPresenceFactory
{
    public static final String VOLITY_NODE_URI = "http://volity.org/protocol/caps";
    public static final String VOLITY_VERSION = "1.0";

    public static final String VOLITY_ROLE_NONE    = "";
    public static final String VOLITY_ROLE_BOOKKEEPER = "bookkeeper";
    public static final String VOLITY_ROLE_PARLOR  = "parlor";
    public static final String VOLITY_ROLE_REFEREE = "referee";
    public static final String VOLITY_ROLE_PLAYER  = "player";
    public static final String VOLITY_ROLE_BOT     = "bot";

    /*
     * This array must be in order of precedence, from highest to lowest. That
     * is: if a JID has two connections with two of these roles, count it as
     * whichever is listed first.
     */
    public static final String[] VOLITY_ROLES = {
        VOLITY_ROLE_BOOKKEEPER, VOLITY_ROLE_PARLOR, VOLITY_ROLE_REFEREE,
        VOLITY_ROLE_PLAYER, VOLITY_ROLE_BOT
    };

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
                addExtension(new CapPacketExtension(VOLITY_NODE_URI,
                                 VOLITY_VERSION, VOLITY_ROLE_PLAYER));
        }

        public CapPresence(Type type, String status, int priority, Mode mode) {
            super(type, status, priority, mode);
            if (type != Type.UNAVAILABLE)
                addExtension(new CapPacketExtension(VOLITY_NODE_URI,
                                 VOLITY_VERSION, VOLITY_ROLE_PLAYER));
        }

    }
}
