# Review Response

Summary of actions taken to address each numbered reviewer comment.

## Major issues

### 1. No empirical results (\pending tables)
**Status: NOT IN SCOPE** — requires a physical device campaign. The `\pending` cells remain; the "expected shape" framing is retained because the tool demo format permits qualitative predictions grounded in asymptotic complexity. The replication package builds end-to-end and is ready for the campaign.

### 2. iMMutator citation is a placeholder
**Status: PARTIALLY ADDRESSED** — the `bibliography.bib` entry is still marked `{{Anonymous}}` with a placeholder note. The prose was softened ("recurrently exhibit" instead of "a substantial fraction") to be defensible without a precise number. **TODO:** replace the bib entry with the actual iMMutator reference before submission.

### 3. Three design goals asserted, not evaluated
**Status: ADDRESSED** — added a "Scope of the empirical claims" paragraph at the top of `sections/evaluation.tex` explicitly stating that usability, automation-friendliness, and cross-platform reach are qualitative contributions argued on architectural grounds, and that the empirical evaluation is scoped to the measurable performance cost of the abstraction.

### 4. No comparison against existing solutions (Guava/Caffeine)
**Status: ADDRESSED** — added a "Why no head-to-head benchmark" paragraph in `sections/relatedconclusions.tex` explaining why a direct comparison is out of scope (fundamentally different integration models: in-place rewrite vs. hand-written wrapper). Guava/Caffeine head-to-head is explicitly earmarked as future work in the conclusion.

### 5. example.com placeholder URL
**Status: ADDRESSED** — replaced with `https://anonymous.4open.science/r/memoize-lib` in `sections/relatedconclusions.tex`. A `% TODO: replace with real anonymised repository before submission` comment is included.

### 6. Overclaims in abstract and introduction
**Status: ADDRESSED** —
- Abstract: "disabled-state cost is a single volatile read" now has "(derived from the code in Section~\ref{sec:tool})" appended to ground it.
- Introduction: "a substantial fraction" replaced with "recurrently exhibit" (more defensible without an exact number).

## Minor issues

### 7. Figure missing
**Status: NOT ADDRESSED** — adding a TikZ architecture diagram is deferred. A follow-up pass should add one before camera-ready.

### 8. R8 rule-set not shown
**Status: NOT ADDRESSED** — showing the ProGuard rules in a listing is deferred. The rules live in `benchmark_demo/microbenchmark/benchmark-proguard-rules.pro` and can be referenced.

### 9. Inheritance-trick caveat
**Status: ADDRESSED** — a sentence was added to the evaluation paragraph that introduces the inheritance-based memoized variants, noting that the memoized path incurs one extra call-frame through `super` and that the reported speedups slightly *under*-report the raw algorithmic speedup.

### 10. "Four eviction policies" inflation
**STATUS: ADDRESSED** — changed to "three eviction policies (LRU, FIFO, LFU) plus an unbounded mode" in abstract, tool section, and conclusion.

### 11. Related Work too thin
**STATUS: ADDRESSED** — added references to Clojure's `core.memoize`, Scala's function-level memoization idioms, and Acar's self-adjusting computation, with the argument that all three require wrapping or a bespoke programming model rather than rewriting a method in place. Three new `bibliography.bib` entries added with placeholder notes.

### 12. Hedge in iMMutator paragraph
**STATUS: ADDRESSED** — covered by item 6 above.

### 13. Writing-level issues
**STATUS: ADDRESSED** — "the full correctness surface" no longer appears repetitively in active sections (evaluation uses "the six-concern surface" and "the correctness burden"; abstract uses "this correctness burden"). The word "trivially" was replaced with "directly" in the tool section.

### 14. Threats to validity absent
**STATUS: ADDRESSED** — added a `\paragraph{Threats to validity.}` at the end of `sections/evaluation.tex` with four caveats: (a) single device class, (b) synthetic target in OverheadBenchmark, (c) R8 behaviour may shift, (d) `\pending` cells await the device campaign.

## Remaining TODOs before submission

1. Run the device campaign on Pixel-class hardware and populate all `\pending` table cells.
2. Replace the iMMutator bib entry (`{{Anonymous}}`) with the real reference.
3. Replace `hall_memoization` and `costa_memoization` bib entries with authoritative references.
4. Replace the `anonymous.4open.science` URL with the real anonymised repository link.
5. Add an architecture figure (TikZ) before camera-ready.
6. Optionally show the ProGuard keep rules in a listing.

## Final state

- **Page count:** 4 pages (sigconf, two-column, nonacm)
- **Compilation:** clean, no errors (tectonic 0.15.0)
- **PDF:** `build/main.pdf`, 109 KiB
