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

import org.jivesoftware.xiff.core.XMPPConnection;
import org.jivesoftware.xiff.data.browse.BrowseExtension;
import org.jivesoftware.xiff.data.disco.ItemDiscoExtension;
import org.jivesoftware.xiff.data.disco.InfoDiscoExtension;
import org.jivesoftware.xiff.data.ExtensionClassRegistry;
import org.jivesoftware.xiff.data.IQ;

/**
 * Provides a means of quering for available services on an XMPP server using
 * the Disco protocol extension. For more information on Disco, take a look at
 * <a href="http://www.jabber.org/jeps/jep-0030.html">JEP-0030</a> and 
 * <a href="http://www.jabber.org/jeps/jep-0011.html">JEP-0011</a> for the
 * protocol enhancement specifications.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @toc-path Core
 * @toc-sort 1
 */
class org.jivesoftware.xiff.core.Browser
{
	private var _connection:XMPPConnection;
	private var _pending:Object;

	private static var _staticDepends:Array = [ ItemDiscoExtension, InfoDiscoExtension, BrowseExtension, ExtensionClassRegistry ];
	private static var _isEventEnabled:Boolean = BrowserStaticConstructor();

	public function Browser(conn:XMPPConnection)
	{
		connection = conn;
		_pending = new Object();
	}

	private static function BrowserStaticConstructor():Boolean
	{
		ItemDiscoExtension.enable();
		InfoDiscoExtension.enable();
		BrowseExtension.enable();
		return true;
	}

	public function getNodeInfo(service:String, node:String, callback:String, scope:Object):Void
	{
		var iq = new IQ(service, IQ.GET_TYPE);
		var ext:InfoDiscoExtension = new InfoDiscoExtension(iq.getNode());
		ext.service = service;
		ext.serviceNode = node;
		iq.callbackName = callback;
		iq.callbackScope = scope;
		iq.addExtension(ext);
		connection.send(iq);
	}

	public function getNodeItems(service:String, node:String, callback:String, scope:Object):Void
	{
		var iq = new IQ(service, IQ.GET_TYPE);
		var ext:ItemDiscoExtension = new ItemDiscoExtension(iq.getNode());
		ext.service = service;
		ext.serviceNode = node;
		iq.callbackName = callback;
		iq.callbackScope = scope;
		iq.addExtension(ext);
		connection.send(iq);
	}

	/**
	 * Retrieves a list of available service information from the server specified. On successful query,
	 * the callback specified will be called and passed a single parameter containing
	 * a reference to an <code>IQ</code> containing the query results.
	 *
	 * @availability Flash Player 7
	 * @param server The server to query for available service information
	 * @param callback The name of a callback function to call when results are retrieved
	 * @param scope The scope of the callback function
	 */
	public function getServiceInfo(server:String, callback:String, scope:Object):Void
	{
		var iq = new IQ(server, IQ.GET_TYPE);
		iq.callbackName = callback;
		iq.callbackScope = scope;
		iq.addExtension(new InfoDiscoExtension(iq.getNode()));
		connection.send(iq);
	}

	/**
	 * Retrieves a list of available services items from the server specified. Items include things such
	 * as available transports and user directories. On successful query, the callback specified in the will be 
	 * called and passed a single parameter containing the query results.
	 *
	 * @availability Flash Player 7
	 * @param server The server to query for service items
	 * @param callback The name of a callback function to call when results are retrieved
	 * @param scope The scope of the callback function
	 */
	public function getServiceItems(server:String, callback:String, scope:Object):Void
	{
		var iq = new IQ(server, IQ.GET_TYPE);
		iq.callbackName = callback;
		iq.callbackScope = scope;
		iq.addExtension(new ItemDiscoExtension(iq.getNode()));
		connection.send(iq);
	}

	/**
	 * Use the BrowseExtension (jabber:iq:browse namespace) to query a resource for supported features and children.
	 *
	 * @availability Flash Player 7
	 * @param id The full JabberID to query for service items
	 * @param callback The name of a callback function to call when results are retrieved
	 * @param scope The scope of the callback function
	 */
	public function browseItem(id:String, callback:String, scope:Object)
	{
		var iq = new IQ(id, IQ.GET_TYPE);
		iq.callbackName = callback;
		iq.callbackScope = scope;
		iq.addExtension(new BrowseExtension(iq.getNode()));
		connection.send(iq);
	}

	/**
	 * The instance of the XMPPConnection class to use for sending and receiving data.
	 *
	 * @availability Flash Player 7
	 */
	public function get connection():XMPPConnection { return _connection; }
	public function set connection(val:XMPPConnection):Void { _connection=val; }
}

