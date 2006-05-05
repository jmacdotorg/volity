package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.*;
import org.volity.client.data.CommandStub;
import org.w3c.dom.Document;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.swing.BasicPanel;
import org.xhtmlrenderer.swing.LinkListener;

/**
 * A customized XHTML component, which knows about the
 * "application/x-volity-command-stub" MIME type, and triggers the
 * appropriate commands when the user clicks on links of that type.
 *
 * This is a copy of the standard XHTMLPanel, except that it doesn't have
 * the font-scaling stuff, and it uses our custom LinkListener.
 */
class XHTMLPane extends BasicPanel {
    public static final int HANDLE_EXTERNAL = 1;
    public static final int HANDLE_INTERNAL = 2;
    public static final int ALREADY_HANDLED = 3;

    protected JFrame mOwner;

    public XHTMLPane(JFrame owner) {
        super();
        mOwner = owner;
        setupListeners();
    }
    public XHTMLPane(JFrame owner, UserAgentCallback uac) {
        super(uac);
        mOwner = owner;
        setupListeners();
    }

    /** Override all the setDocument calls we're likely to use */
    public void setDocument(String uri) {
        setDocument(loadDocument(uri), uri);
    }
    public void setDocument(Document doc) {
        setDocument(doc, "");
    }
    public void setDocument(Document doc, String url) {
        super.setDocument(doc, url, new XhtmlNamespaceHandler());
    }
    public void setDocument(InputStream stream, String url)
        throws Exception {
        super.setDocument(stream, url, new XhtmlNamespaceHandler());
    }

    /** Add mouse-event handlers for cursor changing and hyperlinks. */
    private void setupListeners() {
        LinkListener linkListener = new VolLinkListener(this);
        addMouseListener(linkListener);
        addMouseMotionListener(linkListener);
    }

    /**
     * Given a URL, decide how to handle it. This method should return one
     * of the following:
     *
     * - HANDLE_EXTERNAL: pass URL to the user's default browser.
     * - HANDLE_INTERNAL: change the pane to view this URL.
     * - ALREADY_HANDLED: do nothing; the URL has been passed elsewhere.
     *
     * The default is HANDLE_EXTERNAL.
     */
    public int linkDisposition(URL url, String urlstr) {
        return HANDLE_EXTERNAL;
    }

    /**
     * A customized LinkListener. When the user clicks on a URL which returns a
     * CommandStub, this executes the command. Any other URL does the default
     * thing -- changes the document display.
     */
    private class VolLinkListener extends LinkListener {
        final Class[] arrayCommandStub = new Class[] { CommandStub.class };

        BasicPanel mPanel;
        public VolLinkListener(BasicPanel panel) {
            super(panel);
            mPanel = panel;
        }
        public void linkClicked(String uri) {
            String urlstr = mPanel.getSharedContext().getUac().resolveURI(uri);
            URL url = null;
            try {
                url = new URL(urlstr);
                CommandStub stub = (CommandStub)url.getContent(arrayCommandStub);
                if (stub != null) {
                    // If it's a CommandStub, execute it.
                    JavolinApp.getSoleJavolinApp().doOpenFile(stub);
                    return;
                }

                int val = linkDisposition(url, urlstr);
                if (val == ALREADY_HANDLED) {
                    return;
                }
                if (val == HANDLE_INTERNAL) {
                    super.linkClicked(uri);
                    return;
                }

                // Otherwise, kick it to the external browser.
                boolean res = PlatformWrapper.launchURL(urlstr);
                if (!res) {
                    JOptionPane.showMessageDialog(mOwner,
                        "Unable to launch URL:\n" + urlstr,
                        JavolinApp.getAppName() + ": Error",
                        JOptionPane.ERROR_MESSAGE);
                }
                return;
            }
            catch (MalformedURLException ex) {
                new ErrorWrapper(ex);
                JOptionPane.showMessageDialog(mOwner,
                    ex.toString(),
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
            catch (CommandStub.CommandStubException ex) {
                new ErrorWrapper(ex);
                String msg = ex.getMessage();
                JOptionPane.showMessageDialog(mOwner,
                    "Badly-formed link in Finder page:\n" + msg,
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
            catch (IOException ex) {
                new ErrorWrapper(ex);
                JOptionPane.showMessageDialog(mOwner,
                    ex.toString(),
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

}

