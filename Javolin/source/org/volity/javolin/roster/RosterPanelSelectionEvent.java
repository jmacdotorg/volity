/*
 * RosterPanelSelectionEvent.java
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
 * Event that characterizes a change in the selection of a RosterPanel.
 */
public class RosterPanelSelectionEvent extends EventObject
{
    private UserTreeItem mUserItem;

    /**
     *Constructor.
     *
     * @param source  The object on which the Event initially occurred.
     * @param item    The UserTreeItem that was selected. Is null selection was cleared.
     */
    public RosterPanelSelectionEvent(Object source, UserTreeItem item)
    {
        super(source);
        mUserItem = item;
    }
}
