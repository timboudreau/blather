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
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

final class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private FrameCallback<WebSocketFrame> cb;

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private final BiFunction<FrameCallback<?>, Class<?>, FrameCallback<WebSocketFrame>> convert;
    private final Function<Object, WebSocketFrame> fconvert;
    private final ExceptionCollector ex;
    Logger logger;

    public WebSocketClientHandler(FrameCallback<WebSocketFrame> cb, WebSocketClientHandshaker handshaker,
            BiFunction<FrameCallback<?>, Class<?>, FrameCallback<WebSocketFrame>> convert,
            Function<Object, WebSocketFrame> fconvert, ExceptionCollector ex) {
        this.cb = cb;
        this.handshaker = handshaker;
        this.fconvert = fconvert;
        this.convert = convert;
        this.ex = ex;
    }

    ChannelControl ctrl(Channel channel) {
        return new ChannelControlImpl(channel);
    }

    class ChannelControlImpl implements ChannelControl {

        final Channel channel;

        public ChannelControlImpl(Channel channel) {
            this.channel = channel;
        }

        @Override
        public <T> ChannelControl nextCallback(FrameCallback<T> cb, Class<T> type) {
            WebSocketClientHandler.this.cb = convert.apply(cb, type);
            return this;
        }

        @Override
        public <T> ChannelFuture send(T message) {
            ChannelFuture result = channel().writeAndFlush(fconvert.apply(cb));
            result.addListener((ChannelFuture f) -> {
                if (!f.isSuccess()) {
                    exceptionCaught(null, f.cause());
                }
            });
            return result;
        }

        @Override
        public ChannelControl close() {
            if (logger != null) {
                logger.log(Level.INFO, "Client called ChannelControl.close(), politely closing connection.");
            }
            channel.writeAndFlush(new CloseWebSocketFrame()).addListener(CLOSE);
            return this;
        }

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public <T> ChannelControl nextCallback(WebsocketMessageHandler<T> h, Class<T> type) {
            FrameCallback<T> fc = new FrameCallback<T>() {
                int ix = 0;

                @Override
                @SuppressWarnings("unchecked")
                public T onMessage(WebSocketFrame frame, T data, ChannelControl channel) throws Exception {
                    return (T) h.onMessage(ix++, data, channel);
                }
            };
            return nextCallback(fc, type);
        }
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    ChannelFutureListener onHandshake;

    void onHandshake(ChannelFutureListener r) {
        onHandshake = r;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
        if (onHandshake != null) {
            handshakeFuture.addListener(onHandshake);
        }
        handshakeFuture.addListener((ChannelFuture f) -> {
            if (!f.isSuccess() && f.cause() != null) {
                exceptionCaught(ctx, f.cause());
                return;
            }
            cb.onConnect(new ChannelControlImpl(f.channel()));
        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (logger != null) {
            logger.log(Level.INFO, "Client is connected.");
        }
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (logger != null) {
            logger.log(Level.INFO, "Client is disconnected");
        }
        cb.onDisconnect();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            if (logger != null) {
                logger.log(Level.INFO, "Finishing websocket handshake");
            }
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            if (logger != null) {
                logger.log(Level.INFO, "Websocket handshake complete.");
            }
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status()
                    + ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            if (logger != null) {
                logger.log(Level.INFO, "Received frame {0}", textFrame);
            }
            try {
                WebSocketFrame response = cb.onMessage(frame, frame, ctrl(ctx.channel()));
                if (response != null) {
                    ctx.channel().writeAndFlush(response);
                }
            } catch (Exception e) {
                exceptionCaught(ctx, e);
            }
        } else if (frame instanceof BinaryWebSocketFrame) {
            if (logger != null) {
                logger.log(Level.INFO, "Received frame {0}", frame);
            }
            try {
                WebSocketFrame response = cb.onMessage(frame, frame, ctrl(ctx.channel()));
                if (response != null) {
                    ctx.channel().writeAndFlush(response);
                }
            } catch (Exception e) {
                exceptionCaught(ctx, e);
            }
        } else if (frame instanceof PongWebSocketFrame) {
            if (logger != null) {
                logger.log(Level.FINE, "WebSocket Client received pong");
            }
        } else if (frame instanceof CloseWebSocketFrame) {
            if (logger != null) {
                logger.log(Level.INFO, "WebSocket Client received CloseWebsocketFrame from server, closing connection.");
            }
            ch.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ex.onException(cause, ctx);
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.newSucceededFuture().addListener((ChannelFuture ff) -> {
            ctx.executor().execute(() -> {
                ctx.close();
            });
        });
    }
}
