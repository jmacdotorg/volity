/*
 * RosterTreeItem.java
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
public class RosterTreeItem
{
    private String mID;
    private String mNickname;
    private Presence.Type mPresType = Presence.Type.UNAVAILABLE;
    private Presence.Mode mPresMode = Presence.Mode.AVAILABLE;
    private RosterPacket.ItemType mSubType = RosterPacket.ItemType.NONE;
    private String mVolityRole = null;
    private String mMessage;

    /**
     * Constructor. The role argument is one of the constants from
     * CapPresenceFactory: VOLITY_ROLE_PLAYER, VOLITY_ROLE_REFEREE, etc. If the
     * user is logged on, but not with a Volity client, this is
     * VOLITY_ROLE_NONE. If the user is not logged on, it is null.
     *
     * @param entry     A RosterEntry for the user.
     * @param presence  The user's presence descriptor. Can be null.
     * @param role      The Volity client role.
     */
    public RosterTreeItem(RosterEntry entry, Presence presence, String role)
    {
        mID = entry.getUser();
        mSubType = entry.getType();

        mNickname = entry.getName();
        if (mNickname == null)
        {
            mNickname = "";
        }

        mVolityRole = role;

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

    /**
     * Get the subscription type (FROM/TO/BOTH/NONE) of the user.
     */
    public RosterPacket.ItemType getSubType() 
    {
        return mSubType;
    }

    /**
     * Tells whether the user is logged on via a Volity client, and in what
     * role. If the user is logged on with multiple clients, this returns the
     * Volity one. If there are multiple Volity roles, it just shows one of
     * them.
     */
    public String getVolityRole()
    {
        return mVolityRole;
    }
}
