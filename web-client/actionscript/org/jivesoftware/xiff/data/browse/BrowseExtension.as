﻿/*
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
import org.jivesoftware.xiff.data.IExtension;
import org.jivesoftware.xiff.data.ExtensionClassRegistry;
import org.jivesoftware.xiff.data.browse.BrowseItem;

/**
 * Implements jabber:iq:browse namespace.  Use this extension to request the items
 * of an agent or service such as the rooms of a conference server or the members of
 * a room.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @availability Flash Player 7
 * @toc-path Extensions/Service Discovery
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.browse.BrowseExtension extends BrowseItem implements IExtension, ISerializable 
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "jabber:iq:browse";
	public static var ELEMENT:String = "query";

    private static var staticDepends = ExtensionClassRegistry;

	private var myItems:Array;

	public function BrowseExtension(parent:XMLNode)
	{
		super(parent);

		getNode().attributes.xmlns = getNS();
		getNode().nodeName = getElementName();

		myItems = new Array();
	}

	/**
	 * Gets the namespace associated with this extension.
	 * The namespace for the BrowseExtension is "jabber:iq:browse".
	 *
	 * @return The namespace
	 * @availability Flash Player 7
	 */
	public function getNS():String
	{
		return BrowseExtension.NS;
	}

	/**
	 * Gets the element name associated with this extension.
	 * The element for this extension is "query".
	 *
	 * @return The element name
	 * @availability Flash Player 7
	 */
	public function getElementName():String
	{
		return BrowseExtension.ELEMENT;
	}

    /**
     * Performs the registration of this extension into the extension registry.  
     * 
	 * @availability Flash Player 7
     */
    public static function enable():Void
    {
        ExtensionClassRegistry.register(BrowseExtension);
    }


	/**
	 * If you are generating a browse response to a browse request, then
	 * fill out the items list with this method.
	 *
	 * @param item BrowseItem which contains the info related to the browsed resource
	 * @availability Flash Player 7
	 * @returns the item added
	 * @see org.jivesoftware.xiff.data.browse.BrowseItem
	 */
	public function addItem(item:BrowseItem):BrowseItem
	{
		myItems.push(item);
		return item;
	}

	/**
	 * An array of BrowseItems containing information about the browsed resource
	 *
	 * @availability Flash Player 7
	 * @returns array of BrowseItems
	 * @see org.jivesoftware.xiff.data.browse.BrowseItem
	 */
	public function get items():Array
	{
		return myItems;
	}

	/**
	 * ISerializable implementation which loads this extension from XML
	 *
	 * @availability Flash Player 7
	 * @see org.jivesoftware.xiff.data.ISerializable
	 */
	public function serialize(parentNode:XMLNode):Boolean
	{
		var node:XMLNode = getNode();
		for (var i in myItems) {
			myItems[i].serialize(node);
		}

		if (!exists(node.parentNode)) {
			parentNode.appendChild(node.cloneNode(true));
		}

		return true;
	}

	/**
	 * ISerializable implementation which saves this extension to XML
	 *
	 * @availability Flash Player 7
	 * @see org.jivesoftware.xiff.data.ISerializable
	 */
	public function deserialize(node:XMLNode):Boolean
	{
		setNode(node);

		this['deserialized'] = true;

		myItems = new Array();

		for (var i in node.childNodes) {
			var child = node.childNodes[i];
			switch(child.nodeName) {
				case "item":
					var item = new BrowseItem(getNode());
					item.deserialize(child);
					myItems.push(item);
					break;
			}
		}
		return true;
	}
}
