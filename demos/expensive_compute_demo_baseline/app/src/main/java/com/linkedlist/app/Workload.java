package com.linkedlist.app;

import java.util.Random;

/**
 * A deterministic workload that exercises {@link ExpensiveLib} with a lot of
 * argument repetition. The repetition is what makes memoization worthwhile;
 * the {@code REPEAT_FACTOR} controls how many times the same arguments are
 * fed back to each function.
 *
 * <p>Run {@link #runAll()} and read {@link Result#totalNanos}. Both the
 * baseline and memoized demo projects share an identical workload so the only
 * difference between runs is the presence (or absence) of {@code @Memoize}
 * annotations.
 */
public final class Workload {

    /** How many times each argument is repeated. Higher = more cache benefit. */
    public static final int REPEAT_FACTOR = 40;

    public static final class Result {
        public final String label;
        public final long totalNanos;
        public final long resultChecksum;
        public Result(String label, long totalNanos, long checksum) {
            this.label = label;
            this.totalNanos = totalNanos;
            this.resultChecksum = checksum;
        }
        @Override public String toString() {
            return String.format("%-22s %8.2f ms  checksum=%d",
                    label, totalNanos / 1_000_000.0, resultChecksum);
        }
    }

    private final ExpensiveLib lib;

    public Workload(ExpensiveLib lib) {
        this.lib = lib;
    }

    public Result[] runAll() {
        return new Result[]{
                runFibonacci(),
                runPrimality(),
                runPrimeCount(),
                runLevenshtein(),
                runLcs(),
                runDeterminant(),
                runMandelbrot(),
                runImageFingerprint(),
        };
    }

    public Result runFibonacci() {
        int[] inputs = {28, 30, 32, 29, 28, 31, 32, 30};
        long checksum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < REPEAT_FACTOR; r++) {
            for (int n : inputs) checksum += lib.fibonacci(n);
        }
        return new Result("fibonacci", System.nanoTime() - start, checksum);
    }

    public Result runPrimality() {
        long[] inputs = {1_000_003L, 999_983L, 1_000_033L, 1_000_003L, 999_983L};
        long checksum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < REPEAT_FACTOR * 20; r++) {
            for (long v : inputs) checksum += lib.isPrime(v) ? 1 : 0;
        }
        return new Result("isPrime", System.nanoTime() - start, checksum);
    }

    public Result runPrimeCount() {
        int[] inputs = {20_000, 25_000, 30_000, 20_000, 25_000};
        long checksum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < REPEAT_FACTOR / 2; r++) {
            for (int n : inputs) checksum += lib.countPrimesUpTo(n);
        }
        return new Result("countPrimesUpTo", System.nanoTime() - start, checksum);
    }

    public Result runLevenshtein() {
        String[][] pairs = {
                {"kitten", "sitting"},
                {"flaw", "lawn"},
                {"intention", "execution"},
                {"kitten", "sitting"},
                {"memoization", "optimization"},
        };
        long checksum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < REPEAT_FACTOR * 20; r++) {
            for (String[] p : pairs) checksum += lib.levenshtein(p[0], p[1]);
        }
        return new Result("levenshtein", System.nanoTime() - start, checksum);
    }

    public Result runLcs() {
        String[][] pairs = {
                {"ABCBDAB", "BDCABA"},
                {"AGGTAB",  "GXTXAYB"},
                {"ABCBDAB", "BDCABA"},
                {"HELLOWORLD", "YELLOWPLANET"},
        };
        long checksum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < REPEAT_FACTOR * 20; r++) {
            for (String[] p : pairs) checksum += lib.lcs(p[0], p[1]);
        }
        return new Result("lcs", System.nanoTime() - start, checksum);
    }

    public Result runDeterminant() {
        double[][] m1 = buildMatrix(7, 11);
        double[][] m2 = buildMatrix(7, 13);
        double[][][] inputs = { m1, m2, m1, m1, m2 };
        double checksum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < REPEAT_FACTOR; r++) {
            for (double[][] m : inputs) checksum += lib.determinant(m);
        }
        return new Result("determinant-7x7", System.nanoTime() - start, (long) checksum);
    }

    public Result runMandelbrot() {
        double[][] points = {
                {-0.75, 0.1},
                {-0.745, 0.11},
                {-0.75, 0.1},
                { 0.25, 0.0},
                {-1.25, 0.0},
        };
        long checksum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < REPEAT_FACTOR * 50; r++) {
            for (double[] p : points) checksum += lib.mandelbrot(p[0], p[1], 5000);
        }
        return new Result("mandelbrot", System.nanoTime() - start, checksum);
    }

    public Result runImageFingerprint() {
        int[][] inputs = {
                {128, 128, 7},
                {128, 128, 9},
                {256, 256, 7},
                {128, 128, 7},
                {256, 256, 7},
        };
        long checksum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < REPEAT_FACTOR; r++) {
            for (int[] in : inputs) checksum += lib.imageFingerprint(in[0], in[1], in[2]);
        }
        return new Result("imageFingerprint", System.nanoTime() - start, checksum);
    }

    private static double[][] buildMatrix(int n, long seed) {
        Random rnd = new Random(seed);
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m[i][j] = rnd.nextDouble() * 10.0 - 5.0;
            }
        }
        return m;
    }
}
