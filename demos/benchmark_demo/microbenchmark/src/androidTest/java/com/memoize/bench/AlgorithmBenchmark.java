package com.memoize.bench;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Baseline-vs-memoized comparison for every algorithm in the library.
 *
 * <p>Each algorithm has two benchmark methods: one against a fresh
 * {@link Algorithms} instance (no caching at all) and one against a fresh
 * {@link AlgorithmsLru} instance (the default, recency-aware memoized
 * variant). The same workload is fed to both so the ratio of the reported
 * medians is a direct measure of the speedup produced by memoization.
 *
 * <h3>Reading the output</h3>
 *
 * <ul>
 *   <li>For recursion-heavy algorithms (fibonacci, catalan, binomial,
 *       partition, editDistance, lcs, matrixChain, knapsack, coinChange)
 *       the baseline should be orders of magnitude slower -- most of these
 *       are exponential without memoization and polynomial with it.</li>
 *   <li>The {@code @Memoize} hot-path overhead is only visible when
 *       compared against a hand-rolled tabulation, which this harness does
 *       not include on purpose: the goal is to quantify the win produced by
 *       turning a naive recursive implementation into a memoized one with
 *       just an annotation.</li>
 * </ul>
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AlgorithmBenchmark {

    @Rule
    public BenchmarkRule benchmarkRule = new BenchmarkRule();

    // Sink prevents the JIT from dead-code-eliminating the results.
    private volatile long sink;

    private Algorithms baseline;
    private AlgorithmsLru memoized;

    @Before
    public void setUp() {
        baseline = new Algorithms();
        memoized = new AlgorithmsLru();
    }

    // ------------------------------------------------------------------
    // 1. Fibonacci (n = 25)
    // ------------------------------------------------------------------
    @Test
    public void fibonacci_baseline() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.fibonacci(25);
        }
    }

    @Test
    public void fibonacci_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        // Fresh instance per iteration ensures the cache is cold, so we
        // measure warmup + steady-state inside a single recursion burst,
        // just like the un-memoized baseline.
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().fibonacci(25);
        }
    }

    // ------------------------------------------------------------------
    // 2. Ackermann (m = 3, n = 6)
    // ------------------------------------------------------------------
    @Test
    public void ackermann_baseline() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.ackermann(3, 6);
        }
    }

    @Test
    public void ackermann_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().ackermann(3, 6);
        }
    }

    // ------------------------------------------------------------------
    // 3. Catalan (n = 14)
    // ------------------------------------------------------------------
    @Test
    public void catalan_baseline() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.catalan(14);
        }
    }

    @Test
    public void catalan_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().catalan(14);
        }
    }

    // ------------------------------------------------------------------
    // 4. Binomial (n = 28, k = 12)
    // ------------------------------------------------------------------
    @Test
    public void binomial_baseline() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.binomial(28, 12);
        }
    }

    @Test
    public void binomial_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().binomial(28, 12);
        }
    }

    // ------------------------------------------------------------------
    // 5. Integer partition (n = 40, k = 40)
    // ------------------------------------------------------------------
    @Test
    public void partition_baseline() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.partition(40, 40);
        }
    }

    @Test
    public void partition_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().partition(40, 40);
        }
    }

    // ------------------------------------------------------------------
    // 6. Edit distance
    // ------------------------------------------------------------------
    @Test
    public void editDistance_baseline() {
        baseline.setEditStrings("intention", "execution");
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.editDistance(0, 0);
        }
    }

    @Test
    public void editDistance_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsLru m = new AlgorithmsLru();
            m.setEditStrings("intention", "execution");
            sink = m.editDistance(0, 0);
        }
    }

    // ------------------------------------------------------------------
    // 7. LCS
    // ------------------------------------------------------------------
    @Test
    public void lcs_baseline() {
        baseline.setLcsStrings("HELLOWORLD", "YELLOWPLANET");
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.lcs(0, 0);
        }
    }

    @Test
    public void lcs_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsLru m = new AlgorithmsLru();
            m.setLcsStrings("HELLOWORLD", "YELLOWPLANET");
            sink = m.lcs(0, 0);
        }
    }

    // ------------------------------------------------------------------
    // 8. Matrix chain multiplication
    // ------------------------------------------------------------------
    @Test
    public void matrixChain_baseline() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.matrixChain(0, baseline.matrixChainLast());
        }
    }

    @Test
    public void matrixChain_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            AlgorithmsLru m = new AlgorithmsLru();
            sink = m.matrixChain(0, m.matrixChainLast());
        }
    }

    // ------------------------------------------------------------------
    // 9. 0/1 Knapsack (capacity = 30)
    // ------------------------------------------------------------------
    @Test
    public void knapsack_baseline() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.knapsack(0, 30);
        }
    }

    @Test
    public void knapsack_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().knapsack(0, 30);
        }
    }

    // ------------------------------------------------------------------
    // 10. Coin change (amount = 100)
    // ------------------------------------------------------------------
    @Test
    public void coinChange_baseline() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = baseline.coinChange(0, 100);
        }
    }

    @Test
    public void coinChange_memoized_lru() {
        BenchmarkState state = benchmarkRule.getState();
        while (state.keepRunning()) {
            sink = new AlgorithmsLru().coinChange(0, 100);
        }
    }
}
