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

package io.undertow.test.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.undertow.io.UndertowOutputStream;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpContinueHandler;
import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.streams.ChannelInputStream;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class HttpContinueTestCase {

    private static volatile boolean accept = false;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        final HttpContinueHandler handler = new HttpContinueHandler(blockingHandler) {
            @Override
            protected boolean acceptRequest(final HttpServerExchange exchange) {
                return accept;
            }
        };
        DefaultServer.setRootHandler(handler);
        blockingHandler.setRootHandler(new BlockingHttpHandler() {
            @Override
            public void handleBlockingRequest(final HttpServerExchange exchange) {
                try {
                    byte[] buffer = new byte[1024];
                    final ByteArrayOutputStream b = new ByteArrayOutputStream();
                    int r = 0;
                    final OutputStream outputStream = new UndertowOutputStream(exchange);
                    final InputStream inputStream = new ChannelInputStream(exchange.getRequestChannel());
                    while ((r = inputStream.read(buffer)) > 0) {
                        b.write(buffer, 0, r);
                    }
                    outputStream.write(b.toByteArray());
                    outputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testHttpContinueRejected() throws IOException {
        accept = false;
        String message = "My HTTP Request!";
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter("http.protocol.wait-for-continue", Integer.MAX_VALUE);

        TestHttpClient client = new TestHttpClient();
        client.setParams(httpParams);
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            HttpResponse result = client.execute(post);
            Assert.assertEquals(417, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testHttpContinueAccepted() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter("http.protocol.wait-for-continue", Integer.MAX_VALUE);

        TestHttpClient client = new TestHttpClient();
        client.setParams(httpParams);
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
