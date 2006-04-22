package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;

/**
 * The About box. Do not instantiate this directly; call getSoleAboutBox.
 */
public class AboutBox extends JFrame 
    implements CloseableWindow
{
    private final static String NODENAME = "AboutBox";
    private final static int MARGIN = 12; // Space to window edge
    private final static int GAP = 4; // Space between controls

    private final static String APP_URL = 
        "http://www.volity.org/projects/gamut/";

    private final static String APP_ABOUT_TEXT = 
        "Copyright 2004-2006 by the Volity project contributors:\n"   +
        "Jason McIntosh, Doug Orleans, Andrew Plotkin, Karl\n"        +
        "von Laudermann. Licensed under the Apache License,\n"        +
        "Version 2.0. See Gamut web site for details.\n\n"          +
        "Gamut is built with the Batik SVG toolkit (apache.org),\n" +
        "the Rhino JavaScript engine (mozilla.org), the Flying\n"     +
        "Saucer XHTML toolkit (xhtmlrenderer.dev.java.net), the\n"    +
        "MP3SPI MP3 library (javazoom.net), and the Smack\n"          +
        "XMPP toolkit (jivesoftware.org).";

    static Icon APP_LOGO =
        new ImageIcon(AboutBox.class.getResource("AppLogo-200.png"));

    private static AboutBox soleAboutBox = null;

    /**
     * There should only be one AboutBox at a time. This returns it if there is
     * one, or else creates it.
     */
    public static AboutBox getSoleAboutBox() {
        if (soleAboutBox == null) {
            soleAboutBox = new AboutBox();
        }
        return soleAboutBox;
    }


    protected JButton mButton;
    protected JTextPane mText;
    protected JTextField mUrlLabel;
    protected SizeAndPositionSaver mSizePosSaver;

    public AboutBox() {
        super("About " + JavolinApp.getAppName());
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    mSizePosSaver.saveSizeAndPosition();
                    soleAboutBox = null;
                }
                public void windowOpened(WindowEvent ev) {
                    // Ensure that Enter triggers the "OK" button.
                    mButton.requestFocusInWindow();
                }
            });
        
        buildUI();
        setResizable(false);
        pack();
        mSizePosSaver.restoreSizeAndPosition();

        mButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    // The "OK" button.
                    dispose();
                }
            });

        mText.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            new AbstractAction() {
                public void actionPerformed(ActionEvent ev) {
                    // Hitting Enter, even if the text box has focus
                    dispose();
                }
            });
        
        if (PlatformWrapper.launchURLAvailable()) {
            mUrlLabel.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent ev) {
                        PlatformWrapper.launchURL(APP_URL);
                    }
                });
        }

    }

    /**
     * Create the window UI.
     */
    private void buildUI() {
        Container cPane = getContentPane();
        cPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        JLabel label;

        int row = 0;

        JLabel logo = new JLabel(APP_LOGO);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        cPane.add(logo, c);

        label = new JLabel(JavolinApp.getAppName() + ": a Volity game browser");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.ipady = 8;
        cPane.add(label, c);

        label = new JLabel("Version " + JavolinApp.getAppVersion(), 
            SwingConstants.RIGHT);
        label.setFont(new Font("SansSerif", Font.PLAIN, 9));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.ipady = 2;
        cPane.add(label, c);

        mUrlLabel = new JTextField(APP_URL);
        mUrlLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        if (PlatformWrapper.launchURLAvailable()) {
            // Color the URL blue only if you can click on it.
            mUrlLabel.setForeground(new Color(0f, 0f, 0.8f));
            // Setting the cursor doesn't work on JTextField, I'm afraid
            //mUrlLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        mUrlLabel.setEditable(false);
        mUrlLabel.setOpaque(false);
        mUrlLabel.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.ipady = 8;
        cPane.add(mUrlLabel, c);

        mText = new JTextPane();
        mText.setEditable(false);
        mText.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 6));
        mText.setBackground(new Color(240, 229, 207));
        Document doc = mText.getDocument();
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setFontFamily(style, "SansSerif");
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setForeground(style, Color.BLACK);
        try {
            doc.insertString(doc.getLength(), APP_ABOUT_TEXT, style);
        }
        catch (BadLocationException ex) { }
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
        cPane.add(mText, c);

        mButton = new JButton("OK");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        cPane.add(mButton, c);
        getRootPane().setDefaultButton(mButton);

        // Necessary for all windows, for Mac support
        AppMenuBar.applyPlatformMenuBar(this);
    }
}
