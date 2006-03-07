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
import org.volity.client.Audio;
import org.volity.client.Referee;
import org.volity.client.comm.RPCDispatcherDebug;

/**
 * The Preferences box. Do not instantiate this directly; call
 * getSolePrefsDialog.
 */
public class PrefsDialog extends JFrame
    implements CloseableWindow
{
    private final static String NODENAME = "PrefsDialog";
    private final static String TABPANESELECTION_KEY = "TabSelection";

    /* Preference group names are top-level nodes. They're also used as keys in
     * the notification system, so they're publicly readable. */
    public final static String GAME_OPTIONS = "GamePrefs";
    public final static String CHAT_COLOR_OPTIONS = "ChatPrefs";
    public final static String ROSTER_DISPLAY_OPTIONS = "RosterPrefs";
    public final static String SOUND_OPTIONS = "SoundPrefs";
    public final static String DEBUG_OPTIONS = "DebugPrefs";

    /* Keys within the various top-level nodes. */

    public final static String GAMESHOWHELP_KEY = "ShowHelp";

    public final static String CHATNAMESHADE_KEY = "NameShade";
    public final static String CHATBODYSHADE_KEY = "BodyShade";

    public final static String ROSTERSHOWOFFLINE_KEY = "ShowOffline";
    public final static String ROSTERSHOWREVERSE_KEY = "ShowReverse";
    public final static String ROSTERNOTIFYSUBSCRIPTIONS_KEY = "NotifySubscriptions";

    public final static String SOUNDPLAYAUDIO_KEY = "PlayAudio";
    public final static String SOUNDSHOWALTTAGS_KEY = "ShowAltTags";
    public final static String SOUNDUSEBUDDYSOUNDS_KEY = "UseBuddySounds";
    public final static String SOUNDUSEINVITEDSOUND_KEY = "UseInvitedSound";
    public final static String SOUNDUSEMARKSOUNDS_KEY = "UseMarkSounds";
    public final static String SOUNDUSEMESSAGESOUND_KEY = "UseMessageSound";
    public final static String SOUNDUSEPRESENCESOUNDS_KEY = "UsePresenceSounds";
    public final static String SOUNDUSETHREADSOUND_KEY = "UseThreadSound";
    public final static String SOUNDUSEERRORSOUND_KEY= "UseErrorSound";

    public final static String DEBUGSHOWRPCS_KEY = "ShowRPCs";


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
    private static boolean prefGameShowHelp;
    private static int prefChatBodyShade;
    private static int prefChatNameShade;
    private static boolean prefRosterShowOffline;
    private static boolean prefRosterShowReverse;
    private static boolean prefRosterNotifySubscriptions;
    private static boolean prefSoundPlayAudio;
    private static boolean prefSoundShowAltTags;
    private static boolean prefSoundUseBuddySounds;
    private static boolean prefSoundUseInvitedSound;
    private static boolean prefSoundUseMarkSounds;
    private static boolean prefSoundUseMessageSound;
    private static boolean prefSoundUsePresenceSounds;
    private static boolean prefSoundUseThreadSound;
    private static boolean prefSoundUseErrorSound;
    private static boolean prefDebugShowRPCs;

    /**
     * Load the initial preference state. This should be called exactly once,
     * at app startup time.
     */
    public static void loadPreferences() {
        Preferences prefs;

        prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(GAME_OPTIONS);
        prefGameShowHelp = prefs.getBoolean(GAMESHOWHELP_KEY, true);

        prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(CHAT_COLOR_OPTIONS);
        prefChatBodyShade = prefs.getInt(CHATBODYSHADE_KEY, 30);
        prefChatNameShade = prefs.getInt(CHATNAMESHADE_KEY, 0);

        prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(ROSTER_DISPLAY_OPTIONS);
        prefRosterShowOffline = prefs.getBoolean(ROSTERSHOWOFFLINE_KEY, true);
        prefRosterShowReverse = prefs.getBoolean(ROSTERSHOWREVERSE_KEY, false);
        prefRosterNotifySubscriptions = prefs.getBoolean(ROSTERNOTIFYSUBSCRIPTIONS_KEY, true);

        prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(SOUND_OPTIONS);
        prefSoundPlayAudio = prefs.getBoolean(SOUNDPLAYAUDIO_KEY, true);
        prefSoundShowAltTags = prefs.getBoolean(SOUNDSHOWALTTAGS_KEY, false);
        prefSoundUseBuddySounds = prefs.getBoolean(SOUNDUSEBUDDYSOUNDS_KEY, true);
        prefSoundUseInvitedSound = prefs.getBoolean(SOUNDUSEINVITEDSOUND_KEY, true);
        prefSoundUseMarkSounds = prefs.getBoolean(SOUNDUSEMARKSOUNDS_KEY, true);
        prefSoundUseMessageSound = prefs.getBoolean(SOUNDUSEMESSAGESOUND_KEY, true);
        prefSoundUsePresenceSounds = prefs.getBoolean(SOUNDUSEPRESENCESOUNDS_KEY, true);
        prefSoundUseThreadSound = prefs.getBoolean(SOUNDUSETHREADSOUND_KEY, true);
        prefSoundUseErrorSound = prefs.getBoolean(SOUNDUSEERRORSOUND_KEY, true);

        prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(DEBUG_OPTIONS);
        prefDebugShowRPCs = prefs.getBoolean(DEBUGSHOWRPCS_KEY, false);

        /* The audio preferences are used by a client library, rather than
         * being grabbed by another part of Javolin. So we set up the initial
         * values and the listener now. */
        Audio.setPlayAudio(prefSoundPlayAudio);
        Audio.setShowAltTags(prefSoundShowAltTags);

        PrefsDialog.addListener(PrefsDialog.SOUND_OPTIONS,
            new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    String key = (String)ev.getSource();
                    if (key == SOUNDPLAYAUDIO_KEY)
                        Audio.setPlayAudio(prefSoundPlayAudio);
                    if (key == SOUNDSHOWALTTAGS_KEY)
                        Audio.setShowAltTags(prefSoundShowAltTags);
                }
            });

        /* Same goes for the debug prefs. */
        RPCDispatcherDebug.setDebugOutput(prefDebugShowRPCs);
        Referee.setDebugOutput(prefDebugShowRPCs);

        PrefsDialog.addListener(PrefsDialog.DEBUG_OPTIONS,
            new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    RPCDispatcherDebug.setDebugOutput(prefDebugShowRPCs);
                    Referee.setDebugOutput(prefDebugShowRPCs);
                }
            });
    }

    public static boolean getGameShowHelp() { return prefGameShowHelp; }
    public static int getChatBodyShade() { return prefChatBodyShade; }
    public static int getChatNameShade() { return prefChatNameShade; }
    public static boolean getRosterShowOffline() { return prefRosterShowOffline; }
    public static boolean getRosterShowReverse() { return prefRosterShowReverse; }
    public static boolean getRosterNotifySubscriptions() { return prefRosterNotifySubscriptions; }
    public static boolean getSoundUseBuddySounds() { return prefSoundUseBuddySounds; }
    public static boolean getSoundUseInvitedSound() { return prefSoundUseInvitedSound; }
    public static boolean getSoundUseMarkSounds() { return prefSoundUseMarkSounds; }
    public static boolean getSoundUseMessageSound() { return prefSoundUseMessageSound; }
    public static boolean getSoundUsePresenceSounds() { return prefSoundUsePresenceSounds; }
    public static boolean getSoundUseThreadSound() { return prefSoundUseThreadSound; }
    public static boolean getSoundUseErrorSound() { return prefSoundUseErrorSound; }

    public static void setGameShowHelp(boolean val) {
        if (prefGameShowHelp == val)
            return;

        prefGameShowHelp = val;
        Preferences prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(GAME_OPTIONS);
        prefs.putBoolean(GAMESHOWHELP_KEY, prefGameShowHelp);
        noticeChange(GAME_OPTIONS, GAMESHOWHELP_KEY);

        if (solePrefsDialog != null) {
            solePrefsDialog.mGameShowHelp.setSelected(prefGameShowHelp);
        }
    }

    // The sole existing PrefsDialog.
    private static PrefsDialog solePrefsDialog = null;

    /*
     * Map of node (String) to listeners (List of ChangeListeners).
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
    private static void noticeChange(String node, Object source) {
        List listeners = (List)changeListeners.get(node);
        if (listeners != null) {
            ChangeEvent ev = new ChangeEvent(source);
            for (Iterator it = listeners.iterator(); it.hasNext(); ) {
                ChangeListener listener = (ChangeListener)it.next();
                listener.stateChanged(ev);
            }
        }
    }

    /**
     * Add a listener for changes in a particular group of preferences.
     *
     * The listener will be called in the Swing thread.
     */
    public static void addListener(String node, ChangeListener listener) {
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
    public static void removeListener(String node, ChangeListener listener) {
        List listeners = (List)changeListeners.get(node);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    private final static String LABEL_GAMESHOWHELP = "Display seating help";
    private final static String LABEL_ROSTERSHOWOFFLINE = "Display offline buddies";
    private final static String LABEL_ROSTERSHOWREVERSE = "Display people whose roster you are on";
    private final static String LABEL_ROSTERNOTIFYSUBSCRIPTIONS = "Ask when someone adds you to his roster";
    private final static String LABEL_SOUNDPLAYAUDIO = "Play audio effects";
    private final static String LABEL_SOUNDSHOWALTTAGS = "Print text equivalents for audio effects";
    private final static String LABEL_SOUNDALERTGROUP = "Play alert sounds for...";
    private final static String LABEL_SOUNDUSEBUDDYSOUNDS = "Buddies logging in and out";
    private final static String LABEL_SOUNDUSEINVITEDSOUND = "Game invitations";
    private final static String LABEL_SOUNDUSEMARKSOUNDS = "It's your turn";
    private final static String LABEL_SOUNDUSEMESSAGESOUND = "Message in a chat window";
    private final static String LABEL_SOUNDUSEPRESENCESOUNDS = "People arriving and leaving in a chat window";
    private final static String LABEL_SOUNDUSETHREADSOUND = "New chat window";
    private final static String LABEL_SOUNDUSEERRORSOUND = "Javolin errors";
    private final static String LABEL_DEBUGSHOWRPCS = "Print all RPCs";

    private JavolinApp mOwner;
    private SizeAndPositionSaver mSizePosSaver;

    private JTabbedPane mTabPane;
    private JCheckBox mGameShowHelp;
    private JCheckBox mRosterShowOffline;
    private JCheckBox mRosterShowReverse;
    private JCheckBox mRosterNotifySubscriptions;
    private JSlider mChatBodyShade;
    private JSlider mChatNameShade;
    private JTextPane mChatSampleText;
    private JCheckBox mSoundPlayAudio;
    private JCheckBox mSoundShowAltTags;
    private JCheckBox mSoundUseBuddySounds;
    private JCheckBox mSoundUseInvitedSound;
    private JCheckBox mSoundUseMarkSounds;
    private JCheckBox mSoundUseMessageSound;
    private JCheckBox mSoundUsePresenceSounds;
    private JCheckBox mSoundUseThreadSound;
    private JCheckBox mSoundUseErrorSound;
    private JCheckBox mDebugShowRPCs;

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

        mGameShowHelp.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefGameShowHelp = mGameShowHelp.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(GAME_OPTIONS);
                    prefs.putBoolean(GAMESHOWHELP_KEY, prefGameShowHelp);
                    noticeChange(GAME_OPTIONS, GAMESHOWHELP_KEY);
                }
            });

        mRosterShowOffline.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefRosterShowOffline = mRosterShowOffline.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(ROSTER_DISPLAY_OPTIONS);
                    prefs.putBoolean(ROSTERSHOWOFFLINE_KEY, prefRosterShowOffline);
                    noticeChange(ROSTER_DISPLAY_OPTIONS, ROSTERSHOWOFFLINE_KEY);
                }
            });

        mRosterShowReverse.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefRosterShowReverse = mRosterShowReverse.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(ROSTER_DISPLAY_OPTIONS);
                    prefs.putBoolean(ROSTERSHOWREVERSE_KEY, prefRosterShowReverse);
                    noticeChange(ROSTER_DISPLAY_OPTIONS, ROSTERSHOWREVERSE_KEY);
                }
            });

        mRosterNotifySubscriptions.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefRosterNotifySubscriptions = mRosterNotifySubscriptions.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(ROSTER_DISPLAY_OPTIONS);
                    prefs.putBoolean(ROSTERNOTIFYSUBSCRIPTIONS_KEY, prefRosterNotifySubscriptions);
                    noticeChange(ROSTER_DISPLAY_OPTIONS, ROSTERNOTIFYSUBSCRIPTIONS_KEY);
                }
            });

        mChatNameShade.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent ev) {
                    if (!mChatNameShade.getValueIsAdjusting()) {
                        prefChatNameShade = mChatNameShade.getValue();
                        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(CHAT_COLOR_OPTIONS);
                        prefs.putInt(CHATNAMESHADE_KEY, prefChatNameShade);
                        updateChatSampleText();
                        noticeChange(CHAT_COLOR_OPTIONS, CHATNAMESHADE_KEY);
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
                        noticeChange(CHAT_COLOR_OPTIONS, CHATBODYSHADE_KEY);
                    }
                }
            });

        mSoundPlayAudio.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundPlayAudio = mSoundPlayAudio.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDPLAYAUDIO_KEY, prefSoundPlayAudio);
                    noticeChange(SOUND_OPTIONS, SOUNDPLAYAUDIO_KEY);
                }
            });

        mSoundShowAltTags.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundShowAltTags = mSoundShowAltTags.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDSHOWALTTAGS_KEY, prefSoundShowAltTags);
                    noticeChange(SOUND_OPTIONS, SOUNDSHOWALTTAGS_KEY);
                }
            });

        mSoundUseBuddySounds.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundUseBuddySounds = mSoundUseBuddySounds.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDUSEBUDDYSOUNDS_KEY, prefSoundUseBuddySounds);
                    noticeChange(SOUND_OPTIONS, SOUNDUSEBUDDYSOUNDS_KEY);
                }
            });

        mSoundUseInvitedSound.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundUseInvitedSound = mSoundUseInvitedSound.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDUSEINVITEDSOUND_KEY, prefSoundUseInvitedSound);
                    noticeChange(SOUND_OPTIONS, SOUNDUSEINVITEDSOUND_KEY);
                }
            });

        mSoundUseMarkSounds.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundUseMarkSounds = mSoundUseMarkSounds.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDUSEMARKSOUNDS_KEY, prefSoundUseMarkSounds);
                    noticeChange(SOUND_OPTIONS, SOUNDUSEMARKSOUNDS_KEY);
                }
            });

        mSoundUseMessageSound.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundUseMessageSound = mSoundUseMessageSound.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDUSEMESSAGESOUND_KEY, prefSoundUseMessageSound);
                    noticeChange(SOUND_OPTIONS, SOUNDUSEMESSAGESOUND_KEY);
                }
            });

        mSoundUsePresenceSounds.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundUsePresenceSounds = mSoundUsePresenceSounds.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDUSEPRESENCESOUNDS_KEY, prefSoundUsePresenceSounds);
                    noticeChange(SOUND_OPTIONS, SOUNDUSEPRESENCESOUNDS_KEY);
                }
            });

        mSoundUseThreadSound.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundUseThreadSound = mSoundUseThreadSound.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDUSETHREADSOUND_KEY, prefSoundUseThreadSound);
                    noticeChange(SOUND_OPTIONS, SOUNDUSETHREADSOUND_KEY);
                }
            });

        mSoundUseErrorSound.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefSoundUseErrorSound = mSoundUseErrorSound.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(SOUND_OPTIONS);
                    prefs.putBoolean(SOUNDUSEERRORSOUND_KEY, prefSoundUseErrorSound);
                    noticeChange(SOUND_OPTIONS, SOUNDUSEERRORSOUND_KEY);
                }
            });

        mDebugShowRPCs.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefDebugShowRPCs = mDebugShowRPCs.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(DEBUG_OPTIONS);
                    prefs.putBoolean(DEBUGSHOWRPCS_KEY, prefDebugShowRPCs);
                    noticeChange(DEBUG_OPTIONS, DEBUGSHOWRPCS_KEY);
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
        Color roscol = new Color(0.0f, 0.681f, 0.0f);
        Color guilcol = new Color(1.0f, 0.0f, 0.0f);
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
            
            mGameShowHelp = new JCheckBox(LABEL_GAMESHOWHELP, prefGameShowHelp);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(mGameShowHelp, c);

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

            mTabPane.addTab("Game", pane);
        }

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

        {
            JPanel pane = new JPanel(new GridBagLayout());
            
            int row = 0;
            
            mSoundPlayAudio = new JCheckBox(LABEL_SOUNDPLAYAUDIO, prefSoundPlayAudio);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(mSoundPlayAudio, c);

            mSoundShowAltTags = new JCheckBox(LABEL_SOUNDSHOWALTTAGS, prefSoundShowAltTags);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
            pane.add(mSoundShowAltTags, c);

            label = new JLabel(LABEL_SOUNDALERTGROUP);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(label, c);

            mSoundUseMarkSounds = new JCheckBox(LABEL_SOUNDUSEMARKSOUNDS, prefSoundUseMarkSounds);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseMarkSounds, c);

            mSoundUseInvitedSound = new JCheckBox(LABEL_SOUNDUSEINVITEDSOUND, prefSoundUseInvitedSound);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseInvitedSound, c);

            mSoundUseThreadSound = new JCheckBox(LABEL_SOUNDUSETHREADSOUND, prefSoundUseThreadSound);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseThreadSound, c);

            mSoundUseMessageSound = new JCheckBox(LABEL_SOUNDUSEMESSAGESOUND, prefSoundUseMessageSound);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseMessageSound, c);

            mSoundUsePresenceSounds = new JCheckBox(LABEL_SOUNDUSEPRESENCESOUNDS, prefSoundUsePresenceSounds);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUsePresenceSounds, c);

            mSoundUseBuddySounds = new JCheckBox(LABEL_SOUNDUSEBUDDYSOUNDS, prefSoundUseBuddySounds);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseBuddySounds, c);

            mSoundUseErrorSound = new JCheckBox(LABEL_SOUNDUSEERRORSOUND, prefSoundUseErrorSound);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseErrorSound, c);

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

            mTabPane.addTab("Sound", pane);
        }

        {
            JPanel pane = new JPanel(new GridBagLayout());
            
            int row = 0;
            
            mDebugShowRPCs = new JCheckBox(LABEL_DEBUGSHOWRPCS, prefDebugShowRPCs);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(mDebugShowRPCs, c);

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

            mTabPane.addTab("Debug", pane);
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

