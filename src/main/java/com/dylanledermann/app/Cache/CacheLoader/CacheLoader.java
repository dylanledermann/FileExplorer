package com.dylanledermann.app.Cache.CacheLoader;

public interface CacheLoader<K, V> {
    V load(K key) throws Exception;
}