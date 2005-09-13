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

        Graphics2D g2 = (Graphics2D)g;
        RenderingHints hints = new RenderingHints(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHints(hints);

        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();

        try {
            int diameter = 2*mRadius;

            if (mFillColor != null) {
                g2.setColor(mFillColor);
                g2.fillRoundRect(x, y, width-1, height-1, diameter, diameter);
            }

            g2.setStroke(new BasicStroke(thickness));
            g2.setColor(lineColor);
            g2.drawRoundRect(x+thickness/2, y+thickness/2,
                width - thickness, height - thickness, 
                diameter, diameter);
        }
        finally {
            g2.setColor(oldColor);
            g2.setStroke(oldStroke);
        }
    }
}
