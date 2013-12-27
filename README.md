# Volity, and related stuff

This repository contains everything relating to __Volity__, an open platform for online real-time multiplayer strategy games.

## What is Volity?

Volity is a specification for a network of individual game servers, called _parlors_, organized around a coordinating registration server, known as the _bookkeeper_. A single Volity game network has a single bookkeeper and many parlors, each of which knows how to play a single game.

Every entity on a Volity network, both servers and players, exists atop a single [XMPP (Jabber)](http://en.wikipedia.org/wiki/XMPP) user account, and all Volity-specific network communication between these entities occurs over [Jabber-RPC](http://xmpp.org/extensions/xep-0009.html). Players are transparently XMPP users, and play Volity games through client programs. These programs know how to use both the basic XMPP protocol as well as Volity's own RPC-based protocol.

Each parlor is a process running on a machine somewhere -- not necessarily alongside the network's bookkeeper, or any other parlor -- that knows how to play a single game. Parlors can be written by anyone with the gumption to do so, using any programming language they choose, so long as they stick to Volity's own communication protocol. They can then register themselves with any Volity bookkeeper open to new parlors, and that bookkeeper will then reveal to its players the availability of this parlor.

The documentation folder in this repository contains a much deeper dive into all these components and how they all work together, and why it's cool.

Volity is in fact cool enough that some friends and I launched a startup around it in 2005, where we ran our own Volity network and licensed some commercial games to adapt for it, but it didn't work very well as a business and we shut it down a few years later. So here it is on GitHub! I hope it you find it interesting.

## What's in this repository?

Directories found at the top level:

* __bookeeper-perl__: Perl implemenentation of a Volity bookkeeper.

* __documentation__: The `handbook` directory contains the static HTML remnants of what used to be Volity's official documentation wiki. It remains the best single reference for the Volity specification. The other subdirs contain the book _Volity Developers' Guide_ (incomplete, but lays out some basics fairly well) and an article about creating Volity UI bundles.

* __dtd__: Contains the XML DTD document describing the grammar used by the game-record documents that parlors submit to bookkeepers after games wrap up.

* __games-perl__: Parlor modules implemented in Perl. At the time of this writing, contains only an implementation of Hearts.

* __games-python__: Parlor modules for a bunch of public-domain games implemented in Python, including Werewolf, Hex, and others.

* __games-ui__: Volity user-interface bundles for all the games found in the previous two directories, as well as some test stuff. Everything has an SVG UI and some have a text UI as well.

* __Javolin__: A Java-based game client for the Volity. Able to use games' SVG-based UI bundles.

* __server-perl__: Implementation of a Volity parlor in Perl.

* __server-python__: Implementation of a Volity parlor in Python.

* __smack-dev-2.0-vol__: A fork of [the Smack XMPP library for Java](http://www.igniterealtime.org/projects/smack/), containing some customizations that Gamut depends on.

* __web-client__: An experimental web-based Volity client ("WebGamut"), which never saw official release.

## License

Many files and directories found throughout this repository contain or reference various open-source licenses. These licenses apply to the files and directories to which they are attached.

Files and directories in this repository that do not specify a license in this manner are copyright (c) 2004-2013 by Jason McIntosh (<jmac@jmac.org>).

## Credits and contributors

The Volity project was conceived and led by Jason McIntosh (<jmac@jmac.org>). He, Andrew Plotkin (<zarf@eblong.com>) and Rebecca Turner (<turner@mikomi.org>) co-developed its core protocols and implementations, as well as the web services that used to run on volity.net.

Other contributors to software and documentation in this repository include:

* Bill Barksdale (<bill@billbarksdale.com>)

* Phil Bordelon (<phil@lsu.edu>)

* Austin Henry (<ahenry@sf.net>)

* Dan Knapp (<dankna@accela.net>)

* Doug Orleans (<dougorleans@gmail.com>)

* Karl von Laudermann (<karlvonl@rcn.com>)

* Bill Racicot (<sparkyalbatross@gmail.com>)