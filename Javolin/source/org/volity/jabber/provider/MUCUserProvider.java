package org.volity.jabber.provider;

import java.io.IOException;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.volity.jabber.packet.MUCUser;

public class MUCUserProvider implements PacketExtensionProvider {
  // Inherited from PacketExtensionProvider.
  public PacketExtension parseExtension(XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    MUCUser user = new MUCUser();
    while (parser.nextTag() == parser.START_TAG) {
      String elementName = parser.getName();
      if (elementName.equals("item"))
	parseItem(user, parser);
      else if (elementName.equals("status"))
	parseStatus(user, parser);
    }
    parser.require(parser.END_TAG, null, MUCUser.elementName);
    return user;
  }

  public void parseItem(MUCUser user, XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    user.setRole(parser.getAttributeValue("", "role"));
    user.setAffiliation(parser.getAttributeValue("", "affiliation"));
    user.setJID(parser.getAttributeValue("", "jid"));
    user.setNickname(parser.getAttributeValue("", "nickname"));

    parser.nextTag();
    parser.require(parser.END_TAG, null, "item");
  }

  public void parseStatus(MUCUser user, XmlPullParser parser)
    throws XmlPullParserException, IOException
  {
    String code = parser.getAttributeValue("", "code");
    if (code == null)
      throw new XmlPullParserException("no code attribute in status element");
    try {
      user.setStatus(Integer.parseInt(code));
    } catch (NumberFormatException e) {
      throw new XmlPullParserException("status code: " + e.getMessage());
    }
    parser.nextTag();
    parser.require(parser.END_TAG, null, "status");
  }
}
