package io.github.sanadlab.annotations;

/**
 * Thread safety strategy for the memoization cache.
 */
public enum ThreadSafety {
    /**
     * No synchronization. Use only when the method is called from a single thread.
     */
    NONE,

    /**
     * Synchronized access via intrinsic locks. Simple but higher contention.
     */
    SYNCHRONIZED,

    /**
     * ConcurrentHashMap-based. Lock-free reads, bucket-level write locks.
     * Recommended default for most use cases.
     */
    CONCURRENT
}
