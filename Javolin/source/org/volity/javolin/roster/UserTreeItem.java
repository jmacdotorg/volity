/*
 * UserTreeItem.java
 *
 * Copyright 2004-2005 Karl von Laudermann
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
package org.volity.javolin.roster;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;

/**
 * Object representing a Jabber user, to be used in a node of the roster tree.
 */
public class UserTreeItem
{
    private String mID;
    private String mNickname;
    private Presence.Type mPresType = Presence.Type.UNAVAILABLE;
    private Presence.Mode mPresMode = Presence.Mode.AVAILABLE;
    private String mMessage;

    /**
     * Constructor.
     *
     * @param entry     A RosterEntry for the user.
     * @param presence  The user's presence descriptor. Can be null.
     */
    public UserTreeItem(RosterEntry entry, Presence presence)
    {
        mID = entry.getUser();

        mNickname = entry.getName();
        if (mNickname == null)
        {
            mNickname = "";
        }

        if (presence != null)
        {
            mPresType = presence.getType();
            mPresMode = presence.getMode();
            mMessage = presence.getStatus(); // Might be null
        }
    }

    /**
     * Gets the user ID of the item.
     *
     * @return   The user ID of the item.
     */
    public String getId()
    {
        return mID;
    }

    /**
     * Gets the nickname of the item.
     *
     * @return   The nickname of the item. If the user does not have a nickname, the
     * empty string will be returned.
     */
    public String getNickname()
    {
        return mNickname;
    }

    /**
     * Returns the string to use in the tree to represent this item.
     *
     * @return   The string to use in the tree to represent this item.
     */
    public String toString()
    {
        String retVal = mNickname;

        if (retVal.equals(""))
        {
            retVal = mID;
        }

        if ((mMessage != null) && !mMessage.equals(""))
        {
            retVal += " (" + mMessage + ")";
        }

        return retVal;
    }

    /**
     * Tells whether the user is available.
     *
     * @return   true if the user is available, false if not.
     */
    public boolean isAvailable()
    {
        return mPresType != Presence.Type.UNAVAILABLE;
    }

    /**
     * Tells whether the user is busy.
     *
     * @return   True if the user is busy, false if not.
     */
    public boolean isBusy()
    {
        return (mPresMode != Presence.Mode.AVAILABLE) &&
            (mPresMode != Presence.Mode.CHAT);
    }
}
