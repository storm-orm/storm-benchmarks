# Run: 2026-07-20, Storm `main`

- **Date:** 2026-07-20
- **Storm:** built from `main` at commit `072a2fb5` (`perf: extend multi-row batch key fetch to all capable dialects (#291)`), resolved as `1.13.0`
- **Runner:** GitHub-hosted `Linux-x64-4core-16Gb-Ubuntu24` (dedicated, 4 vCPU / 16 GB, Ubuntu 24.04)
- **Workflow run:** https://github.com/storm-orm/storm-benchmarks/actions/runs/29720221378
- **JDK:** OpenJDK 64-Bit Server VM 21.0.11+10-LTS
- **JMH:** 1.36, average time (`avgt`), reported in µs/op; 2 forks, 5 × 2 s warmup, 5 × 3 s measurement
- **Database:** PostgreSQL 17-alpine via Testcontainers 1.21.4, pgjdbc 42.7.13, shared HikariCP pool

## Library versions

| Library | Version |
|---|---|
| Storm | `main` @ 072a2fb5 (1.13.0) |
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

This run is the first with the JDBC baseline aligned to the same technique as the fastest ORM on each
workload: `batchInsert` and `graphInsert` issue a single multi-row `INSERT ... VALUES (...),(...) RETURNING`
rather than `executeBatch`. On those two workloads the baseline now sits below the ORMs, so the gap reads as
mapping overhead rather than a difference in approach.
