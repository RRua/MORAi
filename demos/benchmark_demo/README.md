# MORAl benchmark demo

A Jetpack-Benchmark harness that quantifies what `@Memoize` is actually
worth on Android. Ships with ten compute-intensive algorithms -- all
recursive, all memoizable, all with heavy overlapping subproblems -- in a
baseline and three memoized variants (`LRU`, `FIFO`, `LFU`), plus four
benchmark suites that sweep different dimensions.

## What's measured

| Benchmark class | What it answers |
|---|---|
| `AlgorithmBenchmark` | "How much does turning a naive recursive implementation into a `@Memoize`-annotated one with one-line change actually save, per algorithm?" |
| `PolicyBenchmark` | "Given that the data fits, which eviction policy has the cheapest hot path? And once the cache is forced to evict, which one keeps the hit rate highest?" |
| `HitRateBenchmark` | "At what hit rate does `@Memoize` stop paying for itself? Where should `autoMonitor` be kicking in?" |
| `OverheadBenchmark` | "What is the per-call overhead of the library at controlled hit rates (75%, 50%, 25%, 0%)? What is the empirical break-even point between compute savings and wrapper cost?" |

## The ten algorithms

All live in `lib/src/main/java/com/memoize/bench/Algorithms.java`. Every
one is pure w.r.t. its argument tuple once the instance state is fixed, so
every one is a legitimate memoization target.

| # | Method | Why it's a memoization target |
|---|---|---|
| 1 | `fibonacci(n)` | Classic exponential recurrence; becomes linear. |
| 2 | `ackermann(m, n)` | Non-primitive-recursive explosion; memoization tames the reachable `(m, n)` lattice. |
| 3 | `catalan(n)` | Direct `C(n) = sum C(i)*C(n-1-i)` recurrence; exponential without a cache. |
| 4 | `binomial(n, k)` | Pascal's recurrence -- overlapping subproblems in a triangle. |
| 5 | `partition(n, k)` | Integer partition count with max-part constraint; textbook DP. |
| 6 | `editDistance(i, j)` | Levenshtein on offset indices over instance-held strings. |
| 7 | `lcs(i, j)` | Longest common subsequence, offset-indexed. |
| 8 | `matrixChain(i, j)` | Min scalar mults for matrix chain mult; `O(n^3)` with memo, `O(2^n)` without. |
| 9 | `knapsack(i, w)` | 0/1 knapsack; `(index, remaining capacity)` key. |
| 10 | `coinChange(i, amount)` | Ways-to-make-change; `(index, remaining amount)` key. |

Argument tuples are deliberately kept primitive (or small offsets into
instance-held arrays/strings) so the `CacheKeyWrapper.deepHashCode` /
`deepEquals` path is cheap and cache-key construction never shows up in
the measurement.

### Memoized variants

`AlgorithmsLru`, `AlgorithmsFifo`, `AlgorithmsLfu` each extend
`Algorithms` and `@Override` every method with just
`return super.method(...)` plus an `@Memoize(eviction = ...)` annotation.
This trick works because javac emits the recursive calls inside
`Algorithms` as `invokevirtual` -- at runtime they dispatch back to the
subclass, so the cache cascades through the entire recursion tree without
any duplicate source code.

## How to run

Prerequisites:
1. Android SDK (`compileSdk` 35 in both modules).
2. A connected Android device -- `androidx.benchmark` refuses to run on
   an emulator by default because results are noisy; we pass
   `androidx.benchmark.suppressErrors=DEBUGGABLE,EMULATOR,UNLOCKED,LOW-BATTERY`
   to allow it during development. Remove those suppressions for the
   final numbers you quote.
3. `MORAl` built and published -- we pick it up via `includeBuild`
   from `../MORAl`, so no extra setup is needed.

```bash
# Build everything (no device needed)
./gradlew :lib:assembleRelease :microbenchmark:assembleRelease

# Run benchmarks on the connected device
./gradlew :microbenchmark:connectedReleaseAndroidTest
```

Results land in
`microbenchmark/build/outputs/connected_android_test_additional_output/`
as per-class JSON, plus a log file with the formatted summary. Every
`@Memoize`-enabled class has `recordStats = true`, so the log also
contains hit/miss counts per dispatcher when `MemoLogger` is at `INFO`
or higher.

## Extracting conclusions

A machine-readable JSON is produced per benchmark class. The simplest
way to read it:

```bash
find microbenchmark/build/outputs -name '*.json' -exec cat {} +
```

Every test method has a `metrics.timeNs.median` field. For a clean
per-algorithm speedup table, pair each `<name>_baseline` with its
corresponding `<name>_memoized_lru` and divide the medians.

Expected shape of the conclusions once you've collected numbers on a
real device -- fill in from your own results:

### 1. Per-algorithm speedup (baseline / memoized_lru)

| Algorithm | Baseline median | Memoized (LRU) median | Speedup |
|---|---|---|---|
| fibonacci(25) |  |  |  |
| ackermann(3, 6) |  |  |  |
| catalan(14) |  |  |  |
| binomial(28, 12) |  |  |  |
| partition(40, 40) |  |  |  |
| editDistance |  |  |  |
| lcs |  |  |  |
| matrixChain |  |  |  |
| knapsack |  |  |  |
| coinChange(0, 100) |  |  |  |

**Expected qualitative result:** every recursion-heavy row should show a
speedup in the hundreds-to-millions range once the cache-warming iteration
dominates. The set is chosen so that none of these is already
pre-tabulated.

### 2. Hot-path cost per policy (no eviction pressure)

`PolicyBenchmark.fibonacci_*` and `PolicyBenchmark.partition_*` exercise
policies under a workload where the cache never overflows, so every run
pays only the per-operation cost of the chosen policy.

| Policy | fibonacci(25) median | partition(40, 40) median |
|---|---|---|
| FIFO |  |  |
| LRU  |  |  |
| LFU  |  |  |

**Expected ordering:** `FIFO < LRU < LFU`. FIFO does no per-hit
bookkeeping, LRU relinks the hit node, LFU additionally updates a
frequency counter + bucket.

### 3. Eviction pressure with a skewed key space

`PolicyBenchmark.coinChange_*` deliberately uses a 7-denomination set
and a deeper recursion so the `(index, amount)` key space overflows the
cache. Here LFU should out-hit LRU because a small number of
`(index, amount)` pairs dominate the call graph.

### 4. Hit-rate break-even

`HitRateBenchmark` runs the same `fibonacci` workload against four input
distributions (HOT, WARM, COLD, VERY_COLD). Divide each memoized row by
its baseline counterpart:

| Distribution | baseline | memoized_lru | ratio |
|---|---|---|---|
| HOT | | | strong speedup |
| WARM | | | speedup |
| COLD | | | mild speedup |
| VERY_COLD | | | expected ratio >= 1.0 (overhead) |

When `ratio` crosses 1.0 you've found the break-even: below it, caching
is costing you more than it saves. That's where `@Memoize(autoMonitor =
true)` should kick in -- set `minHitRate` just above the observed ratio's
crossover hit rate (which the class embedded `recordStats = true` makes
directly readable).

### 5. Per-call overhead at controlled hit rates

`OverheadBenchmark` isolates the library's per-call wrapper cost by
driving a fixed synthetic workload at four precise hit-rate points
(75%, 50%, 25%, 0%). The output is a four-row table whose per-call
delta against a single baseline identifies the empirical break-even hit
rate below which `@Memoize` costs more than it saves.

See [`OVERHEAD_BENCHMARK.md`](OVERHEAD_BENCHMARK.md) for the full
description: the synthetic target design, the three hit-rate control
mechanisms (hot/cold partition, `pauseTiming` warm-up, shuffled
workload), the five `@Test` methods, the interpretation formula, and
how to turn the measured break-even into an `autoMonitor` configuration.

## Memory overhead

`androidx.benchmark` reports allocation counts per iteration under
`metrics.allocationCount`. The baseline rows give the recursion-only
allocation footprint; the memoized rows add (per unique key):

- 1 `CacheKeyWrapper` with its boxed-arg `Object[]`
- 1 boxed return value (when the method returns a primitive)
- 1 `LinkedHashMap.Entry` (`LRU`/`FIFO`) or 2-3 `HashMap.Entry` nodes
  (`LFU`, which owns three auxiliary maps)
- plus a single `MemoDispatcher` and `MemoCacheManager` per instance

For our primitive-argument algorithms, the steady-state cache footprint
per entry is roughly 80-120 bytes; compare the allocation deltas between
policies to verify.

## Where to go next

- Drop the `maxSize` on any memoized variant to force eviction and watch
  the hit-rate / latency curves diverge.
- Add `autoMonitor = true` to the `VERY_COLD` measurement and see the
  cache disable itself mid-benchmark (watch `MemoLogger` output at
  `WARN`).
- Swap `AlgorithmsLru` for a custom subclass that uses TTL (`expireAfterWrite`)
  and measure the dual-map cost documented in `docs/runtime.md`.
