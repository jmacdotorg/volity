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
 
import org.jivesoftware.xiff.data.XMLStanza;
import org.jivesoftware.xiff.data.ISerializable;

/**
 * This class is used internally by the RosterExtension class for managing items
 * received and sent as roster data. Usually, each item in the roster represents a single
 * contact, and this class is used to represent, abstract, and serialize/deserialize
 * this data.
 *
 * @author Sean Voisen
 * @since 2.0.0
 * @availability Flash Player 7
 * @see org.jivesoftware.xiff.data.im.RosterExtension
 * @param parent The parent XMLNode
 * @toc-path Extensions/Instant Messaging
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.im.RosterItem extends XMLStanza implements ISerializable
{
	public static var ELEMENT:String = "item";
	
	private var myGroupNodes:Array;
	
	public function RosterItem( parent:XMLNode )
	{
		super();
		
		getNode().nodeName = ELEMENT;
		myGroupNodes = new Array();
		
		if( exists( parent ) ) {
			parent.appendChild( getNode() );
		}
	}
	
	/**
	 * Serializes the RosterItem data to XML for sending.
	 *
	 * @availability Flash Player 7
	 * @param parent The parent node that this item should be serialized into
	 * @return An indicator as to whether serialization was successful
	 */
	public function serialize( parent:XMLNode ):Boolean
	{
		if (!exists(jid)) {
			trace("Warning: required roster item attributes 'jid' missing");
			return false;
		}
		
		if( parent != getNode().parentNode ) {
			parent.appendChild( getNode().cloneNode( true ) );
		}

		return true;
	}
	
	/**
	 * Deserializes the RosterItem data.
	 *
	 * @availability Flash Player 7
	 * @param node The XML node associated this data
	 * @return An indicator as to whether deserialization was successful
	 */
	public function deserialize( node:XMLNode ):Boolean
	{
		setNode( node );

		var children = node.childNodes;
		for( var i in children ) {
			switch( children[i].nodeName ) {
				case "group":
					myGroupNodes.push( children[i] );
					break;
			}
		}
		
		return true;
	}
	
	/**
	 * Adds a group to the roster item. Contacts in the roster can be associated
	 * with multiple groups.
	 *
	 * @availability Flash Player 7
	 * @param groupName The name of the group to add
	 */
	public function addGroup( groupName:String ):Void
	{
		var node:XMLNode = addTextNode( getNode(), "group", groupName );
		
		myGroupNodes.push( node );
	}
	
	/**
	 * Gets a list of all the groups associated with this roster item.
	 *
	 * @availability Flash Player 7
	 * @return An array of strings containing the name of each group
	 */
	public function getGroups():Array
	{
		var returnArr:Array = new Array();
		
		for( var i in myGroupNodes )
		{
			returnArr.push( myGroupNodes[i].firstChild.nodeValue );
		}
		
		return returnArr;
	}
	
	public function getGroupCount():Number
	{
		return myGroupNodes.length;
	}
	
	public function removeAllGroups():Void
	{
		for( var i in myGroupNodes ) {
			myGroupNodes[i].removeNode();
		}
		
		myGroupNodes = new Array();
	}
	
	public function removeGroupByName( groupName:String ):Boolean
	{
		for( var i in myGroupNodes )
		{
			if( myGroupNodes[i].nodeValue == groupName ) {
				myGroupNodes[i].removeNode();
				myGroupNodes.splice( Number(i), 1 );
				return true;
			}
		}
		
		return false;
	}	
	
	/**
	 * The JID for this roster item.
	 *
	 * @availability Flash Player 7
	 */
	public function get jid():String
	{
		return getNode().attributes.jid;
	}
	
	public function set jid( newJID:String ):Void
	{
		getNode().attributes.jid = newJID;
	}
	
	/**
	 * The display name for this roster item.
	 *
	 * @availability Flash Player 7
	 */
	public function get name():String
	{
		return getNode().attributes.name;
	}
	
	public function set name( newName:String ):Void
	{
		getNode().attributes.name = newName;
	}
	
	/**
	 * The subscription type for this roster item. Subscription types
	 * have been enumerated by static variables in the RosterExtension:
	 * <ul>
	 * <li>RosterExtension.SUBSCRIBE_TYPE_NONE</li>
	 * <li>RosterExtension.SUBSCRIBE_TYPE_TO</li>
	 * <li>RosterExtension.SUBSCRIBE_TYPE_FROM</li>
	 * <li>RosterExtension.SUBSCRIBE_TYPE_BOTH</li>
	 * <li>RosterExtension.SUBSCRIBE_TYPE_REMOVE</li>
	 * </ul>
	 *
	 * @availability Flash Player 7
	 */
	public function get subscription():String
	{
		return getNode().attributes.subscription;
	}
	
	public function set subscription( newSubscription:String ):Void
	{
		getNode().attributes.subscription = newSubscription;
	}
}

