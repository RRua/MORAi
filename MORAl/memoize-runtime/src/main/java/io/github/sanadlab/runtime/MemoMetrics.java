package io.github.sanadlab.runtime;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional per-dispatcher timing metrics used to quantify the benefit of memoizing
 * a given method. Tracks total compute nanos on misses, total lookup nanos on hits,
 * and derives an "estimated time saved" figure using the mean compute cost.
 *
 * <p>Only populated when {@link MemoLogger} is enabled at INFO or higher, so the
 * cost of timing is paid only when the user explicitly asked for observability.
 */
public final class MemoMetrics {

    private final AtomicLong totalComputeNanos = new AtomicLong();
    private final AtomicLong totalLookupNanos  = new AtomicLong();
    private final AtomicLong computeSamples    = new AtomicLong();
    private final AtomicLong lookupSamples     = new AtomicLong();

    void recordCompute(long nanos) {
        totalComputeNanos.addAndGet(nanos);
        computeSamples.incrementAndGet();
    }

    void recordLookup(long nanos) {
        totalLookupNanos.addAndGet(nanos);
        lookupSamples.incrementAndGet();
    }

    public long getTotalComputeNanos() { return totalComputeNanos.get(); }
    public long getTotalLookupNanos()  { return totalLookupNanos.get(); }
    public long getComputeSamples()    { return computeSamples.get(); }
    public long getLookupSamples()     { return lookupSamples.get(); }

    public double getMeanComputeNanos() {
        long n = computeSamples.get();
        return n == 0 ? 0.0 : (double) totalComputeNanos.get() / n;
    }

    public double getMeanLookupNanos() {
        long n = lookupSamples.get();
        return n == 0 ? 0.0 : (double) totalLookupNanos.get() / n;
    }

    /**
     * Estimated wall-clock savings from caching: every hit dodged a compute of
     * average cost, minus the lookup overhead it did incur.
     */
    public long getEstimatedSavedNanos() {
        double meanCompute = getMeanComputeNanos();
        long hits = lookupSamples.get();
        long lookupCost = totalLookupNanos.get();
        return (long) (meanCompute * hits) - lookupCost;
    }

    public void reset() {
        totalComputeNanos.set(0);
        totalLookupNanos.set(0);
        computeSamples.set(0);
        lookupSamples.set(0);
    }

    @Override
    public String toString() {
        return String.format(
                "MemoMetrics{meanCompute=%.1fns, meanLookup=%.1fns, savedMs=%.3f}",
                getMeanComputeNanos(),
                getMeanLookupNanos(),
                getEstimatedSavedNanos() / 1_000_000.0);
    }
}
