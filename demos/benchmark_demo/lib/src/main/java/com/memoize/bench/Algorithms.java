package com.memoize.bench;

/**
 * Baseline (un-memoized) implementations of ten compute-intensive algorithms
 * with heavy overlapping-subproblem structure. The set is chosen so that each
 * entry becomes dramatically faster under memoization -- most go from
 * exponential to polynomial time.
 *
 * <p>The three subclasses {@link AlgorithmsLru}, {@link AlgorithmsFifo}, and
 * {@link AlgorithmsLfu} inherit from this class and {@code @Override} every
 * method purely to attach {@code @Memoize} with different eviction policies.
 * Self-recursive calls inside these method bodies virtual-dispatch back to
 * the overriding subclass, so the cache cascades through the entire recursion
 * tree.
 *
 * <h3>Design notes for benchmarking</h3>
 * <ul>
 *   <li>Every method has <b>primitive</b> or short-String arguments -- no
 *       arrays, no mutable state -- so the cache key is cheap to hash and
 *       every subproblem is naturally memoizable.</li>
 *   <li>Input tables for {@code matrixChain}, {@code knapsack}, and
 *       {@code coinChange} are held as instance fields so the argument list
 *       stays primitive. Mutate them via the setters BEFORE a benchmark
 *       run -- the memoized subclasses do not invalidate on set.</li>
 *   <li>All methods are pure w.r.t. their argument tuple once the instance
 *       state is fixed, which is the contract {@code @Memoize} assumes.</li>
 * </ul>
 */
public class Algorithms {

    // ------------------------------------------------------------------
    // 1. Naive recursive Fibonacci
    // ------------------------------------------------------------------
    public long fibonacci(int n) {
        if (n < 2) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    // ------------------------------------------------------------------
    // 2. Ackermann function. Explodes for m >= 3; keep n small.
    // ------------------------------------------------------------------
    public int ackermann(int m, int n) {
        if (m == 0) return n + 1;
        if (n == 0) return ackermann(m - 1, 1);
        return ackermann(m - 1, ackermann(m, n - 1));
    }

    // ------------------------------------------------------------------
    // 3. Catalan number via direct recurrence
    //    C(n) = sum_{i=0}^{n-1} C(i) * C(n-1-i)
    // ------------------------------------------------------------------
    public long catalan(int n) {
        if (n <= 1) return 1;
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += catalan(i) * catalan(n - 1 - i);
        }
        return sum;
    }

    // ------------------------------------------------------------------
    // 4. Binomial coefficient via Pascal's recurrence
    //    C(n, k) = C(n-1, k-1) + C(n-1, k)
    // ------------------------------------------------------------------
    public long binomial(int n, int k) {
        if (k == 0 || k == n) return 1;
        return binomial(n - 1, k - 1) + binomial(n - 1, k);
    }

    // ------------------------------------------------------------------
    // 5. Integer partition count p(n, k) = # partitions of n with max part <= k
    // ------------------------------------------------------------------
    public long partition(int n, int k) {
        if (n == 0) return 1;
        if (n < 0 || k == 0) return 0;
        return partition(n - k, k) + partition(n, k - 1);
    }

    // ------------------------------------------------------------------
    // 6. Edit distance (Levenshtein) over offset indices (i, j).
    //    Strings are instance state so cache keys stay primitive.
    // ------------------------------------------------------------------
    private String edA = "saturday";
    private String edB = "sunday";
    public void setEditStrings(String a, String b) { this.edA = a; this.edB = b; }

    public int editDistance(int i, int j) {
        if (i >= edA.length()) return edB.length() - j;
        if (j >= edB.length()) return edA.length() - i;
        if (edA.charAt(i) == edB.charAt(j)) {
            return editDistance(i + 1, j + 1);
        }
        int a = editDistance(i + 1, j);
        int b = editDistance(i, j + 1);
        int c = editDistance(i + 1, j + 1);
        return 1 + Math.min(a, Math.min(b, c));
    }

    // ------------------------------------------------------------------
    // 7. Longest Common Subsequence length over offset indices (i, j)
    // ------------------------------------------------------------------
    private String lcsA = "HELLOWORLD";
    private String lcsB = "YELLOWPLANET";
    public void setLcsStrings(String a, String b) { this.lcsA = a; this.lcsB = b; }

    public int lcs(int i, int j) {
        if (i >= lcsA.length() || j >= lcsB.length()) return 0;
        if (lcsA.charAt(i) == lcsB.charAt(j)) {
            return 1 + lcs(i + 1, j + 1);
        }
        return Math.max(lcs(i + 1, j), lcs(i, j + 1));
    }

    // ------------------------------------------------------------------
    // 8. Matrix chain multiplication (min scalar multiplications)
    //    Memoized over half-open range (i, j).
    // ------------------------------------------------------------------
    private int[] mcDims = {40, 20, 30, 10, 30, 15, 25};
    public void setMatrixDims(int[] dims) { this.mcDims = dims; }
    public int matrixChainLast() { return mcDims.length - 2; }

    public int matrixChain(int i, int j) {
        if (i == j) return 0;
        int min = Integer.MAX_VALUE;
        for (int k = i; k < j; k++) {
            int cost = matrixChain(i, k) + matrixChain(k + 1, j)
                     + mcDims[i] * mcDims[k + 1] * mcDims[j + 1];
            if (cost < min) min = cost;
        }
        return min;
    }

    // ------------------------------------------------------------------
    // 9. 0/1 knapsack: max value packable from items[i..] with capacity w
    // ------------------------------------------------------------------
    private int[] kWeights = {2, 3, 4, 5, 9, 7, 8, 6, 4, 3};
    private int[] kValues  = {3, 4, 5, 6, 10, 13, 4, 7, 8, 6};
    public void setKnapsack(int[] w, int[] v) { this.kWeights = w; this.kValues = v; }
    public int knapsackItems() { return kWeights.length; }

    public int knapsack(int i, int capacity) {
        if (i >= kWeights.length || capacity <= 0) return 0;
        int without = knapsack(i + 1, capacity);
        if (kWeights[i] > capacity) return without;
        int with = kValues[i] + knapsack(i + 1, capacity - kWeights[i]);
        return Math.max(with, without);
    }

    // ------------------------------------------------------------------
    // 10. Coin change: number of ways to form `amount` using coins[i..]
    // ------------------------------------------------------------------
    private int[] ccCoins = {1, 2, 5, 10, 20, 50, 100};
    public void setCoins(int[] coins) { this.ccCoins = coins; }

    public long coinChange(int i, int amount) {
        if (amount == 0) return 1;
        if (amount < 0 || i >= ccCoins.length) return 0;
        return coinChange(i, amount - ccCoins[i]) + coinChange(i + 1, amount);
    }
}
