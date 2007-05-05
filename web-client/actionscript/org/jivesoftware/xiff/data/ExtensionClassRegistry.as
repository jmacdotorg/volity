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
 * This is a static class that contains class constructors for all extensions that could come from the network.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @availability Flash Player 7
 * @toc-path Data
 * @toc-sort 1
 */
class org.jivesoftware.xiff.data.ExtensionClassRegistry
{
	private static var myClasses:Array = new Array();
	
	public static function register( extensionClass:Function ):Boolean
	{
		var dummyXML:XML = new XML();
		var extensionInstance:IExtension = new extensionClass(dummyXML);

		if (extensionInstance instanceof IExtension) {
			myClasses[extensionInstance.getNS()] = extensionClass;
			return true;
		}
		return false;
	}
	
	public static function lookup( namespace:String ):Function
	{
		return myClasses[namespace];
	}
}
