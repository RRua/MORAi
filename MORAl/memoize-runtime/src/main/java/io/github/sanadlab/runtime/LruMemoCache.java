package io.github.sanadlab.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU (Least Recently Used) cache implementation backed by LinkedHashMap.
 * Thread-safe via synchronized methods.
 *
 * @param <K> the cache key type
 * @param <V> the cached value type
 */
public final class LruMemoCache<K, V> implements MemoCache<K, V> {

    private final LinkedHashMap<K, V> map;
    private final int maxSize;
    private final CacheStats stats;

    public LruMemoCache(int maxSize, CacheStats stats) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        }
        this.maxSize = maxSize;
        this.stats = stats;
        this.map = new LinkedHashMap<K, V>(
                Math.min(maxSize, 16), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean shouldRemove = size() > LruMemoCache.this.maxSize;
                if (shouldRemove && LruMemoCache.this.stats != null) {
                    LruMemoCache.this.stats.recordEviction();
                }
                return shouldRemove;
            }
        };
    }

    public LruMemoCache(int maxSize) {
        this(maxSize, null);
    }

    @Override
    public synchronized V get(K key) {
        return map.get(key);
    }

    @Override
    public synchronized void put(K key, V value) {
        if (value == null) {
            map.remove(key);
            return;
        }
        map.put(key, value);
    }

    @Override
    public synchronized void remove(K key) {
        map.remove(key);
    }

    @Override
    public synchronized void clear() {
        map.clear();
    }

    @Override
    public synchronized int size() {
        return map.size();
    }

    public int getMaxSize() {
        return maxSize;
    }
}
