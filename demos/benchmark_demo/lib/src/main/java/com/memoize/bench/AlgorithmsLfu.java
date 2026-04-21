package com.memoize.bench;

import dev.memoize.annotations.EvictionPolicy;
import dev.memoize.annotations.Memoize;

/**
 * LFU-memoized variant. Same shape as the LRU/FIFO siblings. Best-suited to
 * skewed access where a small set of arguments dominates; more expensive per
 * operation due to the frequency bookkeeping.
 */
public class AlgorithmsLfu extends Algorithms {

    @Override
    @Memoize(maxSize = 128, eviction = EvictionPolicy.LFU, recordStats = true)
    public long fibonacci(int n) { return super.fibonacci(n); }

    @Override
    @Memoize(maxSize = 1024, eviction = EvictionPolicy.LFU, recordStats = true)
    public int ackermann(int m, int n) { return super.ackermann(m, n); }

    @Override
    @Memoize(maxSize = 128, eviction = EvictionPolicy.LFU, recordStats = true)
    public long catalan(int n) { return super.catalan(n); }

    @Override
    @Memoize(maxSize = 2048, eviction = EvictionPolicy.LFU, recordStats = true)
    public long binomial(int n, int k) { return super.binomial(n, k); }

    @Override
    @Memoize(maxSize = 2048, eviction = EvictionPolicy.LFU, recordStats = true)
    public long partition(int n, int k) { return super.partition(n, k); }

    @Override
    @Memoize(maxSize = 1024, eviction = EvictionPolicy.LFU, recordStats = true)
    public int editDistance(int i, int j) { return super.editDistance(i, j); }

    @Override
    @Memoize(maxSize = 1024, eviction = EvictionPolicy.LFU, recordStats = true)
    public int lcs(int i, int j) { return super.lcs(i, j); }

    @Override
    @Memoize(maxSize = 1024, eviction = EvictionPolicy.LFU, recordStats = true)
    public int matrixChain(int i, int j) { return super.matrixChain(i, j); }

    @Override
    @Memoize(maxSize = 2048, eviction = EvictionPolicy.LFU, recordStats = true)
    public int knapsack(int i, int capacity) { return super.knapsack(i, capacity); }

    @Override
    @Memoize(maxSize = 2048, eviction = EvictionPolicy.LFU, recordStats = true)
    public long coinChange(int i, int amount) { return super.coinChange(i, amount); }
}
