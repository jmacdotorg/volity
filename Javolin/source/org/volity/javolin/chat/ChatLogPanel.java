package org.volity.javolin.chat;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;
import org.volity.javolin.LogTextPanel;

/**
 * A subclass of LogTextPanel which is intended for chat transcripts. This
 * class provides a high-level message() method, which records a single message
 * and the context of who sent it.
 */
public class ChatLogPanel extends LogTextPanel
{
    /* Formatting stuff used by all chat logs */
    private final static Color colorCurrentTimestamp = Color.BLACK;
    private final static Color colorDelayedTimestamp = new Color(0.3f, 0.3f, 0.3f);
    private final static SimpleDateFormat timeStampFormat = new SimpleDateFormat("HH:mm:ss");

    private StyledDocument mDocument;
    private UserColorMap mColorMap;
    private Map mUserMap = new HashMap();
    private ChangeListener mColorChangeListener;

    protected Style mStyleBase;
    protected Style mStyleCurrentTimestamp;
    protected Style mStyleDelayedTimestamp;

    /**
     * Constructor. The map may be null, if you don't want name coloring.
     */
    public ChatLogPanel(UserColorMap map) {
        super();

        mDocument = mLogTextPane.getStyledDocument();
        mColorMap = map;

        mStyleBase = mLogTextPane.addStyle("base", null);
        StyleConstants.setFontFamily(mStyleBase, "SansSerif");
        StyleConstants.setFontSize(mStyleBase, 12);

        mStyleCurrentTimestamp = mLogTextPane.addStyle("current", mStyleBase);
        StyleConstants.setForeground(mStyleCurrentTimestamp,
            colorCurrentTimestamp);
        mStyleDelayedTimestamp = mLogTextPane.addStyle("delayed", mStyleBase);
        StyleConstants.setForeground(mStyleDelayedTimestamp,
            colorDelayedTimestamp);

        mColorChangeListener = new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    //###System.out.println("### chat log saw color change");
                    adjustAllColors();
                }
            };
        mColorMap.addListener(mColorChangeListener);
    }

    /** Clean up component. */
    public void dispose() {
        mColorMap.removeListener(mColorChangeListener);
        // Don't dispose of the colormap; we don't own it.
        super.dispose();
    }

    /**
     * Display a message which is not associated with any user.
     */
    public void message(String text) {
        message(null, null, text, null);
    }

    /**
     * Display a message.
     *
     * @param jid The JID associated with the message. This is not displayed,
     * but the message color is based on it, as are the contextual actions.
     * May be null. If the JID has a resource, it is counted as significant.
     *
     * @param nick The nickname to show in the message head. May be null. (A
     * status message about (but not from) a user may be given with a JID but
     * no nick.)
     */
    public void message(String jid, String nick, String text) {
        message(jid, nick, text, null);
    }

    /**
     * Display a message.
     *
     * @param jid The JID associated with the message. This is not displayed,
     * but the message color is based on it, as are the contextual actions.
     * May be null. If the JID has a resource, it is counted as significant.
     *
     * @param nick The nickname to show in the message head. May be null. (A
     * status message about (but not from) a user may be given with a JID but
     * no nick.)
     *
     * @param date The timestamp at which the message occurred. If null, this
     * is assumed to be the present.
     */
    public void message(String jid, String nick, String text, Date date) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        // Append time stamp
        Style dateStyle;
        if (date == null) {
            date = new Date();
            dateStyle = mStyleCurrentTimestamp;
        }
        else {
            dateStyle = mStyleDelayedTimestamp;
        }
        append("[" + timeStampFormat.format(date) + "]  ", dateStyle);

        Entry ent = null;
        if (jid != null)
            ent = getEntry(jid);

        String nickText;
        Style nameStyle = mStyleBase;
        Style textStyle = mStyleBase;

        if (nick == null || nick.equals("")) {
            nickText = "***";
            if (ent != null)
                textStyle = ent.styleAbout;
        }
        else {
            nickText = nick + ":";
            if (ent != null) {
                nameStyle = ent.styleName;
                textStyle = ent.styleText;
            }
        }

        append(nickText + " ", nameStyle);
        append(text + "\n", textStyle);

        scrollToBottom();
    }

    /**
     * Appends the given text to the text pane. This does not do a
     * scroll-to-bottom; the caller is responsible for that.
     */
    public void append(String text, Style style)
    {
        try {
            mDocument.insertString(mDocument.getLength(), text, style);
        }
        catch (BadLocationException ex) { }
    }

    protected Entry getEntry(String jid) {
        Entry ent = (Entry)mUserMap.get(jid);

        if (ent == null) {
            ent = new Entry(jid);
            mUserMap.put(jid, ent);
        }

        return ent;
    }

    protected void adjustAllColors() {
        for (Iterator it = mUserMap.values().iterator(); it.hasNext(); ) {
            Entry ent = (Entry)it.next();
            ent.adjustColors();
        }
    }

    protected class Entry {
        String mJID;
        Style styleName;
        Style styleText;
        Style styleAbout;

        public Entry(String jid) {
            mJID = jid;
            styleName = mLogTextPane.addStyle(null, mStyleBase);
            styleText = mLogTextPane.addStyle(null, mStyleBase);
            styleAbout = mLogTextPane.addStyle(null, mStyleBase);

            adjustColors();
        }

        protected void adjustColors() {
            if (mColorMap == null)
                return;

            Color nameColor = mColorMap.getUserNameColor(mJID);
            Color textColor = mColorMap.getUserTextColor(mJID);

            StyleConstants.setForeground(styleName, nameColor);
            StyleConstants.setForeground(styleText, textColor);
        }
    }
}
