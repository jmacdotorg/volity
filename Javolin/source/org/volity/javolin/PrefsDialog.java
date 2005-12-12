package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;

/**
 * The Preferences box. Do not instantiate this directly; call
 * getSolePrefsDialog.
 */
public class PrefsDialog extends JFrame
{
    private final static String NODENAME = "PrefsDialog";
    private final static String TABPANESELECTION_KEY = "TabSelection";

    /* Preference group names are top-level nodes. They're also used as keys in
     * the notification system, so they're publicly readable. */
    public final static String CHAT_COLOR_OPTIONS = "ChatPrefs";
    public final static String ROSTER_DISPLAY_OPTIONS = "RosterPrefs";

    /* Keys within the various top-level nodes. */
    private final static String CHATNAMESHADE_KEY = "NameShade";
    private final static String CHATBODYSHADE_KEY = "BodyShade";
    private final static String ROSTERSHOWOFFLINE_KEY = "ShowOffline";
    private final static String ROSTERSHOWREVERSE_KEY = "ShowReverse";
    private final static String ROSTERNOTIFYSUBSCRIPTIONS_KEY = "NotifySubscriptions";

    private final static int MARGIN = 12; // Space to window edge
    private final static int GAP = 4; // Space between controls

    private static ImageIcon GREEN_0_ICON =
        new ImageIcon(PrefsDialog.class.getResource("Green_0_ColorIcon.png"));
    private static ImageIcon GREEN_1_ICON =
        new ImageIcon(PrefsDialog.class.getResource("Green_1_ColorIcon.png"));
    private static ImageIcon GREEN_2_ICON =
        new ImageIcon(PrefsDialog.class.getResource("Green_2_ColorIcon.png"));
    private static ImageIcon GREEN_3_ICON =
        new ImageIcon(PrefsDialog.class.getResource("Green_3_ColorIcon.png"));
    private static ImageIcon GREEN_4_ICON =
        new ImageIcon(PrefsDialog.class.getResource("Green_4_ColorIcon.png"));

    /*
     * The actual preference values. Call loadPreferences() at app start time
     * to make sure they're set properly.
     */
    private static int prefChatBodyShade;
    private static int prefChatNameShade;
    private static boolean prefRosterShowOffline;
    private static boolean prefRosterShowReverse;
    private static boolean prefRosterNotifySubscriptions;

    public static void loadPreferences() {
        Preferences prefs;

        prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(CHAT_COLOR_OPTIONS);
        prefChatBodyShade = prefs.getInt(CHATBODYSHADE_KEY, 30);
        prefChatNameShade = prefs.getInt(CHATNAMESHADE_KEY, 0);

        prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(ROSTER_DISPLAY_OPTIONS);
        prefRosterShowOffline = prefs.getBoolean(ROSTERSHOWOFFLINE_KEY, true);
        prefRosterShowReverse = prefs.getBoolean(ROSTERSHOWREVERSE_KEY, false);
        prefRosterNotifySubscriptions = prefs.getBoolean(ROSTERNOTIFYSUBSCRIPTIONS_KEY, true);
    }

    public static int getChatBodyShade() { return prefChatBodyShade; }
    public static int getChatNameShade() { return prefChatNameShade; }
    public static boolean getRosterShowOffline() { return prefRosterShowOffline; }
    public static boolean getRosterShowReverse() { return prefRosterShowReverse; }
    public static boolean getRosterNotifySubscriptions() { return prefRosterNotifySubscriptions; }

    // The sole existing PrefsDialog.
    private static PrefsDialog solePrefsDialog = null;

    /*
     * Map of node (String) to listeners (List of Runnables).
     */
    private static Map changeListeners = new HashMap();

    /**
     * There should only be one PrefsDialog at a time. This returns it if there
     * is one, or else creates it.
     */
    public static PrefsDialog getSolePrefsDialog(JavolinApp owner) {
        if (solePrefsDialog == null) {
            solePrefsDialog = new PrefsDialog(owner);
        }
        return solePrefsDialog;
    }

    /**
     * Notify listeners of changes in a particular group of preferences.
     */
    private static void noticeChange(String node) {
        List listeners = (List)changeListeners.get(node);
        if (listeners != null) {
            for (Iterator it = listeners.iterator(); it.hasNext(); ) {
                Runnable listener = (Runnable)it.next();
                System.out.println("### change " + node + ": " + listener.toString());
                listener.run();
            }
        }
    }

    /**
     * Add a listener for changes in a particular group of preferences.
     *
     * The listener will be called in the Swing thread.
     */
    public static void addListener(String node, Runnable listener) {
        List listeners = (List)changeListeners.get(node);
        if (listeners == null) {
            listeners = new ArrayList();
            changeListeners.put(node, listeners);
        }
        listeners.add(listener);
    }

    /**
     * Remove a listener for changes in a particular group of preferences.
     */
    public static void removeListener(String node, Runnable listener) {
        List listeners = (List)changeListeners.get(node);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    private final static String LABEL_ROSTERSHOWOFFLINE = "Display offline buddies";
    private final static String LABEL_ROSTERSHOWREVERSE = "Display people whose roster you are on";
    private final static String LABEL_ROSTERNOTIFYSUBSCRIPTIONS = "Ask when someone adds you to his roster";

    private JavolinApp mOwner;
    private SizeAndPositionSaver mSizePosSaver;

    private JTabbedPane mTabPane;
    private JCheckBox mRosterShowOffline;
    private JCheckBox mRosterShowReverse;
    private JCheckBox mRosterNotifySubscriptions;
    private JSlider mChatBodyShade;
    private JSlider mChatNameShade;
    private JTextPane mChatSampleText;

    private PrefsDialog(JavolinApp owner) 
    {
        mOwner = owner;

        setTitle(JavolinApp.getAppName() + " Preferences");
        buildUI();

        setSize(450, 350);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        mSizePosSaver.restoreSizeAndPosition();

        restoreWindowState();

        // Handle closing the window to quit the app
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                public void windowClosed(WindowEvent ev) {
                    mSizePosSaver.saveSizeAndPosition();
                    solePrefsDialog = null;
                }
            });
        
        // Save window size and position whenever it is moved or resized
        addComponentListener(
            new ComponentAdapter()
            {
                public void componentMoved(ComponentEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }

                public void componentResized(ComponentEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }
            });

        mTabPane.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    saveWindowState();
                }
            });

        mRosterShowOffline.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefRosterShowOffline = mRosterShowOffline.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(ROSTER_DISPLAY_OPTIONS);
                    prefs.putBoolean(ROSTERSHOWOFFLINE_KEY, prefRosterShowOffline);
                    noticeChange(ROSTER_DISPLAY_OPTIONS);
                }
            });

        mRosterShowReverse.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefRosterShowReverse = mRosterShowReverse.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(ROSTER_DISPLAY_OPTIONS);
                    prefs.putBoolean(ROSTERSHOWREVERSE_KEY, prefRosterShowReverse);
                    noticeChange(ROSTER_DISPLAY_OPTIONS);
                }
            });

        mRosterNotifySubscriptions.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefRosterNotifySubscriptions = mRosterNotifySubscriptions.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(ROSTER_DISPLAY_OPTIONS);
                    prefs.putBoolean(ROSTERNOTIFYSUBSCRIPTIONS_KEY, prefRosterNotifySubscriptions);
                    noticeChange(ROSTER_DISPLAY_OPTIONS);
                }
            });

        mChatNameShade.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    if (!mChatNameShade.getValueIsAdjusting()) {
                        prefChatNameShade = mChatNameShade.getValue();
                        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(CHAT_COLOR_OPTIONS);
                        prefs.putInt(CHATNAMESHADE_KEY, prefChatNameShade);
                        updateChatSampleText();
                        noticeChange(CHAT_COLOR_OPTIONS);
                    }
                }
            });

        mChatBodyShade.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    if (!mChatBodyShade.getValueIsAdjusting()) {
                        prefChatBodyShade = mChatBodyShade.getValue();
                        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(CHAT_COLOR_OPTIONS);
                        prefs.putInt(CHATBODYSHADE_KEY, prefChatBodyShade);
                        updateChatSampleText();
                        noticeChange(CHAT_COLOR_OPTIONS);
                    }
                }
            });

        show();
    }

    protected void saveWindowState() {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        int pos = mTabPane.getSelectedIndex();
        if (pos >= 0) {
            prefs.putInt(TABPANESELECTION_KEY, pos);
        }
    }

    protected void restoreWindowState() {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        int pos = prefs.getInt(TABPANESELECTION_KEY, 0);
        try {
            mTabPane.setSelectedIndex(pos);
        }
        catch (IndexOutOfBoundsException ex) {
            // forget it 
        }
    }

    /**
     * Given a pure color, lighten or darken it. The shift value must be from
     * -100 (white) to 0 (pure color unchanged) to -100 (black).
     * @return the transformed color.
     */
    public static Color transformColor(Color col, int shift) {
        if (shift == 0)
            return col;

        float[] arr = new float[3];
        col.getColorComponents(arr);

        if (shift > 0) {
            float ratio = (float)(100 - shift) * 0.01f;
            arr[0] *= ratio;
            arr[1] *= ratio;
            arr[2] *= ratio;
        }
        else {
            float ratio = (float)(100 + shift) * 0.01f;
            arr[0] = 1.0f - (1.0f - arr[0]) * ratio;
            arr[1] = 1.0f - (1.0f - arr[1]) * ratio;
            arr[2] = 1.0f - (1.0f - arr[2]) * ratio;
        }

        return new Color(arr[0], arr[1], arr[2]);
    }

    /**
     * Update the sample-chat-text area. (Called when the color preferences
     * change.)
     */
    private void updateChatSampleText() {
        Color roscol = new Color(1.0f, 0.0f, 0.0f);
        Color guilcol = new Color(0.0f, 0.681f, 0.0f);
        Color rosbodycol = transformColor(roscol, prefChatBodyShade);
        Color rosnamecol = transformColor(roscol, prefChatNameShade);
        Color guilbodycol = transformColor(guilcol, prefChatBodyShade);
        Color guilnamecol = transformColor(guilcol, prefChatNameShade);

        Document doc = mChatSampleText.getDocument();
        try {
            // Clear the area.
            doc.remove(0, doc.getLength());

            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setFontFamily(style, "SansSerif");
            StyleConstants.setFontSize(style, 12);

            StyleConstants.setForeground(style, Color.BLACK);
            doc.insertString(doc.getLength(), "[10:15:00]  ", style);
            doc.insertString(doc.getLength(), "*** Sample text:\n", style);

            StyleConstants.setForeground(style, Color.BLACK);
            doc.insertString(doc.getLength(), "[10:15:01]  ", style);
            StyleConstants.setForeground(style, rosnamecol);
            doc.insertString(doc.getLength(), "rosenstern:", style);
            StyleConstants.setForeground(style, rosbodycol);
            doc.insertString(doc.getLength(), " We could go.\n", style);
            
            StyleConstants.setForeground(style, Color.BLACK);
            doc.insertString(doc.getLength(), "[10:15:04]  ", style);
            StyleConstants.setForeground(style, guilnamecol);
            doc.insertString(doc.getLength(), "guildencrantz:", style);
            StyleConstants.setForeground(style, guilbodycol);
            doc.insertString(doc.getLength(), " Where?\n", style);
            
            StyleConstants.setForeground(style, Color.BLACK);
            doc.insertString(doc.getLength(), "[10:15:10]  ", style);
            StyleConstants.setForeground(style, rosnamecol);
            doc.insertString(doc.getLength(), "rosenstern:", style);
            StyleConstants.setForeground(style, rosbodycol);
            doc.insertString(doc.getLength(), " After him.", style);
            
        }
        catch (BadLocationException ex) { }
    }

    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());

        GridBagConstraints c;
        JLabel label;
        
        mTabPane = new JTabbedPane();

        {
            JPanel pane = new JPanel(new GridBagLayout());
            
            int row = 0;
            
            mRosterShowOffline = new JCheckBox(LABEL_ROSTERSHOWOFFLINE, prefRosterShowOffline);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(mRosterShowOffline, c);

            mRosterShowReverse = new JCheckBox(LABEL_ROSTERSHOWREVERSE, prefRosterShowReverse);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
            pane.add(mRosterShowReverse, c);

            mRosterNotifySubscriptions = new JCheckBox(LABEL_ROSTERNOTIFYSUBSCRIPTIONS, prefRosterNotifySubscriptions);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
            pane.add(mRosterNotifySubscriptions, c);

            // Blank stretchy spacer
            label = new JLabel(" ");
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 1;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(label, c);

            mTabPane.addTab("Roster", pane);
        }

        {
            JPanel pane = new JPanel(new GridBagLayout());
            
            int row = 0;

            label = new JLabel("Sender color:");
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.weightx = 0;
            c.weighty = 0;
            c.gridwidth = 1;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(MARGIN, MARGIN, 0, 0);
            pane.add(label, c);

            mChatNameShade = new ShadeSlider(prefChatNameShade);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(mChatNameShade, c);

            label = new JLabel("Message color:");
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.weightx = 0;
            c.weighty = 0;
            c.gridwidth = 1;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(GAP, MARGIN, 0, 0);
            pane.add(label, c);

            mChatBodyShade = new ShadeSlider(prefChatBodyShade);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
            pane.add(mChatBodyShade, c);

            mChatSampleText = new JTextPane();
            mChatSampleText.setEditable(false);
            updateChatSampleText();
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(mChatSampleText, c);

            label = new JLabel("(Changes do not affect messages already");
            label.setFont(new Font("SansSerif", Font.PLAIN, 12));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(label, c);

            label = new JLabel("displayed in existing chat windows.)");
            label.setFont(new Font("SansSerif", Font.PLAIN, 12));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, MARGIN, 0, MARGIN);
            pane.add(label, c);

            // Blank stretchy spacer
            label = new JLabel(" ");
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 1;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(label, c);

            mTabPane.addTab("Chat", pane);
        }


        cPane.add(mTabPane);

        // Necessary for all windows, for Mac support
        JavolinMenuBar.applyPlatformMenuBar(this);
    }

    static private class ShadeSlider extends JSlider {
        public ShadeSlider(int start) {
            super(HORIZONTAL, -100, 100, start);

            setPaintTicks(true);
            setPaintLabels(true);
            setMajorTickSpacing(20);

            Hashtable colormap = new Hashtable();
            colormap.put(new Integer(-90), new JLabel(GREEN_4_ICON));
            colormap.put(new Integer(-45), new JLabel(GREEN_3_ICON));
            colormap.put(new Integer( 0), new JLabel(GREEN_2_ICON));
            colormap.put(new Integer(45), new JLabel(GREEN_1_ICON));
            colormap.put(new Integer(90), new JLabel(GREEN_0_ICON));
            setLabelTable(colormap);
        }
    }
}

