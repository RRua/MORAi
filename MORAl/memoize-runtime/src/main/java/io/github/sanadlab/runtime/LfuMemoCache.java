package io.github.sanadlab.runtime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Least-Frequently-Used cache with O(1) get / put / evict.
 *
 * <p>Implementation is the classic "frequency buckets" layout:
 *
 * <ul>
 *   <li>{@code values} -- key to cached value</li>
 *   <li>{@code freqs}  -- key to current frequency counter</li>
 *   <li>{@code buckets} -- frequency to insertion-ordered set of keys at that
 *       frequency. {@link LinkedHashSet} gives O(1) membership mutation AND
 *       preserves insertion order so ties are broken by oldest-first.</li>
 *   <li>{@code minFreq} -- running minimum frequency, so {@link #evict()} can
 *       find a victim in O(1) without scanning.</li>
 * </ul>
 *
 * <p>Every read ({@link #get}) bumps the key's frequency and moves it to the
 * next bucket. Every miss evicts the oldest entry in the {@code minFreq}
 * bucket and admits the newcomer at frequency 1, resetting {@code minFreq} to
 * 1. This matches the canonical O(1) LFU design from Pin et al. 2010.
 *
 * <p>Use this when the workload is <b>skewed</b>: a small set of arguments
 * dominates the call distribution (Zipfian tag lookups, autocomplete prefixes,
 * route resolution, etc.). For uniform access patterns, LRU / FIFO are
 * cheaper and give similar hit rates.
 *
 * <p>Thread-safe via {@code synchronized} methods.
 */
public final class LfuMemoCache<K, V> implements MemoCache<K, V> {

    private final HashMap<K, V> values;
    private final HashMap<K, Integer> freqs;
    private final HashMap<Integer, LinkedHashSet<K>> buckets;
    private final int maxSize;
    private final CacheStats stats;
    private int minFreq;

    public LfuMemoCache(int maxSize, CacheStats stats) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        }
        this.maxSize = maxSize;
        this.stats = stats;
        int initial = Math.min(maxSize, 16);
        this.values = new HashMap<>(initial);
        this.freqs = new HashMap<>(initial);
        this.buckets = new HashMap<>();
        this.minFreq = 0;
    }

    public LfuMemoCache(int maxSize) {
        this(maxSize, null);
    }

    @Override
    public synchronized V get(K key) {
        V v = values.get(key);
        if (v == null) return null;
        touch(key);
        return v;
    }

    @Override
    public synchronized void put(K key, V value) {
        // put(null) is used by MemoDispatcher to drop TTL-expired entries.
        if (value == null) {
            removeInternal(key);
            return;
        }
        if (values.containsKey(key)) {
            values.put(key, value);
            touch(key);
            return;
        }
        if (values.size() >= maxSize) {
            evict();
        }
        values.put(key, value);
        freqs.put(key, 1);
        buckets.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    @Override
    public synchronized void remove(K key) {
        removeInternal(key);
    }

    @Override
    public synchronized void clear() {
        values.clear();
        freqs.clear();
        buckets.clear();
        minFreq = 0;
    }

    @Override
    public synchronized int size() {
        return values.size();
    }

    public int getMaxSize() {
        return maxSize;
    }

    // --- internals ---

    private void touch(K key) {
        int oldFreq = freqs.get(key);
        int newFreq = oldFreq + 1;
        freqs.put(key, newFreq);

        LinkedHashSet<K> oldBucket = buckets.get(oldFreq);
        oldBucket.remove(key);
        if (oldBucket.isEmpty()) {
            buckets.remove(oldFreq);
            if (oldFreq == minFreq) {
                // Only the owner of the min bucket bumps minFreq on empty.
                minFreq = newFreq;
            }
        }
        buckets.computeIfAbsent(newFreq, k -> new LinkedHashSet<>()).add(key);
    }

    private void evict() {
        LinkedHashSet<K> bucket = buckets.get(minFreq);
        if (bucket == null || bucket.isEmpty()) return;
        Iterator<K> it = bucket.iterator();
        K victim = it.next();
        it.remove();
        if (bucket.isEmpty()) {
            buckets.remove(minFreq);
        }
        values.remove(victim);
        freqs.remove(victim);
        if (stats != null) stats.recordEviction();
    }

    private void removeInternal(K key) {
        if (!values.containsKey(key)) return;
        Integer freq = freqs.remove(key);
        values.remove(key);
        if (freq != null) {
            LinkedHashSet<K> bucket = buckets.get(freq);
            if (bucket != null) {
                bucket.remove(key);
                if (bucket.isEmpty()) {
                    buckets.remove(freq);
                    if (freq == minFreq && values.isEmpty()) {
                        minFreq = 0;
                    }
                }
            }
        }
    }
}
