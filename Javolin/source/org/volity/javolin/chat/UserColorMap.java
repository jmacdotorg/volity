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

/**
 * Maps chat users to colors for message text. This allows each user to have a different
 * color within a chat window. Two colors are associated with each user: one for the
 * user name tag, and one for the message text itself.
 */
public class UserColorMap
{
    // Define a range of colors, from just above red to just below blue, in which full
    // brightness is too light to read against a white background.
    private static final float LOWER_BRIGHT = 0.05f;
    private static final float UPPER_BRIGHT = (2.0f/3.0f) - 0.05f;
    
    private Map mHueMap;
    
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
     * Gets hue associated with the given user from the map. If there is no map entry
     * for the user, a new one is created.
     *
     * @param user  The user to retrieve the hue for.
     * @return      The hue associated with the user.
     */
    private float getUserHue(String user)
    {
        if (mHueMap.get(user) == null)
        {
            mHueMap.put(user, new Float(getNextHue()));
        }

        return ((Float)mHueMap.get(user)).floatValue();
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
        float hue = getUserHue(user);
        float bright = 1.0f;

        // Lower brightness if necessary
        if ((hue > LOWER_BRIGHT) && (hue < UPPER_BRIGHT))
        {
            bright = 0.5f;
        }

        return new Color(Color.HSBtoRGB(hue, 1.0f, bright));
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
        return getUserNameColor(user).darker();
    }
}
