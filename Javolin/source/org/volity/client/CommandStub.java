package org.volity.client;

import java.io.*;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * This class represents the contents of a Volity command stub file.
 * At the moment, CommandStub has just two properties: the command
 * (COMMAND_CREATE_TABLE, COMMAND_JOIN_TABLE, COMMAND_JOIN_LOBBY)
 * and the JID (of the parlor, table MUC, or chat MUC, respectively.)
 */
public class CommandStub 
{
    public static final int COMMAND_UNDEFINED    = 0;
    public static final int COMMAND_CREATE_TABLE = 1;
    public static final int COMMAND_JOIN_TABLE   = 2;
    public static final int COMMAND_JOIN_LOBBY   = 3;


    protected int mCommand;
    protected String mJID;

    /**
     * Create a CommandStub with no properties. This is used only by the
     * parse() static method.
     */
    protected CommandStub() {
        mCommand = COMMAND_UNDEFINED;
        mJID = null;
    }

    /**
     * Create a CommandStub with the given command and JID.
     */
    public CommandStub(int command, String jid) {
        mCommand = command;
        mJID = jid;
    }

    public int getCommand() { return mCommand; }
    public String getJID() { return mJID; }

    public String toString() {
        String val;
        if (mCommand == COMMAND_CREATE_TABLE) 
            val = "create-table";
        else if (mCommand == COMMAND_JOIN_TABLE) 
            val = "join-table";
        else if (mCommand == COMMAND_JOIN_LOBBY) 
            val = "join-lobby";
        else
            val = "???";

        return ("<" + val + " " + mJID + ">");
    }

    /**
     * For parsing the XML, I found it easiest to declare an object with a
     * bunch of tag-specific methods -- volity(), create(), parlor(). And the
     * easiest way to do *that* was to define an interface with the top-level
     * method -- volity() -- and then instantiate an anonymous class which
     * defines all the methods.
     */
    protected interface StubTagParse {
        void volity()
            throws XmlPullParserException, IOException, CommandStubException;
    }

    /**
     * Exception type for a malformed command stub file.
     */
    static public class CommandStubException extends Exception {
        CommandStubException(String st) {
            super(st);
        }
    }

    /**
     * Parse an XML stream into a valid CommandStub, or else throw an
     * appropriate exception.
     */
    static public CommandStub parse(Reader reader)
        throws XmlPullParserException, IOException, CommandStubException {
        final XmlPullParser xpp = new MXParser();
        xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        xpp.setInput(reader);

        // Create a blank CommandStub. The parse code will fill it in.
        final CommandStub result = new CommandStub();

        /* Instantiate the anonymous class described above. Each method in this
         * object parses one tag, beginning to end.
         *
         * In each case, the method should be called while the PullParser's
         * current event is START_TAG. When the method returns, the current
         * event will be END_TAG. */

        StubTagParse tagParse = new StubTagParse() {
                public void volity()
                    throws XmlPullParserException, IOException, CommandStubException {
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
                            if (xpp.getName().equals("create")) {
                                this.create();
                            }
                            else if (xpp.getName().equals("join-table")) {
                                this.joinTable();
                            }
                            else if (xpp.getName().equals("join-lobby")) {
                                this.joinLobby();
                            }
                            else {
                                throw new CommandStubException("<volity> cannot contain tag <" + xpp.getName() + ">)");
                            }
                        }
                        xpp.next();
                    }
                }

                public void create()
                    throws XmlPullParserException, IOException, CommandStubException {
                    int startDepth = xpp.getDepth();
                    xpp.next();

                    if (result.mCommand != COMMAND_UNDEFINED)
                        throw new CommandStubException("<volity> may contain only one command tag.");
                    result.mCommand = COMMAND_CREATE_TABLE;

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
                            if (xpp.getName().equals("parlor")) {
                                String jid = xpp.getAttributeValue(null, "jid");
                                if (jid != null && !jid.equals(""))
                                    result.mJID = jid;
                            }
                            else {
                                throw new CommandStubException("<create> cannot contain tag <" + xpp.getName() + ">)");
                            }
                        }
                        xpp.next();
                    }
                }

                public void joinTable()
                    throws XmlPullParserException, IOException, CommandStubException {
                    int startDepth = xpp.getDepth();
                    xpp.next();

                    if (result.mCommand != COMMAND_UNDEFINED)
                        throw new CommandStubException("<volity> may contain only one command tag.");
                    result.mCommand = COMMAND_JOIN_TABLE;

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
                            if (xpp.getName().equals("table")) {
                                String jid = xpp.getAttributeValue(null, "jid");
                                if (jid != null && !jid.equals(""))
                                    result.mJID = jid;
                            }
                            else {
                                throw new CommandStubException("<join-table> cannot contain tag <" + xpp.getName() + ">)");
                            }
                        }
                        xpp.next();
                    }
                }

                public void joinLobby()
                    throws XmlPullParserException, IOException, CommandStubException {
                    int startDepth = xpp.getDepth();
                    xpp.next();

                    if (result.mCommand != COMMAND_UNDEFINED)
                        throw new CommandStubException("<volity> may contain only one command tag.");
                    result.mCommand = COMMAND_JOIN_LOBBY;

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
                            if (xpp.getName().equals("muc")) {
                                String jid = xpp.getAttributeValue(null, "jid");
                                if (jid != null && !jid.equals(""))
                                    result.mJID = jid;
                            }
                            else {
                                throw new CommandStubException("<join-lobby> cannot contain tag <" + xpp.getName() + ">)");
                            }
                        }
                        xpp.next();
                    }
                }

            };

        // Do the parsing, using tagParse.

        while (true) {
            int eventType = xpp.getEventType();
            if (eventType == xpp.END_DOCUMENT) {
                break;
            }
            if (eventType == xpp.START_TAG) {
                if (xpp.getName().equals("volity")) {
                    tagParse.volity();
                }
                else {
                    throw new CommandStubException("command stub must contain a <volity> document.");
                }
            }
            xpp.next();
        }

        if (result.mCommand == COMMAND_UNDEFINED)
            throw new CommandStubException("command stub must contain <create>, <join-table>, or <join-lobby>.");
        if (result.mJID == null || result.mJID.equals(""))
            throw new CommandStubException("command stub must contain a JID.");

        return result;
    }
}
