# Results

Committed benchmark runs. Each run is a dated directory holding the merged results table, the
raw per-library JMH JSON, and a `metadata.md` recording the exact versions, runner, and JMH
configuration. [`summary.md`](summary.md) in this directory mirrors the most recent run's table
for quick reference.

Read a score relative to the JDBC baseline **within the same table**. Absolute µs/op depends on
the runner instance, so numbers are not comparable across runs; the JDBC baseline in each table
is the fixed reference. See [`../METHODOLOGY.md`](../METHODOLOGY.md) for what each workload
measures and the fairness rules.

| Run | Storm | Runner | Notes |
|---|---|---|---|
| [2026-07-22](2026-07-22/) | `main` @ 8aac08da (1.13.0) | dedicated 4 vCPU / 16 GB, Ubuntu 24.04 | canonical run: all fairness-rule applications, fastest-of-5 estimator; see metadata for the runner hardware note |
