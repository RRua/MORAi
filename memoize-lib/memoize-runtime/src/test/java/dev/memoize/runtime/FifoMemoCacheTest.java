package dev.memoize.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class FifoMemoCacheTest {

    @Test
    public void basicGetPut() {
        FifoMemoCache<String, Integer> cache = new FifoMemoCache<>(10);
        assertNull(cache.get("missing"));
        cache.put("key", 42);
        assertEquals(Integer.valueOf(42), cache.get("key"));
        assertEquals(1, cache.size());
    }

    @Test
    public void evictsOldestInsertionWhenFull() {
        FifoMemoCache<Integer, String> cache = new FifoMemoCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        cache.put(4, "d"); // should evict key 1 (earliest insertion)

        assertEquals(3, cache.size());
        assertNull("eldest insertion must be gone", cache.get(1));
        assertEquals("b", cache.get(2));
        assertEquals("c", cache.get(3));
        assertEquals("d", cache.get(4));
    }

    @Test
    public void hitsDoNotReorderEviction() {
        // Key difference vs LRU: accessing key 1 should NOT save it from eviction.
        FifoMemoCache<Integer, String> cache = new FifoMemoCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");

        // Read key 1 several times -- would rescue it under LRU, but not FIFO.
        assertEquals("a", cache.get(1));
        assertEquals("a", cache.get(1));
        assertEquals("a", cache.get(1));

        cache.put(4, "d"); // still evicts key 1
        assertNull("FIFO must ignore access order", cache.get(1));
        assertEquals("b", cache.get(2));
    }

    @Test
    public void putExistingKeyDoesNotChangeInsertionOrder() {
        // LinkedHashMap with accessOrder=false keeps insertion order stable on
        // re-put. We rely on this for FIFO semantics.
        FifoMemoCache<Integer, String> cache = new FifoMemoCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");

        cache.put(1, "a*"); // overwrite value; insertion slot for key 1 stays

        cache.put(4, "d"); // evicts key 1 still (eldest insertion slot)
        assertNull(cache.get(1));
    }

    @Test
    public void removeDropsEntry() {
        FifoMemoCache<Integer, String> cache = new FifoMemoCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.remove(1);

        assertEquals(1, cache.size());
        assertNull(cache.get(1));
        assertEquals("b", cache.get(2));
    }

    @Test
    public void putNullActsAsRemove() {
        FifoMemoCache<Integer, String> cache = new FifoMemoCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(1, null); // remove

        assertEquals(1, cache.size());
        assertNull(cache.get(1));
        assertEquals("b", cache.get(2));
    }

    @Test
    public void clearRemovesAll() {
        FifoMemoCache<Integer, String> cache = new FifoMemoCache<>(10);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get(1));
        assertNull(cache.get(2));
    }

    @Test
    public void statsRecordsEvictions() {
        CacheStats stats = new CacheStats();
        FifoMemoCache<Integer, String> cache = new FifoMemoCache<>(2, stats);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c"); // evicts 1
        cache.put(4, "d"); // evicts 2
        assertEquals(2, stats.getEvictionCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroMaxSize() {
        new FifoMemoCache<>(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeMaxSize() {
        new FifoMemoCache<>(-1);
    }
}
