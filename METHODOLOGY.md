# Methodology

This document describes what the suite measures, how it measures it, and the rules every
implementation follows, so that anyone can rerun the suite and verify published numbers.

## What is measured

Each benchmark measures the end-to-end latency of a database operation as an application
would issue it: building the query, executing it over JDBC against a local PostgreSQL
instance, and materializing the result into objects. With the database on the same host and
tuned for low variance, differences between libraries reflect their client-side overhead:
query construction, statement handling, and row-to-object mapping.

What this suite does not measure: PostgreSQL server performance, network latency beyond the
local loopback, connection acquisition under contention, or multi-threaded scaling. Numbers
are not transferable to other workloads; they compare libraries under identical conditions.

## Libraries

| Library | Language | Access style |
|---|---|---|
| JDBC | Java | Hand-written SQL and mapping (baseline) |
| Storm | Kotlin | Repositories, metamodel DSL, SQL templates |
| Hibernate ORM | Java | JPA entities, HQL, session per operation |
| jOOQ | Java | Generated classes, type-safe DSL |
| Exposed | Kotlin | SQL DSL mapped to data classes |
| Exposed DAO | Kotlin | DAO entities (entity cache, lazy references, `with(...)` eager loading) |
| Ktorm | Kotlin | Entity sequence API with reference bindings, SQL DSL for projections |
| Jimmer | Java | Entity interfaces, object fetchers |

Exposed appears twice because its two APIs have different performance characteristics and
both are in wide use: the SQL DSL maps rows directly, while the DAO layer adds an entity
cache and lazy references. The DAO module drops to the DSL for the projection workload,
as DAO applications do.

Storm, Exposed, and Ktorm are benchmarked through their Kotlin APIs, which are their primary
interfaces. Storm's Java API currently requires the String Templates preview feature and is
not part of the suite; it can be added once the feature stabilizes. The measured work is
dominated by JDBC interaction and mapping, which both languages drive through the same
runtime.

Ktorm is benchmarked through its entity sequence API, its recommended interface for entity
work: reference bindings drive the join and nested materialization for the read workloads,
and it drops to the SQL DSL for the flat projection, as its documentation shows. The batch
workloads use `bulkInsertReturning` from `ktorm-support-postgresql`, Ktorm's documented API for
multi-row `INSERT ... RETURNING` on PostgreSQL, so the generated keys come back with the
statement. (Core Ktorm's `batchInsert` returns affected-row counts only; on databases without
the support module's bulk API, key retrieval would fall back to one insert per row.)

Exact dependency versions are pinned in `gradle/libs.versions.toml`.

## Fairness rules

1. Every library runs against the same schema, the same deterministically generated data,
   the same PostgreSQL instance, the same JDBC driver, and an identical HikariCP pool.
2. Every library gets the most performant solution its ecosystem documents for the
   workload. Database-specific solutions are allowed when they come through the library's
   own modules or configuration; the benchmark code itself stays within the library's API,
   and the JDBC driver and database run stock, identically for everyone: the suite measures
   how the ORM performs when the ORM is configured, not what driver tuning can paper over. An optimization qualifies only if it passes three tests: it is
   documented, it is what production guidance for that library actually recommends, and it
   carries no semantic penalty for the workload it touches. Best practice takes precedence
   over raw speed: a trick a well-informed production team would not ship does not qualify.
   No library is forced through another library's access pattern.
3. Libraries are free to use their natural mechanism for a workload. Hibernate uses
   `join fetch`, jOOQ uses `MULTISET`, Jimmer uses object fetchers with batched secondary
   queries, Storm and the JDBC baseline use joins. Query counts therefore differ per
   library; they are listed per workload below.
4. Applications of rule 2 are tracked in the table below and noted with the workloads they
   touch.
5. Result shapes are equivalent across libraries per workload: the same rows, the same
   materialized fields. A sanity check runs every workload once per trial and fails the
   benchmark if the row counts are wrong.

### Optimizations applied

Every entry passes the three tests of rule 2. Storm's row is listed for symmetry: it runs
unconfigured (`ORMTemplate.of(dataSource)`), and its fast paths (multi-row RETURNING batches,
the literal scroll limit, the write-set dependency ordering) are defaults, not settings.

| Scope | Optimization | Effect |
|---|---|---|
| Everyone | Sequence-fed primary keys on the insert-target tables | No library loses JDBC batching to an identity column; Hibernate's pooled generator (`allocationSize = 50`) allocates ids client-side. |
| Storm | `@DynamicUpdate(UpdateMode.FIELD)` on the update-workload entity | Writes only the changed column. Storm's only opt-in besides the `storm-postgresql` dialect module. |
| Hibernate | `@DynamicUpdate` on the owner entity | Writes only the changed column. |
| Hibernate | HQL `limit 20`, a literal | PostgreSQL caches the generic plan for the keyset join instead of replanning it per call. |
| jOOQ | `limit(DSL.inline(20))` | The same plan-cache effect on the keyset query. |
| Ktorm | `bulkInsertReturning` from `ktorm-support-postgresql` | One multi-row `INSERT ... RETURNING` per batch; core Ktorm would retrieve keys row by row. |
| Jimmer | `setConstraintViolationTranslatable(false)` | Removes the SAVEPOINT / RELEASE pair around each save command; constraint violations surface as raw exceptions, which these workloads never read. |

## Environment

- **Harness**: JMH 1.37. `Mode.AverageTime` in microseconds, 5 forks, 5 warmup iterations
  of 2 s, 5 measurement iterations of 3 s, single thread. The `--quick` profile (1 fork,
  2 x 1 s warmup, 3 x 1 s measurement) exists for smoke testing only; quick numbers are not
  publishable. Two further profiles: `--sample` switches to `Mode.SampleTime` and reports
  p50/p99 latency percentiles instead of averages (tail behavior differs from means when
  allocation and GC profiles differ), and `--threads N` runs every benchmark with N
  concurrent threads against the shared pool.
- **Database**: PostgreSQL 17 (alpine image) in Docker with a tmpfs data directory and
  `fsync=off`, `synchronous_commit=off`, `full_page_writes=off`, `shared_buffers=256MB`.
  These settings trade durability for run-to-run stability, identically for every library.
- **Connections**: one HikariCP pool per fork, 10 connections, default isolation.
  Benchmarks run single-threaded, so pool contention is not a factor.
- **Container lifecycle**: `scripts/run.sh` starts one container for the whole suite and
  runs the library modules sequentially. A standalone `:bench-<lib>:jmh` invocation starts
  its own Testcontainers instance instead.
- **JDK**: 21 (the minimum both Storm and current Hibernate support).

## Schema and dataset

PetClinic-style domain: `city` (100 rows), `owner` (5,000; 50 per city), `pet_type` (6),
`pet` (10,000; 2 per owner), `visit` (30,000; 3 per pet). All foreign keys are indexed.
Data is generated from the row index alone (`bench-common`), so every run and every library
sees identical bytes. Seeded ids start at 1; the `visit` sequence starts at 1,000,000 so
rows written by the insert workload can be removed between iterations without touching seed
data. See `bench-common/src/main/resources/schema.sql`.

Parameters cycle deterministically (visit ids 1..30,000, city ids 1..100), so all
implementations touch the same rows in the same order.

## Workloads

### baseline

A bare `SELECT 1` over plain JDBC, with no mapping. This quantifies the loopback round trip and driver floor
that every other score includes, so library overhead can be read as score minus baseline, and results from
different sessions can be normalized. It appears only in the JDBC column.

### singleRowById

Fetch one `visit` row by primary key. The `pet` foreign key stays a reference (lazy proxy,
`Ref`, id-only object, or plain id, depending on the library); no join is executed.
1 query per operation for every library.

### joinWithMapping10 / joinWithMapping100 / joinWithMapping1000

Fetch a window of 10, 100, or 1000 consecutive pets (by id range) with owner and city fully materialized
(3-table join). The three sizes separate per-query fixed cost from per-row mapping cost: the 10-row score is
dominated by query overhead, the 1000-row score by materialization, and the slope between them is each
library's mapping cost per row. Storm, Hibernate, jOOQ, Exposed (DSL), Ktorm, and JDBC execute 1 join query
(Ktorm's reference bindings emit left joins, equivalent here since every foreign key is non-null). Jimmer
and Exposed DAO follow their fetcher/eager-loading models: 1 main query plus batched association queries.

The per-query fixed cost includes a planning pass on every execution, for every implementation including the
JDBC baseline: with both range bounds arriving as bind parameters, PostgreSQL's generic-plan cost estimate
(built from default range selectivity) never beats the custom plan, so the prepared statement stays in
custom-plan mode and the three-table join is replanned on each call (verified with `PREPARE`/`EXECUTE`: the
plan keeps showing folded constants after any number of executions). Planning this join costs on the order of
executing its 10-row variant, which is why the 10-row and 100-row scores sit close together. The cost is
identical for every library, so it shifts no standings; it only compresses relative differences at the small
row counts.

### projection

Same join shape, but only three columns (`pet.name`, `owner.last_name`, `city.name`)
mapped into a flat row type. 1 query per operation for every library. The delta against
`joinWithMapping` isolates entity-materialization cost from query cost.

### batchInsert

Insert 100 `visit` rows; every library returns the generated keys from the batch. Every
implementation runs inside an explicit transaction. The
JDBC baseline issues a single multi-row `INSERT INTO visit (...) VALUES (...),(...) RETURNING`
and reads the keys from the result set: the same technique the fastest ORMs use, so the
baseline reads as the raw floor for the workload rather than a naive per-row batch. (This is
also why the baseline is not `executeBatch` with `RETURN_GENERATED_KEYS`: pgjdbc disables
`reWriteBatchedInserts` when generated keys are requested, so that path cannot collapse into
one statement.) The libraries use their idiomatic mechanisms: Exposed uses `executeBatch` with
generated keys, Exposed DAO creates entities whose pending inserts flush as a batch, Hibernate
persists with a sequence-backed id and JDBC batching, Storm runs its repository batch insert
inside the ambient `transaction { }`, jOOQ issues a single multi-row `INSERT .. RETURNING`, and
Jimmer runs its save command. Ktorm uses `bulkInsertReturning` from its PostgreSQL support
module, a single multi-row `INSERT ... RETURNING` like the jOOQ and JDBC implementations. The
multi-row statement covers the 100-row batch in one statement; larger batches would chunk into
multiple statements within the same transaction (PostgreSQL caps bind parameters at 65,535 per
statement). Inserted rows are deleted in an untimed teardown between iterations.

### updateById

Load one owner by primary key, change its telephone, and persist the change, atomically. The new value is
derived from the one just read (a prefix toggle), so every update is structurally guaranteed to be a real
change. This matters for fairness: libraries with change detection silently skip the UPDATE when the written
value equals the current one, and a generated value sequence repeats across JMH forks, which would turn the
second fork into a read-only benchmark for those libraries.

Every implementation issues a single UPDATE against the row just read: Storm and Hibernate annotate the owner
entity with their respective `@DynamicUpdate` options (the documented setting for write-heavy entities), jOOQ's
`UpdatableRecord.store()`, Exposed DAO's flush, and Ktorm's `flushChanges()` track changed fields and write only
the changed column, and the JDBC and Exposed (DSL) implementations update the single column explicitly. Jimmer
is the exception: statement logging shows its `UPDATE_ONLY` save writes every loaded column of the draft, which
is its documented save semantics; the written values still differ only in the telephone. This workload exercises each library's change
detection: Hibernate flushes via snapshot-based dirty checking, jOOQ's `UpdatableRecord.store()` updates
changed fields, Exposed DAO flushes tracked field writes at commit, Ktorm's `flushChanges()` writes only the
properties modified on the entity, Jimmer saves a draft-produced copy with `UPDATE_ONLY`, Storm updates an
immutable copy through its repository, Exposed (DSL) and JDBC issue an explicit UPDATE after the read. Every
implementation reads the owner in a lazy-association shape: the JPA-style
libraries declare `FetchType.LAZY` (or are lazy by design), Storm expresses the same fetch decision with a
dedicated record mapping the owner table whose city is a `Ref` foreign key, its equivalent of a lazy
association, and Ktorm reads the owner with `withReferences = false` so the city reference is not joined.
Storm's eager aggregate shape, which joins the city on every read, is exercised by the read
workloads instead.

### objectGraph

Load the 50 owners of one city, each with their list of pets (an N+1-prone shape). Every
library uses its recommended efficient strategy: Hibernate `join fetch` on the collection,
jOOQ `MULTISET`, Jimmer a fetcher with a batched list association, Exposed DAO `with(...)`
eager loading (2 queries), and JDBC, Exposed (DSL), Ktorm, and Storm one join grouped in memory
(Storm selects the owner-pet row pair through its eagerly mapped graph in a single query;
Ktorm queries the pet side joined to owner and city and groups by owner, as its docs show for
one-to-many, which it does not model as an entity property).
This workload compares each library's sanctioned answer to the N+1 problem, not a naive
lazy loop.

### keyset

Fetch one page of `PAGE_SIZE` (20) pets by keyset (seek) pagination: `WHERE id > cursor
ORDER BY id LIMIT 20`, with owner and city materialized (the same 3-table graph as
`joinWithMapping`). Keyset paging is the performant alternative to offset paging, which is why
this variant is measured: the cursor moves through the id space with no `OFFSET` scan cost, so
the page fetch stays flat regardless of how deep into the list it is. Each library uses its
keyset idiom: Storm its `scroll(Scrollable.of(...))` cursor API (its recommended scroll form,
as opposed to page/offset paging), jOOQ its native `ORDER BY ... SEEK(cursor) LIMIT`, and JDBC,
Hibernate, Exposed, Exposed DAO, Ktorm, and Jimmer an explicit `WHERE id > cursor ORDER BY id
LIMIT 20`. 1 query per operation for every library except Exposed DAO and Jimmer, which add
their batched association queries. The cursor cycles so successive pages walk the id space.

The page size is inlined as a literal wherever the library can express it: Storm, JDBC, and Exposed by
construction, Hibernate through the HQL `limit` clause, and jOOQ through `DSL.inline` (rule 2; the page size
is a constant of the workload, not data). The reason: the execution plan is the same either way, but
PostgreSQL never adopts a cached generic plan when the row count arrives as a bind parameter (the unknown
count inflates the generic plan's cost estimate), so it replans the three-table join on every execution, at a
cost comparable to executing it. Verified with `PREPARE`/`EXECUTE`: after any number of executions the
parameter form still shows a custom plan with folded constants, while the literal form switches to the cached
generic plan. Ktorm's `take(n)` has no literal form and keeps the bind parameter; Jimmer's `limit(int)` also
binds, on a single-table statement where planning is cheap.

### dynamic

A filtered pet search whose predicate set is built at runtime. A city equality is always
applied (bounding the result to one city's pets), and a `birth_date >=` range and a `type_id =`
equality are toggled on and off across a four-way cycle, so the SQL text and bind-parameter
list differ from call to call. This measures query-construction cost with runtime-variable
predicates, the shape of a search form or filter panel: JDBC and Hibernate assemble the
statement text conditionally, jOOQ composes a `Condition`, Storm composes a `PredicateBuilder`,
Exposed and Exposed DAO build an `Op<Boolean>`, Ktorm reduces a condition list, and Jimmer
relies on its null-predicate-ignoring `where()`. 1 query per operation for every library. The
result is a flat projection (`pet.name`, `owner.last_name`, `city.name`). The sanity check
always sees the first combination (city only), so its row count is a stable
`PETS_PER_CITY`; the measured calls average over the whole cycle.

### multiStatement

One transactional unit of work with a data dependency between statements: insert a `visit`,
obtain the persisted entity, then update its description, all inside one transaction. This is
the shape of a create-then-amend endpoint, and each library obtains the just-inserted row its
natural way: Hibernate and the entity/DAO libraries (Exposed DAO, Ktorm, Jimmer, Storm) get the
persisted entity back from the insert (a managed instance, a populated draft, `insertAndFetch`,
or an entity `add`) and let dirty tracking or an explicit update flush the change; jOOQ inserts
with `RETURNING` and stores the changed record; and JDBC and Exposed (DSL) read the generated id
and inserted values out of the insert result. So the workload is two statements (insert, update),
with no separate read-back. Every implementation runs in an explicit transaction, and the
inserted rows are removed in the untimed teardown between iterations, like `batchInsert`.

### graphInsert

Write a batch of 20 fresh `owner` → `pet` → `visit` graphs in one transaction, each owner in an
existing city and each pet an existing type. The foreign keys force an insertion order (owners
before pets before visits) and require the generated parent keys to reach the children, so the
workload measures how a library handles dependency-ordered bulk writes with key propagation, and
whether the ordering is the ORM's job or the caller's. Storm and Hibernate let the ORM do it:
Storm passes only the visits to `writeSet().insertAndFetch(...)`, whose insertion closure
discovers the unsaved pets and owners through the visits' refs and writes one batch per type per
dependency level; Hibernate builds the object graph and `persist`s each owner root, letting
cascade persist plus `ORDER_INSERTS` order and batch the inserts. The others order it in the
benchmark: JDBC, jOOQ, and Ktorm use three multi-row `INSERT ... RETURNING` statements (Ktorm
through `bulkInsertReturning` from its PostgreSQL support module), Exposed and Exposed DAO three
`batchInsert`s, and Jimmer three `saveEntities` commands, each threading the
returned parent ids into the next level. Every implementation returns the persisted visits and runs
in an explicit transaction; the inserted owners, pets, and visits are removed in the untimed
teardown between iterations.

Every implementation returns visits assembled client-side from the returned keys; no row is
re-read. Storm goes through `writeSet().insertAndFetchIds`, which reports the keys the execution
already holds for foreign-key propagation; its `insertAndFetch` variant, which re-selects the
written rows so the returned entities reflect the state the database actually applied (column
defaults, triggers), is the API for callers who need that stronger contract and is not what this
workload asks for.

## Statement auditing

`scripts/count-statements.sh` runs each workload against a PostgreSQL container with `log_statement=all` and
summarizes the statements each implementation sends, as counts per statement kind. This makes round-trip
patterns auditable: transaction wrapping shows up as BEGIN counts matching query counts, and secondary-query
loading strategies show up as multiples of the primary query count. Rerun it whenever an implementation or a
library version changes; the numbers in this document were verified with it.

## Running

```bash
# Full suite, one tuned container, results merged into results/
scripts/run.sh

# Smoke run (~5 minutes)
scripts/run.sh --quick

# One library, self-contained (starts its own container)
./gradlew :bench-storm:jmh

# One workload across a library
./gradlew :bench-hibernate:jmh -PbenchIncludes='objectGraph'

# Against an existing PostgreSQL
BENCH_JDBC_URL=jdbc:postgresql://localhost:5432/bench scripts/run.sh
```

Requirements: JDK 21, Docker. Until Storm 1.13.0 is on Maven Central, the build resolves
Storm from `mavenLocal()`.

## Publishing

Per-release results are produced by the `benchmark` GitHub Actions workflow (full mode) on
a standard GitHub-hosted runner, uploaded as an artifact, and published alongside the
release notes. Absolute numbers from CI runners vary with the underlying hardware; the
published tables always state the runner type and the exact library versions. Rerun the
suite locally to verify relative results on your own hardware.

The published table reports, per cell, the fastest fork (each fork scored as the mean of its
measurement iterations) with the range up to the slowest fork shown alongside. The rationale:
the suite measures framework overhead, and benchmark noise is one-sided. Garbage collection,
scheduler preemption and an unfavorable JIT compilation plan only ever add time, so the fastest
fork is the estimate least contaminated by the harness, and empirically the most reproducible
across runs (join workloads whose fork means split between two compilation plans deviate several
percent between runs under a median, because the score then depends on how many forks the
warmup lottery assigns to each plan). The plan lottery itself stays visible in the published
range rather than in the score. Five forks are kept because the estimator depends on at least
one fork compiling well: on real data, a three-fork subset misses the fast plan on up to 40% of
draws for the plan-sensitive cells. A JVM
occasionally settles into an unfavorable JIT compilation plan for an entire fork, which a
plain mean folds into the score; the median keeps the score representative while the spread
keeps the disagreement between forks visible. The rule is applied to every implementation and
every workload identically, and the raw per-fork data is published alongside each table.

## Known caveats

- Loopback round-trip time (roughly 50-100 us) is part of every score and compresses
  relative differences; mapping-heavy workloads (`joinWithMapping`, `objectGraph`) show
  library differences more clearly than `singleRowById`.
- Single-threaded latency only; throughput under concurrency is a different benchmark.
- `AverageTime` mode reports means. Tail latency (sample mode with percentiles) is a
  possible extension.
- Shared-container runs execute libraries sequentially on a warm database; the first
  library seeds it. Order effects are mitigated by per-benchmark JVM forks and by the
  database-side cache being fully warm after seeding.
- On laptops, sustained load throttles the CPU as a module run progresses, inflating
  workloads that run later in the (alphabetical) benchmark order. Measured effect: up to
  roughly 40 percent for `objectGraph` (second-to-last) versus an isolated run. Every
  module runs its workloads in the same order, so comparing libraries within one workload
  row stays position-matched and fair; comparing different workload rows against each
  other, or reading rows as absolute costs, carries the position penalty. For absolute
  numbers, benchmark the workload in isolation or run on hardware with thermal headroom.
