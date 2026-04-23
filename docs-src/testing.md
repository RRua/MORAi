# Testing

## Test Summary

| Test Suite | Tests | Coverage |
|-----------|-------|---------|
| `CacheKeyWrapperTest` | 9 | Key equality/hashing: empty, primitive, composite, null, array, boolean |
| `LruMemoCacheTest` | 7 | Get/put, LRU eviction order, clear, stats, invalid maxSize |
| `MemoDispatcherTest` | 11 | Caching, miss, zero-args, null returns, invalidation, stats, composite keys, auto-monitor (disable/keep/reenable/bypass) |
| `MemoCacheManagerTest` | 5 | Bulk invalidation, selective invalidation, unknown names, dispatcher lookup, total size |
| `LinkedListMemoTest` | 13 | Android end-to-end: correctness, caching, invalidation, independence, null handling |
| `CalculatorTest` (JVM) | 10 | JVM end-to-end: overloading, thread safety options, selective invalidation, auto-monitor |
| `StringProcessorTest` (Kotlin JVM) | 13 | Kotlin end-to-end: nullable returns, overloading, selective invalidation, ThreadSafety.NONE, unbounded cache, independent instances |
| `MathServiceTest` (Kotlin JVM) | 6 | Kotlin auto-monitor, TTL expiry, stats, full invalidation |
| **Total** | **74** | |

## Running Tests

### Runtime Unit Tests

These test the core caching library without any ASM involvement:

```bash
cd memoize-lib
./gradlew :memoize-runtime:test
```

### Android Integration Tests

These test the annotated LinkedList in the test Android app:

```bash
cd memoize-lib/memoize-test-android
./gradlew testDebugUnitTest
```

### JVM Integration Tests

These test overloading, thread safety, and selective invalidation on plain JVM:

```bash
cd memoize-lib/memoize-test-jvm
./gradlew test
```

### Kotlin JVM Integration Tests

These test Kotlin-compiled classes with the bytecode transformation:

```bash
cd memoize-lib/memoize-test-kotlin-jvm
./gradlew test
```

### Full Build Verification

Confirms the ASM transformation produces valid bytecode:

```bash
# Android
cd memoize-lib/memoize-test-android
./gradlew assembleDebug

# JVM (Java)
cd memoize-lib/memoize-test-jvm
./gradlew build

# JVM (Kotlin)
cd memoize-lib/memoize-test-kotlin-jvm
./gradlew build
```

### Bytecode Inspection

Verify the transformation is structurally correct:

```bash
javap -p build/intermediates/classes/debug/transformDebugClassesWithAsm/dirs/dev/memoize/test/LinkedList.class
```

Expected output includes `__memoCacheManager` and `__memoDispatcher_*` fields.

## Runtime Unit Tests

### CacheKeyWrapperTest

Source: [`memoize-runtime/src/test/java/dev/memoize/runtime/CacheKeyWrapperTest.java`](../memoize-runtime/src/test/java/dev/memoize/runtime/CacheKeyWrapperTest.java)

Tests `CacheKeyWrapper` equality and hashing:

- `emptyKeysShouldBeEqual` -- `EMPTY` singleton equals new empty wrapper
- `singlePrimitiveKeyEquality` -- `{42}` equals `{42}`
- `differentPrimitiveKeysNotEqual` -- `{42}` not equals `{99}`
- `compositeKeyEquality` -- `{1, "hello", 3.14}` equals same
- `compositeKeyDifference` -- `{1, "hello"}` not equals `{1, "world"}`
- `nullArgsHandled` -- `{null, "test"}` equals `{null, "test"}`
- `nullVsNonNull` -- `{null}` not equals `{"test"}`
- `arrayArgs` -- `{int[]{1,2,3}}` equals `{int[]{1,2,3}}` (deep equality)
- `booleanKey` -- `{true}` equals `{true}`, not equals `{false}`

### LruMemoCacheTest

Source: [`memoize-runtime/src/test/java/dev/memoize/runtime/LruMemoCacheTest.java`](../memoize-runtime/src/test/java/dev/memoize/runtime/LruMemoCacheTest.java)

- `basicGetPut` -- Put and retrieve a value
- `evictsWhenOverMaxSize` -- Adding 4th entry to size-3 cache evicts the oldest
- `lruOrderRespected` -- Accessing an entry makes it "recent", protecting it from eviction
- `clearRemovesAll` -- `clear()` empties the cache
- `statsTracksEvictions` -- `CacheStats` eviction counter increments on eviction
- `rejectsZeroMaxSize` -- Constructor throws `IllegalArgumentException` for `maxSize = 0`
- `rejectsNegativeMaxSize` -- Same for negative values

### MemoDispatcherTest

Source: [`memoize-runtime/src/test/java/dev/memoize/runtime/MemoDispatcherTest.java`](../memoize-runtime/src/test/java/dev/memoize/runtime/MemoDispatcherTest.java)

- `cachesResultOnSecondCall` -- Second call with same args returns cached value (compute called once)
- `differentArgsMissCache` -- Different args trigger recomputation
- `zeroArgMethodCached` -- Empty args use `EMPTY` key, caches correctly
- `nullReturnValueCached` -- `null` result is cached (not recomputed on second call)
- `invalidateClearsCache` -- After `invalidate()`, same args trigger recomputation
- `statsRecordHitsAndMisses` -- Hit/miss counts are accurate, hit rate calculated correctly
- `autoMonitorDisablesCacheOnLowHitRate` -- 10 unique calls → 0% hit rate → cache disabled
- `autoMonitorKeepsCacheOnHighHitRate` -- 2 unique + 8 repeated → 80% hit rate → cache stays enabled
- `autoMonitorReenableResetsStats` -- After `reenable()`, stats reset and cache is active again
- `autoMonitorBypassesCacheWhenDisabled` -- Disabled cache always returns null, doesn't store
- `multipleArgsCompositeKey` -- `{1, "a", 3.14}` and `{1, "b", 3.14}` are different keys

### MemoCacheManagerTest

Source: [`memoize-runtime/src/test/java/dev/memoize/runtime/MemoCacheManagerTest.java`](../memoize-runtime/src/test/java/dev/memoize/runtime/MemoCacheManagerTest.java)

- `invalidateAllClearsAllDispatchers` -- Two dispatchers registered; `invalidateAll()` clears both
- `selectiveInvalidateClearsOnlySpecifiedDispatchers` -- 3 dispatchers; invalidate 2, verify 3rd is untouched
- `selectiveInvalidateIgnoresUnknownNames` -- Non-existent names don't affect existing caches
- `getDispatcherReturnsRegistered` -- Lookup by method name works; unknown name returns null
- `totalSizeAcrossAllDispatchers` -- Sum of entries across all dispatchers

## Integration Tests

### LinkedListMemoTest

Source: [`memoize-test-android/src/test/java/dev/memoize/test/LinkedListMemoTest.java`](../memoize-test-android/src/test/java/dev/memoize/test/LinkedListMemoTest.java)

End-to-end tests using the annotated `LinkedList`:

- `searchReturnsCorrectResults` -- Basic search correctness
- `lengthReturnsCorrectResults` -- Length accuracy after insertions
- `searchCachesResult` -- Second identical search returns same result
- `cacheInvalidatedOnInsert` -- `insert()` clears length cache
- `cacheInvalidatedOnDelete` -- `delete()` clears both search and length caches
- `cacheInvalidatedOnInsertAtHead` -- `insertAtHead()` clears caches
- `differentInstancesHaveIndependentCaches` -- Two `LinkedList` instances don't share caches
- `multipleMemoizedMethodsWorkIndependently` -- `search` and `length` caches are independent
- `describeReturnsCorrectResults` -- Object return type works
- `describeNullResultIsCached` -- Null return from `describe(99)` is cached
- `describeInvalidatedOnInsert` -- Cache of null result cleared after insert
- `searchFalseIsCached` -- `false` (int 0 at bytecode level) is cached correctly
- `emptyListOperations` -- Empty list returns correct defaults

### CalculatorTest (JVM)

Source: [`memoize-test-jvm/src/test/java/dev/memoize/testjvm/CalculatorTest.java`](../memoize-test-jvm/src/test/java/dev/memoize/testjvm/CalculatorTest.java)

JVM-specific tests using a `Calculator` class with overloaded methods and thread safety options:

- `basicMemoization` -- Basic cache behavior on JVM
- `overloadedMethodsHaveIndependentCaches` -- `compute(int)` and `compute(int,int)` use separate caches
- `overloadedMethodsCacheIndependently` -- Calling one overload doesn't interfere with the other
- `selectiveInvalidationClearsTargets` -- `@CacheInvalidate({"compute", "format"})` clears those but not `fastCompute`
- `fullInvalidationClearsAllCaches` -- `@CacheInvalidate` (no args) clears everything
- `nonThreadSafeMemoWorks` -- `ThreadSafety.NONE` produces correct results (uses `UnsynchronizedLruMemoCache`)
- `formatMemoization` -- String return type caching (same object reference on cache hit)
- `nullSafeFormatVariants` -- Edge case with zero arguments
- `autoMonitorDisablesOnLowHitRate` -- 10 unique calls disable auto-monitored cache; recomputation verified
- `autoMonitorKeepsCacheOnHighHitRate` -- High hit rate keeps cache active; cached values returned

### StringProcessorTest (Kotlin JVM)

Source: [`memoize-test-kotlin-jvm/src/test/kotlin/dev/memoize/testkotlin/StringProcessorTest.kt`](../memoize-test-kotlin-jvm/src/test/kotlin/dev/memoize/testkotlin/StringProcessorTest.kt)

Kotlin-specific integration tests using `StringProcessor` with Kotlin features:

- `basicMemoization` -- String reverse with prefix, same object reference on cache hit
- `differentArgsMiss` -- Different inputs produce different cached results
- `nullableReturnCached` -- Kotlin `Int?` return type: null cached correctly
- `nullableReturnNonNull` -- Kotlin `Int?` return type: non-null cached correctly
- `wordCountMemoized` -- Int return type caching from Kotlin
- `overloadedTransformSingleArg` -- Single-arg `transform` cached independently
- `overloadedTransformTwoArgs` -- Two-arg `transform` cached independently
- `overloadsHaveIndependentCaches` -- Overloaded methods don't share caches
- `selectiveInvalidation` -- `@CacheInvalidate("reverse", "transform")` clears targets but not `wordCount`
- `fullInvalidation` -- `@CacheInvalidate` clears all caches
- `threadSafetyNoneWorks` -- `ThreadSafety.NONE` with Kotlin-compiled classes
- `unboundedCacheWorks` -- `EvictionPolicy.NONE` produces `ConcurrentMemoCache`
- `independentInstances` -- Different instances have isolated caches

### MathServiceTest (Kotlin JVM)

Source: [`memoize-test-kotlin-jvm/src/test/kotlin/dev/memoize/testkotlin/MathServiceTest.kt`](../memoize-test-kotlin-jvm/src/test/kotlin/dev/memoize/testkotlin/MathServiceTest.kt)

Kotlin tests for auto-monitor, TTL, and stats:

- `autoMonitorDisablesOnLowHitRate` -- 10 unique fib calls → cache disabled → recomputation with changed multiplier
- `autoMonitorKeepsCacheOnHighHitRate` -- 1 miss + 9 hits → cache stays active → cached value returned
- `ttlExpiresCachedEntry` -- Entry expires after TTL, recomputation returns updated value
- `ttlReturnsCachedBeforeExpiry` -- Entry returned from cache before TTL expires
- `statsTrackedWithRecordStats` -- `recordStats = true` doesn't break caching behavior
- `fullInvalidationClearsAll` -- `@CacheInvalidate` clears all caches across different configurations

:::{note}
**Android unit tests** (`testDebugUnitTest`) run against pre-transformation bytecode. The ASM transform only applies to the APK packaging pipeline.

**JVM unit tests** run against **post-transformation** bytecode (the plugin's `doLast` hook transforms classes before tests run). This means JVM tests validate actual memoization behavior end-to-end.
:::
