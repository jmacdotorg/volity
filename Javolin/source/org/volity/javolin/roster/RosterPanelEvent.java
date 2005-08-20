/*
 * RosterPanelEvent.java
 *
 * Copyright 2005 Karl von Laudermann
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

import java.util.*;

/**
 * Event that characterizes an action taken on a RosterPanel, such as the selection
 * changing or an item being double-clicked.
 */
public class RosterPanelEvent extends EventObject
{
    private RosterTreeItem mUserItem;
    private int mX;
    private int mY;

    /**
     * Constructor.
     *
     * @param source  The object on which the Event initially occurred.
     * @param item    The RosterTreeItem that was affected. Is null if the event is a
     *                selection change such that the selection was cleared.
     */
    public RosterPanelEvent(Object source, RosterTreeItem item)
    {
        super(source);
        mUserItem = item;
    }

    /**
     * Constructor.
     *
     * @param source  The object on which the Event initially occurred.
     * @param item    The RosterTreeItem that was affected. Is null if the event is a
     *                selection change such that the selection was cleared.
     * @param x       X coordinate of the relevant mouse event.
     * @param y       Y coordinate of the relevant mouse event.
     */
    public RosterPanelEvent(Object source, RosterTreeItem item, int x, int y)
    {
        super(source);
        mUserItem = item;
        mX = x;
        mY = y;
    }

    /**
     * Gets the RosterTreeItem that pertains to the event.
     *
     * @return   The RosterTreeItem that pertains to the event. Returns null if the event
     * is a selection change such that the selection was cleared.
     */
    public RosterTreeItem getRosterTreeItem()
    {
        return mUserItem;
    }

    /**
     * Gets the x coordinate of the relevant mouse event that triggered the
     * RosterPanelEvent.
     *
     * @return   The x coordinate of the triggering mouse event.
     */
    public int getX()
    {
        return mX;
    }

    /**
     * Gets the y coordinate of the relevant mouse event that triggered the
     * RosterPanelEvent.
     *
     * @return   The y coordinate of the triggering mouse event.
     */
    public int getY()
    {
        return mY;
    }
}
