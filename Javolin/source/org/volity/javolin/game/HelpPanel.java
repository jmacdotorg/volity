package org.volity.javolin.game;

import java.awt.*;
import java.util.Iterator;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;
import org.volity.client.*;
import org.volity.javolin.PrefsDialog;

/**
 * UI component which displays contextual help for a game. (Not game-specific
 * help -- just a guide to sitting down, becoming ready, summoning bots, etc.)
 */
public class HelpPanel extends JPanel
    implements StatusListener
{
    static protected Icon ICON_HELP = 
        new ImageIcon(HelpPanel.class.getResource("HelpIcon.png"));

    GameTable mTable;
    boolean everPlayed;
    boolean usesSeatMarks;

    boolean isEmpty;
    JTextPane mText;
    ChangeListener mShowHelpListener;

    public HelpPanel(GameTable table) {
        super(new GridBagLayout());

        mTable = table;
        everPlayed = false;
        usesSeatMarks = false;

        isEmpty = true;
        mText = null;
        rebuildUI();

        mShowHelpListener = new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    String key = (String)ev.getSource();
                    if (key == PrefsDialog.GAMESHOWHELP_KEY)
                        rebuildUI();
                }
            };
        PrefsDialog.addListener(PrefsDialog.GAME_OPTIONS,
            mShowHelpListener);
        mTable.addStatusListener(this);
    }

    /** Clean up this component. */
    public void dispose() {
        if (mShowHelpListener != null) {
            PrefsDialog.removeListener(PrefsDialog.GAME_OPTIONS,
                mShowHelpListener);
            mShowHelpListener = null;
        }
        if (mTable != null) {
            mTable.removeStatusListener(this);
            mTable = null;
        }
    }

    /***** Methods which implement StatusListener. *****/

    /* All these methods are called from outside the Swing thread. */

    public void stateChanged(int newstate) {
        if (newstate != GameTable.STATE_SETUP)
            everPlayed = true;

        invokeRebuildUI();
    }

    public void seatListKnown() {
        invokeRebuildUI();
    }
    public void requiredSeatsChanged() {
        invokeRebuildUI();
    }
    public void seatMarksChanged(List seats) {
        if (seats.size() != 0) {
            if (!usesSeatMarks) {
                usesSeatMarks = true;
                invokeRebuildUI();
            }
        }
    }

    public void playerJoined(Player player) { 
        invokeRebuildUI();
    }
    public void playerLeft(Player player) { 
        invokeRebuildUI();
    }
    public void playerNickChanged(Player player, String oldNick) { }
    public void playerIsReferee(Player player) { }

    public void playerSeatChanged(Player player, Seat oldseat, Seat newseat) {
        invokeRebuildUI();
    }
    public void playerReady(Player player, boolean flag) {
        invokeRebuildUI();
    }

    private void invokeRebuildUI() {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    rebuildUI();
                }
            });
    }

    /**
     * Create a help text based on the current table status. Paragraphs should
     * be delimited by double newlines, with a single newline at the end of the
     * string.
     */
    private String buildHelpText() {
        if (mTable == null)
            return "The window is closed.\n";

        StringBuffer text = new StringBuffer();

        int refstate = mTable.getRefereeState();
        boolean selfseated = mTable.isSelfSeated();
        boolean selfready = mTable.isSelfReady();
        boolean readytime = true;
        int humancount = 0;
        int humanseated = 0;
        int botcount = 0;
        int botseated = 0;
        int reqseats = 0;
        int reqseatsempty = 0;

        for (Iterator it = mTable.getPlayers(); it.hasNext(); ) {
            Player player = (Player)it.next();
            if (player.isReferee()) { 
                // ignore
            }
            else if (player.isBot()) {
                botcount++;
                if (player.isSeated())
                    botseated++;
            }
            else {
                humancount++;
                if (player.isSeated())
                    humanseated++;
            }
        }

        for (Iterator it = mTable.getSeats(); it.hasNext(); ) {
            Seat seat = (Seat)it.next();
            if (seat.isRequired()) {
                reqseats++;
                if (!seat.isOccupied())
                    reqseatsempty++;
            }
        }

        if (refstate == GameTable.STATE_UNKNOWN) {
            text.append("Javolin is contacting the game referee. Please wait...\n");
            return text.toString();
        }

        if (refstate == GameTable.STATE_SETUP && !everPlayed) {
            text.append("The game has not yet begun.\n");
        }
        else if (refstate == GameTable.STATE_SETUP) {
            text.append("The game has ended. You can play again, change seats, or stand up. To leave the table, just close this window.\n");
        }
        else if (refstate == GameTable.STATE_SUSPENDED) {
            text.append("The game has been suspended. You may change seats or summon bots.\n");
        }
        else {
            text.append("The game is in progress.\n");
            readytime = false;
        }

        if (!readytime) {
            if (!selfseated) {
                text.append("\nYou are a bystander at this game. You can watch, and you may chat with the other participants. However, you will not be able to make moves.\n");
                text.append("\nTo join a game, you will have to wait until this game ends.\n");
            }
            else {
                text.append("\nYou are now playing the game. Follow the instructions in the game area.");
                if (usesSeatMarks)
                    text.append(" Watch the seat list above; a blue arrow will indicate whose turn it is.\n");
                text.append("\n");
            }
            return text.toString();
        }

        if (!selfseated) {
            if (humancount <= 1 && !everPlayed)
                text.append("\nThis table has just been created. To take part");
            else
                text.append("\nAt the moment, you are a bystander. To take part in this game");
            text.append(", press the Seat button");
            if (reqseats > 0)
                text.append(" or drag your name to one of the seats");
            text.append(".\n");
        }
        else {
            if (reqseatsempty > 0) {
                text.append("\nThis game requires");
                if (reqseats == 1)
                    text.append(" one seat");
                else
                    text.append(" " + String.valueOf(reqseats) + " seats");
                text.append(" to be filled before the game can begin. You will need to wait for");
                if (reqseatsempty == 1)
                    text.append(" another player");
                else
                    text.append(" more players");
                text.append(". Or you can request");
                if (reqseatsempty == 1)
                    text.append(" a bot");
                else
                    text.append(" bots");
                text.append(", from the Game menu.\n");
            }
            else {
                String verb = "game will begin";
                if (refstate == GameTable.STATE_SETUP && !everPlayed)
                    verb = "game will begin";
                else if (refstate == GameTable.STATE_SETUP) 
                    verb = "next game will begin";
                else if (refstate == GameTable.STATE_SUSPENDED)
                    verb = "game will resume";

                if (!selfready) {
                    text.append("\nThe ");
                    text.append(verb);
                    text.append(" when all seated players have indicated that they are ready. If you are willing to start the game with these players, press the Ready button.\n");
                }
                else {
                    text.append("\nYou have already pressed the Ready button. The ");
                    text.append(verb);
                    text.append(" game will begin when all the other seated players have pressed theirs.\n");
                }
            }
        }

        if (botseated > 0 && humanseated == 0) {
            text.append("\nA game cannot begin with only bots at the table. At least one human be be seated. If you want to watch bots play against each other, share a bot's seat (by dragging yourself into it).\n");
        }
        else if (botseated < botcount) {
            text.append("\n");
            if (botcount-botseated == 1)
                text.append("A bot has been summoned, but it is not yet taking part in the game");
            else if (botseated == 0)
                text.append("Bots have been summoned, but they are not yet taking part in the game");
            else
                text.append("Bots have been summoned, but they are not all taking part in the game");
            text.append(". To place a bot into the game, drag it to the Seat button");
            if (reqseats > 0)
                text.append(" or to one of the seats");
            text.append(".\n");
        }

        return text.toString();
    }

    /**
     * Change the TextPane to reflect the current table status.
     */
    private void adjustText() {
        String text = buildHelpText();

        Document doc = mText.getDocument();

        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setFontFamily(style, "SansSerif");
        StyleConstants.setFontSize(style, 10);
        StyleConstants.setForeground(style, Color.BLACK);
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(doc.getLength(), text, style);
        }
        catch (BadLocationException ex) { }

        revalidate();
    }

    /**
     * Clean out and recreate the contents of this panel. This is called both
     * at construction time and when the table state changes.
     */
    private void rebuildUI() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        boolean beEmpty = false;

        if (!PrefsDialog.getGameShowHelp())
            beEmpty = true;
        if (mTable == null) 
            beEmpty = true;

        if (beEmpty) {
            if (isEmpty) {
                return;
            }
            removeAll();
            revalidate();
            isEmpty = true;
            return;
        }

        if (!isEmpty) {
            adjustText();
            return;
        }
        isEmpty = false;

        GridBagConstraints c;
        JLabel label;
        int row = 0;

        label = new JLabel(ICON_HELP);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        add(label, c);

        mText = new JTextPane();
        mText.setEditable(false);
        mText.setOpaque(false);
        mText.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.SOUTH;
        add(mText, c);

        adjustText();
    }
}