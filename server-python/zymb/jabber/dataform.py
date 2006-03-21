"""This module contains a class representing a Jabber data form. (See
JEP-0004.)
"""

import unittest
import interface

class DataForm:
    """DataForm: A class representing a Jabber data form. (See JEP-0004.)

    This class has only minimal features. It is used by the disco module,
    and supports the range of forms used in disco. It does not understand
    everything in the JEP-0004 spec.

    DataForm() -- constructor.

    Public methods:

    addfield(var, val, label=None, typ=None) -- add a field to the form (or
        modify one).
    getfields() -- get the list of fields.
    makenode() -- convert the form to a Node tree.
    """

    def __init__(self):
        self.fields = []

    def addfield(self, var, val, label=None, typ=None):
        """addfield(var, val, label=None, typ=None) -> None

        Add a field to the form. The *var* is the variable name; the *val*
        is the field value. The *label* is the optional field label, and
        the *typ* is the optional field type.

        If a field with the given *var* exists in the form, this field
        replaces it. Otherwise, a new field is added at the end of the list.
        """
        
        fld = (var, val, label, typ)
        ls = [ ix for ix in range(len(self.fields))
            if self.fields[ix][0] == var ]

        if (not ls):
            self.fields.append(fld)
        else:
            ix = ls[0]
            self.fields[ix] = fld

    def getfields(self):
        """getfields() -> list

        Get the list of fields. The result is a list of (var, val, label, type)
        tuples (where *label* and *type* may be None).
        """
        
        return self.fields

    def makenode(self):
        """makenode() -> Node
        
        Convert the form to a Node tree. The returned node will be an
        <x> node in the 'jabber:x:data' namespace.
        """
        
        dic = { 'type':'result' }
        formnod = interface.Node('x', dic)
        formnod.setnamespace(interface.NS_DATA)

        for (var, val, label, typ) in self.fields:
            dic = { 'var':var }
            if (label):
                dic['label'] = label
            if (typ):
                dic['type'] = typ
            nod = interface.Node('field', dic)
            nod.setchilddata('value', val)
            formnod.addchild(nod)

        return formnod


def parse(nod):
    """parse(Node) -> DataForm

    Given an <x> node in the 'jabber:x:data' namespace, return a DataForm.
    """
    
    nam = nod.getname()
    if (nam != 'x'):
        raise ValueError('tried to parse <%s> as a dataform (must be <x>)'
            % nam)

    ns = nod.getnamespace()
    if (ns != interface.NS_DATA):
        raise ValueError('tried to parse <x xmlns=\'%s\'> as a dataform'
            ' (must be \'%s\')' % (ns, interface.NS_DATA))

    form = DataForm()

    for fnod in nod.getchildren():
        if (fnod.getname() != 'field'):
            continue

        val = None
        valnod = fnod.getchild('value')
        if (valnod):
            val = valnod.getdata()
        if (not val):
            val = ''

        var = fnod.getattr('var')
        label = fnod.getattr('label')

        form.addfield(var, val, label)

    return form

# ------------------- unit tests -------------------

class TestDataForm(unittest.TestCase):
    """Unit tests for the dataform module.
    """

    def test_forms(self):
        form = DataForm()
        form.addfield('a1', 'a1val')
        form.addfield('a2', 'a2val')
        xmlequiv = ('<x xmlns="jabber:x:data" type="result">'
            + '<field var="a1"><value>a1val</value></field>'
            + '<field var="a2"><value>a2val</value></field>'
            + '</x>')
        self.assertEqual(str(form.makenode()), xmlequiv)

        form.addfield('FORM_TYPE', 'wazoo', None, 'hidden')
        xmlequiv = ('<x xmlns="jabber:x:data" type="result">'
            + '<field var="a1"><value>a1val</value></field>'
            + '<field var="a2"><value>a2val</value></field>'
            + '<field var="FORM_TYPE" type="hidden"><value>wazoo</value></field>'
            + '</x>')
        self.assertEqual(str(form.makenode()), xmlequiv)

        form.addfield('b3', 'b3val', 'the label')
        xmlequiv = ('<x xmlns="jabber:x:data" type="result">'
            + '<field var="a1"><value>a1val</value></field>'
            + '<field var="a2"><value>a2val</value></field>'
            + '<field var="FORM_TYPE" type="hidden"><value>wazoo</value></field>'
            + '<field var="b3" label="the label"><value>b3val</value></field>'
            + '</x>')
        self.assertEqual(str(form.makenode()), xmlequiv)
