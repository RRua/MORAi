package dev.memoize.runtime;

import java.lang.reflect.Method;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Static logging facade for the memoize runtime.
 *
 * <p><b>Performance:</b> the global level is a {@code volatile} field and every call
 * site in the runtime is guarded by an {@code isLoggable()} check before any string
 * formatting happens, so the cost of a disabled log call is a single volatile read
 * plus an integer compare. The default level is {@link LogLevel#OFF} which short
 * circuits before any argument evaluation, giving effectively zero overhead for
 * production builds that never enable logging.
 *
 * <p><b>Sinks:</b> on first use a default sink is chosen. If {@code android.util.Log}
 * is on the classpath the logger routes through Logcat; otherwise it prints to
 * {@code System.err}. Users may install a custom sink via {@link #setSink(LogSink)}.
 *
 * <p><b>Thread-safety:</b> all public methods are safe to call concurrently.
 */
public final class MemoLogger {

    public static final String TAG = "Memoize";

    private static volatile LogLevel level = LogLevel.OFF;
    private static volatile LogSink sink;
    private static final ReentrantLock initLock = new ReentrantLock();

    private MemoLogger() {}

    /** @return the currently active verbosity level. */
    public static LogLevel getLevel() {
        return level;
    }

    /**
     * Set the global verbosity level. Passing {@link LogLevel#OFF} disables all
     * logging with near-zero overhead at call sites.
     */
    public static void setLevel(LogLevel newLevel) {
        if (newLevel == null) throw new IllegalArgumentException("level");
        level = newLevel;
    }

    /**
     * Fast guard check used by instrumentation and library code to skip log-call
     * argument construction when a given level is disabled.
     */
    public static boolean isLoggable(LogLevel candidate) {
        return candidate.severity <= level.severity && level != LogLevel.OFF;
    }

    /** Install a custom sink. Pass {@code null} to restore the default sink. */
    public static void setSink(LogSink newSink) {
        sink = newSink;
    }

    // --- Log methods. Callers MUST guard with isLoggable() to avoid format costs. ---

    public static void error(String msg, Throwable t) { log(LogLevel.ERROR, msg, t); }
    public static void warn(String msg)  { log(LogLevel.WARN,  msg, null); }
    public static void info(String msg)  { log(LogLevel.INFO,  msg, null); }
    public static void debug(String msg) { log(LogLevel.DEBUG, msg, null); }
    public static void trace(String msg) { log(LogLevel.TRACE, msg, null); }

    private static void log(LogLevel candidate, String msg, Throwable t) {
        if (!isLoggable(candidate)) return;
        LogSink s = sink;
        if (s == null) s = installDefaultSink();
        try {
            s.write(candidate, TAG, msg, t);
        } catch (Throwable ignored) {
            // Logging must never break the library.
        }
    }

    private static LogSink installDefaultSink() {
        initLock.lock();
        try {
            if (sink != null) return sink;
            LogSink chosen = tryAndroidSink();
            if (chosen == null) chosen = new ConsoleSink();
            sink = chosen;
            return chosen;
        } finally {
            initLock.unlock();
        }
    }

    /** Reflectively bind to android.util.Log; returns null when not on Android. */
    private static LogSink tryAndroidSink() {
        try {
            Class<?> logClass = Class.forName("android.util.Log");
            final Method v = logClass.getMethod("v", String.class, String.class);
            final Method d = logClass.getMethod("d", String.class, String.class);
            final Method i = logClass.getMethod("i", String.class, String.class);
            final Method w = logClass.getMethod("w", String.class, String.class);
            final Method e = logClass.getMethod("e", String.class, String.class, Throwable.class);
            return (level, tag, msg, t) -> {
                try {
                    switch (level) {
                        case TRACE: v.invoke(null, tag, msg); break;
                        case DEBUG: d.invoke(null, tag, msg); break;
                        case INFO:  i.invoke(null, tag, msg); break;
                        case WARN:  w.invoke(null, tag, msg); break;
                        case ERROR: e.invoke(null, tag, msg, t); break;
                        default: break;
                    }
                } catch (Throwable ignored) {}
            };
        } catch (Throwable notAndroid) {
            return null;
        }
    }

    /** Default JVM sink: prints to stderr with a level prefix. */
    private static final class ConsoleSink implements LogSink {
        @Override
        public void write(LogLevel level, String tag, String message, Throwable thrown) {
            System.err.println("[" + tag + "/" + level + "] " + message);
            if (thrown != null) thrown.printStackTrace(System.err);
        }
    }
}
