/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2002-2003 Jive Software. All rights reserved.
 * ====================================================================
 * The Jive Software License (based on Apache Software License, Version 1.1)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by
 *        Jive Software (http://www.jivesoftware.com)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Smack" and "Jive Software" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please
 *    contact webmaster@jivesoftware.com.
 *
 * 5. Products derived from this software may not be called "Smack",
 *    nor may "Smack" appear in their name, without prior written
 *    permission of Jive Software.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL JIVE SOFTWARE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 */

package org.jivesoftware.smack.filter;

import junit.framework.TestCase;
import org.jivesoftware.smack.packet.*;

/**
 * A test case for the ToContainsFilter class.
 */
public class ToContainsFilterTest extends TestCase {

    public void testNullArgs() {
        try {
            new ToContainsFilter(null);
            fail("Parameter can not be null");
        }
        catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    public void testAccept() {
        MockToPacket packet = new MockToPacket("foo@bar.com");

        ToContainsFilter toContainsFilter = new ToContainsFilter("");
        assertTrue(toContainsFilter.accept(packet));

        toContainsFilter = new ToContainsFilter("foo");
        assertTrue(toContainsFilter.accept(packet));

        toContainsFilter = new ToContainsFilter("foo@bar.com");
        assertTrue(toContainsFilter.accept(packet));

        toContainsFilter = new ToContainsFilter("bar@foo.com");
        assertFalse(toContainsFilter.accept(packet));

        toContainsFilter = new ToContainsFilter("blah-stuff,net");
        assertFalse(toContainsFilter.accept(packet));
    }

    /**
     * Wraps the MockPacket class to always give an expected To field.
     */
    private class MockToPacket extends MockPacket {
        private String to;
        public MockToPacket(String to) {
            this.to = to;
        }
        public String getTo() {
            return to;
        }
    }
}
