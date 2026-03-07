package com.jimu.http;

import com.jimu.http.controller.HttpJimuExposedRequestResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpJimuExposedRequestResolverTest {

    @Test
    void shouldResolveAutoRequestData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/orders/100");
        request.setQueryString("tenant=t1");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("{\"orderNo\":\"A100\",\"user\":{\"id\":\"U1\"}}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Token", "abc");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "100"));

        Map<String, Object> input = HttpJimuExposedRequestResolver.resolve(request, "AUTO", "[]");

        assertEquals("t1", input.get("tenant"));
        assertEquals("100", input.get("id"));
        assertEquals("A100", input.get("orderNo"));
        assertEquals("U1", input.get("body.user.id"));
        assertEquals("100", input.get("path.id"));
        assertEquals("t1", input.get("query.tenant"));
        assertEquals("abc", input.get("header.X-Token"));
        assertInstanceOf(Map.class, input.get("body"));
        assertInstanceOf(Map.class, input.get(HttpJimuExposedRequestResolver.INJECT_QUERY_KEY));
        assertInstanceOf(Map.class, input.get(HttpJimuExposedRequestResolver.INJECT_HEADER_KEY));
        assertInstanceOf(Map.class, input.get(HttpJimuExposedRequestResolver.INJECT_BODY_KEY));
    }

    @Test
    void shouldResolveCustomMappings() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/orders/100");
        request.setQueryString("orderId=A100");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("{\"payload\":{\"tenant\":\"t1\"}}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Token", "abc");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "100"));

        String mapping = """
                [
                  {"sourceType":"QUERY","targetType":"BODY"},
                  {"sourceType":"HEADER","targetType":"HEADER"},
                  {"sourceType":"PATH","targetType":"QUERY"}
                ]
                """;

        Map<String, Object> input = HttpJimuExposedRequestResolver.resolve(request, "CUSTOM", mapping);

        assertInstanceOf(Map.class, input.get(HttpJimuExposedRequestResolver.INJECT_BODY_KEY));
        assertInstanceOf(Map.class, input.get(HttpJimuExposedRequestResolver.INJECT_HEADER_KEY));
        assertInstanceOf(Map.class, input.get(HttpJimuExposedRequestResolver.INJECT_QUERY_KEY));
        assertEquals("A100", ((Map<?, ?>) input.get(HttpJimuExposedRequestResolver.INJECT_BODY_KEY)).get("orderId"));
        assertEquals("abc", ((Map<?, ?>) input.get(HttpJimuExposedRequestResolver.INJECT_HEADER_KEY)).get("X-Token"));
        assertEquals("100", ((Map<?, ?>) input.get(HttpJimuExposedRequestResolver.INJECT_QUERY_KEY)).get("id"));
    }

    @Test
    void shouldResolveRawToRawOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/raw");
        request.setContentType("text/plain");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("demo-raw".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> input = HttpJimuExposedRequestResolver.resolve(request, "CUSTOM", """
                [
                  {"sourceType":"RAW","targetType":"RAW"}
                ]
                """);

        assertEquals("demo-raw", input.get(HttpJimuExposedRequestResolver.INJECT_RAW_KEY));
    }

    @Test
    void shouldRejectRawMappedToNonRawTarget() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/raw");
        request.setContentType("text/plain");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("demo-raw".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () ->
                HttpJimuExposedRequestResolver.resolve(request, "CUSTOM", """
                        [
                          {"sourceType":"RAW","targetType":"BODY"}
                        ]
                        """));
    }
}
