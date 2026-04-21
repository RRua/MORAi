package dev.memoize.runtime;

/**
 * Destination for log messages emitted by {@link MemoLogger}.
 *
 * <p>Implementations must be thread-safe. The runtime ships with a {@code ConsoleSink}
 * (prints to stderr) and, when running on Android, an auto-detected Logcat sink.
 * Users may install their own via {@link MemoLogger#setSink(LogSink)}.
 */
public interface LogSink {
    void write(LogLevel level, String tag, String message, Throwable thrown);
}
