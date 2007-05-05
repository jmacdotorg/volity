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

import org.jivesoftware.xiff.data.Extension;
import org.jivesoftware.xiff.data.ExtensionClassRegistry;
import org.jivesoftware.xiff.data.IExtension;

/**
 * This class provides an extension for XHTML body text in messages.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @availability Flash Player 7
 * @param parent The parent node for this extension
 * @toc-path Extensions/HTML
 * @toc-sort 1/2
 */
class org.jivesoftware.xiff.data.xhtml.XHTMLExtension extends Extension implements IExtension
{
	// Static class variables to be overridden in subclasses;
	public static var NS:String = "http://www.w3.org/1999/xhtml";
	public static var ELEMENT:String = "html";

    private static var staticDepends = ExtensionClassRegistry;

	public function XHTMLExtension(parent:XMLNode)
	{
		super(parent);
	}

	/**
	 * Gets the namespace associated with this extension.
	 * The namespace for the XHTMLExtension is "http://www.w3.org/1999/xhtml".
	 *
	 * @return The namespace
	 * @availability Flash Player 7
	 */
	public function getNS():String
	{
		return XHTMLExtension.NS;
	}

	/**
	 * Gets the element name associated with this extension.
	 * The element for this extension is "html".
	 *
	 * @return The element name
	 * @availability Flash Player 7
	 */
	public function getElementName():String
	{
		return XHTMLExtension.ELEMENT;
	}

    /**
     * Performs the registration of this extension into the extension registry.  
     * 
	 * @availability Flash Player 7
     */
    public static function enable():Void
    {
        ExtensionClassRegistry.register(XHTMLExtension);
    }

	/**
	 * The XHTML body text. Valid XHTML is REQUIRED. Because XMPP operates using
	 * valid XML, standard HTML, which is not necessarily XML-parser compliant, will
	 * not work.
	 *
	 * @availability Flash Player 7
	 */
	public function get body():String
	{
		// XXX This kinda sucks becuase the accessor gets called
		// every time it appears in code, rather than being smart
		// about when to call it

		var html:Array = new Array();

		// XXX Some tests need to be done to determine if 
		// for (... in ...); reverse() is faster than for (...; ...; ...)
		// typically there will only be 1-5 nodeValues to join

		var children = getNode().childNodes;
		for (var i in children) {
			html.push(children[i].toString());
		}
		html.reverse();
		return html.join();
	}

	public function set body(theBody:String):Void
	{
		var children = getNode().childNodes;
		for (var i in children) {
			children[i].removeNode();
		}
		getNode().appendChild(new XML(theBody));
	}
}
