package io.github.sanadlab.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages all MemoDispatcher instances for a single object (or class, for static scope).
 * Provides bulk invalidation: when a @CacheInvalidate method is called,
 * it clears all caches at once.
 *
 * <p>Each class with @Memoize-annotated methods gets one MemoCacheManager field,
 * injected by the ASM bytecode transformation.
 */
public final class MemoCacheManager {

    private final ConcurrentHashMap<String, MemoDispatcher> dispatchers = new ConcurrentHashMap<>();

    /**
     * Register a dispatcher for a memoized method.
     *
     * @param methodName the method name (used as key)
     * @param dispatcher the dispatcher handling that method's cache
     */
    public void register(String methodName, MemoDispatcher dispatcher) {
        dispatchers.put(methodName, dispatcher);
    }

    /**
     * Get a dispatcher by method name.
     */
    public MemoDispatcher getDispatcher(String methodName) {
        return dispatchers.get(methodName);
    }

    /**
     * Invalidate ALL caches managed by this manager.
     * Called by @CacheInvalidate (with no arguments) annotated methods.
     */
    public void invalidateAll() {
        for (MemoDispatcher dispatcher : dispatchers.values()) {
            dispatcher.invalidate();
        }
    }

    /**
     * Invalidate caches for specific memoized methods.
     * Called by @CacheInvalidate({"method1", "method2"}) annotated methods.
     *
     * @param methodNames names of @Memoize methods whose caches to clear
     */
    public void invalidate(String[] methodNames) {
        for (String name : methodNames) {
            MemoDispatcher dispatcher = dispatchers.get(name);
            if (dispatcher != null) {
                dispatcher.invalidate();
            }
        }
    }

    /**
     * Drop a single cached entry from a specific method's dispatcher.
     * Called by bytecode generated for {@code @InvalidateCacheEntry}. The
     * {@code args} array must match the argument list of the target @Memoize
     * method in length and boxing, otherwise the removal silently no-ops
     * (the key simply won't be found).
     *
     * <p>Unknown method keys are silently ignored -- this mirrors the
     * behaviour of {@link #invalidate(String[])}.
     */
    public void invalidateEntry(String methodKey, Object[] args) {
        MemoDispatcher dispatcher = dispatchers.get(methodKey);
        if (dispatcher != null) {
            dispatcher.invalidateEntry(args);
        }
    }

    /**
     * Get all registered dispatchers (for stats/debugging).
     */
    public Map<String, MemoDispatcher> getDispatchers() {
        return dispatchers;
    }

    /**
     * Total number of cached entries across all dispatchers.
     */
    public int totalSize() {
        int total = 0;
        for (MemoDispatcher d : dispatchers.values()) {
            total += d.size();
        }
        return total;
    }

    /**
     * Build a multi-line report of every managed dispatcher with its stats and
     * timing metrics. Intended for debugging / research output; cheap to call
     * (O(dispatchers)) but allocates a string builder so do not invoke on a hot
     * path.
     */
    public String dumpReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("MemoCacheManager report (").append(dispatchers.size()).append(" dispatchers)\n");
        for (Map.Entry<String, MemoDispatcher> e : dispatchers.entrySet()) {
            MemoDispatcher d = e.getValue();
            sb.append("  ").append(e.getKey())
              .append(" size=").append(d.size())
              .append(" disabled=").append(d.isDisabled());
            CacheStats s = d.getStats();
            if (s != null) sb.append(' ').append(s);
            MemoMetrics m = d.getMetrics();
            if (m != null) sb.append(' ').append(m);
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Emit the report through {@link MemoLogger} at INFO level. */
    public void logReport() {
        if (MemoLogger.isLoggable(LogLevel.INFO)) {
            MemoLogger.info(dumpReport());
        }
    }
}
