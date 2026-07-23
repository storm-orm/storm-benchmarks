# Results

The currently published benchmark run: a dated directory holding the merged results table, the
raw per-library JMH JSON, and a `metadata.md` recording the exact versions, runner, and JMH
configuration. [`summary.md`](summary.md) in this directory mirrors that run's table for quick
reference. Every published figure derives from this run alone; superseded runs remain available
in the git history and as workflow artifacts.

Read a score relative to the JDBC baseline **within the same table**. Absolute µs/op depends on
the runner instance, so numbers are not comparable across runs; the JDBC baseline in each table
is the fixed reference. See [`../METHODOLOGY.md`](../METHODOLOGY.md) for what each workload
measures and the fairness rules.

| Run | Storm | Runner | Notes |
|---|---|---|---|
| [2026-07-23](2026-07-23/) | `main` @ 3d4cb8e2 (1.13.0) | dedicated 4 vCPU / 16 GB, Ubuntu 24.04 | canonical run: full table-state discipline (per-trial `VACUUM ANALYZE`, pinned statistics, sampled plan log); see metadata for the discipline history |
