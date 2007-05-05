/**
 * RosterTest.as
 * Description: A base implementation for testing the roster/IM functionality.
 *
 * Copyright (C) 2003-2005 Sean Voisen <sean@xifflabs.com>
 * XIFF Labs
 *
 * Usage: Make sure the following items are in your FLA's library:
 * - DataGrid
 * - TextInput
 * - Label
 * #include "RosterTest.as" in the first frame of the FLA and compile
 *
 */
 
import org.jivesoftware.xiff.core.XMPPConnection;
import org.jivesoftware.xiff.im.Roster;

var curDepth = 10;
var margin = 10;

// Attach the data grid used for the roster itself
this.attachMovie( "DataGrid", "rosterUI", curDepth++ );

// Attach the input items
this.attachMovie( "TextInput", "jidInput", curDepth++ );
this.attachMovie( "Label", "jidInputLabel", curDepth++ );
jidInputLabel.autoSize = "left";
jidInputLabel.text = "JID:";
this.attachMovie( "TextInput", "displayNameInput", curDepth++ );
this.attachMovie( "Label", "displayNameInputLabel", curDepth++ );
displayNameInputLabel.autoSize = "left";
displayNameInputLabel.text = "Display Name:";
this.attachMovie( "TextInput", "groupInput", curDepth++ );
this.attachMovie( "Label", "groupInputLabel", curDepth++ );
groupInputLabel.autoSize = "left";
groupInputLabel.text = "Group:";

function positionAndSize()
{
	groupInputLabel._y = Stage.height - groupInputLabel._height - margin;
	groupInputLabel._x = margin;
	
	groupInput.setSize( Stage.width/4 - 3*margin, 25 );
	groupInput._x = groupInputLabel._x + groupInputLabel._width;
	groupInput._y = groupInputLabel._y;
	
	displayNameInputLabel._y = groupInputLabel._y - displayNameInputLabel._height - margin;
	displayNameInputLabel._x = margin;
	
	displayNameInput.setSize( Stage.width/4 - 3*margin, 25 );
	displayNameInput._x = displayNameInputLabel._x + displayNameInputLabel._width;
	displayNameInput._y = displayNameInputLabel._y;
	
	jidInputLabel._y = displayNameInputLabel._y - jidInputLabel._height - margin;
	jidInputLabel._x = margin;
	
	jidInput.setSize( Stage.width/4 - 3*margin, 25 );
	jidInput._x = jidInputLabel._x + jidInputLabel._width;
	jidInput._y = jidInputLabel._y;
	
	rosterUI.setSize( Stage.width - 2*margin, Stage.height - (Stage.height - jidInput._y) - 2*margin);
	rosterUI._x = margin;
	rosterUI._y = margin;
}

// XMPPConnection
var connection = new XMPPConnection();
connection.username = "jive";
connection.password = "demo";
connection.server = "jivesoftware.org";

// Roster
var roster = new Roster( connection );
rosterUI.dataProvider = roster;

// Event handler
eventHandler = new Object();
eventHandler.handleEvent = function( eventObj )
{
	switch( eventObj.type )
	{
		case "outgoingData":
			trace( "SENT: " + eventObj.data );
			break;
			
		case "incomingData":
			trace( "RECEIVED: " + eventObj.data );
			break;
	}
}

connection.addEventListener( "outgoingData", eventHandler );
connection.addEventListener( "incomingData", eventHandler );

positionAndSize();

connection.connect();
