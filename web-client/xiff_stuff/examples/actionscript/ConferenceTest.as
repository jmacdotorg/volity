/**
 * ConferenceTest.as
 * Description: A base implementation for testing the conferencing functionality.
 *
 * Copyright (C) 2003-2005 Sean Voisen <sean@xifflabs.com>
 * XIFF Labs
 *
 * Usage: Make sure the following items are in your FLA's library:
 * - DataGrid
 * - TextArea
 * - TextInput
 * - Button
 * - ComboBox
 * #include "ConferenceTest.as" in the first frame of the FLA and compile
 *
 */
 
import org.jivesoftware.xiff.core.XMPPConnection;
import org.jivesoftware.xiff.conference.Room;

Stage.scaleMode = "noScale";
Stage.align = "TL";

var curDepth = 10;
var margin = 10;

var userColor = "#0000FF";
var serverColor = "#006600";
var otherColor = "#000000";

var moderatorActions = new Array( {label:"Kick", data:"kick"}, {label:"Ban", data:"ban"} );

// Attach the text area used for chat output
this.attachMovie( "TextArea", "chatOutputWindow", curDepth++ );

// Attach the grid used for the room roster
this.attachMovie( "DataGrid", "chatRoster", curDepth++ );

// Attach the input text
this.attachMovie( "TextInput", "chatInput", curDepth++ );
chatOutputWindow.editable = false;
chatOutputWindow.html = true;

// Attach the button
this.attachMovie( "Button", "sendButton", curDepth++ );
sendButton.label = "Send";

// Button for actions
this.attachMovie( "Button", "applyButton", curDepth++ );
applyButton.label = "Apply";

// Attach combo box
this.attachMovie( "ComboBox", "moderatorActionsCB", curDepth++ );
moderatorActionsCB.dataProvider = moderatorActions;

function positionAndSizeGUI()
{
	// Resize and position the UI items
	chatInput.setSize( (2*Stage.width)/3 - 3*margin, 25 );
	chatInput._x = margin;
	chatInput._y = Stage.height - chatInput._height - margin;
	
	sendButton.setSize( Stage.width/3, 25 );
	sendButton._x = chatInput._x + chatInput._width + margin;
	sendButton._y = chatInput._y;
	
	applyButton.setSize( Stage.width/3, 25 );
	applyButton._x = sendButton._x;
	applyButton._y = sendButton._y - margin - applyButton._height;
	
	moderatorActionsCB.setSize( Stage.width/3, 25 );
	moderatorActionsCB._x = applyButton._x;
	moderatorActionsCB._y = applyButton._y - moderatorActionsCB._height - margin;
	
	chatOutputWindow._x = margin;
	chatOutputWindow._y = margin;
	chatOutputWindow.setSize( (2*Stage.width)/3 - 3*margin, Stage.height - 3*margin - chatInput._height );
	
	chatRoster._x = chatOutputWindow._x + chatOutputWindow._width + margin;
	chatRoster._y = margin;
	chatRoster.setSize( Stage.width/3, Stage.height - (Stage.height - moderatorActionsCB._y) - 2*margin );
}

// XMPPConnection
var connection = new XMPPConnection();
connection.username = "jive";
connection.password = "demo";
connection.server = "jivesoftware.org";

// Room
var chatRoom = new Room( connection );
chatRoster.dataProvider = chatRoom;
chatRoom.roomName = "flashroom";
chatRoom.nickname = "flashroom";
chatRoom.conferenceServer = "conference.jivesoftware.org";

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
			
		case "login":
			// Connect to the conference server
			chatRoom.join();
			break;
			
		case "groupMessage":
			var msg = eventObj.data;
			var nick = msg.from.split( "/" )[1];
			addToChatOutput( nick, msg.body );
			break;
	}
}

eventHandler.sendButtonClicked = function()
{
	sendMessage( chatInput.text );
	chatInput.text = "";
}

eventHandler.applyButtonClicked = function()
{
	// Get the action selected and the user selected
	action = moderatorActionsCB.selectedItem.data;
	user = chatRoster.selectedItem.nickname;
	
	switch( action )
	{
		case "kick":
			chatRoom.kickOccupant( user, "Later Sk8r" );
			break;
	}
}

eventHandler.onResize = positionAndSizeGUI;

// Keyboard listener
keyListener = new Object();
keyListener.onKeyUp = function()
{
	if( eval( Selection.getFocus() ) == chatInput.label && Key.getCode() == Key.ENTER )
	{
		// Mimic send button click
		eventHandler.sendButtonClicked();
	}
}

// Listener setup
connection.addEventListener( "outgoingData", eventHandler );
connection.addEventListener( "incomingData", eventHandler );
connection.addEventListener( "login", eventHandler );
chatRoom.addEventListener( "groupMessage", eventHandler );
sendButton.addEventListener( "click", eventHandler.sendButtonClicked );
applyButton.addEventListener( "click", eventHandler.applyButtonClicked );
Key.addListener( keyListener );
Stage.addListener( eventHandler );

function addToChatOutput( nickname, text )
{
	if( nickname == chatRoom.nickname )
	{
		var output = "<font color=\"" + userColor + "\">" + nickname + ": ";
	}
	else if( nickname == null )
	{
		var output = "<font color=\"" + serverColor + "\">* ";
	}
	else
	{
		var output = "<font color=\"" + otherColor + "\">" + nickname + ": ";
	}
	
	output += text + "<br/>";
	
	chatOutputWindow.text += output;
}

function sendMessage( messageBody )
{
	chatRoom.sendMessage( messageBody );
}

positionAndSizeGUI();
connection.connect();
