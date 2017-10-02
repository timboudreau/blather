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
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.util.Checks.notNull;

/**
 * A websocket client request. Call one of the onMessage() methods to trigger
 * initiating the request (set up headers and similar first).
 *
 * @author Tim Boudreau
 */
public interface WebsocketClientRequest extends AutoCloseable {

    /**
     * Called when the websocket handshake is completed. Multiple OnConnect
     * handlers are supported.
     *
     * @param onConnect A callback
     * @return this
     */
    WebsocketClientRequest onConnect(OnConnect onConnect);

    /**
     * Pass an object to send as a web socket frame once the handshake is
     * complete (if not a CharSequence, String or WebSocketFrame, will be
     * converted to JSON using the provided ObjectMapper).
     *
     * @param message The message object
     * @return this
     */
    WebsocketClientRequest sendOnConnect(Object message);

    /**
     * Set up a handler which will receive and be able to reply to web socket
     * messages.
     *
     * @param <T> The type to convert message payloads to
     * @param type The type to convert message payloads to
     * @param handler The callback
     * @return this
     */
    <T> WebsocketClientRequest onMessage(Class<T> type, WebsocketMessageHandler<T> handler);

    /**
     * Set up a handler which will receive and be able to reply to web socket
     * messages, which will also receive the raw WebSocketFrame.  Use
     * {@link WebsocketMessageHandler} unless you really need to get hold
     * of the raw <i>WebSocketFrame</i>.
     *
     * @param cb The callback
     * @return this
     */
    WebsocketClientRequest onMessage(FrameCallback<WebSocketFrame> cb);

    /**
     * Wait for the request to complete. Any exceptions thrown while processing
     * the connection will be rethrown when this call exits.
     *
     * @return this
     * @throws Throwable If asynchronous exceptions were thrown
     */
    WebsocketClientRequest await() throws Throwable;

    /**
     * Wait for the request to complete, either by the connection being closed
     * intentionally, or an exception triggering its closure. Any exceptions
     * thrown while processing the connection will be rethrown when this call
     * exits.
     *
     * @param time Maximum time length to wait
     * @param unit Units for time length
     * @return this
     * @throws Throwable If asynchronous exceptions were thrown
     */
    WebsocketClientRequest await(long time, TimeUnit unit) throws Throwable;

    /**
     * Wait for the request to complete. Any exceptions thrown while processing
     * the connection will be rethrown when this call exits.
     *
     * @param dur The length of time to wait
     * @return this
     * @throws Throwable If asynchronous exceptions were thrown
     */
    default WebsocketClientRequest await(Duration dur) throws Throwable {
        return await(dur.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Add a header to include in the initial websocket handshake request.
     *
     * @param <T> The type of the header
     * @param header The header
     * @param value The header value (may not be null)
     * @return this
     */
    <T> WebsocketClientRequest addHeader(HeaderValueType<T> header, T value);

    /**
     * Add a header to include in the initial websocket handshake request. May
     * not be called after connected.
     *
     * @param headerName The header name
     * @param value The header value (may not be null)
     * @return this
     */
    default WebsocketClientRequest addHeader(String headerName, String value) {
        addHeader(Headers.stringHeader(headerName), value);
        return this;
    }

    /**
     * Add a query pair in the standard form ?key=value to the websocket
     * handshake url. May not be called after connected. Passed names and values
     * will be url-encoded.
     *
     * @param name The name, non null
     * @param value The value, non null
     * @return this
     */
    WebsocketClientRequest addUrlQueryPair(String name, String value);

    /**
     * Convenince method for adding a long typed query pair.
     *
     * @param name The name, non null
     * @param value The value, non null
     * @return this
     */
    default WebsocketClientRequest addUrlQueryPair(String name, long value) {
        return addUrlQueryPair(notNull("name", name), Long.toString(value));
    }

    /**
     * Convenince method for adding an integer typed query pair.
     *
     * @param name The name, non null
     * @param value The value, non null
     * @return this
     */
    default WebsocketClientRequest addUrlQueryPair(String name, int value) {
        return addUrlQueryPair(notNull("name", name), Integer.toString(value));
    }

    /**
     * Convenince method for adding a boolean typed query pair.
     *
     * @param name The name, non null
     * @param value The value, non null
     * @return this
     */
    default WebsocketClientRequest addUrlQueryPair(String name, boolean value) {
        return addUrlQueryPair(notNull("name", name), Boolean.toString(value));
    }

    /**
     * Use an error handler which can decide whether or not to close the
     * connection when an exception is thrown.
     *
     * @param onError An error handler
     * @return this
     */
    WebsocketClientRequest withErrorHandler(WebsocketErrorHandler onError);

    /**
     * Install a callback to run once the connection is closed.  Multiple
     * OnDisconnect callbacks may be provided.
     *
     * @param dc A callback
     * @return this
     */
    WebsocketClientRequest onDisconnect(OnDisconnect dc);

    /**
     * Turn on detailed logging for this request.
     *
     * @return this
     */
    WebsocketClientRequest log();

}
