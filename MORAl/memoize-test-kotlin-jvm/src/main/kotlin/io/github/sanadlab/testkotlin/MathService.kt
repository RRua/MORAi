package io.github.sanadlab.testkotlin

import io.github.sanadlab.annotations.CacheInvalidate
import io.github.sanadlab.annotations.Memoize

/**
 * Kotlin test subject for auto-monitor feature and TTL expiry.
 */
class MathService {
    private var multiplier = 1

    @Memoize(maxSize = 32, autoMonitor = true, minHitRate = 0.5, monitorWindow = 10)
    fun monitoredFib(n: Int): Long {
        if (n <= 1) return n.toLong()
        // Intentionally non-recursive to avoid stack issues in test
        var a = 0L
        var b = 1L
        for (i in 2..n) {
            val c = a + b
            a = b
            b = c
        }
        return b * multiplier
    }

    @Memoize(maxSize = 64, expireAfterWrite = 100) // 100ms TTL
    fun withTtl(x: Int): Int {
        return x * multiplier
    }

    @Memoize(maxSize = 64, recordStats = true)
    fun factorial(n: Int): Long {
        var result = 1L
        for (i in 2..n) result *= i
        return result * multiplier
    }

    @CacheInvalidate
    fun setMultiplier(m: Int) {
        this.multiplier = m
    }
}
