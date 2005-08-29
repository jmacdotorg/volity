package org.volity.testbench;

import java.awt.*;
import javax.swing.*;

/**
 * A version of FlowLayout that gets taller when its contents flow off the
 * right edge (so that they can flow around to a second row). It does this
 * by having a preferred width.
 *
 * Thanks to Dan Shiovitz for figuring out how to do this.
 */
class RestrictedWidthFlowLayout extends FlowLayout
{
  public static final long serialVersionUID = 1;
  
  public RestrictedWidthFlowLayout(int alignment) {
    super(alignment);
  }
  
  public RestrictedWidthFlowLayout(int alignment, int hgap, int vgap) {
    super(alignment, hgap, vgap);
  }
  
  // if target's parent has a width set yet, then take that as our width
  public Dimension preferredLayoutSize(Container target) {
    synchronized (target.getTreeLock()) {
      int parentWidth = target.getParent().getWidth();
      if (parentWidth < 1)
        return super.preferredLayoutSize(target);
      
      Dimension dim = new Dimension(parentWidth, 0);
      int nmembers = target.getComponentCount();

      Insets insets = target.getInsets();
      // count the end-of-line gap but not the beginning one, which we'll
      // do later
      int maxWidth = (parentWidth 
          - (insets.left + insets.right + this.getHgap()));

      int rowHeight = 0;
      int widthSoFar = 0;

      for (int i = 0; i < nmembers; i++) {
        Component m = target.getComponent(i);
        if (!m.isVisible())
          continue;
          
        Dimension d = m.getPreferredSize();

        // wrap if it won't fit on this row:
        if (widthSoFar + getHgap() + d.width >= maxWidth) {
          // ok, wrap:
          dim.height += getVgap();
          dim.height += rowHeight;
          rowHeight = 0;
          widthSoFar = 0;
        }
        
        widthSoFar += getHgap() + d.width;
        rowHeight = Math.max(rowHeight, d.height);
      }
      
      // finally, count the last bit of height:
      dim.height += getVgap();
      dim.height += rowHeight;

      return dim;
    }
  }
}
