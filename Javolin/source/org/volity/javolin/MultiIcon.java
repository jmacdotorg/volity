package org.volity.javolin;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * If you want to mix and match a bunch of Icons, but you don't want to
     * create a static MultiIcon for each combination, you can use this handy
     * cache facility.
     *
     * Prime it by calling add(label, icon) for a bunch of icons; give each one
     * a unique name. You can then call get(string). The string can be any
     * label, or a space-separated list of labels. You will get an Icon or
     * MultiIcon matching your list.
     *
     * The methods of this class are thread-safe.
     */
    public static class Cache {
        Map mMap;

        /** Constructor. */
        public Cache() {
            mMap = new HashMap();
        }

        /**
         * Add a basic Icon to the cache. (Technically this could be a
         * MultiIcon, but that wouldn't be efficient cache usage.) The label
         * must be a simple string (containing no spaces), which will identify
         * the icon.
         */
        public synchronized void add(String label, Icon icon) {
            if (label.indexOf(' ') >= 0)
                throw new RuntimeException("MultiIcon.Cache labels may not contain spaces.");

            mMap.put(label, icon);
        }

        /**
         * Fetch an Icon matching a given string. This should be the label of
         * an existing Icon, or a space-separated list of labels.
         *
         * If there is no such Icon in the cache, it will be created (as a
         * MultiIcon) from the ones that do exist.
         */
        public synchronized Icon get(String labels) {
            Icon res = (Icon)mMap.get(labels);
            if (res != null)
                return res;

            String [] ls = labels.split(" +");
            Icon [] icons = new Icon[ls.length];
            for (int ix=0; ix<ls.length; ix++) {
                Icon icon = (Icon)mMap.get(ls[ix]);
                if (icon == null)
                    throw new RuntimeException("MultiIcon.Cache does not contain '"+ls[ix]+"'.");
                icons[ix] = icon;
            }

            res = new MultiIcon(icons);
            mMap.put(labels, res);
            return res;            
        }
    }
}
