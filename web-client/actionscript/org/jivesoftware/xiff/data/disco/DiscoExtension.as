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

import org.jivesoftware.xiff.data.Extension;
import org.jivesoftware.xiff.data.ISerializable;

/**
 * Base class for service discovery extensions.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @availability Flash Player 7
 * @toc-path Extensions/Service Discovery
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.disco.DiscoExtension extends Extension implements ISerializable
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "http://jabber.org/protocol/disco";
	public static var ELEMENT:String = "query";

	public var myService:String;

	/**
	 * The name of the resource of the service queried if the resource doesn't 
	 * have a JID. For more information, see <a href="http://www.jabber.org/registrar/disco-nodes.html">
	 * http://www.jabber.org/registrar/disco-nodes.html</a>.
	 *
	 * @availability Flash Player 7
	 */
	public function get serviceNode():String 
	{ 
		return getNode().parentNode.attributes.node;
	}

	public function set serviceNode(val:String):Void
	{
		getNode().parentNode.attributes.node = val;
	}

	/**
	 * The service name of the discovery procedure
	 *
	 * @availability Flash Player 7
	 */

	public function set service(val:String):Void
	{
		var parent:XMLNode = getNode().parentNode;

		if (parent.attributes.type == "result") {
			parent.attributes.from = val;
		} else {
			parent.attributes.to = val;
		}
	}

	public function get service():String
	{
		var parent:XMLNode = getNode().parentNode;

		if (parent.attributes.type == "result") {
			return parent.attributes.from;
		} else {
			return parent.attributes.to;
		}
	}

	public function serialize(parentNode:XMLNode):Boolean
	{
		if (parentNode != getNode().parentNode) {
			parentNode.appendChild(getNode().cloneNode(true));
		}
		return true;
	}

	public function deserialize(node:XMLNode):Boolean
	{
		setNode(node);
		return true;
	}
}
