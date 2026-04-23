package io.github.sanadlab.annotations;

/**
 * Cache eviction policy for memoized methods. Pick the policy that best matches
 * the access pattern of the method being cached:
 *
 * <ul>
 *   <li><b>LRU</b> -- recent items stay hot. Best default for most workloads
 *       with temporal locality, at the cost of re-linking the LinkedHashMap
 *       node on every hit.</li>
 *   <li><b>FIFO</b> -- insertion order only. No per-hit bookkeeping, so hits
 *       are cheaper than LRU. Use when every argument is roughly equally likely
 *       and locality is weak, or when you care about the tightest possible hot
 *       path.</li>
 *   <li><b>LFU</b> -- counts how often each key is used and evicts the
 *       least-frequent entry first. Best for Zipfian / skewed access where a
 *       small fraction of arguments account for most of the calls.</li>
 *   <li><b>NONE</b> -- unbounded. No eviction at all; the cache grows forever
 *       until explicitly invalidated. Only safe when the key space is tightly
 *       bounded by construction.</li>
 * </ul>
 */
public enum EvictionPolicy {
    /**
     * Least Recently Used eviction. When the cache reaches maxSize,
     * the least recently accessed entry is evicted. Good general-purpose
     * default when the workload exhibits temporal locality.
     */
    LRU,

    /**
     * First-In-First-Out eviction. When the cache reaches maxSize, the
     * earliest-inserted entry is evicted regardless of how often it has been
     * accessed. Hits do not re-order the cache, so the hot path is cheaper
     * than LRU at the cost of ignoring locality.
     */
    FIFO,

    /**
     * Least Frequently Used eviction. Each key carries a frequency counter
     * incremented on every hit; evictions target the lowest-frequency entry
     * (ties broken by insertion order). Excellent hit rate on skewed
     * workloads where a few arguments dominate; higher per-operation overhead
     * than LRU/FIFO due to the frequency bookkeeping.
     */
    LFU,

    /**
     * No automatic eviction. Cache grows unbounded until explicitly cleared.
     * Use with caution on memory-constrained devices.
     */
    NONE
}
