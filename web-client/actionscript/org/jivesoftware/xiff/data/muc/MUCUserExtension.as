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

import org.jivesoftware.xiff.data.IExtension;
import org.jivesoftware.xiff.data.ISerializable;

import org.jivesoftware.xiff.data.muc.MUCBaseExtension;

/**
 * Implements the base MUC user protocol schema from <a href="http://www.jabber.org/jeps/jep-0045.html">JEP-0045<a> for multi-user chat.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @param parent (Optional) The containing XMLNode for this extension
 * @availability Flash Player 7
 * @toc-path Extensions/Conferencing
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.muc.MUCUserExtension extends MUCBaseExtension implements IExtension
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "http://jabber.org/protocol/muc#user";
	public static var ELEMENT:String = "x";

	public static var DECLINE_TYPE:String = "decline";
	public static var DESTROY_TYPE:String = "destroy";
	public static var INVITE_TYPE:String = "invite";
	public static var OTHER_TYPE:String = "other";

	private var myActionNode:XMLNode;
	private var myPasswordNode:XMLNode;
	private var myStatusNode:XMLNode;

	public function MUCUserExtension( parent:XMLNode )
	{
		super(parent);
	}

	public function getNS():String
	{
		return MUCUserExtension.NS;
	}

	public function getElementName():String
	{
		return MUCUserExtension.ELEMENT;
	}

	public function deserialize( node:XMLNode ):Boolean
	{
		super.deserialize(node);

		var children = node.childNodes;
		for( var i in children ) {
			switch( children[i].nodeName )
			{
				case DECLINE_TYPE:
					myActionNode = children[i];
					break;
					
				case DESTROY_TYPE:
					myActionNode = children[i];
					break;
					
				case INVITE_TYPE:
					myActionNode = children[i];
					break;
					
				case "status":
					myStatusNode = children[i];
					break;
					
				case "password":
					myPasswordNode = children[i];
					break;
			}
		}
		return true;
	}

	/**
	 * The type of user extension this is
	 */
	public function get type():String
	{
		return myActionNode.nodeName == undefined ? OTHER_TYPE : myActionNode.nodeName;
	}

	/**
	 * The to property for invite and decline action types
	 */
	public function get to():String
	{
		return myActionNode.attributes.to;
	}

	/**
	 * The from property for invite and decline action types
	 */
	public function get from():String
	{
		return myActionNode.attributes.from;
	}

	/**
	 * The jid property for destroy the action type
	 */
	public function get jid():String
	{
		return myActionNode.attributes.jid;
	}

    /**
     * The reason for the invite/decline/destroy
     */
    public function get reason():String
    {
        return myActionNode.firstChild.firstChild.nodeValue;
    }

	/**
	 * Use this extension to invite another user
	 */
	public function invite(to:String, from:String, reason:String)
	{
		updateActionNode(INVITE_TYPE, {to:to, from:from}, reason);
	}

	/**
	 * Use this extension to destroy a room
	 */
	public function destroy(room:String, reason:String)
	{
		updateActionNode(DESTROY_TYPE, {jid: room}, reason);
	}

	/**
	 * Use this extension to decline an invitation
	 */
	public function decline(to:String, from:String, reason:String)
	{
		updateActionNode(DECLINE_TYPE, {to:to, from:from}, reason);
	}

	/**
	 * Property to use if the concerned room is password protected
	 */
	public function get password():String
	{
		return myPasswordNode.firstChild.nodeValue;
	}

	public function set password(val:String):Void
	{
		myPasswordNode = replaceTextNode(getNode(), myPasswordNode, "password", val);
	}

	/**
	 * Property used to add or retrieve a status code describing the condition that occurs.
	 */
	public function get statusCode():Number
	{
		return Number(myStatusNode.attributes.code);
	}

	public function set statusCode(val:Number):Void
	{
		myStatusNode = ensureNode(myStatusNode, "status");
		myStatusNode.attributes.code = val.toString();
	}

	/**
	 * Property that contains some text with a description of a condition.
	 */
	public function get statusMessage():String
	{
		return myStatusNode.firstChild.nodeValue;
	}

	public function set statusMessage(val:String):Void
	{
		myStatusNode = replaceTextNode(getNode(), myStatusNode, "status", val);
	}

	/**
	 * Internal method that manages the type of node that we will use for invite/destroy/decline messages
	 */
	private function updateActionNode(type:String, attrs:Object, reason:String)
	{
		myActionNode.removeNode();

		myActionNode = XMLFactory.createElement(type);
		for (var i in attrs) {
			if (exists(attrs[i])) {
				myActionNode.attributes[i] = attrs[i];
			}
		}
		getNode().appendChild(myActionNode);

		if (reason.length > 0) {
			replaceTextNode(myActionNode, undefined, "reason", reason);
		}
	}
}
