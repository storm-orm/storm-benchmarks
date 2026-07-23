# Run: 2026-07-23, Storm `main`

- **Date:** measured 2026-07-23
- **Storm:** built from `main` at commit `3d4cb8e2`, resolved as `1.13.0`
- **Suite:** commit `a3e4d0b` (first run under the full table-state discipline described under Notes)
- **Runner:** GitHub-hosted dedicated runner, 4 vCPU / 16 GB, Ubuntu 24.04
- **Workflow run:** https://github.com/storm-orm/storm-benchmarks/actions/runs/30016426654
- **JDK:** OpenJDK 64-Bit Server VM 21 (21.0.11+10-LTS)
- **JMH:** 1.36, average time (`avgt`), reported in µs/op; 5 forks, 5 × 2 s warmup, 5 × 3 s measurement; published score is the median of the five forks, with the fork range [fastest–slowest] alongside
- **Database:** PostgreSQL 17-alpine started by `scripts/run.sh` (one tuned container for the whole suite), pgjdbc 42.7.13, shared HikariCP pool

## Library versions

| Library | Version |
|---|---|
| Storm | `main` @ 3d4cb8e2 (1.13.0) |
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

First run under the full table-state discipline (suite commit `a3e4d0b`): every fork runs
`VACUUM ANALYZE` at trial setup, automatic statistics collection is disabled on the container
(vacuum itself stays on as a backstop), and `auto_explain` samples 0.1% of executions into the
container log.

The discipline was introduced across two suite commits after two discarded runs on this runner
class. Run 29976612293 showed three cells (Hibernate projection and object graph, jOOQ dynamic)
below the raw JDBC baseline for identical work, all five forks agreeing: an automatic `ANALYZE`
landing mid-suite had flipped cached prepared plans into a different regime. Pinning statistics
(commit `679a653`, run 29996831398) removed the inversions but left wide fork ranges on
`joinWithMapping10` for every single-join implementation, up to +630 µs on a ~700 µs score: that
workload runs directly after the write-heavy `graphInsert`, and its earliest forks paid the
pending table cleanup, the fork means settling monotonically for every library in the same way.
The trial-setup vacuum (commit `a3e4d0b`) pays that debt untimed; in this run the
`joinWithMapping10` ranges sit at +2 to +30 µs, in line with every other read workload, and all
other cells reproduce run 29996831398 within noise.

The untimed `SELECT 1` baseline measured about 134 µs, matching the two runs before it on the
same runner class; the published figures and charts still derive from this run alone.
