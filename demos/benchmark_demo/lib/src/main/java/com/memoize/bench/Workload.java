package com.memoize.bench;

import java.util.Random;

/**
 * Deterministic workload generators used to drive the benchmarks with
 * configurable hit-rate characteristics. Every generator returns an array of
 * argument tuples that the benchmark will fire at an {@link Algorithms}
 * instance in order.
 *
 * <p>The repetition factor controls the hit rate independently of the
 * algorithm's natural recursion: a generator that picks its arguments from a
 * small pool produces a hot workload where memoization should dominate,
 * while a generator that picks uniformly from a wide range produces a cold
 * workload where the cache should barely help (and auto-monitor would
 * ideally disable it).
 */
public final class Workload {

    private Workload() {}

    /** Fibonacci inputs, cycled R times. R controls hit rate. */
    public static int[] fibonacciInputs(int reps) {
        int[] pool = {20, 22, 24, 25, 23};
        return cycle(pool, reps);
    }

    /** Ackermann (m, n) pairs flattened as a {m0, n0, m1, n1, ...} array. */
    public static int[] ackermannInputs(int reps) {
        int[] pool = {2, 3,  3, 3,  3, 4,  2, 5,  3, 3};
        return cycle(pool, reps);
    }

    public static int[] catalanInputs(int reps) {
        int[] pool = {8, 10, 12, 10, 9};
        return cycle(pool, reps);
    }

    /** Binomial (n, k) pairs, flattened. */
    public static int[] binomialInputs(int reps) {
        int[] pool = {15, 5,  20, 10,  18, 9,  12, 6};
        return cycle(pool, reps);
    }

    /** Partition (n, k) pairs, flattened. */
    public static int[] partitionInputs(int reps) {
        int[] pool = {30, 10,  40, 15,  25, 8,  35, 12};
        return cycle(pool, reps);
    }

    /** Edit-distance entry points are always (0, 0). Just return reps-many. */
    public static int[] editDistanceInputs(int reps) { return zeros(reps * 2); }

    public static int[] lcsInputs(int reps) { return zeros(reps * 2); }

    public static int[] matrixChainInputs(int lastIndex, int reps) {
        int[] pool = {0, lastIndex};
        return cycle(pool, reps);
    }

    public static int[] knapsackInputs(int nItems, int reps) {
        int[] pool = {0, 20,  0, 30,  0, 25,  0, 15};
        // Ensure capacities don't overshoot sum of weights.
        return cycle(pool, reps);
    }

    public static int[] coinChangeInputs(int reps) {
        int[] pool = {0, 50,  0, 75,  0, 100,  0, 25};
        return cycle(pool, reps);
    }

    /** Generates a "cold" Fibonacci input distribution with many unique n. */
    public static int[] fibonacciInputsCold(int size, long seed) {
        Random rnd = new Random(seed);
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = 15 + rnd.nextInt(15); // 15..29
        return out;
    }

    /** Zipfian-ish biased distribution: 80% of calls hit the top 20% of keys. */
    public static int[] fibonacciInputsSkewed(int size, long seed) {
        Random rnd = new Random(seed);
        int[] hot = {25, 26, 27};
        int[] cold = {15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        int[] out = new int[size];
        for (int i = 0; i < size; i++) {
            out[i] = rnd.nextDouble() < 0.8
                    ? hot[rnd.nextInt(hot.length)]
                    : cold[rnd.nextInt(cold.length)];
        }
        return out;
    }

    // --- internals ---

    private static int[] cycle(int[] pool, int reps) {
        int[] out = new int[pool.length * reps];
        for (int r = 0; r < reps; r++) {
            System.arraycopy(pool, 0, out, r * pool.length, pool.length);
        }
        return out;
    }

    private static int[] zeros(int n) { return new int[n]; }
}
