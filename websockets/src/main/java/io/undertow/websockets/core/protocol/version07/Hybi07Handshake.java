/*
 * Copyright 2012 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.websockets.core.protocol.version07;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketUtils;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.Handshake;
import org.xnio.IoUtils;

/**
 * The handshaking protocol implementation for Hybi-07.
 *
 * @author Mike Brock
 */
public class Hybi07Handshake extends Handshake {
    protected final boolean allowExtensions;

    protected Hybi07Handshake(final WebSocketVersion version, final Set<String> subprotocols, boolean allowExtensions) {
        super(version, "SHA1", "258EAFA5-E914-47DA-95CA-C5AB0DC85B11", subprotocols);
        this.allowExtensions = allowExtensions;
    }

    public Hybi07Handshake(final Set<String> subprotocols, boolean allowExtensions) {
        this(WebSocketVersion.V07, subprotocols, allowExtensions);
    }

    public Hybi07Handshake() {
        this(WebSocketVersion.V07, Collections.<String>emptySet(), false);
    }

    @Override
    public boolean matches(final HttpServerExchange exchange) {
        if (exchange.getRequestHeaders().contains(Headers.SEC_WEB_SOCKET_KEY) &&
                exchange.getRequestHeaders().contains(Headers.SEC_WEB_SOCKET_VERSION)) {
            return exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_VERSION)
                    .equals(getVersion().toHttpHeaderValue());
        }
        return false;
    }

    @Override
    public void handshake(final HttpServerExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_ORIGIN);
        if (origin != null) {
            exchange.getResponseHeaders().put(Headers.SEC_WEB_SOCKET_ORIGIN, origin);
        }
        String protocol = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_PROTOCOL);
        if (protocol != null) {
            exchange.getResponseHeaders().put(Headers.SEC_WEB_SOCKET_PROTOCOL, protocol);
        }
        exchange.getResponseHeaders().put(Headers.SEC_WEB_SOCKET_LOCATION, getWebSocketLocation(exchange));

        final String key = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_KEY);
        try {
            final String solution = solve(key);
            exchange.getResponseHeaders().put(Headers.SEC_WEB_SOCKET_ACCEPT, solution);
            performUpgrade(exchange);
        } catch (NoSuchAlgorithmException e) {
            IoUtils.safeClose(exchange.getConnection());
            exchange.endExchange();
            return;
        }

    }

    protected final String solve(final String nonceBase64) throws NoSuchAlgorithmException {
        final String concat = nonceBase64.trim() + getMagicNumber();
        final MessageDigest digest = MessageDigest.getInstance(getHashAlgorithm());
        digest.update(concat.getBytes(WebSocketUtils.UTF_8));
        final String result = Base64.encodeBytes(digest.digest()).trim();
        return result;
    }

    @Override
    public WebSocketChannel createChannel(final HttpServerExchange exchange) {
        return new WebSocket07Channel(exchange.getConnection().getChannel(), exchange.getConnection().getBufferPool(), getWebSocketLocation(exchange), subprotocols, allowExtensions);
    }
}
