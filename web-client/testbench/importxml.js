/*****************************************************************************************
          Script to import XML data files and make them available to JavaScript
                     v2.0.6 written by Mark Wilton-Jones, 13/04/2004
Updated 02/07/2004 to provide native Safari 1.2 and Opera 7.6 support using XMLHttpRequest
                   Updated 24/07/2004 to prevent a Safari caching bug
      Updated 02/10/2004 to include support for older Internet Explorer XML objects
 Updated 09/11/2004 to allow a delay for better response in browsers that use the iframe
               Updated 17/03/2007 to support ActiveX object used by Pocket IE
******************************************************************************************

Please see http://www.howtocreate.co.uk/jslibs/ for details and a demo of this script
Please see http://www.howtocreate.co.uk/tutorials/jsexamples/importingXML.html for a demo and description
Please see http://www.howtocreate.co.uk/jslibs/termsOfUse.html for terms of use

To use this, insert the following into the head of your document:

<script type="text/javascript"><!--
//for older browsers like Netscape 4 ... if you care
window.onerror = function () { return true; }
//--></script>
<script src="PATH TO SCRIPT/importxml.js" type="text/javascript"></script>

This header file provides one function:
var canItWork = importXML( string: locationOfXMLFile, string: nameOfFunction[, optional boolean: allowCache[, optional boolean: delay]] );
eg.
var canItWork = importXML( 'myXML.xml', 'runThis' );

To support (Internet) Explorer 5 on Mac, the XML file should use a stylesheet:
<?xml-stylesheet type="text/css" href="blank.css"?>
Although that stylesheet could in fact be completely empty. Failure to do this will produce errors when you
try to manipulate the DOM of the XML file.

When the xml file has loaded, the named function will be run, and will be passed a reference to the document
object of the XML file. You can then manipulate the data in the file using W3C DOM scripting.

Browsers may cache the XML files (with Safari, the import fails if the file is already cached by the current page).
To prevent this, the script adds a timestamp to the end of each request URL (changes every millisecond).
If you do not want this timestamp to be added, pass the value 'true' as a third parameter.
var canItWork = importXML( 'myXML.xml', 'runThis', true );
This is not recommended.

Browsers that use the iframe may have problems if the XML takes a long time to load, as they will attempt to
access the data before it is ready. If you know that this might happen, you can use the delay parameter to
make the script wait for the specified amount of time before attemting to use the data, hopefully giving the
XML the chance to load. For example, to introduce a 2 second delay:
var canItWork = importXML( 'myXML.xml', 'runThis', false, 2000 );
_______________________________________________________________________________________*/

var MWJ_ldD = [];

function importXML( oURL, oFunct, oNoRand, oDelay ) {
	//note: in XML importing event handlers, 'this' refers to window
	if( !oNoRand ) { oURL += ( ( oURL.indexOf('?') + 1 ) ? '&' : '?' ) + ( new Date() ).getTime(); } //prevent cache
	if( window.XMLHttpRequest ) {
		//alternate XMLHTTP request - Gecko, Safari 1.2+ and Opera 7.6+
		MWJ_ldD[MWJ_ldD.length] = new XMLHttpRequest();
		MWJ_ldD[MWJ_ldD.length-1].onreadystatechange = new Function( 'if( MWJ_ldD['+(MWJ_ldD.length-1)+'].readyState == 4 && MWJ_ldD['+(MWJ_ldD.length-1)+'].status < 300 ) { '+oFunct+'(MWJ_ldD['+(MWJ_ldD.length-1)+'].responseXML); }' );
		MWJ_ldD[MWJ_ldD.length-1].open("GET", oURL, true);
		MWJ_ldD[MWJ_ldD.length-1].send(null);
		return true;
	}
	if( !navigator.__ice_version && window.ActiveXObject ) {
		//the Microsoft way - IE 5+/Win (ICE produces errors and fails to use try-catch correctly)
		var activexlist = ['Microsoft.XMLHTTP','Microsoft.XMLDOM'], tho; //add extra progids if you need specifics
		for( var i = 0; !tho && i < activexlist.length; i++ ) {
			try { tho = new ActiveXObject( activexlist[i] ); } catch(e) {}
		}
		if( tho ) {
			MWJ_ldD[MWJ_ldD.length] = tho;
			MWJ_ldD[MWJ_ldD.length-1].onreadystatechange = new Function( 'if( MWJ_ldD['+(MWJ_ldD.length-1)+'].readyState == 4 ) { '+oFunct+'(MWJ_ldD['+(MWJ_ldD.length-1)+'].load?MWJ_ldD['+(MWJ_ldD.length-1)+']:MWJ_ldD['+(MWJ_ldD.length-1)+'].responseXML); }' );
			if( MWJ_ldD[MWJ_ldD.length-1].load ) {
				MWJ_ldD[MWJ_ldD.length-1].load(oURL);
			} else {
				MWJ_ldD[MWJ_ldD.length-1].open('GET', oURL, true);
				MWJ_ldD[MWJ_ldD.length-1].send(null);
			}
			return true;
		}
	}
	if( document.createElement && document.childNodes ) {
		//load the XML in an iframe
		var ifr = document.createElement('DIV');
		ifr.style.visibility = 'hidden'; ifr.style.position = 'absolute'; ifr.style.top = '0px'; ifr.style.left = '0px';
		//onload only fires in Opera so I use a timer for all
		if( !window.MWJ_XML_timer ) { window.MWJ_XML_timer = window.setInterval('MWJ_checkXMLLoad();',100); }
		ifr.innerHTML = '<iframe src="'+oURL+'" name="MWJ_XML_loader_'+MWJ_ldD.length+'" height="0" width="0"><\/iframe>';
		MWJ_ldD[MWJ_ldD.length] = oFunct+'MWJ_SPLIT'+(oDelay?oDelay:1)+'';
		document.body.appendChild(ifr);
		return true;
	}
	return false;
}

function MWJ_checkXMLLoad() {
	//check if each imported file is available (huge files may not have loaded completely - nothing I can do - use the delay to help)
	for( var x = 0; x < MWJ_ldD.length; x++ ) { if( MWJ_ldD[x] && window.frames['MWJ_XML_loader_'+x] ) {
		setTimeout( MWJ_ldD[x].split('MWJ_SPLIT')[0] + '(window.frames.MWJ_XML_loader_'+x+'.window.document);', parseInt(MWJ_ldD[x].split('MWJ_SPLIT')[1]) );
		MWJ_ldD[x] = false;
	} }
}
