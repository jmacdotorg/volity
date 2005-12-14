package org.volity.client;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import javax.sound.sampled.*;

//### http://www.javazoom.net/mp3spi/mp3spi.html

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

    // Import this constant for our convenience
    public static final int LOOP_CONTINUOUSLY = Clip.LOOP_CONTINUOUSLY;

    protected Object mOwner;
    protected URL mURL;
    protected String mAltTag;
    protected int mLoop = 1;

    public Audio(Object owner, URL url) 
        throws UnsupportedAudioFileException, IOException, 
               LineUnavailableException {
        this(owner, url, "");
    }

    public Audio(Object owner, URL url, String alt) 
        throws UnsupportedAudioFileException, IOException,
               LineUnavailableException {
        setupAudio();

        mOwner = owner;
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
     * An audio instance object. One of these is created every time a sound
     * begins playing.
     */
    public class Instance {
        protected URL mURL;
        protected String mAltTag;
        protected int mLoop;

        protected Clip mClip;

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
            AudioInputStream stream = AudioSystem.getAudioInputStream(mURL);

            Line.Info lineinfo = new DataLine.Info(Clip.class, stream.getFormat());
            mClip = (Clip)sMixer.getLine(lineinfo);

            /* As soon as the Instance finishes, or is stopped, it is closed
             * and discarded. */
            mClip.addLineListener(new LineListener() {
                    public void update(LineEvent ev) {
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
                        }
                    }
                });
            mClip.open(stream);

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
