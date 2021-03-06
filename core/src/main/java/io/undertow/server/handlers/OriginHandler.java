/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * A handler for the HTTP Origin header.
 *
 * @author Stuart Douglas
 */
public class OriginHandler implements HttpHandler {

    private volatile HttpHandler originFailedHandler = ResponseCodeHandler.HANDLE_403;
    private volatile Set<String> allowedOrigins = new HashSet<String>();
    private volatile boolean requireAllOrigins = true;
    private volatile boolean requireOriginHeader = true;
    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;


    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final List<String> origin = exchange.getRequestHeaders().get(Headers.ORIGIN);
        if (origin == null) {
            if (requireOriginHeader) {
                //TODO: Is 403 (Forbidden) the best response code
                if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    UndertowLogger.REQUEST_LOGGER.debugf("Refusing request for %s due to lack of Origin: header", exchange.getRequestPath());
                }
                HttpHandlers.executeHandler(originFailedHandler, exchange);
                return;
            }
        } else {
            boolean found = false;
            final boolean requireAllOrigins = this.requireAllOrigins;
            for (final String header : origin) {
                if (allowedOrigins.contains(header)) {
                    found = true;
                    if (!requireAllOrigins) {
                        break;
                    }
                } else if (requireAllOrigins) {
                    if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                        UndertowLogger.REQUEST_LOGGER.debugf("Refusing request for %s due to Origin %s not being in the allowed origins list", exchange.getRequestPath(), header);
                    }
                    HttpHandlers.executeHandler(originFailedHandler, exchange);
                    return;
                }
            }
            if (!found) {
                if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    UndertowLogger.REQUEST_LOGGER.debugf("Refusing request for %s as none of the specified origins %s were in the allowed origins list", exchange.getRequestPath(), origin);
                }
                HttpHandlers.executeHandler(originFailedHandler, exchange);
                return;
            }
        }
        HttpHandlers.executeHandler(next, exchange);
    }

    public synchronized void addAllowedOrigin(final String origin) {
        final Set<String> allowedOrigins = new HashSet<String>(this.allowedOrigins);
        allowedOrigins.add(origin);
        this.allowedOrigins = Collections.unmodifiableSet(allowedOrigins);
    }

    public synchronized void addAllowedOrigins(final Collection<String> origins) {
        final Set<String> allowedOrigins = new HashSet<String>(this.allowedOrigins);
        allowedOrigins.addAll(origins);
        this.allowedOrigins = Collections.unmodifiableSet(allowedOrigins);
    }

    public synchronized void addAllowedOrigins(final String... origins) {
        final Set<String> allowedOrigins = new HashSet<String>(this.allowedOrigins);
        allowedOrigins.addAll(Arrays.asList(origins));
        this.allowedOrigins = Collections.unmodifiableSet(allowedOrigins);
    }

    public synchronized Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public synchronized void clearAllowedOrigins() {
        this.allowedOrigins = Collections.emptySet();
    }

    public boolean isRequireAllOrigins() {
        return requireAllOrigins;
    }

    public void setRequireAllOrigins(final boolean requireAllOrigins) {
        this.requireAllOrigins = requireAllOrigins;
    }

    public boolean isRequireOriginHeader() {
        return requireOriginHeader;
    }

    public void setRequireOriginHeader(final boolean requireOriginHeader) {
        this.requireOriginHeader = requireOriginHeader;
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

    public HttpHandler getOriginFailedHandler() {
        return originFailedHandler;
    }

    public void setOriginFailedHandler(HttpHandler originFailedHandler) {
        HttpHandlers.handlerNotNull(originFailedHandler);
        this.originFailedHandler = originFailedHandler;
    }
}
