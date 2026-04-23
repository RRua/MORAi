# API Reference

## Annotations

### io.github.sanadlab.annotations

| Annotation | Target | Description |
|-----------|--------|-------------|
| `@Memoize` | Method | Marks a method for automatic memoization |
| `@CacheInvalidate` | Method | Marks a method that clears instance caches (all or selective) |
| `@CacheKey` | Parameter | Specifies field to extract from parameter for cache key |

### @Memoize Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `maxSize` | `int` | 128 | Maximum cache entries before eviction |
| `expireAfterWrite` | `long` | -1 | TTL in milliseconds (-1 = no expiry) |
| `scope` | `CacheScope` | `INSTANCE` | Cache lifetime scope |
| `eviction` | `EvictionPolicy` | `LRU` | Eviction strategy |
| `threadSafety` | `ThreadSafety` | `CONCURRENT` | Synchronization strategy |
| `recordStats` | `boolean` | false | Enable hit/miss/eviction tracking |

### @CacheInvalidate Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | `String[]` | `{}` (empty) | Method names to invalidate. Empty = invalidate ALL caches. |

**Examples:**
- `@CacheInvalidate` -- invalidates all caches on the instance
- `@CacheInvalidate("search")` -- invalidates only the `search` method's cache
- `@CacheInvalidate({"search", "length"})` -- invalidates `search` and `length` caches

### Enums

| Enum | Values |
|------|--------|
| `CacheScope` | `INSTANCE`, `CLASS` |
| `EvictionPolicy` | `LRU`, `NONE` |
| `ThreadSafety` | `NONE`, `SYNCHRONIZED`, `CONCURRENT` |

## Runtime Classes

### io.github.sanadlab.runtime

| Class | Description |
|-------|-------------|
| `MemoCache<K,V>` | Cache interface: `get(K)`, `put(K,V)`, `clear()`, `size()` |
| `LruMemoCache<K,V>` | LRU cache (LinkedHashMap, synchronized) |
| `UnsynchronizedLruMemoCache<K,V>` | LRU cache (LinkedHashMap, NO synchronization) |
| `ConcurrentMemoCache<K,V>` | Lock-free cache (ConcurrentHashMap, no eviction) |
| `CacheKeyWrapper` | Wraps `Object[]` as cache key with `deepHashCode`/`deepEquals` |
| `MemoDispatcher` | Per-method cache coordinator (called by injected bytecode) |
| `MemoCacheManager` | Per-instance manager: registers dispatchers, bulk invalidation |
| `CacheStats` | Thread-safe hit/miss/eviction counters (AtomicLong) |

### MemoDispatcher Methods

| Method | Called By | Description |
|--------|----------|-------------|
| `buildKey(Object[])` | ASM entry | Wraps args in `CacheKeyWrapper` |
| `getIfCached(CacheKeyWrapper)` | ASM entry | Returns cached value or null (miss) |
| `putInCache(CacheKeyWrapper, Object)` | ASM exit | Stores result in cache |
| `unwrap(Object)` | ASM entry | Converts `NULL_SENTINEL` back to null |
| `create(String,int,long,String,String,boolean)` | ASM constructor | Factory: creates dispatcher from annotation params (string enums) |
| `invalidate()` | `MemoCacheManager` | Clears this dispatcher's cache |
| `invoke(Object[], Callable)` | Direct use | Combined check-compute-store (for testing) |
| `getStats()` | User code | Returns `CacheStats` or null |
| `size()` | User code | Current number of cached entries |

### MemoCacheManager Methods

| Method | Description |
|--------|-------------|
| `register(String, MemoDispatcher)` | Register a dispatcher by method name |
| `getDispatcher(String)` | Look up dispatcher by method name |
| `invalidateAll()` | Clear ALL registered dispatchers' caches |
| `invalidate(String[])` | Clear only the named dispatchers' caches (selective) |
| `getDispatchers()` | Get all registered dispatchers (Map) |
| `totalSize()` | Sum of entries across all dispatchers |

### CacheStats Methods

| Method | Description |
|--------|-------------|
| `recordHit()` | Increment hit counter |
| `recordMiss()` | Increment miss counter |
| `recordEviction()` | Increment eviction counter |
| `getHitCount()` | Total cache hits |
| `getMissCount()` | Total cache misses |
| `getEvictionCount()` | Total evictions |
| `getRequestCount()` | `hits + misses` |
| `getHitRate()` | `hits / (hits + misses)` as double |
| `reset()` | Reset all counters to 0 |

## Gradle Plugin

### Plugin ID

```
id("io.github.sanadlab")
```

### Behavior

- **Android projects**: Registers `MemoizeClassVisitorFactory` via AGP Instrumentation API
- **JVM projects**: Registers post-compilation task via `JvmBytecodeTransformer`
- Scope: project classes only (skips `android.*`, `androidx.*`, `kotlin.*`, `java.*`, `io.github.sanadlab.*`)
- All `@Memoize` parameters are read and passed to `MemoDispatcher.create()`

### Injected Synthetic Fields

For a class with N `@Memoize` methods, the plugin adds:

| Field | Type | Count |
|-------|------|-------|
| `__memoCacheManager` | `MemoCacheManager` | 1 per class |
| `__memoDispatcher_<name>_<hash>` | `MemoDispatcher` | 1 per `@Memoize` method |

The `<hash>` is a 5-hex-digit hash of `name + descriptor`, ensuring overloaded methods get unique fields. All fields are `private synthetic`.

## File Locations

```
memoize-annotations/src/main/java/io/github/sanadlab/annotations/
  ├── Memoize.java
  ├── CacheInvalidate.java
  ├── CacheKey.java
  ├── CacheScope.java
  ├── EvictionPolicy.java
  └── ThreadSafety.java

memoize-runtime/src/main/java/io/github/sanadlab/runtime/
  ├── MemoCache.java
  ├── LruMemoCache.java
  ├── UnsynchronizedLruMemoCache.java
  ├── ConcurrentMemoCache.java
  ├── CacheKeyWrapper.java
  ├── MemoDispatcher.java
  ├── MemoCacheManager.java
  └── CacheStats.java

memoize-ksp/src/main/kotlin/io/github/sanadlab/ksp/
  ├── MemoizeProcessorProvider.kt
  └── MemoizeProcessor.kt

memoize-gradle-plugin/src/main/kotlin/io/github/sanadlab/plugin/
  ├── MemoizePlugin.kt
  ├── MemoizeClassVisitorFactory.kt
  ├── MemoizeClassVisitor.kt
  └── JvmBytecodeTransformer.kt
```
