# Observability

The runtime ships with an embedded logging facade and per-dispatcher timing metrics
so you can trace cache behaviour on-device without wiring up an external logger or
profiler. Both subsystems are **off by default** and cost effectively nothing at
runtime until you explicitly enable them.

- `MemoLogger` -- static facade, volatile level, pluggable sinks
- `LogLevel` -- OFF / ERROR / WARN / INFO / DEBUG / TRACE
- `LogSink` -- pluggable destination interface (Android Logcat auto-detected)
- `MemoMetrics` -- per-dispatcher compute / lookup nanos, estimated time saved
- `MemoCacheManager.dumpReport()` / `logReport()` -- end-of-run summary

Source: [`memoize-runtime/src/main/java/dev/memoize/runtime/`](https://github.com)

## Why this lives inside the runtime

Memoization is a *silent* optimisation: code that used to run is now skipped, so
bugs and bad fits (low hit rate, cache thrash, unexpected TTL expiries) are
invisible unless you instrument them. Pointing users at an external logger works
on JVM but is awkward on Android, where the runtime must also avoid dragging in
SLF4J or any other third-party dependency. An embedded facade keeps the runtime
dependency-free while still giving researchers a single switch to turn on full
trace output during a benchmarking run.

## Log levels

```java
public enum LogLevel {
    OFF,    // no output, guard checks short-circuit
    ERROR,  // unexpected exceptions inside compute()
    WARN,   // auto-monitor disabled a cache, etc.
    INFO,   // dispatcher creation, invalidations, reports
    DEBUG,  // per-call miss / TTL expiry events
    TRACE   // per-call hits, key hashes -- very noisy
}
```

The default is `OFF`. Every call site inside the runtime is wrapped in an
`isLoggable(level)` guard, so at the default setting a log call boils down to a
single **volatile read + integer compare** and no argument evaluation,
formatting, or sink dispatch happens.

## Enabling and routing

```java
import dev.memoize.runtime.LogLevel;
import dev.memoize.runtime.MemoLogger;

// Enable high-level lifecycle logging + timing metrics collection.
MemoLogger.setLevel(LogLevel.INFO);

// Crank it up when hunting a bug.
MemoLogger.setLevel(LogLevel.TRACE);

// Turn it off again (default state, near-zero overhead).
MemoLogger.setLevel(LogLevel.OFF);
```

### Sinks

On first use the logger picks a default sink:

- On Android, it reflectively binds to `android.util.Log` and routes output to
  Logcat under the tag `Memoize`. This is done via reflection so the runtime
  JAR does not need to depend on the Android SDK.
- On a plain JVM, it falls back to a `ConsoleSink` that prints to `stderr`.

You can install your own sink for integration with SLF4J, Timber, file logging,
or a test buffer:

```java
MemoLogger.setSink((level, tag, message, thrown) -> {
    // forward to whatever you like; implementations must be thread-safe.
});
```

Pass `null` to restore the auto-detected default.

## What gets logged at each level

| Level | Events |
|-------|--------|
| `ERROR` | Exceptions thrown by the original method body on a cache miss. The exception is still rethrown; logging never swallows it. |
| `WARN`  | Auto-monitor disables a cache because the hit rate dropped below `minHitRate`. Message includes the observed rate and request count. |
| `INFO`  | Dispatcher construction (`methodName`, `maxSize`, `ttlMs`, `eviction`, `threadSafety`, `stats`, `autoMonitor`) and `invalidate()` calls. Also emits the full report from `MemoCacheManager.logReport()`. |
| `DEBUG` | Every cache **miss** and every TTL expiry, tagged with `methodName` and `key.hashCode()`. |
| `TRACE` | Every cache **hit**, plus auto-monitor bypass events when a dispatcher is disabled. |

Because DEBUG and TRACE run on the hot path, they will dominate logcat on any
non-trivial workload -- use them only for short repros.

## Timing metrics

Whenever `INFO` (or any higher level) is active, each `MemoDispatcher` records
timing into a `MemoMetrics` object:

```java
public final class MemoMetrics {
    long   getTotalComputeNanos();  // sum of compute() time on misses
    long   getTotalLookupNanos();   // sum of get/put time on hits
    long   getComputeSamples();
    long   getLookupSamples();
    double getMeanComputeNanos();
    double getMeanLookupNanos();

    // Hits * mean compute  -  total lookup cost.
    // Negative => the cache is losing; positive => savings in nanoseconds.
    long getEstimatedSavedNanos();
}
```

`getEstimatedSavedNanos()` is intentionally rough -- it assumes the mean compute
cost is representative of what each hit would have cost had it missed. For
tight methods that estimate is fine; for workloads with wildly different input
costs you should treat it as a directional signal, not an exact figure.

Metrics are accessible directly:

```java
MemoDispatcher d = manager.getDispatcher("fibonacci");
MemoMetrics m = d.getMetrics();
System.out.println(m);
// MemoMetrics{meanCompute=18345.0ns, meanLookup=42.1ns, savedMs=73.412}
```

## End-of-run reports

`MemoCacheManager` builds a multi-line summary of every dispatcher it owns:

```java
String report = manager.dumpReport();
System.out.println(report);
// MemoCacheManager report (3 dispatchers)
//   fibonacci size=6  disabled=false  CacheStats{hits=312, misses=8, ...}
//                                    MemoMetrics{meanCompute=18345.0ns, savedMs=5.716}
//   isPrime   size=5  disabled=false  ...
//   ...

// or route it through the logger:
manager.logReport();
```

`dumpReport()` allocates a `StringBuilder`, so don't call it on a hot path;
call it once at the end of a benchmark pass.

## Performance implications

| State | Cost per hot-path call |
|-------|------------------------|
| `LogLevel.OFF` (default) | 1 volatile read + 1 int compare. No allocation, no formatting, no dispatch. Effectively free. |
| `LogLevel.INFO` | Same guard cost on hits/misses **plus** two `System.nanoTime()` calls (~20-40 ns each) and two `AtomicLong.addAndGet` updates for metrics. |
| `LogLevel.DEBUG` | INFO cost + one `String` concatenation + sink write per miss. Do not leave enabled in production. |
| `LogLevel.TRACE` | DEBUG cost + one formatted line *per hit*. Dominates CPU on hot methods. Use only for short targeted repros. |

The logger is wrapped in a `try { ... } catch (Throwable ignored) {}` so a
misbehaving sink cannot propagate an exception into the cached method.

## Limitations

- **Global level.** There is a single static `LogLevel` for the whole process;
  you cannot enable `TRACE` for just one dispatcher. If you need per-method
  filtering, install a custom `LogSink` that matches on the log message (all
  entries include the method name).
- **Metric aggregation is per-dispatcher.** There is no cross-method rollup
  beyond `MemoCacheManager.dumpReport()`.
- **`getEstimatedSavedNanos()` is a heuristic** -- see the note above about
  input-dependent cost variance.
- **Reflection-based Android detection.** The Logcat sink is bound lazily and
  reflectively; in aggressively obfuscated builds you may need to add a keep
  rule for `android.util.Log` or install a custom sink explicitly.
