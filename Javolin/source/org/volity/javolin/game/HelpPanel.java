package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.util.Iterator;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;
import org.volity.client.GameTable;
import org.volity.client.Player;
import org.volity.client.Seat;
import org.volity.client.StatusListener;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.Localize;
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
    static protected Icon ICON_HELP_PRESS = 
        new ImageIcon(HelpPanel.class.getResource("HelpIconPress.png"));

    GameTable mTable;
    boolean everPlayed;
    boolean usesSeatMarks;

    JTextPane mText;
    ChangeListener mShowHelpListener;

    public HelpPanel(GameTable table) {
        super(new GridBagLayout());

        mTable = table;
        everPlayed = false;
        usesSeatMarks = false;

        mText = null;
        buildUI();
        adjustUI();

        mShowHelpListener = new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    String key = (String)ev.getSource();
                    if (key == PrefsDialog.GAMESHOWHELP_KEY)
                        adjustUI();
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

        invokeAdjustUI();
    }

    public void seatListKnown() {
        invokeAdjustUI();
    }
    public void requiredSeatsChanged() {
        invokeAdjustUI();
    }
    public void seatMarksChanged(List seats) {
        if (seats.size() != 0) {
            if (!usesSeatMarks) {
                usesSeatMarks = true;
                invokeAdjustUI();
            }
        }
    }

    public void playerJoined(Player player) { 
        invokeAdjustUI();
    }
    public void playerLeft(Player player) { 
        invokeAdjustUI();
    }
    public void playerNickChanged(Player player, String oldNick) { }
    public void playerIsReferee(Player player) { }

    public void playerSeatChanged(Player player, Seat oldseat, Seat newseat) {
        invokeAdjustUI();
    }
    public void playerReady(Player player, boolean flag) {
        invokeAdjustUI();
    }

    private void invokeAdjustUI() {
        // Called outside Swing thread!
        // Invoke into the Swing thread.
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    adjustUI();
                }
            });
    }

    /**
     * Localization helper.
     */
    protected String localize(String key) {
        return Localize.localize("HelpPanel", key);
    }
    protected String localize(String key, Object arg1) {
        return Localize.localize("HelpPanel", key, arg1);
    }
    protected String localize(String key, Object arg1, Object arg2) {
        return Localize.localize("HelpPanel", key, arg1, arg2);
    }
    protected String localize(String key, Object[] argls) {
        return Localize.localize("HelpPanel", key, argls);
    }

    /**
     * Create a help text based on the current table status. Paragraphs should
     * be delimited by double newlines, with a single newline at the end of the
     * string.
     */
    private String buildHelpText() {
        if (mTable == null)
            return localize("WindowClosed") + "\n";

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
        boolean optseatmarker = false;

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
            if (!(seat.isRequired() || seat.isOccupied()))
                optseatmarker = true;
        }

        if (refstate == GameTable.STATE_UNKNOWN) {
            text.append(localize("ContactingReferee",
                            JavolinApp.getAppName()) + "\n");
            return text.toString();
        }

        if (refstate == GameTable.STATE_AUTHORIZING) {
            text.append(localize("StateAuthorizing"));
            return text.toString();
        }

        if (refstate == GameTable.STATE_SETUP && !everPlayed) {
            text.append(localize("StateSetup")+"\n");
        }
        else if (refstate == GameTable.STATE_SETUP) {
            text.append(localize("StateSetupAgain")+"\n");
        }
        else if (refstate == GameTable.STATE_SUSPENDED) {
            text.append(localize("StateSuspended")+"\n");
        }
        else if (refstate == GameTable.STATE_DISRUPTED) {
            text.append(localize("StateDisrupted")+"\n");
            readytime = false;
        }
        else if (refstate == GameTable.STATE_ABANDONED) {
            text.append(localize("StateAbandoned")+"\n");
            readytime = false;
        }
        else {
            text.append(localize("StateActive")+"\n");
            readytime = false;
        }

        if (!readytime) {
            if (!selfseated) {
                text.append("\n"+localize("ActiveBystander")+"\n");
            }
            else {
                text.append("\n"+localize("ActivePlaying"));
                if (usesSeatMarks)
                    text.append(" " + localize("ActiveSeatMarks"));
                text.append("\n");
            }
            return text.toString();
        }

        if (!selfseated) {
            String prefix, suffix;
            if (humancount <= 1 && !everPlayed)
                prefix = localize("ReadyStandingNew");
            else
                prefix = localize("ReadyStandingAgain");
            if (reqseats > 0) 
                suffix = " " + localize("ReadyStandingRequired");
            else if (optseatmarker) 
                suffix = " " + localize("ReadyStandingOptional");
            else
                suffix = "";
            text.append("\n");
            text.append(localize("ReadyStanding", prefix, suffix));
            text.append("\n");
        }
        else {
            if (reqseatsempty > 0) {
                Object[] argls = new Object[4];
                if (reqseats == 1)
                    argls[0] = localize("OneSeat");
                else
                    argls[0] = localize("ManySeats", new Integer(reqseats));
                if (reqseatsempty == 1)
                    argls[1] = localize("OnePlayer");
                else
                    argls[1] = localize("ManyPlayers");
                if (reqseatsempty == 1)
                    argls[2] = localize("OneBot");
                else
                    argls[2] = localize("ManyBots");
                argls[3] = Localize.localize("Menu", "Game");
                
                text.append("\n");
                text.append(localize("ReadyRequiredSeats", argls));
                text.append("\n");
            }
            else {
                String verb = localize("GameWillBegin");
                if (refstate == GameTable.STATE_SETUP && !everPlayed)
                    verb = localize("GameWillBegin");
                else if (refstate == GameTable.STATE_SETUP) 
                    verb = localize("GameWillBeginAgain");
                else if (refstate == GameTable.STATE_SUSPENDED)
                    verb = localize("GameWillResume");

                if (!selfready) {
                    text.append("\n");
                    text.append(localize("ReadySeatedUnready", verb));
                    text.append("\n");
                }
                else {
                    text.append("\n");
                    text.append(localize("ReadySeatedReady", verb));
                    text.append("\n");
                }
            }
        }

        if (botseated > 0 && humanseated == 0) {
            text.append("\n");
            text.append(localize("OnlyBots"));
            text.append("\n");
        }
        else if (botseated < botcount) {
            String botdesc, seatdesc;

            if (botcount-botseated == 1)
                botdesc = localize("OneBotStanding");
            else if (botseated == 0)
                botdesc = localize("ManyBotsStanding");
            else
                botdesc = localize("SomeBotsStanding");
            if (reqseats > 0)
                seatdesc = " " + localize("BotsStandingRequired");
            else if (optseatmarker) 
                seatdesc = " " + localize("BotsStandingOptional");
            else
                seatdesc = "";

            text.append("\n");
            text.append(localize("Bots", botdesc, seatdesc));
            text.append("\n");
        }

        return text.toString();
    }

    /**
     * Create or destroy the text pane.
     */
    private void adjustUI() {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        boolean beEmpty = false;

        if (!PrefsDialog.getGameShowHelp())
            beEmpty = true;
        if (mTable == null) 
            beEmpty = true;

        if (beEmpty) {
            if (mText == null) {
                return;
            }
            remove(mText);
            mText = null;
            revalidate();
            return;
        }

        if (mText == null) {
            GridBagConstraints c;

            mText = new JTextPane();
            mText.setEditable(false);
            mText.setOpaque(false);
            mText.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 1;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.SOUTH;
            add(mText, c);
        }

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
     * Create the permanent contents of the panel (only the button).
     */
    private void buildUI() {
        GridBagConstraints c;
        JButton button;
        int row = 0;

        button = new JButton(ICON_HELP);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPressedIcon(ICON_HELP_PRESS);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.weighty = 0;
        c.anchor = GridBagConstraints.CENTER;
        add(button, c);

        button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    boolean val = PrefsDialog.getGameShowHelp();
                    PrefsDialog.setGameShowHelp(!val);
                }
            });

    }
}
