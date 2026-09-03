# Run: 2026-09-03, Storm 1.14.0

- **Date:** measured 2026-09-03
- **Storm:** `v1.14.0` at commit `c128ac47`, resolved as `1.14.0`
- **Suite:** commit `f490a12`
- **Runner:** GitHub-hosted dedicated runner, 4 vCPU / 16 GB, Ubuntu 24.04.4, AMD EPYC 7763
- **Workflow run:** https://github.com/storm-orm/storm-benchmarks/actions/runs/33724393724
- **JDK:** OpenJDK 64-Bit Server VM 21 (21.0.12.1)
- **JMH:** 1.37, average time (`avgt`), reported in µs/op; 5 forks, 5 × 2 s warmup, 5 × 3 s measurement; published score is the median of the five forks, with the fork range [fastest–slowest] alongside
- **Database:** PostgreSQL 17-alpine started by `scripts/run.sh` (one tuned container for the whole suite), pgjdbc 42.7.13, shared HikariCP pool

## Library versions

| Library | Version |
|---|---|
| Storm | 1.14.0 |
| Hibernate ORM | 7.4.7.Final |
| jOOQ | 3.21.7 |
| Exposed (DSL + DAO) | 1.5.0 |
| Ktorm | 4.2.1 |
| Jimmer | 0.11.7 |
| Kotlin | 2.4.10 |

## Files

- `summary.md` — merged results table for this run.
- `combined.json` — every library merged into one JMH-shaped array.
- `bench-<library>.json` — raw JMH output per library (each iteration, error, params, JVM info).

The workflow artifact for this run additionally carries `postgres-plans.log`, the container log
with the sampled `auto_explain` plans; it is not committed here for size, but every plan regime
behind these numbers can be verified from it.

See [`../../METHODOLOGY.md`](../../METHODOLOGY.md) for what each workload measures and the fairness rules.

## Notes

Table-state discipline: every fork runs `VACUUM ANALYZE` at trial setup, automatic statistics
collection is disabled on the container (vacuum itself stays on as a backstop), and `auto_explain`
samples 0.1% of executions into the container log. The trial-setup vacuum pays any pending table
cleanup untimed, so no fork inherits cleanup debt or stale statistics from the workload before it.

This run landed on a slower instance than the 2026-07-25 run: the JDBC baseline measures 140.7 µs/op
against 83.6 µs/op there, +68%, and the drift guard flagged it. The two tables therefore cannot be
compared cell by cell, which is the standing rule for any two runs rather than a caveat specific to
this pair. Read each score against the JDBC baseline **inside this table**. Slower hardware also
compresses the field toward the baseline, since fixed per-query CPU shrinks relative to I/O, so
small margins between libraries move more easily here than the 2026-07-25 spread suggests; the
`baseline_ref` A/B mode on one instance is the instrument for a real before/after.

Every published figure and chart derives from this run alone.
