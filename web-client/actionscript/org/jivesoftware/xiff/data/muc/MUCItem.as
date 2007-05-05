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
 * This class is used by the MUCExtension for internal representation of
 * information pertaining to occupants in a multi-user conference room.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @availability Flash Player 7
 * @toc-path Extensions/Conferencing
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.muc.MUCItem extends XMLStanza implements ISerializable
{
	public static var ELEMENT:String = "item";

	private var myActorNode:XMLNode;
	private var myReasonNode:XMLNode;

	public function MUCItem(parent:XMLNode)
	{
		super();

		getNode().nodeName = ELEMENT;

		if (exists(parent)) {
			parent.appendChild(getNode());
		}
	}

	public function serialize(parent:XMLNode):Boolean
	{
		if (parent != getNode().parentNode) {
			parent.appendChild(getNode().cloneNode(true));
		}

		return true;
	}

	public function deserialize(node:XMLNode):Boolean
	{
		setNode(node);

		var children = node.childNodes;
		for( var i in children ) {
			switch( children[i].nodeName )
			{
				case "actor":
					myActorNode = children[i];
					break;
					
				case "reason":
					myReasonNode = children[i];
					break;
			}
		}
		return true;
	}

	public function get actor():String
	{
		return myActorNode.attributes.jid;
	}

	public function set actor(val:String):Void
	{
		myActorNode = ensureNode(myActorNode, "actor");
		myActorNode.attributes.jid = val;
	}

	public function get reason():String
	{
		return myReasonNode.firstChild.nodeValue;
	}

	public function set reason(val:String):Void
	{
		myReasonNode = replaceTextNode(getNode(), myReasonNode, "reason", val);
	}

	public function get affiliation():String
	{
		return getNode().attributes.affiliation;
	}

	public function set affiliation(val:String):Void
	{
		getNode().attributes.affiliation = val;
	}

	public function get jid():String
	{
		return getNode().attributes.jid;
	}

	public function set jid(val:String):Void
	{
		getNode().attributes.jid = val;
	}

	/**
	 * The nickname of the conference occupant.
	 *
	 * @availability Flash Player 7
	 */
	public function get nick():String
	{
		return getNode().attributes.nick;
	}

	public function set nick(val:String):Void
	{
		getNode().attributes.nick = val;
	}

	public function get role():String
	{
		return getNode().attributes.role;
	}

	public function set role(val:String):Void
	{
		getNode().attributes.role = val;
	}
}
