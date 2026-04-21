package com.memoize.bench;

/**
 * A synthetic baseline target with a single pure compute method of
 * controlled per-call cost. Used by {@code OverheadBenchmark} to quantify
 * the per-call overhead that {@code @Memoize} adds over a baseline that
 * runs the compute every time.
 *
 * <p>The method is deliberately <b>non-recursive</b> and takes a single
 * primitive {@code int} parameter, so cache-key construction is cheap and
 * constant-time, and its compute cost is independent of the input value.
 * These two properties isolate the library's per-call overhead from
 * algorithmic variation and from hashing cost.
 */
public class OverheadLib {

    /**
     * Returns a deterministic hash-like value for the given seed. Runs a
     * short mixing loop whose per-call cost is intended to be on the order
     * of a microsecond on typical Pixel-class hardware---small enough for
     * wrapper overhead to be measurable at low hit rates, large enough for
     * compute savings to dominate at high hit rates.
     *
     * <p>Execution time is independent of {@code seed}: every call runs
     * exactly {@code MIX_ITERS} iterations of the same arithmetic.
     */
    public long expensiveCompute(int seed) {
        long acc = seed;
        for (int i = 0; i < MIX_ITERS; i++) {
            acc = acc * 31L + i;
            acc ^= acc >>> 13;
        }
        return acc;
    }

    private static final int MIX_ITERS = 500;
}
