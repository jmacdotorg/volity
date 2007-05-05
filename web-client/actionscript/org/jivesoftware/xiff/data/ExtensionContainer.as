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
import org.jivesoftware.xiff.data.IExtendable;
 
/**
 * Contains the implementation for a generic extension container.  Use the static method "decorate" to implement the IExtendable interface on a class.
 *
 * @author Sean Treadway
 * @since 2.0.0
 * @availability Flash Player 7
 * @toc-path Data
 * @toc-sort 1
 */
class org.jivesoftware.xiff.data.ExtensionContainer implements IExtendable
{
	static var _fExtensionContainer:ExtensionContainer = undefined;
	static var _exts:String = "__e_";

	static function decorate(proto:Object):Void
	{
		if (_fExtensionContainer == undefined) {
			_fExtensionContainer = new ExtensionContainer();
		}
		
		proto.addExtension = _fExtensionContainer.addExtension;
		proto.getAllExtensionsByNS = _fExtensionContainer.getAllExtensionsByNS;
		proto.getAllExtensions = _fExtensionContainer.getAllExtensions;
		proto.removeExtension = _fExtensionContainer.removeExtension;
		proto.removeAllExtensions = _fExtensionContainer.removeAllExtensions;
	}

	public function addExtension( ext:IExtension ):IExtension
	{
		if (this[_exts] == undefined) {
			this[_exts] = new Object();
			_global.ASSetPropFlags(this, _exts, 1);
		}

		if (this[_exts][ext.getNS()] == undefined) {
			this[_exts][ext.getNS()] = new Array();
		}

		this[_exts][ext.getNS()].push(ext);
		return ext;
	}

	public function removeExtension( ext:IExtension ):Boolean
	{
		var extensions:Array = this[_exts][ext.getNS()];
		for (var i in extensions) {
			if (extensions[i] === ext) {
				extensions[i].remove();
				extensions.splice(Number(i), 1);
				return true;
			}
		}
		return false;
	}

	public function removeAllExtensions( namespace:String ):Void
	{
		for (var i in this[_exts][namespace]) {
			this[_exts][namespace][i].remove();
		}
		this[_exts][namespace] = new Array();
	}

	public function getAllExtensionsByNS( namespace:String ):Array
	{
		return this[_exts][namespace];
	}

	public function getAllExtensions():Array
	{
		var exts:Array = new Array();
		for (var ns in this[_exts]) {
			exts = exts.concat(this[_exts][ns]);
		}
		return exts;
	}
}
