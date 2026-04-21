package dev.memoize.runtime;

/**
 * Cache interface for memoization storage.
 *
 * @param <K> the cache key type
 * @param <V> the cached value type
 */
public interface MemoCache<K, V> {

    /**
     * Returns the cached value for the given key, or null if not present.
     */
    V get(K key);

    /**
     * Stores a value in the cache.
     */
    void put(K key, V value);

    /**
     * Removes a single entry by key. No-op if absent.
     */
    void remove(K key);

    /**
     * Removes all entries from the cache.
     */
    void clear();

    /**
     * Returns the number of entries currently in the cache.
     */
    int size();
}
