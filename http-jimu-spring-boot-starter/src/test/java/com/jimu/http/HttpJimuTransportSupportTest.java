package com.jimu.http;

import com.jimu.http.engine.model.ExecuteDetail;
import com.jimu.http.engine.support.HttpJimuTransportSupport;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuPool;
import com.jimu.http.service.HttpJimuPoolService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpJimuTransportSupportTest {

    private HttpServer server;
    private String baseUrl;
    private AtomicReference<String> capturedMethod;
    private AtomicReference<String> capturedBody;
    private AtomicReference<String> capturedContentType;

    @BeforeEach
    void startServer() throws Exception {
        capturedMethod = new AtomicReference<>("");
        capturedBody = new AtomicReference<>("");
        capturedContentType = new AtomicReference<>("");

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", new EchoHandler(capturedMethod, capturedBody, capturedContentType));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldMergeQueryParams() {
        HttpJimuTransportSupport support = new HttpJimuTransportSupport(mock(HttpJimuPoolService.class));
        String merged = support.mergeUrlQueryParams(baseUrl + "/echo?a=1", Map.of("b", "x y"));
        assertTrue(merged.contains("a=1"));
        assertTrue(merged.contains("b=x+y"));
    }

    @Test
    void shouldFallbackWhenUrlInvalid() {
        HttpJimuTransportSupport support = new HttpJimuTransportSupport(mock(HttpJimuPoolService.class));
        String merged = support.mergeUrlQueryParams("::bad-url", Map.of("a", "1"));
        assertTrue(merged.contains("a=1"));
    }

    @Test
    void shouldSendRawPostRequest() {
        HttpJimuTransportSupport support = new HttpJimuTransportSupport(mock(HttpJimuPoolService.class));
        HttpJimuConfig config = baseConfig("POST");
        config.setBodyType("raw");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");

        ExecuteDetail detail = support.sendRequestWithDetail(config, "POST", baseUrl + "/echo", headers, Map.of("a", "1"));

        assertEquals("POST", capturedMethod.get());
        assertTrue(capturedBody.get().contains("\"a\":\"1\""));
        assertTrue(capturedContentType.get().contains("application/json"));
        assertEquals(200, detail.getResponseStatus());
        assertNotNull(detail.getDurationMs());
    }

    @Test
    void shouldSendFormUrlEncodedRequest() {
        HttpJimuTransportSupport support = new HttpJimuTransportSupport(mock(HttpJimuPoolService.class));
        HttpJimuConfig config = baseConfig("POST");
        config.setBodyType("x-www-form-urlencoded");
        ExecuteDetail detail = support.sendRequestWithDetail(config, "POST", baseUrl + "/echo", new LinkedHashMap<>(), Map.of("k", "v"));
        assertEquals("POST", capturedMethod.get());
        assertTrue(capturedBody.get().contains("k=v"));
        assertTrue(capturedContentType.get().contains("application/x-www-form-urlencoded"));
        assertEquals(200, detail.getResponseStatus());
    }

    @Test
    void shouldSendGetWithoutBody() {
        HttpJimuTransportSupport support = new HttpJimuTransportSupport(mock(HttpJimuPoolService.class));
        HttpJimuConfig config = baseConfig("GET");
        ExecuteDetail detail = support.sendRequestWithDetail(config, "GET", baseUrl + "/echo", new LinkedHashMap<>(), null);
        assertEquals("GET", capturedMethod.get());
        assertEquals("", capturedBody.get());
        assertEquals(200, detail.getResponseStatus());
    }

    @Test
    void shouldReuseAndEvictPoolClient() {
        HttpJimuPoolService poolService = mock(HttpJimuPoolService.class);
        HttpJimuPool pool = new HttpJimuPool();
        pool.setId("pool1");
        pool.setMaxIdleConnections(2);
        pool.setKeepAliveDuration(1000L);
        pool.setConnectTimeout(2000);
        pool.setReadTimeout(2000);
        pool.setWriteTimeout(2000);
        when(poolService.getById("pool1")).thenReturn(pool);

        HttpJimuTransportSupport support = new HttpJimuTransportSupport(poolService);
        HttpJimuConfig config = baseConfig("GET");
        config.setPoolId("pool1");

        support.sendRequestWithDetail(config, "GET", baseUrl + "/echo", new LinkedHashMap<>(), null);
        support.sendRequestWithDetail(config, "GET", baseUrl + "/echo", new LinkedHashMap<>(), null);
        verify(poolService, times(1)).getById("pool1");

        support.evictClientPool("pool1");
        support.sendRequestWithDetail(config, "GET", baseUrl + "/echo", new LinkedHashMap<>(), null);
        verify(poolService, times(2)).getById("pool1");
    }

    @Test
    void shouldThrowWhenRequestFails() {
        HttpJimuTransportSupport support = new HttpJimuTransportSupport(mock(HttpJimuPoolService.class));
        HttpJimuConfig config = baseConfig("GET");
        boolean thrown = false;
        try {
            support.sendRequestWithDetail(config, "GET", "http://127.0.0.1:1/echo", new LinkedHashMap<>(), null);
        } catch (RuntimeException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("HTTP Request failed"));
        }
        assertTrue(thrown);
    }

    private HttpJimuConfig baseConfig(String method) {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setHttpId("h1");
        config.setMethod(method);
        config.setBodyType("raw");
        return config;
    }

    private static class EchoHandler implements HttpHandler {
        private final AtomicReference<String> method;
        private final AtomicReference<String> body;
        private final AtomicReference<String> contentType;

        private EchoHandler(AtomicReference<String> method, AtomicReference<String> body, AtomicReference<String> contentType) {
            this.method = method;
            this.body = body;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(readBody(exchange.getRequestBody()));
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }

        private String readBody(InputStream is) throws IOException {
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}

