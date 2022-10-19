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

import com.google.inject.AbstractModule;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.server.ServerLifecycleHook;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.url.Protocol;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.net.PortFinder;
import com.mastfrog.blather.WebSocketClientsImpl.ClientImpl.ReqImpl;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import io.netty.channel.Channel;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test harness for writing tests that call websockets, useful with the
 * <a href="https://github.com/timboudreau/giulius-tests">Giulius Tests</a>
 * Guice-enabled JUnit test runner. Simply bind Server to whatever server you
 * want to start, and ask for an instance of WebsocketHostClient to be injected
 * into your test. The server will be started on the first request.
 *
 * @author Tim Boudreau
 */
public class BlatherTestModule extends AbstractModule {

    private final boolean startServer;

    public BlatherTestModule() {
        this(true);
    }

    public BlatherTestModule(boolean startServer) {
        this.startServer = startServer;
    }

    @Override
    protected void configure() {
        bind(HK.class).asEagerSingleton();
        bind(WebsocketHostClient.class).to(HarnessImpl.class);
        if (startServer) {
            bind(ErrorInterceptor.class).to(HarnessImpl.class);
        }
        bind(Boolean.class).annotatedWith(Names.named("_startServer")).toInstance(startServer);
    }

    @ImplementedBy(HostPortImpl.class)
    public interface HostPort {

        int port();

        default String host() {
            return "127.0.0.1";
        }
    }

    static class HostPortImpl implements HostPort {

        int port = -1;

        @Override
        public int port() {
            if (port < 0) {
                return port = new PortFinder().findAvailableServerPort();
            }
            return port;
        }

    }

    static class HK extends ServerLifecycleHook {

        private final WebsocketHostClient client;

        @Inject
        public HK(Registry reg, WebsocketHostClient client) {
            super(reg);
            this.client = client;
        }

        @Override
        protected void onStartup(Application application, Channel channel) throws Exception {
            ((HarnessImpl) client).proceed();
        }
    }

    @Singleton
    static class HarnessImpl implements ErrorInterceptor, WebsocketHostClient {

        private final WebSocketClientsImpl clients;
        private final Server server;
        private ServerControl ctrl;
        private final int port;
        private final WebSocketClientsImpl.ClientImpl client;
        private final AtomicBoolean started = new AtomicBoolean();
        private final CountDownLatch proceedLatch = new CountDownLatch(1);
        private final boolean startServer;

        @Inject
        HarnessImpl(WebSocketClientsImpl clients, Server server, ShutdownHookRegistry reg, @Named("_startServer") boolean startServer, HostPort hp) throws IOException {
            this.clients = clients;
            this.server = server;
            port = hp.port();
            client = (WebSocketClientsImpl.ClientImpl) clients.client(hp.host(), port);
            this.startServer = startServer;
            clients.throttle(120);
            reg.addResource((AutoCloseable) client::closeImmediately);

        }

        void proceed() {
            proceedLatch.countDown();
        }

        void start() throws IOException, InterruptedException {
            if (started.compareAndSet(false, true) && startServer) {
                ctrl = server.start(port);
                proceedLatch.await(30, TimeUnit.SECONDS);
            }
        }

        ExceptionCollector collector;
        Throwable thrownEarly;

        @Override
        public void onError(Throwable thrwbl) {
            if (collector == null) {
                thrownEarly = thrwbl;
            } else {
                collector.onException(thrwbl, null);
            }
        }

        void checkEarlyThrow() {
            Throwable t = thrownEarly;
            thrownEarly = null;
            if (t != null) {
                Exceptions.chuck(t);
            }
        }

        @Override
        public WebsocketClientRequest request(String path) {
            try {
                start();
            } catch (IOException | InterruptedException ex) {
                Exceptions.chuck(ex);
            }
            checkEarlyThrow();
            WebsocketClientRequest result = client.request(path);
            this.collector = ((ReqImpl) result).collector();
            return result;
        }

        @Override
        public WebsocketClientRequest request(String path, Object sendWhenConnected) {
            try {
                start();
            } catch (IOException | InterruptedException ex) {
                Exceptions.chuck(ex);
            }
            checkEarlyThrow();
            WebsocketClientRequest result = client.request(path, sendWhenConnected);
            this.collector = ((ReqImpl) result).collector();
            return result;
        }

        @Override
        public int getPort() {
            return client.getPort();
        }

        @Override
        public String getHost() {
            return client.getHost();
        }

        @Override
        public Protocol getProtocol() {
            return client.getProtocol();
        }
    }
}
