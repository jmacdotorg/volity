package org.volity.javolin.game;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import org.volity.client.*;

/**
 * UI component which displays the box for a single seat. (Or the borderless
 * box which contains all the observers -- the unseated players.)
 *
 * I decided not to give this object a direct link to a Seat. Somehow, it was
 * just as elegant not to. The one place where it gets a little inelegant is
 * the mVisible field: this winds up being manipulated entirely by SeatChart,
 * because only the SeatChart knows when it should be set.
 */
public class SeatPanel extends JPanel
{
    static protected Font fontTitle = new Font("SansSerif", Font.BOLD, 10);
    static protected Font fontName = new Font("SansSerif", Font.PLAIN, 12);
    static protected Font fontNameUnready = new Font("SansSerif", Font.ITALIC, 12);

    static protected ImageIcon ICON_BLANK = 
        new ImageIcon(SeatPanel.class.getResource("Blank_TreeIcon.png"));
    static protected ImageIcon ICON_READY = 
        new ImageIcon(SeatPanel.class.getResource("Ready_TreeIcon.png"));
    static protected ImageIcon ICON_SEATED = 
        new ImageIcon(SeatPanel.class.getResource("Seated_TreeIcon.png"));
    static protected ImageIcon ICON_STANDING = 
        new ImageIcon(SeatPanel.class.getResource("Standing_TreeIcon.png"));


    SeatChart mChart;
    String mID;
    boolean mIsObserver;
    boolean mVisible; // friend to SeatChart

    /**
     * @param chart the SeatChart which owns this panel
     * @param id the ID string of the seat (or null, for the observer panel)
     * @param visible the initial visibility flag for this panel
     */
    public SeatPanel(SeatChart chart, String id, boolean visible) {
        super(new GridBagLayout());

        mChart = chart;
        mID = id;
        mIsObserver = (mID == null);
        mVisible = visible;

        Border border;
        if (mIsObserver) {
            border = new EmptyBorder(2, 8, 2, 1);
        }
        else {
            Border innerBorder = new RoundLineBorder(Color.GRAY, 3, 4);
            border = new CompoundBorder(
                new EmptyBorder(2, 1, 2, 1),
                innerBorder);
        }
        setBorder(border);
        adjustNames(null);
    }

    /**
     * Clear out this panel and rebuild it.
     *
     * This method erases everything in the container, and then adds the title
     * label. It then adds all the player name labels, or an "<empty>" label.
     *
     * The observer panel has no title label, and it doesn't get an "<empty>"
     * label either.
     */
    public void adjustNames(Iterator iter) {
        removeAll();

        GridBagConstraints c;
        JLabel label;
        int row = 0;

        if (!mIsObserver) {
            String id;
            id = mChart.getTranslator().translateSeatID(mID);
            if (id == null)
                id = mID;
            label = new JLabel(id, SwingConstants.LEFT);
            label.setFont(fontTitle);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.ipady = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            add(label, c);
        }
        
        int count = 0;

        if (iter != null) {
            while (iter.hasNext()) {
                Player player = (Player)iter.next();
                Font font;
                if (mIsObserver || player.isReady()) //### or game in progress
                    font = fontName;
                else
                    font = fontNameUnready;
                Icon icon;
                if (mIsObserver) 
                    icon = ICON_STANDING;
                else if (player.isReady())
                    icon = ICON_READY;
                else
                    icon = ICON_SEATED;

                label = new JLabel(player.getNick(), icon, SwingConstants.LEFT);
                label.setFont(font);
                c = new GridBagConstraints();
                c.gridx = 0;
                c.gridy = row++;
                c.weightx = 1;
                c.weighty = 0;
                c.ipady = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.anchor = GridBagConstraints.WEST;
                add(label, c);
                count++;
            }
        }

        if (count == 0 && !mIsObserver) {
            /* We throw in a blank icon so that the panel has exactly the same
             * size when it's empty as it does with one player. The icon has
             * the height of a player icon, but is only one pixel wide. (It
             * looks better that way, that's why.) */
            label = new JLabel("<empty>", ICON_BLANK, SwingConstants.CENTER);
            label.setFont(fontName);
            label.setForeground(Color.GRAY);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.ipady = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            add(label, c);
        }

        revalidate();
    }
}
