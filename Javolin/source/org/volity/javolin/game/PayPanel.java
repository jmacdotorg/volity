package org.volity.javolin.game;

import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.text.*;
import org.volity.client.Bookkeeper;
import org.volity.client.DefaultStatusListener;
import org.volity.client.GameServer;
import org.volity.client.GameTable;
import org.volity.client.Player;
import org.volity.client.Seat;
import org.volity.client.comm.RPCBackground;
import org.volity.javolin.JavolinApp;
import org.volity.javolin.Localize;
import org.volity.javolin.PlatformWrapper;

/* ### Bug: if the game is suspended, the checkbox should say "Paid", not "I
 * agree to pay". The protocol currently has no way to communicate this state
 * to the client.
 */

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
    private final static Color colorAuth = new Color(0x80, 0xD8, 0x80);
    // Delimiter character, chosen to be something that will not show up
    // in any real language. (U+203B: REFERENCE MARK)
    private final static char DELIMCHAR = '\u203B';
    private final static String DELIM = "\u203B";

    public static final int AUTH_FREE = 0;
    public static final int AUTH_DEMO = 1;
    public static final int AUTH_AUTH = 2;
    public static final int AUTH_UNAUTH = 3;
    public static final int AUTH_FEE = 4;
    public static final int AUTH_NOFEE = 5;

    GameServer mParlor;
    String mParlorJID;
    GameTable mGameTable;

    boolean mIsSeated = false;
    boolean mInProgress = false;
    int mAuthType = AUTH_FREE;
    int mAuthFee = 0;
    int mYourCredits = 0;
    String mPayURL = null;

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

        /*
         * We need to adjust the "I agree" checkbox when the player sits or
         * stands, and when the game begins and ends. It isn't a good idea to
         * use adjustUI() at that time -- that causes undesirable flickering.
         */
        mTableStatusListener = new DefaultStatusListener() {
                public void stateChanged(final int newstate) { 
                    // Called outside Swing thread!
                    // Invoke into the Swing thread.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                boolean val = mGameTable.isRefereeStateActive();
                                if (val == mInProgress)
                                    return;
                                mInProgress = val;
                                adjustCheckbox();
                            }
                        });
                }
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

        /*
         * Fire off a game_player_authorized() RPC to the bookkeeper.
         */
        RPCBackground.Callback callback = new RPCBackground.Callback() {
                public void run(Object result, Exception err, Object rock) {
                    if (mParlor == null) {
                        // panel has been closed.
                        return;
                    }
                    if (result == null || !(result instanceof Map)) {
                        // error or timeout -- assume game is free.
                        updatePayInfo(AUTH_FREE, 0, 0, null);
                        return;
                    }

                    Map map = (Map)result;
                    updatePayInfo(map);
                    return;
                }
            };
        JavolinApp app = JavolinApp.getSoleJavolinApp();
        Bookkeeper bookkeeper = app.getBookkeeper();
        if (bookkeeper != null) {
            bookkeeper.gamePlayerAuthorized(mParlorJID, app.getSelfJID(),
                callback, null);
        }        
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
            if (mPayCheckBox.isSelected()) {
                prefs.putInt(mParlorJID, mAuthFee);
            }
            else {
                prefs.remove(mParlorJID);
            }
        }
    }

    /**
     * Update the fields displayed in the panel. The map structure is as
     * defined in the bookkeeper RPCs.
     */
    public void updatePayInfo(Map map) {
        Object val;

        int code = AUTH_FREE;
        int fee = -1;
        int credits = -1;
        String url = null;

        val = map.get("code");
        if (val == null)
            return;
        if (val.equals("free"))
            code = AUTH_FREE;
        else if (val.equals("demo"))
            code = AUTH_DEMO;
        else if (val.equals("fee"))
            code = AUTH_FEE;
        else if (val.equals("nofee"))
            code = AUTH_NOFEE;
        else if (val.equals("auth"))
            code = AUTH_AUTH;
        else if (val.equals("unauth"))
            code = AUTH_UNAUTH;

        val = map.get("url");
        if (val != null && val instanceof String)
            url = (String)val;
        val = map.get("fee");
        if (val != null && val instanceof Integer)
            fee = ((Integer)val).intValue();
        val = map.get("credits");
        if (val != null && val instanceof Integer)
            credits = ((Integer)val).intValue();

        updatePayInfo(code, fee, credits, url);
    }

    /**
     * Update the fields displayed in the panel. For the purposes of this call
     * (but not the RPCs that invoke it), a negative value for authfee or
     * yourcredits means "do not change".
     */
    public void updatePayInfo(int authtype, int authfee, int yourcredits,
        String payurl) {
        assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

        if (authfee < 0)
            authfee = mAuthFee;
        if (yourcredits < 0)
            yourcredits = mYourCredits;

        boolean sameurl;
        if (payurl == null && mPayURL == null)
            sameurl = true;
        else if (payurl == null || mPayURL == null)
            sameurl = false;
        else
            sameurl = mPayURL.equals(payurl);

        if (mAuthType == authtype && mAuthFee == authfee 
            && mYourCredits == yourcredits && sameurl) {
            return;
        }

        mAuthType = authtype;
        mAuthFee = authfee;
        mYourCredits = yourcredits;
        mPayURL = payurl;

        adjustUI();
    }

    /**
     * Check whether the player is really in agreement with paying the given
     * sum. (This is in response to a bookkeeper query.) We are a little
     * paranoid here, since money is involved.
     */
    public boolean verifyGameFee(int fee) {
        // If we're not in a FEE authorization mode, then no.
        if (mAuthType != AUTH_FEE)
            return false;

        // If the fee value doesn't match, then no.
        if (fee != mAuthFee)
            return false;

        // If there is no check box, then no.
        if (mPayCheckBox == null)
            return false;

        // If the check box is greyed out, then no.
        if (!mPayCheckBox.isEnabled())
            return false;

        // If the check box is unchecked, then no.
        if (!mPayCheckBox.isSelected())
            return false;

        // Ok then.
        return true;
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

    /**
     * Adjust the "I agree" checkbox, when the player sits or stands.
     */
    private void adjustCheckbox() {
        if (mPayCheckBox == null) 
            return;

        if (mInProgress && mIsSeated) {
            mPayCheckBox.setText(localize("AgreedPaid"));
            mPayCheckBox.setSelected(true);
            mPayCheckBox.setEnabled(false);
        }
        else if (mIsSeated) {
            mPayCheckBox.setText(localize("AgreePay"));
            Preferences prefs = Preferences.userNodeForPackage(getClass()).node(PAYING_PARLORS);
            boolean val = (prefs.getInt(mParlorJID, 0) == mAuthFee);

            mPayCheckBox.setSelected(val);
            mPayCheckBox.setEnabled(true);
        }
        else {
            mPayCheckBox.setText(localize("AgreePayUnseated"));
            mPayCheckBox.setSelected(true);
            mPayCheckBox.setEnabled(false);
        }
    }

    /**
     * Remove and recreate the contents of the pane, as defined by mAuthType,
     * etc.
     */
    private void adjustUI() {
        setVisible(false);
        removeAll();

        mPayCheckBox = null;
        boolean visible = true;

        JTextPaneLink textpane;
        String msg, credits;

        GridBagConstraints c;
        int row = 0;

        // Free: pane is entirely hidden.
        if (mAuthType == AUTH_FREE) {
            visible = false;
        }

        // X credits per game.
        if (mAuthType == AUTH_FEE) {
            setBackground(colorAuthPay);

            Object obj;
            if (mYourCredits >= 0)
                obj = new Integer(mYourCredits);
            else
                obj = "\u203B??\u203B";
            credits = localize("Credits");
            msg = localize("YourCredits", DELIM+credits+DELIM, obj);

            textpane = new JTextPaneLink();
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
            textpane.setMessage(msg, 10);

            if (mAuthFee == 1)
                credits = localize("OneCredit");
            else
                credits = localize("ManyCredits", new Integer(mAuthFee));
            msg = localize("GameWillCost", DELIM+credits+DELIM);

            textpane = new JTextPaneLink();
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
            textpane.setMessage(msg, 12);

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

            textpane = new JTextPaneLink();
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
            textpane.setMessage(localize("WhenGameBegins"), 10);
        }

        // X credits per game, which you do not have.
        if (mAuthType == AUTH_NOFEE) {
            setBackground(colorUnauth);

            Object obj;
            if (mYourCredits >= 0)
                obj = new Integer(mYourCredits);
            else
                obj = "\u203B??\u203B";
            credits = localize("Credits");
            msg = localize("YourCredits", DELIM+credits+DELIM, obj);

            textpane = new JTextPaneLink();
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
            textpane.setMessage(msg, 10);

            if (mAuthFee == 1)
                credits = localize("OneCredit");
            else
                credits = localize("ManyCredits", new Integer(mAuthFee));
            msg = localize("GameWillCost", DELIM+credits+DELIM);

            textpane = new JTextPaneLink();
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
            textpane.setMessage(msg, 12);

            if (mYourCredits == 0) {
                String val = localize("BuySome");
                msg = localize("NotEnoughCreditsNone", DELIM+val+DELIM);
            }
            else {
                String val = localize("BuyMore");
                msg = localize("NotEnoughCredits", DELIM+val+DELIM);
            }

            textpane = new JTextPaneLink();
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
            textpane.setMessage(msg, 12);
        }

        // No subscription.
        if (mAuthType == AUTH_UNAUTH) {
            setBackground(colorUnauth);

            String val1 = localize("Authorized");
            String val2 = localize("PaymentOptions");
            msg = localize("GameNotAuthorized", DELIM+val1+DELIM,
                DELIM+val2+DELIM);

            textpane = new JTextPaneLink();
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
            textpane.setMessage(msg, 12);
        }

        // Subscription.
        if (mAuthType == AUTH_AUTH) {
            setBackground(colorAuth);

            String val = localize("Authorized");
            msg = localize("GameAuthorized", DELIM+val+DELIM);

            textpane = new JTextPaneLink();
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
            textpane.setMessage(msg, 12);
        }

        // Demo.
        if (mAuthType == AUTH_DEMO) {
            setBackground(colorAuth);

            String val = localize("Authorized");
            String val2 = localize("FreeTrial");
            msg = localize("GameDemo", DELIM+val+DELIM, DELIM+val2+DELIM);

            textpane = new JTextPaneLink();
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
            textpane.setMessage(msg, 12);
        }

        setVisible(visible);

        revalidate();
    }

    /**
     * Create the permanent contents of the panel. Actually, there aren't any.
     * We do set the thing to be opaque (color to be determined in adjustUI),
     * and add a border.
     */
    private void buildUI() {
        setOpaque(true);
        setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
    }

    /**
     * Subclass of JTextPane that can respond to mouse clicks.
     */
    protected class JTextPaneLink extends JTextPane {

        /** Customized mouse handler that knows about hyperlinks. */
        protected void processMouseEvent(MouseEvent ev) {
            if (ev.getID() == MouseEvent.MOUSE_CLICKED
                && ev.getClickCount() == 1
                && PlatformWrapper.launchURLAvailable()
                && mPayURL != null) {
                if (launchURLAt(ev.getX(), ev.getY()))
                    return;
            }
            
            super.processMouseEvent(ev);
        }

        protected boolean launchURLAt(int xp, int yp) {
            int pos = viewToModel(new Point(xp, yp));
            StyledDocument doc = this.getStyledDocument();
            Element el = doc.getCharacterElement(pos);
            if (el == null)
                return false;
        
            AttributeSet attrs = el.getAttributes();
            if (attrs == null)
                return false;
            if (!StyleConstants.isUnderline(attrs))
                return false;

            return PlatformWrapper.launchURL(mPayURL);
        }

        /**
         * Set the text of a JTextPane item to the given string, in the given
         * font.
         *
         * If the msg contains delimiter characters (\u203B), these are assumed
         * to begin and end hyperlinks to mPayURL.
         */
        public void setMessage(String msg, int fontsize) {
            String[] ls = null;

            Document doc = this.getDocument();

            SimpleAttributeSet baseStyle = new SimpleAttributeSet();
            StyleConstants.setFontFamily(baseStyle, "SansSerif");
            StyleConstants.setFontSize(baseStyle, fontsize);
            StyleConstants.setForeground(baseStyle, Color.BLACK);

            SimpleAttributeSet linkStyle = null;

            if (msg.indexOf(DELIMCHAR) >= 0) {
                if (mPayURL != null) {
                    linkStyle = new SimpleAttributeSet();
                    StyleConstants.setFontFamily(linkStyle, "SansSerif");
                    StyleConstants.setFontSize(linkStyle, fontsize);
                    StyleConstants.setForeground(linkStyle, colorHyperlink);
                    StyleConstants.setUnderline(linkStyle, true);
                }
                else {
                    linkStyle = baseStyle;
                }

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
