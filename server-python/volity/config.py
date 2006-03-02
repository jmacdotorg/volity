import os
import unittest

class ConfigFile:
    """ConfigFile: A class which parses a simple config file.

    (Probably every app has one of these classes, so I don't feel bad about
    reinventing the wheel for the thousandth time. At least it's short.)

    ConfigFile(filename, argmap=None, envprefix='VOLITY_') -- constructor.

    The *filename* is parsed for lines of the form

        key: value

    Blank lines and lines beginning with '#' are ignored. A line without
    a colon is considered to have the empty string for a value.

    A line with the special form 'INCLUDE: filename' will parse in the
    given filename (taken as relative to the one being parsed).

    If *filename* is None, you still get a ConfigFile (as if it had parsed
    an empty file).

    The *argmap*, if supplied, is a set of key-value pairs which override
    the config file entries. (You might get these from command-line
    arguments.) Note that an *argmap* entry which maps to None is ignored,
    but an entry which maps to '' is considered to exist.

    The system also checks for environment variables of the form
    'VOLITY_KEY'. (The key name is capitalized, and spaces and dashes
    are converted to underscores; then *envprefix* is tacked on the
    front.)

    *argmap* entries override environment variables override config file
    lines.

    Public methods:

    get(key) -- get a config value.
    has_key(key) -- check whether a config value exists.

    Internal methods:

    parse(fl) -- parse the config file.
    makeenvname(key) -- convert a key name to a possible environment var name.
    """
    
    def __init__(self, filename, argmap=None, envprefix='VOLITY_'):
        self.filename = filename
        if (not argmap):
            argmap = {}
        self.argmap = argmap
        self.envprefix = envprefix
        
        self.map = {}
        if (type(filename) in [str, unicode]):
            fl = open(self.filename)
            self.parse(fl, filename)
            fl.close()
        elif (filename == None):
            pass
        else:
            self.parse(filename)

    def parse(self, fl, filename='.'):
        """parse(fl, filename='.') -- parse the config file.

        This is called internally when you create the ConfigFile.
        """
        
        while (1):
            ln = fl.readline()
            if (not ln):
                break
            ln = ln.strip()
            if ((not ln) or (ln.startswith('#'))):
                continue

            pos = ln.find(':')
            if (pos < 0):
                key = ln
                val = ''
            else:
                key = ln[ : pos ].strip()
                val = ln[ pos+1 : ].strip()
            if (key == 'INCLUDE'):
                if (val):
                    val = os.path.join(os.path.dirname(filename), val)
                    subfl = open(val)
                    self.parse(subfl, val)
                    subfl.close()
            else:
                self.map[key] = val
            
    def makeenvname(self, key):
        """makeenvname(key) -- convert a key name to a possible environment
            variable name.
        """

        key = key.upper()
        key = key.replace(' ', '_')
        key = key.replace('-', '_')
        return self.envprefix + key

    def get(self, key, default=None):
        """get(key, default=None) -> str

        Get an entry from the config file (or the command-line arguments,
        or an environment variable). If the entry cannot be found by any
        means, this returns None. (Unless another default is specified.)
        
        *argmap* entries override environment variables override config file
        lines.
        """
                
        val = self.argmap.get(key)
        if (val != None):
            return val
        val = os.getenv(self.makeenvname(key))
        if (val != None):
            return val
        val = self.map.get(key)
        if (val != None):
            return val
        return default

    def getbool(self, key, default=False):
        """getbool(key, default=False) -> bool

        Get an entry from the config file (or the command-line arguments,
        or an environment variable). If the entry cannot be found by any
        means, this returns False. (Unless another default is specified.)
        
        *argmap* entries override environment variables override config file
        lines. Boolean values of the form 't', 'true', 'y', 'yes', and '1'
        are all recognized.
        """

        val = self.get(key, None)
        if (val == None):
            return default
        if (not val):
            return False
        val = val.lower()
        if (val[0] in 'nf0'):
            return False
        return True

    def has_key(self, key):
        """has_key(key) -> bool

        Check whether an entry exists in the config file (or the command-line
        arguments, or an environment variable).
        """
        
        val = self.argmap.get(key)
        if (val != None):
            return True
        val = os.getenv(self.makeenvname(key))
        if (val != None):
            return True
        val = self.map.get(key)
        if (val != None):
            return True
        return False

# Unit test
# Run with 'python volity/config.py'

import StringIO
        
class TestConfig(unittest.TestCase):
    def test_file(self):
        configfile = """
# Comment
   # Another comment
   
space key : 1 2
space other key:three four
c: c
ca: ca
ce: ce
cae: cae
"""
        map = { 'a':'3a', 'ae':'3ae', 'ca':'3ca', 'cae':'3cae' }

        fl = StringIO.StringIO(configfile)
        conf = ConfigFile(fl, map)

        val = conf.get('space key')
        self.assertEqual(val, '1 2')
        val = conf.get('space other key')
        self.assertEqual(val, 'three four')

        os.environ['VOLITY_SPACE_KEY'] = '11 22'
        os.environ['VOLITY_SPACE_OTHER_KEY'] = '3 4'
        val = conf.get('space key')
        self.assertEqual(val, '11 22')
        val = conf.get('space other key')
        self.assertEqual(val, '3 4')

        os.environ['VOLITY_E'] = '2e'
        os.environ['VOLITY_AE'] = '2ae'
        os.environ['VOLITY_CE'] = '2ce'
        os.environ['VOLITY_CAE'] = '2cae'

        self.assertEqual(conf.get('c'), 'c')
        self.assertEqual(conf.get('a'), '3a')
        self.assertEqual(conf.get('e'), '2e')
        self.assertEqual(conf.get('ca'), '3ca')
        self.assertEqual(conf.get('ce'), '2ce')
        self.assertEqual(conf.get('ae'), '3ae')
        self.assertEqual(conf.get('cae'), '3cae')
        
        self.assert_(conf.has_key('c'))
        self.assert_(conf.has_key('a'))
        self.assert_(conf.has_key('e'))
        self.assert_(conf.has_key('ca'))
        self.assert_(conf.has_key('ce'))
        self.assert_(conf.has_key('cae'))
        self.assert_(not conf.has_key('sporkle'))

    def test_nofile(self):
        map = { 'a':'3a', 'ae':'3ae', 'ca':'3ca', 'cae':'3cae' }
        conf = ConfigFile(None, map)

        os.environ['VOLITY_E'] = '2e'
        os.environ['VOLITY_AE'] = '2ae'
        os.environ['VOLITY_CE'] = '2ce'
        os.environ['VOLITY_CAE'] = '2cae'

        self.assertEqual(conf.get('c'), None)
        self.assertEqual(conf.get('a'), '3a')
        self.assertEqual(conf.get('e'), '2e')
        self.assertEqual(conf.get('ca'), '3ca')
        self.assertEqual(conf.get('ce'), '2ce')
        self.assertEqual(conf.get('ae'), '3ae')
        self.assertEqual(conf.get('cae'), '3cae')
        
        self.assert_(not conf.has_key('c'))
        self.assert_(conf.has_key('a'))
        self.assert_(conf.has_key('e'))
        self.assert_(conf.has_key('ca'))
        self.assert_(conf.has_key('ce'))
        self.assert_(conf.has_key('cae'))
        self.assert_(not conf.has_key('sporkle'))

        

if __name__ == '__main__':
    unittest.main()
    