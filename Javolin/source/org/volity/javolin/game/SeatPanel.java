package org.volity.javolin.game;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.io.*;
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
    static DragSource dragSource = DragSource.getDefaultDragSource();

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
    DropTarget mDropTarget;

    Color mBorderColor, mFillColor, mDragColor;
    Border mStandardBorder, mDragBorder;

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

        setOpaque(true);
        setBackground(Color.WHITE);

        if (mIsObserver) {
            mBorderColor = null;
            mFillColor = Color.WHITE;
            mDragColor = new Color(0.866f, 0.866f, 0.92f);
            /*
             * We want the labels in the observer panel to line up with the
             * labels in other (outlined) panels. So the border here has seven
             * extra pixels on the left side, to match the seven (3+4) pixel
             * thickness of the RoundLineBorders below.
             */
            mStandardBorder = new EmptyBorder(3, 9, 1, 2);
            mDragBorder = mStandardBorder;
        }
        else {
            mBorderColor = new Color(0.5f, 0.5f, 0.5f);
            mFillColor = new Color(0.92f, 0.92f, 0.92f);
            mDragColor = new Color(0.8f, 0.8f, 0.9f);

            Border innerBorder;
            innerBorder = new RoundLineBorder(mBorderColor, 3, 4, mFillColor);
            mStandardBorder = new CompoundBorder(
                new EmptyBorder(3, 2, 1, 2),
                innerBorder);

            innerBorder = new RoundLineBorder(mBorderColor, 3, 4, mDragColor);
            mDragBorder = new CompoundBorder(
                new EmptyBorder(3, 2, 1, 2),
                innerBorder);
        }
        setBorder(mStandardBorder);
        adjustNames(null);

        /* And now, drag-and-drop code. The SeatPanel is a drop target; each
         * individual name JLabel is a drag source. This would have been easier
         * with TransferHandler, but that mechanism can't highlight source or
         * target objects (as far as I know). */

        mDropTarget = new DropTarget(this, DnDConstants.ACTION_MOVE, 
            new DropTargetAdapter() {
                public void dragEnter(DropTargetDragEvent dtde) {
                    // Highlight target.
                    if (mIsObserver) {
                        SeatPanel.this.setBackground(mDragColor);
                    }
                    else {
                        SeatPanel.this.setBorder(mDragBorder);
                    }
                }
                public void dragExit(DropTargetEvent dte) {
                    // Dehighlight target.
                    if (mIsObserver) {
                        SeatPanel.this.setBackground(mFillColor);
                    }
                    else {
                        SeatPanel.this.setBorder(mStandardBorder);
                    }
                }
                public void drop(DropTargetDropEvent ev) {
                    // First, dehighlight target.
                    if (mIsObserver) {
                        SeatPanel.this.setBackground(mFillColor);
                    }
                    else {
                        SeatPanel.this.setBorder(mStandardBorder);
                    }
                    try {
                        Transferable transfer = ev.getTransferable();
                        if (transfer.isDataFlavorSupported(JIDTransfer.JIDFlavor)) {
                            ev.acceptDrop(DnDConstants.ACTION_MOVE);
                            JIDTransfer obj = (JIDTransfer)transfer.getTransferData(JIDTransfer.JIDFlavor);
                            mChart.requestSeatChange(obj.getJID(),
                                SeatPanel.this.mID);
                            ev.dropComplete(true);
                            return;
                        }
                        ev.rejectDrop();
                    }
                    catch (Exception ex) {
                        ev.rejectDrop();
                    }
                }
            });
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

                if (player.isReferee()) {
                    mChart.mUserColorMap.setUserColor(player.getJID(), player.getNick(), Color.GRAY);
                }
                Color col = mChart.mUserColorMap.getUserNameColor(player.getJID(), player.getNick());

                label = new JLabel(player.getNick(), icon, SwingConstants.LEFT);
                label.setFont(font);
                label.setForeground(col);
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

                if (!player.isReferee()) {
                    /* Set up the label as a drag source. No dragging the ref,
                     * though. */
                    DragGestureRecognizer recognizer = 
                        dragSource.createDefaultDragGestureRecognizer(
                            label, 
                            DnDConstants.ACTION_MOVE, 
                            new DragSourceThing(label, this, player.getJID()));
                }
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

    private class DragSourceThing 
        implements DragGestureListener {
        JComponent mComponent;
        SeatPanel mSourcePanel;
        String mJID;
        boolean mWasOpaque;

        public DragSourceThing(JComponent obj, SeatPanel source, String jid) {
            mComponent = obj;
            mSourcePanel = source;
            mJID = jid;
        }

        public void dragGestureRecognized(DragGestureEvent ev) {
            // Highlight the source label.
            mWasOpaque = mComponent.isOpaque();
            mComponent.setBackground(mDragColor);
            mComponent.setOpaque(true);
            // We don't want to accept a drag to the panel we started in.
            mSourcePanel.mDropTarget.setActive(false);

            Transferable transfer = new JIDTransfer(mJID);
            dragSource.startDrag(ev, DragSource.DefaultMoveDrop, transfer,
                new DragSourceAdapter() {
                    public void dragDropEnd(DragSourceDropEvent ev) {
                        // Unhighlight and reactivate drag target.
                        mComponent.setOpaque(mWasOpaque);
                        mComponent.setBackground(null);
                        mSourcePanel.mDropTarget.setActive(true);
                    }
                });
        }
    }
}
