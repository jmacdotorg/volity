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
import org.jivesoftware.xiff.data.XMLStanza;

/**
 * Class that representes a child resource of a browsed resource.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @availability Flash Player 7
 * @toc-path Extensions/Service Discovery
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.browse.BrowseItem extends XMLStanza implements ISerializable
{
	// Static class variables to be overridden in subclasses;
	public function BrowseItem(parent:XMLNode)
	{
		super();
		getNode().nodeName = "item";

		if (exists(parent)) {
			parent.appendChild(getNode());
		}

	}

	/**
	 * [required] The full JabberID of the entity described
	 *
	 * @availability Flash Player 7
	 */
	public function get jid():String 
	{ 
		return getNode().attributes.jid;
	}

	public function set jid(val:String):Void
	{
		getNode().attributes.jid = val;
	}

	/**
	 * [optional] One of the categories from the list above, or a non-standard category prefixed with the string "x-". 
	 *
	 * @availability Flash Player 7
	 * @see http://www.jabber.org/jeps/jep-0011.html#sect-id2594870
	 */
	public function get category():String 
	{ 
		return getNode().attributes.category;
	}

	public function set category(val:String):Void
	{
		getNode().attributes.category = val;
	}

	/**
	 * [optional] A friendly name that may be used in a user interface
	 *
	 * @availability Flash Player 7
	 */
	public function get name():String 
	{ 
		return getNode().attributes.name;
	}

	public function set name(val:String):Void
	{
		getNode().attributes.name = val;
	}

	/**
	 * [optional] -- One of the official types from the specified category, or a non-standard type prefixed with the string "x-". 
	 *
	 * @availability Flash Player 7
	 * @see http://www.jabber.org/jeps/jep-0011.html#sect-id2594870
	 */
	public function get type():String 
	{ 
		return getNode().attributes.type;
	}

	public function set type(val:String):Void
	{
		getNode().attributes.type = val;
	}

	/**
	 * [optional] A string containing the version of the node, equivalent to the response provided to a query in the 'jabber:iq:version' namespace. This is useful for servers, especially for lists of services (see the 'service/serverlist' category/type above). 
	 *
	 * @availability Flash Player 7
	 */
	public function get version():String 
	{ 
		return getNode().attributes.version;
	}

	public function set version(val:String):Void
	{
		getNode().attributes.version = val;
	}

	/**
	 * Add new features that are supported if you are responding to a browse request
	 *
	 * @availability Flash Player 7
	 */
	public function addNamespace(ns:String):XMLNode
	{
		return addTextNode(getNode(), "ns", ns);
	}
 
 	/**
	 * On top of the browsing framework, a simple form of "feature
	 * advertisement" can be built. This enables any entity to advertise which
	 * features it supports, based on the namespaces associated with those
	 * features. The <ns/> element is allowed as a subelement of the item. This
	 * element contains a single namespace that the entity supports, and
	 * multiple <ns/> elements can be included in any item. For a connected
	 * client this might be <ns>jabber:iq:oob</ns>, or for a service
	 * <ns>jabber:iq:search</ns>. This list of namespaces should be used to
	 * present available options for a user or to automatically locate
	 * functionality for an application.
	 *
	 * The children of a browse result may proactively contain a few <ns/>
	 * elements (such as the result of the service request to the home server),
	 * which advertises the features that the particular service supports. This
	 * list may not be complete (it is only for first-pass filtering by simpler
	 * clients), and the JID should be browsed if a complete list is required.
	 *
	 * @availability Flash Player 7
	 */
	public function get namespaces():Array
	{
		var res:Array = new Array();
		var node:XMLNode = getNode();

		for (var i in node.childNodes) {
			var child:XMLNode = node.childNodes[i];
			if (child.nodeName == "ns") {
				res.push(child.firstChild.nodeValue);
			}
		}
		return res;
	}

	public function serialize(parentNode:XMLNode):Boolean
	{
		var node:XMLNode = getNode();
		if (!exists(node.parentNode)) {
			parentNode.appendChild(node.cloneNode(true));
		}

		return true;
	}

	public function deserialize(node:XMLNode):Boolean
	{
		setNode(node);
		return true;
	}
}
