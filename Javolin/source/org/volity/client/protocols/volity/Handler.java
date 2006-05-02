package org.volity.client.protocols.volity;

import java.io.*;
import java.net.*;
import org.volity.client.data.CommandStub;

public class Handler 
    extends URLStreamHandler
{
    protected URLConnection openConnection(URL url) 
        throws MalformedURLException {

        return new CommandStubConnection(url);
    }

    static class CommandStubConnection 
        extends URLConnection {

        public CommandStubConnection(URL url) {
            super(url);
        }

        public String getContentType() {
            return "application/x-volity-command-stub";
        }

        public Object getContent()
            throws IOException {
            URL url = getURL();
            return CommandStub.parse(url);
        }

        public Object getContent(Class[] classes)
            throws IOException {
            for (int ix=0; ix<classes.length; ix++) {
                if (classes[ix] == CommandStub.class)
                    return getContent();
            }

            return null;
        }

        /**
         * This method is supposed to connect to the URL's resource. Since the
         * resource is built into the StubConnection itself, this does nothing.
         */
        public void connect() {
            return;
        }

    }
}
