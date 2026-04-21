package com.memoize.bench;

import dev.memoize.annotations.EvictionPolicy;
import dev.memoize.annotations.Memoize;

/**
 * Memoized variant of {@link OverheadLib} used by the overhead benchmark.
 *
 * <p>The {@code maxSize} is deliberately set large enough (128) that no
 * cold key inserted during a 100-call iteration can evict a hot key, so
 * the observed hit rate matches the workload's target hit rate exactly.
 * {@link EvictionPolicy#LRU} is used because it is the default and
 * recency-aware eviction does not affect the measurement as long as
 * capacity is not reached.
 */
public class OverheadLibLru extends OverheadLib {

    @Override
    @Memoize(maxSize = 128, eviction = EvictionPolicy.LRU, recordStats = true)
    public long expensiveCompute(int seed) {
        return super.expensiveCompute(seed);
    }
}
