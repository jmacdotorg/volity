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
import org.jivesoftware.xiff.conference.Room;
import org.jivesoftware.xiff.data.Message;
import org.jivesoftware.xiff.data.muc.MUCUserExtension;

/**
 * Broadcast when an invite has been received to the connection user
 *
 * The event object has the following properties:
 *
 * <code>from</code> - the JID of the user initiating the invite
 *
 * <code>reason</code> - a string containing the reason to join the room
 *
 * <code>room</code> - a Room instance of an unjoined room.  Finish the configuration of the
 * room by adding your nickname then join to accept the invitation, or call the
 * <code>decline</code> method of this room instance.  If you join this room, remember
 * to keep a reference to it, and add your event listeners.
 *
 * <code>data</code> - the original message containing the invite request
 *
 * @see org.jivesoftware.xiff.conference.Room
 * @see org.jivesoftware.xiff.conference.Room.invite
 * @availability Flash Player 7
 */
[Event("invited")]

/**
 * Manages the broadcasting of events during invitations.  Add event
 * listeners to an instance of this class to monitor invite and decline
 * events
 *
 * You only need a single instance of this class to listen for all invite
 * or decline events.
 *
 * @since 2.0.0
 * @author Sean Treadway
 * @param connection An XMPPConnection instance that is providing the primary server connection
 * @toc-path Conferencing
 * @toc-sort 1
 */
class org.jivesoftware.xiff.conference.InviteListener
{
	private var myConnection:XMPPConnection;
	
	// These are needed by the EventDispatcher
	private var dispatchEvent:Function;
	public var removeEventListener:Function;
	public var addEventListener:Function;
	
	// Used for static constructor with EventDispatcher and DataProvider
	private static var staticConstructorDependencies = [ mx.events.EventDispatcher ]

	private static var isEnabled:Boolean = InviteListener.enable();
	
	public function InviteListener( aConnection:XMPPConnection )
	{
		setConnection( aConnection );	
	}
	
	private static function enable():Boolean
	{
		mx.events.EventDispatcher.initialize( InviteListener.prototype );
		return true;
	}
	
	/**
	 * Sets a reference to the XMPPConnection being used for incoming/outgoing XMPP data.
	 *
	 * @param connection The XMPPConnection instance to use.
	 * @availability Flash Player 7
	 * @see org.jivesoftware.xiff.core.XMPPConnection
	 */
	public function setConnection( connection:XMPPConnection ):Void
	{
        myConnection.removeEventListener("message", this);
		myConnection = connection;
	 	myConnection.addEventListener("message", this);
	}

	/**
	 * Gets a reference to the XMPPConnection being used for incoming/outgoing XMPP data.
	 *
	 * @returns The XMPPConnection used
	 * @availability Flash Player 7
	 * @see org.jivesoftware.xiff.core.XMPPConnection
	 */
	public function getConnection():XMPPConnection
	{
		return myConnection;
	}
	 
	private function handleEvent( eventObj ):Void
	{
		switch( eventObj.type )
		{
			case "message":
				var msg:Message = eventObj.data;
                var muc:MUCUserExtension = msg.getAllExtensionsByNS(MUCUserExtension.NS)[0];

                if (muc != null) {
                    if (muc.type == MUCUserExtension.INVITE_TYPE) {
                        var room:Room = new Room(myConnection);
                        room.setRoomJID(msg.from);
                        room.password = muc.password;

                        dispatchEvent({
                            type:"invited", 
                            target:this, 
                            from: muc.from,
                            reason: muc.reason,
                            room: room,
                            data: msg
                        });
                    }
                }
				break;
		}
	}
}
