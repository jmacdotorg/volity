package org.volity.javolin;

import java.net.URL;
import javax.sound.sampled.*;
import org.volity.client.GameTable;

/**
 * This class contains static methods for playing alert sounds. We load in one
 * Clip for each sound, as needed. (A sound which is disabled or never required
 * will never be loaded; but a sound which is played multiple times gets just
 * one Clip.)
 */
public class Audio
{
    private Audio() {
        // Not instantiable
    }

    private static Object clipLock = new Object();

    private static Clip clipBuddyIn = null;
    private static Clip clipBuddyOut = null;
    private static Clip clipError = null;
    private static Clip clipInvited = null;
    private static Clip clipMarkFirst = null;
    private static Clip clipMarkTurn = null;
    private static Clip clipMarkOther = null;
    private static Clip clipMarkWin = null;
    private static Clip clipMessage = null;
    private static Clip clipPresenceIn = null;
    private static Clip clipPresenceOut = null;
    private static Clip clipThread = null;

    /**
     * Load a clip (from the jar file).
     */
    private static Clip getClip(String filename) {
        return getClip(filename, false);
    }

    /**
     * Load a clip (from the jar file).
     *
     * If suppressError is true, any exceptions are silently discarded. (This
     * option exists so that the ErrorWrapper class can call playError()
     * without risk of an infinite exception cascade.)
     */
    private static Clip getClip(String filename, boolean suppressError) {
        try {
            Clip clip = null;

            Mixer mixer = AudioSystem.getMixer(null);
            URL resource = Audio.class.getResource(filename);
            if (resource == null)
                return null;
            AudioInputStream stream = AudioSystem.getAudioInputStream(resource);
            AudioFormat format = stream.getFormat();
            Line.Info lineinfo = new DataLine.Info(Clip.class, format);
            clip = (Clip)mixer.getLine(lineinfo);
            clip.open(stream);
            return clip;
        }
        catch (Exception ex) {
            if (suppressError) {
                /* We do *not* make an ErrorWrapper here, because ErrorWrapper
                 * can call playError(). Recipe for infinitely-recursive
                 * hilarity.
                 */
            }
            else {
                // Safe to make an ErrorWrapper
                new ErrorWrapper(ex);
            }
            return null;
        }
    }

    /**
     * Start a clip playing. If it's already playing, stop and rewind it
     * first.
     */
    private static void playClip(Clip clip) {
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    /**
     * Play the alert for a roster buddy becoming available.
     */
    public static void playBuddyIn() {
        if (!PrefsDialog.getSoundUseBuddySounds()) 
            return;

        synchronized (clipLock) {
            if (clipBuddyIn == null) {
                clipBuddyIn = getClip("buddy-in-alert.wav");
                if (clipBuddyIn == null) 
                    return;
            }
            playClip(clipBuddyIn);
        }
    }

    /**
     * Play the alert for a roster buddy becoming unavailable.
     */
    public static void playBuddyOut() {
        if (!PrefsDialog.getSoundUseBuddySounds()) 
            return;

        synchronized (clipLock) {
            if (clipBuddyOut == null) {
                clipBuddyOut = getClip("buddy-out-alert.wav");
                if (clipBuddyOut == null) 
                    return;
            }
            playClip(clipBuddyOut);
        }
    }

    /**
     * Play the alert for an exception in Javolin (or the UI).
     */
    public static void playError() {
        if (!PrefsDialog.getSoundUseErrorSound()) 
            return;

        synchronized (clipLock) {
            if (clipError == null) {
                clipError = getClip("error-alert.wav", true);
                if (clipError == null) 
                    return;
            }
            playClip(clipError);
        }
    }

    /**
     * Play the alert for a game invitation arriving. Also used for a roster
     * subscription request arriving.
     */
    public static void playInvited() {
        if (!PrefsDialog.getSoundUseInvitedSound()) 
            return;

        synchronized (clipLock) {
            if (clipInvited == null) {
                clipInvited = getClip("invited-alert.wav");
                if (clipInvited == null) 
                    return;
            }
            playClip(clipInvited);
        }
    }

    /**
     * Play the alert for a new message in an existing chat, MUC, or table
     * window.
     */
    public static void playMessage() {
        if (!PrefsDialog.getSoundUseMessageSound()) 
            return;

        synchronized (clipLock) {
            if (clipMessage == null) {
                clipMessage = getClip("message-alert.wav");
                if (clipMessage == null) 
                    return;
            }
            playClip(clipMessage);
        }
    }

    /**
     * Play the alert for someone leaving a table or MUC.
     */
    public static void playPresenceIn() {
        if (!PrefsDialog.getSoundUsePresenceSounds()) 
            return;

        synchronized (clipLock) {
            if (clipPresenceIn == null) {
                clipPresenceIn = getClip("presence-in-alert.wav");
                if (clipPresenceIn == null) 
                    return;
            }
            playClip(clipPresenceIn);
        }
    }

    /**
     * Play the alert for someone joining a table or MUC.
     */
    public static void playPresenceOut() {
        if (!PrefsDialog.getSoundUsePresenceSounds()) 
            return;

        synchronized (clipLock) {
            if (clipPresenceOut == null) {
                clipPresenceOut = getClip("presence-out-alert.wav");
                if (clipPresenceOut == null) 
                    return;
            }
            playClip(clipPresenceOut);
        }
    }

    /**
     * Play the alert for a new chat window opening.
     */
    public static void playThread() {
        if (!PrefsDialog.getSoundUseThreadSound()) 
            return;

        synchronized (clipLock) {
            if (clipThread == null) {
                clipThread = getClip("thread-alert.wav");
                if (clipThread == null) 
                    return;
            }
            playClip(clipThread);
        }
    }

    /**
     * Play the alert for the given seat mark. (One of the GameTable.MARK_*
     * constants.) If the mark is null, or matches no MARK constant, then this
     * does nothing.
     */
    public static void playMark(String mark) {
        if (!PrefsDialog.getSoundUseMarkSounds())
            return;

        synchronized (clipLock) {
            Clip clip = null;

            if (mark == GameTable.MARK_TURN) {
                if (clipMarkTurn == null) 
                    clipMarkTurn = getClip("mark-turn-alert.wav");
                clip = clipMarkTurn;
            }
            else if (mark == GameTable.MARK_WIN) {
                if (clipMarkWin == null)
                    clipMarkWin = getClip("mark-win-alert.wav");
                clip = clipMarkWin;
            }
            else if (mark == GameTable.MARK_FIRST) {
                if (clipMarkFirst == null)
                    clipMarkFirst = getClip("mark-first-alert.wav");
                clip = clipMarkFirst;
            }
            else if (mark == GameTable.MARK_OTHER) {
                if (clipMarkOther == null)
                    clipMarkOther = getClip("mark-other-alert.wav");
                clip = clipMarkOther;
            }

            if (clip == null) {
                return;
            }

            playClip(clip);
        }
    }

}
