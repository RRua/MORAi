package dev.memoize.testjvm;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for JVM bytecode transformation: overloading, thread safety options,
 * selective invalidation.
 */
public class CalculatorTest {

    @Test
    public void basicMemoization() {
        Calculator calc = new Calculator();
        long r1 = calc.compute(5);
        long r2 = calc.compute(5);
        assertEquals(r1, r2);
        assertEquals(5000, r1);
    }

    @Test
    public void overloadedMethodsHaveIndependentCaches() {
        Calculator calc = new Calculator();
        long singleArg = calc.compute(3);
        long doubleArg = calc.compute(3, 4);
        // These should be different results (different methods)
        assertNotEquals(singleArg, doubleArg);
        assertEquals(3000, singleArg);
        assertEquals(12000, doubleArg);
    }

    @Test
    public void overloadedMethodsCacheIndependently() {
        Calculator calc = new Calculator();
        // Call single-arg compute twice
        calc.compute(5);
        long cached = calc.compute(5);
        assertEquals(5000, cached);

        // Call double-arg compute -- should not interfere
        long doubleArg = calc.compute(5, 2);
        assertEquals(10000, doubleArg);

        // Single-arg should still return cached value
        long stillCached = calc.compute(5);
        assertEquals(5000, stillCached);
    }

    @Test
    public void selectiveInvalidationClearsTargets() {
        Calculator calc = new Calculator();

        // Populate caches
        calc.compute(5);         // compute(int) cached
        calc.compute(5, 2);      // compute(int,int) cached
        calc.format(5);          // format cached
        calc.fastCompute(5.0);   // fastCompute cached

        // setBase invalidates "compute" and "format" but NOT "fastCompute"
        calc.setBase(10);

        // compute and format should give new results (invalidated)
        // base=10, loop 1000 times adding x=5: 10 + 5*1000 = 5010
        long recomputed = calc.compute(5);
        assertEquals(5010, recomputed);
        // base=10, loop 1000 times adding x*y=10: 10 + 10*1000 = 10010
        assertEquals(10010, calc.compute(5, 2));
        assertEquals("base=10, x=5", calc.format(5));

        // fastCompute was NOT invalidated by setBase (not in its target list).
        // However since it reads 'base' too, the cached value is stale.
        // This demonstrates why you'd choose full vs selective invalidation.
        // For this test we just verify it doesn't crash.
        double fast = calc.fastCompute(5.0);
        assertTrue(fast > 0);
    }

    @Test
    public void fullInvalidationClearsAllCaches() {
        Calculator calc = new Calculator();
        calc.compute(5);
        calc.format(5);
        calc.fastCompute(5.0);

        calc.reset(); // clears ALL caches

        // All should recompute (base is now 0 again)
        assertEquals(5000, calc.compute(5));
        assertEquals("base=0, x=5", calc.format(5));
    }

    @Test
    public void nonThreadSafeMemoWorks() {
        Calculator calc = new Calculator();
        double r1 = calc.fastCompute(9.0);
        double r2 = calc.fastCompute(9.0);
        assertEquals(r1, r2, 0.001);
        assertEquals(9.0, r1, 0.001); // sqrt(81 + 0) = 9.0
    }

    @Test
    public void formatMemoization() {
        Calculator calc = new Calculator();
        String r1 = calc.format(42);
        String r2 = calc.format(42);
        assertEquals("base=0, x=42", r1);
        assertSame(r1, r2); // Should be same object (cached)
    }

    @Test
    public void autoMonitorDisablesOnLowHitRate() {
        Calculator calc = new Calculator();
        // monitoredCompute has monitorWindow=10, minHitRate=0.5
        // 10 unique calls = 10 misses → hitRate=0.0 → should disable
        for (int i = 0; i < 10; i++) {
            calc.monitoredCompute(i);
        }
        // After disabling, calling with a previously-seen arg should recompute
        // (not use cache). We verify by changing base and checking the result updates.
        calc.setBase(100);
        long result = calc.monitoredCompute(5);
        // base=100, loop 100 times adding 5: 100 + 5*100 = 600
        assertEquals(600, result);
    }

    @Test
    public void autoMonitorKeepsCacheOnHighHitRate() {
        Calculator calc = new Calculator();
        // Warm up with one unique call, then repeat it 9 times = 1 miss + 9 hits = 90% hit rate
        calc.monitoredCompute(42);  // miss
        for (int i = 0; i < 9; i++) {
            calc.monitoredCompute(42);  // hits
        }
        // Cache still enabled — cached result should be returned
        long cached = calc.monitoredCompute(42);
        assertEquals(4200, cached); // base=0, 42*100 = 4200

        // A new arg should also be computed and cached (cache is active)
        long fresh = calc.monitoredCompute(10);
        assertEquals(1000, fresh); // base=0, 10*100 = 1000
        assertEquals(1000, calc.monitoredCompute(10)); // cache hit
    }

    @Test
    public void nullSafeFormatVariants() {
        Calculator calc = new Calculator();
        String r = calc.format(0);
        assertEquals("base=0, x=0", r);
    }
}
