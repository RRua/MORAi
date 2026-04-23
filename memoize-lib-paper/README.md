# MemoizeLib paper

LaTeX source for the paper accompanying the `memoize-lib` project. Structure
follows the `capstone-automated-api-usage` paper template: `acmart` class,
top-level `main.tex` wiring a handful of `sections/*.tex` files, a dedicated
`tables/*.tex` directory, plus `packages.tex`, `macros.tex`, and
`authors.tex`.

## Layout

```
memoize-lib-paper/
├── main.tex                  # document root
├── packages.tex              # required LaTeX packages
├── macros.tex                # \code{}, acronyms, \circnum, \roundbox, \pending
├── authors.tex               # anonymised for review
├── bibliography.bib          # references (Guava, Caffeine, AspectJ, JPB, etc.)
├── sections/
│   ├── acmart.cls            # ACM document class (copied from capstone)
│   ├── abstract.tex
│   ├── introduction.tex
│   ├── design.tex            # library architecture, ASM transform, policies
│   ├── setup.tex             # benchmark harness, 10 algorithms, 3 suites
│   ├── results.tex           # RQ1/RQ2/RQ3 with expected-shape tables
│   ├── threats.tex
│   ├── relatedwork.tex
│   └── conclusions.tex
├── tables/
│   ├── algorithms.tex        # the 10 benchmark algorithms
│   ├── results_speedup.tex   # RQ1 table (values marked \pending)
│   ├── results_policy.tex    # RQ2 table
│   └── results_hitrate.tex   # RQ3 table
├── acmauthoryear.bbx
├── acmnumeric.bbx
└── acmnumeric.cbx
```

## Building

```bash
# From this directory:
pdflatex -interaction=nonstopmode main.tex
bibtex main
pdflatex -interaction=nonstopmode main.tex
pdflatex -interaction=nonstopmode main.tex
```

`acmart.cls` is vendored under `sections/` (the same location as in the
inspiration project). `bibliography.bib` uses BibTeX keys; no `biber` needed.

## Filling in the result tables

Every number in `tables/results_*.tex` is marked `\pending`, defined in
`macros.tex`. To populate:

1. Run the Jetpack Benchmark campaign on a physical Pixel-class device:
   ```bash
   cd ../benchmark_demo
   ./gradlew :microbenchmark:connectedReleaseAndroidTest
   ```
2. Collect the per-class JSON under
   `benchmark_demo/microbenchmark/build/outputs/connected_android_test_additional_output/`.
3. Replace each `\pending` cell with the median `metrics.timeNs.median`
   value (in nanoseconds) from the matching `@Test` method name.

The qualitative shape of the results section (Section `sec:results`) is
written to be true regardless of absolute numbers: it grounds every
prediction in the asymptotic complexity of the algorithm and the per-policy
hot-path cost documented in `sections/design.tex`.

## Notes

- The paper is written for a review pass (`review` option on `acmart`).
  Remove it for a camera-ready submission.
- `authors.tex` is intentionally commented out for anonymous review. Un-
  comment the `\author{...}` block before camera-ready.
- The ten algorithms and three benchmark suites exactly mirror the
  implementation in `../benchmark_demo/`. Any change to the benchmark code
  should be reflected in `sections/setup.tex`.
- `tables/algorithms.tex` and the results tables use `\code{}` (defined in
  `macros.tex`) for inline code so that method names render consistently
  with the rest of the paper.
