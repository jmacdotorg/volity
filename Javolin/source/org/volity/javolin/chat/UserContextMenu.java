package org.volity.javolin.chat;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import org.volity.jabber.JIDUtils;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.PlatformWrapper;

/**
 * A contextual menu which can be used anywhere we have a JID. (Lines in chat
 * panes, etc.)
 */
public class UserContextMenu extends JPopupMenu
    implements ActionListener
{
    String mJID = null;
    boolean mIsSelf;

    JMenuItem mChatMenuItem;
    JMenuItem mStatsMenuItem;

    /** Constructor. */
    public UserContextMenu() {
        super();
        buildUI();
    }

    /** Clean up component. */
    public void dispose() {
        removeAll();
        mJID = null;
    }

    /**
     * This is the entry point; call this when you want a menu. The JID should
     * be a full JID if possible.
     */
    public void adjustShow(String jid, JComponent parent, int xpos, int ypos) {
        if (isVisible()) {
            // Menu is already in use.
            return;
        }

        mJID = jid;
        mIsSelf = false;

        String selfjid = JavolinApp.getSoleJavolinApp().getSelfJID();
        if (selfjid != null && JIDUtils.bareMatch(selfjid, jid))
            mIsSelf = true;

        mChatMenuItem.setEnabled(!mIsSelf);

        show(parent, xpos, ypos);
    }

    // Implements ActionListener interface
    public void actionPerformed(ActionEvent ev) {
        if (mJID == null)
            return;

        Object source = ev.getSource();
        if (source == null)
            return;

        if (source == mChatMenuItem) {
            JavolinApp.getSoleJavolinApp().chatWithUser(mJID);
        }
        if (source == mStatsMenuItem) {
            JavolinApp.getSoleJavolinApp().showUserGameStats(mJID);
        }
    }

    /** Create the interface. */
    protected void buildUI() {
        mChatMenuItem = new JMenuItem("Chat With User");
        mChatMenuItem.addActionListener(this);
        add(mChatMenuItem);

        mStatsMenuItem = new JMenuItem("Game Stats");
        mStatsMenuItem.addActionListener(this);
        add(mStatsMenuItem);
        if (!PlatformWrapper.launchURLAvailable())
            mStatsMenuItem.setEnabled(false);
    }
}
