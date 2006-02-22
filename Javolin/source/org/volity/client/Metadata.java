package org.volity.client;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class Metadata
{
    public static final String NS_XML = "http://www.w3.org/XML/1998/namespace";
    public static final String NS_SVG = "http://www.w3.org/2000/svg";
    public static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    public static final String NS_VOLITY = "http://volity.org/protocol/metadata";

    public static final String SVG_TITLE = createKey(NS_SVG, "metadata");

    public static final String DC_TITLE = createKey(NS_DC, "title");
    public static final String DC_CREATOR = createKey(NS_DC, "creator");

    public static final String VOLITY_REQUIRE_RULESET = createKey(NS_VOLITY, "require-ruleset");

    protected Map mMap = new HashMap();

    protected Metadata() {
        // nothing to set up
    }

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

    public String get(String key) {
        return get(key, null);
    }

    public String get(String key, String language) {
        List ls = (List)mMap.get(key);
        if (ls == null)
            return null;

        String resultNone = null;
        String resultWrong = null;

        for (int ix=0; ix<ls.size(); ix++) {
            Entry val = (Entry)ls.get(ix);
            String lang = val.getLanguage();
            if (language != null && lang.equals(language))
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
    }

    public static String createKey(String namespace, String name) {
        return namespace + " " + name;
    }

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

    public static Metadata parseSVGMetadata(File file) 
        throws XmlPullParserException, IOException
    {
        Metadata result = new Metadata();

        XmlPullParser xpp = new MXParser();
        xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

        FileReader in = new FileReader(file);
        xpp.setInput(in);

        boolean inMetadata = false;
        boolean inString = false;
        String currentTag = null;
        String currentLang = null;
        int tuple[] = new int[2];

        StringBuffer buf = new StringBuffer();

        int eventType = xpp.getEventType();
        do {
            if (eventType == xpp.START_TAG) {
                if (xpp.getDepth() == 2 && xpp.getName().equals("metadata")
                    && xpp.getNamespace().equals(NS_SVG)) {
                    inMetadata = true;
                }
                if (inMetadata && xpp.getDepth() == 3) {
                    currentTag = createKey(xpp.getNamespace(), xpp.getName());
                    currentLang = xpp.getAttributeValue(NS_XML, "lang");
                    buf.setLength(0);
                    inString = true;
                }
                if (xpp.getDepth() == 2 && xpp.getName().equals("title")
                    && xpp.getNamespace().equals(NS_SVG)) {
                    assert (!inMetadata);
                    currentTag = DC_TITLE;
                    currentLang = null;
                    buf.setLength(0);
                    inString = true;
                }
            }
            if (eventType == xpp.END_TAG) {
                if (xpp.getDepth() == 2 && inMetadata) {
                    inMetadata = false;
                }
                if (inMetadata && xpp.getDepth() == 3 && inString) {
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
                if (inString && xpp.getDepth() == 2 && xpp.getName().equals("title")) {
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
            }
            if (eventType == xpp.TEXT) {
                if (inString) {
                    char ch[] = xpp.getTextCharacters(tuple);
                    int start = tuple[0];
                    int length = tuple[1];
                    buf.append(ch, start, length);
                }
            }
            eventType = xpp.next();
        } while (eventType != xpp.END_DOCUMENT);

        in.close();

        return result;
    }
}
