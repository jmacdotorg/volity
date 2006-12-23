package org.volity.javolin.chat;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;
import org.volity.javolin.LogTextPanel;
import org.volity.javolin.PlatformWrapper;

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
    private final static Color colorHyperlink = new Color(0.0f, 0.0f, 0.8f);
    private final static SimpleDateFormat timeStampFormat = new SimpleDateFormat("HH:mm:ss");
    private final static SimpleDateFormat dateTimeStampFormat = 
        new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss");

    private static Pattern sURLPattern = Pattern.compile(
        "(http|https|ftp):[^\\s]*[^\\s.?!,;\">)]+",
        Pattern.CASE_INSENSITIVE);

    public final static String ATTR_JID = "VolityJID";
    public final static String ATTR_REALJID = "VolityRealJID";
    public final static String ATTR_TYPE = "VolityType";
    public final static String ATTR_TYPE_NAME = "VolityJID:Name";
    public final static String ATTR_TYPE_TEXT = "VolityJID:Text";
    public final static String ATTR_TYPE_ABOUT = "VolityJID:About";
    public final static String ATTR_TYPE_HYPERLINK = "VolityJID:Link";

    private StyledDocument mDocument;
    private UserColorMap mColorMap;
    private Map mUserMap = new HashMap();
    private ChangeListener mColorChangeListener;

    protected UserContextMenu mPopupMenu = null;

    protected Style mStyleBase;
    protected Style mStyleCurrentTimestamp;
    protected Style mStyleDelayedTimestamp;
    protected Style mStyleHyperlink;

    /**
     * Constructor. The map may be null, if you don't want name coloring.
     */
    public ChatLogPanel(UserColorMap map) {
        super();

        mColorMap = map;

        mPopupMenu = new UserContextMenu();

        mDocument = mTextPane.getStyledDocument();

        mStyleBase = mTextPane.addStyle("base", null);
        StyleConstants.setFontFamily(mStyleBase, "SansSerif");
        StyleConstants.setFontSize(mStyleBase, 12);

        mStyleCurrentTimestamp = mTextPane.addStyle("current", mStyleBase);
        StyleConstants.setForeground(mStyleCurrentTimestamp,
            colorCurrentTimestamp);
        mStyleDelayedTimestamp = mTextPane.addStyle("delayed", mStyleBase);
        StyleConstants.setForeground(mStyleDelayedTimestamp,
            colorDelayedTimestamp);
        mStyleHyperlink = mTextPane.addStyle("hyperlink", mStyleBase);
        StyleConstants.setForeground(mStyleHyperlink,
            colorHyperlink);
        StyleConstants.setUnderline(mStyleHyperlink, true);
        mStyleHyperlink.addAttribute(ATTR_TYPE, ATTR_TYPE_HYPERLINK);

        mColorChangeListener = new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    adjustAllColors();
                }
            };
        mColorMap.addListener(mColorChangeListener);
    }

    /** Clean up component. */
    public void dispose() {
        if (mPopupMenu != null) {
            mPopupMenu.dispose();
            mPopupMenu = null;
        }

        ((JTextPanePopup)mTextPane).dispose();

        mColorMap.removeListener(mColorChangeListener);
        // Don't dispose of the colormap; we don't own it.
        super.dispose();
    }

    /** Create a JTextPane subclass. */
    protected JTextPane buildTextPane() {
        return new JTextPanePopup(this);
    }

    /**
     * Display a message which is not associated with any user.
     */
    public void message(String text) {
        message(null, false, null, text, null);
    }

    /**
     * Display a message.
     */
    public void message(String jid, String nick, String text) {
        message(jid, true, nick, text, null);
    }

    /**
     * Display a message.
     */
    public void message(String jid, boolean realjid, String nick, 
        String text) {
        message(jid, realjid, nick, text, null);
    }

    /**
     * Display a message.
     */
    public void message(String jid, String nick, String text, Date date) {
        message(jid, true, nick, text, date);
    }

    /**
     * Display a message.
     *
     * @param jid The JID associated with the message. This is not displayed,
     * but the message color is based on it, as are the contextual actions.
     * May be null. If the JID has a resource, it is counted as significant.
     *
     * @param realjid Whether the JID is a real JID or a MUC identifier.
     * Pass false if you want unique coloring for the JID but you don't
     * want a contextual menu for it.
     *
     * @param nick The nickname to show in the message head. May be null. (A
     * status message about (but not from) a user may be given with a JID but
     * no nick.)
     *
     * @param text The message.
     *
     * @param date The timestamp at which the message occurred. If null, this
     * is assumed to be the present.
     */
    public void message(String jid, boolean realjid, String nick,
        String text, Date date) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";
        
        // Get today's date sans time
        Calendar today = new GregorianCalendar();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);

        // Append time stamp
        Style dateStyle;
        if (date == null) {
            date = new Date();
            dateStyle = mStyleCurrentTimestamp;
        }
        else {
            dateStyle = mStyleDelayedTimestamp;
        }
    
        DateFormat formatter = 
            date.before(today.getTime()) ? dateTimeStampFormat : timeStampFormat;
            
        append("[" + formatter.format(date) + "] ", dateStyle);

        Entry ent = null;
        if (jid != null)
            ent = getEntry(jid, realjid);

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

        text = text+"\n";

        Matcher matcher = sURLPattern.matcher(text);
        int pos = 0;
        String seq;

        while (matcher.find()) {
            int start = matcher.start();
            if (pos < start) {
                seq = text.substring(pos, start);
                append(seq, textStyle);
            }
            seq = matcher.group();
            append(seq, mStyleHyperlink);
            pos = matcher.end();
        }
        if (pos == 0) {
            append(text, textStyle);
        }
        else {
            seq = text.substring(pos);
            append(seq, textStyle);            
        }

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
    protected Entry getEntry(String jid, boolean real) {
        Entry ent = (Entry)mUserMap.get(jid);

        if (ent == null) {
            ent = new Entry(jid, real);
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
        Boolean realjid = (Boolean)attrs.getAttribute(ATTR_REALJID);

        if (jid != null && type != null && realjid != null 
            && type != ATTR_TYPE_ABOUT) {
            int start = el.getStartOffset();
            int end = el.getEndOffset();

            Entry ent = getEntry(jid, realjid.booleanValue());

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
     * Given a popup-inducing click at a particular location, see if there's a
     * JID associated with that location. If so, pop up a contextual menu.
     * 
     * Should this just be shoved into the JTextPanePopup class?
     */
    protected void displayPopupMenu(int xp, int yp) {
        if (mPopupMenu == null)
            return;

        int pos = mTextPane.viewToModel(new Point(xp, yp));
        Element el = mDocument.getCharacterElement(pos);
        if (el == null)
            return;
        AttributeSet attrs = el.getAttributes();
        if (attrs == null)
            return;
        String jid = (String)attrs.getAttribute(ATTR_JID);
        if (jid == null)
            return;
        Boolean realjid = (Boolean)attrs.getAttribute(ATTR_REALJID);
        if (realjid != null && !realjid.booleanValue())
            return;

        mPopupMenu.adjustShow(jid, mTextPane, xp, yp);
    }

    /**
     * Given a click at a particular location, see if there's a URL associated
     * with that location. If so, launch it.
     *
     * Returns whether a URL was found.
     */
    protected boolean launchURLAt(int xp, int yp) {
        int pos = mTextPane.viewToModel(new Point(xp, yp));
        Element el = mDocument.getCharacterElement(pos);
        if (el == null)
            return false;
        
        AttributeSet attrs = el.getAttributes();
        if (attrs == null)
            return false;
        String jid = (String)attrs.getAttribute(ATTR_TYPE);
        if (jid == null)
            return false;
        if (!jid.equals(ATTR_TYPE_HYPERLINK))
            return false;

        int start = el.getStartOffset();
        int end = el.getEndOffset();
        String link = null;
        try {
            link = mDocument.getText(start, end-start);
        }
        catch (BadLocationException ex) {
            return false;
        }

        return PlatformWrapper.launchURL(link);
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
        boolean mReal;
        Style styleName;
        Style styleText;
        Style styleAbout;

        public Entry(String jid, boolean real) {
            mJID = jid;
            mReal = real;

            styleName = mTextPane.addStyle(null, mStyleBase);
            styleText = mTextPane.addStyle(null, mStyleBase);
            styleAbout = mTextPane.addStyle(null, mStyleBase);

            styleName.addAttribute(ATTR_JID, mJID);
            styleText.addAttribute(ATTR_JID, mJID);
            styleAbout.addAttribute(ATTR_JID, mJID);

            Boolean val = Boolean.valueOf(mReal);

            styleName.addAttribute(ATTR_REALJID, val);
            styleText.addAttribute(ATTR_REALJID, val);
            styleAbout.addAttribute(ATTR_REALJID, val);

            styleName.addAttribute(ATTR_TYPE, ATTR_TYPE_NAME);
            styleText.addAttribute(ATTR_TYPE, ATTR_TYPE_TEXT);
            styleAbout.addAttribute(ATTR_TYPE, ATTR_TYPE_ABOUT);

            adjustColors();
        }

        /**
         * Re-fetch the colors from the colormap, and modify the "name" and
         * "text" styles to use them.
         */
        protected void adjustColors() {
            if (mColorMap == null)
                return;

            Color nameColor = mColorMap.getUserNameColor(mJID);
            Color textColor = mColorMap.getUserTextColor(mJID);

            StyleConstants.setForeground(styleName, nameColor);
            StyleConstants.setForeground(styleText, textColor);
        }
    }

    /**
     * Subclass of JTextPane that has a pop-up menu.
     */
    protected static class JTextPanePopup extends JTextPane {
        ChatLogPanel mOwner;

        public JTextPanePopup(ChatLogPanel owner) {
            mOwner = owner;
        }

        /** Clean up component. */
        public void dispose() {
            mOwner = null;
        }

        /** Customized mouse handler that knows about popup clicks. */
        protected void processMouseEvent(MouseEvent ev) {
            if (ev.isPopupTrigger()) {
                if (mOwner != null) {
                    mOwner.displayPopupMenu(ev.getX(), ev.getY());
                }
                return;
            }

            if (ev.getID() == MouseEvent.MOUSE_CLICKED
                && ev.getClickCount() == 1
                && PlatformWrapper.launchURLAvailable()
                && mOwner != null) {
                if (mOwner.launchURLAt(ev.getX(), ev.getY()))
                    return;
            }
            
            super.processMouseEvent(ev);
        }

        /**
         * Block the scrollRectToVisible() method, as required by
         * LogTextPanel.
         */
        public void scrollRectToVisible(Rectangle rect) {
            // Do nothing.
        }
    }
}
