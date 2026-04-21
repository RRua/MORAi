# OverheadBenchmark

`OverheadBenchmark` is the fourth benchmark suite in the `benchmark_demo`
project. Unlike `AlgorithmBenchmark`, `PolicyBenchmark`, and
`HitRateBenchmark` -- which all measure memoization in the context of
real, algorithmically interesting workloads -- this suite is designed to
answer a narrower and more mechanical question:

> **What is the per-call overhead of the memoize library as a function of
> the hit rate it actually sees?**

The output is a single four-row table that directly informs the
`@Memoize(autoMonitor = true, minHitRate = ...)` setting for real
production code: every row below the observed break-even hit rate is a
workload where the library costs more than it saves, and `autoMonitor`
should be configured to intervene.

## TL;DR

```bash
cd benchmark_demo
./gradlew :microbenchmark:connectedReleaseAndroidTest \
    --tests com.memoize.bench.OverheadBenchmark
```

Then compare each `memoized_hr_XX` median to the `baseline` median. The
per-call overhead is:

```
overhead_per_call_ns = (memoized_ns - baseline_ns) / 100
```

Negative values are a speedup (compute savings outweigh wrapper cost);
positive values mean the library is costing you time. The hit rate at
which this value crosses zero is the **empirical break-even point**.

## Why a dedicated suite

Measuring "library overhead" on a real algorithm is confounded: the
algorithm's cost varies with the input, the recursion cascades through
the cache, and the hit rate is whatever the algorithm's natural access
pattern produces. That makes it impossible to attribute a measured time
delta cleanly to the library rather than to the workload.

`OverheadBenchmark` removes every one of those confounds:

1. **A synthetic target** (`OverheadLib.expensiveCompute`) whose compute
   cost is constant per call (a 500-iteration mixing loop) and
   independent of the argument.
2. **No recursion**, so the cache does not cascade through subproblems.
   Each call is exactly one cache lookup, so the per-call overhead of
   the library is observed in isolation.
3. **Exact hit-rate control** via a hot / cold split and pre-populated
   cache, so that the measurement at a given hit rate is reproducible
   and directly comparable across policies and configurations.

With these confounds removed, the delta `(memoized - baseline)` is
entirely attributable to the library's per-call wrapper cost minus the
compute savings it earned by skipping `expensiveCompute` on the hits.

## Architecture

Three files are added to the project for this benchmark:

| File | Purpose |
|---|---|
| `lib/src/main/java/com/memoize/bench/OverheadLib.java` | Baseline library with a single synthetic compute method. No `@Memoize`. |
| `lib/src/main/java/com/memoize/bench/OverheadLibLru.java` | Memoized subclass overriding `expensiveCompute` with `@Memoize(maxSize = 128, eviction = LRU, recordStats = true)`. |
| `microbenchmark/src/androidTest/java/com/memoize/bench/OverheadBenchmark.java` | The five `@Test` methods (1 baseline + 4 hit-rate regimes). |

The memoized variant uses the same inheritance trick as the rest of the
suite: it `@Override`s `expensiveCompute` with a single-line `return
super.expensiveCompute(seed)` body and attaches the annotation. At
runtime, the memoize plugin rewrites the override to install the
cache-check and cache-store wrappers at method entry and exit.

### Target method

```java
public long expensiveCompute(int seed) {
    long acc = seed;
    for (int i = 0; i < 500; i++) {
        acc = acc * 31L + i;
        acc ^= acc >>> 13;
    }
    return acc;
}
```

Properties that matter for the measurement:

- **Non-recursive**, so the cache is not implicitly cascaded through
  subproblems.
- **Constant per-call cost**: the 500-iteration loop runs the same
  number of arithmetic operations regardless of `seed`, so the measured
  baseline is insensitive to the workload's argument distribution.
- **Cheap but non-trivial**: on a Pixel-class device, one call is on
  the order of a microsecond -- small enough for wrapper overhead to
  be measurable at low hit rates, large enough for compute savings to
  dominate at high hit rates.
- **Pure** with respect to `seed` with no side effects, which is the
  minimum precondition for `@Memoize` to be correct.

### Memoized variant

```java
public class OverheadLibLru extends OverheadLib {

    @Override
    @Memoize(maxSize = 128, eviction = EvictionPolicy.LRU, recordStats = true)
    public long expensiveCompute(int seed) {
        return super.expensiveCompute(seed);
    }
}
```

The `maxSize = 128` is chosen deliberately: it is large enough that
within one benchmark iteration (`100` calls + `8` hot keys = `108`
entries worst-case) no cold key can evict any hot key. This removes
eviction pressure as a variable, so the only thing varying across rows
is the hit rate of the workload itself.

`recordStats = true` enables per-dispatcher hit/miss counting through
`CacheStats`, which is used as a cross-check on the observed hit rate
(see [Cross-validation](#cross-validation-with-recordstats)).

## Hit-rate control

The benchmark enforces each target hit rate -- **75%**, **50%**,
**25%**, and **0%** -- through three cooperating mechanisms. Together
they guarantee that the observed hit rate equals the target from the
first measured call.

### 1. Hot / cold key partition

```java
private static final int[] HOT_KEYS  = {1, 2, 3, 4, 5, 6, 7, 8};
private static final int   COLD_BASE = 100_000;
```

A fixed pool of 8 *hot* keys always maps to a cached row; they are used
as the "hit" source in every workload. *Cold* keys are drawn from a
monotonically increasing range starting at `COLD_BASE`, so every cold
call within an iteration is a fresh miss. The two ranges never overlap
(`100_000 >> 8`), so hot and cold sets are disjoint by construction.

### 2. Pre-populated cache via `pauseTiming`

Each iteration of `state.keepRunning()` constructs a fresh
`OverheadLibLru`, which means the cache starts empty. If the hot keys
were inserted during the measured region, the hit rate would start at
0% (cache empty) and converge toward the target only after 8 calls,
biasing every row low.

The benchmark pauses the Jetpack Benchmark timer around the warm-up:

```java
state.pauseTiming();
OverheadLibLru lib = new OverheadLibLru();
for (int k : HOT_KEYS) lib.expensiveCompute(k);
state.resumeTiming();

for (int k : workload) sink = lib.expensiveCompute(k);
```

After the `resumeTiming()` call, the cache contains exactly the 8 hot
keys, so the first measured call is immediately at the target hit rate.

### 3. Workload composition and shuffle

```java
private static int[] buildWorkload(int hitRatePercent) {
    int hits = CALLS_PER_ITER * hitRatePercent / 100;
    int[] out = new int[CALLS_PER_ITER];
    int hotIdx = 0, coldIdx = 0;
    for (int i = 0; i < CALLS_PER_ITER; i++) {
        if (i < hits) {
            out[i] = HOT_KEYS[hotIdx++ % HOT_KEYS.length];
        } else {
            out[i] = COLD_BASE + coldIdx++;
        }
    }
    // Deterministic Fisher-Yates shuffle keyed on the hit rate.
    Random rnd = new Random(42L + hitRatePercent);
    for (int i = out.length - 1; i > 0; i--) {
        int j = rnd.nextInt(i + 1);
        int tmp = out[i]; out[i] = out[j]; out[j] = tmp;
    }
    return out;
}
```

The first loop builds a 100-element array with exactly
`CALLS_PER_ITER * hitRatePercent / 100` hot entries and the remaining
slots filled with unique cold values. The second loop deterministically
shuffles the array so hot and cold calls are interleaved rather than
bunched, which more closely matches a realistic usage pattern.

Because the cold keys are produced by a strictly increasing counter and
`maxSize = 128` is above the worst-case total (`8 + 100 = 108`), no
cold key can ever coincidentally hit an earlier cold key within the
same iteration, so every cold call is a guaranteed miss.

## The five `@Test` methods

| Test | Workload | Hit rate |
|---|---|---|
| `baseline()` | 100 unique cold keys, `OverheadLib` (no `@Memoize`) | n/a |
| `memoized_hr_75()` | 75 hot / 25 cold, `OverheadLibLru` | 75% |
| `memoized_hr_50()` | 50 hot / 50 cold, `OverheadLibLru` | 50% |
| `memoized_hr_25()` | 25 hot / 75 cold, `OverheadLibLru` | 25% |
| `memoized_hr_0()`  | 0 hot / 100 cold, `OverheadLibLru`  | 0% |

The `baseline` row measures the un-memoized library under a 100-call
workload of cold keys. Since `expensiveCompute` is independent of its
input, the baseline time is directly comparable to every memoized row,
regardless of the workload's hit-rate composition.

## Reading the output

Jetpack Benchmark reports `metrics.timeNs.median` per `@Test`. The
per-call overhead is the difference of medians divided by the number of
calls:

```
overhead_per_call_ns = (memoized_ns - baseline_ns) / 100
```

A negative value means the library is *saving* time: compute avoidance
on hits more than pays for the wrapper cost on every call. A positive
value means the library is *costing* time: wrapper cost exceeds compute
savings.

### Expected shape

| Hit rate | Expected sign of overhead | Reason |
|---|---|---|
| 75% | Strongly negative (speedup) | 3/4 calls skip the full compute -- the savings dominate. |
| 50% | Near zero | Compute savings and wrapper cost roughly cancel. |
| 25% | Positive (cost) | Only 1/4 calls is saved; every call pays wrapper cost. |
| 0%  | Strongly positive (worst case) | Every call pays wrapper cost on top of the full compute. |

### The break-even point

The hit rate at which `overhead_per_call_ns` crosses zero is the
**empirical break-even**: any method whose observed hit rate stays
above it benefits from `@Memoize`; any method below it is hurt by it.
Numerically, the break-even hit rate satisfies

```
compute_cost * hit_rate ≈ wrapper_cost_per_call
↔  hit_rate ≈ wrapper_cost_per_call / compute_cost
```

So for a target method whose `compute_cost` is larger than
`expensiveCompute`, the break-even hit rate is *lower* than the one
measured here; for a cheaper method it is *higher*. The benchmark
numbers are the value for the reference cost of `~1 µs`; use them as
a rule of thumb and re-measure with your own target if you need
precise numbers for a much cheaper or much more expensive method.

### Applying the break-even to `autoMonitor`

Once you know the break-even hit rate for your target compute cost,
pick

```java
@Memoize(
    autoMonitor = true,
    minHitRate  = <break-even> + epsilon,
    monitorWindow = 100
)
```

After `monitorWindow` calls, if the observed hit rate is below
`minHitRate`, the dispatcher sets its `disabled` flag, clears its
caches, and from that point forward every call bypasses the library
entirely. This bounds the worst-case overhead of a mis-classified
memoization candidate to one monitor window's worth of observations.

## Cross-validation with `recordStats`

`OverheadLibLru` enables `recordStats = true`, which means every
dispatcher owns a `CacheStats` tracker that counts hits, misses, and
evictions atomically. To verify that the target hit rate is actually
achieved, enable `MemoLogger.setLevel(LogLevel.INFO)` in a test
harness and print the per-dispatcher stats at the end of a run:

```java
MemoLogger.setLevel(LogLevel.INFO);
MemoCacheManager manager = /* retrieved via reflection from __memoCacheManager */;
System.out.println(manager.dumpReport());
```

The reported `hitRate` for the `expensiveCompute` dispatcher should
match the target within rounding. Discrepancies indicate either that
the warm-up step did not run (so hot keys were missed in the first
calls) or that eviction was triggered (so hot keys were pushed out of
the cache mid-iteration). Neither should happen with the default
configuration, but this is the check you run when the numbers look
surprising.

## Caveats

- **The measured overhead is for `expensiveCompute` specifically.** If
  your target method is much cheaper, the break-even hit rate is
  higher; if much more expensive, lower. Use the formula in
  [Reading the output](#reading-the-output) to scale.
- **100 calls per iteration is arbitrary.** Increasing it reduces
  per-call variance but increases total benchmark wall-clock time. The
  default is a reasonable compromise; adjust `CALLS_PER_ITER` in
  `OverheadBenchmark.java` if you need tighter confidence intervals.
- **Pixel-class devices only.** Jetpack Benchmark refuses emulators by
  default, and vendor-customised ROMs with non-standard CPU scheduling
  policies can skew the absolute numbers. The relative ordering
  (`75% < 50% < 25% < 0%` in per-call cost) is robust across
  hardware.
- **No cold-start.** This benchmark measures the steady-state
  per-call cost. The one-time cost of constructing the dispatcher,
  registering it with the manager, and allocating the backing cache is
  not included, because it occurs during the paused warm-up.

## See also

- [`README.md`](README.md) -- the top-level overview of the benchmark
  project and the other three suites.
- [`../memoize-lib/docs/observability.md`](../memoize-lib/docs/observability.md)
  -- documentation of `MemoLogger`, `MemoMetrics`, and the
  `recordStats` subsystem this benchmark relies on for hit-rate
  cross-validation.
- [`../memoize-lib/docs/annotations.md`](../memoize-lib/docs/annotations.md)
  -- reference for every `@Memoize` parameter, including
  `autoMonitor`, `minHitRate`, and `monitorWindow` discussed above.
