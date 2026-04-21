# Annotations API

All annotations are in the `dev.memoize.annotations` package. They use `RetentionPolicy.CLASS` -- readable by ASM at build time but stripped from the final APK.

Source: [`memoize-annotations/src/main/java/dev/memoize/annotations/`](https://github.com)

## @Memoize

Marks a method for automatic memoization. The method must have a non-void return type.

```java
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Memoize {
    int maxSize() default 128;
    long expireAfterWrite() default -1;
    CacheScope scope() default CacheScope.INSTANCE;
    EvictionPolicy eviction() default EvictionPolicy.LRU;
    ThreadSafety threadSafety() default ThreadSafety.CONCURRENT;
    boolean recordStats() default false;
    boolean autoMonitor() default false;
    double minHitRate() default 0.3;
    int monitorWindow() default 100;
}
```

### Parameters

`maxSize`
: Maximum number of entries in the cache. When exceeded, the eviction policy determines which entries are removed.
: **Default:** 128
: **Example:** `@Memoize(maxSize = 64)`

`expireAfterWrite`
: Time-to-live in milliseconds after a cache entry is written. `-1` disables TTL.
: **Default:** -1 (no expiry)
: **Example:** `@Memoize(expireAfterWrite = 30000)` (30 seconds)

`scope`
: Cache lifetime scope.
: - `CacheScope.INSTANCE` -- Per-object cache. Each instance has its own cache.
: - `CacheScope.CLASS` -- Static cache shared across all instances.
: **Default:** `INSTANCE`
: **Note:** `CLASS` scope is defined but not yet implemented in the ASM transformation.

`eviction`
: Eviction policy when the cache reaches `maxSize`. Pick the policy that matches the access pattern of the method you're caching -- see [Runtime Library](runtime.md) for the implementation details and hot-path cost of each.
: - `EvictionPolicy.LRU` -- Least Recently Used. Good general-purpose default when the workload has temporal locality. Hits relink the accessed entry.
: - `EvictionPolicy.FIFO` -- First-In-First-Out. Cheaper hit path than LRU (no relink on access), but ignores locality. Use when every argument is roughly equally likely or you need the tightest possible hot path.
: - `EvictionPolicy.LFU` -- Least Frequently Used. Best hit rate on skewed / Zipfian workloads where a small set of arguments dominates. Higher bookkeeping cost than LRU/FIFO; avoid on scan-heavy workloads.
: - `EvictionPolicy.NONE` -- No eviction (unbounded growth). Use with caution.
: **Default:** `LRU`

`threadSafety`
: Thread safety strategy for cache operations. Directly controls which cache implementation is used at runtime.
: - `ThreadSafety.CONCURRENT` -- `LruMemoCache` with `synchronized` methods. Safe for multi-threaded access. Recommended default.
: - `ThreadSafety.SYNCHRONIZED` -- Same as `CONCURRENT` (synchronized `LruMemoCache`).
: - `ThreadSafety.NONE` -- `UnsynchronizedLruMemoCache` with zero synchronization overhead. Use only when the method is called from a single thread (e.g., Android main thread). Offers the best performance for single-threaded code paths.
: **Default:** `CONCURRENT`

The cache implementation selected for each combination:

| ThreadSafety | `LRU` | `FIFO` | `LFU` | `NONE` |
|-------------|-------|--------|-------|--------|
| `NONE` | `UnsynchronizedLruMemoCache` | `FifoMemoCache` | `LfuMemoCache` | `ConcurrentMemoCache` |
| `SYNCHRONIZED` | `LruMemoCache` | `FifoMemoCache` | `LfuMemoCache` | `ConcurrentMemoCache` |
| `CONCURRENT` | `LruMemoCache` | `FifoMemoCache` | `LfuMemoCache` | `ConcurrentMemoCache` |

:::{note}
`FIFO` and `LFU` are always `synchronized`; they do not ship unsynchronized variants. Use `LRU` with `ThreadSafety.NONE` if you need the fastest possible single-threaded hot path.
:::

`recordStats`
: When `true`, tracks hit/miss/eviction counts via `CacheStats`. Adds minor overhead. Automatically enabled when `autoMonitor` is `true`.
: **Default:** `false`
: **Example:** `@Memoize(recordStats = true)`

`autoMonitor`
: When `true`, enables auto-monitoring mode. The cache tracks its hit rate and **automatically disables itself** (bypasses caching entirely) if the hit rate falls below `minHitRate` after `monitorWindow` calls. This prevents memoization from adding overhead to methods where caching is not beneficial (e.g., high argument cardinality, low temporal locality).
: When disabled by auto-monitor, `getIfCached()` always returns `null` and `putInCache()` is a no-op. The cache contents are freed to reclaim memory.
: **Default:** `false`
: **Example:** `@Memoize(autoMonitor = true, minHitRate = 0.4, monitorWindow = 200)`

`minHitRate`
: Minimum hit rate threshold (0.0 to 1.0) for auto-monitoring. If the hit rate falls below this value after `monitorWindow` calls, the cache is disabled. Only used when `autoMonitor = true`.
: **Default:** 0.3 (30%)
: **Example:** `@Memoize(autoMonitor = true, minHitRate = 0.5)`

`monitorWindow`
: Number of calls after which the auto-monitor evaluates the hit rate. Only used when `autoMonitor = true`. Set higher for methods with warm-up patterns where early misses are expected.
: **Default:** 100
: **Example:** `@Memoize(autoMonitor = true, monitorWindow = 500)`

### Usage Rules

- The method **must return a value** (non-void, non-Unit). Void methods cause a compile-time error via KSP.
- The method **must not be abstract**. Abstract methods cause a compile-time error.
- Method arguments are used as cache keys via `Arrays.deepHashCode`/`Arrays.deepEquals`. Arguments should have correct `hashCode()`/`equals()` implementations.
- Null return values are cached correctly (via a sentinel pattern).

## @CacheInvalidate

Marks a method as a cache invalidator. When this method executes successfully, the specified memoization caches are cleared. Supports both **full invalidation** (all caches) and **selective invalidation** (specific methods only).

```java
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface CacheInvalidate {
    String[] value() default {};  // Empty = invalidate ALL caches
}
```

### Parameters

`value`
: Names of `@Memoize`-annotated methods whose caches should be invalidated. An empty array (the default) means invalidate ALL caches on the instance.
: **Default:** `{}` (all caches)

### Full Invalidation (Default)

When no method names are specified, ALL memoization caches on the instance are cleared:

```java
public class DataStore {
    @Memoize
    public List<Item> getItems() { /* reads from internal list */ }

    @Memoize
    public int getCount() { /* returns size of internal list */ }

    @CacheInvalidate  // Clears BOTH getItems and getCount caches
    public void reset() { /* clears everything */ }
}
```

### Selective Invalidation

When specific method names are provided, only those methods' caches are invalidated. Other caches remain intact:

```java
public class DataStore {
    @Memoize
    public List<Item> getItems() { /* reads from internal list */ }

    @Memoize
    public int getCount() { /* returns size of internal list */ }

    @Memoize
    public String getStatus() { /* reads status flag, NOT affected by item changes */ }

    // Only invalidates getItems and getCount; getStatus cache is preserved
    @CacheInvalidate({"getItems", "getCount"})
    public void addItem(Item item) { /* modifies internal list */ }

    // Only invalidates getStatus; getItems and getCount caches are preserved
    @CacheInvalidate("getStatus")
    public void setStatus(String status) { /* modifies status flag */ }
}
```

This is a performance optimization: if you know a mutation only affects specific cached queries, you can avoid clearing unrelated caches. This is particularly valuable when a class has many memoized methods and mutations are frequent.

### When to Use Which

| Scenario | Recommendation |
|----------|---------------|
| Mutation affects all cached state | `@CacheInvalidate` (no args) |
| Mutation affects specific methods only | `@CacheInvalidate({"method1", "method2"})` |
| Not sure which caches are affected | `@CacheInvalidate` (no args) -- safer |

### Behavior

- Invalidation happens **after** the method body executes (before the return instruction).
- If the method **throws an exception**, caches are NOT invalidated. This is intentional: if the mutation failed, the state may not have changed.
- Full invalidation calls `MemoCacheManager.invalidateAll()`.
- Selective invalidation calls `MemoCacheManager.invalidate(String[])`, which only clears the named dispatchers. Unknown method names are silently ignored.

## @InvalidateCacheEntry

Evicts **a single row** from a specific `@Memoize` cache instead of clearing the whole cache. Use this when a mutating method only affects one keyed entry and you want every other cached value to stay hot.

```java
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface InvalidateCacheEntry {
    String method();            // target @Memoize method (simple name)
    int[] keys() default {};    // indices of the enclosing method's parameters
                                // used to rebuild the target cache key
}
```

### Parameters

`method`
: Simple name of the `@Memoize` method whose cache holds the entry to evict. Must match an `@Memoize` method on the same class.

`keys`
: Parameter indices of the **enclosing** method whose runtime values should be boxed and passed, in order, as the cache key of the target `@Memoize` method. The selected parameters must match the target method's argument list in count and in boxing type. An empty array means the target takes no arguments.
: **Default:** `{}` (zero-arg target)

### Example

```java
public class TagStore {

    @Memoize(maxSize = 1024)
    public List<Tag> getTags(int itemId) {
        // expensive lookup
    }

    // Updating tags for ONE item evicts only that row from the getTags
    // cache; every other cached itemId stays valid.
    @InvalidateCacheEntry(method = "getTags", keys = {0})
    public void updateTags(int itemId, List<Tag> newTags) {
        // persist newTags
    }

    // Compare: @CacheInvalidate({"getTags"}) here would flush ALL of
    // getTags's entries, losing 1023 perfectly-good rows for no reason.
}
```

### Behaviour

- Eviction happens **after** the method body executes (before the return instruction), exactly like `@CacheInvalidate`.
- If the method **throws an exception**, no eviction occurs -- the failed mutation may not have changed anything.
- The generated bytecode boxes the selected parameters into an `Object[]`, calls `MemoCacheManager.invalidateEntry(targetMethodKey, args)`, which in turn calls `MemoDispatcher.invalidateEntry(key)`. Both the value cache and the parallel TTL timestamp cache are cleaned up.
- **Typed mismatch is a silent no-op.** If you accidentally hand over a parameter whose boxed shape doesn't match the target's key, the `CacheKeyWrapper.equals` check fails, no row is found, and the call quietly does nothing. There is no runtime exception.

### Limitations

- **No overload disambiguation.** If the class contains several `@Memoize` methods with the same simple name, the plugin resolves `method = "…"` to the first overload it finds. Use `@CacheInvalidate` instead when the target is overloaded.
- **Not repeatable.** Each method can carry at most one `@InvalidateCacheEntry`. If you need to evict single rows from several caches from the same mutating method, combine it with `@CacheInvalidate({"otherMethod"})` or split the work across helpers.
- **Indices are trusted, not checked against the target's signature.** A `keys` list that picks the wrong parameters compiles fine -- you'll see a silent miss at runtime instead of an eviction.

## @CacheKey

Specifies which field of a parameter to use for cache key construction. Applied to method parameters.

```java
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface CacheKey {
    String value() default "";
}
```

### Example

```java
@Memoize
public Route findRoute(@CacheKey("id") Location start, @CacheKey("id") Location end) {
    // Only start.id and end.id are used as cache keys,
    // not the entire Location objects
}
```

:::{note}
`@CacheKey` field extraction is defined in the annotation API but **not yet implemented** in the ASM transformation. Currently, entire parameter objects are used as keys. See [Limitations](limitations.md).
:::

## Enums

### CacheScope

```java
public enum CacheScope {
    INSTANCE,  // Per-object cache (default)
    CLASS      // Static cache shared across instances
}
```

### EvictionPolicy

```java
public enum EvictionPolicy {
    LRU,   // Least Recently Used (default). Recency-aware.
    FIFO,  // First-In-First-Out. Cheapest hits; ignores locality.
    LFU,   // Least Frequently Used. Best for skewed workloads.
    NONE   // No eviction -- unbounded growth.
}
```

### ThreadSafety

```java
public enum ThreadSafety {
    NONE,          // No synchronization
    SYNCHRONIZED,  // synchronized blocks
    CONCURRENT     // ConcurrentHashMap (default, recommended)
}
```
