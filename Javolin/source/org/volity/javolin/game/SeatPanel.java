package org.volity.javolin.game;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.MouseEvent;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import org.volity.client.*;
import org.volity.client.data.JIDTransfer;
import org.volity.javolin.Audio;
import org.volity.javolin.MultiIcon;

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

    static protected Icon ICON_BLANK = 
        new ImageIcon(SeatPanel.class.getResource("Blank_TreeIcon.png"));
    static protected Icon ICON_READY = 
        new ImageIcon(SeatPanel.class.getResource("Ready_TreeIcon.png"));
    static protected Icon ICON_STANDING = 
        new ImageIcon(SeatPanel.class.getResource("Standing_TreeIcon.png"));
    static protected Icon ICON_REFEREE = 
        new ImageIcon(SeatPanel.class.getResource("Referee_TreeIcon.png"));
    static protected Icon ICON_BOT = 
        new ImageIcon(SeatPanel.class.getResource("Bot_TreeIcon.png"));
    static protected Icon ICON_SELF = 
        new ImageIcon(SeatPanel.class.getResource("Self_TreeIcon.png"));

    static protected MultiIcon.Cache iconCache;
    static {
        iconCache = new MultiIcon.Cache();
        iconCache.add("ready", ICON_READY);
        iconCache.add("human", ICON_STANDING);
        iconCache.add("referee", ICON_REFEREE);
        iconCache.add("bot", ICON_BOT);
        iconCache.add("self", ICON_SELF);
    }

    static protected Icon ICON_MARK_NONE = 
        new ImageIcon(SeatPanel.class.getResource("Blank_SeatIcon.png"));
    static protected Icon ICON_MARK_TURN = 
        new ImageIcon(SeatPanel.class.getResource("Turn_SeatIcon.png"));
    static protected Icon ICON_MARK_WIN = 
        new ImageIcon(SeatPanel.class.getResource("Win_SeatIcon.png"));
    static protected Icon ICON_MARK_FIRST = 
        new ImageIcon(SeatPanel.class.getResource("First_SeatIcon.png"));
    static protected Icon ICON_MARK_OTHER = 
        new ImageIcon(SeatPanel.class.getResource("Other_SeatIcon.png"));

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
            Color color = mChart.getSeatColor(mID);

            if (color == null) {
                mBorderColor = new Color(0.5f, 0.5f, 0.5f);
                mFillColor = new Color(0.92f, 0.92f, 0.92f);
            }
            else {
                float[] arr = new float[4];

                arr = color.getRGBComponents(arr);
                arr[0] = arr[0] * 0.25f + 0.5f * 0.75f;
                arr[1] = arr[1] * 0.25f + 0.5f * 0.75f;
                arr[2] = arr[2] * 0.25f + 0.5f * 0.75f;
                mBorderColor = new Color(arr[0], arr[1], arr[2]);

                arr = color.getRGBComponents(arr);
                arr[0] = arr[0] * 0.08f + 0.92f * 0.92f;
                arr[1] = arr[1] * 0.08f + 0.92f * 0.92f;
                arr[2] = arr[2] * 0.08f + 0.92f * 0.92f;
                mFillColor = new Color(arr[0], arr[1], arr[2]);
            }
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

    protected static Icon getIconForMark(String mark) {
        if (mark == GameTable.MARK_NONE)
            return ICON_MARK_NONE;
        if (mark == GameTable.MARK_TURN)
            return ICON_MARK_TURN;
        if (mark == GameTable.MARK_WIN)
            return ICON_MARK_WIN;
        if (mark == GameTable.MARK_FIRST)
            return ICON_MARK_FIRST;
        if (mark == GameTable.MARK_OTHER)
            return ICON_MARK_OTHER;
        return ICON_MARK_NONE;
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
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        removeAll();

        boolean gameIsActive = mChart.mTable.isRefereeStateActive();
        String mark = (String)(mChart.mTable.getSeatMarks().get(mID));
        Icon markIcon = getIconForMark(mark);

        boolean isSelf = false;
        Color selfColor = null;

        GridBagConstraints c;
        JLabel seatlabel = null;
        JLabel label;
        int row = 0;

        if (!mIsObserver) {
            String id;
            id = mChart.getTranslator().translateSeatID(mID);
            if (id == null)
                id = mID;
            seatlabel = new JLabel(id, SwingConstants.LEFT);
            seatlabel.setFont(fontTitle);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.ipady = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            add(seatlabel, c);
        }
        
        int count = 0;

        if (iter != null) {
            while (iter.hasNext()) {
                Player player = (Player)iter.next();

                Font font;
                if (mIsObserver || gameIsActive || player.isReady())
                    font = fontName;
                else
                    font = fontNameUnready;

                String iconlabel;

                if (player.isReferee()) 
                    iconlabel = "referee";
                else if (player.isBot())
                    iconlabel = "bot";
                else if (mIsObserver) 
                    iconlabel = "human";
                else
                    iconlabel = "human";

                if (player.isReady())
                    iconlabel += " ready";

                if (player.isSelf()) 
                    iconlabel += " self";

                Icon icon = iconCache.get(iconlabel);

                if (player.isReferee()) {
                    mChart.mColorMap.setUserColor(player.getJID(), Color.GRAY);
                }
                Color col = mChart.mColorMap.getUserNameColor(player.getJID());

                if (player.isSelf()) {
                    isSelf = true;
                    selfColor = col;
                }

                label = new JLabelPop(player, 
                    player.getNick(), icon, SwingConstants.LEFT);
                label.setFont(font);
                label.setForeground(col);
                c = new GridBagConstraints();
                c.gridx = 0;
                c.gridy = row++;
                c.weightx = 1;
                c.weighty = 0;
                c.ipady = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.anchor = GridBagConstraints.NORTHWEST;
                add(label, c);
                count++;

                if (!player.isReferee()) {
                    /* Set up the label as a drag source. No dragging the ref,
                     * though. */
                    DragGestureRecognizer recognizer = 
                        dragSource.createDefaultDragGestureRecognizer(
                            label, 
                            DnDConstants.ACTION_MOVE, 
                            new DragSourceThing(label, player.getJID()));
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
            c.anchor = GridBagConstraints.NORTHWEST;
            add(label, c);
        }

        if (mIsObserver) {
            /* The observer panel, unlike the seat panels, can stretch
             * vertically. We want the player names to stick to the top. So we
             * put an invisible stretchy label at the bottom; this expands to
             * fill in the slack. */
            label = new JLabel(" ", ICON_BLANK, SwingConstants.LEFT);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 1;
            c.ipady = 1;
            c.fill = GridBagConstraints.BOTH;
            c.anchor = GridBagConstraints.NORTHWEST;
            add(label, c);
        }

        if (isSelf && seatlabel != null) {
            seatlabel.setForeground(selfColor);
        }

        // The mark icon (real seat panels only)

        if (!mIsObserver) {
            label = new JLabel(markIcon);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = 0;
            c.gridheight = GridBagConstraints.REMAINDER;
            c.weightx = 0;
            c.weighty = 1;
            c.fill = GridBagConstraints.VERTICAL;
            c.anchor = GridBagConstraints.CENTER;
            add(label, c);
        }

        // Track where the player is
        if (isSelf) {
            if (mIsObserver) {
                mChart.mCurrentSeat = null;
                mChart.mCurrentSelfMark = GameTable.MARK_NONE;
            }
            else {
                if (mChart.mCurrentSeat != this) {
                    mChart.mCurrentSeat = this;
                    mChart.mCurrentSelfMark = mark;
                }
                else {
                    if (mChart.mCurrentSelfMark != mark) {
                        mChart.mCurrentSelfMark = mark;
                        Audio.playMark(mark);
                    }
                }
            }
        }

        revalidate();
    }

    private class JLabelPop extends JLabel {
        Player mPlayer;

        public JLabelPop(Player player,
            String text, Icon icon, int horizontalAlignment) {
            super(text, icon, horizontalAlignment);
            mPlayer = player;
        }

        protected void processMouseEvent(MouseEvent ev) {
            if (ev.isPopupTrigger()) {
                if (mChart != null) {
                    Point pt = getLocationOnScreen();
                    mChart.displayPopupMenu(mPlayer, pt.x+ev.getX(), pt.y+ev.getY());
                }
                return;
            }
            
            super.processMouseEvent(ev);
        }
        
    }

    private class DragSourceThing 
        implements DragGestureListener {
        JComponent mComponent;
        SeatPanel mSourcePanel;
        String mJID;
        boolean mWasOpaque;

        public DragSourceThing(JComponent obj, String jid) {
            mSourcePanel = SeatPanel.this;
            mComponent = obj;
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
