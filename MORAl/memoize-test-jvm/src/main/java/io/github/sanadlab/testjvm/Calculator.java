package io.github.sanadlab.testjvm;

import io.github.sanadlab.annotations.CacheInvalidate;
import io.github.sanadlab.annotations.Memoize;
import io.github.sanadlab.annotations.ThreadSafety;

/**
 * JVM test subject: a calculator with memoized methods demonstrating
 * overloading, thread safety options, and selective invalidation.
 */
public class Calculator {
    private int base = 0;

    // Overloaded methods with different parameter types
    @Memoize(maxSize = 64)
    public long compute(int x) {
        long result = base;
        for (int i = 0; i < 1000; i++) result += x;
        return result;
    }

    @Memoize(maxSize = 64)
    public long compute(int x, int y) {
        long result = base;
        for (int i = 0; i < 1000; i++) result += x * y;
        return result;
    }

    @Memoize(maxSize = 32)
    public String format(int x) {
        return "base=" + base + ", x=" + x;
    }

    // Non-thread-safe: for single-threaded use (e.g., main thread only)
    @Memoize(maxSize = 128, threadSafety = ThreadSafety.NONE)
    public double fastCompute(double x) {
        return Math.sqrt(x * x + base);
    }

    // Auto-monitored: disables itself if hit rate < 50% after 10 calls
    @Memoize(maxSize = 32, autoMonitor = true, minHitRate = 0.5, monitorWindow = 10)
    public long monitoredCompute(int x) {
        long result = base;
        for (int i = 0; i < 100; i++) result += x;
        return result;
    }

    // Selective: only invalidates compute overloads, not format or fastCompute
    @CacheInvalidate({"compute", "format"})
    public void setBase(int newBase) {
        this.base = newBase;
    }

    // Full invalidation
    @CacheInvalidate
    public void reset() {
        this.base = 0;
    }

    public int getBase() {
        return base;
    }
}
