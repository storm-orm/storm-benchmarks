# Run: 2026-07-21, Storm `main`

- **Date:** measured 2026-07-20, published 2026-07-21
- **Storm:** built from `main` at commit `14b57c2f` (includes the multi-row keyless inserts, the statement-build allocation work, and the generated record deconstructors), resolved as `1.13.0`
- **Runner:** GitHub-hosted `Linux-x64-4core-16Gb-Ubuntu24` (dedicated, 4 vCPU / 16 GB, Ubuntu 24.04)
- **Workflow run:** https://github.com/storm-orm/storm-benchmarks/actions/runs/29777893709
- **JDK:** OpenJDK 64-Bit Server VM 21 (21.0.11+10-LTS)
- **JMH:** 1.36, average time (`avgt`), reported in µs/op; 2 forks, 5 × 2 s warmup, 5 × 3 s measurement
- **Database:** PostgreSQL 17-alpine via Testcontainers 1.21.4, pgjdbc 42.7.13, shared HikariCP pool

## Library versions

| Library | Version |
|---|---|
| Storm | `main` @ 14b57c2f (1.13.0) |
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

A same-day run of identical code (actions/runs/29770563350) reproduced every workload within error except
the two larger joins, where Storm's hydration loop is sensitive to the JIT compilation plan chosen early in
a run: `joinWithMapping100` measured 417 there versus 547 here, and `joinWithMapping1000` measured 2723 there
versus 2172 here, with both forks agreeing tightly inside each run and the JDBC baseline stable across runs.
This run matches the historical values for both joins and is the published one. Future runs use a higher fork
count so per-run join figures average over compilation plans.
