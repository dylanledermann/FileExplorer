package Cache;

import java.io.Closeable;
import java.util.Optional;

public interface Cache<K, V> extends Closeable {
    Optional<V> get(K key);

    void put(K key, V value);

    void invalidate(K key);

    void clear();

    CacheStats stats();
}