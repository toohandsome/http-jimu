package com.jimu.http;

import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.support.HttpJimuConfigSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpJimuConfigSupportTest {

    @Test
    void shouldNormalizeExposedApiConfig() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setExposeApi(true);
        config.setExposedPath("open/demo/");
        config.setExposedMethod("post");
        config.setExposedParamType("custom");

        String error = HttpJimuConfigSupport.validateAndNormalizeExposedApi(config);

        assertNull(error);
        assertEquals("/open/demo", config.getExposedPath());
        assertEquals("POST", config.getExposedMethod());
        assertEquals("CUSTOM", config.getExposedParamType());
    }

    @Test
    void shouldRejectInvalidExposedParamType() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setExposeApi(true);
        config.setExposedPath("/open/demo");
        config.setExposedMethod("GET");
        config.setExposedParamType("XML");

        String error = HttpJimuConfigSupport.validateAndNormalizeExposedApi(config);

        assertTrue(error.contains("Unsupported exposed param type"));
    }

    @Test
    void shouldDefaultExposedMethodToRealMethod() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setExposeApi(true);
        config.setMethod("put");
        config.setExposedPath("/open/demo");
        config.setExposedParamType("AUTO");

        String error = HttpJimuConfigSupport.validateAndNormalizeExposedApi(config);

        assertNull(error);
        assertEquals("PUT", config.getExposedMethod());
    }

    @Test
    void shouldKeepExposedConfigWhenApiIsDisabled() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setExposeApi(false);
        config.setExposedPath("/open/demo");
        config.setExposedMethod("post");
        config.setExposedParamType("custom");
        config.setExposedMappingConfig("[{\"sourceType\":\"QUERY\",\"targetType\":\"BODY\"}]");

        String error = HttpJimuConfigSupport.validateAndNormalizeExposedApi(config);

        assertNull(error);
        assertEquals("/open/demo", config.getExposedPath());
        assertEquals("POST", config.getExposedMethod());
        assertEquals("CUSTOM", config.getExposedParamType());
        assertEquals("[{\"sourceType\":\"QUERY\",\"targetType\":\"BODY\"}]", config.getExposedMappingConfig());
    }
}
