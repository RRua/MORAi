package dev.memoize.runtime;

/**
 * Log verbosity levels for {@link MemoLogger}. Ordered from least to most verbose.
 *
 * <p>OFF disables all logging with zero-cost guard checks. The default level is OFF
 * so that logging has no impact on production performance unless explicitly enabled.
 */
public enum LogLevel {
    /** No log output at all. Guard checks short-circuit immediately. */
    OFF(0),
    /** Only unexpected errors (e.g. exceptions thrown inside compute()). */
    ERROR(1),
    /** Warnings such as auto-monitor disabling a cache. */
    WARN(2),
    /** High-level lifecycle events: dispatcher creation, invalidations, reports. */
    INFO(3),
    /** Per-call events: hits, misses, evictions. May produce high volume. */
    DEBUG(4),
    /** Everything, including key hashes and timings. Very noisy. */
    TRACE(5);

    final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }
}
