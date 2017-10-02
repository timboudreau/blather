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

/**
 * Simple handler for websocket messages - the inbound bytes are marshalled
 * to the type requested, and the handler receives messages of that type.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface WebsocketMessageHandler<T> {

    /**
     *
     * @param msgIndex The cumulative count of the messages sent to <i>this handler</i>
     * (not necessarily the entire websocket session, if the handler was replaced).
     * @param data The data, marshalled to whatever type is requested
     * @param ctrl Object for manipulating the channel, sending responses or replacing
     * this handler with another.
     * @return A reply message to marshal and send, or null to send no reply (you
     * can hold onto the ChannelControl and send one asynchronously if necessary).
     */
    Object onMessage(int msgIndex, T data, ChannelControl ctrl) throws Exception;

}
