package com.dylanledermann.app;

import java.io.IOException;
import java.time.Duration;

import com.dylanledermann.app.Cache.GenericCache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GenericCacheTests {

    @Test
    public void testPutGetInvalidateClear() throws IOException {
        try (GenericCache<String, String> cache = GenericCache.<String, String>builder()
                .maxSize(3)
                .ttl(Duration.ofSeconds(5))
                .sweepInterval(Duration.ofSeconds(1))
                .build()) {
            cache.put("a", "1");
            assertTrue(cache.get("a").isPresent(), "Expected value present after put");
            cache.invalidate("a");
            assertTrue(cache.get("a").isEmpty(), "Expected value missing after invalidate");
            cache.put("b", "2");
            cache.clear();
            assertTrue(cache.get("b").isEmpty(), "Expected empty cache after clear");
        }
    }

    @Test
    public void testTTLExpiration() throws Exception {
        try (GenericCache<String, String> cache = GenericCache.<String, String>builder()
                .maxSize(3)
                .ttl(Duration.ofMillis(100))
                .sweepInterval(Duration.ofMillis(50))
                .build()) {
            cache.put("a", "1");
            Thread.sleep(250);
            assertTrue(cache.get("a").isEmpty(), "Expected expired entry to be removed");
        }
    }

    @Test
    public void testEvictionOnMaxSize() throws IOException {
        try (GenericCache<String, String> cache = GenericCache.<String, String>builder()
                .maxSize(2)
                .ttl(Duration.ofMinutes(1))
                .sweepInterval(Duration.ofSeconds(1))
                .build()) {
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3");
            assertTrue(cache.get("a").isEmpty(), "Expected least recently accessed entry to be evicted");
            assertTrue(cache.stats().evictions() >= 1, "Expected at least one eviction");
        }
    }
}
