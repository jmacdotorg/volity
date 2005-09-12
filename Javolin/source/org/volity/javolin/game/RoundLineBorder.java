package org.volity.javolin.game;

import java.awt.*;
import javax.swing.border.*;

/**
 * The library LineBorder has an option to do rounded-rectangle corners, but it
 * doesn't work right. This version does it right. You can supply a thickness
 * (line width) and radius (*outer* radius of the corner arcs). You can also
 * optionally supply a fill color -- this becomes the background for anything
 * inside the border.
 */
public class RoundLineBorder extends LineBorder
{
    protected int mRadius;
    protected Color mFillColor;

    public RoundLineBorder(Color color, int thickness, int radius) {
        super(color, thickness, true);
        mRadius = radius;
        mFillColor = null;
    }

    public RoundLineBorder(Color color, int thickness, int radius, 
        Color fillColor) {
        super(color, thickness, true);
        mRadius = radius;
        mFillColor = fillColor;
    }

    public Insets getBorderInsets(Component c, Insets insets) {
        int val = thickness + mRadius;
        insets.left = insets.right = insets.top = insets.bottom = val;
        return insets;
    }

    public Insets getBorderInsets(Component c) { 
        int val = thickness + mRadius;
        return new Insets(val, val, val, val);
    }

    public void paintBorder(Component c, Graphics  g,
        int x, int y, int width, int height) {
        Color oldColor = g.getColor();

        try {
            int rad = thickness + 2*mRadius;

            if (mFillColor != null) {
                g.setColor(mFillColor);
                g.fillRoundRect(x, y, width, height, rad, rad);
            }
 
            g.setColor(lineColor);

            /* If width and height were not adjusted, the border would
             * appear one pixel too large in both directions.
             */
            width -= 1;
            height -= 1;

            for (int i = 0; i < thickness; i++) {
                g.drawRoundRect(x, y, width, height, rad, rad);

                rad -= 2;
                if (rad < 0)
                    rad = 0;
                x += 1;
                y += 1;
                width -= 2;
                height -= 2;
            }
        }
        finally {
            g.setColor(oldColor);
        }
        
    }
}
