package org.volity.javolin.game;

import java.util.*;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.volity.client.*;
import org.volity.javolin.chat.UserColorMap;

public class SeatChart 
    implements StatusListener
{
    GameTable mTable;
    JTextPane mUserListText;
    SimpleAttributeSet mBaseUserListStyle;
    UserColorMap mUserColorMap;

    public SeatChart(GameTable table, UserColorMap colormap) {
        mTable = table;
        mUserColorMap = colormap;

        mBaseUserListStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(mBaseUserListStyle, "SansSerif");
        StyleConstants.setFontSize(mBaseUserListStyle, 12);

        mUserListText = new JTextPane();
        mUserListText.setEditable(false);

        mTable.addStatusListener(this);
    }

    public JComponent getChart() {
        return mUserListText;
    }

    /**
     * Updates the list of users in the MUC.
     */
    private void updateUserList()
    {
        mUserListText.setText("");
        Iterator iter = mTable.getPlayers(); //###

        while (iter.hasNext()) {
            Player player = (Player)iter.next();
            String userName = player.getNick(); //###

            SimpleAttributeSet style = new SimpleAttributeSet(mBaseUserListStyle);
            StyleConstants.setForeground(style,
                mUserColorMap.getUserNameColor(userName));

            Document doc = mUserListText.getDocument();

            try {
                doc.insertString(doc.getLength(), userName + "\n", style);
            }
            catch (BadLocationException ex) {
            }
        }
    }

    /***** Methods which implement StatusListener. *****/

    public void seatListKnown() {
        System.out.println("Known seats changed.");
    }
    public void requiredSeatsChanged() {
        System.out.println("Required seats changed.");
    }

    public void playerJoined(Player player) {
        System.out.println("Player joined: " + player.getJID() + " (" + player.getNick() + ")");
        updateUserList(); //###
    }
    public void playerLeft(Player player) {
        System.out.println("Player left: " + player.getJID() + " (" + player.getNick() + ")");
        updateUserList(); //###
    }
    public void playerNickChanged(Player player, String oldNick) {
        System.out.println("Player nickname changed: " + player.getJID() + " (" + player.getNick() + "), was (" + oldNick + ")");
        updateUserList(); //###
    }

    public void playerSeatChanged(Player player, Seat oldseat, Seat newseat) {
        String oldid = "<unseated>";
        String newid = "<unseated>";
        if (oldseat != null)
            oldid = oldseat.getID();
        if (newseat != null)
            newid = newseat.getID();
        System.out.println("Player seat: " + player.getJID() + " from " + oldid + " to " + newid);
    }
    public void playerReady(Player player, boolean flag) {
        System.out.println("Player ready: " + player.getJID() + ": " + flag);
    }

}

