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
| [2026-07-21-storm-main](2026-07-21-storm-main/) | `main` @ 14b57c2f (1.13.0) | dedicated 4-core Ubuntu 24 | statement-build allocation work landed; see metadata for the join compilation-plan note |
| [2026-07-20-storm-main](2026-07-20-storm-main/) | `main` @ 072a2fb5 (1.13.0) | dedicated 4-core Ubuntu 24 | multi-row `RETURNING` batch inserts; JDBC baseline aligned to the same technique |
