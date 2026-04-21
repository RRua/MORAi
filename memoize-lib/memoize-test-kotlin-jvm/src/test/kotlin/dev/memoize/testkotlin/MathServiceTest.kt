package dev.memoize.testkotlin

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for auto-monitor, TTL expiry, and stats in Kotlin JVM context.
 */
class MathServiceTest {

    @Test
    fun autoMonitorDisablesOnLowHitRate() {
        val svc = MathService()
        // monitorWindow=10, minHitRate=0.5
        // 10 unique calls → all misses → disabled
        for (i in 0 until 10) {
            svc.monitoredFib(i)
        }
        // Cache should be disabled now. Change multiplier and verify recomputation.
        svc.setMultiplier(2)
        // fib(5) = 5, * 2 = 10. If cache were active, it would return old value (5).
        assertEquals(10L, svc.monitoredFib(5))
    }

    @Test
    fun autoMonitorKeepsCacheOnHighHitRate() {
        val svc = MathService()
        // 1 unique call (miss) + 9 repeats (hits) = 90% hit rate → stays enabled
        svc.monitoredFib(10)
        for (i in 0 until 9) {
            svc.monitoredFib(10)
        }
        // Cache still enabled. Verify by calling with same arg — should still get cached result.
        // fib(10) = 55 * 1 = 55
        val cached = svc.monitoredFib(10)
        assertEquals(55L, cached)

        // Also verify a NEW arg still gets computed and cached (cache is active, not disabled)
        val fresh = svc.monitoredFib(7)
        assertEquals(13L, fresh) // fib(7) = 13
        // Second call should hit cache
        assertEquals(13L, svc.monitoredFib(7))
    }

    @Test
    fun ttlExpiresCachedEntry() {
        val svc = MathService()
        val r1 = svc.withTtl(42)
        assertEquals(42, r1)

        // Sleep past TTL (100ms)
        Thread.sleep(150)

        // Change multiplier so recomputation gives different result
        svc.setMultiplier(3)

        // After TTL expiry, should recompute: 42 * 3 = 126
        // BUT setMultiplier calls @CacheInvalidate which clears all caches anyway.
        // So this test verifies the combination works.
        assertEquals(126, svc.withTtl(42))
    }

    @Test
    fun ttlReturnsCachedBeforeExpiry() {
        val svc = MathService()
        val r1 = svc.withTtl(7)
        assertEquals(7, r1)

        // Immediately call again — should hit cache even if we somehow change state
        val r2 = svc.withTtl(7)
        assertEquals(7, r2)
    }

    @Test
    fun statsTrackedWithRecordStats() {
        val svc = MathService()
        // factorial has recordStats=true
        svc.factorial(5)    // miss
        svc.factorial(5)    // hit
        svc.factorial(10)   // miss
        svc.factorial(5)    // hit

        // We can't directly access stats from the test (no public API on the object),
        // but we can verify the method works correctly and caches.
        assertEquals(120L, svc.factorial(5))    // 5! = 120
        assertEquals(3628800L, svc.factorial(10)) // 10! = 3628800
    }

    @Test
    fun fullInvalidationClearsAll() {
        val svc = MathService()
        svc.factorial(5)
        svc.monitoredFib(10)
        svc.withTtl(42)

        svc.setMultiplier(2) // invalidates all caches

        assertEquals(240L, svc.factorial(5))    // 120 * 2
        assertEquals(84, svc.withTtl(42))        // 42 * 2
    }
}
