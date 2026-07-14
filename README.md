# storm-benchmarks

Reproducible benchmark suite comparing [Storm](https://github.com/storm-orm/storm-framework)
against plain JDBC, Hibernate ORM, jOOQ, Exposed, and Jimmer on realistic PostgreSQL
workloads. JMH with Testcontainers; identical schema, data, driver, and connection pool for
every library.

See [METHODOLOGY.md](METHODOLOGY.md) for what is measured, the fairness rules, and how to
interpret results.

## Workloads

| Workload | Shape |
|---|---|
| `singleRowById` | One row by primary key, foreign key left as a reference |
| `joinWithMapping10/100/1000` | 10, 100, or 1000 rows over a 3-table join, object graph materialized |
| `projection` | Same join, 3 columns into a flat row type |
| `batchInsert` | 100 rows in a transaction, generated keys returned |
| `updateById` | One row read by primary key, one column changed and persisted atomically |
| `objectGraph` | 50 parents with their child collections (N+1-prone shape) |

## Running

Requirements: JDK 21, Docker.

```bash
scripts/run.sh            # full suite (~30 min), merged results in results/
scripts/run.sh --quick    # smoke run (~5 min), numbers are indicative only
./gradlew :bench-storm:jmh    # one library, self-contained
```

Each `bench-<library>` module is a standalone JMH project; `bench-common` holds the schema,
the deterministic dataset, and the container lifecycle. Results land in
`results/combined.json` and `results/summary.md`.

## Versions

Dependency versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Results

Per-release results are produced by the `benchmark` GitHub Actions workflow and published
with the release notes, including runner type and exact versions. No numbers are committed
to this repository; run the suite to produce your own.

## Contributing

Improvements to any implementation are welcome, in particular from users of the compared
libraries: if an implementation is not what the library's documentation recommends, that is
a bug. Please include a pointer to the documentation in question.

## License

Apache License 2.0, see [LICENSE.txt](LICENSE.txt).
