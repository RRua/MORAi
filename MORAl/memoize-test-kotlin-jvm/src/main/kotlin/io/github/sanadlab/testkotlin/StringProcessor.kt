package io.github.sanadlab.testkotlin

import io.github.sanadlab.annotations.CacheInvalidate
import io.github.sanadlab.annotations.EvictionPolicy
import io.github.sanadlab.annotations.Memoize
import io.github.sanadlab.annotations.ThreadSafety

/**
 * Kotlin test subject: exercises Kotlin-specific patterns with memoization.
 * Tests: nullable returns, String operations, default params (at bytecode level),
 * various annotation configurations.
 */
class StringProcessor(private var prefix: String = "") {

    @Memoize(maxSize = 64)
    fun reverse(input: String): String {
        return prefix + input.reversed()
    }

    @Memoize(maxSize = 32)
    fun wordCount(text: String): Int {
        return text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    }

    /**
     * Returns null for empty input — tests nullable return caching in Kotlin.
     */
    @Memoize(maxSize = 32)
    fun findFirst(text: String, char: Char): Int? {
        val idx = text.indexOf(char)
        return if (idx >= 0) idx else null
    }

    /**
     * Overloaded: single-arg version.
     */
    @Memoize(maxSize = 16)
    fun transform(input: String): String {
        return prefix + input.uppercase()
    }

    /**
     * Overloaded: two-arg version with repeat count.
     */
    @Memoize(maxSize = 16)
    fun transform(input: String, times: Int): String {
        return (prefix + input.uppercase()).repeat(times)
    }

    @Memoize(maxSize = 16, threadSafety = ThreadSafety.NONE)
    fun unsafeHash(input: String): Long {
        var hash = 0L
        for (c in input) {
            hash = hash * 31 + c.code
        }
        return hash
    }

    @Memoize(maxSize = 16, eviction = EvictionPolicy.NONE)
    fun unboundedLookup(key: String): String {
        return "[$prefix:$key]"
    }

    @CacheInvalidate("reverse", "transform", "unboundedLookup")
    fun setPrefix(newPrefix: String) {
        this.prefix = newPrefix
    }

    @CacheInvalidate
    fun resetAll() {
        this.prefix = ""
    }
}
