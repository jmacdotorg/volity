/*
 * RosterPanelSelectionListener.java
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
 * Interface for classes that want to be notified when the selection has changed in a
 * RosterPanel.
 */
public interface RosterPanelSelectionListener
{
    /**
     * Called whenever the value of the selection changes.
     *
     * @param e  The event that characterizes the change.
     */
    public void valueChanged(RosterPanelSelectionEvent e);
}
