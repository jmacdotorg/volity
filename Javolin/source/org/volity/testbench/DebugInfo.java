package org.volity.testbench;

import java.io.*;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.mxp1.MXParser;
import org.volity.javolin.game.UIFileCache;

/**
 * Represents the contents of a testbench.xml file. It also includes a list of
 * seat IDs, if that was findable.
 */
public class DebugInfo
{
    public final static int CMD_BUTTON = 1;
    public final static int CMD_FIELD  = 2;
    public final static int DATTYP_STRING = 1;
    public final static int DATTYP_INT    = 2;

    /**
     * Inner class which represents one command (button or field) in the file.
     * This is a simplistic "bag of public fields" class. I could have made
     * Button and Field subclasses, but it wouldn't have made anybody any
     * smarter.
     *
     * Type is CMD_BUTTON or CMD_FIELD. Label is the command name (and the
     * on-screen label). Code is the Javascript contents (for buttons).
     * Datatype is the field type (for fields).
     */
    public class Command {
        public int type;
        public String label;
        public String code;
        public int datatype;
        public Command(int type) { this.type = type; }
    }

    File uiDir;
    List seatList;
    List commandList;

    public DebugInfo(File uiDir) {
        this.uiDir = uiDir;

        /** 
         * Try to figure out the ids of all the seats in the game. To do this,
         * we check for a locale directory, and see if there's a
         * "seattokens.xml" in any of its language subdirectories. If not, too
         * bad -- we'll have an empty list. If there's more than one, we'll
         * pick the first one we see... hope they all list the same seat ids.
         */
        seatList = new ArrayList();

        try {
            File localeDir = UIFileCache.findFileCaseless(uiDir, "locale");
            if (localeDir != null && localeDir.isDirectory()) {
                File seatTokenFile = null;

                String[] children = localeDir.list();

                for (int ix=0; ix<children.length; ix++) {
                    File langDir = new File(localeDir, children[ix]);
                    if (langDir.isDirectory()) {
                        seatTokenFile = UIFileCache.findFileCaseless(langDir,
                            "seattokens.xml");
                        if (seatTokenFile != null)
                            break;
                    }
                }

                if (seatTokenFile != null) {
                    extractTableKeys(seatList, seatTokenFile);
                }
            }
        }
        catch (Exception ex) {
            System.err.println("Problem parsing the seattokens.xml file: " +
                ex.toString());
        }

        commandList = new ArrayList();

        File debugFile = UIFileCache.findFileCaseless(uiDir, "testbench.xml");
        if (debugFile != null) {
            try {
                parseDebugFile(commandList, debugFile);
            }
            catch (Exception ex) {
                System.err.println("Problem parsing the testbench.xml file: " +
                    ex.toString());
            }
        }
    }

    /**
     * Return the list of seat IDs, as intuited from the locale files. If there
     * were no locale files, this will be the empty list.
     */
    public List getSeatList() {
        return seatList;
    }

    /**
     * Return the list of Commands found in testbench.xml.
     */
    public List getCommandList() {
        return commandList;
    }

    /**
     * For parsing the XML, I found it easiest to declare an object with a
     * bunch of tag-specific methods -- testbench(), button(), field(). And the
     * easiest way to do *that* was to define an interface with the top-level
     * method -- testbench() -- and then instantiate an anonymous class which
     * defines all the methods.
     */
    protected interface DebugTagParse {
        void testbench()
            throws XmlPullParserException, IOException, DebugFileException;
    }

    /**
     * Exception type for a malformed testbench.xml file.
     */
    protected class DebugFileException extends Exception {
        DebugFileException(String st) {
            super(st);
        }
    }

    /**
     * Parse an XML debug-command file.
     *
     * @param ls list to add Commands to.
     * @param debugFile the XML file.
     */
    protected void parseDebugFile(List ls, File debugFile) 
        throws XmlPullParserException, IOException, DebugFileException
    {
        final XmlPullParser xpp = new MXParser();
        xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

        final List result = ls;   // must be final for inner classes
        FileReader in = new FileReader(debugFile);
        xpp.setInput(in);
        final int tuple[] = new int[2];
        final StringBuffer buf = new StringBuffer();

        /* Instantiate the anonymous class described above. Each method in this
         * object parses one tag, beginning to end.
         *
         * In each case, the method should be called while the PullParser's
         * current event is START_TAG. When the method returns, the current
         * event will be END_TAG. */

        DebugTagParse tagParse = new DebugTagParse() {
                public void testbench()
                    throws XmlPullParserException, IOException, DebugFileException {
                    int startDepth = xpp.getDepth();
                    xpp.next();

                    while (true) {
                        int eventType = xpp.getEventType();
                        if (eventType == xpp.END_DOCUMENT) {
                            break;
                        }
                        if (eventType == xpp.END_TAG 
                            && xpp.getDepth() == startDepth) {
                            break;
                        }
                        if (eventType == xpp.START_TAG) {
                            if (xpp.getName().equals("button")) {
                                this.button();
                            }
                            else if (xpp.getName().equals("field")) {
                                this.field();
                            }
                            else {
                                throw new DebugFileException("<testbench> cannot contain tag <" + xpp.getName() + ">)");
                            }
                        }
                        xpp.next();
                    }
                }

                public void button()
                    throws XmlPullParserException, IOException, DebugFileException {
                    int startDepth = xpp.getDepth();
                    String name = xpp.getAttributeValue(null, "name");
                    buf.setLength(0);
                    xpp.next();

                    while (true) {
                        int eventType = xpp.getEventType();
                        if (eventType == xpp.END_DOCUMENT) {
                            break;
                        }
                        if (eventType == xpp.END_TAG 
                            && xpp.getDepth() == startDepth) {
                            break;
                        }
                        if (eventType == xpp.START_TAG) {
                            throw new DebugFileException("<button> can only contain text data (found <" + xpp.getName() + ">)");
                        }
                        if (eventType == xpp.TEXT) {
                            char ch[] = xpp.getTextCharacters(tuple);
                            int start = tuple[0];
                            int length = tuple[1];
                            buf.append(ch, start, length);
                        }
                        xpp.next();
                    }

                    if (name == null)
                        name = "Button_" + String.valueOf(result.size()+1);
                    Command cmd = new Command(CMD_BUTTON);
                    cmd.label = name;
                    cmd.code = buf.toString();
                    result.add(cmd);
                }

                public void field()
                    throws XmlPullParserException, IOException, DebugFileException {
                    int startDepth = xpp.getDepth();
                    String name = xpp.getAttributeValue(null, "name");
                    String dattyp = xpp.getAttributeValue(null, "type");
                    xpp.next();

                    while (true) {
                        int eventType = xpp.getEventType();
                        if (eventType == xpp.END_DOCUMENT) {
                            break;
                        }
                        if (eventType == xpp.END_TAG 
                            && xpp.getDepth() == startDepth) {
                            break;
                        }
                        if (eventType == xpp.START_TAG) {
                            throw new DebugFileException("<field> cannot contain tags (found <" + xpp.getName() + ">)");
                        }
                        xpp.next();
                    }

                    if (name == null)
                        name = "Field_" + String.valueOf(result.size()+1);
                    Command cmd = new Command(CMD_FIELD);
                    cmd.label = name;
                    cmd.datatype = DATTYP_STRING;
                    if (dattyp != null) {
                        dattyp = dattyp.toLowerCase();
                        if (dattyp.equals("int"))
                            cmd.datatype = DATTYP_INT;
                    }
                    result.add(cmd);
                }
            };

        while (true) {
            int eventType = xpp.getEventType();
            if (eventType == xpp.END_DOCUMENT) {
                break;
            }
            if (eventType == xpp.START_TAG) {
                if (xpp.getName().equals("testbench")) {
                    tagParse.testbench();
                }
                else {
                    throw new DebugFileException("testbench.xml file must contain a <testbench> document.");
                }
            }
            xpp.next();
        }

        in.close();
    }

    /**
     * Parse an XML token translation table, and pull out the keys.
     *
     * @param ls list to add keys to
     * @param tokenFile the XML file.
     */
    protected void extractTableKeys(List ls, File tokenFile) 
        throws XmlPullParserException, IOException
    {
        XmlPullParser xpp = new MXParser();
        xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

        FileReader in = new FileReader(tokenFile);
        xpp.setInput(in);
        int tuple[] = new int[2];
        boolean intoken = false;
        boolean instring = false;
        String key = null;
        StringBuffer buf = new StringBuffer();

        int eventType = xpp.getEventType();
        do {
            if (eventType == xpp.START_TAG) {
                if (xpp.getDepth() == 2 && xpp.getName().equals("token")) {
                    intoken = true;
                }
                if (intoken && xpp.getDepth() == 3
                    && xpp.getName().equals("key")) {
                    instring = true;
                }
                if (intoken && xpp.getDepth() == 3 
                    && xpp.getName().equals("value")) {
                    instring = true;
                }
            }
            if (eventType == xpp.TEXT) {
                if (instring) {
                    char ch[] = xpp.getTextCharacters(tuple);
                    int start = tuple[0];
                    int length = tuple[1];
                    buf.append(ch, start, length);
                }
            }
            if (eventType == xpp.END_TAG) {
                if (xpp.getDepth() == 2 && intoken) {
                    if (key != null && key.length() > 0) {
                        ls.add(key);
                    }
                    key = null;
                    intoken = false;
                }
                if (xpp.getDepth() == 3 && instring) {
                    if (xpp.getName().equals("key")) {
                        key = buf.toString();
                    }
                    instring = false;
                    buf.setLength(0);
                }
            }
            eventType = xpp.next();
        } while (eventType != xpp.END_DOCUMENT);

        in.close();
    }


}
