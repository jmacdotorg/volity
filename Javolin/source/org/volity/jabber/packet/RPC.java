package org.volity.jabber.packet;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.util.StringUtils;
import java.text.SimpleDateFormat;
import java.util.*;

/** A remote procedure call packet conforming to JEP-0009 (Jabber-RPC). */
public abstract class RPC extends IQ {
  // Inherited from IQ.
  public String getChildElementXML() {
    return "<query xmlns='jabber:iq:rpc'>" + getPayloadXML() + "</query>";
  }

  /** XML string representing the RPC payload (method call or response). */
  public abstract String getPayloadXML();

  /** Serialize a list of parameters to XML. */
  public static String getParamsXML(List params) {
    if (params == null || params.isEmpty()) return "";
    String xml = "<params>";
    for (Iterator it = params.iterator(); it.hasNext();)
      xml += "<param>" + getValueXML(it.next()) + "</param>";
    return xml + "</params>";
  }

  /** Serialize a Java value to XML. */
  public static String getValueXML(Object v) {
    String xml = "<value>";
    if (v instanceof Double || v instanceof Float)
      xml += "<double>" + v + "</double>";
    else if (v instanceof Number)
      xml += "<int>" + v + "</int>";
    else if (v instanceof Boolean)
      xml += "<boolean>" + (((Boolean) v).booleanValue() ? "1" : "0") +
	"</boolean>";
    else if (v instanceof String)
      xml += "<string>" + StringUtils.escapeForXML((String) v) + "</string>";
    else if (v instanceof Date)
      xml += "<dateTime.iso8601>" + 
	date.format((Date) v) + "</dateTime.iso8601>";
    else if (v instanceof byte[])
      xml += "<base64>" + StringUtils.encodeBase64((byte[]) v) + "</base64>";
    else if (v instanceof Map) {
      xml += "<struct>";
      for (Iterator it = ((Map) v).entrySet().iterator(); it.hasNext();) {
	Map.Entry entry = (Map.Entry) it.next();
	xml += "<member>";
	xml += "<name>" + StringUtils.escapeForXML(entry.getKey().toString()) +
	  "</name>";
	xml += getValueXML(entry.getValue());
	xml += "</member>";
      }
      xml += "</struct>";
    } else if (v instanceof Collection) {
      xml += "<array>";
      for (Iterator it = ((Collection) v).iterator(); it.hasNext();)
	xml += "<data>" + getValueXML(it.next()) + "</data>";
      xml += "</array>";
    } else
      throw new RuntimeException("Don't know how to serialize " + v.getClass() +
				 "to Jabber-RPC.");
    return xml + "</value>";
  }

  /** ISO 8601 date format. */
  public static SimpleDateFormat date =
    new SimpleDateFormat("yyyyMMdd'T'HH:mm:ss");
}
