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
 * Lesser General Public License for more details.s
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA 
 *
 */

/**
 * An extension of the XMLSocket that allows delegation of event handling to a listener
 * class instead of using the default callback implementation.
 *
 * @author Sean Voisen
 * @since 2.0.0
 * @availability Flash Player 6
 * @exclude
 * @toc-path Utility
 * @toc-sort 1
 */
class org.jivesoftware.xiff.utility.ListenerXMLSocket extends XMLSocket
{
	private var _listener;
	
	public function ListenerXMLSocket()
	{		
		onConnect = dispatchOnConnect;
		onData = dispatchOnData;
		onClose = dispatchOnClose;
	}
	
	/**
	 * Sets the instance to be used as a listener for socket events.
	 *
	 * @param aListener The event listener. This class should implement the following methods:
	 * <code>socketConnected</code>, <code>socketReceivedData</code>, and <code>socketClosed</code>.
	 * @availability Flash Player 6
	 */
	public function setListener( aListener:Object ):Void
	{
		_listener = aListener;
	}
	
	private function dispatchOnConnect( success ):Void
	{
    
		_listener.socketConnected( success );
	}
	
	private function dispatchOnData( data ):Void
	{
		_listener.socketReceivedData( data );
	}
	
	private function dispatchOnClose():Void
	{
		_listener.socketClosed();
	}
  
  
}
