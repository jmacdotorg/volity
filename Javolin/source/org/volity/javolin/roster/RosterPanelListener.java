/*
 * RosterPanelListener.java
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

/**
 * Interface for classes that want to be notified of actions taken on a RosterPanel, such
 * as the selection changing or an item being double-clicked.
 */
public interface RosterPanelListener
{
    /**
     * Called whenever the value of the selection changes.
     *
     * @param e  The event that characterizes the change.
     */
    public void selectionChanged(RosterPanelEvent e);

    /**
     * Called when an item is double-clicked.
     *
     * @param e  The event that characterizes the action.
     */
    public void itemDoubleClicked(RosterPanelEvent e);

    /**
     * Called when the user invokes the context menu on the roster.
     *
     * @param e  The event that characterizes the action.
     */
    public void contextMenuInvoked(RosterPanelEvent e);
}
