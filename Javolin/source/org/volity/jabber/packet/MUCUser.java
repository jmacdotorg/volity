package org.volity.jabber.packet;

import java.util.*;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;

/**
 * MUC user info packet extension, implementing the muc#user protocol
 * from JEP-0045.
 */
public class MUCUser implements PacketExtension {
  public MUCUser() { }

  public static final String
    elementName = "x",
    namespace = "http://jabber.org/protocol/muc#user";

  // Inherited from PacketExtension.
  public String getElementName() { return elementName; }
  public String getNamespace() { return namespace; }
  public String toXML() {
    String xml = "<" + elementName + " xmlns='" + namespace + "'>";
    xml += "<item" +
      " role='" + role + "'" +
      " affiliation='" + affiliation + "'" +
      (jid == null ? "" : " jid='" + jid + "'") +
      (nickname == null ? "" : " nick='" + nickname + "'") +
      " />";
    xml += (status == null ? "" : "<status code='" + status.code + "' />");
    return xml + "</" + elementName + ">";
  }

  public static class Role {
    public static final Role
      NONE = new Role("none"),
      VISITOR = new Role("visitor"),
      PARTICIPANT = new Role("participant"),
      MODERATOR = new Role("moderator");
    static List values =
      Arrays.asList(new Role[] {
		      NONE,
		      VISITOR,
		      PARTICIPANT,
		      MODERATOR
		    });

    String value;
    Role(String value) { this.value = value; }
    public String toString() { return value; }
    public boolean equals(Object x) {
      return value.equals(((Role) x).value);
    }

    public static Role fromString(String value) {
      if (value == null) return NONE;
      value = value.toLowerCase();
      int i = values.indexOf(new Role(value));
      if (i == -1) throw new IllegalArgumentException(value);
      return (Role) values.get(i);
    }
  }

  Role role;
  public Role getRole() { return role; }
  public void setRole(Role role) { this.role = role; }
  public void setRole(String role) { this.role = Role.fromString(role); }

  public static class Affiliation {
    public static final Affiliation
      NONE = new Affiliation("none"),
      MEMBER = new Affiliation("member"),
      ADMIN = new Affiliation("admin"),
      OWNER = new Affiliation("owner"),
      OUTCAST = new Affiliation("outcast");
    static List values =
      Arrays.asList(new Affiliation[] {
		      NONE,
		      MEMBER,
		      ADMIN,
		      OWNER,
		      OUTCAST
		    });

    String value;
    Affiliation(String value) { this.value = value; }
    public String toString() { return value; }
    public boolean equals(Object x) {
      return value.equals(((Affiliation) x).value);
    }

    public static Affiliation fromString(String value) {
      if (value == null) return NONE;
      value = value.toLowerCase();
      int i = values.indexOf(new Affiliation(value));
      if (i == -1) throw new IllegalArgumentException(value);
      return (Affiliation) values.get(i);
    }
  }

  Affiliation affiliation;
  public Affiliation getAffiliation() {
    return affiliation;
  }
  public void setAffiliation(Affiliation affiliation) {
    this.affiliation = affiliation;
  }
  public void setAffiliation(String affiliation) {
    this.affiliation = Affiliation.fromString(affiliation);
  }
    
  String jid;
  public String getJID() { return jid; }
  public void setJID(String jid) { this.jid = jid; }

  String nickname;
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }

  public static class Status {
    int code;
    public Status(int code) { this.code = code; }
    public int getCode() { return code; }
    public String toString() { return String.valueOf(code); }
    public boolean equals(Object x) { return code == ((Status) x).code; }
  }

  Status status;
  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }
  public void setStatus(int code) { this.status = new Status(code); }

  /**
   * Get the user info packet extension from a presence packet, or
   * null if it has no extension.
   */
  public static MUCUser getUserInfo(Presence presence) {
    PacketExtension ext = presence.getExtension(elementName, namespace);
    return ext == null ? null : (MUCUser) ext;
  }
}
