package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.text.*;
import org.volity.client.DefaultStatusListener;
import org.volity.client.GameServer;
import org.volity.client.GameTable;
import org.volity.client.Player;
import org.volity.client.Seat;
import org.volity.javolin.Localize;

//### don't allow seat unless paid! Or does the ref handle that?

/**
 * UI component which displays the payment or subscription information for a
 * game.
 */
public class PayPanel extends JPanel implements ActionListener
{
    private final static String NODENAME = "PayPanel";
    private final static String PAYING_PARLORS = "PayingParlors";

    private final static Color colorHyperlink = new Color(0.0f, 0.0f, 0.8f);
    private final static Color colorAuthPay = new Color(0xE0, 0xE0, 0x40);
    private final static Color colorUnauth = new Color(0xFF, 0x98, 0x88);
    private final static Color colorAuth = new Color(0x80, 0xD0, 0x80);
    private final static char DELIMCHAR = '\u203B';
    private final static String DELIM = "\u203B";

    public static final int AUTH_FREE = 0;
    public static final int AUTH_AUTH = 1;
    public static final int AUTH_UNAUTH = 2;
    public static final int AUTH_FEE = 3;
    public static final int AUTH_NOFEE = 4;

    GameServer mParlor;
    String mParlorJID;
    GameTable mGameTable;

    boolean mIsSeated = false;
    int mAuthType = AUTH_FREE; //###
    int mAuthFee = 100; //###
    int mYourCredits = 10; //###

    DefaultStatusListener mTableStatusListener;

    JCheckBox mPayCheckBox;

    public PayPanel(GameServer parlor, GameTable table) {
        super(new SquishyGridBagLayout());

        mParlor = parlor;
        mGameTable = table;
        mParlorJID = mParlor.getResponderJID();

        mIsSeated = mGameTable.isSelfSeated();

        buildUI();
        adjustUI();

        mTableStatusListener = new DefaultStatusListener() {
                public void playerSeatChanged(final Player player, 
                    Seat oldseat, Seat newseat) {
                    // Called outside Swing thread!
                    // Invoke into the Swing thread.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (player == mGameTable.getSelfPlayer()) {
                                    boolean val = mGameTable.isSelfSeated();
                                    if (val == mIsSeated)
                                        return;
                                    mIsSeated = val;
                                    adjustCheckbox();
                                }
                            }
                        });
                }
            };
        mGameTable.addStatusListener(mTableStatusListener);
        
    }

    /** Clean up this component. */
    public void dispose() {
        mParlor = null;

        if (mGameTable != null) {
            if (mTableStatusListener != null) {
                mGameTable.removeStatusListener(mTableStatusListener);
                mTableStatusListener = null;
            }
            mGameTable = null;
        }
    }

    /**
     * ActionListener interface method implementation.
     */
    public void actionPerformed(ActionEvent ev)
    {
        Object source = ev.getSource();
        if (source == null)
            return;

        if (source == mPayCheckBox) {
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(PAYING_PARLORS);
            boolean val = mPayCheckBox.isSelected();
            prefs.putBoolean(mParlorJID, val);
        }
    }

    /**
     * Localization helper.
     */
    protected static String localize(String key) {
        return Localize.localize(NODENAME, key);
    }
    protected String localize(String key, Object arg1) {
        return Localize.localize(NODENAME, key, arg1);
    }
    protected String localize(String key, Object arg1, Object arg2) {
        return Localize.localize(NODENAME, key, arg1, arg2);
    }

    static protected void setText(JTextPane textpane, String msg, int fontsize) {
        String[] ls = null;

        Document doc = textpane.getDocument();

        SimpleAttributeSet baseStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(baseStyle, "SansSerif");
        StyleConstants.setFontSize(baseStyle, fontsize);
        StyleConstants.setForeground(baseStyle, Color.BLACK);

        SimpleAttributeSet linkStyle = null;

        if (msg.indexOf(DELIMCHAR) >= 0) {
            linkStyle = new SimpleAttributeSet();
            StyleConstants.setFontFamily(linkStyle, "SansSerif");
            StyleConstants.setFontSize(linkStyle, fontsize);
            StyleConstants.setForeground(linkStyle, colorHyperlink);
            StyleConstants.setUnderline(linkStyle, true);

            ls = msg.split(DELIM);            
        }
        else {
            ls = new String[] { msg };
        }

        try {
            doc.remove(0, doc.getLength());
            boolean link=false;
            for (int ix=0; ix<ls.length; ix++, link=!link) {
                if (ls[ix].length() == 0)
                    continue;
                SimpleAttributeSet style = (link ? linkStyle : baseStyle);
                doc.insertString(doc.getLength(), ls[ix], style);
            }
        }
        catch (BadLocationException ex) { }
    }

    private void adjustCheckbox() {
        if (mPayCheckBox == null) 
            return;

        if (mIsSeated) {
            mPayCheckBox.setText(localize("AgreePay"));
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(PAYING_PARLORS);
            boolean val = prefs.getBoolean(mParlorJID, false);

            mPayCheckBox.setSelected(val);
        }
        else {
            mPayCheckBox.setText(localize("AgreePayUnseated"));
            mPayCheckBox.setSelected(false);
        }
        mPayCheckBox.setEnabled(mIsSeated);
    }

    private void adjustUI() {
        setVisible(false);
        removeAll();

        mPayCheckBox = null;
        boolean visible = true;

        JTextPane textpane;
        String msg, credits;

        GridBagConstraints c;
        int row = 0;

        if (mAuthType == AUTH_FREE) {
            visible = false;
        }

        if (mAuthType == AUTH_FEE) {
            setBackground(colorAuthPay);

            Object obj;
            if (mYourCredits >= 0)
                obj = new Integer(mYourCredits);
            else
                obj = "\u203B??\u203B";
            credits = localize("Credits");
            msg = localize("YourCredits", DELIM+credits+DELIM, obj);

            textpane = new JTextPane();
            textpane.setEditable(false);
            textpane.setOpaque(false);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(6, 8, 4, 4);
            add(textpane, c);
            setText(textpane, msg, 10);

            if (mAuthFee == 1)
                credits = localize("OneCredit");
            else
                credits = localize("ManyCredits", new Integer(mAuthFee));
            msg = localize("GameWillCost", DELIM+credits+DELIM);

            textpane = new JTextPane();
            textpane.setEditable(false);
            textpane.setOpaque(false);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(4, 8, 4, 4);
            add(textpane, c);
            setText(textpane, msg, 12);

            mPayCheckBox = new JCheckBox("XXX");
            mPayCheckBox.addActionListener(this);
            mPayCheckBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
            mPayCheckBox.setBackground(new Color(0xFF, 0xFF, 0x60));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(0, 4, 4, 4);
            add(mPayCheckBox, c);
            adjustCheckbox();

            textpane = new JTextPane();
            textpane.setEditable(false);
            textpane.setOpaque(false);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(0, 8, 6, 4);
            add(textpane, c);
            setText(textpane, localize("WhenGameBegins"), 10);
        }

        if (mAuthType == AUTH_NOFEE) {
            setBackground(colorUnauth);

            Object obj;
            if (mYourCredits >= 0)
                obj = new Integer(mYourCredits);
            else
                obj = "\u203B??\u203B";
            credits = localize("Credits");
            msg = localize("YourCredits", DELIM+credits+DELIM, obj);

            textpane = new JTextPane();
            textpane.setEditable(false);
            textpane.setOpaque(false);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(6, 8, 4, 4);
            add(textpane, c);
            setText(textpane, msg, 10);

            if (mAuthFee == 1)
                credits = localize("OneCredit");
            else
                credits = localize("ManyCredits", new Integer(mAuthFee));
            msg = localize("GameWillCost", DELIM+credits+DELIM);

            textpane = new JTextPane();
            textpane.setEditable(false);
            textpane.setOpaque(false);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(4, 8, 4, 4);
            add(textpane, c);
            setText(textpane, msg, 12);

            if (mYourCredits == 0) {
                String val = localize("BuySome");
                msg = localize("NotEnoughCreditsNone", DELIM+val+DELIM);
            }
            else {
                String val = localize("BuyMore");
                msg = localize("NotEnoughCredits", DELIM+val+DELIM);
            }

            textpane = new JTextPane();
            textpane.setEditable(false);
            textpane.setOpaque(false);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(4, 8, 6, 4);
            add(textpane, c);
            setText(textpane, msg, 12);
        }

        if (mAuthType == AUTH_UNAUTH) {
            setBackground(colorUnauth);

            String val1 = localize("Authorized");
            String val2 = localize("PaymentOptions");
            msg = localize("GameNotAuthorized", DELIM+val1+DELIM,
                DELIM+val2+DELIM);

            textpane = new JTextPane();
            textpane.setEditable(false);
            textpane.setOpaque(false);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(6, 8, 6, 4);
            add(textpane, c);
            setText(textpane, msg, 12);
        }

        if (mAuthType == AUTH_AUTH) {
            setBackground(colorAuth);

            String val = localize("Authorized");
            msg = localize("GameAuthorized", DELIM+val+DELIM);

            textpane = new JTextPane();
            textpane.setEditable(false);
            textpane.setOpaque(false);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(6, 8, 6, 4);
            add(textpane, c);
            setText(textpane, msg, 12);
        }

        setVisible(visible);

        revalidate();
    }

    /**
     * Create the permanent contents of the panel. Actually, there aren't any.
     */
    private void buildUI() {
        setOpaque(true);
        setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
    }

    /**
     * We don't want the minimum width of the contents to impact the minimum
     * width of the container. (Much.) So we use a GridBagLayout subclass which
     * isn't pushy about width.
     */
    private static class SquishyGridBagLayout extends GridBagLayout {
        public SquishyGridBagLayout() {
            super();
        }

        public Dimension minimumLayoutSize(Container parent) {
            Dimension dim = super.minimumLayoutSize(parent);
            if (dim.width > 64)
                dim = new Dimension(64, dim.height);
            return dim;
        }
    }

}
