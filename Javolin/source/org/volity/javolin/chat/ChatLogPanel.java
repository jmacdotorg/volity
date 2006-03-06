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
 *
 * This is also capable of recoloring all the messages in the log, if the
 * color-name associations (or the preferences) change.
 */
public class ChatLogPanel extends LogTextPanel
{
    /* Formatting stuff used by all chat logs */
    private final static Color colorCurrentTimestamp = Color.BLACK;
    private final static Color colorDelayedTimestamp = new Color(0.3f, 0.3f, 0.3f);
    private final static SimpleDateFormat timeStampFormat = new SimpleDateFormat("HH:mm:ss");

    private final static String ATTR_JID = "VolityJID";
    private final static String ATTR_TYPE = "VolityType";
    private final static String ATTR_TYPE_NAME = "VolityJID:Name";
    private final static String ATTR_TYPE_TEXT = "VolityJID:Text";
    private final static String ATTR_TYPE_ABOUT = "VolityJID:About";

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

        mColorMap = map;

        mDocument = mLogTextPane.getStyledDocument();

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

    /**
     * Get the entry for a given JID (creating one if necessary). This entry
     * contains the styles needed to draw the user's messages.
     */
    protected Entry getEntry(String jid) {
        Entry ent = (Entry)mUserMap.get(jid);

        if (ent == null) {
            ent = new Entry(jid);
            mUserMap.put(jid, ent);
        }

        return ent;
    }

    /**
     * Recolor all the text. This is called when we notice that the colormap
     * has changed.
     * 
     * It turns out that logical styles in Swing are a filthy trap and an
     * unusable snare, so we have to go through the document and apply color
     * attributes to each chunk which is a user name or message.
     */
    protected void adjustAllColors() {
        for (Iterator it = mUserMap.values().iterator(); it.hasNext(); ) {
            Entry ent = (Entry)it.next();
            ent.adjustColors();
        }

        Element el = mDocument.getDefaultRootElement();
        recurseElements(el);
    }

    /**
     * Work function -- used to iterate through Element tree in
     * adjustAllColors().
     */
    protected void recurseElements(Element el) {
        if (!el.isLeaf()) {
            int count = el.getElementCount();
            for (int ix=0; ix<count; ix++) {
                Element subel = el.getElement(ix);
                recurseElements(subel);
            }
            return;
        }

        AttributeSet attrs = el.getAttributes();
        String jid = (String)attrs.getAttribute(ATTR_JID);
        Object type = attrs.getAttribute(ATTR_TYPE);
        if (jid != null && type != null && type != ATTR_TYPE_ABOUT) {
            int start = el.getStartOffset();
            int end = el.getEndOffset();

            Entry ent = getEntry(jid);

            Style style = null;
            if (type == ATTR_TYPE_NAME)
                style = ent.styleName;
            if (type == ATTR_TYPE_TEXT)
                style = ent.styleText;

            mDocument.setCharacterAttributes(start, end-start,
                style, true);
        }
    }

    /**
     * An Entry structure is associated with every JID which appears in the
     * chat. This contains the styles needed to draw the user's text:
     *
     * styleName: name (displayed before message)
     * styleText: a message from the user
     * styleAbout: used for "... has joined the chat" messages
     *
     * Each of these styles has an ATTR_JID attribute (whose value is the JID).
     * This is very handy when it comes time to pop up a contextual menu.
     */
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

            styleName.addAttribute(ATTR_JID, mJID);
            styleText.addAttribute(ATTR_JID, mJID);
            styleAbout.addAttribute(ATTR_JID, mJID);

            styleName.addAttribute(ATTR_TYPE, ATTR_TYPE_NAME);
            styleText.addAttribute(ATTR_TYPE, ATTR_TYPE_TEXT);
            styleAbout.addAttribute(ATTR_TYPE, ATTR_TYPE_ABOUT);

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
