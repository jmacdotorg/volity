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

import org.jivesoftware.xiff.data.Extension;

/**
 * Implements the base MUC protocol schema from <a href="http://www.jabber.org/jeps/jep-0045.html">JEP-0045<a> for multi-user chat.
 *
 * This extension is typically used to test for the presence of MUC enabled conferencing service, or a MUC related error condition.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @param parent (Optional) The containing XMLNode for this extension
 * @availability Flash Player 7
 * @toc-path Extensions/Conferencing
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.muc.MUCExtension extends Extension implements IExtension, ISerializable
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "http://jabber.org/protocol/muc";
	public static var ELEMENT:String = "x";

	private var myHistoryNode:XMLNode;
	private var myPasswordNode:XMLNode;

	public function MUCExtension( parent:XMLNode )
	{
		super(parent);
	}

	public function getNS():String
	{
		return MUCExtension.NS;
	}

	public function getElementName():String
	{
		return MUCExtension.ELEMENT;
	}

	public function serialize( parent:XMLNode ):Boolean
	{
		if (!exists(getNode().parentNode)) {
			parent.appendChild(getNode().cloneNode(true));
		}
		return true;
	}

	public function deserialize( node:XMLNode ):Boolean
	{
		setNode(node);

		var children = node.childNodes;
		for( var i in children ) {
			switch( children[i].nodeName )
			{
				case "history":
					myHistoryNode = children[i];
					break;
					
				case "password":
					myPasswordNode = children[i];
					break;
			}
		}
		return true;
	}

	/**
	 * If a room is password protected, add this extension and set the password
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
	 * This is property allows a user to retrieve a server defined collection of previous messages.
	 * Set this property to "true" to retrieve a history of the dicussions.
	 */
	public function get history():Boolean
	{
		return exists(myHistoryNode);
	}

	public function set history(val:Boolean):Void
	{
		if (val) {
			myHistoryNode = ensureNode(myHistoryNode, "history");
		} else {
			myHistoryNode.removeNode();
			delete myHistoryNode;
		}
	}

	/**
	 * Size based condition to evaluate by the server for the maximum characters to return during history retrieval
	 */
	public function get maxchars():Number
	{
		return Number(myHistoryNode.attributes.maxchars);
	}

	public function set maxchars(val:Number):Void
	{
		myHistoryNode = ensureNode(myHistoryNode, "history");
		myHistoryNode.attributes.maxchars = val.toString();
	}

	/**
	 * Protocol based condition for the number of stanzas to return during history retrieval
	 */
	public function get maxstanzas():Number
	{
		return Number(myHistoryNode.attributes.maxstanzas);
	}

	public function set maxstanzas(val:Number):Void
	{
		myHistoryNode = ensureNode(myHistoryNode, "history");
		myHistoryNode.attributes.maxstanzas = val.toString();
	}

	/**
	 * Time based condition to retrive all messages for the last N seconds.
	 */
	public function get seconds():Number
	{
		return Number(myHistoryNode.attributes.seconds);
	}

	public function set seconds(val:Number):Void
	{
		myHistoryNode = ensureNode(myHistoryNode, "history");
		myHistoryNode.attributes.seconds = val.toString();
	}

	/**
	 * Time base condition to retrieve all messages from a given time formatted in the format described in 
	 * <a href="http://www.jabber.org/jeps/jep-0082.html">JEP-0082</a>.
	 *
	 */
	public function get since():String
	{
		return myHistoryNode.attributes.since;
	}

	public function set since(val:String):Void
	{
		myHistoryNode = ensureNode(myHistoryNode, "history");
		myHistoryNode.attributes.since = val;
	}
}
