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
import org.jivesoftware.xiff.data.ExtensionClassRegistry;

import org.jivesoftware.xiff.data.disco.DiscoExtension;

/**
 * Implements <a href="http://www.jabber.org/jeps/jep-0030.html">JEP-0030<a> for service item discovery.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @param parent The XML root of the containing stanza
 * @availability Flash Player 7
 * @toc-path Extensions/Service Discovery
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.disco.ItemDiscoExtension extends DiscoExtension implements IExtension
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "http://jabber.org/protocol/disco#items";

	private var myItems:Array;

	public function getElementName():String
	{
		return DiscoExtension.ELEMENT;
	}

	public function getNS():String
	{
		return ItemDiscoExtension.NS;
	}

    /**
     * Performs the registration of this extension into the extension registry.  
     * 
	 * @availability Flash Player 7
     */
    public static function enable():Void
    {
        ExtensionClassRegistry.register(ItemDiscoExtension);
    }

	/**
	 * An array of objects that represent the items discovered
	 *
	 * The objects in the array have the following possible attributes:
	 * <ul>
	 * <li><code>jid</code> - the resource name</li>
	 * <li><code>node</code> - a path to a resource that can be discovered without a JID</li>
	 * <li><code>name</code> - the friendly name of the jid</li>
	 * <li><code>action</code> - the kind of action that occurs during publication of services
	 * it can be either "remove" or "update"</li>
	 * </ul>
	 *
	 * @availability Flash Player 7
	 */
	public function get items():Array
	{
		return myItems;
	}

	public function deserialize(node:XMLNode):Boolean
	{
		if (super.deserialize(node)) {
			myItems = new Array();

			var children = getNode().childNodes;
			for (var i = 0; i < children.length; i++) {
				myItems.push(children[i].attributes);
			}
			return true;
		}
		return false;
	}
}
