package dev.memoize.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache with NO synchronization. Use only when the method is called from a
 * single thread (e.g., Android main thread only). Avoids all synchronization overhead.
 *
 * @param <K> the cache key type
 * @param <V> the cached value type
 */
public final class UnsynchronizedLruMemoCache<K, V> implements MemoCache<K, V> {

    private final LinkedHashMap<K, V> map;
    private final int maxSize;
    private final CacheStats stats;

    public UnsynchronizedLruMemoCache(int maxSize, CacheStats stats) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        }
        this.maxSize = maxSize;
        this.stats = stats;
        this.map = new LinkedHashMap<K, V>(
                Math.min(maxSize, 16), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean shouldRemove = size() > UnsynchronizedLruMemoCache.this.maxSize;
                if (shouldRemove && UnsynchronizedLruMemoCache.this.stats != null) {
                    UnsynchronizedLruMemoCache.this.stats.recordEviction();
                }
                return shouldRemove;
            }
        };
    }

    public UnsynchronizedLruMemoCache(int maxSize) {
        this(maxSize, null);
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public void put(K key, V value) {
        if (value == null) {
            map.remove(key);
            return;
        }
        map.put(key, value);
    }

    @Override
    public void remove(K key) {
        map.remove(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public int size() {
        return map.size();
    }
}
