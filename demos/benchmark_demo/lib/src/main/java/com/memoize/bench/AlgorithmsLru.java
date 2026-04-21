package com.memoize.bench;

import dev.memoize.annotations.EvictionPolicy;
import dev.memoize.annotations.Memoize;

/**
 * Memoized variant using {@link EvictionPolicy#LRU}. Every method overrides
 * its {@link Algorithms} counterpart and is annotated with {@code @Memoize};
 * the bodies just call {@code super}. Because self-recursive calls inside
 * the parent body virtual-dispatch back to {@code this} -- and therefore
 * re-enter the memoized override -- the cache cascades through the entire
 * recursion tree.
 *
 * <p><b>maxSize</b> is tuned per method so that the full relevant key space
 * for the benchmarks fits without eviction; if you want to force eviction,
 * lower it at the annotation level or create a custom subclass.
 */
public class AlgorithmsLru extends Algorithms {

    @Override
    @Memoize(maxSize = 128, eviction = EvictionPolicy.LRU, recordStats = true)
    public long fibonacci(int n) { return super.fibonacci(n); }

    @Override
    @Memoize(maxSize = 1024, eviction = EvictionPolicy.LRU, recordStats = true)
    public int ackermann(int m, int n) { return super.ackermann(m, n); }

    @Override
    @Memoize(maxSize = 128, eviction = EvictionPolicy.LRU, recordStats = true)
    public long catalan(int n) { return super.catalan(n); }

    @Override
    @Memoize(maxSize = 2048, eviction = EvictionPolicy.LRU, recordStats = true)
    public long binomial(int n, int k) { return super.binomial(n, k); }

    @Override
    @Memoize(maxSize = 2048, eviction = EvictionPolicy.LRU, recordStats = true)
    public long partition(int n, int k) { return super.partition(n, k); }

    @Override
    @Memoize(maxSize = 1024, eviction = EvictionPolicy.LRU, recordStats = true)
    public int editDistance(int i, int j) { return super.editDistance(i, j); }

    @Override
    @Memoize(maxSize = 1024, eviction = EvictionPolicy.LRU, recordStats = true)
    public int lcs(int i, int j) { return super.lcs(i, j); }

    @Override
    @Memoize(maxSize = 1024, eviction = EvictionPolicy.LRU, recordStats = true)
    public int matrixChain(int i, int j) { return super.matrixChain(i, j); }

    @Override
    @Memoize(maxSize = 2048, eviction = EvictionPolicy.LRU, recordStats = true)
    public int knapsack(int i, int capacity) { return super.knapsack(i, capacity); }

    @Override
    @Memoize(maxSize = 2048, eviction = EvictionPolicy.LRU, recordStats = true)
    public long coinChange(int i, int amount) { return super.coinChange(i, amount); }
}
