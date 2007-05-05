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
import org.jivesoftware.xiff.data.ExtensionClassRegistry;
import org.jivesoftware.xiff.data.auth.SHA1;

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
class org.jivesoftware.xiff.data.auth.AuthExtension extends Extension implements IExtension, ISerializable
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "jabber:iq:auth";
	public static var ELEMENT:String = "query";

	private var myUsernameNode:XMLNode;
	private var myPasswordNode:XMLNode;
	private var myDigestNode:XMLNode;
	private var myResourceNode:XMLNode;

	public function AuthExtension( parent:XMLNode )
	{
		super(parent);
	}

	/**
	 * Gets the namespace associated with this extension.
	 * The namespace for the AuthExtension is "jabber:iq:auth".
	 *
	 * @return The namespace
	 * @availability Flash Player 7
	 */
	public function getNS():String
	{
		return AuthExtension.NS;
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
		return AuthExtension.ELEMENT;
	}

    /**
     * Performs the registration of this extension into the extension registry.  
     * 
	 * @availability Flash Player 7
     */
    public static function enable():Void
    {
        ExtensionClassRegistry.register(AuthExtension);
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
	 * Computes the SHA1 digest of the password and session ID for use when authenticating with the server.
	 *
	 * @param sessionID The session ID provided by the server
	 * @param password The user's password
	 * @availability Flash Player 7
	 */
	public static function computeDigest( sessionID:String, password:String ):String
	{
		return SHA1.calcSHA1( sessionID + password ).toLowerCase();
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

	/**
	 * Determines whether this is a plain-text password authentication.
	 *
	 * @return It is plain-text password (true); it is not plain-text password (false)
	 * @availability Flash Player 7
	 */
	public function isPassword():Boolean 
	{ 
		return exists(myPasswordNode); 
	}

	/**
	 * The username to use for authentication.
	 *
	 * @availability Flash Player 7
	 */
	public function get username():String 
	{ 
		return myUsernameNode.firstChild.nodeValue; 
	}

	public function set username(val:String):Void 
	{ 
		myUsernameNode = replaceTextNode(getNode(), myUsernameNode, "username", val);
	}

	/**
	 * The password to use for authentication.
	 *
	 * @availability Flash Player 7
	 */
	public function get password():String 
	{ 
		return myPasswordNode.firstChild.nodeValue;
	}

	public function set password(val:String):Void
	{
		// Either or for digest or password
		myDigestNode.removeNode();
		delete myDigestNode;

		myPasswordNode = replaceTextNode(getNode(), myPasswordNode, "password", val);
	}

	/**
	 * The SHA1 digest to use for authentication.
	 *
	 * @availability Flash Player 7
	 */
	public function get digest():String 
	{ 
		return myDigestNode.firstChild.nodeValue;
	}

	public function set digest(val:String):Void
	{
		// Either or for digest or password
		myPasswordNode.removeNode();
		delete myPasswordNode;

		myDigestNode = replaceTextNode(getNode(), myDigestNode, "digest", val);
	}

	/**
	 * The resource to use for authentication.
	 *
	 * @availability Flash Player 7
	 * @see org.jivesoftware.xiff.core.XMPPConnection#resource
	 */
	public function get resource():String 
	{ 
		return myResourceNode.firstChild.nodeValue 
	}

	public function set resource(val:String):Void
	{
		myResourceNode = replaceTextNode(getNode(), myResourceNode, "resource", val);
	}

}
