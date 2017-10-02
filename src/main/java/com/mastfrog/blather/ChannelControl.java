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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

/**
 * Interface for manipulating the HTTP connection and handler list,
 * passed in to other callbacks.
 *
 * @author Tim Boudreau
 */
public interface ChannelControl {

    /**
     * Allows a callback to supply a different callback to receive the
     * next websocket frame.
     *
     * @param <T> The type
     * @param cb The callback
     * @param type The type message payloads will be converte dto
     * @return this
     */
    <T> ChannelControl nextCallback(FrameCallback<T> cb, Class<T> type);

    /**
     * Allows a callback to supply a different callback to receive the
     * next websocket frame.
     *
     * @param <T> The type
     * @param cb A callback
     * @param type The type it wants web socket payloads marshalled to
     * @return this
     */
    <T> ChannelControl nextCallback(WebsocketMessageHandler<T> cb, Class<T> type);

    /**
     * Send a message, which will be converted to a web socket frame as follows:
     * <ul>
     * <li>If a String or other CharSequence, send a TextWebSocketFrame</li>
     * <li>If a WebSocketFrame, send it as is</li>
     * <li>Any other object - covert it to JSON (or whatever Jackson is
     * configured to convert it to there is BSON and other support for Jackson)
     * using Jackson
     * </ul>
     *
     * @param <T> The message type
     * @param message The message
     * @return A future which can be listened on to determine when the resulting
     * message has been flushed to the socket.  If you want to send multiple messages,
     * and guarantee the order they are sent in, then listen on the ChannelFuture and
     * do not call send again until you are inside your ChannelFutureListener's
     * callback method.
     */
    <T> ChannelFuture send(T message);

    /**
     * Politely close the channel (sending a CloseWebSocketFrame) and then closing
     * the connection from our side (to brutally kill a connection, simply call
     * channel().close()).
     *
     * @return this
     */
    ChannelControl close();

    /**
     * Get the raw Netty channel being used for communication.
     *
     * @return A channel
     */
    Channel channel();

}
