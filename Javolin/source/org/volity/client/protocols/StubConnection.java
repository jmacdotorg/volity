package org.volity.client.protocols;

import java.io.*;
import java.net.*;

/**
 * A simple URLConnection class, which returns a fixed chunk of text.
 * (Encoded in UTF-8, since it is meant to be used in XML files.)
 */
public class StubConnection
    extends URLConnection 
{
    String mText;

    /**
     * Create a StubConnection which will generate the given text.
     */
    public StubConnection(URL url, String text)
        throws MalformedURLException {
        super(url);

        mText = text;
    }

    /**
     * This method is supposed to connect to the URL's resource. Since the
     * resource is built into the StubConnection itself, this does nothing.
     */
    public void connect() {
        return;
    }

    /**
     * Create a stream from which the URL's data can be read. This creates a
     * ByteArrayInputStream containing our built-in text.
     */
    public InputStream getInputStream() 
        throws UnsupportedEncodingException {
        byte[] bytes = mText.getBytes("UTF-8");
        return new ByteArrayInputStream(bytes);
    }
}

