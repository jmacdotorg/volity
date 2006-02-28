package org.volity.client;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;
import org.volity.client.protocols.volresp.Handler;

/**
 * A class which parses metadata out of an SVG file, according to the Volity
 * metadata spec. (Which is extremely simple.) The metadata object can then be
 * queried (in an extremely simple way) to determine what was there.
 */
public class Metadata
{
    // XML namespaces used in the general vicinity of metadata
    public static final String NS_XML = "http://www.w3.org/XML/1998/namespace";
    public static final String NS_SVG = "http://www.w3.org/2000/svg";
    public static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    public static final String NS_VOLITY = "http://volity.org/protocol/metadata";

    // Keys which are used as metadata labels. Each exists in either the
    // DC or VOLITY namespace.

    public static final String DC_TITLE = createKey(NS_DC, "title");
    public static final String DC_CREATOR = createKey(NS_DC, "creator");
    public static final String DC_DESCRIPTION = createKey(NS_DC, "description");
    public static final String DC_CREATED = createKey(NS_DC, "created");
    public static final String DC_MODIFIED = createKey(NS_DC, "modified");
    public static final String DC_LANGUAGE = createKey(NS_DC, "language");

    public static final String VOLITY_DESCRIPTION_URL = createKey(NS_VOLITY, "description-url");
    public static final String VOLITY_RULESET = createKey(NS_VOLITY, "ruleset");
    public static final String VOLITY_VERSION = createKey(NS_VOLITY, "version");
    public static final String VOLITY_REQUIRES_ECMASCRIPT_API = createKey(NS_VOLITY, "requires-ecmascript-api");
    public static final String VOLITY_REQUIRES_RESOURCE = createKey(NS_VOLITY, "requires-resource");
    public static final String VOLITY_PROVIDES_RESOURCE = createKey(NS_VOLITY, "provides-resource");

    public static final URI sBlankURI;
    static {
        try {
            sBlankURI = new URI("");
        }
        catch (URISyntaxException ex) {
            throw new RuntimeException("Unable to create blank URI.");
        }
    }

    protected boolean isTopLevel;
    protected Map mResources;
    protected Map mMap = new HashMap();

    /**
     * Constructor. You should only call this if you want an empty metadata
     * set. Call parseSVGMetadata() to create a Metadata object from an SVG
     * file.
     */
    public Metadata() {
        this(null);
    }

    /**
     * Constructor. If you pass a (non-null) parent object, this creates a
     * subordinate Metadata object.
     */
    protected Metadata(Metadata parent) {
        if (parent == null) {
            isTopLevel = true;
            /* A top-level Metadata object holds its own resource map. Its
             * children will share access to it. */
            mResources = new HashMap();

            // Add the Metadata itself, with a blank URI.
            ResourceEntry entry = new ResourceEntry(null, null, this);
            mResources.put(sBlankURI, entry);
        }
        else {
            isTopLevel = false;
            /* A subordinate Metadata object points at its parent's map. */
            mResources = parent.mResources;
        }
    }

    /**
     * Add an entry to a metadata set. This is only used internally, while
     * parsing metadata from a file.
     */
    protected void add(String key, String value, String language) {
        List ls;

        if (!mMap.containsKey(key)) {
            ls = new ArrayList();
            mMap.put(key, ls);
        }
        else {
            ls = (List)mMap.get(key);
        }

        ls.add(new Entry(value, language));
    }

    /**
     * Retrieve an entry from the set. If there are multiple entries matching
     * this key, it will retrieve the first one which has no xml:lang attribute
     * (or the first one overall, if necessary).
     */
    public String get(String key) {
        return get(key, null);
    }

    /**
     * Retrieve an entry from the set. If there are multiple entries matching
     * this key, it will retrieve the first one whose xml:lang attribute
     * matches the given language. If there are none of those (or if the
     * language argument is null), this retrieves the first entry which has no
     * xml:lang attribute (or the first one overall, if necessary).
     */
    public String get(String key, String language) {
        List ls = (List)mMap.get(key);
        if (ls == null)
            return null;

        String resultNone = null;
        String resultWrong = null;

        for (int ix=0; ix<ls.size(); ix++) {
            Entry val = (Entry)ls.get(ix);
            String lang = val.getLanguage();
            if (language != null && lang != null && lang.equals(language))
                return val.getValue();
            if (lang == null) {
                if (resultNone == null)
                    resultNone = val.getValue();
            }
            else {
                if (resultWrong == null)
                    resultWrong = val.getValue();
            }
        }

        if (resultNone != null)
            return resultNone;
        else
            return resultWrong;
    }

    /**
     * Retrieve all the entries from the set which match the given key. The
     * result is a List of Strings.
     */
    public List getAll(String key) {
        List res = new ArrayList();
        List ls = (List)mMap.get(key);
        if (ls != null) {
            for (int ix=0; ix<ls.size(); ix++) {
                Entry val = (Entry)ls.get(ix);
                String value = val.getValue();
                res.add(value);
            }
        }
        return res;
    }

    /**
     * Retrieve all the entries from the set which match the given key. The
     * result is a List of Entry objects; each Entry contains the string value
     * of the key, and the entry language (its xml:lang attribute).
     */
    public List getAllEntries(String key) {
        List res = new ArrayList();
        List ls = (List)mMap.get(key);
        if (ls != null) {
            for (int ix=0; ix<ls.size(); ix++) {
                Entry val = (Entry)ls.get(ix);
                res.add(val);
            }
        }
        return res;
    }

    /**
     * Get the external location of the file containing the resource with the
     * given URI. If the URI is null or "", this returns null. If the URI is
     * not known, this returns null. If the resource is taken from the UI
     * package (the default, rather than being a player preference) then this
     * returns null.
     */
    public URL getResourceLocation(URI uri) {
        if (uri == null || uri.toString().equals(""))
            uri = sBlankURI;

        ResourceEntry entry = (ResourceEntry)mResources.get(uri);
        if (entry == null)
            return null;

        return entry.source;
    }

    /**
     * Get the location of the file containing the resource with the given URI.
     * If the URI is null or "", this returns the location of the top-level
     * Metadata object. If the URI is not known, this returns null.
     */
    public URL getLocalResourceLocation(URI uri) {
        if (uri == null || uri.toString().equals(""))
            uri = sBlankURI;

        ResourceEntry entry = (ResourceEntry)mResources.get(uri);
        if (entry == null)
            return null;

        return entry.file;
    }

    /**
     * Get the Metadata associated with the given resource URI. If the URI is
     * null or "", this returns the top-level Metadata object for the UI. If
     * the resource has no metadata, this returns an empty Metadata object. If
     * the URI is not known, this returns null.
     */
    public Metadata getResource(URI uri) {
        if (uri == null || uri.toString().equals(""))
            uri = sBlankURI;

        ResourceEntry entry = (ResourceEntry)mResources.get(uri);
        if (entry == null)
            return null;

        if (entry.metadata == null) {
            try {
                entry.metadata = parseSVGMetadata(entry.file, this);
            }
            catch (Exception ex) {
                // nothing we can do
            }
        }
        return entry.metadata;
    }

    /**
     * Return a list of the resource URIs in the Metadata list. (Excluding the
     * blank URI which represents the top level.) You can feed these URIs to
     * getResource() to examine the metadata of each resource.
     */
    public List getAllResources() {
        List res = new ArrayList();

        for (Iterator it = mResources.keySet().iterator(); it.hasNext(); ) {
            URI key = (URI)it.next();
            if (key.toString().equals("")) {
                // don't repeat the resource that is the top-level file.
                continue;
            }
            res.add(key);
        }

        return res;
    }

    /**
     * Dump a text representation of the metadata to an output stream. This is
     * useful mostly for debugging.
     */
    public void dump(PrintStream out) {
        for (Iterator it = mMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry ent = (Map.Entry)it.next();
            String key = (String)ent.getKey();
            List ls = (List)ent.getValue();
            for (int ix=0; ix<ls.size(); ix++) {
                Entry val = (Entry)ls.get(ix);
                String lang = val.getLanguage();
                String value = val.getValue();
                if (lang == null)
                    out.println("<" + key + ">: \"" + value + "\"");
                else
                    out.println("<" + key + "> (" + lang + "): \"" + value + "\"");
            }
        }

        if (isTopLevel) {
            List ls = getAllResources();
            for (int ix=0; ix<ls.size(); ix++) {
                URI key = (URI)ls.get(ix);
                URL file = getLocalResourceLocation(key);
                out.println("* resource <" + key + ">: <" + file + ">");
                Metadata subdata = getResource(key);
                if (subdata == null)
                    out.println("...not readable");
                else
                    subdata.dump(out);
            }
        }
    }

    /**
     * Create a key (such as may be passed to the get() or getAll() methods of
     * Metadata). A key is a namespaced XML element name, so you have to pass
     * in a namespace and a name.
     */
    public static String createKey(String namespace, String name) {
        return namespace + " " + name;
    }

    /**
     * A data-only class which represents a metadata entry. It contains a
     * string value and, optionally, a language (which is taken from the
     * entry's xml:lang attribute.)
     */
    public static class Entry {
        protected String value;
        protected String language;
        public Entry(String value, String language) {
            this.value = value;
            this.language = language;
        }
        public String getValue() { return value; }
        public String getLanguage() { return language; }
    }

    /**
     * Create a Metadata object from an SVG file.
     */
    public static Metadata parseSVGMetadata(File file) 
        throws SAXException, IOException
    {
        return parseSVGMetadata(file, null);
    }

    /**
     * Create a Metadata object from an SVG file.
     */
    public static Metadata parseSVGMetadata(File file, Metadata parent) 
        throws SAXException, IOException
    {
        Metadata result = null;
        FileReader in = new FileReader(file);

        try {
            result = parseSVGMetadata(file.toURL(), null, in, parent);
        }
        finally {
            in.close();
        }

        return result;
    }

    /**
     * Create a Metadata object from an SVG URL.
     */
    public static Metadata parseSVGMetadata(URL url) 
        throws SAXException, IOException
    {
        return parseSVGMetadata(url, null);
    }

    /**
     * Create a Metadata object from an SVG URL.
     */
    public static Metadata parseSVGMetadata(URL url, Metadata parent) 
        throws SAXException, IOException
    {
        Metadata result = null;
        InputStream in = url.openStream();

        try {
            result = parseSVGMetadata(url, in, null, parent);
        }
        finally {
            in.close();
        }

        return result;
    }

    protected static Metadata parseSVGMetadata(final URL url,
        InputStream inputStream, Reader reader, Metadata parent)
        throws SAXException, IOException
    {
        final Metadata result = new Metadata(parent);

        XMLReader xr = XMLReaderFactory.createXMLReader("org.apache.xerces.parsers.SAXParser");
        InputSource input = null;
        if (reader != null)
            input = new InputSource(reader);
        else if (inputStream != null)
            input = new InputSource(inputStream);
        input.setEncoding("UTF-8");
        input.setSystemId(url.toString());

        final StringBuffer buf = new StringBuffer();

        xr.setFeature("http://xml.org/sax/features/validation", false);
        xr.setFeature("http://xml.org/sax/features/external-general-entities", true);
        xr.setFeature("http://xml.org/sax/features/external-parameter-entities", true);


        EntityResolver resolver = new EntityResolver() {
                public InputSource resolveEntity(String publicId,
                    String systemId)
                    throws IOException, SAXException {

                    URI uri;
                    try {
                        uri = new URI(systemId);
                    }
                    catch (URISyntaxException ex) {
                        throw new SAXException(ex);
                    }
                    String scheme = uri.getScheme();

                    if (scheme.equals("file")) {
                        // load the file
                        return null;
                    }
                    if (scheme.equals("vollocp")) {
                        // resolve the localization protocol
                        return null;
                    }
                    if (scheme.equals("volresp")) {
                        /* resolve the resource protocol, but first record the
                         * URI for future metadata lookups */
                        Handler.Trio pair = Handler.resolveURI(url, uri);
                        if (!result.mResources.containsKey(pair.uri)) {
                            ResourceEntry entry = new ResourceEntry(pair.source, pair.result);
                            result.mResources.put(pair.uri, entry);
                        }
                        return null;
                    }

                    Reader reader = new StringReader("");
                    InputSource stub = new InputSource(reader);
                    if (systemId != null)
                        stub.setSystemId(systemId);
                    if (publicId != null)
                        stub.setPublicId(publicId);
                    return stub;
                }
            };
        xr.setEntityResolver(resolver);

        ContentHandler handler = new DefaultHandler() {
                private int depth = 0;

                private boolean inMetadata = false;
                private boolean inString = false;
                private String currentTag = null;
                private String currentLang = null;

                public void startElement (String uri, String name,
                    String qName, Attributes attrs) {

                    depth++;

                    if (depth == 2 && name.equals("metadata")
                        && uri.equals(NS_SVG)) {
                        inMetadata = true;
                    }
                    if (inMetadata && depth == 3) {
                        currentTag = createKey(uri, name);
                        currentLang = attrs.getValue(NS_XML, "lang");
                        buf.setLength(0);
                        inString = true;
                    }
                    if (depth == 2 && name.equals("title")
                        && uri.equals(NS_SVG)) {
                        assert (!inMetadata);
                        currentTag = DC_TITLE;
                        currentLang = null;
                        buf.setLength(0);
                        inString = true;
                    }
                }

                public void endElement (String uri, String name,
                    String qName) {

                    if (depth == 2 && inMetadata) {
                        inMetadata = false;
                    }
                    if (inMetadata && depth == 3 && inString) {
                        String val = buf.toString();
                        buf.setLength(0);
                        inString = false;

                        // Remove leading/trailing whitespace
                        val = val.trim();
                        // Turn all other whitespace into single spaces
                        val = val.replaceAll("\\s+", " ");

                        result.add(currentTag, val, currentLang);

                        currentTag = null;
                    }
                    if (inString && depth == 2 && name.equals("title")) {
                        String val = buf.toString();
                        buf.setLength(0);
                        inString = false;

                        // Remove leading/trailing whitespace
                        val = val.trim();
                        // Turn all other whitespace into single spaces
                        val = val.replaceAll("\\s+", " ");

                        result.add(currentTag, val, currentLang);

                        currentTag = null;
                    }

                    depth--;
                }

                public void characters(char ch[], int start, int length) {
                    if (inString) {
                        buf.append(ch, start, length);
                    }
                }
            };
        xr.setContentHandler(handler);

        xr.parse(input);
        return result;
    }

    protected static class ResourceEntry {
        URL source;
        URL file;
        Metadata metadata;
        public ResourceEntry(URL source, URL file) {
            this.source = source;
            this.file = file;
            metadata = null;
        }
        public ResourceEntry(URL source, URL file, Metadata data) {
            this.source = source;
            this.file = file;
            metadata = data;
        }
    }

    /**
     * This lets you run the metadata parser as a stand-alone command, which is
     * handy for unit testing.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("usage: java -cp .:../lib/xerces_2_5_0.jar:../lib/smack.jar org.volity.client.Metadata file [ file2 ...]");
            return;
        }

        String val = System.getProperty("java.protocol.handler.pkgs");
        if (val == null)
            val = "org.volity.client.protocols";
        else
            val = val + "|org.volity.client.protocols";
        System.setProperty("java.protocol.handler.pkgs", val);

        for (int ix=0; ix<args.length; ix++) {
            String filename = args[ix];
            File file = new File(filename);
            try {
                Metadata data = parseSVGMetadata(file);
                data.dump(System.out);
            }
            catch (Exception ex) {
                System.out.println("Cannot parse " + filename + ":");
                ex.printStackTrace();
            }
        }
    }
}
