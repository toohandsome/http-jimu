package com.jimu.http;

import com.jimu.http.cache.MemoryJimuCacheProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemoryJimuCacheProviderTest {

    @Test
    void shouldHonorPerEntryTtlWithinSameNamespace() throws Exception {
        MemoryJimuCacheProvider provider = new MemoryJimuCacheProvider();

        provider.put("ns", "short", "a", 20);
        provider.put("ns", "long", "b", 200);

        Thread.sleep(80);

        assertNull(provider.get("ns", "short", String.class));
        assertEquals("b", provider.get("ns", "long", String.class));
    }

    @Test
    void shouldKeepEntryWhenTtlIsNonPositive() throws Exception {
        MemoryJimuCacheProvider provider = new MemoryJimuCacheProvider();

        provider.put("ns", "persistent", "v", 0);

        Thread.sleep(50);

        assertEquals("v", provider.get("ns", "persistent", String.class));
    }
}
