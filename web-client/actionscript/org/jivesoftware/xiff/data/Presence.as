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

/**
 * This class provides encapsulation for manipulation of presence data for sending and receiving.
 *
 * @author Sean Voisen
 * @since 2.0.0
 * @availability Flash Player 7
 * @param recipient The recipient of the presence, usually in the form of a JID.
 * @param sender The sender of the presence, usually in the form of a JID.
 * @param presenceType The type of presence as a string. There are predefined static variables for this.
 * @param showVal What to show for this presence (away, online, etc.) There are predefined static variables for this.
 * @param statusVal The status; usually used for the "away message."
 * @param priorityVal The priority of this presence; usually on a scale of 1-5.
 * @toc-path Data
 * @toc-sort 1
 */
class org.jivesoftware.xiff.data.Presence extends XMPPStanza implements ISerializable 
{
	// Static variables for specific type strings
	public static var AVAILABLE_TYPE:String = "available";
	public static var UNAVAILABLE_TYPE:String = "unavailable";
	public static var PROBE_TYPE:String = "probe";
	public static var SUBSCRIBE_TYPE:String = "subscribe";
	public static var UNSUBSCRIBE_TYPE:String = "unsubscribe";
	public static var SUBSCRIBED_TYPE:String = "subscribed";
	public static var UNSUBSCRIBED_TYPE:String = "unsubscribed";
	public static var ERROR_TYPE:String = "error";
	
	// Static variables for show values
	public static var SHOW_AWAY:String = "away";
	public static var SHOW_CHAT:String = "chat";
	public static var SHOW_DND:String = "dnd";
	public static var SHOW_NORMAL:String = "normal";
	public static var SHOW_XA:String = "xa";

	// Private node references for property lookups
	private var myShowNode:XMLNode;
	private var myStatusNode:XMLNode;
	private var myPriorityNode:XMLNode;


	public function Presence( recipient:String, sender:String, presenceType:String, showVal:String, statusVal:String, priorityVal:Number ) 
	{		
		super( recipient, sender, presenceType, null, "presence" );
		
		show = showVal;
		status = statusVal;
		priority = priorityVal;
	}
	
	/**
	 * Serializes the Presence into XML form for sending to a server.
	 *
	 * @return An indication as to whether serialization was successful
	 * @availability Flash Player 7
	 */
	public function serialize( parentNode:XMLNode ):Boolean 
	{
		return super.serialize( parentNode );
	}
	
	/**
	 * Deserializes an XML object and populates the Presence instance with its data.
	 *
	 * @param xmlNode The XML to deserialize
	 * @return An indication as to whether deserialization was sucessful
	 * @availability Flash Player 7
	 */
	public function deserialize( xmlNode:XMLNode ):Boolean 
	{	
		var isDeserialized:Boolean = super.deserialize( xmlNode );
		
		if (isDeserialized) { 
			var children = xmlNode.childNodes;
			for( var i in children ) 
			{
				switch( children[i].nodeName ) 
				{
					case "show":
						myShowNode = children[i];
						break;
						
					case "status":
						myStatusNode = children[i];
						break;
						
					case "priority":
						myPriorityNode = children[i];
						break;
				}
			}
		}
		return isDeserialized;
	}
	
	/**
	 * The show value; away, online, etc. There are predefined static variables in the Presence
	 * class for this:
	 * <ul>
	 * <li>Presence.SHOW_AWAY</li>
	 * <li>Presence.SHOW_CHAT</li>
	 * <li>Presence.SHOW_DND</li>
	 * <li>Presence.SHOW_NORMAL</li>
	 * <li>Presence.SHOW_XA</li>
	 * </ul>
	 *
	 * @availability Flash Player 7
	 */
	public function get show():String 
	{
		return myShowNode.firstChild.nodeValue;
	}
	
	public function set show( showVal:String ):Void 
	{
		myShowNode = replaceTextNode(getNode(), myShowNode, "show", showVal);
	}
	
	/**
	 * The status; usually used for "away messages."
	 *
	 * @availability Flash Player 7
	 */
	public function get status():String 
	{
		return myStatusNode.firstChild.nodeValue;
	}
	
	public function set status( statusVal:String ):Void 
	{
		myStatusNode = replaceTextNode(getNode(), myStatusNode, "status", statusVal);
	}
	
	/**
	 * The priority of the presence, usually on a scale of 1-5.
	 *
	 * @availability Flash Player 7
	 */
	public function get priority():Number 
	{
		var p = Number(myPriorityNode.firstChild.nodeValue);
		if( isNaN( p ) ) {
			return null;
		}
		else {
			return p;
		}
	}
	
	public function set priority( priorityVal:Number ):Void 
	{
		myPriorityNode = replaceTextNode(getNode(), myPriorityNode, "priority", priorityVal.toString());
	}
}
