import codecs
import xml.parsers.expat
import unittest

class Node:
    """Node: A class representing a node in an XML DOM tree.

    This is a lightweight XML representation -- I didn't want to use
    the full, foot-crushing power of xml.dom (from the standard Python
    library). Each Node represents an XML tag (or tree of tags). There
    are no separate objects for documents, attributes, data, etc.
    It's just tags, with stuff in them.

    Node(name, attrs=None, namespace=None, parent=None, children=None,
        data=None) -- constructor.

    *name* and *namespace* are what they say; these must be strings
    (or unicode). If *attrs* (a dict) is provided, the new node is
    given those attributes. If the *parent* is provided, the new node
    is appended to its contents. If the *children* (a list) are
    provided, they are appended to the new node's contents. If the
    *data* is provided (a string or unicode object, or something
    convertible to unicode), it is appended as well.

    A Node's namespace is assumed to be inherited from its parent, if
    you provide none. (This means that the value of nod.getnamespace()
    can change after you add it as a child to some other Node.)

    (You can also provide a namespace by putting an 'xmlns' key in
    *attrs*. This is equivalent to providing a *namespace*.)

    A Node can only have one parent. If you try to add a Node somewhere
    as a child, and it already has a parent, you will get a ValueError.
    (If you call the node's remove() method, it will become parentless, 
    and you can then give it a new parent.)

    Don't do crazy stuff like setting a Node to be its own grandpa.

    Class methods:

    escapetext() -- escape <, >, ", and & symbols in a string.
    parse() -- parse a string into a Node tree.
    parsefile() -- parse a file into a Node tree.

    Public methods:

    delete() -- destroy a Node and its children.
    copy() -- return a (deep) copy of a Node.
    getname() -- return the Node's tag name.
    getnamespace() -- return the Node's namespace.
    setnamespace() -- set (or clear) the Node's namespace.
    getcontents() -- return the Node's list of Nodes and data.
    
    addchild() -- add a Node as a child of this Node.
    removechild() -- remove a Node as a child of this Node.
    remove() -- remove this Node from its parent, if any.
    getparent() -- return the Node's parent.
    setparent() -- add this Node as a child of some other Node.
    getchild() -- return a child of the Node, according to criteria.
    getchildren() -- return some children of the Node, according to criteria.
    
    getdata() -- return all the data in the Node.
    setdata() -- change the data in the Node.
    adddata() -- append data to the Node.
    cleardata() -- clear all data from the Node.
    
    getattrs() -- return all attributes in the Node.
    setattrs() -- set several attributes in the Node.
    clearattrs() -- clear all attributes from the Node.
    
    getattr() -- return one attribute in the Node.
    getattrdef() -- return one attribute in the Node.
    hasattr() -- test if the Node has a given attribute.
    setattr() -- set an attribute in the Node.
    clearattr() -- clear an attribute from the Node.
    
    setchild() -- find or create a child, according to criteria.
    getchilddata() -- return the data from one child of the Node.
    setchilddata() -- find or create a child, according to criteria, and
        set its data.
    addchilddata() -- find or create a child, according to criteria, and
        append to its data.
    getchildattr() -- return an attribute from one child of the Node.
    getchildattrdef() -- return an attribute from one child of the Node.
    setchildattr() -- find or create a child, according to criteria, and
        set an attribute in it.

    serialize() -- create a string representing the Node as XML.
    """
    
    def __init__(self, name, attrs=None, namespace=None, parent=None,
        children=None, data=None):

        if (attrs and attrs.has_key('xmlns')):
            ns = attrs.pop('xmlns')
            if (not namespace):
                namespace = ns

        if (not name):
            raise ValueError('Node must have a name')        
        self.name = name
        self.namespace = namespace
        self.attrs = {}
        self.contents = []
        self.parent = None
        
        if (parent):
            if (not isinstance(parent, Node)):
                raise TypeError('parent must be a Node')
            parent.addchild(self)
            
        if (attrs):
            for key in attrs.keys():
                self.setattr(key, attrs[key])

        self.adddata(data)

        if (children):
            if (isinstance(children, Node)):
                children = [children]
            for nod in children:
                self.addchild(nod)

    def __str__(self):
        return str(self.serialize())

    def __unicode__(self):
        return unicode(self.serialize())

    def copy(self):
        """copy() -> Node

        Return a (deep) copy of a Node.

        The copy will not have a parent, even if this Node does. (But it
        will copy an inherited namespace as an explicit one.)
        """
        
        nod = Node(self.getname(), self.getattrs(), self.getnamespace())
        for val in self.getcontents():
            if (isinstance(val, Node)):
                subnod = val.copy()
                nod.addchild(subnod)
            else:
                nod.adddata(val)

        return nod

    def delete(self):
        """delete() -> None

        Destroy a Node and its children. You don't have to do this, but it
        gets rid of the object without relying on garbage collection.
        """
        
        if (self.parent):
            self.parent.removechild(self)
        for nod in self.contents:
            if (isinstance(nod, Node)):
                nod.delete()
        self.attrs = None
        self.contents = []
        self.parent = None

    def getname(self):
        """getname() -> str/unicode

        Return the Node's tag name.
        """
        
        return self.name
        
    def getnamespace(self, default=None):
        """getnamespace(default=None) -> ns

        Return the Node's namespace. If the Node has no namespace set for
        itself, this returns its parent's namespace, if it has a parent.
        If not, it returns None (or *default*).
        """
        
        nod = self
        while (nod):
            if (nod.namespace):
                return nod.namespace
            nod = nod.parent
        return default
        
    def setnamespace(self, ns):
        """setnamespace(ns) -> None

        Set (or clear) the Node's namespace. If *ns* is a string (or unicode),
        this sets the namespace. If *ns* is None, it clears it.

        If a Node's namespace is unset, it inherits the namespace of its
        parent (if it has a parent). Therefore, calling setnamespace(None)
        does not imply that getnamespace() will return None.
        """
        
        if (not ns):
            self.namespace = None
        else:
            self.namespace = ns

    def getcontents(self):
        """getcontents() -> list

        Return the Node's list of Nodes and data. Data elements are
        represented as string/unicode objects. They are concatenated if
        possible -- you will not see two data elements in a row.
        """
        
        ls = []
        data = []
        for val in self.contents:
            if (isinstance(val, Node)):
                if (data):
                    ls.append(''.join(data))
                    data = []
                ls.append(val)
            else:
                data.append(val)
        if (data):
            ls.append(''.join(data))
            data = []
        return ls
        
    def addchild(self, nod):
        """addchild(node) -> None

        Add *node* as a child of this Node. The *node* must not already
        have a parent. If the Node already has child nodes or data, the new
        child is placed at the end.
        """
        
        if (not isinstance(nod, Node)):
            raise TypeError('children must be Nodes')
        if (nod.parent):
            raise ValueError('child node already has a parent')
        self.contents.append(nod)
        nod.parent = self

    def removechild(self, nod, preservenamespace=False):
        """removechild(node, preservenamespace=False) -> Node

        Remove *node* as a child of this Node. Returns *node*.

        If *preservenamespace* is True, and *node* was inheriting its
        namespace from its parent, then *node* will get that namespace
        explicitly set. (So calling getnamespace() on it after removal
        will return the same value as before.)
        """
        
        if (not isinstance(nod, Node)):
            raise TypeError('children must be Nodes')
        if (not nod in self.contents):
            raise ValueError('not a child of this Node')
        nod.remove(preservenamespace=preservenamespace)
        return nod

    def remove(self, preservenamespace=False):
        """remove(preservenamespace=False) -> None

        Remove this Node from its parent, if any. (If it had no parent,
        this method does nothing.)

        If *preservenamespace* is True, and the node was inheriting its
        namespace from its parent, then it will get that namespace
        explicitly set. (So calling getnamespace() on it after removal
        will return the same value as before.)
        """
        
        if (not self.parent):
            return
        if (preservenamespace and not self.namespace):
            ns = self.getnamespace()
            self.setnamespace(ns)
        self.parent.contents.remove(self)
        self.parent = None

    def getparent(self):
        """getparent() -> Node

        Return the Node's parent. If it had none, returns None.
        """
        
        return self.parent

    def setparent(self, nod):
        """setparent(node) -> None

        Add this Node as a child of *node*. This node must not already
        have a parent. If *node* already has child nodes or data, the new
        child is placed at the end.
        """
        
        nod.addchild(self)

    def getchild(self, name=None, attrs=None, namespace=None):
        """getchild(name=None, attrs=None, namespace=None) -> Node

        Return a child of the Node, according to criteria.

        If no parameters are supplied, this returns the first of the Node's
        child nodes. The parameters put conditions on the children which
        are considered:

            name=string: Only children whose tag names match.
            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)

        If there are no matches, None is returned.
        """
        
        return self.getchildren(name=name, attrs=attrs, namespace=namespace,
            first=True)
    
    def getchildren(self, name=None, attrs=None, namespace=None, first=False):
        """getchildren(name=None, attrs=None, namespace=None, first=False)
            -> list

        Return some children of the Node, according to criteria.

        If no parameters are supplied, this returns all the Node's child
        nodes, as a list. The parameters filter the list:

            name=string: Only children whose tag names match.
            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)

        If *first* is True, the first matching child is returned, instead
        of a list of all matches. If there are no matches, None is returned.
        """
        
        if (attrs and attrs.has_key('xmlns')):
            ns = attrs.pop('xmlns')
            if (not namespace):
                namespace = ns
                
        ls = [ val for val in self.contents if isinstance(val, Node) ]
        if (name != None):
            ls = [ nod for nod in ls if nod.name == name ]
        if (namespace != None):
            ls = [ nod for nod in ls if nod.getnamespace() == namespace ]
        if (attrs):
            for key in attrs.keys():
                val = attrs[key]
                ls = [ nod for nod in ls if nod.attrs.get(key) == val ]
                
        if (first):
            if (not ls):
                return None
            return ls[0]
        else:
            return ls

    def getdata(self):
        """getdata() -> str/unicode

        Return all the data in the Node. If there are multiple elements,
        they are concatenated, ignoring the child Nodes between them.
        """
        
        ls = [ val for val in self.contents if not isinstance(val, Node) ]
        return ''.join(ls)

    def setdata(self, data):
        """setdata(data) -> None

        Change the data in the Node. Previously existing data elements
        are removed, and the new data is added. (After any child nodes,
        if there are any.)
        
        The *data* must be str, unicode, or convertible to unicode. (Or
        None, which is considered equivalent to ''.)
        """
        
        self.cleardata()
        self.adddata(data)

    def adddata(self, data):
        """adddata(data) -> None

        Append data to the Node. (After any child nodes, if there are any.)
        The *data* must be str, unicode, or convertible to unicode. (Or
        None, which is considered equivalent to ''.)
        """
        
        if (data):
            if (not type(data) in [str, unicode]):
                if (isinstance(data, Node)):
                    raise TypeError('cannot pass a Node to adddata')
                data = unicode(data)
            self.contents.append(data)
        
    def cleardata(self):
        """cleardata() -> None

        Clear all data from the Node. Previously existing data elements
        are removed.
        """
        
        ls = [ val for val in self.contents if isinstance(val, Node) ]
        self.contents = ls

    def getattrs(self):
        """getattrs() -> dict

        Return all attributes in the Node. You should not modify the
        returned dict.

        The dict will not contain 'xmlns'. Call getnamespace() to see the
        Node's namespace.
        """
        
        return self.attrs

    def setattrs(self, arg_=None, **dic):
        """setattrs(val=None, **dic) -> None

        Set (or clear) several attributes in the Node. You get several
        options for setting this up:

            setattrs( fish='trout', fowl='dove' )
            setattrs( {'fish':'trout', 'fowl':'dove'} )
            setattrs( [ ('fish','trout'), ('fowl','dove') ] )
            setattrs(val)    # *val* is anything which can be cast to a dict

        Any key whose value is None (or '') causes the removal of that
        attribute (if present).

        The key 'xmlns' sets or clears the Node's namespace.
        """
        
        if (arg_ != None):
            arg_ = dict(arg_)
            dic.update(arg_)
        for key in dic.keys():
            val = dic[key]
            self.setattr(key, val)

    def clearattrs(self):
        """clearattrs() -> None

        Clear all attributes from the Node. (Except namespace; this method
        does not affect that.)
        """
        
        self.attrs.clear()

    def getattr(self, key, default=None):
        """getattr(key, default=None) -> str/unicode

        Return one attribute in the Node. If the attribute is not present,
        returns None (or *default*).

        If *key* is 'xmlns', returns the namespace (which may be inherited).
        """
        
        if (key == 'xmlns'):
            return self.getnamespace(default)
        return self.attrs.get(key, default)
    
    def getattrdef(self, key, *default):
        """getattrdef(key [, default] ) -> str/unicode

        Return one attribute in the Node. If the attribute is not present,
        raises KeyError (unless *default* has been provided, in which case
        it returns that.).

        If *key* is 'xmlns', returns the namespace (which may be inherited).
        """
        
        if (key == 'xmlns'):
            return self.getnamespace(*default)
        if (not default):
            return self.attrs[key]
        return self.attrs.get(key, *default)

    def hasattr(self, key):
        """hasattr(key) -> bool

        Test if the Node has a given attribute.

        If *key* is 'xmlns', returns whether the Node has a namespace.
        (Inherited namespaces count.)
        """
        
        if (key == 'xmlns'):
            return bool(self.getnamespace())
        return self.attrs.has_key(key)
        
    def setattr(self, key, data):
        """setattr(key, data) -> None

        Set an attribute in the Node. The *data* must be str, unicode, or
        convertible to unicode. If *data* is None (or ''), this removes
        the attribute instead (if present).

        If *key* is 'xmlns', this sets or clears the namespace.
        """
        
        if (not type(key) in [str, unicode]):
            raise TypeError('key must be a string')
        if (' ' in key):
            raise ValueError('attribute names cannot contain spaces')
        if (data):
            if (not type(data) in [str, unicode]):
                data = unicode(data)
            if (key == 'xmlns'):
                self.setnamespace(data)
            else:
                self.attrs[key] = data
        else:
            if (key == 'xmlns'):
                self.setnamespace(None)
            else:
                self.attrs.pop(key, None)

    def clearattr(self, key):
        """clearattr(key) -> None

        Clear an attribute from the Node.
        
        If *key* is 'xmlns', this clears the namespace.
        """

        if (key == 'xmlns'):
            self.setnamespace(None)
        else:
            self.attrs.pop(key, None)

    def setchild(self, name, attrs=None, namespace=None):
        """setchild(name, attrs=None, namespace=None) -> Node

        Find or create a child, according to criteria.

        This finds the first child node whose name is *name*. If there is
        no such node, it creates one. It then returns the node that was
        found or created.

        You can supply additional conditions for which children to
        consider:

            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)

        If a new node is created, these values are applied to it.
        """
        
        nod = self.getchild(name=name, attrs=attrs, namespace=namespace)
        if (not nod):
            nod = Node(name=name, attrs=attrs, namespace=namespace,
                parent=self)
        return nod
        
    def getchilddata(self, name=None, attrs=None, namespace=None):
        """getchilddata(name=None, attrs=None, namespace=None) -> str/unicode

        Return the data from one child of the Node.
        
        This finds the first child node, and returns all its data.
        (If there are no children, it returns None.) If the child has
        multiple data elements, they are concatenated, ignoring the Nodes
        between them.
        
        You can supply additional conditions for which children to
        consider:

            name=string: Only children whose tag names match.
            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)
        """
        
        nod = self.getchild(name=name, attrs=attrs, namespace=namespace)
        if (not nod):
            return None
        return nod.getdata()

    def setchilddata(self, name, data='', attrs=None, namespace=None):
        """setchilddata(name, data='', attrs=None, namespace=None) -> Node

        Find or create a child, according to criteria, and set its data.

        This finds the first child node whose name is *name*. If there is
        no such node, it creates one. It then sets the child's character
        data to *data*. Finally, it returns the node that was found or
        created.

        You can supply additional conditions for which children to
        consider:

            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)

        If a new node is created, these values are applied to it.
        """
        
        nod = self.setchild(name=name, attrs=attrs, namespace=namespace)
        nod.setdata(data)
        return nod

    def addchilddata(self, name, data='', attrs=None, namespace=None):
        """addchilddata(name, data='', attrs=None, namespace=None) -> Node

        Find or create a child, according to criteria, and append to its
        data.
        
        This finds the first child node whose name is *name*. If there is
        no such node, it creates one. It then appends *data* to the child's
        character data. Finally, it returns the node that was found or
        created.

        You can supply additional conditions for which children to
        consider:

            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)

        If a new node is created, these values are applied to it.
        """
        
        nod = self.setchild(name=name, attrs=attrs, namespace=namespace)
        nod.adddata(data)
        return nod

    def getchildattr(self, key, default=None,
        name=None, attrs=None, namespace=None):
        """getchildattr(key, default=None, name=None, attrs=None,
            namespace=None) -> str/unicode

        Return an attribute from one child of the Node.

        This finds the first child node, and returns the value of its
        *key* attribute. (If there is no such attribute, this returns
        None, or *default*. If there are no children, same thing.)
        
        You can supply additional conditions for which children to
        consider:

            name=string: Only children whose tag names match.
            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)
        """
        
        nod = self.getchild(name=name, attrs=attrs, namespace=namespace)
        if (not nod):
            return default
        return nod.getattr(key, default)

    def getchildattrdef(self, key, default=KeyError,
        name=None, attrs=None, namespace=None):
        """getchildattrdef(key, default=KeyError, name=None, attrs=None,
            namespace=None) -> str/unicode
            
        Return an attribute from one child of the Node.

        This finds the first child node, and returns the value of its
        *key* attribute. (If there is no such attribute, this raises
        KeyError -- unless *default* is supplied, in which case it
        returns that. If there are no children, same thing.)
        
        You can supply additional conditions for which children to
        consider:

            name=string: Only children whose tag names match.
            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)
        """
        
        nod = self.getchild(name=name, attrs=attrs, namespace=namespace)
        if (not nod):
            if (default == KeyError):
                raise KeyError('no such child')
            else:
                return default
        if (default == KeyError):
            return nod.getattrdef(key)
        else:
            return nod.getattrdef(key, default)

    def setchildattr(self, name, key, data,
        attrs=None, namespace=None):
        """setchildattr(name, key, data, attrs=None, namespace=None) -> Node

        This finds the first child node whose name is *name*. If there is
        no such node, it creates one. It then sets the child's *key*
        attribute to *data*. (If *data* is None or '', it instead
        removes the *key* attribute, if present.) Finally, it returns
        the node that was found or created.

        You can supply additional conditions for which children to
        consider:

            attrs=dict: Only children who match each of the given attributes.
            namespace=string: Only children whose namespace matches.
                (Inherited namespaces count.)

        (You can also match the namespace by putting an 'xmlns' key in
        *attrs*.)

        If a new node is created, these values are applied to it.
        """
        
        nod = self.setchild(name=name, attrs=attrs, namespace=namespace)
        nod.setattr(key, data)
        return nod
        
    def escapetext(st):
        """escapetext(str) -> str

        Escape <, >, ", and & symbols in a string or unicode object.
        """
        
        st = st.replace('&', '&amp;')
        st = st.replace('"', '&quot;')
        st = st.replace('<', '&lt;')
        st = st.replace('>', '&gt;')
        return st
    escapetext = staticmethod(escapetext)

    def serialize(self, pretty=False, depth=0, namespace=None):
        """serialize(pretty=False) -> str/unicode

        Create a string representing the Node as XML. The result will
        be str if possible, otherwise unicode. (In practice, usually
        unicode.)

        If *pretty* is True, the string will contain pretty indentation and
        line breaks. (It will not end with a linebreak.) Pretty mode also
        skips printing whitespace between tags.
        """
        
        st = []
        if (pretty):
            st.append('  ' * depth)
        st.extend([ '<', self.name ])

        if (depth == 0):
            selfns = self.getnamespace()
        else:
            selfns = self.namespace
        if (selfns and namespace != selfns):
            st.extend([' xmlns="', selfns, '"'])
            namespace = selfns
            
        for key in self.attrs.keys():
            val = self.attrs[key]
            st.extend([' ', key, '="', self.escapetext(val), '"'])
            
        if (not self.contents):
            st.append(' />')
        else:
            st.append('>')
            ls = self.getcontents()
            prettysquish = (len(ls) == 1
                and (not isinstance(ls[0], Node)))
            if (pretty and not prettysquish):
                st.append('\n')
            for val in ls:
                if (isinstance(val, Node)):
                    st2 = val.serialize(pretty, depth+1, namespace)
                    st.append(st2)
                    if (pretty):
                        st.append('\n')
                else:
                    if (pretty and not prettysquish):
                        val = val.strip()
                        if (not val):
                            continue
                    if (pretty and not prettysquish):
                        st.append('  ' * (depth+1))
                    st.append(self.escapetext(val))
                    if (pretty and not prettysquish):
                        st.append('\n')
            if (pretty and not prettysquish):
                st.append('  ' * depth)
            st.extend([ '</', self.name, '>'])
        return ''.join(st)

    def parse(data, encoding='UTF-8'):
        """parse(data, encoding='UTF-8') -> Node

        Parse a string into a Node tree. If the string is not well-formed
        XML, this raises xml.parsers.expat.ExpatError.

        By default, *encoding* is UTF-8, and so the *data* must be a string
        in that encoding. You can supply other encodings. If *encoding* is
        None, the *data* should be a unicode object (or convertible to
        unicode).
        """
        
        if (encoding == None):
            encoding = 'UTF-8'
            udata = unicode(data)
            (data, dummy) = codecs.getencoder(encoding)(udata)
        parser = StaticNodeGenerator(data, encoding=encoding)
        nod = parser.get()
        parser.close()
        return nod
    parse = staticmethod(parse)

    def parsefile(file, encoding='UTF-8'):
        """parsefile(file, encoding='UTF-8') -> Node

        Parse the contents of a file into a Node tree. If the data is not
        well-formed XML, this raises xml.parsers.expat.ExpatError.

        By default, *encoding* is UTF-8, and so the *file* must contain data
        in that encoding. You can supply other encodings.

        This leaves *file* open, but at EOF.
        """
        
        if (encoding == None):
            raise Exception('you must give an encoding')
        parser = FileNodeGenerator(file, encoding=encoding)
        nod = parser.get()
        parser.close()
        return nod
    parsefile = staticmethod(parsefile)

class NodeGenerator:
    """NodeGenerator: A base class for utilities which parse strings into
    Node trees.

    This class is not useful on its own. See StaticNodeGenerator for a
    functional use of it.

    NodeGenerator(encoding='UTF-8') -- constructor.

    By default, *encoding* is UTF-8, and so the data must be a string
    in that encoding. You can supply other encodings. If *encoding* is
    None, the data should be unicode objects (or convertible to
    unicode).

    Public method:

    close() -- shut down the NodeGenerator.

    Internal methods:

    handle_startnamespace() -- expat parser callback.
    handle_startelement() -- expat parser callback.
    handle_endelement() -- expat parser callback.
    handle_data() -- expat parser callback.
    """
    
    def __init__(self, encoding='UTF-8'):
        self.result = None
        self.curnode = None
        self.depth = 0
        self.namespaces = { 'http://www.w3.org/XML/1998/namespace' : 'xml' }
        
        self.parser = xml.parsers.expat.ParserCreate(encoding=encoding,
            namespace_separator=' ')
        self.parser.returns_unicode = True

        self.parser.StartNamespaceDeclHandler = self.handle_startnamespace
        self.parser.StartElementHandler = self.handle_startelement
        self.parser.EndElementHandler = self.handle_endelement
        self.parser.CharacterDataHandler = self.handle_data

    def close(self):
        """close() -> None

        Shut down the NodeGenerator. You don't have to do this, but it
        gets rid of the object without relying on garbage collection.
        """
        
        self.parser.StartNamespaceDeclHandler = None
        self.parser.StartElementHandler = None
        self.parser.EndElementHandler = None
        self.parser.CharacterDataHandler = None
        
        self.result = None
        self.parser = None

    def handle_startnamespace(self, prefix, uri):
        """handle_startnamespace() -- expat parser callback. Do not call.
        """
        
        self.namespaces[uri] = prefix

    def handle_startelement(self, name, attrs):
        """handle_startelement() -- expat parser callback. Do not call.
        """
        
        for key in attrs.keys():
            pos = key.find(' ')
            if (pos >= 0):
                val = attrs.pop(key)
                keyns = key[ : pos ]
                keyname = key[ pos+1 : ]
                key = self.namespaces[keyns] + ':' + keyname
                attrs[key] = val
    
        namespace = None
        pos = name.find(' ')
        if (pos >= 0):
            namespace = name[ : pos ]
            name = name[ pos+1 : ]
        if (self.depth == 0):
            if (self.result):
                raise Exception('got top-level StartElement twice')
            self.result = Node(name, attrs, namespace=namespace)
            self.curnode = self.result
        else:
            nod = Node(name, attrs, namespace=namespace, parent=self.curnode)
            self.curnode = nod
        self.depth += 1

    def handle_endelement(self, name):
        """handle_endelement() -- expat parser callback. Do not call.
        """
        
        pos = name.find(' ')
        if (pos >= 0):
            name = name[ pos+1 : ]
        if (name != self.curnode.getname()):
            raise Exception('EndElement name did not match StartElement')
        self.curnode = self.curnode.getparent()
        self.depth -= 1

    def handle_data(self, data):
        """handle_data() -- expat parser callback. Do not call.
        """
        
        self.curnode.adddata(data)
    
    
class StaticNodeGenerator(NodeGenerator):
    """StaticNodeGenerator: A NodeGenerator which parses a single string.

    StaticNodeGenerator(data, encoding='UTF-8') -- constructor.
    
    By default, *encoding* is UTF-8, and so the *data* must be a string
    in that encoding. You can supply other encodings. If *encoding* is
    None, the *data* should be a unicode object (or convertible to
    unicode).

    Public method:

    get() -- return the Node tree.
    """
    
    def __init__(self, data, encoding='UTF-8'):
        self.staticdata = data
        self.parsed = False
        NodeGenerator.__init__(self, encoding=encoding)
        
    def get(self):
        """get() -> Node

        Return the Node tree. If the data passed to the constructor was
        not well-formed, this raises xml.parsers.expat.ExpatError.
        """
        
        if (not self.parsed):
            self.parser.Parse(self.staticdata, True)
            self.parsed = True
        return self.result

class FileNodeGenerator(NodeGenerator):
    """FileNodeGenerator: A NodeGenerator which parses a single string.

    FileNodeGenerator(file, encoding='UTF-8') -- constructor.
    
    By default, *encoding* is UTF-8, and so the *file* must contain data
    in that encoding. You can supply other encodings. If *encoding* is
    None, bad things will probably happen.

    Public method:

    get() -- return the Node tree.
    """
    
    def __init__(self, file, encoding='UTF-8'):
        self.file = file
        NodeGenerator.__init__(self, encoding=encoding)

    def get(self):
        """get() -> Node

        Return the Node tree. This parses the entire file, reading it in
        one line at a time. If the data is not well-formed, this raises
        xml.parsers.expat.ExpatError.
        """
        
        while (True):
            ln = self.file.readline()
            if (not ln):
                break
            self.parser.Parse(ln)
        self.parser.Parse('', True)
        return self.result
        
# ------------------- unit tests -------------------

class TestXMLData(unittest.TestCase):
    """Unit tests for the xmldata module.
    """

    def test_data(self):
        nod = Node('tag')
        self.assertEqual(nod.getdata(), '')
        nod.adddata('frog')
        self.assertEqual(nod.getdata(), 'frog')
        nod.setdata('hello')
        self.assertEqual(nod.getdata(), 'hello')
        nod.adddata('goodbye')
        self.assertEqual(nod.getdata(), 'hellogoodbye')
        nod.adddata(u'b\xe9d')
        self.assertEqual(nod.getdata(), u'hellogoodbyeb\xe9d')
        nod.cleardata()
        self.assertEqual(nod.getdata(), '')
        nod.adddata(117)
        self.assertEqual(nod.getdata(), '117')

        nod = Node('tag', data='string')
        self.assertEqual(nod.getdata(), 'string')

    def test_contents(self):
        nod = Node('tag')
        nod.adddata('11')
        nod.adddata('')
        nod.adddata('22')
        nod2 = Node('child')
        nod.addchild(nod2)
        nod.adddata('33')
        nod.adddata('44')
        ls = nod.getcontents()
        self.assertEqual(ls, ['1122', nod2, '3344'])
    
    def test_child(self):
        nod = Node('tag')
        nod2 = Node('child', parent=nod)
        nod.cleardata()
        self.assertEqual(nod.getchildren(), [nod2])
        self.assertEqual(nod2.parent, nod)
        nod.removechild(nod2)
        self.assertEqual(nod.getchildren(), [])
        self.assertEqual(nod2.parent, None)

        nod = Node('tag', data='hello')
        nod2 = Node('child')
        nod.addchild(nod2)
        self.assertEqual(nod.getchildren(), [nod2])
        self.assertEqual(nod2.parent, nod)
        nod.removechild(nod2)
        self.assertEqual(nod.getchildren(), [])
        self.assertEqual(nod2.parent, None)
        self.assertEqual(nod.getdata(), 'hello')

        nod2 = Node('child')
        nod = Node('tag', children=[nod2])
        self.assertEqual(nod.getchildren(), [nod2])
        self.assertEqual(nod2.parent, nod)
        
        nod2 = Node('child')
        nod = Node('tag', children=nod2)
        self.assertEqual(nod.getchildren(), [nod2])
        self.assertEqual(nod2.parent, nod)

        nod2.delete()
        self.assertEqual(nod.getchildren(), [])
        self.assertEqual(nod2.parent, None)

    def test_namespace(self):
        nod = Node('tag')
        self.assertEqual(nod.getnamespace(), None)
        self.assertEqual(nod.getnamespace('cheese'), 'cheese')
        self.assert_(not nod.hasattr('xmlns'))
        nod = Node('tag', namespace='frog')
        self.assertEqual(nod.getnamespace(), 'frog')
        self.assert_(nod.hasattr('xmlns'))
        nod.setnamespace('toad')
        self.assertEqual(nod.getnamespace(), 'toad')
        self.assert_(nod.hasattr('xmlns'))

        nod = Node('tag', namespace='frog')
        nod2 = Node('child', parent=nod)
        self.assertEqual(nod2.getnamespace(), 'frog')
        self.assert_(nod2.hasattr('xmlns'))
        nod2.setnamespace('toad')
        self.assertEqual(nod2.getnamespace(), 'toad')
        nod2.setnamespace(None)
        self.assertEqual(nod2.getnamespace(), 'frog')
        nod2.setnamespace('')
        self.assertEqual(nod2.getnamespace(), 'frog')
        self.assert_(nod2.hasattr('xmlns'))

        nod = Node('tag', namespace='frog')
        nod2 = Node('child')
        self.assertEqual(nod2.getnamespace(), None)
        nod2.setparent(nod)
        self.assertEqual(nod2.getnamespace(), 'frog')
        nod2.remove(True)
        self.assertEqual(nod2.getnamespace(), 'frog')
        nod2 = Node('child')
        self.assertEqual(nod2.getnamespace(), None)
        nod2.setparent(nod)
        self.assertEqual(nod2.getnamespace(), 'frog')
        nod2.remove(False)
        self.assertEqual(nod2.getnamespace(), None)

    def test_attrs(self):
        nod = Node('tag')
        val = nod.getattrdef('key', None)
        self.assertEqual(val, None)
        val = nod.getattr('key')
        self.assertEqual(val, None)
        val = nod.getattrdef('key', 'none')
        self.assertEqual(val, 'none')
        val = nod.getattr('key', 'none')
        self.assertEqual(val, 'none')
        self.assertRaises(KeyError, nod.getattrdef, 'key')
        self.assert_(not nod.hasattr('key'))

        nod.setattr('frog', 'toad')
        val = nod.getattrdef('frog')
        self.assertEqual(val, 'toad')
        val = nod.getattr('frog')
        self.assertEqual(val, 'toad')
        self.assert_(nod.hasattr('frog'))
        val = nod.getattrdef('frog', 'lizard')
        self.assertEqual(val, 'toad')
        val = nod.getattr('frog', 'lizard')
        self.assertEqual(val, 'toad')

        self.assertEqual(nod.getattrs(), {'frog':'toad'})

        nod.setattr('frog', '')
        val = nod.getattrdef('frog', None)
        self.assertEqual(val, None)
        
        nod.setattr('frog', 'toad')
        nod.clearattr('frog')
        val = nod.getattrdef('frog', None)
        self.assertEqual(val, None)

        nod.setattrs(newt='newtskin', lizard='scale')
        val = nod.getattrdef('newt')
        self.assertEqual(val, 'newtskin')
        val = nod.getattrdef('lizard')
        self.assertEqual(val, 'scale')
        nod.setattrs( {'worm':'wormcast'} )
        val = nod.getattrdef('newt')
        self.assertEqual(val, 'newtskin')
        val = nod.getattrdef('worm')
        self.assertEqual(val, 'wormcast')

        nod.clearattrs()
        self.assertRaises(KeyError, nod.getattrdef, 'newt')
        self.assertRaises(KeyError, nod.getattrdef, 'lizard')
        self.assertRaises(KeyError, nod.getattrdef, 'worm')
        val = nod.getattr('newt')
        self.assertEqual(val, None)
        val = nod.getattr('lizard')
        self.assertEqual(val, None)
        val = nod.getattr('worm')
        self.assertEqual(val, None)

        nod = Node('tag', attrs={'frog':'toad'})
        val = nod.getattrdef('frog')
        self.assertEqual(val, 'toad')

        nod.setattr('frog', 17)
        val = nod.getattrdef('frog')
        self.assertEqual(val, '17')
        nod.setattr('frog', u'h\xe9llo')
        val = nod.getattrdef('frog')
        self.assertEqual(val, u'h\xe9llo')

    def test_namespaceattr(self):
        nod = Node('tag')
        nod.setattr('xmlns', 'frog')
        self.assertEqual(nod.getattrs(), {})
        self.assertEqual(nod.getnamespace(), 'frog')

        nod = Node('tag', attrs={'xmlns':'frog', 'newt':'lizard'})
        self.assertEqual(nod.getattrs(), {'newt':'lizard'})
        self.assertEqual(nod.getnamespace(), 'frog')

        nod.clearattrs()
        nod.setattr('xmlns', '')
        self.assertEqual(nod.getattrs(), {})
        self.assertEqual(nod.getnamespace(), None)

        nod.setattrs(xmlns='toad', newt='lizard')
        self.assertEqual(nod.getattrs(), {'newt':'lizard'})
        self.assertEqual(nod.getnamespace(), 'toad')
        
        nod.clearattrs()
        nod.setnamespace('worm')
        val = nod.getattrdef('xmlns')
        self.assertEqual(val, 'worm')

    def test_getchildren(self):
        nod = Node('tag')
        nod2 = Node('child', parent=nod)
        nod3 = Node('child', parent=nod)
        nod4 = Node('child', parent=nod, attrs={'frog':'toad'})
        nod5 = Node('egg', parent=nod, attrs={'frog':'toad'})
        nod6 = Node('egg', parent=nod, attrs={'frog':'lizard'})
        nod7 = Node('egg', parent=nod, attrs={'key':'toad'})
        nod8 = Node('child', parent=nod, namespace='html')
        nod9 = Node('thing', parent=nod, namespace='html',
            attrs={'key':'toad'})
        nod.setdata('I am the data')

        ls = nod.getchildren()
        self.assertEqual(ls, [nod2, nod3, nod4, nod5, nod6, nod7, nod8, nod9])
        val = nod.getchildren(first=True)
        self.assertEqual(val, nod2)
        ls = nod.getchildren(name='blarg')
        self.assertEqual(ls, [])
        val = nod.getchildren(first=True, name='blarg')
        self.assertEqual(val, None)
        ls = nod.getchildren(name='child')
        self.assertEqual(ls, [nod2, nod3, nod4, nod8])
        ls = nod.getchildren(name=u'egg')
        self.assertEqual(ls, [nod5, nod6, nod7])
        ls = nod.getchildren(namespace='html')
        self.assertEqual(ls, [nod8, nod9])
        ls = nod.getchildren(name='child', namespace='html')
        self.assertEqual(ls, [nod8])
        ls = nod.getchildren(attrs={'frog':'toad'})
        self.assertEqual(ls, [nod4, nod5])
        ls = nod.getchildren(name='egg', attrs={'frog':'toad'})
        self.assertEqual(ls, [nod5])
        ls = nod.getchildren(attrs={'spaz':'toad'})
        self.assertEqual(ls, [])
        ls = nod.getchildren(attrs={'xmlns':'html'})
        self.assertEqual(ls, [nod8, nod9])
        val = nod.getchild(name='egg')
        self.assertEqual(val, nod5)

    def test_setchild(self):
        nod = Node('tag')
        nod2 = nod.setchild('child')
        self.assertEqual(nod2.getname(), 'child')
        self.assertEqual(nod2.getparent(), nod)

        nod3 = nod.setchilddata('child', 'hello')
        self.assertEqual(nod2, nod3)
        self.assertEqual(nod2.getdata(), 'hello')
        
        nod3 = nod.setchilddata('child', 'hello')
        self.assertEqual(nod2, nod3)
        self.assertEqual(nod2.getdata(), 'hello')
        
        nod3 = nod.addchilddata('more', 'good')
        nod4 = nod.addchilddata('more', 'bye')
        self.assertEqual(nod3, nod4)
        self.assertNotEqual(nod2, nod3)
        self.assertEqual(nod3.getdata(), 'goodbye')
        
        nod3 = nod.setchildattr('child', 'frog', 'toad', namespace='html')
        self.assertNotEqual(nod2, nod3)
        self.assertEqual(nod3.getnamespace(), 'html')
        self.assertEqual(nod3.getattrdef('frog'), 'toad')

        nod3 = nod.setchild('ping').setchilddata('pong', 'data')
        self.assertEqual(nod3.getdata(), 'data')
        self.assertEqual(nod3.getname(), 'pong')
        self.assertEqual(nod3.getparent().getname(), 'ping')
        self.assertEqual(nod3.getparent().getparent(), nod)

        nod3 = nod.setchild('query', namespace='jabber')
        self.assertEqual(nod3.getname(), 'query')
        self.assertEqual(nod3.getnamespace(), 'jabber')

        nod3 = nod.setchild('reply', {'lizard':'snake'})
        self.assertEqual(nod3.getname(), 'reply')
        self.assertEqual(nod3.getattrdef('lizard'), 'snake')
        
    def test_serialize(self):
        nod = Node('tag')
        val = nod.serialize()
        self.assertEqual(val, '<tag />')

        nod.setattr('frog', 'frogskin')
        val = nod.serialize()
        self.assertEqual(val, '<tag frog="frogskin" />')

        nod2 = Node('child')
        nod2.setparent(nod)
        val = nod.serialize()
        self.assertEqual(val, '<tag frog="frogskin"><child /></tag>')

        nod.adddata('hel')
        nod.adddata('lo')
        val = nod.serialize()
        self.assertEqual(val, '<tag frog="frogskin"><child />hello</tag>')
        
        nod.addchild(Node('more'))
        val = nod.serialize()
        self.assertEqual(val,
            '<tag frog="frogskin"><child />hello<more /></tag>')

        nod.setnamespace('html')
        val = nod.serialize()
        self.assertEqual(val,
            '<tag xmlns="html" frog="frogskin"><child />hello<more /></tag>')

        nod2.setdata('goodbye')
        
        nod2 = Node('yetmore')
        nod2.addchild(Node('more'))
        nod.addchild(nod2)

        val = nod.serialize(True)
        self.assertEqual(val,
"""<tag xmlns="html" frog="frogskin">
  <child>goodbye</child>
  hello
  <more />
  <yetmore>
    <more />
  </yetmore>
</tag>""")

        nod = Node('tag')
        nod.setdata('hi. "Hi." <tag> this&that.')
        val = nod.serialize()
        self.assertEqual(val, '<tag>hi. &quot;Hi.&quot; &lt;tag&gt; this&amp;that.</tag>')

        nod = Node('tag', namespace='html')
        nod2 = nod.setchild('one', namespace='svg')
        nod2.setchild('onex')
        nod2.setchild('oney', namespace='html')
        nod2.setchild('onez', namespace='svg')
        nod.setchild('two', namespace='html')
        val = nod.serialize()
        self.assertEqual(val, '<tag xmlns="html"><one xmlns="svg"><onex /><oney xmlns="html" /><onez /></one><two /></tag>')

        nod.setnamespace(None)
        val = nod.serialize()
        self.assertEqual(val, '<tag><one xmlns="svg"><onex /><oney xmlns="html" /><onez /></one><two xmlns="html" /></tag>')

    def test_parse(self):
        nod = Node.parse('<tag />')
        self.assertEqual(nod.getname(), 'tag')
        self.assertEqual(nod.getcontents(), [])

        unist = u'<tag>h\xe9llo</tag>'
        (st, dummy) = codecs.getencoder('UTF-8')(unist)
        nod = Node.parse(st)
        self.assertEqual(nod.getname(), 'tag')
        self.assertEqual(nod.getdata(), u'h\xe9llo')

        nod = Node.parse(unist, encoding=None)
        self.assertEqual(nod.getname(), 'tag')
        self.assertEqual(nod.getdata(), u'h\xe9llo')

        badlist = [
            '<tag>',
            'data',
            '</tag>',
            '<tag></tag',
            '<tag></tag>more',
            '',
        ]

        goodlist = [
            '<tag />',
            '<tag>data</tag>',
            '<tag>data<child /></tag>',
            '<tag>data<child />more</tag>',
            '<tag>data<child /><child2 />more</tag>',
            '<tag>X&gt;Y&lt;Z&amp;Q</tag>',
        ]

        for st in badlist:
            self.assertRaises(xml.parsers.expat.ExpatError, Node.parse, st)

        for st in goodlist:
            nod = Node.parse(st)
            st2 = nod.serialize()
            self.assertEqual(st, st2)

    def test_parsenamespaces(self):
        list = [
            ('<stream:stream xmlns="defuri" xmlns:stream="nsuri">'
                + '<stream:tag /></stream:stream>',
            '<stream xmlns="nsuri"><tag /></stream>',
            ['nsuri', 'nsuri'] ),
            
            ('<stream:stream xmlns="defuri" xmlns:stream="nsuri">'
                + '<tag /></stream:stream>',
            '<stream xmlns="nsuri"><tag xmlns="defuri" /></stream>',
            ['nsuri', 'defuri'] ),
            
            ('<stream:stream xmlns="defuri" xmlns:stream="nsuri">'
                + '<tag><child /></tag></stream:stream>',
            '<stream xmlns="nsuri"><tag xmlns="defuri">'
                + '<child /></tag></stream>',
            ['nsuri', 'defuri', 'defuri'] ),
            
            ('<stream:stream xmlns="defuri" xmlns:stream="nsuri">'
                + '<tag xml:lang="en" stream:maerts="foo" /></stream:stream>',
            '<stream xmlns="nsuri"><tag xmlns="defuri"'
                + ' xml:lang="en" stream:maerts="foo" /></stream>',
            ['nsuri', 'defuri'] ),
        ]

        for (stin, stout, urilist) in list:
            nod = Node.parse(stin)
            st = nod.serialize()
            self.assertEqual(st, stout)
            while (nod):
                self.assertEqual(nod.getnamespace(), urilist.pop(0))
                nod = nod.getchild()

        st = ('<stream:stream xmlns="defuri" xmlns:stream="nsuri">'
            + '<tag xml:lang="en" frog:tag="bar" /></stream:stream>')
        self.assertRaises(xml.parsers.expat.ExpatError, Node.parse, st)

    def test_copy(self):
        list = [
            '<tag><child />hello</tag>',
            '<tag>foo<child />hello<child>bar </child></tag>',
            '<tag one="1"><child two="22" /></tag>',
            '<tag namespace="uri"><child /></tag>',
            '<tag namespace="uri"><child namespace="uri2" /></tag>',
        ]

        for st in list:
            nod = Node.parse(st)
            nod2 = nod.copy()
            self.assertNotEqual(nod, nod2)
            self.assertEqual(st, nod.serialize())
            self.assertEqual(st, nod2.serialize())

        nod = Node('tag', namespace='parenturi')
        nod2 = Node('child', parent=nod)
        nod3 = nod2.copy()
        self.assertEqual(nod2.serialize(), nod3.serialize())
        self.assert_(not nod3.getparent())
        