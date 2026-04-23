package io.github.sanadlab.runtime;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MemoCacheManagerTest {

    @Test
    public void invalidateAllClearsAllDispatchers() throws Exception {
        MemoCacheManager manager = new MemoCacheManager();
        MemoDispatcher searchDispatcher = new MemoDispatcher("search", 128);
        MemoDispatcher lengthDispatcher = new MemoDispatcher("length", 128);
        manager.register("search", searchDispatcher);
        manager.register("length", lengthDispatcher);

        AtomicInteger searchCount = new AtomicInteger(0);
        AtomicInteger lengthCount = new AtomicInteger(0);

        // Populate caches
        searchDispatcher.invoke(new Object[]{10}, () -> {
            searchCount.incrementAndGet();
            return true;
        });
        lengthDispatcher.invoke(new Object[]{}, () -> {
            lengthCount.incrementAndGet();
            return 5;
        });

        assertEquals(1, searchCount.get());
        assertEquals(1, lengthCount.get());

        // Invalidate all
        manager.invalidateAll();

        // Both should recompute
        searchDispatcher.invoke(new Object[]{10}, () -> {
            searchCount.incrementAndGet();
            return true;
        });
        lengthDispatcher.invoke(new Object[]{}, () -> {
            lengthCount.incrementAndGet();
            return 5;
        });

        assertEquals(2, searchCount.get());
        assertEquals(2, lengthCount.get());
    }

    @Test
    public void getDispatcherReturnsRegistered() {
        MemoCacheManager manager = new MemoCacheManager();
        MemoDispatcher dispatcher = new MemoDispatcher("test", 64);
        manager.register("test", dispatcher);

        assertSame(dispatcher, manager.getDispatcher("test"));
        assertNull(manager.getDispatcher("nonexistent"));
    }

    @Test
    public void selectiveInvalidateClearsOnlySpecifiedDispatchers() throws Exception {
        MemoCacheManager manager = new MemoCacheManager();
        MemoDispatcher searchDispatcher = new MemoDispatcher("search", 128);
        MemoDispatcher lengthDispatcher = new MemoDispatcher("length", 128);
        MemoDispatcher describeDispatcher = new MemoDispatcher("describe", 128);
        manager.register("search", searchDispatcher);
        manager.register("length", lengthDispatcher);
        manager.register("describe", describeDispatcher);

        AtomicInteger searchCount = new AtomicInteger(0);
        AtomicInteger lengthCount = new AtomicInteger(0);
        AtomicInteger describeCount = new AtomicInteger(0);

        // Populate all caches
        searchDispatcher.invoke(new Object[]{10}, () -> { searchCount.incrementAndGet(); return true; });
        lengthDispatcher.invoke(new Object[]{}, () -> { lengthCount.incrementAndGet(); return 5; });
        describeDispatcher.invoke(new Object[]{"x"}, () -> { describeCount.incrementAndGet(); return "desc"; });

        assertEquals(1, searchCount.get());
        assertEquals(1, lengthCount.get());
        assertEquals(1, describeCount.get());

        // Selectively invalidate only "search" and "length"
        manager.invalidate(new String[]{"search", "length"});

        // search and length should recompute; describe should still be cached
        searchDispatcher.invoke(new Object[]{10}, () -> { searchCount.incrementAndGet(); return true; });
        lengthDispatcher.invoke(new Object[]{}, () -> { lengthCount.incrementAndGet(); return 5; });
        describeDispatcher.invoke(new Object[]{"x"}, () -> { describeCount.incrementAndGet(); return "desc"; });

        assertEquals(2, searchCount.get());   // recomputed
        assertEquals(2, lengthCount.get());   // recomputed
        assertEquals(1, describeCount.get()); // still cached!
    }

    @Test
    public void selectiveInvalidateIgnoresUnknownNames() throws Exception {
        MemoCacheManager manager = new MemoCacheManager();
        MemoDispatcher d = new MemoDispatcher("search", 128);
        manager.register("search", d);

        d.invoke(new Object[]{1}, () -> "a");
        assertEquals(1, d.size());

        // Invalidating a non-existent name should not throw or affect anything
        manager.invalidate(new String[]{"nonexistent"});
        assertEquals(1, d.size()); // search cache untouched
    }

    @Test
    public void invalidateEntryDropsSingleRowOnNamedDispatcher() throws Exception {
        MemoCacheManager manager = new MemoCacheManager();
        MemoDispatcher getTags = new MemoDispatcher("getTags", 128);
        MemoDispatcher getStatus = new MemoDispatcher("getStatus", 128);
        manager.register("getTags", getTags);
        manager.register("getStatus", getStatus);

        AtomicInteger getTagsCount = new AtomicInteger(0);
        AtomicInteger getStatusCount = new AtomicInteger(0);

        // Populate getTags with several rows and getStatus with one.
        getTags.invoke(new Object[]{1}, () -> { getTagsCount.incrementAndGet(); return "tagsFor1"; });
        getTags.invoke(new Object[]{2}, () -> { getTagsCount.incrementAndGet(); return "tagsFor2"; });
        getTags.invoke(new Object[]{3}, () -> { getTagsCount.incrementAndGet(); return "tagsFor3"; });
        getStatus.invoke(new Object[]{}, () -> { getStatusCount.incrementAndGet(); return "OK"; });
        assertEquals(3, getTagsCount.get());
        assertEquals(1, getStatusCount.get());

        // Single-row invalidation: drop getTags(2) only.
        manager.invalidateEntry("getTags", new Object[]{2});

        // getTags(1) and getTags(3) still hit; getTags(2) recomputes;
        // getStatus is completely untouched.
        getTags.invoke(new Object[]{1}, () -> { getTagsCount.incrementAndGet(); return "X"; });
        getTags.invoke(new Object[]{3}, () -> { getTagsCount.incrementAndGet(); return "X"; });
        assertEquals(3, getTagsCount.get());

        getTags.invoke(new Object[]{2}, () -> { getTagsCount.incrementAndGet(); return "tagsFor2*"; });
        assertEquals(4, getTagsCount.get());

        getStatus.invoke(new Object[]{}, () -> { getStatusCount.incrementAndGet(); return "OK"; });
        assertEquals("unrelated dispatcher untouched", 1, getStatusCount.get());
    }

    @Test
    public void invalidateEntryUnknownMethodIsNoOp() {
        MemoCacheManager manager = new MemoCacheManager();
        // Should not throw even when the dispatcher key does not exist.
        manager.invalidateEntry("neverRegistered", new Object[]{1});
    }

    @Test
    public void totalSizeAcrossAllDispatchers() throws Exception {
        MemoCacheManager manager = new MemoCacheManager();
        MemoDispatcher d1 = new MemoDispatcher("m1", 128);
        MemoDispatcher d2 = new MemoDispatcher("m2", 128);
        manager.register("m1", d1);
        manager.register("m2", d2);

        d1.invoke(new Object[]{1}, () -> "a");
        d1.invoke(new Object[]{2}, () -> "b");
        d2.invoke(new Object[]{3}, () -> "c");

        assertEquals(3, manager.totalSize());
    }
}
