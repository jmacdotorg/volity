package org.volity.client.data;

import java.io.IOException;
import java.net.*;
import java.util.*;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;

/**
 * Some information about a game user interface document.  Typically
 * this will be retrieved from a Bookkeeper.
 */
public class GameUIInfo {
  /**
   * @param name the name of this game UI
   * @param location the location from where this game UI can be downloaded
   * @param form a data form containing info about this game UI
   */
  public GameUIInfo(String name, URL location, Form form) {
    this.name = name;
    this.location = location;
    this.form = form;
  }

  String name;
  URL location;
  Form form;

  /**
   * Get the name of this game UI.
   */
  public String getName() { return name; }

  /**
   * Get the location from where this game UI can be downloaded.
   */
  public URL getLocation() { return location; }

  /**
   * Get the client types supported by this game UI.
   * @return a list of URIs
   */
  public List getClientTypes() {
    List types = new ArrayList();
    FormField field = form.getField("client-type");
    if (field == null)
      return types;
    for (Iterator it = field.getValues(); it.hasNext();)
      try {
        types.add(new URI((String) it.next()));
      } catch (URISyntaxException e) { }
    return types;
  }

  /**
   * Get the languages supported by this game UI.
   * @return a list of strings, two-letter ISO-639 language codes
   */
  public List getLanguages() {
    List langs = new ArrayList();
    FormField field = form.getField("languages");
    if (field == null)
      return langs;
    for (Iterator it = field.getValues(); it.hasNext();)
      langs.add(it.next());
    return langs;
  }

  /**
   * Get the ruleset supported by this game UI.
   */
  public URI getRuleset() {
    return URI.create(getValue("ruleset"));
  }

  /**
   * Get the reputation ranking of this game UI.
   */
  public int getReputation() {
    String val = getValue("reputation");
    if (val == null)
      return 0;
    return Integer.parseInt(val);
  }

  /**
   * Get the email address of the person responsible for this game UI.
   */
  public String getContactEmail() {
    return getValue("contact-email");
  }

  /**
   * Get the JID of the person responsible for this game UI.
   */
  public String getContactJID() {
    return getValue("contact-jid");
  }

  /**
   * Get the description of this game UI.
   */
  public String getDescription() {
    return getValue("description");
  }

  /**
   * Get the media type of the content of this game UI.  This will
   * typically be "image/svg+xml" or "application/zip".
   * @return a media type string in RFC 2045 format, or null if unavailable
   */
  public String getContentType() {
    try {
      return getLocation().openConnection().getContentType();
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Get the value of a text-single field.
   * @param name a field variable name
   */
  protected String getValue(String name) {
    FormField field = form.getField(name);
    if (field == null)
      return null;
    return field.getValues().next().toString();
  }

  /**
   * A human-readable representation of the information for this game UI.
   */
  public String toString() {
    String s = "name: " + name + "\n" + "location: " + location + "\n";
    for (Iterator it = form.getFields(); it.hasNext();) {
      FormField field = (FormField) it.next();
      s += field.getVariable() + ": ";
      boolean first = true;
      for (Iterator it2 = field.getValues(); it2.hasNext();) {
        if (first) first = false; else s += ", ";
        s += it2.next();
      }
      s += "\n";
    }
    s += "content-type: " + getContentType();
    return s;
  }
}
