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
import org.jivesoftware.xiff.data.XMLStanza;

/**
 * This is a base class for all data extensions.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @availability Flash Player 7
 * @param parent The parent node that this extension should be appended to
 * @toc-path Data/Base Classes
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.Extension extends XMLStanza
{
	public function Extension(parent:XMLNode)
	{
		super();

		getNode().nodeName = IExtension(this).getElementName();
		getNode().attributes.xmlns = IExtension(this).getNS();

		if (exists(parent)) {
			parent.appendChild(getNode());
		}
	}

	/**
	 * Removes the extension from its parent.
	 *
	 * @availability Flash Player 7
	 */
	public function remove():Void
	{
		getNode().removeNode();
	}
	
	/**
	 * Converts the extension stanza XML to a string.
	 *
	 * @availability Flash Player 7
	 * @return The extension XML in string form
	 */
	public function toString():String
	{
		return getNode().toString();
	}
}
