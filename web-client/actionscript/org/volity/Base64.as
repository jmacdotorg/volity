/*

License

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License (http://www.gnu.org/copyleft/gpl.html) for more details.

Original JavaScript Encoding/Decoding
	Written by Stephen Ostermiller
	Copyright (C) 2003-2005 Stephen Ostermiller (http://ostermiller.org/contact.pl?regarding=JavaScript+Encoding)

Actionscript 2.0 Port:
	Jason Nussbaum, September 2005
	Copyright (C) 2005 Jason Nussbaum (http://www.jasonnussbaum.com)
*/

class org.volity.Base64
	extends Object
{
///#region Properties


///#endregion

///#region Constructor

	// all access to this class is through the
	//	static Encode and Decode methods.
	private function Base64() {}

///#endregion


///#region Public Methods

	///#region Static Methods

		public static function Encode( str:String ) : String
		{
			var encoder:Base64 = new Base64();
			return encoder.encodeBase64( str );
		}

		public static function Decode( str:String ) : String
		{
			var decoder:Base64 = new Base64();
			return decoder.decodeBase64( str );
		}
		
		public static function StringReplaceAll( source:String, find:String, replacement:String ) : String
		{
			return source.split( find ).join( replacement );
		}

	///#endregion


///#endregion


///#region Private vars

	///#region Static Vars

		private static var _EndOfInput = -1;

		private static var _Chars:Array = new Array(
			'A','B','C','D','E','F','G','H',
			'I','J','K','L','M','N','O','P',
			'Q','R','S','T','U','V','W','X',
			'Y','Z','a','b','c','d','e','f',
			'g','h','i','j','k','l','m','n',
			'o','p','q','r','s','t','u','v',
			'w','x','y','z','0','1','2','3',
			'4','5','6','7','8','9','+','/'
		);

		private static var _CharsReverseLookup:Array; // = new Array();
		private static var _CharsReverseLookupInited:Boolean = InitReverseChars();
		private static var _Digits = new Array( '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' );


	///#endregion

	private var _base64Str:String;
	private var _base64Count:Number;


///#endregion


///#region Private Mtehods

	///#region Static Methods

	private static function InitReverseChars() : Boolean
	{
		_CharsReverseLookup = new Array();

		for ( var i=0; i < _Chars.length; i++ )
		{
			_CharsReverseLookup[ _Chars[i] ] = i;
		}

		return true;
	}

	private static function UrlDecode( str:String ) : String
	{
		str = StringReplaceAll( str, "\+", " " );
		str = unescape( str );
		return str;
	}

	private static function UrlEncode( str:String ) : String
	{
		str = escape( str );
		str = StringReplaceAll( str, "\+", "%2B" );
		str = StringReplaceAll( str, "%20", "+" );
		return str;
	}

	///#endregion

	private function setBase64Str( str:String )
	{
		_base64Str = str;
		_base64Count = 0;
	}

	private function readBase64() : Number
	{
		if( !_base64Str )
		{
			return _EndOfInput;
		}

		if( _base64Count >= _base64Str.length )
		{
			return _EndOfInput;
		}

		var c:Number = _base64Str.charCodeAt( _base64Count ) & 0xff;
		_base64Count++;

		return c;
	}

	private function encodeBase64( str:String )
	{
		setBase64Str( str );
		var result = "";
		var inBuffer = new Array(3);
		var lineCount = 0;
		var done = false;

		while( !done && ( inBuffer[0] = readBase64() ) != _EndOfInput )
		{
			inBuffer[1] = readBase64();
			inBuffer[2] = readBase64();

			result += ( _Chars[ inBuffer[0] >> 2 ] );

		if( inBuffer[1] != _EndOfInput )
		{
				result += ( _Chars[ ( ( inBuffer[ 0 ] << 4 ) & 0x30 ) | ( inBuffer[ 1 ] >> 4 ) ] );
				if( inBuffer[ 2 ] != _EndOfInput )
				{
					result += ( _Chars[ ( ( inBuffer[ 1 ] << 2 ) & 0x3c ) | ( inBuffer[ 2 ] >> 6 ) ] );
					result += ( _Chars[ inBuffer[ 2 ] & 0x3F ] );
				}
				else
				{
					result += ( _Chars[ ( ( inBuffer[ 1 ] << 2 ) & 0x3c ) ] );
					result += ( "=" );
					done = true;
				}
			}
			else
			{
				result += ( _Chars[ ( ( inBuffer[ 0 ] << 4 ) & 0x30 ) ] );
				result += "=";
				result += "=";
				done = true;
			}

			lineCount += 4;

			if(lineCount >= 76)
			{
				result += ('\n');
				lineCount = 0;
			}
		}
		return result;
	}

	private function readReverseBase64(){
		if( !_base64Str )
			{ return _EndOfInput; }

		while( true )
		{
			if( _base64Count >= _base64Str.length )
				{ return _EndOfInput; }

			var nextCharacter:String = _base64Str.charAt( _base64Count );

			_base64Count++;

			if( _CharsReverseLookup[ nextCharacter ] )
			{
				return _CharsReverseLookup[nextCharacter];
			}

			if( nextCharacter == 'A' )
				{ return 0; }
		}

		return _EndOfInput;
	}

	private function ntos( n:Number ) : String
	{
		var str:String = n.toString( 16 ); // parseInt( n.toString(), 16 ).toString(); //

		if( str.length == 1 ) str = "0" + str;
		str = "%" + str;

		return unescape( str );
	}

	private function decodeBase64( str:String ) : String
	{
		setBase64Str(str);
		var result:String = "";
		var inBuffer:Array = new Array( 4 );
		var done:Boolean = false;

		while( !done && ( inBuffer[ 0 ] = readReverseBase64() ) != _EndOfInput
			&& ( inBuffer[ 1 ] = readReverseBase64() ) != _EndOfInput )
		{
			inBuffer[ 2 ] = readReverseBase64();
			inBuffer[ 3 ] = readReverseBase64();

			result += ntos( ( ( ( inBuffer[ 0 ] << 2 ) & 0xff ) | inBuffer[ 1 ] >> 4 ) );

			if( inBuffer[ 2 ] != _EndOfInput )
			{
				result += ntos( ( ( ( inBuffer[ 1 ] << 4 ) & 0xff ) | inBuffer[ 2 ] >> 2 ) );
				if(inBuffer[3] != _EndOfInput)
				{
					result +=  ntos((((inBuffer[2] << 6)  & 0xff) | inBuffer[3]));
				}
				else
				{
					done = true;
				}
			}
			else
			{
				done = true;
			}
		}

		return result;
	}

	private function toHex( n:Number ) : String
	{
		var result:String = "";
		var start = true;
		for( var i:Number=32; i>0; )
		{
			i-=4;
			var digit = (n>>i) & 0xf;
			if(!start || digit != 0)
			{
				start = false;
				result += _Digits[digit];
			}
		}
		return ( result=="" ? "0" : result );
	}

	private function pad( str:String, len:Number, pad:String ) : String
	{
		var result:String = str;
		for (var i=str.length; i<len; i++){
			result = pad + result;
		}
		return result;
	}

	function encodeHex( str:String ) : String
	{
		var result:String = "";
		for( var i:Number=0; i<str.length; i++)
		{
			result += pad( toHex( str.charCodeAt( i ) & 0xff ), 2, '0' );
		}
		return result;
	}

	private function decodeHex( str:String ) : String
	{
		//str = str.replace( new RegExp("s/[^0-9a-zA-Z]//g" ) );

		var result = "";
		var nextchar = "";

		for( var i=0; i<str.length; i++ )
		{
			nextchar += str.charAt(i);

			if(nextchar.length == 2)
			{
				result += ntos( parseInt( "0x" + nextchar) );
				nextchar = "";
			}
		}
		return result;
	}
}