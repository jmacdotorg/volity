package org.volity.client;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import javax.sound.sampled.*;

//### http://www.javazoom.net/vorbisspi/vorbisspi.html

/**
 * A handler for Volity client audio.
 *
 * This class approximately provides the audio object API defined in the Volity
 * client ECMAScript spec. (Audio instance objects are represented by the
 * Audio.Instance class.)
 *
 * (Actually making this API available to ECMAScript requires ScriptableObject
 * wrappers. See GameUI.)
 */
public class Audio
{
    protected static boolean sInitialized = false;
    protected static Mixer sMixer = null;

    protected static boolean sPlayAudio = true;
    protected static boolean sShowAltTags = false;

    /**
     * It is necessary to track all open Clips, so that we can shut them down
     * en masse when a game window closes. The sLiveClips table maps each owner
     * object (the GameUI or TestUI) to a Set of Clips.
     */
    protected static Map sLiveClips = new HashMap();

    /**
     * General initialization, run when the first Audio is instantiated.
     */
    protected static void setupAudio() {
        if (sInitialized)
            return;

        sInitialized = true;
        sMixer = AudioSystem.getMixer(null);
    }

    /**
     * Set a global "play audio" preference.
     */
    public static void setPlayAudio(boolean val) {
        if (val && !sPlayAudio) {
            // unmute
            sPlayAudio = true;
            setAllSoundsMute(!sPlayAudio);
        }

        if (!val && sPlayAudio) {
            // mute
            sPlayAudio = false;
            setAllSoundsMute(!sPlayAudio);
        }
    }

    /**
     * Set a global "show alt tags" preference.
     */
    public static void setShowAltTags(boolean val) {
        sShowAltTags = val;
    }

    // Import this constant for our convenience
    public static final int LOOP_CONTINUOUSLY = Clip.LOOP_CONTINUOUSLY;

    protected Object mOwner;
    protected GameUI.MessageHandler mMessageHandler;
    protected URL mURL;
    protected String mAltTag;
    protected int mLoop = 1;

    public Audio(Object owner, URL url,
        GameUI.MessageHandler messageHandler) 
        throws UnsupportedAudioFileException, IOException, 
               LineUnavailableException {
        this(owner, url, "", messageHandler);
    }

    public Audio(Object owner, URL url, String alt,
        GameUI.MessageHandler messageHandler) 
        throws UnsupportedAudioFileException, IOException,
               LineUnavailableException {
        setupAudio();

        mOwner = owner;
        mMessageHandler = messageHandler;
        mURL = url;
        setAlt(alt);

        /* Make sure it's readable. If it's not, this will throw an
         * exception. */
        AudioFileFormat format = AudioSystem.getAudioFileFormat(mURL);
    }

    /**
     * Get the URL of this audio resource.
     */
    public URL getURL() {
        return mURL;
    }

    /**
     * Get the alternate text string for this audio resource.
     */
    public String getAlt() {
        return mAltTag;
    }

    /**
     * Set the alternate text string for this audio resource. Passing null is
     * equivalent to the empty string.
     */
    public void setAlt(String alt) {
        if (alt == null)
            alt = "";
        mAltTag = alt;
    }

    /**
     * Get the looping factor for this audio resource. LOOP_CONTINUOUSLY means
     * loop forever. Otherwise, this will be a positive number indicating the
     * number of repeats.
     */
    public int getLoop() {
        return mLoop;
    }

    /**
     * Set the looping factor for this audio resource. LOOP_CONTINUOUSLY means
     * loop forever. Otherwise, this must be a positive number indicating the
     * number of repeats.
     */
    public void setLoop(int val) {
        if (val < 1 && val != LOOP_CONTINUOUSLY)
            throw new IllegalArgumentException("loop value must be positive or -1");
        mLoop = val;
    }

    /**
     * Create an audio instance and start it playing.
     */
    public Instance play()
        throws UnsupportedAudioFileException, IOException, LineUnavailableException  {
        //### it might be nice to cache a Clip (not an Instance) for future
        //### reuse.
        Instance instance = new Instance();
        instance.start();
        return instance;
    }

    /**
     * Stop all open Clips associated with a given owner.
     */
    public static void stopGroup(Object owner) {
        List ls = new ArrayList();

        synchronized (sLiveClips) {
            Set set = (Set)sLiveClips.get(owner);
            if (set == null) 
                return;
            for (Iterator iter = set.iterator(); iter.hasNext(); ) {
                Clip clip = (Clip)iter.next();
                ls.add(clip);
            }
        }

        for (int ix=0; ix<ls.size(); ix++) {
            Clip clip = (Clip)ls.get(ix);
            clip.stop();
        }
    }

    /**
     * Mute or unmute every open Clip.
     */
    protected static void setAllSoundsMute(boolean val) {
        synchronized (sLiveClips) {
            Iterator siter = sLiveClips.values().iterator();
            while (siter.hasNext()) {
                Set set = (Set)siter.next();
                for (Iterator iter = set.iterator(); iter.hasNext(); ) {
                    Clip clip = (Clip)iter.next();
                    BooleanControl mute = (BooleanControl)clip.getControl(BooleanControl.Type.MUTE);
                    mute.setValue(val);
                }
            }
        }        
    }

    /**
     * Decide whether we should decode a particular audio format. If it's good
     * to play as is, return null. If it needs to be decoded, return the format
     * to decode to.
     */
    protected static AudioFormat decodingFormat(AudioFormat informat) {
        // Probably I should be doing an "isSupported" query of some sort?
        // As opposed to matching against hardwired encodings.

        if (informat.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
            || informat.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED) {
            return null;
        }

        int samplesize = informat.getSampleSizeInBits();
        if (samplesize <= 0)
            samplesize = 16;

        AudioFormat res = new AudioFormat(informat.getSampleRate(),
            samplesize, informat.getChannels(), true, informat.isBigEndian());
        return res;
    }

    /**
     * An audio instance object. One of these is created every time a sound
     * begins playing.
     */
    public class Instance {
        protected URL mURL;
        protected String mAltTag;
        protected int mLoop;

        protected Clip mClip;
        protected AudioInputStream mStream;
        protected AudioInputStream mOrigStream;

        protected Instance() {
            // Make local copies of these values, since the Audio fields can
            // change.
            mURL = Audio.this.mURL;
            mLoop = Audio.this.mLoop;
            mAltTag = Audio.this.mAltTag;
        }

        public Audio getAudio() {
            return Audio.this;
        }

        public URL getURL() {
            return mURL;
        }

        public String getAlt() {
            return mAltTag;
        }

        public int getLoop() {
            return mLoop;
        }

        /**
         * Start the instance playing. This is always (and only) called right
         * after construction, by the Audio object's play() method.
         */
        protected void start()
            throws UnsupportedAudioFileException, IOException, LineUnavailableException  {
            if (sShowAltTags) {
                String val = mAltTag;
                if (val.equals(""))
                    val = "sound";
                if (mLoop == LOOP_CONTINUOUSLY)
                    val = val + ", repeated forever";
                else if (mLoop > 1)
                    val = val + ", repeated " + String.valueOf(mLoop) + " times";
                //### not localized!
                mMessageHandler.print("[sound: " + val + "]");
            }

            mStream = AudioSystem.getAudioInputStream(mURL);
            mOrigStream = null;

            AudioFormat origformat = mStream.getFormat();
            AudioFormat finalformat = decodingFormat(origformat);
            if (finalformat == null) {
                finalformat = origformat;
            }
            else {
                mOrigStream = mStream;
                mStream = null;
                mStream = AudioSystem.getAudioInputStream(finalformat,
                    mOrigStream);
            }

            Line.Info lineinfo = new DataLine.Info(Clip.class, finalformat);
            mClip = (Clip)sMixer.getLine(lineinfo);

            /* As soon as the Instance finishes, or is stopped, it is closed
             * and discarded. */
            mClip.addLineListener(new LineListener() {
                    public void update(LineEvent ev) {
                        // Called outside Swing thread!

                        if (ev.getType() == LineEvent.Type.STOP) {
                            // Remove from the table of live Clips.
                            synchronized (sLiveClips) {
                                Set set = (Set)sLiveClips.get(mOwner);
                                if (set != null)
                                    set.remove(mClip);
                            }
                            // Always close when the Clip stops.
                            mClip.close();
                            mClip = null;
                            try {
                                if (mStream != null) {
                                    mStream.close();
                                    mStream = null;
                                }
                                if (mOrigStream != null) {
                                    mOrigStream.close();
                                    mOrigStream = null;
                                }
                            }
                            catch (IOException ex) { }
                        }
                    }
                });

            mClip.open(mStream);

            // If sound is off, we'll start it playing, but muted.
            if (!sPlayAudio) {
                BooleanControl mute = (BooleanControl)mClip.getControl(BooleanControl.Type.MUTE);
                mute.setValue(true);
            }

            // Add this to the table of live Clips.
            synchronized (sLiveClips) {
                Set set = (Set)sLiveClips.get(mOwner);
                if (set == null) {
                    set = new HashSet();
                    sLiveClips.put(mOwner, set);
                }
                set.add(mClip);
            }

            mClip.setFramePosition(0);
            if (mLoop == 1) {
                mClip.start();
            }
            else if (mLoop == LOOP_CONTINUOUSLY) {
                mClip.setLoopPoints(0, -1);
                mClip.loop(LOOP_CONTINUOUSLY);
            }
            else {
                mClip.setLoopPoints(0, -1);
                mClip.loop(mLoop-1);
            }
        }

        /**
         * Stop the clip. If the clip has stopped already (either on its own,
         * or because of a previous stop() call) then this safely does nothing.
         */
        public void stop() {
            if (mClip != null)
                mClip.stop();
        }
    }
}
