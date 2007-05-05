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
import org.jivesoftware.xiff.data.XMPPStanza;

/**
 * A class for abstraction and encapsulation of IQ (info-query) data.
 * 
 * @author Sean Voisen
 * @since 2.0.0
 * @availability Flash Player 7
 * @param recipient The JID of the IQ recipient
 * @param sender The JID of the IQ sender - the server should report an error if this is falsified
 * @param iqType The type of the IQ - there are static variables declared for each type
 * @param iqID The unique ID of the IQ
 * @param iqCallback The function to be called when the server responds to the IQ
 * @param iqCallbackScope The object instance containing the callback method
 * @toc-path Data
 * @toc-sort 1
 */
class org.jivesoftware.xiff.data.IQ extends XMPPStanza implements ISerializable
{
	private var myCallback:String;
	private var myCallbackScope:Object;
    private var myCallbackFunc:Function;

	private var myQueryName:String;
	private var myQueryFields:Array;
	
	// Static variables for specific type strings
	public static var SET_TYPE:String = "set";
	public static var GET_TYPE:String = "get";
	public static var RESULT_TYPE:String = "result";
	public static var ERROR_TYPE:String = "error";

	public function IQ( recipient:String, iqType:String, iqID:String, iqCallback:String, iqCallbackScope:Object, iqCallbackFunc:Function )
	{
		// Flash gives a warning if superconstructor is not first, hence the inline id check
		super( recipient, null, iqType, exists( iqID ) ? iqID : generateID("iq_"), "iq" );
		
		callbackName = iqCallback;
		callbackScope = iqCallbackScope;
        callback = iqCallbackFunc;
	}

	/**
	 * Serializes the IQ into XML form for sending to a server.
	 *
	 * @return An indication as to whether serialization was successful
	 * @availability Flash Player 7
	 */
	public function serialize( parentNode:XMLNode ):Boolean
	{
		return super.serialize( parentNode );
	}
	
	/**
	 * Deserializes an XML object and populates the IQ instance with its data.
	 *
	 * @param xmlNode The XML to deserialize
	 * @return An indication as to whether deserialization was sucessful
	 * @availability Flash Player 7
	 */
	public function deserialize( xmlNode:XMLNode ):Boolean
	{
		return super.deserialize( xmlNode );
	}
	
	/**
	 * The function that will be called when an IQ result or error 
	 * is received with the same ID as one you send.  The function will
     * be called in the scope of the IQ, so if you wish to have this
     * called with the scope of your class wrap your function with a
     * mx.utils.Delegate class.
     *
     * If both <code>callbackName/callbackScope</code> and callback are 
     * set then both functions will be called.
     *
     * This is an alternative to the <code>callbackName/callbackScope</code> 
     * method of receiving callbacks.
     *
     * Callback functions take one parameter which will be the IQ instance
     * received from the server.
     *
     * This isn't a required property, but is useful if you
	 * need to respond to server responses to an IQ.
	 *
	 * @see #callbackScope
	 * @see #callbackName
	 * @availability Flash Player 7
	 */
    public function get callback():Function
    {
        return myCallbackFunc;
    }

    public function set callback( aFunc:Function ):Void
    {
        myCallbackFunc = aFunc;
    }

	/**
	 * The name of the callback function to call when a response to the IQ
	 * is received. This isn't a required property, but is useful if you
	 * need to respond to server responses to an IQ.
	 *
	 * @availability Flash Player 7
	 * @see #callbackScope
     * @see #callback
	 */
	public function get callbackName():String
	{
		return myCallback;
	}
	
	public function set callbackName( aName:String ):Void
	{
		myCallback = aName;
	}
	
	/**
	 * The scope of the callback function to call when a response to the IQ
	 * is received. This isn't a required property, but is useful if you
	 * need to respond to server responses to an IQ.
	 *
	 * @availability Flash Player 7
	 * @see #callbackName
     * @see #callback
	 */
	public function get callbackScope():Object
	{
		return myCallbackScope;
	}
	
	public function set callbackScope( scope:Object ):Void
	{
		myCallbackScope = scope;
	}
}
