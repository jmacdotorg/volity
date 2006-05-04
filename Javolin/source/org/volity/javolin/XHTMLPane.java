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

    private void setupListeners() {
        LinkListener linkListener = new VolLinkListener(this);
        addMouseListener(linkListener);
        addMouseMotionListener(linkListener);
    }

    public boolean handleLinkInternally(URL url, String urlstr) {
        return false;
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

                if (handleLinkInternally(url, urlstr)) {
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

