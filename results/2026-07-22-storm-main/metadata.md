# Run: 2026-07-22, Storm `main`

- **Date:** measured 2026-07-22
- **Storm:** built from `main` at commit `8aac08da` (includes the multi-row keyless inserts, the statement-build allocation work, the generated record deconstructors, and the write-set `insertAndFetchIds` used by the graph insert), resolved as `1.13.0`
- **Runner:** GitHub-hosted dedicated runner, 4 vCPU / 16 GB, Ubuntu 24.04
- **Workflow run:** https://github.com/storm-orm/storm-benchmarks/actions/runs/29869688250
- **JDK:** OpenJDK 64-Bit Server VM 21 (21.0.11+10-LTS)
- **JMH:** 1.36, average time (`avgt`), reported in µs/op; 5 forks, 5 × 2 s warmup, 5 × 3 s measurement; published score is the fastest fork with the range to the slowest fork
- **Database:** PostgreSQL 17-alpine via Testcontainers 1.21.4, pgjdbc 42.7.13, shared HikariCP pool

## Library versions

| Library | Version |
|---|---|
| Storm | `main` @ 8aac08da (1.13.0) |
| Hibernate ORM | 7.4.5.Final |
| jOOQ | 3.21.6 |
| Exposed (DSL + DAO) | 1.3.1 |
| Ktorm | 4.1.1 |
| Jimmer | 0.11.0 |
| Kotlin | 2.4.0 |

## Files

- `summary.md` — merged results table for this run.
- `combined.json` — every library merged into one JMH-shaped array.
- `bench-<library>.json` — raw JMH output per library (each iteration, error, params, JVM info).

See [`../../METHODOLOGY.md`](../../METHODOLOGY.md) for what each workload measures and the fairness rules.

## Notes

The runner pool's hardware class changed with this run: the untimed `SELECT 1` baseline measures
about 135 µs, where runs before 2026-07-21 measured 77 to 78 µs on the same runner label. Absolute
values are therefore not comparable to earlier runs; comparisons within this run are unaffected,
and every published figure and chart derives from this run alone.

The immediately preceding run on the same hardware class (29852748225) was discarded: several
cells showed a framework running identical statements faster than the raw JDBC baseline, with all
five forks agreeing, consistent with PostgreSQL settling prepared statements into a cached generic
plan for some library segments while others replanned per call. This run shows no such inversion.

`joinWithMapping10` retains wide fork ranges for some libraries (the known JIT compilation-plan
sensitivity of join hydration); the fastest-fork estimator reports the settled plan.
