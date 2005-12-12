/*
 * UserColorMap.java
 *
 * Copyright 2004 Karl von Laudermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.volity.javolin.chat;

import java.awt.*;
import java.util.*;
import org.volity.javolin.PrefsDialog;

/**
 * Maps chat users to colors for message text. This allows each user to have a
 * different color within a chat window. Two colors are associated with each
 * user: one for the user name tag, and one for the message text itself.
 *
 * A user can also have two *names* associated with him. (A real JID and a MUC
 * nickname.) The map tries to keep these in sync. It can do this better if you
 * call changeUserName() when you detect a nickname change.
 */
public class UserColorMap
{
    /* A maximum value (in the "HSV" sense) for text colors. Values higher than
     * this are hard to read on a light background.
     */
    private static final float VALUE_LIMIT = 0.4f;

    private Map mHueMap;
    private Runnable mChangeListener;
    
    // These values are used for calculating the next hue
    private int mHueNumerator = 0;
    private int mHueDenominator = 3;
    private int mHueNumerDelta = 1;

    /**
     * Constructor.
     */
    public UserColorMap()
    {
        mHueMap = new Hashtable();
        mChangeListener = new Runnable() {
                public void run() {
                    clearCachedColors();
                }
            };
        PrefsDialog.addListener(PrefsDialog.CHAT_COLOR_OPTIONS,
            mChangeListener);
    }

    /**
     * Finalizer. Call this when you no longer need the UserColorMap.
     */
    public void dispose() 
    {
        PrefsDialog.removeListener(PrefsDialog.CHAT_COLOR_OPTIONS,
            mChangeListener);
    }

    /**
     * Internal class: An entry in the hue map. This stores a basic color, and
     * the darker color used for the body of text messages.
     */
    private class ColorEntry 
    {
        Color mBaseColor;
        Color mTitleColor;
        Color mBodyColor;

        /**
         * Create an entry containing a (saturated) color.
         * @param hue a hue value on the color wheel, from 0.0 to 1.0.
         */
        public ColorEntry(float hue)
        {
            Color col = new Color(Color.HSBtoRGB(hue, 1.0f, 1.0f));

            // Measure the value (in HSV space) using a hoary old formula.
            float arr[] = col.getColorComponents(null);
            float value = arr[0] * 0.299f + arr[1] * 0.587f + arr[2] * 0.114f;

            // If the color's value is too high, scale it down.
            if (value >= VALUE_LIMIT) 
            {
                float bright = VALUE_LIMIT / value;
                col = new Color(arr[0] * bright, arr[1] * bright, arr[2] * bright);
            }

            mBaseColor = col;
            mTitleColor = null;
            mBodyColor = null;
        }

        /**
         * Create an entry containing a given color. This can be used to
         * created fixed shades of gray.
         */
        public ColorEntry(Color col)
        {
            mBaseColor = col;
            mTitleColor = null;
            mBodyColor = null;
        }
    }

    /**
     * Gets the next hue value to assign to a new user.
     *
     * @return   The next hue value to assign to a new user.
     */
    private float getNextHue()
    {
        float retVal = (float)(1.0 * mHueNumerator / mHueDenominator);
        
        if (mHueNumerator == (mHueDenominator - 1))
        {
            mHueDenominator *= 2;
            mHueNumerator = 1;
            mHueNumerDelta = 2;
        }
        else
        {
            mHueNumerator += mHueNumerDelta;
        }

        return retVal;
    }

    /**
     * Gets color entry associated with the given user from the map. If there
     * is no map entry for the user, a new one is created.
     *
     * @param user  The user to retrieve the colors for.
     * @return      The colors associated with the user.
     */
    private ColorEntry getUserEntry(String user)
    {
        ColorEntry ent = (ColorEntry)mHueMap.get(user);
        if (ent == null) 
        {
            ent = new ColorEntry(getNextHue());
            mHueMap.put(user, ent);
        }
        return ent;
    }

    /**
     * Gets color entry associated with the given user from the map. If there
     * is no map entry for the user, a new one is created.
     *
     * This form of getUserEntry accepts two name strings (i.e., a real JID and
     * a nickname). It tries to associate the same color with both.
     *
     * @param user  The user to retrieve the colors for.
     * @param nick  An alternate name for the user.
     * @return      The colors associated with the user.
     */
    private ColorEntry getUserEntry(String user, String nick)
    {
        ColorEntry ent1 = (ColorEntry)mHueMap.get(user);
        ColorEntry ent2 = (ColorEntry)mHueMap.get(nick);

        if (ent1 == null && ent2 == null)
        {
            ColorEntry ent = new ColorEntry(getNextHue());
            mHueMap.put(user, ent);
            mHueMap.put(nick, ent);
            return ent;
        }

        if (ent1 != null && ent2 == null)
        {
            mHueMap.put(nick, ent1);
            return ent1;
        }

        if (ent2 != null && ent1 == null)
        {
            mHueMap.put(nick, ent2);
            return ent2;
        }

        return ent2;
    }

    /** 
     * Set up a color entry for a user, *if* there is not one set already. (If
     * there is, it remains set.)
     *
     * The given color will become the user's NameColor; the TextColor will be
     * a darker version of it.
     *
     * @param user  The user to set the colors for.
     * @param nick  An alternate name for the user.
     */
    public void setUserColor(String user, String nick, Color col)
    {
        ColorEntry ent1 = (ColorEntry)mHueMap.get(user);
        ColorEntry ent2 = (ColorEntry)mHueMap.get(nick);

        if (ent1 == null && ent2 == null)
        {
            ColorEntry ent = new ColorEntry(col);
            mHueMap.put(user, ent);
            mHueMap.put(nick, ent);
            return;
        }

        if (ent1 != null && ent2 == null)
        {
            mHueMap.put(nick, ent1);
            return;
        }

        if (ent2 != null && ent1 == null)
        {
            mHueMap.put(nick, ent2);
            return;
        }
    }

    /** 
     * Set up a color entry for a user. This overwrites any existing entry; you
     * should use setUserColor if you don't have a reason to be invasive.
     *
     * The given color will become the user's NameColor; the TextColor will be
     * a darker version of it.
     *
     * @param user  The user to set the colors for.
     * @param nick  An alternate name for the user.
     */
    public void changeUserColor(String user, String nick, Color col)
    {
        ColorEntry ent = new ColorEntry(col);
        mHueMap.put(user, ent);
        mHueMap.put(nick, ent);
    }

    private void clearCachedColors() {
        for (Iterator it = mHueMap.values().iterator(); it.hasNext(); ) {
            ColorEntry ent = (ColorEntry)it.next();
            ent.mTitleColor = null;
            ent.mBodyColor = null;
        }
    }

    /**
     * Gets name tag color associated with the given user. If there is no color
     * associated with this user, the next available color will be assigned to it.
     *
     * @param user  The user to retrieve the name tag color for.
     * @return      The name tag color associated with the user.
     */
    public Color getUserNameColor(String user)
    {
        ColorEntry ent = getUserEntry(user);
        if (ent.mTitleColor == null) {
            ent.mTitleColor = PrefsDialog.transformColor(ent.mBaseColor,
                PrefsDialog.getChatNameShade());
        }
        return ent.mTitleColor;
    }

    /**
     * Gets message text color associated with the given user. If there is no color
     * associated with this user, the next available color will be assigned to it.
     *
     * @param user  The user to retrieve the message text color for.
     * @return      The message text color associated with the user.
     */
    public Color getUserTextColor(String user)
    {
        ColorEntry ent = getUserEntry(user);
        if (ent.mBodyColor == null) {
            ent.mBodyColor = PrefsDialog.transformColor(ent.mBaseColor,
                PrefsDialog.getChatBodyShade());
        }
        return ent.mBodyColor;
    }

    /**
     * Gets name tag color associated with the given user. If there is no color
     * associated with this user, the next available color will be assigned to it.
     *
     * @param user  The user to retrieve the name tag color for.
     * @param nick  An alternate name for the user.
     * @return      The name tag color associated with the user.
     */
    public Color getUserNameColor(String user, String nick)
    {
        ColorEntry ent = getUserEntry(user, nick);
        if (ent.mTitleColor == null) {
            ent.mTitleColor = PrefsDialog.transformColor(ent.mBaseColor,
                PrefsDialog.getChatNameShade());
        }
        return ent.mTitleColor;
    }

    /**
     * Gets message text color associated with the given user. If there is no color
     * associated with this user, the next available color will be assigned to it.
     *
     * @param user  The user to retrieve the message text color for.
     * @param nick  An alternate name for the user.
     * @return      The message text color associated with the user.
     */
    public Color getUserTextColor(String user, String nick)
    {
        ColorEntry ent = getUserEntry(user, nick);
        if (ent.mBodyColor == null) {
            ent.mBodyColor = PrefsDialog.transformColor(ent.mBaseColor,
                PrefsDialog.getChatBodyShade());
        }
        return ent.mBodyColor;
    }

    /* Note that a user name (say, a MUC nickname) has changed. Reassign the
     * color entry from the old name to the new one. */
    public void changeUserName(String oldname, String newname)
    {
        ColorEntry ent = (ColorEntry)mHueMap.get(oldname);
        if (ent != null) 
        {
            mHueMap.remove(oldname);
            mHueMap.put(newname, ent);
        }
    }

}
