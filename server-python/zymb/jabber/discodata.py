"""This module contains classes which represent service discovery data,
and functions to convert them to and from Jabber stanzas. It is mostly
intended for use by zymb.jabber.disco.
"""

import unittest
import dataform
import interface

class DiscoInfo:
    """DiscoInfo: A class which represents the results of a disco info
    query. It can contain a list of identities, a list of features,
    and (optionally) an extended data form.

    DiscoInfo(identities=None, features=None, extended=None) -- constructor.

    The *identities* (if present) must be a list of (category, type, name)
    tuples (where *name* may be None).

    The *features* (if present) must be a list of *var* strings.

    The *extended* (if present) must be a DataForm.

    Public methods:

    addidentity() -- add a new identity to the identities list.
    getidentities() -- get the identities list.
    addfeature() -- add a new feature to the features list.
    getfeatures() -- get the features list.
    setextendedinfo() -- set an extended data form.
    getextendedinfo() -- get the extended data form.

    """
    
    def __init__(self, identities=None, features=None, extended=None):
        if (not identities):
            identities = []
        if (not features):
            features = []
            
        self.identities = identities
        self.features = features
        self.extended = extended

    def addidentity(self, cat, typ, name=None):
        """addidentity(cat, typ, name=None) -> None
        
        Add a new identity to the identities list. *cat* is the category;
        *typ* is the type; *name* is the (optional) name.
        """
        
        self.identities.append( (cat, typ, name) )

    def getidentities(self):
        """getidentities() -> list
        
        Get the identities list. Each member of the list will be a tuple
        (category, type, name), where *name* may be None.
        """
        
        return self.identities

    def getfeatures(self):
        """getfeatures() -> list

        Get the features list. Each member of the list will be a string
        defining the feature.
        """
        
        return self.features
    
    def addfeature(self, var):
        """addfeature(var) -> None

        Add a new feature to the features list. *var* is a string
        defining the feature.
        """
        
        self.features.append(var)

    def setextendedinfo(self, form):
        """setextendedinfo(form) -> None

        Set an extended data form. The *form* must be a DataForm, or
        None (to delete the form).
        """
        
        self.extended = form

    def getextendedinfo(self):
        """getextendedinfo() -> DataForm

        Get the extended data form. The result will be a DataForm, or
        None (if there was no form set).
        """
        
        return self.extended

class DiscoItems:
    """DiscoItems: A class which represents the results of a disco items
    query. It contains a list of items.

    DiscoItems(items=None) -- constructor.

    The *items* (if present) must be a list of (jid, name, node) tuples
    (where *name* and *node* may be None).

    Public methods:

    additem(jid, name=None, node=None) -- add a new item to the list.
    getitems() -- return the list of items.
    """
    
    def __init__(self, items=None):
        if (not items):
            items = []
            
        self.items = items

    def additem(self, jid, name=None, node=None):
        """additem(jid, name=None, node=None) -> None

        Add a new item to the list. *jid* is the JID; *name* is the (optional)
        name; *node* is the (optional) node.
        """
        
        jid = unicode(jid)
        self.items.append( (jid, name, node) )

    def getitems(self):
        """getitems() -> list

        Return the list of items. Each member of the list will be a tuple
        (jid, name, node), where *name* and *node* may be None.
        """

        return self.items

def makediscoitems(items):
    """makediscoitems(items) -> Node

    Given a DiscoItems object, return a <query> node in the disco#items
    namespace, containing the item list.
    """
    
    qnod = interface.Node('query')
    qnod.setnamespace(interface.NS_DISCO_ITEMS)

    for (jid, name, node) in items.items:
        nod = interface.Node('item',
            attrs={ 'jid':jid })
        if (name):
            nod.setattr('name', name)
        if (node):
            nod.setattr('node', node)
        qnod.addchild(nod)

    return qnod

def makediscoinfo(info):
    """makediscoinfo(info) -> Node

    Given a DiscoInfo object, return a <query> node in the disco#info
    namespace, containing the identities, features, and extended info.
    """
    
    qnod = interface.Node('query')
    qnod.setnamespace(interface.NS_DISCO_INFO)

    for nod in makediscoidentities(info.identities):
        qnod.addchild(nod)
    for nod in makediscofeatures(info.features):
        qnod.addchild(nod)
    if (info.extended):
        nod = makediscoextended(info.extended)
        qnod.addchild(nod)

    return qnod

def makediscoidentities(identities):
    """makediscoidentities(list) -> list

    Given a list of (category, type, name) tuples, return a list of
    <identity> nodes.
    """
    
    ls = []
    for (cat, typ, name) in identities:
        nod = interface.Node('identity',
            attrs={ 'category':cat, 'type':typ })
        if (name):
            nod.setattr('name', name)
        ls.append(nod)
    return ls

def makediscofeatures(features):
    """makediscofeatures(list) -> list

    Given a list of strings, return a list of <feature> nodes.
    """
    
    ls = []
    for st in features:
        nod = interface.Node('feature', attrs={ 'var':st })
        ls.append(nod)
    return ls
    
def makediscoextended(form):
    """makediscoextended(form) -> Node

    Given a DataForm, return an <x> node containing its data.
    """
    
    nod = form.makenode()
    return nod
    
def checknodename(nod, wanted, ns=None):
    nam = nod.getname()
    if (nam != wanted):
        raise ValueError('got \'' + nam + '\', not \'' + wanted + '\'')
        
    if (ns):
        st = nod.getnamespace()
        if (st != ns):
            raise ValueError('got \'' + st + '\', not \'' + ns + '\'')

def parsediscoitems(qnod):
    """parsediscoitems(Node) -> DiscoItems

    Given a <query> node in the disco#items namespace, return a DiscoItems
    object.
    """
    
    checknodename(qnod, 'query', interface.NS_DISCO_ITEMS)

    items = DiscoItems()
    
    ls = qnod.getchildren()
    for nod in ls:
        nam = nod.getname()
        if (nam == 'item'):
            items.additem(nod.getattr('jid'),
                nod.getattr('name'),
                nod.getattr('node'))
        else:
            raise ValueError('disco query has illegal node <' + nam + '>')

    return items
        
def parsediscoinfo(qnod):
    """parsediscoinfo(Node) -> DiscoInfo

    Given a <query> node in the disco#info namespace, return a DiscoInfo
    object.
    """
    
    checknodename(qnod, 'query', interface.NS_DISCO_INFO)

    identities = []
    features = []
    extended = None
    
    ls = qnod.getchildren()
    for nod in ls:
        nam = nod.getname()
        if (nam == 'identity'):
            identities.append(parsediscoidentity(nod))
        elif (nam == 'feature'):
            features.append(parsediscofeature(nod))
        elif (nam == 'x'):
            extended = parsediscoextended(nod)
        else:
            raise ValueError('disco query has illegal node <' + nam + '>')

    return DiscoInfo(identities, features, extended)

def parsediscoidentity(nod):
    """parsediscoidentity(Node) -> (category, type, name)

    Given an <identity> node, return a tuple. (The *name* may be None).
    """
    
    checknodename(nod, 'identity')
    
    cat = nod.getattr('category')
    typ = nod.getattr('type')
    name = nod.getattr('name')
    return (cat, typ, name)

def parsediscofeature(nod):
    """parsediscofeature(Node) -> str

    Given a <feature> node, return a string.
    """
    
    checknodename(nod, 'feature')

    var = nod.getattr('var')
    return var

def parsediscoextended(nod):
    """parsediscoextended(Node) -> DataForm

    Given an <x> node, return a DataForm.
    """
    
    return dataform.parse(nod)

# ------------------- unit tests -------------------

class TestDiscoData(unittest.TestCase):
    """Unit tests for the discodata module.
    """

    def test_items(self):
        itls = [
            ('test@test.com', None, None),
            ('test@test.com/test', 'Test Name', None),
            ('test@test.com/test', None, 'nodename'),
        ]
        xmlequiv = [
            '<item xmlns="http://jabber.org/protocol/disco#items"'
                + ' jid="test@test.com" />',
            '<item xmlns="http://jabber.org/protocol/disco#items"'
                + ' jid="test@test.com/test" name="Test Name" />',
            '<item xmlns="http://jabber.org/protocol/disco#items"'
                + ' node="nodename" jid="test@test.com/test" />',
        ]

        items = DiscoItems(itls)
        qnod = makediscoitems(items)

        newitems = parsediscoitems(qnod)
        self.assertEqual(items.items, newitems.items)
        
        ix = 0
        for nod in qnod.getchildren():
            self.assertEqual(str(nod), xmlequiv[ix])
            ix += 1
        

    def test_identities(self):
        idls = [
            ('server', 'im', None),
            ('client', 'muc', 'Usenet'),
        ]
        xmlequiv = [
            '<identity category="server" type="im" />',
            '<identity category="client" type="muc" name="Usenet" />',
        ]

        nodls = makediscoidentities(idls)

        ix = 0
        for nod in nodls:
            self.assertEqual(str(nod), xmlequiv[ix])
            tup = parsediscoidentity(nod)
            self.assertEqual(tup, idls[ix])
            ix += 1

    def test_features(self):
        idls = [
            'http://jabber.org/protocol/disco#info',
            'jabber:iq:register',
            'http://jabber.org/protocol/muc',
        ]
        xmlequiv = [
            '<feature var="http://jabber.org/protocol/disco#info" />',
            '<feature var="jabber:iq:register" />',
            '<feature var="http://jabber.org/protocol/muc" />',
        ]

        nodls = makediscofeatures(idls)
        
        ix = 0
        for nod in nodls:
            self.assertEqual(str(nod), xmlequiv[ix])
            st = parsediscofeature(nod)
            self.assertEqual(st, idls[ix])
            ix += 1

    def test_extended(self):
        origls = [
            ('port', '5222', None, None),
            ('ip', 'ipv4', None, None),
            ('url', 'http://jabber.org/', 'the url', None),
        ]
        
        form = dataform.DataForm()
        form.addfield('port', '5222')
        form.addfield('ip', 'ipv4')
        form.addfield('url', 'http://jabber.org/', 'the url')

        nod = makediscoextended(form)

        form2 = parsediscoextended(nod)
        ls = form2.getfields()

        self.assertEqual(ls, origls)
