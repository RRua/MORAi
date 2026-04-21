package com.memoize.bench;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Compares the three eviction policies (LRU / FIFO / LFU) on three
 * representative workloads:
 *
 * <ul>
 *   <li><b>Fibonacci(25)</b> -- narrow dense key space ({@code 0..25}); the
 *       cache never overflows, so eviction policy is irrelevant to
 *       correctness and the measurement isolates pure hot-path cost (LRU
 *       relinks, FIFO does not, LFU has frequency bookkeeping).</li>
 *   <li><b>Partition(40, 40)</b> -- moderate 2-D key space, polynomial. The
 *       cache is sized to fit; again compares pure per-operation cost.</li>
 *   <li><b>CoinChange(0, 120)</b> on a 7-coin denomination set -- wider 2-D
 *       space with skewed access where LFU's strength is expected to
 *       emerge under an {@code maxSize} that deliberately forces
 *       evictions.</li>
 * </ul>
 *
 * Interpret the medians in relative terms:
 *
 * <pre>
 *   FIFO &lt; LRU &lt; LFU    -- when everything fits and policy is irrelevant
 *                         (pure hot-path cost dominates)
 *   LFU wins on skew    -- when the cache must evict and hot keys stay hot
 *   LRU wins on scans   -- when a working set is re-used within a window
 * </pre>
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class PolicyBenchmark {

    @Rule
    public BenchmarkRule benchmarkRule = new BenchmarkRule();

    private volatile long sink;

    // -------- Fibonacci --------

    @Test
    public void fibonacci_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().fibonacci(25);
        }
    }

    @Test
    public void fibonacci_fifo() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsFifo().fibonacci(25);
        }
    }

    @Test
    public void fibonacci_lfu() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLfu().fibonacci(25);
        }
    }

    // -------- Partition --------

    @Test
    public void partition_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().partition(40, 40);
        }
    }

    @Test
    public void partition_fifo() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsFifo().partition(40, 40);
        }
    }

    @Test
    public void partition_lfu() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLfu().partition(40, 40);
        }
    }

    // -------- Coin change with a wider denomination set --------
    // Uses non-default coins so the 2-D key space (index, amount) exercises
    // eviction under a realistic skewed distribution.

    private static final int[] COINS = {1, 2, 5, 10, 20, 25, 50};

    @Test
    public void coinChange_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsLru a = new AlgorithmsLru();
            a.setCoins(COINS);
            sink = a.coinChange(0, 120);
        }
    }

    @Test
    public void coinChange_fifo() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsFifo a = new AlgorithmsFifo();
            a.setCoins(COINS);
            sink = a.coinChange(0, 120);
        }
    }

    @Test
    public void coinChange_lfu() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsLfu a = new AlgorithmsLfu();
            a.setCoins(COINS);
            sink = a.coinChange(0, 120);
        }
    }
}
