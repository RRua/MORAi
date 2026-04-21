package dev.memoize.runtime;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap-based cache. Lock-free reads, bucket-level write locks.
 * No size limit — use when unbounded caching is acceptable or eviction is handled externally.
 *
 * @param <K> the cache key type
 * @param <V> the cached value type
 */
public final class ConcurrentMemoCache<K, V> implements MemoCache<K, V> {

    private final ConcurrentHashMap<K, V> map;

    public ConcurrentMemoCache() {
        this.map = new ConcurrentHashMap<>();
    }

    public ConcurrentMemoCache(int initialCapacity) {
        this.map = new ConcurrentHashMap<>(initialCapacity);
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
