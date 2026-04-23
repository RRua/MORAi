package io.github.sanadlab.test;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests that verify the @Memoize and @CacheInvalidate bytecode transformation
 * works correctly at runtime.
 */
public class LinkedListMemoTest {

    @Test
    public void searchReturnsCorrectResults() {
        LinkedList list = new LinkedList();
        list.insert(10);
        list.insert(20);
        list.insert(30);

        assertTrue(list.search(10));
        assertTrue(list.search(20));
        assertTrue(list.search(30));
        assertFalse(list.search(99));
    }

    @Test
    public void lengthReturnsCorrectResults() {
        LinkedList list = new LinkedList();
        assertEquals(0, list.length());
        list.insert(10);
        assertEquals(1, list.length());
        list.insert(20);
        assertEquals(2, list.length());
    }

    @Test
    public void searchCachesResult() {
        LinkedList list = new LinkedList();
        list.insert(10);

        // First call computes
        boolean r1 = list.search(10);
        // Second call should hit cache (same result)
        boolean r2 = list.search(10);

        assertTrue(r1);
        assertTrue(r2);
    }

    @Test
    public void cacheInvalidatedOnInsert() {
        LinkedList list = new LinkedList();
        list.insert(10);

        // Cache the length
        assertEquals(1, list.length());

        // Insert invalidates cache
        list.insert(20);

        // Must return fresh value, not cached 1
        assertEquals(2, list.length());
    }

    @Test
    public void cacheInvalidatedOnDelete() {
        LinkedList list = new LinkedList();
        list.insert(10);
        list.insert(20);

        assertTrue(list.search(20));
        assertEquals(2, list.length());

        list.delete(20);

        // Cache invalidated: search and length must recompute
        assertFalse(list.search(20));
        assertEquals(1, list.length());
    }

    @Test
    public void cacheInvalidatedOnInsertAtHead() {
        LinkedList list = new LinkedList();
        list.insert(10);

        assertEquals(1, list.length());

        list.insertAtHead(5);

        assertEquals(2, list.length());
    }

    @Test
    public void differentInstancesHaveIndependentCaches() {
        LinkedList list1 = new LinkedList();
        LinkedList list2 = new LinkedList();

        list1.insert(10);
        list2.insert(20);

        assertTrue(list1.search(10));
        assertFalse(list1.search(20));
        assertFalse(list2.search(10));
        assertTrue(list2.search(20));
    }

    @Test
    public void multipleMemoizedMethodsWorkIndependently() {
        LinkedList list = new LinkedList();
        list.insert(10);
        list.insert(20);

        // Call both memoized methods
        assertTrue(list.search(10));
        assertEquals(2, list.length());

        // Both should be cached and return same results
        assertTrue(list.search(10));
        assertEquals(2, list.length());
    }

    @Test
    public void describeReturnsCorrectResults() {
        LinkedList list = new LinkedList();
        list.insert(10);
        list.insert(20);

        assertEquals("Found: 10", list.describe(10));
        assertNull(list.describe(99));
    }

    @Test
    public void describeNullResultIsCached() {
        LinkedList list = new LinkedList();
        list.insert(10);

        // First call: null (not found)
        assertNull(list.describe(99));
        // Second call: should still be null (cached)
        assertNull(list.describe(99));
    }

    @Test
    public void describeInvalidatedOnInsert() {
        LinkedList list = new LinkedList();

        // Cache a miss
        assertNull(list.describe(10));

        // Insert the value
        list.insert(10);

        // Cache invalidated: should now find it
        assertEquals("Found: 10", list.describe(10));
    }

    @Test
    public void searchFalseIsCached() {
        LinkedList list = new LinkedList();
        list.insert(10);

        // Cache a false result
        assertFalse(list.search(99));
        // Should still be false (cached)
        assertFalse(list.search(99));
    }

    @Test
    public void emptyListOperations() {
        LinkedList list = new LinkedList();

        assertEquals(0, list.length());
        assertFalse(list.search(1));
        assertNull(list.describe(1));
    }
}
