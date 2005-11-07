package org.volity.client.protocols.application;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import org.volity.client.CommandStub;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A ContentHandler for the MIME type "application/x-volity-command-stub".
 *
 * To make this class visible to Java's URL system, add
 * "org.volity.client.protocols" to the "java.content.handler.pkgs" system
 * property. Once that is done, calling url.getContent() on a URL of the
 * correct MIME type will return a CommandStub object.
 *
 * Note that this does not magically cause the host OS to direct URLs of that
 * MIME type to this application.
 */
public class x_volity_command_stub extends ContentHandler {
    static Charset utf8 = Charset.forName("UTF-8");

    public Object getContent(URLConnection conn) throws IOException {
        InputStream instr = conn.getInputStream();
        Reader reader = new BufferedReader(new InputStreamReader(instr, utf8));
        try {
            return CommandStub.parse(reader);
        }
        catch (XmlPullParserException ex) {
            throw new IOException(ex.toString());
        }
        catch (CommandStub.CommandStubException ex) {
            throw new IOException(ex.toString());
        }
    }

}
