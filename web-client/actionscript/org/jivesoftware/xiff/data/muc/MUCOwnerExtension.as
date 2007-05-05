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
import org.jivesoftware.xiff.data.IExtendable;
import org.jivesoftware.xiff.data.ISerializable;

import org.jivesoftware.xiff.data.muc.MUCBaseExtension;

/**
 * Implements the administration command data model in <a href="http://www.jabber.org/jeps/jep-0045.html">JEP-0045<a> for multi-user chat.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @param parent (Optional) The containing XMLNode for this extension
 * @availability Flash Player 7
 * @toc-path Extensions/Conferencing
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.muc.MUCOwnerExtension extends MUCBaseExtension implements IExtension
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "http://jabber.org/protocol/muc#owner";
	public static var ELEMENT:String = "query";

	private var myDestroyNode:XMLNode;

	public function MUCOwnerExtension( parent:XMLNode )
	{
		super(parent);
	}

	public function getNS():String
	{
		return MUCOwnerExtension.NS;
	}

	public function getElementName():String
	{
		return MUCOwnerExtension.ELEMENT;
	}

	public function deserialize( node:XMLNode ):Boolean
	{
		super.deserialize(node);

		var children = node.childNodes;
		for( var i in children ) {
			switch( children[i].nodeName )
			{
				case "destroy":
					myDestroyNode = children[i];
					break;
			}
		}
		return true;
	}

    public function serialize( parent:XMLNode ):Boolean
    {
        return super.serialize(parent);
    }

    /**
     * Replaces the <code>destroy</code> node with a new node and sets
     * the <code>reason</code> element and <code>jid</code> attribute
     *
     * @param reason A string describing the reason for room destruction
     * @param alternateJID A string containing a JID that room members can use instead of this room
     * @availability Flash Player 7
     */
    public function destroy(reason:String, alternateJID:String):Void
    {
        myDestroyNode = ensureNode(myDestroyNode, "destroy");
        for (var i in myDestroyNode.childNodes) {
            myDestroyNode.childNodes[i].removeNode();
        }

        if( exists(reason) ) { replaceTextNode(myDestroyNode, undefined, "reason", reason); }
        if( exists(alternateJID) ) { myDestroyNode.attributes.jid = alternateJID; }
    }
}
