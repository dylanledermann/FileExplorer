package com.dylanledermann.app.Cache;

public record CacheStats(
        long hits,
        long misses,
        long evictions) {
    @Override
    public String toString() {
        return "CacheStats{hits=%d, misses=%d, evictions=%d, hitRate=%.1f%%}"
                .formatted(hits, misses, evictions, hits + misses == 0 ? 0.0 : (double) hits / (hits + misses) * 100);
    }
}