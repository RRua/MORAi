package dev.memoize.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FIFO (First-In-First-Out) cache. Backed by {@link LinkedHashMap} in
 * insertion-order mode, so hits do <b>not</b> re-link the entry. This makes
 * the hot path cheaper than {@link LruMemoCache} at the cost of ignoring
 * temporal locality: the eldest-inserted entry is evicted when the cache
 * overflows, regardless of how recently it was accessed.
 *
 * <p>Thread-safe via {@code synchronized} methods.
 */
public final class FifoMemoCache<K, V> implements MemoCache<K, V> {

    private final LinkedHashMap<K, V> map;
    private final int maxSize;
    private final CacheStats stats;

    public FifoMemoCache(int maxSize, CacheStats stats) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        }
        this.maxSize = maxSize;
        this.stats = stats;
        // accessOrder=false => insertion order; get() does not relink the node.
        this.map = new LinkedHashMap<K, V>(Math.min(maxSize, 16), 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean shouldRemove = size() > FifoMemoCache.this.maxSize;
                if (shouldRemove && FifoMemoCache.this.stats != null) {
                    FifoMemoCache.this.stats.recordEviction();
                }
                return shouldRemove;
            }
        };
    }

    public FifoMemoCache(int maxSize) {
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
