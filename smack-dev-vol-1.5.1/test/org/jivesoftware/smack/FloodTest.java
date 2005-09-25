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

package org.jivesoftware.smack;

import org.jivesoftware.smack.test.SmackTestCase;

/**
 * Simple test to measure server performance.
 *
 * @author Gaston Dombiak
 */
public class FloodTest extends SmackTestCase {

    public FloodTest(String arg0) {
        super(arg0);
    }

    public void testMessageFlood() {
        try {
            Chat chat11 = getConnection(0).createChat(getBareJID(1));
            Chat chat12 = new Chat(getConnection(1), getBareJID(0), chat11.getThreadID());

            Chat chat21 = getConnection(0).createChat(getBareJID(2));
            Chat chat22 = new Chat(getConnection(2), getBareJID(0), chat21.getThreadID());

            Chat chat31 = getConnection(0).createChat(getBareJID(3));
            Chat chat32 = new Chat(getConnection(3), getBareJID(0), chat31.getThreadID());

            for (int i=0; i<500; i++) {
                chat11.sendMessage("Hello_1" + i);
                chat21.sendMessage("Hello_2" + i);
                chat31.sendMessage("Hello_3" + i);
            }
            for (int i=0; i<500; i++) {
                assertNotNull("Some message was lost (" + i + ")", chat12.nextMessage(1000));
                assertNotNull("Some message was lost (" + i + ")", chat22.nextMessage(1000));
                assertNotNull("Some message was lost (" + i + ")", chat32.nextMessage(1000));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /*public void testMUCFlood() {
        try {
            int floodNumber = 50000;
            MultiUserChat chat = new MultiUserChat(getConnection(0), "myroom@" + getMUCDomain());
            chat.create("phatom");
            chat.sendConfigurationForm(new Form(Form.TYPE_SUBMIT));

            MultiUserChat chat2 = new MultiUserChat(getConnection(1), "myroom@" + getMUCDomain());
            chat2.join("christine");

            for (int i=0; i<floodNumber; i++)
            {
                chat.sendMessage("hi");
            }

            Thread.sleep(200);

            for (int i=0; i<floodNumber; i++)
            {
                if (i % 100 == 0) {
                    System.out.println(i);
                }
                assertNotNull("Received " + i + " of " + floodNumber + " messages",
                        chat2.nextMessage(SmackConfiguration.getPacketReplyTimeout()));
            }

            chat.leave();
            //chat2.leave();

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }*/

    protected int getMaxConnections() {
        return 4;
    }
}
