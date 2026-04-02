package com.dylanledermann.app.Cache;

record CacheEntry<V>(
        V value,
        long expiresAt,
        long createdAt,
        long lastAccessedAt) {
    boolean isExpired() {
        return System.nanoTime() > expiresAt;
    }

    CacheEntry<V> withAccessed() {
        return new CacheEntry<>(value, expiresAt, createdAt, System.nanoTime());
    }

    static <V> CacheEntry<V> of(V value, long ttlNano) {
        long now = System.nanoTime();
        return new CacheEntry<>(value, now + ttlNano, now, now);
    }
}
