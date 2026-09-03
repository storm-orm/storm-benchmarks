# Results

Each benchmark run gets a dated directory holding the merged results table, the raw per-library
JMH JSON, and a `metadata.md` recording the exact versions, runner, and JMH configuration. The
run at the top of the table below is the current one, and [`summary.md`](summary.md) in this
directory mirrors it for quick reference. Earlier runs are kept alongside it rather than removed,
so a published figure can always be traced back to the table it came from.

Read a score relative to the JDBC baseline **within the same table**. Absolute µs/op depends on
the runner instance, so numbers are not comparable across runs, and no chart or claim mixes two:
the JDBC baseline in each table is the fixed reference for that table. To measure a change in
Storm itself, use the workflow's `baseline_ref` A/B mode, which runs two refs back to back on one
instance. See [`../METHODOLOGY.md`](../METHODOLOGY.md) for what each workload measures and the
fairness rules.

| Run | Storm | Runner | Notes |
|---|---|---|---|
| [2026-09-03](2026-09-03/) | 1.14.0 (`v1.14.0` @ c128ac47) | dedicated 4 vCPU / 16 GB, Ubuntu 24.04, AMD EPYC 7763 | current. JDBC baseline 140.7 µs/op: a slower instance than the run below, so the two tables are not cell-comparable |
| [2026-07-25](2026-07-25/) | `main` @ 5556faea (1.13.0) | dedicated 4 vCPU / 16 GB, Ubuntu 24.04 | JDBC baseline 83.6 µs/op. Full table-state discipline (per-trial `VACUUM ANALYZE`, pinned statistics, sampled plan log); see metadata |
