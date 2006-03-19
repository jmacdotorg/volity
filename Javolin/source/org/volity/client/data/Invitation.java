package org.volity.client.data;

import java.util.Map;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * An invitation to join a game table.
 */
public class Invitation {
    /** Create an invitation from a struct recieved via RPC. */
    public Invitation(Map invitationStruct) {
        fields = invitationStruct;
    }

    Map fields;

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

    // Inherited from Object
    public String toString() {
        return fields.toString();
    }
}
