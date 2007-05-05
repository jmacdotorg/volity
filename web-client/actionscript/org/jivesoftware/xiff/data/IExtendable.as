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

/**
 * This interface provides access to contained extensions and methods to modify the contained extensions.  
 * All XMPP stanzas that can be extended should implement this interface.
 *
 * @author Sean Voisen
 * @since 2.0.0
 * @see org.jivesoftware.xiff.data.ExtensionContainer
 * @see org.jivesoftware.xiff.data.IExtension
 * @availability Flash Player 7
 * @toc-path Interfaces
 * @toc-sort 1
 */
interface org.jivesoftware.xiff.data.IExtendable
{
	public function addExtension( extension:IExtension ):IExtension;
	public function getAllExtensionsByNS( namespace:String ):Array;
	public function getAllExtensions():Array;
	public function removeExtension( extension:IExtension ):Boolean;
	public function removeAllExtensions( namespace:String ):Void;
}
