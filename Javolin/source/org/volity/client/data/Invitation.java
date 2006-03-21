package org.volity.client.data;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.volity.client.comm.FormPacketExtension;

/**
 * An invitation to join a game table.
 */
public class Invitation {
    Map fields;
    Date timestamp;

    /** Create an invitation from a struct received via RPC. */
    public Invitation(Map invitationStruct) {
        fields = invitationStruct;
        timestamp = new Date();
    }

    /**
     * Create an invitation from a data form.
     *
     * The dateext argument may be null or the result of
     * message.getExtension("x", "jabber:x:delay"). If present, it will be
     * parsed for a timestamp.
     */
    public Invitation(FormPacketExtension form, PacketExtension dateext) {
        fields = new HashMap();

        for (Iterator it = form.getFields(); it.hasNext(); ) {
            FormField field = (FormField)it.next();
            String key = field.getVariable();
            String val = (String) field.getValues().next();
            if (key != null && val != null)
                fields.put(key, val);
        }

        timestamp = null;
        if (dateext != null && dateext instanceof DelayInformation) {
            timestamp = ((DelayInformation)dateext).getStamp();
        }

        if (timestamp == null)
            timestamp = new Date();
    }

    /** The JID of the inviting player. */
    public String getPlayerJID() {
        return (String) fields.get("player");
    }

    /** The JID of the game table to be joined. */
    public String getTableJID() {
        return (String) fields.get("table");
    }

    /** The JID of the referee of the game table. */
    public String getRefereeJID() {
        return (String) fields.get("referee");
    }

    /** The game's name. */
    public String getGameName() {
        return (String) fields.get("name");
    }

    /**
     * The JID of the game parlor that created the game table, or null
     * if it was not provided in the invitation.
     */
    public String getParlorJID() {
        return (String) fields.get("parlor");
    }

    /**
     * The URI of the ruleset used at the game table, or null if it was
     * not provided in the invitation (or was malformed).
     */
    public URI getRuleset() {
        try {
            return new URI((String) fields.get("ruleset"));
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * The text message accompanying the invitation, or null if it was
     * not provided.
     */
    public String getMessage() {
        return (String) fields.get("message");
    }

    /**
     * The time that the message was received.
     */
    public Date getTimestamp() {
        return timestamp;
    }

    // Inherited from Object
    public String toString() {
        return fields.toString();
    }
}
