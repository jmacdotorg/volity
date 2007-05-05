import org.jivesoftware.xiff.core.XMPPConnection;
import org.jivesoftware.xiff.conference.Room;
import flash.external.*;

var isAvailable:Boolean = ExternalInterface.available;

if (isAvailable)
{
  // XMPPConnection - CHANGE THESE SETTINGS
  var connection = new org.jivesoftware.xiff.core.XMPPConnection();
  connection.username = "misuba";
  connection.password = "";
  connection.server = "volity.net";
  connection.port = 5322;
  
  // Event handler
  var eventHandler = new Object();
  eventHandler.handleEvent = function(eventObj) {
    switch (eventObj.type) {
    case "outgoingData":
      var outgg:String = eventObj.data.split('<').join('&lt;') + '<br>';
      if (outgg.indexOf("&lt;auth ") != 0) {
        xdebug(outgg);
      }
      break;
      
    case "incomingData":
      xdebug( eventObj.data.split('<').join('&lt;') + '<br>');
      break;
      
    case "connection":
      // nothing to do! xiff calls auth.
      break;
      
    case "login":
      //chatRoom.join();
	  discoverUI(rulesetURI);
      break;
      
    case "disconnection":
	  // some kind of error msg
      break;
      
	case "message":
	  // use the same tab-handler thing as below?
	  break;
	  
    case "groupMessage":
      var msg = eventObj.data;
      var nick = msg.from.split("/")[1];
	  // will need a new thing for connecting msgs by from to the right tab
      addToChatOutput(nick, msg.body);
      break;
      
    case "error":
      xdebug( 'error: ' + eventObj.errorCondition + ',' + eventObj.errorMessage + ',' + eventObj.errorType);
      break;
    }
  };
  
  function xdebug(val:String):Void
  {
    ExternalInterface.call("Volity.debug",val);
  }
  
  function discoverUIs(ruleset:String):Void
  {
    var discoIQ:IQ = new IQ(BOOKKEEPER_JID, IQ.GET_TYPE, XMPPStanza.generateID("disco_"), "discoverBestUI", this );
    var discoExt:ItemDiscoExtension = new ItemDiscoExtension(discoIQ.getNode());
    discoExt.getNode().attributes.node = ruleset + '|uis';
    
    discoIQ.addExtension(discoExt);
    send(discoIQ);
  }
  
  function discoverBestUI(reply:IQ):Void
  {
    if( reply.type == IQ.RESULT_TYPE )
    {
      // we should have a big list of UI files to check
      // extract it
      // put it in a global var
      // if we have one or none with a compatible type,
        // use it or error
      // else,
        // loop thru and check UIs
    }
    
  }
  
  function checkUI(reply:IQ):Void
  {
    if( reply.type == IQ.RESULT_TYPE )
    {
      // fish out 
    }
  }
  
  // Listener setup
  connection.addEventListener("outgoingData", eventHandler);
  connection.addEventListener("incomingData", eventHandler);
  connection.addEventListener("connection", eventHandler);
  connection.addEventListener("login", eventHandler);
  connection.addEventListener("disconnection", eventHandler);
  connection.addEventListener("error", eventHandler);
  chatRoom.addEventListener("groupMessage", eventHandler);
  _root.addListener(eventHandler);
  
  function sendMessage(messageBody) {
    chatRoom.sendMessage(messageBody);
  }
  ExternalInterface.addCallback("sendToRoom", null, sendMessage);
  
  if(connection.connect("standard")) {
    //chatOutputWindow.text += "found server";	
  }
  else {
    xdebug( "failed to initialize/find server");
  }
}
