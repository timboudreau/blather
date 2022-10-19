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
import com.google.inject.ImplementedBy;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import static com.mastfrog.shutdown.hooks.ShutdownHookRegistry.shutdownHookRegistry;
import com.mastfrog.url.Parameters;
import com.mastfrog.url.ParametersElement;
import com.mastfrog.url.ParsedParameters;
import com.mastfrog.url.Protocols;
import com.mastfrog.url.URL;
import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Entry point for creating web socket clients - use one of the
 * <code>create()</code> methods to create an instance.  For tests,
 * simply set up callbacks and call one of the <code>await()</code>
 * methods in the main test methods;  in your callbacks, use the usual
 * assertions - any failure will trigger connection shutdown and cause
 * the assertion error to be rethrown in the main thread.
 * <p>
 * For use in non test environments, simply set up a
 * {@link WebsocketErrorHandler} to handle exceptions and decide
 * whether to abort the connection or not.
 * <p>
 * This library also comes with a <code>test-jar</code> maven artifact
 * for use with the <a href="https://github.com/timboudreau/giulius-tests">Giulius
 * Tests</a> JUnit+Guice test runner, making it trivial to create tests
 * which start a server and then make websocket connections to it.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(WebSocketClientsImpl.class)
public abstract class Blather {

    /**
     * Create a client for the specified host and port, specifying ssl.
     *
     * @param host The host
     * @param port The port
     * @param ssl If true, use wss not ws protocol
     * @return this
     */
    public abstract WebsocketHostClient client(String host, int port, boolean ssl);

    /**
     * Create a client for the specified host and port, using plain HTTP.
     *
     * @param host The host
     * @param port The port
     * @return this
     */
    public final WebsocketHostClient client(String host, int port) {
        return client(host, port, false);
    }

    /**
     * Create a request to the passed URL.
     *
     * @param url The url
     * @return A request - attach a message handler to execute it.
     */
    public final WebsocketClientRequest client(URL url) {
        if (!notNull("u", url).isValid()) {
            url.getProblems().throwIfFatalPresent();
        }
        if (url.getProtocol() != Protocols.WS && url.getProtocol() != Protocols.WSS) {
            throw new IllegalArgumentException("Only ws:// and wss:// supported");
        }
        String host = url.getHost().toString();
        int port = url.getPort().intValue();

        WebsocketHostClient client = client(host, port, url.getProtocol().isSecure());

        WebsocketClientRequest result = client.request(url.getPath().toString());

        Parameters p = url.getParameters();
        if (p != null) {
            ParsedParameters parsed = p.toParsedParameters();
            for (ParametersElement el : parsed.getElements()) {
                result.addUrlQueryPair(el.getKey(), el.getValue());
            }
        }
        return result;
    }

    /**
     * Create a request to the passed URL.
     *
     * @param url The url
     * @return A request - attach a message handler to execute it.
     */
    public final WebsocketClientRequest client(String url) {
        return client(URL.parse(url));
    }

    /**
     * Create a client factory with a vanilla ObjectMapper, which will use a
     * runtime shutdown hook to close connections and thread pools.
     *
     * @return this
     */
    public static Blather create() {
        return new WebSocketClientsImpl(shutdownHookRegistry(), new ObjectMapper());
    }

    /**
     * Create a client factory with the passed ObjectMapper, which will use a
     * runtime shutdown hook to close connections and thread pools.
     *
     * @param mapper A Jackon ObjectMapper for json construction
     * @return this
     */
    public static Blather create(ObjectMapper mapper) {
        return new WebSocketClientsImpl(shutdownHookRegistry(), mapper);
    }
}

