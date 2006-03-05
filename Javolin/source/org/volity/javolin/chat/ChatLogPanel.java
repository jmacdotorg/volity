package org.volity.javolin.chat;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import org.volity.javolin.LogTextPanel;

//### styling

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

    private UserColorMap mColorMap;

    public ChatLogPanel(UserColorMap map) {
        super();

        mColorMap = map;
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
        Color dateColor;
        if (date == null) {
            date = new Date();
            dateColor = colorCurrentTimestamp;
        }
        else {
            dateColor = colorDelayedTimestamp;
        }
        append("[" + timeStampFormat.format(date) + "]  ", dateColor);

        String nickText;
        Color nameColor, textColor;

        if (nick == null || nick.equals("")) {
            nickText = "***";
            nameColor = Color.BLACK;
            textColor = Color.BLACK;
        }
        else {
            nickText = nick + ":";
            if (jid == null) {
                nameColor = Color.BLACK;
                textColor = Color.BLACK;
            }
            else {
                nameColor = mColorMap.getUserNameColor(jid);
                textColor = mColorMap.getUserTextColor(jid);
            }
        }

        append(nickText + " ", nameColor);
        append(text + "\n", textColor);
    }

}
