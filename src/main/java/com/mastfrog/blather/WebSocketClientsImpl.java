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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.url.Path;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.Protocols;
import com.mastfrog.url.URL;
import com.mastfrog.url.URLBuilder;
import static com.mastfrog.util.Checks.notNull;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.thread.ResettableCountDownLatch;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.net.ssl.SSLException;

/**
 * This is an example of a WebSocket client.
 * <p>
 * In order to run this example you need a compatible WebSocket server.
 * Therefore you can either start the WebSocket server from the examples by
 * running {@link io.netty.example.http.websocketx.server.WebSocketServer} or
 * request to an existing WebSocket server such as
 * <a href="http://www.websocket.org/echo.html">ws://echo.websocket.org</a>.
 * <p>
 * The client will attempt to request to the URI passed to it as the first
 * argument. You don't have to specify any arguments if you want to request to
 * the example WebSocket server, as this is the default.
 */
final class WebSocketClientsImpl extends Blather {

    final EventLoopGroup group = new NioEventLoopGroup();
    private final ObjectMapper mapper;

    @Inject
    WebSocketClientsImpl(ShutdownHookRegistry registry, ObjectMapper mapper) {
        registry.add(group);
        this.mapper = mapper;
    }

    @Override
    public WebsocketHostClient client(String host, int port, boolean ssl) {
        return new ClientImpl(host, port, ssl);
    }

    final class ClientImpl implements WebsocketHostClient {

        private final String host;
        private final int port;
        private final boolean ssl;

        ClientImpl(String host, int port, boolean ssl) {
            this.host = host;
            this.port = port;
            this.ssl = ssl;
        }

        @Override
        public WebsocketClientRequest request(String path) {
            return new ReqImpl(notNull("path", path), null);
        }

        @Override
        public WebsocketClientRequest request(String path, Object sendWhenConnected) {
            return new ReqImpl(notNull("path", path), sendWhenConnected);
        }

        @Override
        public int getPort() {
            return port;
        }

        @Override
        public String getHost() {
            return host;
        }

        @Override
        public Protocol getProtocol() {
            return ssl ? Protocols.WSS : Protocols.WS;
        }

        final class ReqImpl implements WebsocketClientRequest {

            private final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
            final String path;
            private final List<OnConnect> onConnects = new ArrayList<>();
            private Channel channel;
            private final ExceptionCollectorImpl ex = new ExceptionCollectorImpl();
            FrameCallback<WebSocketFrame> callback;
            private final AtomicBoolean started = new AtomicBoolean();
            private final AtomicBoolean closed = new AtomicBoolean();
            private final List<String[]> queryPairs = new ArrayList<>();
            private final List<HeaderEntry<?>> headers = new ArrayList<>();
            WebsocketErrorHandler onError;
            private boolean log;
            private final Logger logger;

            ReqImpl(String path, Object sendWhenConnected) {
                this.path = path;
                if (sendWhenConnected != null) {
                    onConnects.add((OnConnect) (URL ignored, ChannelControl ctrl) -> {
                        log("Send initial message {0} as web socket frame", sendWhenConnected);
                        WebSocketFrame frame = toWebSocketFrame(sendWhenConnected, ctrl.channel());
                        ctrl.channel().writeAndFlush(frame);
                    });
                }
                logger = Logger.getLogger(url(path).toString());
            }

            void log(String s, Object... params) {
                if (log) {
                    logger.log(Level.WARNING, s, params);
                }
            }

            void log(String msg, Throwable thrown) {
                if (log) {
                    logger.log(Level.SEVERE, msg, thrown);
                }
            }

            public WebsocketClientRequest log() {
                this.log = true;
                return this;
            }

            private URL url(String path) {
                Protocol protocol = ssl ? Protocols.WSS : Protocols.WS;
                int pt = port <= 0 ? protocol.getDefaultPort().intValue() : port;
                URLBuilder bldr = URL.builder(protocol).setHost(host).setPort(pt).setPath(Path.parse(path));
                for (String[] queryPair : queryPairs) {
                    bldr.addQueryPair(queryPair[0], queryPair[1]);
                }
                URL result = bldr.create();
                if (!result.isValid()) {
                    result.getProblems().throwIfFatalPresent();
                }
                return result;
            }

            ExceptionCollector collector() {
                return ex;
            }

            @Override
            public <T> WebsocketClientRequest addHeader(HeaderValueType<T> header, T value) {
                if (started.get()) {
                    throw new IllegalStateException("Request already initiated, cannot add headers now");
                }
                headers.add(new HeaderEntry<T>(header, value));
                return this;
            }

            @Override
            public WebsocketClientRequest addUrlQueryPair(String name, String value) {
                if (started.get()) {
                    throw new IllegalStateException("Request already initiated, cannot add headers now");
                }
                queryPairs.add(new String[]{notNull("name", name), notNull("value", value)});
                return this;
            }

            public void close() {
                if (closed.compareAndSet(false, true) && channel != null && channel.isOpen()) {
                    channel.writeAndFlush(new CloseWebSocketFrame()).addListener(CLOSE);
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> WebsocketClientRequest onMessage(Class<T> type, WebsocketMessageHandler<T> handler) {
                FrameCallback<Object> fc = new FrameCallback<Object>() {
                    int ix = 0;

                    @Override
                    public Object onMessage(WebSocketFrame frame, Object data, ChannelControl channel) throws Exception {
                        return handler.onMessage(ix, type.cast(data), channel);
                    }
                };
                FrameCallback<WebSocketFrame> f = new JsonFrameCallback(fc, mapper, type);
                return onMessage(f);
            }

            @Override
            public WebsocketClientRequest onMessage(FrameCallback<WebSocketFrame> f) {
                this.callback = f;
                // Throw this on the queue so headers and query pairs can be set
                // before executions.  Note this could be racy, in which case we
                // need to block, or an explicit go() method.
                group.execute((Runnable) () -> {
                    if (started.compareAndSet(false, true)) {
                        try {
                            doConnect(callback);
                        } catch (URISyntaxException | SSLException e) {
                            Exceptions.chuck(e);
                        }
                    }
                });
                return this;
            }

            @Override
            public WebsocketClientRequest withErrorHandler(WebsocketErrorHandler onError) {
                this.onError = onError;
                return this;
            }

            private final List<OnDisconnect> onDisconnects = new ArrayList<>();

            @Override
            public WebsocketClientRequest onDisconnect(OnDisconnect dc) {
                onDisconnects.add(notNull("dc", dc));
                return this;
            }

            final class ExceptionCollectorImpl implements ExceptionCollector {

                private Throwable lastThrown;
                private volatile Throwable thrown;
                Set<Throwable> seen = ConcurrentHashMap.newKeySet();

                @Override
                public void rethrow() throws Throwable {
                    lastThrown = thrown;
                    thrown = null;
                    if (lastThrown != null) {
                        throw lastThrown;
                    }
                }

                @Override
                public void onException(Throwable t, ChannelHandlerContext ctx) {
                    if (onError != null) {
                        if (!onError.onError(thrown)) {
                            log("WebsocketErrorHandler suppressing exception", t);
                            return;
                        }
                    }
                    log("Exception thrown", t);
                    if (seen.contains(t)) {
                        return;
                    }
                    seen.add(t);
                    if (thrown != null) {
                        thrown.addSuppressed(t);
                    } else {
                        thrown = t;
                    }
                }
            };
            ChannelFutureListener closeListener = (ChannelFuture f) -> {
                log("Connection {0} closed", f.channel());
                try {
                    if (f.cause() != null) {
                        ex.onException(f.cause(), null);
                    }
                } finally {
                    if (group.isShutdown()) {
                        log("EventLoopGroup already shutdown, trigger await() exit immediately");
                        latch.countDown();
                    } else {
                        log("Trigger await() exit in next round on event loop");
                        group.submit(new Runnable() {
                            @Override
                            public void run() {
                                log("Triggering await() exit CountDownLatch");
                                latch.countDown();
                            }
                        });
                    }
                }
            };

            void doConnect(FrameCallback<WebSocketFrame> frameCallback) throws URISyntaxException, SSLException {
                latch.reset(1);
                URL url = url(path);
                URI uri = url.toURI();
                log("Will connect to {0}", url);
                final SslContext sslCtx;
                if (ssl) {
                    log("Using SSL");
                    sslCtx = SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                } else {
                    sslCtx = null;
                }
                Function<Object, WebSocketFrame> fconvert = new Function<Object, WebSocketFrame>() {
                    @Override
                    public WebSocketFrame apply(Object t) {
                        try {
                            return toWebSocketFrame(t, channel);
                        } catch (IOException e) {
                            return Exceptions.chuck(e);
                        }
                    }
                };

                @SuppressWarnings("unchecked")
                BiFunction<FrameCallback<?>, Class<?>, FrameCallback<WebSocketFrame>> convert = (callback, type) -> {
                    return callbackFor(callback, (Class) type);
                };

                DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
                for (HeaderEntry<?> e : headers) {
                    e.decorate(httpHeaders);
                }

                final WebSocketClientHandler handler
                        = new WebSocketClientHandler(frameCallback,
                                WebSocketClientHandshakerFactory.newHandshaker(
                                        uri, WebSocketVersion.V13, null, true, httpHeaders), convert, fconvert, ex);

                if (log) {
                    handler.logger = logger;
                }
                handler.onHandshake((ChannelFuture f) -> {
                    if (!f.isSuccess()) {
                        log("Websocket handshake FAILED to {0}", url);
                    } else {
                        log("Websocket handshake SUCCESS to {0}", url);
                        for (OnConnect oc : onConnects) {
                            oc.onConnect(url, handler.ctrl(f.channel()));
                        }
                    }
                });

                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                log("Initialize SocketChannel {0}", ch);
                                ChannelPipeline p = ch.pipeline();
                                if (sslCtx != null) {
                                    p.addLast(sslCtx.newHandler(ch.alloc(), url.getHost().toString(), url.getPort().intValue()));
                                }
                                p.addLast(
                                        new HttpClientCodec(),
                                        new HttpObjectAggregator(8192),
                                        WebSocketClientCompressionHandler.INSTANCE,
                                        handler);
                            }
                        });
                b.connect(url.getHost().toString(), url.getPort().intValue()).addListener((ChannelFuture f) -> {
                    log("Connected to {0}", f.channel().remoteAddress());
                    if (closed.get()) {
                        log("close() was called before connection established, aborting.");
                        latch.countDown();
                        f.channel().close();
                        return;
                    }
                    if (f.isSuccess()) {
                        channel = f.channel();
                        channel.closeFuture().addListener((ChannelFutureListener) (ChannelFuture f1) -> {
                            for (OnDisconnect dc : onDisconnects) {
                                dc.onDisconnect(url, ex.thrown == null ? ex.lastThrown : ex.thrown);
                            }
                        });
                        channel.closeFuture().addListener(closeListener);
                        ChannelControl ctrl = handler.ctrl(channel);
                    } else {
                        log("Connecting failed, wake up waiters.", f.cause());
                        ex.onException(f.cause(), null);
                        latch.countDown();
                    }
                });

            }

            @Override
            public WebsocketClientRequest onConnect(OnConnect onConnect) {
                onConnects.add(notNull("onConnect", onConnect));
                return this;
            }

            @Override
            public WebsocketClientRequest sendOnConnect(Object message) {
                if (channel != null) {
                    throw new IllegalStateException("Already connected");
                }
                onConnects.add((OnConnect) (URL ignored, ChannelControl ctrl) -> {
                    log("Send initial message {0} as web socket frame", message);
                    WebSocketFrame frame = toWebSocketFrame(message, ctrl.channel());
                    ctrl.channel().writeAndFlush(frame);
                });
                return this;
            }

            @Override
            public WebsocketClientRequest await() throws Throwable {
                ex.rethrow();
                latch.await();
                ex.rethrow();
                return this;
            }

            @Override
            public WebsocketClientRequest await(long duration, TimeUnit unit) throws Throwable {
                ex.rethrow();
                latch.await(duration, unit);
                ex.rethrow();
                return this;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> FrameCallback<WebSocketFrame> callbackFor(FrameCallback<T> callback, Class<T> type) {
        if (type == WebSocketFrame.class) {
            return (FrameCallback<WebSocketFrame>) callback;
        } else {
            return new JsonFrameCallback<>(callback, mapper, type);
        }
    }

    <T> WebSocketFrame toWebSocketFrame(T obj, Channel channel) throws IOException {
        if (obj == null) {
            return null;
        }
        if (obj instanceof WebSocketFrame) {
            return (WebSocketFrame) obj;
        }
        ByteBuf buf = channel == null ? ByteBufAllocator.DEFAULT.buffer() : channel.alloc().buffer();
        if (obj instanceof CharSequence) {
            buf.writeCharSequence((CharSequence) obj, StandardCharsets.UTF_8);
            return new TextWebSocketFrame(buf);

        } else {
            mapper.writeValue((OutputStream) new ByteBufOutputStream(buf), obj);
            return new BinaryWebSocketFrame(buf);
        }
    }

    static class HeaderEntry<T> {

        private final HeaderValueType<T> header;
        private final T value;

        public HeaderEntry(HeaderValueType<T> header, T value) {
            this.header = header;
            this.value = value;
        }

        void decorate(HttpHeaders r) {
            CharSequence val = header.toCharSequence(value);
            r.add(header.name(), val);
        }
    }

    class JsonFrameCallback<T> implements FrameCallback<WebSocketFrame> {

        private final FrameCallback<T> delegate;
        private final ObjectMapper mapper;
        private final Class<T> type;

        public JsonFrameCallback(FrameCallback<T> delegate, ObjectMapper mapper, Class<T> type) {
            this.delegate = delegate;
            this.mapper = mapper;
            this.type = type;
        }

        @Override
        @SuppressWarnings("unchecked")
        public WebSocketFrame onMessage(WebSocketFrame frame, WebSocketFrame data, ChannelControl channel) throws Exception {
            if (type == String.class || type == CharSequence.class) {
                CharSequence seq = frame instanceof TextWebSocketFrame ? ((TextWebSocketFrame) frame).text()
                        : frame.content().readCharSequence(frame.content().readableBytes(), StandardCharsets.UTF_8);
                if (type == String.class) {
                    seq = seq.toString();
                }
                return toWebSocketFrame((T) delegate.onMessage(frame, (T) seq, channel), channel.channel());
            }
            T obj = mapper.readValue((InputStream) new ByteBufInputStream(data.content()), type);
            T response = delegate.onMessage(frame, obj, channel);
            if (response != null) {
                return toWebSocketFrame(response, channel.channel());
            }
            return null;
        }

        @Override
        public void onConnect(ChannelControl channel) {
            delegate.onConnect(channel);
        }
    }

    class StringFrameCallback implements FrameCallback<WebSocketFrame> {

        private final FrameCallback<String> delegate;

        public StringFrameCallback(FrameCallback<String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public WebSocketFrame onMessage(WebSocketFrame frame, WebSocketFrame data, ChannelControl channel) throws Exception {
            CharSequence seq = data.content().readCharSequence(frame.content().readableBytes(), StandardCharsets.UTF_8);
            String res = delegate.onMessage(frame, seq.toString(), channel);
            if (res != null) {
                return new TextWebSocketFrame(res);
            }
            return null;
        }

        @Override
        public void onConnect(ChannelControl channel) {
            delegate.onConnect(channel);
        }
    }
}
