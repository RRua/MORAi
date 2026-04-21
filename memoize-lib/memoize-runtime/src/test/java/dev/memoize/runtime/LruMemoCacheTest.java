package dev.memoize.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class LruMemoCacheTest {

    @Test
    public void basicGetPut() {
        LruMemoCache<String, Integer> cache = new LruMemoCache<>(10);
        assertNull(cache.get("key"));
        cache.put("key", 42);
        assertEquals(Integer.valueOf(42), cache.get("key"));
    }

    @Test
    public void evictsWhenOverMaxSize() {
        LruMemoCache<Integer, String> cache = new LruMemoCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        assertEquals(3, cache.size());

        cache.put(4, "d"); // should evict key 1 (LRU)
        assertEquals(3, cache.size());
        assertNull(cache.get(1)); // evicted
        assertEquals("d", cache.get(4));
    }

    @Test
    public void lruOrderRespected() {
        LruMemoCache<Integer, String> cache = new LruMemoCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");

        cache.get(1); // access key 1, making it recently used

        cache.put(4, "d"); // should evict key 2 (now LRU)
        assertNotNull(cache.get(1)); // still present
        assertNull(cache.get(2)); // evicted
    }

    @Test
    public void clearRemovesAll() {
        LruMemoCache<Integer, String> cache = new LruMemoCache<>(10);
        cache.put(1, "a");
        cache.put(2, "b");
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get(1));
    }

    @Test
    public void statsTracksEvictions() {
        CacheStats stats = new CacheStats();
        LruMemoCache<Integer, String> cache = new LruMemoCache<>(2, stats);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c"); // evicts 1
        assertEquals(1, stats.getEvictionCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroMaxSize() {
        new LruMemoCache<>(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeMaxSize() {
        new LruMemoCache<>(-1);
    }
}
