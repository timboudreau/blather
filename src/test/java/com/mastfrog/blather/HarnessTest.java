/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.blather;

import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.collections.StringObjectMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.Duration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(TestApplication.Module.class)
public class HarnessTest {

    int count = 0;

    @Test(timeout = 20000)
    public void test(WebsocketHostClient client) throws Throwable {
        client.request("/ws", "hello")
                .addHeader(Headers.stringHeader("X-Foo"), "bar")
                .addUrlQueryPair("foo", "bar")
                .onMessage(String.class, this::withFrame)
                .await(Duration.ofSeconds(20));
    }

    public Object withFrame(int msgIndex, String data, ChannelControl ctrl) {
        if (count++ == 4) {
            ctrl.close();
            fail("Handler should have been replaced");
            return null;
        } else if (count == 3) {
            ctrl.nextCallback(this::bypass, String.class);
        }
        return "Hello: " + data;
    }

    public Object bypass(int msgIndex, String data, ChannelControl ctrl) {
        ctrl.close();
        return null;
    }

    @Test(timeout = 20000)
    public void testJson(WebsocketHostClient client) throws Throwable {
        client.request("/ws", map("this").to("that").build())
                .addHeader(Headers.stringHeader("X-Foo"), "bar")
                .addUrlQueryPair("foo", "bar")
                .onMessage(StringObjectMap.class, this::withJson)
                .await(Duration.ofSeconds(20));
    }

    public Object withJson(int msgIndex, StringObjectMap data, ChannelControl ctrl) {
        assertEquals("that", data.get("this"));
        assertEquals(Boolean.TRUE, data.get("echo"));
        ctrl.close();
        return null;
    }
}
