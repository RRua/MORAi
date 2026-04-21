package dev.memoize.testkotlin

import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for Kotlin JVM bytecode transformation.
 * Tests Kotlin-compiled classes with @Memoize and @CacheInvalidate.
 */
class StringProcessorTest {

    @Test
    fun basicMemoization() {
        val proc = StringProcessor(">>")
        val r1 = proc.reverse("hello")
        val r2 = proc.reverse("hello")
        assertEquals(">>olleh", r1)
        assertSame("Should return same cached object", r1, r2)
    }

    @Test
    fun differentArgsMiss() {
        val proc = StringProcessor()
        val r1 = proc.reverse("abc")
        val r2 = proc.reverse("xyz")
        assertEquals("cba", r1)
        assertEquals("zyx", r2)
        assertNotEquals(r1, r2)
    }

    @Test
    fun nullableReturnCached() {
        val proc = StringProcessor()
        // 'z' not found → returns null
        val r1 = proc.findFirst("hello", 'z')
        val r2 = proc.findFirst("hello", 'z')
        assertNull(r1)
        assertNull(r2)
    }

    @Test
    fun nullableReturnNonNull() {
        val proc = StringProcessor()
        val r1 = proc.findFirst("hello", 'l')
        val r2 = proc.findFirst("hello", 'l')
        assertEquals(2, r1)
        assertEquals(2, r2)
    }

    @Test
    fun wordCountMemoized() {
        val proc = StringProcessor()
        val r1 = proc.wordCount("the quick brown fox")
        val r2 = proc.wordCount("the quick brown fox")
        assertEquals(4, r1)
        assertEquals(4, r2)
    }

    @Test
    fun overloadedTransformSingleArg() {
        val proc = StringProcessor("pre_")
        val r1 = proc.transform("hello")
        val r2 = proc.transform("hello")
        assertEquals("pre_HELLO", r1)
        assertSame(r1, r2)
    }

    @Test
    fun overloadedTransformTwoArgs() {
        val proc = StringProcessor("x")
        val r1 = proc.transform("ab", 3)
        val r2 = proc.transform("ab", 3)
        assertEquals("xABxABxAB", r1)
        assertSame(r1, r2)
    }

    @Test
    fun overloadsHaveIndependentCaches() {
        val proc = StringProcessor("_")
        val single = proc.transform("hi")
        val multi = proc.transform("hi", 2)
        assertEquals("_HI", single)
        assertEquals("_HI_HI", multi)
        assertNotEquals(single, multi)
    }

    @Test
    fun selectiveInvalidation() {
        val proc = StringProcessor("A")
        // Populate caches
        val rev1 = proc.reverse("bc")
        val wc1 = proc.wordCount("one two")
        val tr1 = proc.transform("x")
        assertEquals("Acb", rev1)
        assertEquals(2, wc1)
        assertEquals("AX", tr1)

        // setPrefix invalidates reverse, transform, unboundedLookup — NOT wordCount
        proc.setPrefix("B")
        val rev2 = proc.reverse("bc")
        val wc2 = proc.wordCount("one two")
        val tr2 = proc.transform("x")

        assertEquals("Bcb", rev2)        // recomputed with new prefix
        assertEquals(2, wc2)              // still cached (not invalidated)
        assertEquals("BX", tr2)           // recomputed
    }

    @Test
    fun fullInvalidation() {
        val proc = StringProcessor("Z")
        proc.reverse("test")
        proc.wordCount("a b c")

        proc.resetAll() // clears all + resets prefix to ""

        assertEquals("tset", proc.reverse("test"))
        assertEquals(3, proc.wordCount("a b c"))
    }

    @Test
    fun threadSafetyNoneWorks() {
        val proc = StringProcessor()
        val r1 = proc.unsafeHash("hello")
        val r2 = proc.unsafeHash("hello")
        assertEquals(r1, r2)
        assertTrue(r1 != 0L)
    }

    @Test
    fun unboundedCacheWorks() {
        val proc = StringProcessor("ns")
        val r1 = proc.unboundedLookup("key1")
        val r2 = proc.unboundedLookup("key1")
        assertEquals("[ns:key1]", r1)
        assertSame(r1, r2)
    }

    @Test
    fun independentInstances() {
        val p1 = StringProcessor("A")
        val p2 = StringProcessor("B")
        val r1 = p1.reverse("x")
        val r2 = p2.reverse("x")
        assertEquals("Ax", r1)
        assertEquals("Bx", r2)
    }
}
