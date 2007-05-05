if (!window.console) window.console = {debug: function(){}};

var Volity = Class.create();

Volity.debug = function(debugstr)
{
  console.debug(debugstr);
}

Volity.swf = null;
Volity.js = null;
Volity.filepath = 'volity.swf';

Volity.prototype = {
  initialize: function(ruleset, token, parentNd)
  {
    var theparent = document.body;
    if (parentNd != null && (typeof parentNd == 'HTMLElement' || typeof parentNd == 'string'))
    {
      theparent = (typeof parentNd == 'string' ? $$(parentnd)[0] : parentnd);
    }
    
    // make flash node AND THEN
    // (we will want to pass in some vars when html is written)
    var flashNode = document.createElement("div");
    flashNode.innerHTML = '<object width="1" height="1" ' +
      'id="volity_transport" ' +
      'type="application/x-shockwave-flash" ' +
      'data="' + Volity.filepath + '?ruleset=' + ruleset + '&token=' + token + '" ' +
    '>' +
      '<param name="allowScriptAccess" value="sameDomain" />' +
      '<param name="bgcolor" value="#ffffff" />' +
      '<param name="movie" value="' + Volity.filepath + '?ruleset=' + ruleset + '&token=' + token + '" />' +
      '<param name="scale" value="noscale" />' +
      '<param name="salign" value="lt" />' +
      '<param name="wmode" value="transparent" />' +
    '</object>';
    
    theparent.appendChild(flashNode);
    
    // wait for callback from applet to know when to proceed?
    
    Volity.swf = flashNode.firstChild;
    Volity.js = this;
  },
  
  sendMsg: function(to, body)
  {
    Volity.swf.sendMsg(to, body);
  },
  
  recvMsg: function(from, body)
  {
    if (!this.tabs[from])
    {
      this.makeTab(from);
    }
    this.addMessage(this.tabs[from], body);
    if (!this.tabs[from].hasClass('active-tab')) 
    {
      this.flashTab(from);
    }
  },
  
  sendMove: function(movetype, movecontent)
  {
    Volity.swf.sendMove(movetype, movecontent);
  },
  
  recvMove: function(player, movetype, movecontent)
  {
    var themove = this.moves[movetype];
    if (typeof themove == 'undefined')
    {
      themove = Volity.rpcs[movetype];
      if (typeof themove == 'undefined') return false;
    }
    
    themove.call(this, player, movecontent); // FIXME
  },
  
  loadUI: function(url)
  {
    $('uiframe').window.location = "http://" + url;
  },
  
  addPlayer: function()
  {
    // add player html to roster elm
    // also add dumb player record to data structure indexed by jid
    
    players[jid] = {name: name, jid: jid, type: type};
  },
  
  getPlayer: function(jid)
  {
    return $H(players[jid]); // FIXME
  },
  
  editPlayer: function(jid, newOrChangedHash)
  {
    var player = this.getPlayer(jid);
    player.merge($H(newOrChangedHash));
    players[jid] = player;
    // also, rerender html
    // remembering that it may need sorting
  },
  
  removePlayer: function(jid)
  {
    delete players[jid];
    // remove html element
  },
  
  makeTab: function(fromjid) 
  {
    // make an HTMLElement!
    // file it in this.tabs, under its jid
    // also add it to dom
    // do whatever tab magic we need to do
    // (may include overflow handling)
  },
  
  addMessage: function(fromjid, body)
  {
    if (typeof body == 'string')
    {
      // make it an element instead
    }
    this.tabs[fromjid].appendChild(body);
  },
  
  flashTab: function(fromjid)
  {
    // give it an interval thing
    // give what an interval thing?
    // um, i guess the HTMLElement we have filed
    // we are gonna have to be extra good about clearing it though
    // like, when the page closes we have to go through tabs and clear any that exist.
    // ok
    // also they will have to be Elements not HTMLElements because you can't always extend the latter
    // so. do that
    // and i guess an onclick handler that kills it?
    // no, that should go into all tabs when they are made
  },
  
  tabs: {},
  moves: {}
};

Volity.rpcs = {
  start_game: function ()
  {
//A notification that a game has begun at a table at which the receiver is seater.

//(All players become unready.)
  },
  
  end_game: function()
  {
//A notification that a game that the receiver was playing in just ended.

//(All players become unready.)
  },
  
  suspend_game: function()
  {
//The referee has suspended the game. (See the next method for the player-thrown version.)

//(All players become unready.)
  },
  
  suspend_game: function( JID )
  {
//The player with the given JID has suspended the game. (JID may also be the referee, in the case of an abandoned game.)

//(All players become unready.)
  },
  resume_game: function()
  {
//The game has been resumed (after suspension).

//(All players become unready.)
  },
  
  receive_state: function( struct )
  {
//The referee is about to begin sending a lot of configuration RPCs (mostly game-specific ones, but also seating information and so on). The blast of configuration will be closed with a volity.state_sent() RPC. This generally occurs when the player joins a table, but may also be the result of a volity.send_state request. See state recovery.

//The struct argument provides yet more information. At the moment, this is just one field:

//state
//    The current referee state. 
  },
  
  game_has_started: function()
  {
//The referee has finished sending configuration information, and is about to begin sending game state. This only occurs during state recovery, and only if a game is in progress.
  },
  
  game_activity: function( state )
  {
//The referee has moved to the given state, which will be one of the strings active, disrupted, abandoned. (The other states, setup and suspended, are signalled by other RPCs.)

//This call is only used when moving from one of those three states. (When leaving setup or suspended, the referee always goes to active, so no volity.game_activity() call is necessary.)
  },
  
  state_sent: function()
  {
//The referee has finished sending configuration information and game state. This concludes a state recovery burst, which started with volity.receive_state().
  },
  
  kill_game: function( JID, doItFlag )
  {
//The player with the given JID has either proposed that the game be killed upon resumption of play (if the second argument is true), or that the game resume normally (if it's false). This is only legal when the game is suspended.

//(All players become unready.)
  },
  
  show_table: function( JID, true | false )
  {
//The player with the given JID has changed the table's visibility in the the game browser. (See also table configuration.)

//(All players become unready.)
  },
  
  record_games: function( JID, true | false )
  {
//The player with the given JID has that the table will send game records to the bookkeeper at the end of each game (if the argument is true) or not (if the argument is false). (See also table configuration.)

//(All players become unready.)
  },
  
  language: function( JID, language )
  {
//The player with the given JID changed the table's preferred language. The argument is a two-letter language code.

//(All players become unready.)
  },
  
  player_ready: function( JID )
  {
//The player with the given JID has indicated that it is ready to play, agreeing with the present table configuration.
  },
  
  player_unready: function( JID )
  {
//The player with the given JID has indicated that it is not ready to play. It's likely that the table configuration changed somehow, and the referee called this not just on this player but every ready player at the table, but it could be that this player is individially changing its mind about its own readiness.
  },
  
  player_stood: function( JID )
  {
//The player with the given JID has indicated that it no longer wishes to play, and instead simply observe. This is also sent when a seated player disconnects from the table.

//(All players become unready.)
  },
  
  player_sat: function( JID, seat-id )
  {
//The player with the given JID wishes the play the game, and has sat in the seat with the given ID. This is also sent when an absent player reconnects to a game in progress, and therefore returns to the seat which belongs to him.

//(All players, including this one, become unready.)
  },
  
  seat_list: function( seat-ids )
  {
//The given array represents the IDs of all the seats that the referee might ever refer to over the lifetime of the table.

//Usually called on a player only as it joins the table.
  },
  
  required_seat_list: function( seat-ids )
  {
//The given array represents the IDs of all the seats that must be occupied before the game can start. This is useful for games where seats have roles pertinent to the ruleset, such as (black, white) in chess, or (north, south, east, west) in Bridge. Referees of rulesets with no such role-driven seats may elect to not bother with this request.

//The array must be a subset of the array provided with a preceding volity.seat_list request.

//Usually called on a player only as it joins the table.
  },
  
  receive_invitation: function( struct )
  {
//A player at some table is inviting you to join it. The contents of the struct are described on the invitation page.

//Note that this RPC is sent by the table's referee, not by a player. It is the only RPC which the client will receive from a referee of a table you have not joined.
  },
  
  message: function( list )
  {
//The referee is sending a symbolic message, which should be localized and displayed in the client's message stream. The list is a nonempty array of tokens, as described in RPC replies. It may not begin with volity.ok; any other token in any namespace is legitimate. 
  }
};
