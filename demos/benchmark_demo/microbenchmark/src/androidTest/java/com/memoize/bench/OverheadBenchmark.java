package com.memoize.bench;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

/**
 * Quantifies the per-call <b>overhead</b> of the memoize library at four
 * controlled hit-rate points: 75%, 50%, 25%, and 0%.
 *
 * <p>Each regime drives a fixed workload of {@link #CALLS_PER_ITER} calls
 * to {@link OverheadLib#expensiveCompute(int)} against a freshly constructed
 * {@link OverheadLibLru} instance whose cache has been pre-populated with a
 * small set of "hot" keys during a paused warm-up pass. The workload
 * interleaves hot-key lookups and never-before-seen cold-key lookups at
 * exactly the ratio needed to match the target hit rate, so the observed
 * steady-state hit rate equals the target from call 0.
 *
 * <h3>Reading the output</h3>
 *
 * <p>Each {@code memoized_hr_XX} row should be compared against the single
 * {@link #baseline()} row. The per-call overhead introduced by the library is:
 * <pre>
 *   overhead_per_call =
 *       (memoized_ns - baseline_ns) / CALLS_PER_ITER
 * </pre>
 * <ul>
 *   <li>At <b>75%</b> hit rate, the overhead is expected to be strongly
 *       <b>negative</b> (i.e., a speedup): three out of every four calls
 *       skip the compute entirely, so the saved work dominates the per-call
 *       wrapper cost.</li>
 *   <li>At <b>50%</b>, the overhead moves toward the break-even point where
 *       compute savings and wrapper overhead roughly cancel.</li>
 *   <li>At <b>25%</b>, the overhead typically turns positive: only one
 *       in four calls is saved, while every call pays the wrapper cost.</li>
 *   <li>At <b>0%</b>, the overhead is the worst case: every call pays the
 *       wrapper cost on top of the full compute cost. This is the scenario
 *       {@code @Memoize(autoMonitor = true, minHitRate = ...)} is designed
 *       to detect and disable.</li>
 * </ul>
 *
 * <h3>Hit-rate control</h3>
 *
 * <p>Hit rates are enforced through three design choices:
 * <ol>
 *   <li>{@link OverheadLibLru#expensiveCompute(int)} uses {@code maxSize =
 *       128}, which is large enough that no cold key inserted during the
 *       100-call iteration evicts any hot key.</li>
 *   <li>The hot-key pool is pre-populated via {@code pauseTiming()} /
 *       {@code resumeTiming()}, so that the warm-up cost is excluded from
 *       the measurement and the measured workload begins with a warm cache.</li>
 *   <li>Cold keys are drawn from a monotonically increasing range
 *       ({@link #COLD_BASE}{@code + i}) that cannot collide with hot keys
 *       or with cold keys used in previous calls of the same iteration, so
 *       every cold call is a guaranteed miss.</li>
 * </ol>
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class OverheadBenchmark {

    @Rule
    public BenchmarkRule benchmarkRule = new BenchmarkRule();

    private volatile long sink;

    /** Calls per measured iteration. Larger = more per-call averaging. */
    private static final int CALLS_PER_ITER = 100;

    /** Hot keys that stay in the cache. Kept small so warm-up is cheap. */
    private static final int[] HOT_KEYS = {1, 2, 3, 4, 5, 6, 7, 8};

    /** Base value for cold keys; chosen high to never collide with hot keys. */
    private static final int COLD_BASE = 100_000;

    // Pre-built workloads for each hit rate. Built once to take the
    // Random-based shuffle cost out of the measurement.
    private static final int[] WORKLOAD_75 = buildWorkload(75);
    private static final int[] WORKLOAD_50 = buildWorkload(50);
    private static final int[] WORKLOAD_25 = buildWorkload(25);
    private static final int[] WORKLOAD_0  = buildWorkload(0);

    // ------------------------------------------------------------
    // Baseline: no memoization. Every call pays the compute cost.
    // The workload shape is the same as the memoized rows so the
    // baseline number is directly comparable.
    // ------------------------------------------------------------

    @Test
    public void baseline() {
        BenchmarkState state = benchmarkRule.getState();
        OverheadLib lib = new OverheadLib();
        while (state.keepRunning()) {
            for (int k : WORKLOAD_0) sink = lib.expensiveCompute(k);
        }
    }

    // ------------------------------------------------------------
    // Memoized rows. One per target hit rate.
    // ------------------------------------------------------------

    @Test public void memoized_hr_75() { runAtHitRate(WORKLOAD_75); }
    @Test public void memoized_hr_50() { runAtHitRate(WORKLOAD_50); }
    @Test public void memoized_hr_25() { runAtHitRate(WORKLOAD_25); }
    @Test public void memoized_hr_0()  { runAtHitRate(WORKLOAD_0);  }

    private void runAtHitRate(int[] workload) {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            // Fresh instance per iteration so the cache starts empty.
            // The warm-up pass runs with the timer paused, so the measured
            // workload begins with exactly HOT_KEYS.length entries cached.
            state.pauseTiming();
            OverheadLibLru lib = new OverheadLibLru();
            for (int k : HOT_KEYS) lib.expensiveCompute(k);
            state.resumeTiming();

            for (int k : workload) sink = lib.expensiveCompute(k);
        }
    }

    // ------------------------------------------------------------
    // Workload construction
    // ------------------------------------------------------------

    /**
     * Builds a CALLS_PER_ITER-length array in which {@code hitRatePercent}%
     * of positions hold a hot key and the remaining positions hold a unique
     * cold key. After population, the array is shuffled deterministically
     * so hot and cold calls interleave.
     */
    private static int[] buildWorkload(int hitRatePercent) {
        int hits = CALLS_PER_ITER * hitRatePercent / 100;
        int[] out = new int[CALLS_PER_ITER];
        int hotIdx = 0;
        int coldIdx = 0;
        for (int i = 0; i < CALLS_PER_ITER; i++) {
            if (i < hits) {
                out[i] = HOT_KEYS[hotIdx++ % HOT_KEYS.length];
            } else {
                out[i] = COLD_BASE + coldIdx++;
            }
        }
        // Deterministic shuffle keyed on the hit rate so every run of the
        // benchmark sees the exact same interleaving for a given rate.
        Random rnd = new Random(42L + hitRatePercent);
        for (int i = out.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = out[i];
            out[i] = out[j];
            out[j] = tmp;
        }
        return out;
    }
}
