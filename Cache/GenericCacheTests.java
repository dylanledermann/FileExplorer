package Cache;

import java.io.IOException;
import java.time.Duration;

public class GenericCacheTests {
    public static void main(String[] args) throws Exception {
        GenericCacheTests tests = new GenericCacheTests();
        tests.runAll();
    }

    private void runAll() throws Exception {
        testPutGetInvalidateClear();
        testTTLExpiration();
        testEvictionOnMaxSize();
        System.out.println("GenericCacheTests: all tests passed.");
    }

    private void testPutGetInvalidateClear() throws IOException {
        try (GenericCache<String, String> cache = GenericCache.<String, String>builder()
                .maxSize(3)
                .ttl(Duration.ofSeconds(5))
                .sweepInterval(Duration.ofSeconds(1))
                .build()) {
            cache.put("a", "1");
            assert cache.get("a").isPresent() : "Expected value present after put";
            cache.invalidate("a");
            assert cache.get("a").isEmpty() : "Expected value missing after invalidate";
            cache.put("b", "2");
            cache.clear();
            assert cache.get("b").isEmpty() : "Expected empty cache after clear";
        }
    }

    private void testTTLExpiration() throws Exception {
        try (GenericCache<String, String> cache = GenericCache.<String, String>builder()
                .maxSize(3)
                .ttl(Duration.ofMillis(100))
                .sweepInterval(Duration.ofMillis(50))
                .build()) {
            cache.put("a", "1");
            Thread.sleep(250);
            assert cache.get("a").isEmpty() : "Expected expired entry to be removed";
        }
    }

    private void testEvictionOnMaxSize() throws IOException {
        try (GenericCache<String, String> cache = GenericCache.<String, String>builder()
                .maxSize(2)
                .ttl(Duration.ofMinutes(1))
                .sweepInterval(Duration.ofSeconds(1))
                .build()) {
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3");
            assert cache.get("a").isEmpty() : "Expected least recently accessed entry to be evicted";
            assert cache.stats().evictions() >= 1 : "Expected at least one eviction";
        }
    }
}
