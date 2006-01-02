package org.volity.javolin;

import java.awt.*;
import javax.swing.Icon;

/**
 * This is a utility class which lets you concatenate several Icons together
 * and use them as a single Icon. Currently, only works horizontally, and icons
 * are center-aligned vertically. I may add more layout options if I need them.
 */
public class MultiIcon implements Icon
{
    protected Element[] mIcons;
    protected int mCount;
    protected int mTotalWidth;
    protected int mTotalHeight;

    public MultiIcon(Icon icon1, Icon icon2) {
        this(new Icon[] { icon1, icon2 } );
    }

    public MultiIcon(Icon[] icons) {
        if (icons.length <= 0)
            throw new IllegalArgumentException("must supply at least one Icon");

        mCount = icons.length;
        mIcons = new Element[mCount];
        for (int ix=0; ix<mCount; ix++)
            mIcons[ix] = new Element(icons[ix]);

        mTotalWidth = 0;
        mTotalHeight = 0;
        for (int ix=0; ix<mCount; ix++) {
            Element el = mIcons[ix];
            mTotalWidth += el.mIcon.getIconWidth();
            if (mTotalHeight < el.mIcon.getIconHeight())
                mTotalHeight = el.mIcon.getIconHeight();
        }

        int offset = 0;
        for (int ix=0; ix<mCount; ix++) {
            Element el = mIcons[ix];
            el.offsetX = offset;
            offset += el.mIcon.getIconWidth();
            el.offsetY = (mTotalHeight - el.mIcon.getIconHeight()) / 2;
        }
    }

    public int getIconWidth() {
        return mTotalWidth;
    }

    public int getIconHeight() {
        return mTotalHeight;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        for (int ix=0; ix<mCount; ix++) {
            Element el = mIcons[ix];
            el.mIcon.paintIcon(c, g, x+el.offsetX, y+el.offsetY);
        }        
    }

    /**
     * Internal data class used to store information about a particular Icon. 
     */
    protected static class Element {
        Icon mIcon;
        int offsetX, offsetY;

        protected Element(Icon icon) {
            mIcon = icon;
        }
    }
}
