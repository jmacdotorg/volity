import org.jivesoftware.xiff.core.XMPPConnection;
import org.jivesoftware.xiff.conference.Room;
_root.scaleMode = "noScale";
_root.align = "TL";
var curDepth = 10;
var margin = 10;
var userColor = "#0000FF";
var serverColor = "#FF0000";
var otherColor = "#000000";

// Attach the text area used for chat output
this.attachMovie("TextArea", "chatOutputWindow", curDepth++);

// Attach the grid used for the room roster
this.attachMovie("DataGrid", "chatRoster", curDepth++);

// Attach the input text
this.attachMovie("TextInput", "chatInput", curDepth++);
chatOutputWindow.editable = false;
chatOutputWindow.html = true;

// Attach the button
this.attachMovie("Button", "sendButton", curDepth++);
sendButton.label = "Send";

function positionAndSizeGUI() {
	// Resize and position the UI items
	chatInput.setSize((2*_root.width)/3-3*margin, 25);
	chatInput._x = margin;
	chatInput._y = _root.height-chatInput._height-margin;
	sendButton.setSize(_root.width/3, 25);
	sendButton._x = chatInput._x+chatInput._width+margin;
	sendButton._y = chatInput._y;
	chatOutputWindow._x = margin;
	chatOutputWindow._y = margin;
  chatOutputWindow.html = true;
	chatOutputWindow.setSize((2*_root.width)/3-3*margin, _root.height-3*margin-chatInput._height);
	chatRoster._x = chatOutputWindow._x+chatOutputWindow._width+margin;
	chatRoster._y = margin;
	chatRoster.setSize(_root.width/3, _root.height-(_root.height-sendButton._y)-2*margin);
}

// XMPPConnection - CHANGE THESE SETTINGS
var connection = new org.jivesoftware.xiff.core.XMPPConnection();
connection.username = "misuba";
connection.password = ""; // FIXME
connection.server = "volity.net";
connection.port = 5322;

// Room - CHANGE THESE SETTINGS
var chatRoom = new org.jivesoftware.xiff.conference.Room(connection);
chatRoster.dataProvider = chatRoom;
chatRoom.roomName = "devchat";
chatRoom.nickname = "misuba";
chatRoom.conferenceServer = "conference.volity.net";

// Event handler
var eventHandler = new Object();
eventHandler.handleEvent = function(eventObj) {
	switch (eventObj.type) {
	case "outgoingData" :
		var outgg:String = eventObj.data.split('<').join('&lt;') + '<br>';
    if (outgg.indexOf("&lt;auth ") != 0) {
      chatOutputWindow.text += outgg;
    }
		break;
    
	case "incomingData" :
		var ingg:String = eventObj.data.split('<').join('&lt;') + '<br>';
    if (ingg.indexOf("&lt;message ") != 0) {
      chatOutputWindow.text += ingg;
    }
		break;
    
	case "connection" :
    // nothing to do! xiff calls auth.
		break;
    
	case "login":
		chatRoom.join();
    //chatOutputWindow.text += "new JID is " + connection.getJID();
		break;
    
	case "disconnection" :
		break;
    
	case "groupMessage" :
		var msg = eventObj.data;
		var nick = msg.from.split("/")[1];
		addToChatOutput(nick, msg.body);
		break;
    
	case "error" :
		chatOutputWindow.text += 'error: ' + eventObj.errorCondition + ',' + eventObj.errorMessage + ',' + eventObj.errorType;
		break;
	}
};

eventHandler.sendButtonClicked = function() {
	sendMessage(chatInput.text);
	chatInput.text = "";
};

eventHandler.onResize = positionAndSizeGUI;

// Keyboard listener
var keyListener = new Object();
keyListener.onKeyUp = function() {
	if (eval(Selection.getFocus()) == chatInput.label && Key.getCode() == Key.ENTER) {
		// Mimic send button click
		eventHandler.sendButtonClicked();
	}
};

// Listener setup
connection.addEventListener("outgoingData", eventHandler);
connection.addEventListener("incomingData", eventHandler);
connection.addEventListener("connection", eventHandler);
connection.addEventListener("login", eventHandler);
connection.addEventListener("disconnection", eventHandler);
connection.addEventListener("error", eventHandler);
chatRoom.addEventListener("groupMessage", eventHandler);
sendButton.addEventListener("click", eventHandler.sendButtonClicked);
Key.addListener(keyListener);
_root.addListener(eventHandler);

function addToChatOutput(nickname, text) {
	if (nickname == chatRoom.nickname) {
		var output = "<font color=\""+userColor+"\">"+nickname+": ";
	} else if (nickname == null) {
		var output = "<font color=\""+serverColor+"\">* ";
	} else {
		var output = "<font color=\""+otherColor+"\">"+nickname+": ";
	}
	output += text+"";
	chatOutputWindow.text += output;
}

function sendMessage(messageBody) {
	chatRoom.sendMessage(messageBody);
}

positionAndSizeGUI();

if(connection.connect("standard")) {
	//chatOutputWindow.text += "found server";	
}
else {
	chatOutputWindow.text += "failed to initialize/find server";
}

