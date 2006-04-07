package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Base class for dialog-like (nonmodal) windows. Defines some layout metric
 * constants, and adds support for saving and restoring the dialog size and
 * position in the preferences storage.
 *
 * This is different from BaseDialog for lousy reasons. We want to have
 * dialog-like, non-modal windows. However, if a JDialog is non-modal, the Mac
 * menu bars vanish when it has focus. And you can't *give* it menu bars,
 * because the Mac "menu bars at the top of the screen" property only affects
 * JFrames! So we need a JFrame that behaves like a dialog box.
 *
 * (Since this class takes care of menu bars, you don't have to call
 * applyPlatformMenuBar in your subclass.)
 */
public class BaseWindow extends JFrame
{
    protected final static int MARGIN = 12; // Space to window edge
    protected final static int SPACING = 6; // Space between related controls
    protected final static int GAP = 12; // Space between unrelated controls

    protected JavolinApp mOwner;
    protected SizeAndPositionSaver mSizePosSaver;

    public BaseWindow(JavolinApp owner, String title, String nodeName) {
        super(title);
        mOwner = owner;

        // Necessary for all windows, for Mac support
        AppMenuBar.applyPlatformMenuBar(this);

        mSizePosSaver = new SizeAndPositionSaver(this, nodeName);

        // Save window size and position when closing
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        mOwner.mDialogWindows.add(this);
        AppMenuBar.notifyUpdateWindowMenu();

        addWindowListener(
            new WindowAdapter()
            {
                public void windowClosed(WindowEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();

                    BaseWindow win = BaseWindow.this;
                    mOwner.mDialogWindows.remove(win);
                    AppMenuBar.notifyUpdateWindowMenu();
                }
            });
    }

    /**
     * If the subclass calls setTitle, we need to recreate the Window menu to
     * pick up the new title string. (Because the original
     * notifyUpdateWindowMenu call is in the BaseWindow constructor, and
     * therefore occurs before the subclass has a chance to call setTitle.)
     */
    public void setTitle(String title) {
        super.setTitle(title);
        AppMenuBar.notifyUpdateWindowMenu();
    }
}
