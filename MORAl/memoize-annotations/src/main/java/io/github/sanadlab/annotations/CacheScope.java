package io.github.sanadlab.annotations;

/**
 * Defines the scope/lifetime of a memoization cache.
 */
public enum CacheScope {
    /**
     * Cache is per-instance. Each object has its own cache.
     * Appropriate for methods that read instance state.
     */
    INSTANCE,

    /**
     * Cache is shared across all instances (static).
     * Appropriate for pure functions that depend only on arguments.
     */
    CLASS
}
