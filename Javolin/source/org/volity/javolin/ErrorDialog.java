package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import javax.swing.*;
import javax.swing.text.*;

/**
 * A window which displays an exception.
 */
public class ErrorDialog extends JFrame 
{
    private final static String NODENAME = "DebugErrorDialog";
    private final static int MARGIN = 12; // Space to window edge
    private final static int GAP = 4; // Space between controls

    private static SimpleDateFormat TimeStampFormat =
        new SimpleDateFormat("HH:mm:ss");

    JButton mButton;
    JScrollPane mScroller;

    protected SizeAndPositionSaver mSizePosSaver;
    protected ErrorWrapper mError;

    /**
     * Create a window showing the given ErrorWrapper.
     */
    public ErrorDialog(ErrorWrapper err) {
        this(err, false);
    }

    /**
     * Create a window showing the given Throwable. (The time field in the
     * window will show the current time, since a Throwable has no built-in
     * timestamp.)
     */
    public ErrorDialog(Throwable ex) {
        this(new ErrorWrapper(ex), false);
    }

    /**
     * Create a window showing the given ErrorWrapper.
     * @param err the error to display.
     * @param showFull whether the window should appear displaying the full
     *        stack trace. (NOT YET IMPLEMENTED)
     */
    public ErrorDialog(ErrorWrapper err, boolean showFull) {
        super("Exception");

        if (err == null)
            err = new ErrorWrapper("There have been no errors yet.");
        mError = err;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    mSizePosSaver.saveSizeAndPosition();
                }
                public void windowOpened(WindowEvent ev) {
                    // Make sure the top lines (the interesting ones) are
                    // visible.
                    JScrollBar vertBar = mScroller.getVerticalScrollBar();
                    vertBar.setValue(vertBar.getMinimum());
                    // Ensure that Enter triggers the "Ok" button.
                    mButton.requestFocusInWindow();
                }
            });
        
        buildUI();
        setSize(650, 350);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        mSizePosSaver.restoreSizeAndPosition();

        mButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    // The "Ok" button.
                    dispose();
                }
            });
    }

    /**
     * Create the window UI.
     */
    private void buildUI() {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());

        Throwable except = mError.getException();
        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        PrintStream instream = new PrintStream(outstream);
        except.printStackTrace(instream);
        instream.close();
        String msg = outstream.toString();
        msg = msg.replace('\t', ' ');

        JTextPane text = new JTextPane();
        text.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        text.setEditable(false);
        Document doc = text.getDocument();
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setFontFamily(style, "SansSerif");
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setForeground(style, Color.BLACK);
        try {
            doc.insertString(doc.getLength(), msg, style);
        }
        catch (BadLocationException ex) { }
        mScroller = new JScrollPane(text);
        mScroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        cPane.add(mScroller, BorderLayout.CENTER);

        // Add a top panel.
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        cPane.add(panel, BorderLayout.NORTH);

        GridBagConstraints c;
        JLabel label;
        int row = 0;

        label = new JLabel(except.toString());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
        panel.add(label, c);

        msg = TimeStampFormat.format(mError.getTime());
        label = new JLabel("(at " + msg + ")");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(GAP, MARGIN, 0, 0);
        panel.add(label, c);

        mButton = new JButton("Close");
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(GAP, MARGIN, MARGIN, MARGIN);
        panel.add(mButton, c);
        getRootPane().setDefaultButton(mButton);

        row++;

        // Necessary for all windows, for Mac support
        JavolinMenuBar.applyPlatformMenuBar(this);
    }
}
