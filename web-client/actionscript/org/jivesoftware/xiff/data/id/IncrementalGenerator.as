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

import org.jivesoftware.xiff.data.id.IIDGenerator;

/**
 * Uses a simple incrementation of a static variable to generate new IDs.
 * Guaranteed to generate unique IDs for the duration of application execution.
 *
 * @author Sean Treadway
 * @availability Flash Player 7
 * @since 2.0.0
 * @toc-path Data/ID Generation
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.id.IncrementalGenerator implements IIDGenerator
{
	private var myCounter:Number;
	private static var instance:IIDGenerator;
	
	/**
	 *	ddura: I am turning this into a singleton, for more global access. I am
	 *  trying to work around a Flex bug. Not sure yet?
	 */
	static function getInstance():IIDGenerator
	{
		if(instance == null)
		{
			instance = new IncrementalGenerator();
		}
		
		return instance;
	}

	/**
	 * Initializes the counter to 0.
	 *
	 * @availability Flash Player 7
	 */
	private function IncrementalGenerator()
	{
		myCounter = 0;
	}

	/**
	 * Gets the unique ID.
	 *
	 * @param prefix The ID prefix to use when generating the ID
	 * @return The generated ID
	 * @availability Flash Player 7
	 */
	function getID(prefix:String):String
	{
		myCounter++;
		var id:String;

		if ( prefix != null ) {
			id = prefix + myCounter;
		} else {
			id = myCounter.toString();
		}
		return id;
	}
}
