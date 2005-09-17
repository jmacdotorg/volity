"""Volity: A Network Standard for Near-Real-Time Gaming
    Volity home page: <http://volity.org/>
    
    Python Volity framework designed by Andrew Plotkin <erkyrath@eblong.com>
    Python Volity home page: <http://eblong.com/zarf/volity/>

The Python source code in this module, and its submodules, is
copyright 2005 by Andrew Plotkin. You may copy and distribute it
freely, by any means and under any conditions. You may incorporate
this code into your own software and distribute that, or modify this
code and distribute the modified version, as long as you retain a
notice in your software or documentation which includes this copyright
notice, this license, and the URL shown above.

Volity is a protocol for interactive games (both single and
multiplayer) which do not require high bandwidth or low latency. In
other words: card games, board games, any kind of turn-based games;
but not real-time shooters.

This package requires Python version 2.3 or later. It also requires
Zymb (a framework for Python Jabber communication). You can download
Zymb from <http://eblong.com/zarf/zymb/>.

* To run games:

This package contains the Python classes needed to run a Volity game
parlor. A parlor allows players to join and play a particular game.
The game is loaded from a Python plug-in game module. See the URLs
above for various game modules you can download.

You will also need volityd.py -- the brief script that loads a game
module and starts the parlor running. It is available at
<http://eblong.com/zarf/volity/volityd.py>.

And you will need a Jabber ID -- that is, an account on a Jabber
server. Jabber is an open-source instant messaging system. This code
does not allow you to run a Jabber server. (It's a game server, but a
Jabber *client*.) However, any Jabber server will do, including
jabber.org. See <http://jabber.org/> for more information, and for
Jabber server and client software.

Once you have all of these things, make sure the volity, zymb, and
game modules are in your Python path. You can then type:

python volityd.py --jid JABBERID --password JABBERPASSWORD --game games.rps.RPS

...to begin running a Rock Paper Scissors parlor.

* To develop games:

This package contains the Python classes needed to develop new Volity
game referees. You will also want to test your game as you develop it,
so you'll still need volityd.py.

(Developing a Volity requires you to create a referee and a client UI
file. This package lets you do the former. The latter is a matter of
SVG and Javascript, not of Python.)

* Contents:

parlor -- contains the Parlor class
referee -- contains the Referee class
volent -- a base class for Parlor and Referee
game -- the base class used to develop a new game

* Version history:

- 1.3.3:
The referee is no longer confused by a player whose MUC affiliation is
    'owner'.
Updated disco code to support the 'volity-role' field.
Changed 'server' field in the referee disco to 'parlor'.

- 1.3.2:
The referee no longer sends player_unready calls when volity.* RPCs
    cause all players to become unready. The client must know that
    unready-all is a side effect of player_sat, start_game, etc.
JID strings sent in failure tokens are now properly namespaced as
    'literal'.
Referee copes better with a player changing nickname. (Although it
    registers as the player leaving the MUC and immediately rejoining,
    so the client must do another send_state.)

- 1.3.1:
Parse <double> RPC values as integers, if they have integral values.
Contrariwise, if an RPC method's validator demands a float, silently
    accept an integer in that place. (But not vice versa.)

- 1.3:
Permitted Seat and Player arguments in the game RPC-sending methods
    (these send the seat ID and the player JID, respectively).
Updated volity.send_state to include seating info.
Changed referee to not send seating/config info until requested.
Improved some RPC fault messages.

- 1.2.1:
Added the volity.send_state RPC (previously available as get_full_state)
    to the referee.

- 1.2:
Changed the class name "server" (and its module) to "parlor", to match
    current Volity terminology.

- 1.1.3:
The token system now specifies that you can return at most one value
    after 'volity.ok'.
Set up parlors/referees to return an RPC fault (instead of a stanza
    error) when an RPC is not recognized.

- 1.1.2:
Updated the referee to have five states and two timers (the config
    abandonment timer and the game abandonment timer). Suspension and
    unsuspension now work.
Removed the timeout and timeout_reaction configuration variables.
Added the kill_game configuration variable.

- 1.1.1:
Finally added a non-ugly way to end the game.
Send receive_state/state_sent configuration dumps whenever a client
    joins the table, or requests a dump.

- 1.1:
Updated to match latest Zymb changes. No functional changes.

- 1.0:
Initial release. Missing many features, but I call it 1.0 anyway.
"""

__all__ = [ 
    'volent', 'parlor', 'referee', 'game'
]
