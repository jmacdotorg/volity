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

import org.jivesoftware.xiff.data.XMPPStanza;
import org.jivesoftware.xiff.data.IQ;
import org.jivesoftware.xiff.data.Message;
import org.jivesoftware.xiff.data.Presence;
import org.jivesoftware.xiff.data.auth.AuthExtension;
import org.jivesoftware.xiff.data.register.RegisterExtension;
import org.jivesoftware.xiff.data.ExtensionClassRegistry;

import org.jivesoftware.xiff.exception.SerializationException;

import org.jivesoftware.xiff.utility.ListenerXMLSocket;

import org.volity.Base64;
/*
 * NOTE: Jabber error codes are deprecated in IETF XMPP.
 * XIFF supports both Jabber codes and XMPP error conditions.
 * A list of error conditions is available at: http://www.jabber.org/jeps/jep-0086.html
 */

/**
 * Broadcast when the user is disconnected from the server.
 *
 * @availability Flash Player 7
 * @see #disconnect
 * @example The following example traces out a message when there has been a disconnection.
 * <pre>listener = new Object();
 * listener.disconnection = function( eventObject ) {
 *     trace( "There has been a disconnection." );
 * }
 * myXMPPConnection.addEventListener( "disconnection", listener );
 * </pre>
 */
[Event("disconnection")]

/**
 * Broadcast when a password change on the server is successful. Password changes are initiated using the <code>changePassword</code> method.
 *
 * @availability Flash Player 7
 * @see #changePassword
 * @example The following example waits for a change password success, and updates the password stored in the XMPPConnection class, in case
 * the same class instance is used for future connections.
 * <pre>var newPasswd:String = "12345";
 * listener = new Object();
 * listener.changePasswordSuccess = function( eventObject ) {
 *     trace( "Password changed!" );
 *     myXMPPConnection.password = newPasswd;
 * }
 * myXMPPConnection.addEventListener( "changePasswordSuccess", listener );
 *
 * myXMPPConnection.changePassword( newPasswd );
 * </pre>
 */
[Event("changePasswordSuccess")]

/**
 * Broadcast when the data required for registration is received. This data is requested using the getRegistrationFields() method.
 * The event object contains a member "fields" that is an array of strings representing each field.
 *
 * @availability Flash Player 7
 * @see #getRegistrationFields
 */
[Event("registrationFields")]

/**
 * Broadcast when registration of a new account on the server is successful. You can register a new account using the
 * <code>sendRegistrationFields()</code> method.
 * 
 * @availability Flash Player 7
 * @see #sendRegistrationFields
 */
[Event("registrationSuccess")]

/**
 * Broadcast when a connection to the server has successfully been made. Use the <code>connect()</code> method to connect
 * to the server.
 *
 * @availability Flash Player 7
 * @see #connect
 */
[Event("connection")]

/**
 * Broadcast when the XMPPConnection receives data from the server. The event object contains an attribute <code>data</code> with
 * the XML data (as an instance of an XML object) received from the server. This event is primarily useful for debugging purposes.
 *
 * @availability Flash Player 7
 * @example This example traces out all incoming XML data from the server.
 * <pre>listener = new Object();
 * listener.incomingData = function( eventObject ) {
 *     trace( "INCOMING: " + eventObject.data );
 * }
 * myXMPPConnection.addEventListener( "incomingData", listener );
 * </pre>
 */
[Event("incomingData")]

/**
 * Broadcast whenever there is an update to the roster. This is primarily used by the <code>Roster</code> class to get updates and
 * process them accordingly. The event object contains an attribute <code>data</code> with an instance of an <code>IQ</code> containing the
 * entire update stanza.
 *
 * @availability Flash Player 7
 */
[Event("rosterUpdate")]

/**
 * Broadcast whenever a message is received. The event object contains an attribute <code>data</code> with an instance of the <code>Message</code>
 * class containing the message data.
 *
 * @availability Flash Player 7
 * @see org.jivesoftware.xiff.data.Message
 * @example This example traces out all incoming messages.
 * <pre>listener = new Object();
 * listener.message = function( eventObject ) {
 *    trace( eventObject.data.body );
 * }
 * myXMPPConnection.addEventListener( "message", listener );
 * </pre>
 */
[Event("message")]

/**
 * Broadcast whenever a presence XMPP stanza is received. The event object contains an attribute <code>data</code> with an instance of the
 * <code>Presence</code> class containing the presence data.
 *
 * @availability Flash Player 7
 * @see org.jivesoftware.xiff.data.Presence
 */
[Event("presence")]

/**
 * Broadcast whenever an error is returned by the server or the library. For more information on errors and XMPP error conditions,
 * take a look at <a href="http://www.jabber.org/jeps/jep-0086.html">JEP-0086</a>. The event object contains several attributes:
 * <ul>
 * <li>errorCondition - The XMPP condition of the error.</li>
 * <li>errorMessage - The message for the error as given by the server.</li>
 * <li>errorType - The error type.</li>
 * <li>errorCode - The (deprecated) Jabber error code. Error codes are still used by Jabber servers like Jabberd 1.4, but are deprecated by the official XMPP standard.</li>
 * </ul>
 *
 * @availability Flash Player 7
 * @example This example simply traces out relevant error information when an error is received.
 * <pre>listener = new Object();
 * listener.error = function( eventObject ) {
 *     trace( "ERROR " + eventObject.errorCode + ": " + eventObject.errorMessage );
 * }
 * myXMPPConnection.addEventListener( "error", listener );
 * </pre>
 */
[Event("error")]

/**
 * Broadcast whenever data is sent to the server. The event object contains an attribute <code>data</code> with the XML data being
 * sent (as an instance of the XML object). This is primarily useful for debugging.
 *
 * @availability Flash Player 7
 * @example This example traces out all outgoing XML data from the client.
 * <pre>listener = new Object();
 * listener.outgoingData = function( eventObject ) {
 *     trace( "OUTGOING: " + eventObject.data );
 * }
 * myXMPPConnection.addEventListener( "outgoingData", listener );
 * </pre>
 */
[Event("outgoingData")]

/* debugging only */
[Event("beginAuth")]

/**
 * Connects to an XMPP server and manages incoming/outgoing data from that server.
 * The XMPPConnection class is the core of the XIFF Library, as all else relies on this class
 * to communicate with the server. For an in-depth look at the XMPP protocol core, take a look
 * at the <a href="http://www.jabber.org/ietf/draft-ietf-xmpp-core-23.txt">IETF draft for the
 * XMPP Core</a>. Usually, you will have one instance of the XMPPConnection class for each
 * connection to a server.
 *
 * @author Sean Voisen
 * @since 2.0.0
 * @toc-path Core
 * @toc-sort 1
 */
class org.jivesoftware.xiff.core.XMPPConnection
{
	
	private var _useAnonymousLogin:Boolean;
	
	private var socket:ListenerXMLSocket;
	
	private var myServer:String;
	private var myUsername:String;
	private var myResource:String;
	private var myPassword:String;
	private var myJID:String;

	private var myPort:Number;
	private var active:Boolean;
	private var loggedIn:Boolean;
	private var ignoreWhitespace:Boolean;
	
	private var openingStreamTag;
	private var closingStreamTag;
	
	private var sessionID:String;
	private var pendingIQs;

	// The following are needed by the EventDispatcher
	private var dispatchEvent:Function;
	
	/**
	 * Used to remove instances as listeners for certain events. Event dispatching is handled using
	 * Macromedia's EventDispatcher class found in mx.events.EventDispatcher.
	 *
	 * @availability Flash Player 7
	 * @param event The name of the event that should no longer be listened to
	 * @param handler The event listener that should stop listening
	 */
	public function removeEventListener( event:String, handler ):Void {};
	
	/**
	 * Used to add listeners for certain events. Event dispatching is handled using Macromedia's
	 * EventDispatcher class found in mx.events.EventDispatcher. Listeners can either implement a method
	 * with the same name as the event they are listening to, or a method called <code>handleEvent</code>
	 * that will handle all incoming event broadcasts. If <code>handleEvent</code> is implemented, the method
	 * can examine the event object's <code>type</code> attribute to find out which type of event occured.
	 *
	 * @availability Flash Player 7
	 * @param event The name of the event to listen to
	 * @param handler The class instance that is listening for events
	 * @example This example adds a class instance called "listener" as a listener for all <code>incomingData</code> events.
	 * <pre>var listener = new Object();
	 * listener.handleEvent = function( eventObject ) {
	 *     if( eventObject.type == "incomingData" ) {
	 *         trace( "INCOMING: " + eventObject.data );
	 *     }
	 * }
	 * myXMPPConnection.addEventListener( "incomingData", listener );
	 * </pre>
	 */
	public function addEventListener( event:String, handler ):Void {};
	
	// End EventDispatcher decorated methods
	
	// These are used by the static constructor
	private static var staticConstructorDependency = [ mx.events.EventDispatcher, XMPPStanza, AuthExtension, RegisterExtension, ExtensionClassRegistry ];
	private static var xmppConnectionStaticConstructed:Boolean = XMPPConnectionStaticConstructor();
	
	public function XMPPConnection()
	{	
		// Create the socket
		socket = new ListenerXMLSocket();
		socket.setListener( this );
		
		// Hash to hold callbacks for IQs
		pendingIQs = new Object();
		
		_useAnonymousLogin = false;
		active = false;
		loggedIn = false;
		ignoreWhitespace = true;
		resource = "xiff";
		port = 5222;
	}
	
	// Static constructor
	private static function XMPPConnectionStaticConstructor():Boolean
	{
		// Add event dispatch capabilities
		mx.events.EventDispatcher.initialize( XMPPConnection.prototype );

		// Add extensions that we recognize
		AuthExtension.enable();
		RegisterExtension.enable();
		
		return true;
	}
	
	/**
	 * Connects to the server.
	 *
	 * @availability Flash Player 7
	 * @param streamType (Optional) The type of initial stream negotiation, either &lt;flash:stream&gt; or &lt;stream:stream&gt;. 
	 * Some servers, like Jabber, Inc.'s XCP and Jabberd 1.4 expect &lt;flash:stream&gt; from a Flash client instead of the standard &lt;stream:stream&gt;.
	 * The options for this parameter are: "flash", "terminatedFlash", "standard" and "terminatedStandard". The default is "terminatedStandard".
	 *
	 * @return A boolean indicating whether the server was found.
	 */
	public function connect( streamType:String ):Boolean
	{
		active = false;
		loggedIn = false;
		
		// Stream type lets user set opening/closing tag - some servers (jadc2s) prefer <stream:flash> to the standard
		// <stream:stream>
		switch( streamType ) {
			case "flash":
				openingStreamTag = new String( "<?xml version=\"1.0\"?><flash:stream to=\"" + server + "\" xmlns=\"jabber:client\" xmlns:flash=\"http://www.jabber.com/streams/flash\" version=\"1.0\">" );
				closingStreamTag = new String( "</flash:stream>" );
				break;
				
			case "terminatedFlash":
				openingStreamTag = new String( "<?xml version=\"1.0\"?><flash:stream to=\"" + server + "\" xmlns=\"jabber:client\" xmlns:flash=\"http://www.jabber.com/streams/flash\" version=\"1.0\" />" );
				closingStreamTag = new String( "</flash:stream>" );
				break;
				
			case "standard":
				openingStreamTag = new String( "<?xml version=\"1.0\"?><stream:stream to=\"" + server + "\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">" );
				closingStreamTag = new String( "</stream:stream>" );
				break;
		
			case "terminatedStandard":
			default:
				openingStreamTag = new String( "<?xml version=\"1.0\"?><stream:stream to=\"" + server + "\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" />" );
				closingStreamTag = new String( "</stream:stream>" );
				break;
		}
		
		// Throws error if connect returns false (no connection)
		if( !socket.connect( server, port ) ) {
			dispatchError( "remote-server-not-found", "Server Not Found", "cancel", 404 );
      return false;
		}
		else {
			return true;
		}
	}
	
	/**
	 * Disconnects from the server if currently connected. After disconnect, a <code>disconnection</code> event is broadcast.
	 *
	 * @availability Flash Player 7
	 * @see #disconnection
	 */
	public function disconnect():Void
	{
		if( isActive() ) {
			sendXML( closingStreamTag );
			socket.close();
			active = false;
			loggedIn = false;
			
			var eventObj:Object = {target:this, type:"disconnection"};
			dispatchEvent( eventObj );
		}
	}
	
	/**
	 * Sends data to the server. If the data to send cannot be serialized properly, this method throws a SerializeException.
	 *
	 * @availability Flash Player 7
	 * @param o The data to send. This must be an instance of a class that implements the ISerializable interface.
	 * @see org.jivesoftware.xiff.data.ISerializable
	 * @example The following example sends a basic chat message to the user with the JID "sideshowbob@springfieldpenitentiary.gov".
	 * <pre>var msg:Message = new Message( "sideshowbob@springfieldpenitentiary.gov", null, "Hi Bob.", "<b>Hi Bob.</b>", Message.CHAT_TYPE );
	 * myXMPPConnection.send( msg );</pre>
	 */
	public function send( o:XMPPStanza ):Void
	{
		if( isActive() ) {
			if( o instanceof IQ ) {
				// Add reference to callback to pending
                var iq:IQ = IQ(o);
				addIQCallbackToPending( iq.id, iq.callbackName, iq.callbackScope, iq.callback );
			}
			
			var root:XMLNode = o.getNode().parentNode;

			if (root == null || root == undefined) {
				root = new XML();
			}

			if (o.serialize(root)) {
				sendXML( root.firstChild );
			} else {
				throw new SerializationException();
			}
		}
	}
	
	/**
	 * Determines whether the connection with the server is currently active. (Not necessarily logged in.
	 * For login status, use the <code>isLoggedIn()</code> method.)
	 * 
	 * @availability Flash Player 7
	 * @return A boolean indicating whether the connection is active.
	 * @see org.jivesoftware.xiff.core.XMPPConnection#isLoggedIn
	 */
	public function isActive():Boolean
	{
		return active;
	}
	
	public function dummyAuthStr():String
	{
		var thegoods:String = username + "@" + server + "\0";
		thegoods += username + "\0" + password;
		return thegoods;
	}
	
	/**
	 * Determines whether the user is connected and logged into the server.
	 * 
	 * @availability Flash Player 7
	 * @return A boolean indicating whether the user is logged in.
	 * @see org.jivesoftware.xiff.core.XMPPConnection#isActive
	 */
	public function isLoggedIn():Boolean
	{
		return loggedIn;
	}
	
	/**
	 * Issues a request for the information that must be submitted for registration with the server.
	 * When the data returns, an event of type "registrationFields" is broadcast containing the requested data.
	 *
	 * @availability Flash Player 7
	 */
	public function getRegistrationFields():Void
	{
		var regIQ:IQ = new IQ( server, IQ.GET_TYPE, XMPPStanza.generateID("reg_info_"), "getRegistrationFields_result", this );
		regIQ.addExtension(new RegisterExtension(regIQ.getNode()));

		send( regIQ );
	}
	
	/**
	 * Registers a new account with the server, sending the registration data as specified in the fieldMap paramter.
	 *
	 * @param fieldMap An object map containing the data to use for registration. The map should be composed of attribute:value pairs for each registration data item.
	 * @param key (Optional) If a key was passed in the "data" field of the "registrationFields" event, that key must also be passed here.
	 * required field needed for registration.
	 * @availability Flash Player 7
	 */
	public function sendRegistrationFields( fieldMap:Object, key:String ):Void
	{
		var regIQ:IQ = new IQ( server, IQ.SET_TYPE, XMPPStanza.generateID("reg_attempt_"), "sendRegistrationFields_result", this );
		var ext:RegisterExtension = new RegisterExtension(regIQ.getNode());

		for( var i in fieldMap ) {
			ext[i] = fieldMap[i];
		}

		if (key != null && key != undefined) {
			ext.key = key;
		}

		regIQ.addExtension(ext);
		
		send( regIQ );
	}
	
	/**
	 * Changes the user's account password on the server. If the password change is successful, the class will broadcast a <code>changePasswordSuccess</code> event.
	 *
	 * @param newPassword The new password
	 * @availability Flash Player 7
	 * @see #changePasswordSuccess
	 * @example The following example waits for a change password success, and updates the password stored in the XMPPConnection class, in case
	 * the same class instance is used for future connections.
	 * <pre>var newPasswd:String = "12345";
	 * listener = new Object();
	 * listener.changePasswordSuccess = function( eventObject ) {
	 *     trace( "Password changed!" );
	 *     myXMPPConnection.password = newPasswd;
	 * }
	 * myXMPPConnection.addEventListener( "changePasswordSuccess", listener );
	 *
	 * myXMPPConnection.changePassword( newPasswd );
	 * </pre>
	 */
	public function changePassword( newPassword:String ):Void
	{
		var passwdIQ:IQ = new IQ( server, IQ.SET_TYPE, XMPPStanza.generateID("pswd_change_"), "changePassword_result", this );
		var ext:RegisterExtension = new RegisterExtension(passwdIQ.getNode());

		ext.username = getBareJID();
		ext.password = newPassword;

		passwdIQ.addExtension(ext);
		send( passwdIQ );
	}
	
	/**
	 * Gets the fully qualified JID (user@server/resource) of the user. A fully-qualified JID includes the resource. A bare JID does not.
	 * To get the bare JID, use the <code>getBareJID()</code> method.
	 *
	 * @return The fully qualified JID
	 * @see #getBareJID
	 * @availability Flash Player 7
	 */
	public function getJID():String
	{
		return myJID;
	}
	
	/**
	 * Gets the bare JID (non-fully qualified: user@server) of the user. The bare JID does not include the resource.
	 *
	 * @return The bare JID
	 * @see #getJID
	 * @availability Flash Player 7
	 */
	public function getBareJID():String
	{
		// NOTE: what happens with anonymous login?
		return getJID().split( "/" )[0];
	}
	
	private function changePassword_result( resultIQ:IQ ):Void
	{
		if( resultIQ.type == IQ.RESULT_TYPE ) {
			var eventObj:Object = {target:this, type:"changePasswordSuccess"};
			dispatchEvent( eventObj );
		}
		else {
			// We weren't expecting this
			dispatchError( "unexpected-request", "Unexpected Request", "wait", 400 );
		}
	}
	
	private function getRegistrationFields_result( resultIQ:IQ ):Void
	{
		var ext:RegisterExtension = resultIQ.getAllExtensionsByNS(RegisterExtension.NS)[0];
		dispatchEvent({
			target:this,
			type: "registrationFields",
			fields: ext.getRequiredFieldNames(),  // TODO, phase this out
			data: ext
		});
	}
	
	private function sendRegistrationFields_result( resultIQ:IQ ):Void
	{
		if( resultIQ.type == IQ.RESULT_TYPE ) {
			var eventObj:Object = {target:this, type:"registrationSuccess"};
			dispatchEvent( eventObj );
		}
		else {
			// We weren't expecting this
			dispatchError( "unexpected-request", "Unexpected Request", "wait", 400 );
		}
	}
	
	// Listener function from the ListenerXMLSocket
	private function socketConnected( success ):Void
	{
		if( success ) {
			sendXML( openingStreamTag );
			active = true;
			
			var eventObj:Object = {target:this, type:"connection"};
			dispatchEvent( eventObj );
		}
		else {
			dispatchError( "service-unavailable", "Service Unavailable", "cancel", 503 );
		}
	}
	
	// Listener function from the ListenerXMLSocket
	private function socketReceivedData( data ):Void
	{
		var xmlData:XML = new XML();
		xmlData.ignoreWhite = this.ignoreWhite;
		xmlData.parseXML( data );
		
		var eventObj:Object = {target:this, type:"incomingData", data:data}
		dispatchEvent( eventObj );
		
		// Read the data and send it to the appropriate parser
		var firstNode:XMLNode = xmlData.firstChild;
		var nodeName:String = firstNode.nodeName.toLowerCase();
		
		switch( nodeName )
		{
			case "stream:stream":
			case "flash:stream":
				handleStream( firstNode );
				break;
		  
		  case "stream:features":
				if (loggedIn) {
						  bindResource();
				}
				else
				{
				  beginSASLAuthentication();
				}
			  break;
					
			case "success":
					loggedIn = true;
          SASLAuthRestartStream();
					break;
					
			case "stream:error":
				handleStreamError( firstNode );
				break;
				
			case "iq":
				handleIQ( firstNode );
				break;
				
			case "message":
				handleMessage( firstNode );
				break;
				
			case "presence":
				handlePresence( firstNode );
				break;
				
			default:
				// silently ignore lack of or unknown stanzas
				// if the app designer wishes to handle raw data they
				// can on "incomingData".

				// Use case: received null byte, XMLSocket parses empty document
				// sends empty document

				// dispatchError( "undefined-condition", "Unknown Error", "modify", 500 );
				break;
		}
	}
	
	private function socketClosed():Void
	{
		var eventObj:Object = {target:this, type:"disconnection"};
		dispatchEvent( eventObj );
	}
	
	private function handleStream( node:XMLNode ):Void
	{
		sessionID = node.attributes.id;
		
		if(_useAnonymousLogin) {
			// Begin anonymous login
			sendAnonymousLogin();
		} else if( username != null && username.length > 0 ) {
			// Begin login sequence
			//beginAuthentication();
		} else {
			//get registration fields
			getRegistrationFields();
		}
	}
	
	private function handleStreamError( node:XMLNode ):Void
	{
		dispatchError( "service-unavailable", "Remote Server Error", "cancel", 502 );
		
		// Cancel everything by closing connection
		socket.close();
		active = false;
		loggedIn = false;
			
		var eventObj:Object = {target:this, type:"disconnection"};
		dispatchEvent( eventObj );
	}
	
	private function handleIQ( node:XMLNode ):IQ
	{
		var iq:IQ = new IQ();
		
		// Populate the IQ with the incoming data
		if( !iq.deserialize( node ) ) {
			throw new SerializationException();
		}
		
		// If it's an error, handle it
		if( iq.type == IQ.ERROR_TYPE ) {
			dispatchError( iq.errorCondition, iq.errorMessage, iq.errorType, iq.errorCode );
		}
		else {
			// Start the callback for this IQ if one exists
			if( pendingIQs[iq.id] !== undefined ) {
				var callbackInfo = pendingIQs[iq.id];
				callbackInfo.methodScope[callbackInfo.methodName].apply( callbackInfo.methodScope, [iq] );
				callbackInfo.func( iq );
				delete pendingIQs[iq.id];
			}
			else {
				var exts:Array = iq.getAllExtensions();
				for (var ns in exts) {
					// Static type casting
					var ext:IExtension = IExtension(exts[ns]);
					if (ext != null) {
						dispatchEvent({
							type: ext.getNS(),
							target: this,
							data: ext,
							iq: iq
						});
					}
				}
			}
		}
        return iq;
	}
	
	private function handleMessage( node:XMLNode ):Message
	{
		var msg:Message = new Message();
		
		// Populate with data
		if( !msg.deserialize( node ) ) {
			throw new SerializationException();
		}
		
		var eventObj:Object = {target:this, type:"message", data:msg};
		dispatchEvent( eventObj );

        return msg;
	}
	
	private function handlePresence( node:XMLNode ):Presence
	{
		var pres:Presence = new Presence();
		
		// Populate
		if( !pres.deserialize( node ) ) {
			throw new SerializationException();
		}
		
		var eventObj:Object = {target:this, type:"presence", data:pres};
		dispatchEvent( eventObj );

        return pres;
	}
	
	private function updateJID():Void
	{
		// Update the user ID with the latest server/user information
		if(!_useAnonymousLogin) {
			myJID = username + "@" + server + "/" + resource;
		} else {
			myJID = server + "/" + resource;
		}
	}
	
	private function dispatchError( condition:String, message:String, type:String, code:Number )
	{
		var eventObj:Object = {target:this, type:"error", errorCondition:condition, errorMessage:message, errorType:type, errorCode:code};
		dispatchEvent( eventObj );
	}
	
	private function sendXML( someData ):Void
	{
		// Data is untyped because it could be a string or XML
		socket.send( someData );
		
		var eventObj:Object = {target:this, type:"outgoingData", data: someData.toString()};
		dispatchEvent( eventObj );
	}
	
	// anonymous login
	private function sendAnonymousLogin():Void {
		var anonIQ:IQ = new IQ(null, IQ.SET_TYPE, XMPPStanza.generateID("log_anom_"), "sendAnonymousLogin_result", this );
		var authExt:AuthExtension = new AuthExtension(anonIQ.getNode());
		anonIQ.addExtension(authExt);
		send(anonIQ);
	}
	
	private function sendAnonymousLogin_result(resultIQ:IQ):Void {
		if( resultIQ.type == IQ.RESULT_TYPE ) {
			// update resource
			var resultAuth = resultIQ.getAllExtensionsByNS(AuthExtension.NS)[0];
			resource = resultAuth.resource;
			// dispatch login event
			loggedIn = true;
			var eventObj:Object = {target:this, type:"login"};
			dispatchEvent( eventObj );
		}
	}
	
	private function beginAuthentication():Void
	{
		var authIQ:IQ = new IQ( null, IQ.GET_TYPE, XMPPStanza.generateID("log_user_"), "beginAuthentication_result", this );
		var authExt:AuthExtension = new AuthExtension(authIQ.getNode());
		authExt.username = username
		
		authIQ.addExtension(authExt);
		send( authIQ );
	}
	
	private function beginAuthentication_result( resultIQ:IQ ):Void
	{
		var connectionType:String = "none";

		// Begin authentication procedure
		if( resultIQ.type == IQ.RESULT_TYPE ) {
			var authIQ:IQ = new IQ( null, IQ.SET_TYPE, XMPPStanza.generateID("log_user2_"), "sendAuthentication_result", this );
			
			var resultAuth = resultIQ.getAllExtensionsByNS(AuthExtension.NS)[0];
			var responseAuth:AuthExtension = new AuthExtension(authIQ.getNode());

			if (resultAuth.isDigest()) {
				responseAuth.digest = AuthExtension.computeDigest(sessionID, password);
			} else if (resultAuth.isPassword()) {
				responseAuth.password = password;
			} else {
				// Connection method not supported
				dispatchError( "feature-not-implemented", "Feature Not Implemented", "cancel", 501 );
				return;
			}

			responseAuth.username = username;
			responseAuth.resource = resource;
			authIQ.addExtension(responseAuth);

			send( authIQ );
		}
		else {
			// We weren't expecting this
			dispatchError( "unexpected-request", "Unexpected Request", "wait", 400 );
		}
	}
	
  public function beginSASLAuthentication():Void
	{
		var thetag:String = "<auth mechanism=\"PLAIN\" xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">";
		thetag += "put some base64 tokenage here</auth>"; // FIXME
		sendXML(thetag);
	}
  
  private function SASLAuthRestartStream():Void
	{
    // send a new header yay
    sendXML( openingStreamTag );
	}
  
  private function bindResource():Void
	{
    var bindIQ:IQ = new IQ( null, IQ.SET_TYPE, "bind_1", "confirmBindAndMakeSession", this );
    
    var doc:XML = new XML("<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>");
    var bindNode:XMLNode = doc.firstChild;
    
    bindIQ.getNode().appendChild(bindNode);
    send(bindIQ);
	}
  
  private function confirmBindAndMakeSession(resultIQ:IQ):Void
	{
    if( resultIQ.type == IQ.RESULT_TYPE ) {
			var realthing:XMLNode = resultIQ.getNode();
      resource = realthing.toString().split("/")[1];
      
      // then do session
      var sessionIQ:IQ = new IQ( null, IQ.SET_TYPE, "session_1", "finalizeSession", this );
    
      var doc:XML = new XML("<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>");
      var sessionNode:XMLNode = doc.firstChild;
      
      sessionIQ.getNode().appendChild(sessionNode);
      send(sessionIQ);
		}
		else {
			// We weren't expecting this
			dispatchError( "unexpected-request", "Resource Binding Error", "wait", 400 );
		}
	}
  
  private function finalizeSession(resultIQ:IQ):Void
	{
		if( resultIQ.type == IQ.RESULT_TYPE ) {
      // huzzah. jeez
			var eventObj:Object = {target:this, type:"login"};
      dispatchEvent( eventObj );
		}
		else {
			// We weren't expecting this
			dispatchError( "unexpected-request", "Session Error", "wait", 400 );
		}
    
	}
  
	private function sendAuthentication_result( resultIQ:IQ ):Void
	{
		if( resultIQ.type == IQ.RESULT_TYPE ) {
			loggedIn = true;

			var eventObj:Object = {target:this, type:"login"};
			dispatchEvent( eventObj );
		}
		else {
			// We weren't expecting this
			dispatchError( "unexpected-request", "Unexpected Request", "wait", 400 );
		}
	}
	
	private function addIQCallbackToPending( id, callbackName, callbackScope, callbackFunc ):Void
	{
		pendingIQs[id] = {methodName:callbackName, methodScope:callbackScope, func:callbackFunc};
	}
	
	/**
	 * The XMPP server to use for connection.
	 *
	 * @availability Flash Player 7
	 */
	public function get server():String
	{
		return myServer;
	}
	
	public function set server( theServer:String ):Void
	{
		myServer = theServer;
		
		updateJID();
	}
	
	/**
	 * The username to use for connection. If this property is null when <code>connect()</code> is called,
	 * the class will fetch registration field data rather than attempt to login.
	 *
	 * @availability Flash Player 7
	 */
	function get username():String
	{
		return myUsername;
	}
	
	function set username( theUsername:String ):Void
	{
		myUsername = theUsername;
		
		updateJID();
	}
	
	/**
	 * The password to use when logging in.
	 *
	 * @availability Flash Player 7
	 */
	function get password():String
	{
		return myPassword;
	}
	
	function set password( thePassword:String ):Void
	{
		myPassword = thePassword;
	}
	
	/**
	 * The resource to use when logging in. A resource is required (defaults to "XIFF") and allows a user to login using the same account
	 * simultaneously (most likely from multiple machines). Typical examples of the resource include "Home" or "Office" to indicate
	 * the user's current location.
	 *
	 * @availability Flash Player 7
	 */
	public function get resource():String
	{
		return myResource;
	}
	
	public function set resource( theResource:String ):Void
	{
		if( theResource.length > 0 )
		{
			myResource = theResource;
		
			updateJID();
		}
	}
	
	
	public function get useAnonymousLogin():Boolean { return _useAnonymousLogin; }
	public function set useAnonymousLogin(value):Void {
		// set only if not connected
		if(!isActive()) _useAnonymousLogin = value;
	}
		
	
	/**
	 * The port to use when connecting. The default XMPP port is 5222.
	 *
	 * @availability Flash Player 7
	 */
	public function get port():Number
	{
		return myPort;
	}
	
	public function set port( portNum:Number ):Void
	{
		myPort = portNum;
	}

	/**
	 * Determines whether whitespace will be ignored on incoming XML data.
	 * Behaves the same as XML.ignoreWhite
	 *
	 * @availability Flash Player 7
	 */
	public function get ignoreWhite():Boolean
	{
		return ignoreWhitespace;
	}

	public function set ignoreWhite( val:Boolean ):Void
	{
		ignoreWhitespace = val;
	}
  
}
	 
