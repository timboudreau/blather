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
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.RequestLogger;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteur.websocket.OnWebsocketConnect;
import com.mastfrog.acteur.websocket.WebSocketUpgradeActeur;
import com.mastfrog.acteurbase.Deferral;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class TestApplication extends Application {

    private final Provider<ErrorInterceptor> icept;

    @Inject
    TestApplication(Provider<ErrorInterceptor> icept) {
        this.icept = icept;
        add(WsTestPage.class);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onError(Throwable err) {
        icept.get().onError(err);
    }

    public static class Module extends AbstractModule implements RequestLogger {

        @Override
        protected void configure() {
            install(new ServerModule<>(TestApplication.class));
            install(new BlatherTestModule());
            bind(RequestLogger.class).toInstance(this);
//            bind(OnWebsocketConnect.class).to(OWC.class);
        }

        @Override
        public void onBeforeEvent(RequestID rid, Event<?> event) {
            // do nothing
        }

        @Override
        public void onRespond(RequestID rid, Event<?> event, HttpResponseStatus status) {
            // do nothing
        }
    }

    static class OWC implements OnWebsocketConnect {

        @Override
        public Object connected(HttpEvent evt, Channel channel) {
            channel.writeAndFlush(new TextWebSocketFrame("hello"));
            return null;
        }
    }

    static class WsTestPage extends Page {

        @Inject
        @SuppressWarnings("deprecation")
        WsTestPage(ActeurFactory af) {
            add(af.matchMethods(true, Method.GET, Method.POST));
            add(af.matchPath("^ws$"));
            add(WebSocketUpgradeActeur.class);
            add(EchoWebsocketActeur.class);
        }

        static class EchoWebsocketActeur extends Acteur {

            @Inject
            @SuppressWarnings("unchecked")
            EchoWebsocketActeur(WebSocketFrame frame, ObjectMapper mapper, Deferral defer) throws IOException {
                if (frame instanceof TextWebSocketFrame) {
                    TextWebSocketFrame frm = (TextWebSocketFrame) frame;
                    ok(new TextWebSocketFrame("GOT: " + frm.text()));
//                    ok("GOT: " + frm.text());
                } else {
                    Map<String, Object> m = mapper.readValue((InputStream) new ByteBufInputStream(frame.content()), Map.class);
                    m = new LinkedHashMap<>(m);
                    m.put("echo", true);
                    ok(m);
                }
            }
        }
    }
}
