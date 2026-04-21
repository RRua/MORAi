package com.memoize.bench;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Sweeps the hit-rate dimension by driving {@link AlgorithmsLru#fibonacci}
 * through workloads with different input-reuse characteristics.
 *
 * <p>The cache is shared across an entire workload run (one instance per
 * {@code keepRunning} iteration), so the first call populates it and the
 * remaining calls either hit the hot rows or miss into a colder region,
 * depending on the input distribution:
 *
 * <ul>
 *   <li><b>HOT</b>: 5 unique {@code n} values repeated 40 times each ->
 *       hit rate &gt;= 0.87 once the cache is warm. Memoization wins big.</li>
 *   <li><b>WARM</b>: 15 unique values drawn uniformly 200 times -> hit rate
 *       ~0.92 in steady state but much more eviction churn and a bigger
 *       working set.</li>
 *   <li><b>COLD</b>: a Zipfian-ish distribution where 20% of keys see 80%
 *       of the traffic -> hit rate ~0.8 but with a long tail that
 *       exercises the eviction policy.</li>
 *   <li><b>VERY_COLD</b>: uniform over a wide range with many one-shot
 *       queries -> hit rate close to 0; memoization should add overhead
 *       rather than save time, which is exactly the scenario
 *       {@code autoMonitor} is designed to detect.</li>
 * </ul>
 *
 * <p>Compared side-by-side with a baseline sweep (also included) so the
 * table at the end of a benchmark run shows the cross-over point where
 * {@code @Memoize} stops paying for itself.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class HitRateBenchmark {

    @Rule
    public BenchmarkRule benchmarkRule = new BenchmarkRule();

    private volatile long sink;

    private static final int[] HOT      = Workload.fibonacciInputs(40);                // 5 vals x 40 reps
    private static final int[] WARM     = Workload.fibonacciInputsCold(200, 0xC0FFEEL);
    private static final int[] COLD     = Workload.fibonacciInputsSkewed(400, 0xDECAFL);
    private static final int[] VERYCOLD = Workload.fibonacciInputsCold(600, 0xFEEDL);

    // -------- HOT (reuse-heavy) --------

    @Test
    public void fibonacci_hot_baseline() {
        runBaseline(HOT);
    }

    @Test
    public void fibonacci_hot_lru() {
        runLru(HOT);
    }

    @Test
    public void fibonacci_hot_fifo() {
        runFifo(HOT);
    }

    @Test
    public void fibonacci_hot_lfu() {
        runLfu(HOT);
    }

    // -------- WARM (moderate reuse) --------

    @Test
    public void fibonacci_warm_baseline() {
        runBaseline(WARM);
    }

    @Test
    public void fibonacci_warm_lru() {
        runLru(WARM);
    }

    // -------- COLD (skewed Zipf-ish) --------

    @Test
    public void fibonacci_cold_baseline() {
        runBaseline(COLD);
    }

    @Test
    public void fibonacci_cold_lru() {
        runLru(COLD);
    }

    @Test
    public void fibonacci_cold_lfu() {
        runLfu(COLD);
    }

    // -------- VERY_COLD (near-zero reuse) --------

    @Test
    public void fibonacci_verycold_baseline() {
        runBaseline(VERYCOLD);
    }

    @Test
    public void fibonacci_verycold_lru() {
        runLru(VERYCOLD);
    }

    // --- helpers ---

    private void runBaseline(int[] inputs) {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            Algorithms a = new Algorithms();
            long acc = 0;
            for (int n : inputs) acc += a.fibonacci(n);
            sink = acc;
        }
    }

    private void runLru(int[] inputs) {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsLru a = new AlgorithmsLru();
            long acc = 0;
            for (int n : inputs) acc += a.fibonacci(n);
            sink = acc;
        }
    }

    private void runFifo(int[] inputs) {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsFifo a = new AlgorithmsFifo();
            long acc = 0;
            for (int n : inputs) acc += a.fibonacci(n);
            sink = acc;
        }
    }

    private void runLfu(int[] inputs) {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsLfu a = new AlgorithmsLfu();
            long acc = 0;
            for (int n : inputs) acc += a.fibonacci(n);
            sink = acc;
        }
    }
}
