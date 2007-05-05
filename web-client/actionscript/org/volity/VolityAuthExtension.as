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

import org.jivesoftware.xiff.data.auth.AuthExtension;

/**
 * Implements <a href="http://www.jabber.org/jeps/jep-0078.html">JEP-0078<a> for non SASL authentication.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @param parent The extension's parent node
 * @availability Flash Player 7
 * @toc-path Extensions/Authentication
 * @toc-sort 1/2
 */
class org.volity.VolityAuthExtension extends org.jivesoftware.xiff.data.auth.AuthExtension
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "jabber:iq:auth";
	public static var ELEMENT:String = "query";

	private var myUsernameNode:XMLNode;
	private var myPasswordNode:XMLNode;
	private var myDigestNode:XMLNode;
	private var myResourceNode:XMLNode;

	public function VolityAuthExtension( parent:XMLNode )
	{
		super(parent);
	}

	public function deserialize( node:XMLNode ):Boolean
	{
		setNode(node);
    
    var children = node.childNodes;
		for( var i in children ) {
			switch( children[i].nodeName )
			{
				case "username":
					myUsernameNode = children[i];
					break;
					
				case "password":
					myPasswordNode = children[i];
					break;
					
				case "digest":
					myDigestNode = children[i];
					break;

				case "resource":
					myResourceNode = children[i];
					break;
			}
		}
		return true;
	}

	/**
	 * Determines whether this is a digest (SHA1) authentication.
	 *
	 * @return It is a digest (true); it is not a digest (false)
	 * @availability Flash Player 7
	 */
	public function isDigest():Boolean 
	{ 
		return exists(myDigestNode); 
	}

}
