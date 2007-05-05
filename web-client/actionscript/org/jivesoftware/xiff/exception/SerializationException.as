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

/**
 * This exception is thrown whenever there is a problem serializing or deserializing
 * data for sending to the server.
 *
 * @availability Flash Player 7
 * @author Sean Voisen
 * @since 2.0.0
 * @toc-path Exceptions
 * @toc-sort 1
 */
class org.jivesoftware.xiff.exception.SerializationException extends Error
{
	private static var MSG:String = "Could not properly serialize/deserialize stanza."
	
	public function SerializationException()
	{
		super( MSG );
	}
}

