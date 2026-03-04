package com.jimu.http;

import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.support.HttpJimuConfigSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpJimuConfigSupportTest {

    @Test
    void shouldAcceptDisabledJobWithoutCron() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setEnableJob(false);
        config.setCronConfig("");
        assertNull(HttpJimuConfigSupport.validateCronConfig(config));
    }

    @Test
    void shouldRejectEnabledJobWithoutCron() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setEnableJob(true);
        config.setCronConfig("  ");
        assertNotNull(HttpJimuConfigSupport.validateCronConfig(config));
    }

    @Test
    void shouldRejectInvalidCronExpression() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setEnableJob(true);
        config.setCronConfig("invalid");
        assertNotNull(HttpJimuConfigSupport.validateCronConfig(config));
    }

    @Test
    void shouldApplyParamsConfigObjectDefaults() {
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "A001");

        Map<String, Object> context = HttpJimuConfigSupport.buildContext(input, """
                {
                  "merchantId":"M100",
                  "signType":"SHA256"
                }
                """);

        assertEquals("A001", context.get("orderId"));
        assertEquals("M100", context.get("merchantId"));
        assertEquals("SHA256", context.get("signType"));
    }

    @Test
    void shouldApplyParamsConfigArrayMappingAndDefault() {
        Map<String, Object> input = new HashMap<>();
        input.put("oid", "A002");

        Map<String, Object> context = HttpJimuConfigSupport.buildContext(input, """
                [
                  {"key":"orderId","source":"oid","required":true},
                  {"key":"channel","defaultValue":"WEB"}
                ]
                """);

        assertEquals("A002", context.get("orderId"));
        assertEquals("WEB", context.get("channel"));
    }

    @Test
    void shouldFailWhenRequiredParamMissing() {
        Map<String, Object> input = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () ->
                HttpJimuConfigSupport.buildContext(input, """
                        [
                          {"key":"orderId","required":true}
                        ]
                        """));
    }

    @Test
    void shouldSupportObjectRuleWithSourceAndRequired() {
        Map<String, Object> input = new HashMap<>();
        input.put("oid", "A003");

        Map<String, Object> context = HttpJimuConfigSupport.buildContext(input, """
                {
                  "orderId":{"source":"oid","required":true},
                  "channel":{"default":"APP"}
                }
                """);

        assertEquals("A003", context.get("orderId"));
        assertEquals("APP", context.get("channel"));
    }

    @Test
    void shouldThrowWhenParamsConfigIsNotJson() {
        Map<String, Object> input = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () -> HttpJimuConfigSupport.buildContext(input, "key=value"));
    }

    @Test
    void shouldKeepExistingValueWhenObjectDefaultProvided() {
        Map<String, Object> input = new HashMap<>();
        input.put("channel", "H5");

        Map<String, Object> context = HttpJimuConfigSupport.buildContext(input, """
                {
                  "channel":"APP"
                }
                """);

        assertEquals("H5", context.get("channel"));
    }

    @Test
    void shouldIgnoreInvalidArrayItemsAndEmptyKeys() {
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "A004");

        Map<String, Object> context = HttpJimuConfigSupport.buildContext(input, """
                [
                  1,
                  {"name":"", "required":false},
                  {"paramKey":"orderId"}
                ]
                """);

        assertEquals("A004", context.get("orderId"));
    }

    @Test
    void shouldReturnInputWhenParamsConfigBlank() {
        Map<String, Object> input = new HashMap<>();
        input.put("k", "v");
        Map<String, Object> context = HttpJimuConfigSupport.buildContext(input, "   ");
        assertEquals("v", context.get("k"));
    }

    @Test
    void shouldSupportRequireAliasAndDefaultAlias() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> context = HttpJimuConfigSupport.buildContext(input, """
                [
                  {"key":"channel","default":"MINI"},
                  {"key":"requiredKey","require":true,"defaultValue":"ok"}
                ]
                """);
        assertEquals("MINI", context.get("channel"));
        assertEquals("ok", context.get("requiredKey"));
    }
}
