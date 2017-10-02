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

import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Low level interface for receiving websocket frames - use
 * {@link WebsocketMessageHandler} unless you need access to the raw
 * WebSocketFrame <i>and</i> it's marshalled payload.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface FrameCallback<T> {

    /**
     * Called when a message is received.
     *
     * @param frame The frame
     * @param data The data, using whatever type this is parameterized on
     * @param channel The channel controller
     * @return A new message to marshal and send in response, or null
     * @throws Exception If something goes wrong - this may abort the
     * connection, or may be passed to a {@link WebsocketErrorHandler} if one is
     * set on the request.
     */
    T onMessage(WebSocketFrame frame, T data, ChannelControl channel) throws Exception;

    /**
     * Called once the websocket handshake is complete.
     */
    default void onDisconnect() {
    }

    /**
     * Called after the channel is closed.
     * @param channel Object for manipulating the channel or sending an initial
     * message
     */
    default void onConnect(ChannelControl channel) {
    }

}
