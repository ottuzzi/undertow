/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package io.undertow.websockets.jsr;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

import javax.websocket.server.HandshakeRequest;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * {@link HandshakeRequest} which wraps a {@link HttpServerExchange} to act on it.
 * Once the processing of it is done {@link #update()} must be called to persist any changes
 * made.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class ExchangeHandshakeRequest implements HandshakeRequest {
    private final HttpServerExchange exchange;
    private Map<String, List<String>> headers;
    public ExchangeHandshakeRequest(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }
    @Override
    public Map<String, List<String>> getHeaders() {
        if (headers == null) {
            headers = new HashMap<String, List<String>>();
            HeaderMap reqHeaders = exchange.getRequestHeaders();
            for (HttpString name: reqHeaders.getHeaderNames()) {
                headers.put(name.toString(), new LinkedList<String>(reqHeaders.get(name)));
            }
        }
        return headers;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public URI getRequestURI() {
        return URI.create(exchange.getRequestURI());
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public Object getSession() {
        // TODO: What todo ?
        return null;
    }

    @Override
    public Map<String, List<String>> getParameterMap() {
        return Collections.emptyMap();
    }

    @Override
    public String getQueryString() {
        return exchange.getQueryString();
    }

    /**
     * Persist all changes and update the wrapped {@link HttpServerExchange}.
     */
    void update() {
        if (headers != null) {
            HeaderMap map = exchange.getRequestHeaders();
            map.clear();
            for (Map.Entry<String, List<String>> header: headers.entrySet()) {
                map.addAll(HttpString.tryFromString(header.getKey()), header.getValue());
            }
        }
    }
}
