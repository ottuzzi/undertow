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

package io.undertow.websockets.core.protocol.version00;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.Handshake;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author Mike Brock
 */
public class Hybi00Handshake extends Handshake {
    private static final Pattern PATTERN = Pattern.compile("[^0-9]");

    public Hybi00Handshake() {
        super(WebSocketVersion.V00, "MD5", null, Collections.<String>emptySet());
    }

    public Hybi00Handshake(final Set<String> subprotocols) {
        super(WebSocketVersion.V00, "MD5", null, subprotocols);
    }

    @Override
    public void handshake(final HttpServerExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_ORIGIN);
        if (origin != null) {
            exchange.getResponseHeaders().put(Headers.SEC_WEB_SOCKET_ORIGIN, origin);
        }

        exchange.getResponseHeaders().put(Headers.SEC_WEB_SOCKET_LOCATION, getWebSocketLocation(exchange));

        String protocol = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_PROTOCOL);
        if (protocol != null) {
            exchange.getResponseHeaders().put(Headers.SEC_WEB_SOCKET_PROTOCOL, protocol);
        }

        // Calculate the answer of the challenge.
        final String key1 = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_KEY1);
        final String key2 = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_KEY2);
        final byte[] key3 = new byte[8];
        final ByteBuffer buffer = ByteBuffer.wrap(key3);

        final StreamSourceChannel channel = exchange.getRequestChannel();
        int r, read = 0;
        do {
            try {
                r = channel.read(buffer);
                read += r;

                if (r == -1) {
                    IoUtils.safeClose(exchange.getConnection());
                    exchange.endExchange();
                    return;
                }
            } catch (IOException e) {
                IoUtils.safeClose(exchange.getConnection());
                exchange.endExchange();
                return;
            }
        } while (r > 0 && read < 8);

        if (read != 8) {
            final int soFar = read;
            channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                @Override
                public void handleEvent(final StreamSourceChannel channel) {
                    int r, read = soFar;
                    do {
                        try {
                            r = channel.read(buffer);
                            read += r;
                            if (r == -1) {
                                IoUtils.safeClose(exchange.getConnection());
                                exchange.endExchange();
                                return;
                            }
                        } catch (IOException e) {
                            IoUtils.safeClose(exchange.getConnection());
                            exchange.endExchange();
                            return;
                        }
                    } while (r > 0 && read != 8);
                    if (read == 8) {
                        channel.suspendReads();
                        final byte[] solution = solve(getHashAlgorithm(), key1, key2, key3);
                        performUpgrade(exchange, solution);
                    }

                }
            });
        } else {
            channel.suspendReads();
            final byte[] solution = solve(getHashAlgorithm(), key1, key2, key3);
            performUpgrade(exchange, solution);
        }
    }

    @Override
    public boolean matches(final HttpServerExchange exchange) {
        return exchange.getRequestHeaders().contains(Headers.SEC_WEB_SOCKET_KEY1) &&
                exchange.getRequestHeaders().contains(Headers.SEC_WEB_SOCKET_KEY2);
    }

    @Override
    public WebSocketChannel createChannel(final HttpServerExchange exchange) {
        return new WebSocket00Channel(exchange.getConnection().getChannel(), exchange.getConnection().getBufferPool(), getWebSocketLocation(exchange), subprotocols);
    }

    protected static byte[] solve(final String hashAlgorithm, String encodedKey1, String encodedKey2, byte[] key3) {
        return solve(hashAlgorithm, decodeKey(encodedKey1), decodeKey(encodedKey2), key3);
    }

    protected static byte[] solve(final String hashAlgorithm, long key1, long key2, byte[] key3) {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);

        buffer.putInt((int) key1);
        buffer.putInt((int) key2);
        buffer.put(key3);
        buffer.rewind();

        try {
            final MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            digest.update(buffer);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("error generating hash", e);
        }
    }

    protected static long decodeKey(final String encoded) {
        final int len = encoded.length();
        int numSpaces = 0;

        for (int i = 0; i < len; ++i) {
            if (encoded.charAt(i) == ' ') {
                ++numSpaces;
            }
        }
        final String digits = PATTERN.matcher(encoded).replaceAll("");
        final long product = Long.parseLong(digits);
        return product / numSpaces;
    }
}
