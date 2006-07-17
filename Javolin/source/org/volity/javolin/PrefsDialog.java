package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
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
    public final static String GAMESELECTUIALWAYS_KEY = "SelectUIAlways";
    public final static String GAMESELECTUISOLE_KEY = "SelectUISole";
    public final static String GAMESELECTUIFAMILIAR_KEY = "SelectUIFamiliar";
    public final static String GAMEFINDERSTARTUP_KEY = "GameFinderStartup";
    public final static int GAMEFINDERSTARTUP_ALWAYS = 1;
    public final static int GAMEFINDERSTARTUP_NEVER = 2;
    public final static int GAMEFINDERSTARTUP_REMEMBER = 3;

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
    private static boolean prefGameSelectUIAlways;
    private static boolean prefGameSelectUISole;
    private static boolean prefGameSelectUIFamiliar;
    private static int prefGameFinderStartup;
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
        prefGameSelectUIAlways = prefs.getBoolean(GAMESELECTUIALWAYS_KEY, true);
        prefGameSelectUISole = prefs.getBoolean(GAMESELECTUISOLE_KEY, false);
        prefGameSelectUIFamiliar = prefs.getBoolean(GAMESELECTUIFAMILIAR_KEY, true);
        int defaultstartup = (PlatformWrapper.isRunningOnMac()) ? GAMEFINDERSTARTUP_REMEMBER : GAMEFINDERSTARTUP_ALWAYS;
        prefGameFinderStartup = prefs.getInt(GAMEFINDERSTARTUP_KEY, defaultstartup);

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
         * being grabbed by another part of Gamut. So we set up the initial
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
                    if (ev.getSource() == DEBUGSHOWRPCS_KEY) {
                        RPCDispatcherDebug.setDebugOutput(prefDebugShowRPCs);
                        Referee.setDebugOutput(prefDebugShowRPCs);
                        AppMenuBar.notifyUpdateItems();
                    }
                }
            });
    }

    public static boolean getGameShowHelp() { return prefGameShowHelp; }
    public static boolean getGameSelectUIAlways() { return prefGameSelectUIAlways; }
    public static boolean getGameSelectUISole() { return prefGameSelectUISole; }
    public static boolean getGameSelectUIFamiliar() { return prefGameSelectUIFamiliar; }
    public static int getGameFinderStartup() { return prefGameFinderStartup; }
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
    public static boolean getDebugShowRPCs() { return prefDebugShowRPCs; }

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

    public static void setDebugShowRPCs(boolean val) {
        if (prefDebugShowRPCs == val)
            return;

        prefDebugShowRPCs = val;
        Preferences prefs = Preferences.userNodeForPackage(PrefsDialog.class).node(DEBUG_OPTIONS);
        prefs.putBoolean(DEBUGSHOWRPCS_KEY, prefDebugShowRPCs);
        noticeChange(DEBUG_OPTIONS, DEBUGSHOWRPCS_KEY);

        if (solePrefsDialog != null) {
            solePrefsDialog.mDebugShowRPCs.setSelected(prefDebugShowRPCs);
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

    private JavolinApp mOwner;
    private SizeAndPositionSaver mSizePosSaver;

    private JTabbedPane mTabPane;
    private JCheckBox mGameShowHelp;
    private JCheckBox mGameSelectUIAlways;
    private JCheckBox mGameSelectUISole;
    private JCheckBox mGameSelectUIFamiliar;
    private JRadioButton mGameFinderStartupAlways;
    private JRadioButton mGameFinderStartupNever;
    private JRadioButton mGameFinderStartupRemember;
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

        setTitle(JavolinApp.getAppName() + " " + localize("WindowTitle"));
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

        mGameSelectUIAlways.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefGameSelectUIAlways = mGameSelectUIAlways.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(GAME_OPTIONS);
                    prefs.putBoolean(GAMESELECTUIALWAYS_KEY, prefGameSelectUIAlways);
                    noticeChange(GAME_OPTIONS, GAMESELECTUIALWAYS_KEY);
                    mGameSelectUISole.setEnabled(!prefGameSelectUIAlways);
                    mGameSelectUIFamiliar.setEnabled(!prefGameSelectUIAlways);
                }
            });

        mGameSelectUISole.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefGameSelectUISole = mGameSelectUISole.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(GAME_OPTIONS);
                    prefs.putBoolean(GAMESELECTUISOLE_KEY, prefGameSelectUISole);
                    noticeChange(GAME_OPTIONS, GAMESELECTUISOLE_KEY);
                }
            });

        mGameSelectUIFamiliar.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    prefGameSelectUIFamiliar = mGameSelectUIFamiliar.isSelected();
                    Preferences prefs = Preferences.userNodeForPackage(getClass()).node(GAME_OPTIONS);
                    prefs.putBoolean(GAMESELECTUIFAMILIAR_KEY, prefGameSelectUIFamiliar);
                    noticeChange(GAME_OPTIONS, GAMESELECTUIFAMILIAR_KEY);
                }
            });

        mGameFinderStartupAlways.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    if (mGameFinderStartupAlways.isSelected()) {
                        prefGameFinderStartup = GAMEFINDERSTARTUP_ALWAYS;
                        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(GAME_OPTIONS);
                        prefs.putInt(GAMEFINDERSTARTUP_KEY, prefGameFinderStartup);
                        noticeChange(GAME_OPTIONS, GAMEFINDERSTARTUP_KEY);
                    }
                }
            });

        mGameFinderStartupNever.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    if (mGameFinderStartupNever.isSelected()) {
                        prefGameFinderStartup = GAMEFINDERSTARTUP_NEVER;
                        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(GAME_OPTIONS);
                        prefs.putInt(GAMEFINDERSTARTUP_KEY, prefGameFinderStartup);
                        noticeChange(GAME_OPTIONS, GAMEFINDERSTARTUP_KEY);
                    }
                }
            });

        mGameFinderStartupRemember.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    if (mGameFinderStartupRemember.isSelected()) {
                        prefGameFinderStartup = GAMEFINDERSTARTUP_REMEMBER;
                        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(GAME_OPTIONS);
                        prefs.putInt(GAMEFINDERSTARTUP_KEY, prefGameFinderStartup);
                        noticeChange(GAME_OPTIONS, GAMEFINDERSTARTUP_KEY);
                    }
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

        setVisible(true);
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
     * Localization helper.
     */
    protected String localize(String key) {
        try {
            return JavolinApp.resources.getString(NODENAME+"_"+key);
        }
        catch (MissingResourceException ex) {
            return "???"+NODENAME+"_"+key;
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
            doc.insertString(doc.getLength(), "*** "+localize("ChatSampleText")+"\n", style);

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
            
            label = new JLabel(localize("GameFinderStartup"));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(label, c);

            mGameFinderStartupAlways = new JRadioButton(
                localize("GameFinderStartupAlways"),
                (prefGameFinderStartup==GAMEFINDERSTARTUP_ALWAYS));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mGameFinderStartupAlways, c);

            mGameFinderStartupNever = new JRadioButton(
                localize("GameFinderStartupNever"),
                (prefGameFinderStartup==GAMEFINDERSTARTUP_NEVER));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mGameFinderStartupNever, c);

            mGameFinderStartupRemember = new JRadioButton(
                localize("GameFinderStartupRemember"),
                (prefGameFinderStartup==GAMEFINDERSTARTUP_REMEMBER));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mGameFinderStartupRemember, c);

            ButtonGroup GameFinderStartupGroup = new ButtonGroup();
            GameFinderStartupGroup.add(mGameFinderStartupAlways);
            GameFinderStartupGroup.add(mGameFinderStartupNever);
            GameFinderStartupGroup.add(mGameFinderStartupRemember);

            label = new JLabel(localize("GameSelectUI"));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(label, c);

            mGameSelectUIAlways = new JCheckBox(localize("GameSelectUIAlways"),
                prefGameSelectUIAlways);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mGameSelectUIAlways, c);

            mGameSelectUISole = new JCheckBox(localize("GameSelectUISole"),
                prefGameSelectUISole);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            mGameSelectUISole.setEnabled(!prefGameSelectUIAlways);
            pane.add(mGameSelectUISole, c);

            mGameSelectUIFamiliar = new JCheckBox(localize("GameSelectUIFamiliar"), 
                prefGameSelectUIFamiliar);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            mGameSelectUIFamiliar.setEnabled(!prefGameSelectUIAlways);
            pane.add(mGameSelectUIFamiliar, c);

            mGameShowHelp = new JCheckBox(localize("GameShowHelp"),
                prefGameShowHelp);
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

            mTabPane.addTab(localize("TabGame"), pane);
        }

        {
            JPanel pane = new JPanel(new GridBagLayout());
            
            int row = 0;
            
            mRosterShowOffline = new JCheckBox(
                localize("RosterShowOffline"),
                prefRosterShowOffline);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(mRosterShowOffline, c);

            mRosterShowReverse = new JCheckBox(
                localize("RosterShowReverse"),
                prefRosterShowReverse);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
            pane.add(mRosterShowReverse, c);

            mRosterNotifySubscriptions = new JCheckBox(
                localize("RosterNotifySubscriptions"),
                prefRosterNotifySubscriptions);
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

            mTabPane.addTab(localize("TabRoster"), pane);
        }

        {
            JPanel pane = new JPanel(new GridBagLayout());
            
            int row = 0;

            label = new JLabel(localize("ChatSenderColor"));
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

            label = new JLabel(localize("ChatMessageColor"));
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

            mTabPane.addTab(localize("TabChat"), pane);
        }

        {
            JPanel pane = new JPanel(new GridBagLayout());
            
            int row = 0;
            
            mSoundPlayAudio = new JCheckBox(
                localize("SoundPlayAudio"),
                prefSoundPlayAudio);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(mSoundPlayAudio, c);

            mSoundShowAltTags = new JCheckBox(
                localize("SoundShowAltTags"),
                prefSoundShowAltTags);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, MARGIN, 0, MARGIN);
            pane.add(mSoundShowAltTags, c);

            label = new JLabel(localize("SoundAlertGroup"));
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
            pane.add(label, c);

            mSoundUseMarkSounds = new JCheckBox(
                localize("SoundUseMarkSounds"),
                prefSoundUseMarkSounds);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseMarkSounds, c);

            mSoundUseInvitedSound = new JCheckBox(
                localize("SoundUseInvitedSound"),
                prefSoundUseInvitedSound);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseInvitedSound, c);

            mSoundUseThreadSound = new JCheckBox(
                localize("SoundUseThreadSound"),
                prefSoundUseThreadSound);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseThreadSound, c);

            mSoundUseMessageSound = new JCheckBox(
                localize("SoundUseMessageSound"),
                prefSoundUseMessageSound);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseMessageSound, c);

            mSoundUsePresenceSounds = new JCheckBox(
                localize("SoundUsePresenceSounds"),
                prefSoundUsePresenceSounds);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUsePresenceSounds, c);

            mSoundUseBuddySounds = new JCheckBox(
                localize("SoundUseBuddySounds"),
                prefSoundUseBuddySounds);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.weightx = 1;
            c.weighty = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(GAP, 2*MARGIN, 0, MARGIN);
            pane.add(mSoundUseBuddySounds, c);

            mSoundUseErrorSound = new JCheckBox(
                localize("SoundUseErrorSound"),
                prefSoundUseErrorSound);
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

            mTabPane.addTab(localize("TabSound"), pane);
        }

        {
            JPanel pane = new JPanel(new GridBagLayout());
            
            int row = 0;
            
            mDebugShowRPCs = new JCheckBox(
                localize("DebugShowRPCs"), prefDebugShowRPCs);
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

            mTabPane.addTab(localize("TabDebug"), pane);
        }


        cPane.add(mTabPane);

        // Necessary for all windows, for Mac support
        AppMenuBar.applyPlatformMenuBar(this);
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

