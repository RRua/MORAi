package io.github.sanadlab.runtime;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe cache statistics tracker.
 */
public final class CacheStats {

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();

    public void recordHit() {
        hitCount.incrementAndGet();
    }

    public void recordMiss() {
        missCount.incrementAndGet();
    }

    public void recordEviction() {
        evictionCount.incrementAndGet();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getEvictionCount() {
        return evictionCount.get();
    }

    public long getRequestCount() {
        return hitCount.get() + missCount.get();
    }

    public double getHitRate() {
        long requests = getRequestCount();
        return requests == 0 ? 0.0 : (double) hitCount.get() / requests;
    }

    public void reset() {
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
    }

    @Override
    public String toString() {
        return String.format("CacheStats{hits=%d, misses=%d, evictions=%d, hitRate=%.2f%%}",
                getHitCount(), getMissCount(), getEvictionCount(), getHitRate() * 100);
    }
}
