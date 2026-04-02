package com.dylanledermann.app.Cache;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.dylanledermann.app.Cache.CacheLoader.CacheLoadException;
import com.dylanledermann.app.Cache.CacheLoader.CacheLoader;

import java.time.Duration;

public final class GenericCache<K, V> implements Cache<K, V> {
    // Class vars
    private final ConcurrentHashMap<K, CacheEntry<V>> store;
    private final int maxSize;
    private final long ttlNano;
    private final CacheLoader<K, V> loader;

    // Stats
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    // Sweeper
    private final ScheduledExecutorService sweeper;

    public GenericCache(Builder<K, V> builder) {
        this.maxSize = builder.maxSize;
        this.ttlNano = builder.ttl.toNanos();
        this.loader = builder.loader;
        this.store = new ConcurrentHashMap<>(Math.min(maxSize, 1024));
        this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-sweeper");
            t.setDaemon(true);
            return t;
        });
        long sweepMillis = Math.max(builder.sweepInterval.toMillis(), 1);
        this.sweeper.scheduleAtFixedRate(
                this::sweep,
                sweepMillis,
                sweepMillis,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws IOException {
        sweeper.shutdownNow();
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key must not be null");

        CacheEntry<V> entry = store.get(key);

        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            store.replace(key, entry, entry.withAccessed());
            return Optional.of(entry.value());
        }

        if (entry != null)
            store.remove(key, entry);
        misses.incrementAndGet();

        if (loader == null)
            return Optional.empty();

        return Optional.ofNullable(loadAndCache(key));
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        evictIfOversize();
        store.put(key, CacheEntry.of(value, ttlNano));
    }

    @Override
    public void invalidate(K key) {
        Objects.requireNonNull(key, "key must not be null");
        store.remove(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), evictions.get());
    }

    private V loadAndCache(K key) {
        CacheEntry<V> loaded = store.computeIfAbsent(key, k -> {
            try {
                V value = loader.load(k);
                return value != null ? CacheEntry.of(value, this.ttlNano) : null;
            } catch (Exception e) {
                throw new CacheLoadException("Failed to load key: " + k, e);
            }
        });

        return loaded != null ? loaded.value() : null;
    }

    private void evictIfOversize() {
        if (store.size() < maxSize)
            return;

        store.entrySet()
                .stream()
                .min(Comparator.comparingLong(e -> e.getValue().lastAccessedAt()))
                .map(Map.Entry::getKey)
                .ifPresent(k -> {
                    store.remove(k);
                    evictions.incrementAndGet();
                });
    }

    private void sweep() {
        store.entrySet()
                .removeIf(e -> {
                    boolean expired = e.getValue().isExpired();
                    if (expired)
                        evictions.incrementAndGet();
                    return expired;
                });
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    public static final class Builder<K, V> {
        private int maxSize = 1000;
        private Duration ttl = Duration.ofMinutes(5);
        private Duration sweepInterval = Duration.ofMinutes(1);
        private CacheLoader<K, V> loader = null;

        public Builder<K, V> maxSize(int maxSize) {
            if (maxSize <= 0)
                throw new IllegalArgumentException("maxSize must be positive");
            this.maxSize = maxSize;
            return this;
        }

        public Builder<K, V> ttl(Duration ttl) {
            if (ttl.isNegative() || ttl.isZero())
                throw new IllegalArgumentException("ttl must be positive and non-zero");
            this.ttl = ttl;
            return this;
        }

        public Builder<K, V> sweepInterval(Duration sweepInterval) {
            if (sweepInterval.isNegative() || sweepInterval.isZero())
                throw new IllegalArgumentException("sweepInterval must be positive and non-zero");
            this.sweepInterval = sweepInterval;
            return this;
        }

        public Builder<K, V> loader(CacheLoader<K, V> loader) {
            this.loader = loader;
            return this;
        }

        public GenericCache<K, V> build() {
            return new GenericCache<>(this);
        }
    }
}
