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

import com.mastfrog.url.Protocol;

/**
 * A websocket client that knows what host and port and protocol it is talking
 * to, and can create websocket HTTP requests.
 *
 * @author Tim Boudreau
 */
public interface WebsocketHostClient {

    /**
     * Initiate a new request (http call will actually be made when you add an
     * on message handler).
     *
     * @param path The path
     * @return The request
     */
    WebsocketClientRequest request(String path);

    /**
     * Initiate a new request (http call will actually be made when you add an
     * on message handler).
     *
     * @param path The path
     * @param sendWhenConnected An object to send over the wire once the
     * websocket handshake is completed.
     * @return The request
     */
    WebsocketClientRequest request(String path, Object sendWhenConnected);

    /**
     * Get the port requests will be made to.
     *
     * @return the port
     */
    int getPort();

    /**
     * Get the host requests will be made to.
     *
     * @return the host
     */
    String getHost();

    /**
     * Get the protocol requests will use - will be Protocols.WSS or
     * Protocols.WS.
     *
     * @return The protocol
     */
    Protocol getProtocol();
}
