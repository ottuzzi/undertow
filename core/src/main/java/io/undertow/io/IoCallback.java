package io.undertow.io;

import java.io.IOException;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public interface IoCallback {

    void onComplete(final HttpServerExchange exchange, final Sender sender);

    void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception);

    IoCallback END_EXCHANGE = new DefaultIoCallback();

}
