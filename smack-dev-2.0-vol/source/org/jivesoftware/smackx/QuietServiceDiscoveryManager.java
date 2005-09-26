/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright 2003-2004 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smackx;

import org.jivesoftware.smack.XMPPConnection;

/**
 * This is a version of ServiceDiscoveryManager which does not actually listen
 * for queries or send replies. It *does* allow you to make disco queries to
 * other entities.
 *
 * QuietServiceDiscoveryManager allows you to set up an XMPPConnection which
 * doesn't support service discovery, but which is still usable with classes
 * (like MultiUserChat) which assume the existence of an SDM.
 */
public class QuietServiceDiscoveryManager extends ServiceDiscoveryManager
{
    public QuietServiceDiscoveryManager(XMPPConnection connection) {
        super(connection);
    }

    protected void initPacketListener() {
        // Do not set up listeners.
    }
}

