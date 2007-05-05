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
import org.jivesoftware.xiff.data.ExtensionClassRegistry;

import org.jivesoftware.xiff.data.disco.DiscoExtension;

/**
 * Implements <a href="http://www.jabber.org/jeps/jep-0030.html">JEP-0030<a> for service info discovery.
 * Also, take a look at <a href="http://www.jabber.org/jeps/jep-0020.html">JEP-0020</a> and 
 * <a href="http://www.jabber.org/jeps/jep-0060.html">JEP-0060</a>.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @param parent (Optional) The XMLNode that contains this extension
 * @availability Flash Player 7
 * @toc-path Extensions/Service Discovery
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.disco.InfoDiscoExtension extends DiscoExtension implements IExtension
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "http://jabber.org/protocol/disco#info";

	private var myIdentities:Array;
	private var myFeatures:Array;

	public function getElementName():String
	{
		return DiscoExtension.ELEMENT;
	}

	public function getNS():String
	{
		return InfoDiscoExtension.NS;
	}

    /**
     * Performs the registration of this extension into the extension registry.  
     * 
	 * @availability Flash Player 7
     */
    public static function enable():Void
    {
        ExtensionClassRegistry.register(InfoDiscoExtension);
    }

	/**
	 * An array of objects that represent the identities of a resource discovered. For more information on
	 * categories, see <a href="http://www.jabber.org/registrar/disco-categories.html">
	 * http://www.jabber.org/registrar/disco-categories.html</a>
	 *
	 * The objects in the array have the following possible attributes:
	 * <ul>
	 * <li><code>category</code> - a category of the kind of identity</li>
	 * <li><code>type</code> - a path to a resource that can be discovered without a JID</li>
	 * <li><code>name</code> - the friendly name of the identity</li>
	 * </ul>
	 *
	 * @availability Flash Player 7
	 */
	public function get identities():Array
	{
		return myIdentities;
	}

	/**
	 * An array of namespaces this service supports for feature negotiation.
	 *
	 * @availability Flash Player 7
	 */
	public function get features():Array
	{
		return myFeatures;
	}

	public function deserialize(node:XMLNode):Boolean
	{
		if (super.deserialize(node)) {
			myIdentities = new Array();
			myFeatures = new Array();

			var children = getNode().childNodes;
			for (var i = 0; i < children.length; i++) {
				switch (children[i].nodeName) {
					case "identity":
						myIdentities.push(children[i].attributes);
						break;

					case "feature":
						myFeatures.push(children[i].attributes["var"]);
						break;
				}
			}
			return true;
		}
		return false;
	}
}
