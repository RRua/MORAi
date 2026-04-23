package com.linkedlist.app;

import io.github.sanadlab.annotations.Memoize;

/**
 * MEMOIZED version of the expensive-compute library. Every pure, deterministic
 * method that the workload calls with repeated arguments is annotated with
 * {@link Memoize}, so the bytecode transformation installs an inline cache at
 * method entry and cache-store at return.
 *
 * <p>The BODIES of the methods are intentionally identical to the baseline so
 * that any runtime difference measured on a real device is attributable to the
 * memoization layer alone.
 */
public class ExpensiveLib {

    // -------- 1. naive recursive Fibonacci --------
    // Small cache: only a handful of distinct n's in the workload.
    @Memoize(maxSize = 32, recordStats = true)
    public long fibonacci(int n) {
        if (n < 2) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    // -------- 2. primality check (trial division) --------
    @Memoize(maxSize = 64, recordStats = true)
    public boolean isPrime(long value) {
        if (value < 2) return false;
        if (value < 4) return true;
        if ((value & 1L) == 0) return false;
        long limit = (long) Math.sqrt((double) value);
        for (long d = 3; d <= limit; d += 2) {
            if (value % d == 0) return false;
        }
        return true;
    }

    // -------- 3. count primes up to n --------
    @Memoize(maxSize = 16, recordStats = true)
    public int countPrimesUpTo(int n) {
        int count = 0;
        for (long v = 2; v <= n; v++) {
            if (isPrime(v)) count++;
        }
        return count;
    }

    // -------- 4. Levenshtein distance --------
    @Memoize(maxSize = 128, recordStats = true)
    public int levenshtein(String a, String b) {
        int la = a.length();
        int lb = b.length();
        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            for (int j = 1; j <= lb; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[lb];
    }

    // -------- 5. longest common subsequence length --------
    @Memoize(maxSize = 128, recordStats = true)
    public int lcs(String a, String b) {
        int la = a.length();
        int lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[la][lb];
    }

    // -------- 6. determinant of an n x n matrix (Laplace expansion) --------
    // Matrices are keyed by reference equality via Arrays.deepHashCode over the
    // boxed arg array, so passing the same array instance hits; different copies
    // of equal data still hit because deepEquals walks the rows.
    @Memoize(maxSize = 16, recordStats = true)
    public double determinant(double[][] matrix) {
        int n = matrix.length;
        if (n == 1) return matrix[0][0];
        if (n == 2) return matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0];
        double det = 0.0;
        double[][] sub = new double[n - 1][n - 1];
        for (int col = 0; col < n; col++) {
            for (int i = 1; i < n; i++) {
                int subCol = 0;
                for (int j = 0; j < n; j++) {
                    if (j == col) continue;
                    sub[i - 1][subCol++] = matrix[i][j];
                }
            }
            double sign = (col % 2 == 0) ? 1.0 : -1.0;
            det += sign * matrix[0][col] * determinant(sub);
        }
        return det;
    }

    // -------- 7. Mandelbrot escape iteration count --------
    @Memoize(maxSize = 64, recordStats = true)
    public int mandelbrot(double cr, double ci, int maxIter) {
        double zr = 0, zi = 0;
        for (int k = 0; k < maxIter; k++) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            if (zr2 + zi2 > 4.0) return k;
            double newZi = 2 * zr * zi + ci;
            zr = zr2 - zi2 + cr;
            zi = newZi;
        }
        return maxIter;
    }

    // -------- 8. hash of a pseudo image --------
    @Memoize(maxSize = 32, recordStats = true)
    public long imageFingerprint(int width, int height, int seed) {
        long acc = 0x9E3779B97F4A7C15L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                long pixel = (long) seed * 31 + x * 2654435761L + y * 40503L;
                pixel ^= (pixel >>> 21);
                pixel *= 0xC2B2AE3D27D4EB4FL;
                acc ^= pixel + 0x9E3779B97F4A7C15L + (acc << 6) + (acc >> 2);
            }
        }
        return acc;
    }
}
