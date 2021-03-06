#!/usr/bin/perl
use Test::More;
use strict;
use warnings;

eval 'use Test::Spelling';

if ( $@ ) {
    plan skip_all => 'Can\'t run spelling tests without Test::Spelling';
} else {
    add_stopwords(<DATA>);
    all_pod_files_spelling_ok();
}
__DATA__
API
Bot
CALLBACK
Callback
ECMAScript
Friv
Friv's
GPG
IDs
IM
IQ
JABBER
JEP
JID
JID's
JIDs
Jabber
Jabber's
JabberID
JabberIDs
Javolin
MUC
RPCs
SQL
STDERR
UI
URI
UTF
Volity
Whoah
XMPP
account's
attribute's
bot
bots
callback
callback's
class's
com
configuratory
curveball
datatyping
debuggy
default's
desc
en
enfolding
er
foo
form's
friv
game's
groupchat
hash's
hashrefs
internet
jabber
jabbery
jid
jids
key's
langauges
launchable
methodname
msg
namespaced
op
org
passphrase
patchily
pidfile
premade
record's
ref
ref's
refs
request's
response's
rmsg
rpc
rps
ruleset
rulesets
se
seat's
tmsg
unblessed
unsign
uri
username
utf
var
volity
volityd
website
