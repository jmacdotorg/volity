package org.volity.jabber.provider;

import java.io.IOException;
import java.util.*;
import java.text.ParsePosition;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.volity.jabber.packet.*;

/** An IQ provider for parsing Jabber-RPC packets as defined by JEP-0009. */
public class RPCProvider implements IQProvider {
  // Inherited from IQProvider
  public IQ parseIQ(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    RPC packet;
    parser.nextTag();
    String elementName = parser.getName();
    if (elementName.equals("methodCall")) {
      packet = parseRequest(parser);
    } else if (elementName.equals("methodResponse")) {
      packet = parseResponse(parser);
    } else throw error(parser, "Unknown RPC payload.");
    parser.require(parser.END_TAG, null, elementName);
    return packet;
  }

  /** Parse a RPC request. */
  public RPCRequest parseRequest(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    parser.nextTag();
    parser.require(parser.START_TAG, null, "methodName");
    String methodName = parser.nextText();
    parser.require(parser.END_TAG, null, "methodName");

    List params = null;
    if (parser.nextTag() == parser.START_TAG) {
      parser.require(parser.START_TAG, null, "params");
      params = parseParams(parser);
      parser.require(parser.END_TAG, null, "params");
      parser.nextTag();
    }

    return new RPCRequest(methodName, params);
  }

  /** Parse a RPC response. */
  public RPCResponse parseResponse(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    RPCResponse response;
    parser.nextTag();
    String elementName = parser.getName();
    if (elementName.equals("params")) {
      response = parseResult(parser);
    } else if (elementName.equals("fault")) {
      response = parseFault(parser);
    } else throw error(parser, "Unknown RPC response.");
    parser.require(parser.END_TAG, null, elementName);

    parser.nextTag();
    return response;
  }

  /** Parse a RPC result (a non-fault response). */
  public RPCResult parseResult(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    List params = parseParams(parser);
    if (params.size() != 1)
      throw error(parser, "RPC result did not have exactly one param.");
    return new RPCResult(params.get(0));
  }

  /** Parse a RPC fault response. */
  public RPCFault parseFault(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    parser.nextTag();
    parser.require(parser.START_TAG, null, "value");
    Object faultValue = parseValue(parser);
    if (!(faultValue instanceof Map))
      throw error(parser, "RPC fault did not contain struct.");
    Map faultStruct = (Map) faultValue;
    if (!faultStruct.containsKey("faultCode"))
      throw error(parser, "RPC fault did not contain fault code.");
    Integer faultCode = (Integer) faultStruct.get("faultCode");
    if (!faultStruct.containsKey("faultString"))
      throw error(parser, "RPC fault did not contain fault string.");
    String faultString = (String) faultStruct.get("faultString");

    parser.nextTag();
    return new RPCFault(faultCode.intValue(), faultString);
  }

  /**
   * Parse a list of parameters.  The parser must be positioned at
   * the <params> start tag, and afterwards will be positioned at the
   * </params> end tag.
   */
  public List parseParams(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    List params = new ArrayList();
    while (parser.nextTag() == parser.START_TAG) {
      parser.require(parser.START_TAG, null, "param");
      parser.nextTag();
      parser.require(parser.START_TAG, null, "value");
      params.add(parseValue(parser));
      parser.require(parser.END_TAG, null, "value");
      parser.nextTag();
      parser.require(parser.END_TAG, null, "param");
    }
    return params;
  }

  /**
   * Parse a value.  The parser must be positioned at the <value>
   * start tag, and afterwards will be positioned at the </value> end
   * tag.
   */
  public Object parseValue(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    Object value;
    switch (parser.next()) {
    case XmlPullParser.TEXT: value = parser.getText(); parser.nextTag(); break;
    case XmlPullParser.END_TAG: value = ""; break;
    case XmlPullParser.START_TAG:
      String elementName = parser.getName();
      if (elementName.equals("i4") || elementName.equals("int"))
	value = Integer.valueOf(parser.nextText());
      else if (elementName.equals("boolean"))
	value = (parser.nextText().equals("0") ? Boolean.TRUE : Boolean.FALSE);
      else if (elementName.equals("string"))
	value = parser.nextText();
      else if (elementName.equals("double"))
	value = Double.valueOf(parser.nextText());
      else if (elementName.equals("dateTime.iso8601"))
	value = RPC.date.parse(parser.nextText(), new ParsePosition(0));
      else if (elementName.equals("base64"))
	value = StringUtils.decodeBase64(parser.nextText());
      else if (elementName.equals("struct"))
	value = parseStruct(parser);
      else if (elementName.equals("array"))
	value = parseArray(parser);
      else throw error(parser, "Unknown RPC value.");
      parser.require(parser.END_TAG, null, elementName);
      parser.nextTag();
      break;
    case XmlPullParser.END_DOCUMENT:
    default: throw error(parser, "Premature document end.");
    }
    return value;
  }

  /**
   * Parse a struct value.  The parser must be positioned at the <struct>
   * start tag, and afterwards will be positioned at the </struct> end
   * tag.
   */
  public Map parseStruct(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    Map map = new LinkedHashMap();
    while (parser.nextTag() == parser.START_TAG) {
      parser.require(parser.START_TAG, null, "member");

      parser.nextTag();
      parser.require(parser.START_TAG, null, "name");
      Object key = parser.nextText();
      parser.require(parser.END_TAG, null, "name");

      parser.nextTag();
      parser.require(parser.START_TAG, null, "value");
      Object value = parseValue(parser);
      parser.require(parser.END_TAG, null, "value");
      map.put(key, value);

      parser.nextTag();
      parser.require(parser.END_TAG, null, "member");
    }
    return map;
  }

  /**
   * Parse an array value.  The parser must be positioned at the <array>
   * start tag, and afterwards will be positioned at the </array> end
   * tag.
   */
  public List parseArray(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    List list = new ArrayList();
    parser.nextTag();
    parser.require(parser.START_TAG, null, "data");
    while (parser.nextTag() == parser.START_TAG) {
      parser.require(parser.START_TAG, null, "value");
      list.add(parseValue(parser));
      parser.require(parser.END_TAG, null, "value");
    }
    parser.require(parser.END_TAG, null, "data");
    parser.nextTag();
    return list;
  }

  private XmlPullParserException error(XmlPullParser parser, String msg) {
    return new XmlPullParserException(msg, parser, null);
  }
}
