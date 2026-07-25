# Run: 2026-07-25, Storm `main`

- **Date:** measured 2026-07-25
- **Storm:** built from `main` at commit `5556faea`, resolved as `1.13.0`
- **Suite:** commit `e9fb5b3`
- **Runner:** GitHub-hosted dedicated runner, 4 vCPU / 16 GB, Ubuntu 24.04
- **Workflow run:** https://github.com/storm-orm/storm-benchmarks/actions/runs/30155193729
- **JDK:** OpenJDK 64-Bit Server VM 21 (21.0.11)
- **JMH:** 1.37, average time (`avgt`), reported in µs/op; 5 forks, 5 × 2 s warmup, 5 × 3 s measurement; published score is the median of the five forks, with the fork range [fastest–slowest] alongside
- **Database:** PostgreSQL 17-alpine started by `scripts/run.sh` (one tuned container for the whole suite), pgjdbc 42.7.13, shared HikariCP pool

## Library versions

| Library | Version |
|---|---|
| Storm | `main` @ 5556faea (1.13.0) |
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

The workflow artifact for this run additionally carries `postgres-plans.log`, the container log
with the sampled `auto_explain` plans; it is not committed here for size, but every plan regime
behind these numbers can be verified from it.

See [`../../METHODOLOGY.md`](../../METHODOLOGY.md) for what each workload measures and the fairness rules.

## Notes

Table-state discipline: every fork runs `VACUUM ANALYZE` at trial setup, automatic statistics
collection is disabled on the container (vacuum itself stays on as a backstop), and `auto_explain`
samples 0.1% of executions into the container log. The trial-setup vacuum pays any pending table
cleanup untimed, so no fork inherits cleanup debt or stale statistics from the workload before it.

Absolute µs/op depend on the runner instance; within this table the JDBC baseline is the fixed
reference, so read each score relative to it rather than against another run. Every published
figure and chart derives from this run alone.
