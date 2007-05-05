/*
 * Copyright (C) 2003-2004 
 * Sean Voisen <sean@mediainsites.com>
 * Sean Treadway <seant@oncotype.dk>
 * Media Insites, Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA 
 *
 */
 
import org.jivesoftware.xiff.data.ISerializable;
import org.jivesoftware.xiff.data.XMPPStanza;
import org.jivesoftware.xiff.data.ExtensionClassRegistry;
import org.jivesoftware.xiff.data.xhtml.XHTMLExtension;

/**
 * A class for abstraction and encapsulation of message data.
 *
 * @author Sean Voisen
 # @since 2.0.0
 * @param recipient The JID of the message recipient
 * @param sender The JID of the message sender - the server should report an error if this is falsified
 * @param msgID The message ID
 * @param msgBody The message body in plain-text format
 * @param msgHTMLBody The message body in XHTML format
 * @param msgType The message type
 * @param msgSubject (Optional) The message subject
 * @availability Flash Player 7
 * @toc-path Data
 * @toc-sort 1
 */

class org.jivesoftware.xiff.data.Message extends XMPPStanza implements ISerializable
{
	// Static variables for specific type strings
	public static var NORMAL_TYPE:String = "normal";
	public static var CHAT_TYPE:String = "chat";
	public static var GROUPCHAT_TYPE:String = "groupchat";
	public static var HEADLINE_TYPE:String = "headline";
	public static var ERROR_TYPE:String = "error";

	// Private references to nodes within our XML
	private var myBodyNode:XMLNode;
	private var mySubjectNode:XMLNode;
	private var myThreadNode:XMLNode;

	private static var isMessageStaticCalled = MessageStaticConstructor();
	private static var staticConstructorDependency = [ XMPPStanza, XHTMLExtension, ExtensionClassRegistry ];

	public function Message( recipient:String, msgID:String, msgBody:String, msgHTMLBody:String, msgType:String, msgSubject:String )
	{
		// Flash gives a warning if superconstructor is not first, hence the inline id check
		super( recipient, null, msgType, exists( msgID ) ? msgID : generateID("m_"), "message" );
		body = msgBody;
		htmlBody = msgHTMLBody;
		subject = msgSubject;
	}

	public static function MessageStaticConstructor():Boolean
	{
		XHTMLExtension.enable();
		return true;
	}

	/**
	 * Serializes the Message into XML form for sending to a server.
	 *
	 * @return An indication as to whether serialization was successful
	 * @availability Flash Player 7
	 */
	public function serialize( parentNode:XMLNode ):Boolean
	{
		return super.serialize( parentNode );
	}

	/**
	 * Deserializes an XML object and populates the Message instance with its data.
	 *
	 * @param xmlNode The XML to deserialize
	 * @availability Flash Player 7
	 * @return An indication as to whether deserialization was sucessful
	 */
	public function deserialize( xmlNode:XMLNode ):Boolean
	{
		var isSerialized = super.deserialize( xmlNode );
		if (isSerialized) {
			var children = xmlNode.childNodes;
			for( var i in children )
			{
				switch( children[i].nodeName )
				{
					case "body":
						myBodyNode = children[i];
						break;
					
					case "subject":
						mySubjectNode = children[i];
						break;
						
					case "thread":
						myThreadNode = children[i];
						break;
				}
			}
		}
		return isSerialized;
	}
	
	/**
	 * The message body in plain-text format. If a client cannot render HTML-formatted
	 * text, this text is typically used instead.
	 *
	 * @availability Flash Player 7
	 */
	public function get body():String
	{
		return myBodyNode.firstChild.nodeValue;
	}
	
	public function set body( bodyText:String ):Void
	{
		myBodyNode = replaceTextNode(getNode(), myBodyNode, "body", bodyText);
	}
	
	/**
	 * The message body in XHTML format. Internally, this uses the XHTML data extension.
	 *
	 * @see org.jivesoftware.xiff.data.xhtml.XHTMLExtension
	 * @availability Flash Player 7
	 */
	public function get htmlBody():String
	{
		var ext:XHTMLExtension = getAllExtensionsByNS(XHTMLExtension.NS)[0];
		return ext.body;
	}
	
	public function set htmlBody( bodyHTML:String ):Void
	{
		// Removes any existing HTML body text first
        removeAllExtensions(XHTMLExtension.NS);

        if (exists(bodyHTML) && bodyHTML.length > 0) {
            var ext:XHTMLExtension = new XHTMLExtension(getNode());
            ext.body = bodyHTML;
            addExtension(ext);
        }
	}

	/**
	 * The message subject. Typically chat and groupchat-type messages do not use
	 * subjects. Rather, this is reserved for normal and headline-type messages.
	 *
	 * @availability Flash Player 7
	 */
	public function get subject():String
	{
		return mySubjectNode.firstChild.nodeValue;
	}
	
	public function set subject( aSubject:String ):Void
	{
		mySubjectNode = replaceTextNode(getNode(), mySubjectNode, "subject", aSubject);
	}

	/**
	 * The message thread ID. Threading is used to group messages of the same discussion together.
	 * The library does not perform message grouping, rather it is up to any client authors to
	 * properly perform this task.
	 *
	 * @availability Flash Player 7
	 */
	public function get thread():String
	{
		return myThreadNode.firstChild.nodeValue;
	}
	
	public function set thread( theThread:String ):Void
	{
		myThreadNode = replaceTextNode(getNode(), myThreadNode, "thread", theThread);
	}
}
